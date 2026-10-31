package com.backend.documind.service;

import org.springframework.core.io.Resource;

import java.io.InputStream;

/**
 * Saves and loads uploaded files.
 *
 * The rest of the app only talks to this interface, so moving from the local
 * disk to S3 or similar later means writing one new class, nothing else.
 *
 * A "key" is the file's path inside the storage area, e.g. "{userId}/{random}.pdf".
 */
public interface StorageService {

    void store(String key, InputStream content);

    Resource load(String key);

    /** Does nothing if the file is already gone. */
    void delete(String key);
}
