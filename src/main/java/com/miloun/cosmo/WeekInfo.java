package com.miloun.cosmo;

import java.util.Collections;
import java.util.List;

/**
 * Week conventions for a locale's region. Days use ISO-8601 numbering
 * (1 = Monday … 7 = Sunday), matching the JS port's `Intl` convention.
 *
 * <p>Unlike the Python port (whose PyICU build exposes no weekend API),
 * ICU4J provides the weekend days directly — no hardcoded data.
 */
public final class WeekInfo {
    /** First day of the week, ISO numbering. */
    public final int firstDay;
    /** Minimal days required in the first week of the year. */
    public final int minimalDays;
    /** Weekend days, ISO numbering, in onset → cease order. */
    public final List<Integer> weekend;

    WeekInfo(int firstDay, int minimalDays, List<Integer> weekend) {
        this.firstDay = firstDay;
        this.minimalDays = minimalDays;
        this.weekend = Collections.unmodifiableList(weekend);
    }
}
