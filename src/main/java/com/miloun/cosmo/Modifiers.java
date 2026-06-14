package com.miloun.cosmo;

/**
 * Optional overrides resolved at construction: {@code calendar},
 * {@code currency} and {@code timeZone}. Any field may be {@code null}
 * (meaning "derive from the locale, or unavailable").
 */
public final class Modifiers {
    /** Calendar keyword, e.g. {@code "buddhist"}. */
    public final String calendar;
    /** ISO 4217 currency code used as the default for {@link Cosmo#money}. */
    public final String currency;
    /** IANA time-zone id, e.g. {@code "Australia/Sydney"}. */
    public final String timeZone;

    public Modifiers(String calendar, String currency, String timeZone) {
        this.calendar = calendar;
        this.currency = currency;
        this.timeZone = timeZone;
    }

    /** All-null modifiers — everything derived from the locale. */
    public static Modifiers none() {
        return new Modifiers(null, null, null);
    }
}
