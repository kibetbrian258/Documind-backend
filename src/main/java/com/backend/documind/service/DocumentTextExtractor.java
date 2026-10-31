package com.backend.documind.service;

import com.backend.documind.entity.DocumentType;
import com.backend.documind.exception.DocumentProcessingException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Step 1 of ingestion: pull plain text out of a file.
 *
 * - PDF:  one piece of text per page, so answers can cite the page.
 * - DOCX: one piece of text for the whole file (Word files have no fixed pages).
 * - TXT:  the file's text as it is.
 *
 * Problems the user can fix (scanned PDF, password, empty file) are thrown as
 * DocumentProcessingException with a friendly message.
 */
@Component
public class DocumentTextExtractor {

    /** Text from one page; pageNumber is null for formats without pages (DOCX, TXT). */
    public record PageText(Integer pageNumber, String text) {
    }

    /** All text from a file; pageCount is null for formats without pages. */
    public record ExtractedText(List<PageText> pages, Integer pageCount) {
    }

    public ExtractedText extract(DocumentType type, Resource file) {
        return switch (type) {
            case PDF -> extractPdf(file);
            case DOCX -> extractDocx(file);
            case TXT -> extractTxt(file);
        };
    }

    private ExtractedText extractPdf(Resource file) {
        int pageCount;
        try (PDDocument pdf = Loader.loadPDF(file.getContentAsByteArray())) {
            pageCount = pdf.getNumberOfPages();
        } catch (InvalidPasswordException e) {
            throw new DocumentProcessingException(
                    "This PDF is password-protected. Remove the password and upload it again.");
        } catch (IOException e) {
            throw new DocumentProcessingException("This PDF could not be read. It may be damaged.");
        }

        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                .withPagesPerDocument(1)
                // Removes the indentation PDF layout adds, which would otherwise waste tokens.
                .withPageExtractedTextFormatter(ExtractedTextFormatter.builder().withLeftAlignment(true).build())
                .build();

        // The reader skips pages with no text, so a scanned PDF gives an empty list.
        List<PageText> pages = new PagePdfDocumentReader(file, config).get().stream()
                .map(page -> new PageText(
                        ((Number) page.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER)).intValue(),
                        Objects.requireNonNullElse(page.getText(), "")))
                .filter(page -> !page.text().isBlank())
                .toList();

        if (pages.isEmpty()) {
            throw new DocumentProcessingException(
                    "No text was found in this PDF. It looks like a scanned document, "
                            + "and scanned PDFs aren't supported yet.");
        }
        return new ExtractedText(pages, pageCount);
    }

    private ExtractedText extractDocx(Resource file) {
        String text;
        try {
            text = new TikaDocumentReader(file).get().stream()
                    .map(document -> Objects.requireNonNullElse(document.getText(), ""))
                    .collect(Collectors.joining("\n\n"));
        } catch (RuntimeException e) {
            throw new DocumentProcessingException("This Word document could not be read. It may be damaged.");
        }
        if (text.isBlank()) {
            throw new DocumentProcessingException("No text was found in this Word document.");
        }
        return new ExtractedText(List.of(new PageText(null, text)), null);
    }

    private ExtractedText extractTxt(Resource file) {
        String text;
        try {
            // Upload already checked that the file is valid UTF-8.
            text = file.getContentAsString(StandardCharsets.UTF_8).replace("﻿", "");
        } catch (IOException e) {
            throw new DocumentProcessingException("This text file could not be read.");
        }
        if (text.isBlank()) {
            throw new DocumentProcessingException("This text file is empty.");
        }
        return new ExtractedText(List.of(new PageText(null, text)), null);
    }
}
