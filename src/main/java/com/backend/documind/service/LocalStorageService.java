package com.backend.documind.service;

import com.backend.documind.config.DocumentProperties;
import com.backend.documind.exception.StorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Keeps uploaded files in a folder on this machine (documind.documents.storage-dir).
 */
@Slf4j
@Service
public class LocalStorageService implements StorageService {

    private final Path root;

    public LocalStorageService(DocumentProperties properties) {
        this.root = properties.storageDir().toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new StorageException("Could not create storage folder " + root, e);
        }
        log.info("Storing uploaded files in {}", root);
    }

    @Override
    public void store(String key, InputStream content) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target); // fails rather than overwrite an existing file
        } catch (IOException e) {
            throw new StorageException("Could not store file " + key, e);
        }
    }

    @Override
    public Resource load(String key) {
        Path file = resolve(key);
        if (!Files.isReadable(file)) {
            throw new StorageException("Stored file is missing: " + key);
        }
        return new FileSystemResource(file);
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new StorageException("Could not delete file " + key, e);
        }
    }

    /** Turns a key into a path, refusing anything that would point outside the storage folder. */
    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new StorageException("Invalid storage key: " + key);
        }
        return path;
    }
}
