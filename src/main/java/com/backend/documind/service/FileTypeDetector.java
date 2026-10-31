package com.backend.documind.service;

import com.backend.documind.entity.DocumentType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * Works out a file's real type from its contents, not its name.
 *
 * A file called "notes.pdf" could be anything, so the extension and the
 * browser's Content-Type header are ignored:
 * - PDF:  starts with the bytes "%PDF-".
 * - DOCX: a ZIP archive that contains "word/document.xml".
 * - TXT:  valid UTF-8 text with no binary zero bytes.
 */
@Component
public class FileTypeDetector {

    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP_SIGNATURE = {0x50, 0x4B, 0x03, 0x04}; // "PK\3\4"
    private static final String DOCX_MAIN_PART = "word/document.xml";

    public Optional<DocumentType> detect(MultipartFile file) {
        try {
            byte[] head;
            try (InputStream in = file.getInputStream()) {
                head = in.readNBytes(8);
            }
            if (startsWith(head, PDF_SIGNATURE)) {
                return Optional.of(DocumentType.PDF);
            }
            if (startsWith(head, ZIP_SIGNATURE)) {
                return isDocx(file) ? Optional.of(DocumentType.DOCX) : Optional.empty();
            }
            return isUtf8Text(file) ? Optional.of(DocumentType.TXT) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length
                && Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
    }

    /** Lists the ZIP's entries (without unpacking them) looking for Word's main document part. */
    private static boolean isDocx(MultipartFile file) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (DOCX_MAIN_PART.equals(entry.getName())) {
                    return true;
                }
            }
            return false;
        } catch (ZipException e) {
            return false; // corrupt archive
        }
    }

    /** Reads the whole file as strict UTF-8; any invalid byte or zero byte means it isn't plain text. */
    private static boolean isUtf8Text(MultipartFile file) throws IOException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try (Reader reader = new InputStreamReader(file.getInputStream(), decoder)) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                for (int i = 0; i < read; i++) {
                    if (buffer[i] == '\u0000') {
                        return false;
                    }
                }
            }
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}