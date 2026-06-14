package com.miloun.cosmo;

/**
 * The underlying ICU4J build exposes no API for the requested operation.
 * Environmental, not a caller bug.
 */
public class UnsupportedException extends CosmoException {
    public UnsupportedException(String message) {
        super(message);
    }
}
