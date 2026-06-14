package com.miloun.cosmo;

/**
 * A caller passed an invalid argument — an unknown currency code, an
 * unsupported width/unit, a bad enum value, …
 *
 * <p>Java's single inheritance forces a choice the other ports don't have to
 * make (Python's variant is also a {@code ValueError}): this extends
 * {@link CosmoException} so the library hierarchy stays catchable as
 * one family, rather than extending {@link IllegalArgumentException}.
 */
public class InvalidArgumentException extends CosmoException {
    public InvalidArgumentException(String message) {
        super(message);
    }
}
