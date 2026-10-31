package com.backend.documind.service;

import com.backend.documind.config.DocumentProperties;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Step 2 of ingestion: cut the text into chunks of about 500 tokens (FR-4).
 *
 * Spring AI's TokenTextSplitter does the cutting, preferring to end a chunk at
 * a sentence or line break. It has no overlap option, so this class adds one:
 * each chunk starts with the last ~50 tokens of the chunk before it, so a
 * sentence cut at a boundary still appears whole in one of the two chunks.
 *
 * Every chunk is tagged with who owns it and where it came from. The user_id
 * tag is what keeps searches private.
 */
@Component
public class TextChunker {

    public static final String USER_ID = "user_id";
    public static final String DOCUMENT_ID = "document_id";
    public static final String PAGE = "page";
    public static final String CHUNK_INDEX = "chunk_index";

    private final TokenTextSplitter splitter;
    private final Encoding encoding;
    private final int overlapTokens;

    public TextChunker(DocumentProperties properties) {
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(properties.chunkSizeTokens())
                .build();
        // Same tokenizer the splitter uses, so "tokens" means the same thing in both.
        this.encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
        this.overlapTokens = properties.chunkOverlapTokens();
    }

    public List<Document> chunk(UUID userId, UUID documentId, List<DocumentTextExtractor.PageText> pages) {
        List<Document> chunks = new ArrayList<>();
        String previousChunk = null;
        int chunkIndex = 0;

        for (DocumentTextExtractor.PageText page : pages) {
            for (Document piece : splitter.split(new Document(page.text()))) {
                String text = Objects.requireNonNullElse(piece.getText(), "").strip();
                if (text.isEmpty()) {
                    continue;
                }
                String withOverlap = previousChunk == null ? text : tailOf(previousChunk) + "\n" + text;

                Map<String, Object> metadata = new HashMap<>();
                metadata.put(USER_ID, userId.toString());
                metadata.put(DOCUMENT_ID, documentId.toString());
                metadata.put(CHUNK_INDEX, chunkIndex++);
                if (page.pageNumber() != null) { // metadata can't hold null values
                    metadata.put(PAGE, page.pageNumber());
                }
                chunks.add(new Document(withOverlap, metadata));
                previousChunk = text;
            }
        }
        return chunks;
    }

    /** The last overlapTokens tokens of a chunk, starting at a word boundary. */
    private String tailOf(String chunk) {
        List<Integer> tokens = encoding.encode(chunk).boxed();
        if (tokens.size() <= overlapTokens) {
            return chunk;
        }
        List<Integer> tail = tokens.subList(tokens.size() - overlapTokens, tokens.size());
        IntArrayList tailTokens = new IntArrayList(tail.size());
        tail.forEach(tailTokens::add);
        String text = encoding.decode(tailTokens);

        // The cut may land mid-word; drop that partial word.
        int firstSpace = text.indexOf(' ');
        return (firstSpace >= 0 ? text.substring(firstSpace + 1) : text).strip();
    }
}
