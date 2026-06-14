package com.miloun.cosmo;

/** Parsed language / script / region subtags of a locale. Empty when absent. */
public final class Subtags {
    public final String language;
    public final String script;
    public final String region;

    public Subtags(String language, String script, String region) {
        this.language = language;
        this.script = script;
        this.region = region;
    }
}
