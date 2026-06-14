package com.miloun.cosmo;

import com.ibm.icu.impl.ICUData;

/**
 * Names of the ICU resource bundles reachable through {@link Cosmo#get}.
 *
 * <p>These mirror the constants in the PHP and Python ports' {@code Bundle}
 * classes. The values come from ICU4J's {@code ICUData} constants — an
 * internal-but-stable class — so the data version ({@code icudt77b}, …) is
 * never hardcoded here.
 */
public final class Bundle {
    /** Break-iterator rule source data. */
    public static final String BRKITR = ICUData.ICU_BRKITR_BASE_NAME;
    /** Currency symbols and display names. */
    public static final String CURRENCY = ICUData.ICU_CURR_BASE_NAME;
    /** The per-locale bundle (delimiters, layout, …). */
    public static final String LOCALE = ICUData.ICU_BASE_NAME;
    /** Language / script / calendar display names. */
    public static final String LANGUAGE = ICUData.ICU_LANG_BASE_NAME;

    private Bundle() {
    }
}
