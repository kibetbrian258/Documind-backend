package com.backend.documind.service;

import com.backend.documind.config.EvalProperties;
import com.backend.documind.dto.EvalCase;
import com.backend.documind.dto.EvalCaseResult;
import com.backend.documind.dto.EvalRunResponse;
import com.backend.documind.dto.EvalSummary;
import com.backend.documind.entity.Document;
import com.backend.documind.entity.DocumentStatus;
import com.backend.documind.repository.DocumentRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * The evaluation suite (FR-17, US-12): an automated exam for answer quality.
 *
 * Each case is a question with a known answer. The case is sent through
 * RagAnswerService, the same code the chat uses, and the answer is scored:
 * - answerable questions: does it contain the key-words, and cite a right page?
 * - unanswerable questions: does it honestly say "not found"?
 *
 * Runs in the background because a local model can take minutes for a full set.
 * The admin starts a run, then checks its progress by id. The last few runs are
 * kept in memory, so they're gone after a restart.
 *
 * Questions are answered as the admin who starts the run, using the documents
 * in the admin's own account, so the usual privacy rules apply here too.
 */
@Slf4j
@Service
public class EvalService {

    private static final int RUNS_KEPT = 10;

    private final RagAnswerService ragAnswerService;
    private final UsageService usageService;
    private final DocumentRepository documents;
    private final ResourceLoader resourceLoader;
    private final JsonMapper jsonMapper;
    private final EvalProperties properties;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<UUID, EvalRunResponse> runs = new LinkedHashMap<>(); // guarded by "this"

    public EvalService(RagAnswerService ragAnswerService, UsageService usageService, DocumentRepository documents,
                       ResourceLoader resourceLoader, JsonMapper jsonMapper, EvalProperties properties) {
        this.ragAnswerService = ragAnswerService;
        this.usageService = usageService;
        this.documents = documents;
        this.resourceLoader = resourceLoader;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
    }

    /** Loads the cases and starts answering them in the background. Only one run at a time. */
    public EvalRunResponse start(UUID adminUserId) {
        List<EvalCase> cases = loadCases();
        if (!running.compareAndSet(false, true)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An evaluation is already running. Wait for it to finish.");
        }
        UUID runId = UUID.randomUUID();
        Instant startedAt = Instant.now();
        save(new EvalRunResponse(runId, EvalRunResponse.RunStatus.RUNNING, startedAt, null,
                cases.size(), 0, null, null, List.of()));
        log.info("Evaluation {} started with {} case(s)", runId, cases.size());

        executor.execute(() -> {
            try {
                execute(runId, adminUserId, cases, startedAt);
            } catch (RuntimeException e) {
                log.error("Evaluation {} failed", runId, e);
                EvalRunResponse current = get(runId);
                save(new EvalRunResponse(runId, EvalRunResponse.RunStatus.FAILED, startedAt, Instant.now(),
                        cases.size(), current.completedCases(), null,
                        "The evaluation stopped unexpectedly. See the server log.", current.results()));
            } finally {
                running.set(false);
            }
        });
        return get(runId);
    }

    public synchronized EvalRunResponse get(UUID runId) {
        EvalRunResponse run = runs.get(runId);
        if (run == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Evaluation run not found. Runs are kept in memory and cleared on restart.");
        }
        return run;
    }

