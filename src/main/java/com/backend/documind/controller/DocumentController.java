package com.backend.documind.controller;

import com.backend.documind.dto.DocumentResponse;
import com.backend.documind.entity.Document;
import com.backend.documind.entity.DocumentType;
import com.backend.documind.service.DocumentEventService;
import com.backend.documind.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentEventService documentEventService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a PDF, DOCX or TXT file (max 20 MB)",
            description = "Returns 202 Accepted: the file is saved and will be processed in the background.")
    public ResponseEntity<DocumentResponse> upload(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestParam("file") MultipartFile file) {
        Document document = documentService.upload(userId(jwt), file);
        return ResponseEntity.accepted().body(DocumentResponse.from(document));
    }

    @GetMapping
    @Operation(summary = "List my documents, newest first")
    public List<DocumentResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return documentService.list(userId(jwt)).stream().map(DocumentResponse::from).toList();
    }

    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Live stream of status changes (Processing -> Ready/Failed)",
            description = "Server-Sent Events. Each 'document-status' event carries the document's current details. "
                    + "Swagger UI can't display streams; open this URL in a browser tab instead.")
    public SseEmitter events(@AuthenticationPrincipal Jwt jwt) {
        return documentEventService.subscribe(userId(jwt));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One document's details")
    public DocumentResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return DocumentResponse.from(documentService.get(userId(jwt), id));
    }

    @GetMapping("/{id}/file")
    @Operation(summary = "View or download the original file")
    public ResponseEntity<Resource> file(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        DocumentService.DocumentFile file = documentService.loadFile(userId(jwt), id);
        Document document = file.document();

        MediaType mediaType = MediaType.parseMediaType(document.getContentType());
        if (DocumentType.TXT.getMimeType().equals(document.getContentType())) {
            mediaType = new MediaType(mediaType, StandardCharsets.UTF_8);
        }
        // "inline" lets the browser show PDFs and text directly; the name is used if saved.
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(document.getFileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(document.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file.content());
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Process a failed document again",
            description = "Only for documents with status FAILED. Returns 202; watch the status as after an upload.")
    public ResponseEntity<DocumentResponse> retry(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.accepted().body(DocumentResponse.from(documentService.retry(userId(jwt), id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a document and its file")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        documentService.delete(userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    /** The token's subject is the user's id, set by JwtService at login. */
    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}