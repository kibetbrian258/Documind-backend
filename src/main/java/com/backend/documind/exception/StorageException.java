package com.backend.documind.exception;
/**
 * A file could not be saved, read or deleted.
 * Reaches the user as a generic 500; the details go to the log.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