    /** Recent runs, newest first. */
    public synchronized List<EvalRunResponse> list() {
        List<EvalRunResponse> newestFirst = new ArrayList<>(runs.values());
        Collections.reverse(newestFirst);
        return newestFirst;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private void execute(UUID runId, UUID userId, List<EvalCase> cases, Instant startedAt) {
        // Newest first, so if a file was uploaded twice the latest copy is used.
        List<Document> userDocuments = documents.findAllByUserIdOrderByCreatedAtDesc(userId);
        List<EvalCaseResult> results = new ArrayList<>();

        for (EvalCase evalCase : cases) {
            EvalCaseResult result;
            try {
                result = evaluate(userId, evalCase, userDocuments);
            } catch (RuntimeException e) {
                log.warn("Eval case {} errored", evalCase.id(), e);
                result = failed(evalCase, "Error while answering: " + e.getMessage());
            }
            results.add(result);
            log.info("Eval case {}: {}", result.id(), result.status());
            save(new EvalRunResponse(runId, EvalRunResponse.RunStatus.RUNNING, startedAt, null,
                    cases.size(), results.size(), null, null, List.copyOf(results)));
        }

        EvalSummary summary = summarise(results);
        save(new EvalRunResponse(runId, EvalRunResponse.RunStatus.COMPLETED, startedAt, Instant.now(),
                cases.size(), results.size(), summary, null, List.copyOf(results)));
        log.info("Evaluation {} finished: {}/{} passed ({}%)", runId, summary.passed(),
                summary.total() - summary.skipped(), summary.passRatePercent());
    }

    private EvalCaseResult evaluate(UUID userId, EvalCase evalCase, List<Document> userDocuments) {
        List<UUID> scope = List.of();
        if (evalCase.document() != null && !evalCase.document().isBlank()) {
            Optional<Document> document = userDocuments.stream()
                    .filter(d -> d.getFileName().equalsIgnoreCase(evalCase.document().strip()))
                    .filter(d -> d.getStatus() == DocumentStatus.READY)
                    .findFirst();
            if (document.isEmpty()) {
                return skipped(evalCase, "Document '" + evalCase.document()
                        + "' isn't uploaded (and READY) in the account running the evaluation.");
            }
            scope = List.of(document.get().getId());
        }

        RagAnswerService.Answer answer = ragAnswerService.answer(userId, evalCase.question(), List.of(), scope,
                piece -> { /* not streamed anywhere; only the final answer is scored */ });
        if (answer.modelCalled()) {
            // FR-14: every AI call is recorded, including evaluation ones (no message id).
            usageService.recordChat(userId, null, answer.model(), answer.inputTokens(), answer.outputTokens(),
                    answer.firstTokenAt() - answer.startedAt(), answer.finishedAt() - answer.startedAt());
        }
        return score(evalCase, answer);
    }

    private EvalCaseResult score(EvalCase evalCase, RagAnswerService.Answer answer) {
        long latencyMs = answer.finishedAt() - answer.startedAt();
        List<Integer> citedPages = answer.cited().stream()
                .map(p -> p.chunk().page())
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        if (evalCase.shouldBeNotFound()) {
            boolean passed = answer.notFound();
            return new EvalCaseResult(evalCase.id(), evalCase.question(),
                    passed ? EvalCaseResult.Status.PASSED : EvalCaseResult.Status.FAILED,
                    passed ? null : "Should have said \"not found\", but answered anyway.",
                    answer.text(), true, answer.notFound(), null, List.of(), citedPages, List.of(), null, latencyMs);
        }

        if (answer.notFound()) {
            return new EvalCaseResult(evalCase.id(), evalCase.question(), EvalCaseResult.Status.FAILED,
                    "Said \"not found\", but the document contains the answer.",
                    answer.text(), false, true, evalCase.keywords().isEmpty() ? null : 0.0,
                    evalCase.keywords(), citedPages, evalCase.pages(),
                    evalCase.pages().isEmpty() ? null : false, latencyMs);
        }

        String normalisedAnswer = normalise(answer.text());
        List<String> missing = evalCase.keywords().stream()
                .filter(Predicate.not(keyword -> normalisedAnswer.contains(normalise(keyword))))
                .toList();
        Double coverage = evalCase.keywords().isEmpty()
                ? null
                : round((evalCase.keywords().size() - missing.size()) / (double) evalCase.keywords().size(), 3);
        Boolean citationCorrect = evalCase.pages().isEmpty()
                ? null
                : citedPages.stream().anyMatch(evalCase.pages()::contains);

        List<String> problems = new ArrayList<>();
        if (coverage != null && coverage < properties.minKeywordCoverage()) {
            problems.add("Missing key words: " + String.join(", ", missing) + ".");
        }
        if (Boolean.FALSE.equals(citationCorrect)) {
            problems.add("Cited page(s) " + citedPages + " instead of " + evalCase.pages() + ".");
        }
        return new EvalCaseResult(evalCase.id(), evalCase.question(),
                problems.isEmpty() ? EvalCaseResult.Status.PASSED : EvalCaseResult.Status.FAILED,
                problems.isEmpty() ? null : String.join(" ", problems),
                answer.text(), false, false, coverage, missing, citedPages, evalCase.pages(),
                citationCorrect, latencyMs);
    }

    private static EvalSummary summarise(List<EvalCaseResult> results) {
        List<EvalCaseResult> evaluated = results.stream()
                .filter(r -> r.status() != EvalCaseResult.Status.SKIPPED).toList();
        List<EvalCaseResult> answerable = evaluated.stream().filter(r -> !r.expectNotFound()).toList();
        List<EvalCaseResult> unanswerable = evaluated.stream().filter(EvalCaseResult::expectNotFound).toList();
        List<EvalCaseResult> withPages = answerable.stream().filter(r -> r.citationCorrect() != null).toList();
        List<Long> latencies = evaluated.stream().map(EvalCaseResult::latencyMs)
                .filter(Objects::nonNull).sorted().toList();

        int passed = (int) evaluated.stream().filter(r -> r.status() == EvalCaseResult.Status.PASSED).count();
        return new EvalSummary(
                results.size(),
                passed,
                evaluated.size() - passed,
                results.size() - evaluated.size(),
                percent(passed, evaluated.size()),
                percent(answerable.stream().filter(r -> r.status() == EvalCaseResult.Status.PASSED).count(),
                        answerable.size()),
                percent(withPages.stream().filter(r -> Boolean.TRUE.equals(r.citationCorrect())).count(),
                        withPages.size()),
                percent(unanswerable.stream().filter(r -> r.status() == EvalCaseResult.Status.PASSED).count(),
                        unanswerable.size()),
                latencies.isEmpty() ? null : latencies.get(latencies.size() / 2));
    }

    private List<EvalCase> loadCases() {
        Resource resource = resourceLoader.getResource(properties.casesFile());
        EvalCase[] loaded;
        try (InputStream in = resource.getInputStream()) {
            loaded = jsonMapper.readValue(in, EvalCase[].class);
        } catch (IOException | RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not read the eval cases file " + properties.casesFile() + ": " + e.getMessage());
        }
        List<EvalCase> cases = new ArrayList<>();
        for (int i = 0; i < loaded.length; i++) {
            EvalCase c = loaded[i];
            if (c == null || c.question() == null || c.question().isBlank()) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Eval case number " + (i + 1) + " has no question.");
            }
            String id = c.id() == null || c.id().isBlank() ? "case-" + (i + 1) : c.id();
            cases.add(new EvalCase(id, c.question(), c.document(), c.expectNotFound(),
                    c.mustContain(), c.expectedPages(), c.notes()));
        }
        if (cases.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "The eval cases file is empty.");
        }
        return cases;
    }

    private synchronized void save(EvalRunResponse run) {
        runs.put(run.id(), run);
        while (runs.size() > RUNS_KEPT) {
            runs.remove(runs.keySet().iterator().next()); // drop the oldest
        }
    }

    private static EvalCaseResult skipped(EvalCase c, String reason) {
        return new EvalCaseResult(c.id(), c.question(), EvalCaseResult.Status.SKIPPED, reason, null,
                c.shouldBeNotFound(), false, null, List.of(), List.of(), c.pages(), null, null);
    }

    private static EvalCaseResult failed(EvalCase c, String reason) {
        return new EvalCaseResult(c.id(), c.question(), EvalCaseResult.Status.FAILED, reason, null,
                c.shouldBeNotFound(), false, null, List.of(), List.of(), c.pages(), null, null);
    }

    /** Lower case, straight apostrophes, single spaces, so "Cost  Budget" matches "cost budget". */
    private static String normalise(String text) {
        return text.toLowerCase(Locale.ROOT).replace('’', '\'').replaceAll("\\s+", " ").strip();
    }

    private static Double percent(long part, long whole) {
        return whole == 0 ? null : round(100.0 * part / whole, 1);
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}