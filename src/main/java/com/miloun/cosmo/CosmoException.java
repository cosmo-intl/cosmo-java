package com.miloun.cosmo;

/**
 * Base exception for cosmo. Catch this to handle any library error;
 * catch a subclass to distinguish the cause.
 */
public class CosmoException extends RuntimeException {
    public CosmoException(String message) {
        super(message);
    }

    public CosmoException(String message, Throwable cause) {
        super(message, cause);
    }
}
