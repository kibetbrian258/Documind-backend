package com.backend.documind.service;

import com.backend.documind.config.ChatProperties;
import com.backend.documind.dto.RetrievedChunk;
import com.backend.documind.entity.ChatMessage;
import com.backend.documind.entity.MessageRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The core RAG pipeline: question in, cited answer out (PRD Flow B, steps 3-7).
 *
 *  1. retrieve the most relevant passages (RetrievalService: always this user's only),
 *     and report them to onSearched
 *  2. nothing relevant -> the "not found" reply, without calling the AI model (US-6)
 *  3. otherwise send rules + recent messages + passages + question to the model
 *  4. pass each piece of the answer to onToken as it arrives
 *  5. work out which passages the answer cites
 *
 * Reliability (PRD section 7): if the search or the model call fails, it is
 * tried once more after a short pause. The model call is only retried if no
 * text has been sent yet; otherwise the user would see the start of the
 * answer twice.
 *
 * It saves nothing and knows nothing about HTTP. ChatService adds saving and
 * streaming to the browser; EvalService calls it to score answers. Both use
 * exactly this code, so the evaluation measures what users really get.
 */
@Slf4j
@Service
public class RagAnswerService {

    private static final Pattern CITATION_MARKER = Pattern.compile("\\[(\\d{1,2})]");
    private static final long RETRY_PAUSE_MS = 1_000;

    private final ChatClient chatClient;
    private final RetrievalService retrievalService;
    private final PromptBuilder promptBuilder;
    private final ChatProperties properties;

    public RagAnswerService(ChatClient.Builder chatClientBuilder,
                            RetrievalService retrievalService,
                            PromptBuilder promptBuilder,
                            ChatProperties properties) {
        this.chatClient = chatClientBuilder.build();
        this.retrievalService = retrievalService;
        this.promptBuilder = promptBuilder;
        this.properties = properties;
    }

    /** A retrieved passage with the number it has in the prompt (and in the answer's [n] markers). */
    public record Passage(int number, RetrievedChunk chunk) {
    }

    /**
     * @param text          the full answer
     * @param notFound      true if the answer is the honest "not found" reply
     * @param modelCalled   false when no passage was relevant and the model was skipped
     * @param cited         the passages the answer cites, in number order
     * @param model         the model name the provider reported; null if the model wasn't called
     * @param firstTokenAt  when the first piece of text arrived (epoch ms)
     */
    public record Answer(String text, boolean notFound, boolean modelCalled, List<Passage> cited, String model,
                         int inputTokens, int outputTokens, long startedAt, long firstTokenAt, long finishedAt) {
    }

    /** Same as below, for callers that don't need to know when the search has finished (EvalService). */
    public Answer answer(UUID userId, String question, List<ChatMessage> history,
                         Collection<UUID> documentIds, Consumer<String> onToken) {
        return answer(userId, question, history, documentIds, passages -> { }, onToken);
    }

    /**
     * @param history     earlier messages in the conversation, oldest first (empty for a fresh question)
     * @param documentIds optional: only search these documents
     * @param onSearched  receives the passages found, once, before any text (empty means "not found")
     * @param onToken     receives each piece of the answer as it is written; may throw to stop early
     */
    public Answer answer(UUID userId, String question, List<ChatMessage> history,
                         Collection<UUID> documentIds, Consumer<List<RetrievedChunk>> onSearched,
                         Consumer<String> onToken) {
        long startedAt = System.currentTimeMillis();
        String searchQuery = retrievalQuery(history, question);
        List<RetrievedChunk> passages = withOneRetry("search",
                () -> retrievalService.retrieve(userId, searchQuery, documentIds));
        onSearched.accept(passages);

        if (passages.isEmpty()) {
            String text = properties.notFoundMessage();
            onToken.accept(text);
            long now = System.currentTimeMillis();
            return new Answer(text, true, false, List.of(), null, 0, 0, startedAt, now, now);
        }

        AtomicBoolean textSent = new AtomicBoolean(false);
        Streamed streamed;
        try {
            streamed = streamOnce(question, history, passages, onToken, textSent);
        } catch (RuntimeException firstFailure) {
            if (textSent.get()) {
                throw firstFailure; // part of the answer is already on screen; don't start again
            }
            log.warn("AI model call failed; retrying once: {}", firstFailure.toString());
            pause(firstFailure);
            streamed = streamOnce(question, history, passages, onToken, textSent);
        }

        long finishedAt = System.currentTimeMillis();
        String text = streamed.text();
        if (text.isEmpty()) {
            text = properties.notFoundMessage();
        }
        if (streamed.usage() == null) {
            log.warn("The model reported no token usage; recording zeros");
        }
        boolean notFound = isNotFound(text);
        return new Answer(
                text,
                notFound,
                true,
                notFound ? List.of() : citedPassages(text, passages),
                Objects.requireNonNullElse(streamed.model(), "unknown"),
                streamed.usage() == null ? 0 : nullToZero(streamed.usage().getPromptTokens()),
                streamed.usage() == null ? 0 : nullToZero(streamed.usage().getCompletionTokens()),
                startedAt,
                streamed.firstTokenAt() == 0 ? finishedAt : streamed.firstTokenAt(),
                finishedAt);
    }

    /** What one model call produced. */
    private record Streamed(String text, Usage usage, String model, long firstTokenAt) {
    }

    /** One streaming call to the model; textSent becomes true as soon as any text reaches onToken. */
    private Streamed streamOnce(String question, List<ChatMessage> history, List<RetrievedChunk> passages,
                                Consumer<String> onToken, AtomicBoolean textSent) {
        StringBuilder answer = new StringBuilder();
        Usage usage = null;
        String model = null;
        long firstTokenAt = 0;

        try (Stream<ChatResponse> responses = chatClient.prompt()
                .system(promptBuilder.systemPrompt())
                .messages(promptBuilder.history(history))
                .user(promptBuilder.userPrompt(passages, question))
                .stream()
                .chatResponse()
                .toStream()) { // closing the stream stops the model, e.g. if the user leaves

            for (ChatResponse response : (Iterable<ChatResponse>) responses::iterator) {
                String piece = textOf(response);
                if (!piece.isEmpty()) {
                    if (firstTokenAt == 0) {
                        firstTokenAt = System.currentTimeMillis();
                    }
                    answer.append(piece);
                    textSent.set(true);
                    onToken.accept(piece);
                }
                // Token counts arrive with the last piece of the stream.
                Usage pieceUsage = response.getMetadata().getUsage();
                if (pieceUsage != null && nullToZero(pieceUsage.getPromptTokens())
                        + nullToZero(pieceUsage.getCompletionTokens()) > 0) {
                    usage = pieceUsage;
                }
                if (StringUtils.hasText(response.getMetadata().getModel())) {
                    model = response.getMetadata().getModel();
                }
            }
        }

        return new Streamed(answer.toString().strip(), usage, model, firstTokenAt);
    }

    /** Runs an action; if it fails, waits a moment and tries exactly once more. */
    private <T> T withOneRetry(String what, Supplier<T> action) {
        try {
            return action.get();
        } catch (RuntimeException firstFailure) {
            log.warn("{} failed; retrying once: {}", what, firstFailure.toString());
            pause(firstFailure);
            return action.get();
        }
    }

    private static void pause(RuntimeException cause) {
        try {
            Thread.sleep(RETRY_PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw cause;
        }
    }

    /** True if the text is the "not found" reply (tolerating curly apostrophes and quotes from the model). */
    public boolean isNotFound(String text) {
        return normalise(text).contains(normalise(properties.notFoundMessage()));
    }

    private static String normalise(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replace('’', '\'')      // ’ -> '
                .replaceAll("[\"“”.]", "")
                .replaceAll("\\s+", " ")
                .strip();
    }

    /**
     * Which passages the answer cites, from its [n] markers. If a small model
     * forgets the markers, all retrieved passages are listed, so the user can
     * still check the sources.
     */
    private static List<Passage> citedPassages(String answer, List<RetrievedChunk> passages) {
        Set<Integer> numbers = new LinkedHashSet<>();
        Matcher matcher = CITATION_MARKER.matcher(answer);
        while (matcher.find()) {
            int number = Integer.parseInt(matcher.group(1));
            if (number >= 1 && number <= passages.size()) {
                numbers.add(number);
            }
        }
        List<Passage> cited = new ArrayList<>();
        if (numbers.isEmpty()) {
            for (int i = 0; i < passages.size(); i++) {
                cited.add(new Passage(i + 1, passages.get(i)));
            }
        } else {
            numbers.stream().sorted().forEach(n -> cited.add(new Passage(n, passages.get(n - 1))));
        }
        return cited;
    }

    /**
     * For a follow-up like "explain that more simply", searching for those words alone
     * finds nothing useful, so the previous question is included in the search.
     */
    private static String retrievalQuery(List<ChatMessage> history, String question) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).getRole() == MessageRole.USER) {
                return history.get(i).getContent() + "\n" + question;
            }
        }
        return question;
    }

    private static String textOf(ChatResponse response) {
        Generation result = response.getResult();
        if (result == null || result.getOutput() == null) {
            return "";
        }
        return Objects.requireNonNullElse(result.getOutput().getText(), "");
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}