package com.miloun.cosmo;

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.number.Notation;
import com.ibm.icu.number.NumberFormatter;
import com.ibm.icu.number.NumberRangeFormatter;
import com.ibm.icu.text.AlphabeticIndex;
import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.DateFormat;
import com.ibm.icu.text.DateFormatSymbols;
import com.ibm.icu.text.DateIntervalFormat;
import com.ibm.icu.text.DecimalFormat;
import com.ibm.icu.text.DecimalFormatSymbols;
import com.ibm.icu.text.ListFormatter;
import com.ibm.icu.text.LocaleDisplayNames;
import com.ibm.icu.text.MeasureFormat;
import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.NumberingSystem;
import com.ibm.icu.text.PersonName;
import com.ibm.icu.text.PersonNameFormatter;
import com.ibm.icu.text.PluralRules;
import com.ibm.icu.text.RelativeDateTimeFormatter;
import com.ibm.icu.text.RelativeDateTimeFormatter.RelativeDateTimeUnit;
import com.ibm.icu.text.RuleBasedCollator;
import com.ibm.icu.text.RuleBasedNumberFormat;
import com.ibm.icu.text.SimpleDateFormat;
import com.ibm.icu.text.SimplePersonName;
import com.ibm.icu.text.SpoofChecker;
import com.ibm.icu.text.Transliterator;
import com.ibm.icu.util.Calendar;
import com.ibm.icu.util.Currency;
import com.ibm.icu.util.CurrencyAmount;
import com.ibm.icu.util.DateInterval;
import com.ibm.icu.util.LocaleMatcher;
import com.ibm.icu.util.Measure;
import com.ibm.icu.util.MeasureUnit;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.ULocale;
import com.ibm.icu.util.UResourceBundle;

import java.text.ParseException;
import java.text.ParsePosition;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Cosmo — application localisation for Java.
 *
 * <p>A thin, ergonomic layer over <strong>ICU</strong> (reached through ICU4J).
 * It bundles <strong>no</strong> locale data of its own — every result comes
 * straight from ICU. Construct once per locale and reuse.
 */
public final class Cosmo {

    private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Z]{3}");

    // Every key a number-options map may carry. The JS-only options are accepted
    // but ignored here (no equivalent in ICU's DecimalFormat); anything else is a
    // typo and raises. Keys are camelCase, the JS/PHP form.
    private static final Set<String> KNOWN_NUMBER_OPTIONS = new HashSet<>(Arrays.asList(
            "minimumIntegerDigits", "minimumFractionDigits", "maximumFractionDigits",
            "minimumSignificantDigits", "maximumSignificantDigits",
            "roundingMode", "roundingIncrement", "useGrouping",
            "signDisplay", "trailingZeroDisplay", "roundingPriority", "notation", "compactDisplay"));

    private static final Set<String> KNOWN_COLLATION_OPTIONS =
            new HashSet<>(Arrays.asList("numeric", "caseFirst"));

    // Units accepted by duration() in its multi-unit (map) form, largest first.
    private static final List<String> DURATION_UNITS = Arrays.asList(
            "years", "months", "weeks", "days", "hours", "minutes", "seconds", "milliseconds");

    // Width combos dateRange supports, mapped to ICU interval skeletons.
    private static final Map<String, String> RANGE_SKELETONS = new HashMap<>();

    static {
        RANGE_SKELETONS.put("short|none", "yMd");
        RANGE_SKELETONS.put("medium|none", "yMMMd");
        RANGE_SKELETONS.put("long|none", "yMMMMd");
        RANGE_SKELETONS.put("full|none", "yMMMMEEEEd");
        RANGE_SKELETONS.put("none|short", "jm");
        RANGE_SKELETONS.put("none|medium", "jms");
        RANGE_SKELETONS.put("medium|short", "yMMMdjm");
        RANGE_SKELETONS.put("short|short", "yMdjm");
    }

    /** Canonical ICU locale id, e.g. {@code "en_AU"}. */
    public final String locale;
    /** Parsed language / script / region subtags. */
    public final Subtags subtags;
    /** Resolved modifiers (calendar / currency / timeZone). */
    public final Modifiers modifiers;

    private final ULocale uloc;

    /** Builds a Cosmo for the given locale with no modifier overrides. */
    public Cosmo(String locale) {
        this(locale, Modifiers.none());
    }

    /**
     * @param locale BCP-47 or underscore locale id ({@code en_AU}, {@code fa-IR},
     *   {@code en_AU@calendar=buddhist}). {@code null}/blank falls back to the
     *   JVM's default locale.
     * @param modifiers optional {@code calendar}, {@code currency} and
     *   {@code timeZone} overrides; {@code null} fields are derived from the locale.
     */
    public Cosmo(String locale, Modifiers modifiers) {
        String raw = locale == null ? "" : locale.replace('-', '_').trim();
        if (raw.isEmpty()) {
            raw = ULocale.getDefault().getName();
        }
        // new ULocale() converts BCP-47 u-extensions (-u-nu-thai) into ICU keywords;
        // createCanonical() on the raw string would mangle them into bogus variants.
        this.uloc = ULocale.createCanonical(new ULocale(raw));
        this.locale = uloc.getName();

        this.subtags = new Subtags(uloc.getLanguage(), uloc.getScript(), uloc.getCountry());

        if (modifiers == null) {
            modifiers = Modifiers.none();
        }
        String calendar = modifiers.calendar != null ? modifiers.calendar : emptyToNull(uloc.getKeywordValue("calendar"));
        String currency = modifiers.currency;

        // PHP-style region -> currency inference (ICU-backed, no data table).
        if (currency == null && !subtags.region.isEmpty()) {
            Currency inferred = Currency.getInstance(uloc);
            if (inferred != null && !"XXX".equals(inferred.getCurrencyCode())) {
                currency = inferred.getCurrencyCode();
            }
        }
        this.modifiers = new Modifiers(calendar, currency, modifiers.timeZone);
    }

    // ------------------------------------------------------------------ //
    // constructors
    // ------------------------------------------------------------------ //

    /** Builds a Cosmo from locale subtags, e.g. {@code new Subtags("en", "", "AU")}. */
    public static Cosmo fromSubtags(Subtags subtags) {
        return fromSubtags(subtags, Modifiers.none());
    }

    /** Builds a Cosmo from locale subtags instead of a string. */
    public static Cosmo fromSubtags(Subtags subtags, Modifiers modifiers) {
        // Build via the ULocale.Builder so the subtags are validated and canonicalised.
        ULocale.Builder builder = new ULocale.Builder();
        if (subtags.language != null && !subtags.language.isEmpty()) {
            builder.setLanguage(subtags.language);
        }
        if (subtags.script != null && !subtags.script.isEmpty()) {
            builder.setScript(subtags.script);
        }
        if (subtags.region != null && !subtags.region.isEmpty()) {
            builder.setRegion(subtags.region);
        }
        return new Cosmo(builder.build().getName(), modifiers);
    }

    /** Builds a Cosmo from an HTTP {@code Accept-Language} header. */
    public static Cosmo fromAcceptLanguage(String header) {
        return fromAcceptLanguage(header, Modifiers.none());
    }

    /** Builds a Cosmo from an HTTP {@code Accept-Language} header, picking the best-quality tag. */
    public static Cosmo fromAcceptLanguage(String header, Modifiers modifiers) {
        List<String> tags = parseAcceptLanguage(header);
        return new Cosmo(tags.isEmpty() ? null : tags.get(0), modifiers);
    }

    /** Negotiating variant of {@link #fromAcceptLanguage(String, Collection, Modifiers)}. */
    public static Cosmo fromAcceptLanguage(String header, Collection<String> supported) {
        return fromAcceptLanguage(header, supported, Modifiers.none());
    }

    /**
     * Builds a Cosmo for the <em>supported</em> locale that best serves an HTTP
     * {@code Accept-Language} header, negotiated with CLDR language-distance
     * data (see {@link #bestMatch}). Falls back to the first supported locale.
     */
    public static Cosmo fromAcceptLanguage(String header, Collection<String> supported, Modifiers modifiers) {
        Map<ULocale, String> originals = ulocales(supported);
        List<ULocale> desired = new ArrayList<>();
        for (String tag : parseAcceptLanguage(header)) {
            desired.add(toULocale(tag));
        }
        if (desired.isEmpty()) {
            return new Cosmo(originals.values().iterator().next(), modifiers);
        }
        ULocale match = matcher(originals.keySet()).getBestMatch(desired);
        return new Cosmo(originals.get(match), modifiers);
    }

    /** Header tags ordered by descending quality (ties keep header order). */
    private static List<String> parseAcceptLanguage(String header) {
        List<Map.Entry<String, Double>> entries = new ArrayList<>();
        for (String part : (header == null ? "" : header).split(",")) {
            String[] pieces = part.trim().split(";");
            String tag = pieces[0].trim();
            if (tag.isEmpty() || tag.equals("*")) {
                continue;
            }
            double q = 1.0;
            for (int i = 1; i < pieces.length; i++) {
                String p = pieces[i].trim();
                if (p.startsWith("q=")) {
                    try {
                        q = Double.parseDouble(p.substring(2));
                    } catch (NumberFormatException e) {
                        q = 0;
                    }
                }
            }
            entries.add(new java.util.AbstractMap.SimpleEntry<>(tag, q));
        }
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue())); // stable
        List<String> tags = new ArrayList<>(entries.size());
        for (Map.Entry<String, Double> e : entries) {
            tags.add(e.getKey());
        }
        return tags;
    }

    // ------------------------------------------------------------------ //
    // resource-bundle access
    // ------------------------------------------------------------------ //

    /**
     * Read a value from an ICU resource bundle, falling back locale → language → root.
     *
     * @param bundleName one of the {@link Bundle} constants.
     * @return a leaf {@link String}, a nested {@link UResourceBundle}, or
     *   {@code null} if the path is absent. Mirrors the PHP port's {@code get()}.
     */
    public Object get(String bundleName, String... path) {
        for (String loc : new String[]{locale, uloc.getLanguage(), "root"}) {
            Object result = extract(loc, bundleName, path);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static Object extract(String localeId, String bundleName, String[] path) {
        try {
            UResourceBundle current = UResourceBundle.getBundleInstance(bundleName, new ULocale(localeId));
            for (String key : path) {
                current = current.get(key);
            }
            // Collapse a string leaf to its value for convenience.
            return current.getType() == UResourceBundle.STRING ? current.getString() : current;
        } catch (RuntimeException e) { // includes MissingResourceException
            return null;
        }
    }

    // ------------------------------------------------------------------ //
    // key -> value lookups
    // ------------------------------------------------------------------ //

    /** Localised name of this locale's own language. */
    public String language() {
        return language(locale);
    }

    /** Localised language name ({@code "en"} → {@code "English"}). */
    public String language(String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        return new ULocale(code.replace('-', '_')).getDisplayLanguage(uloc);
    }

    /** Localised name of this locale's own region. */
    public String country() {
        return country(subtags.region);
    }

    /** Localised country/region name ({@code "AU"} → {@code "Australia"}). */
    public String country(String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        // ULocale needs a region context: prefix a bare region with "_".
        String id = code.replace('-', '_');
        if (!id.contains("_")) {
            id = "_" + id;
        }
        return new ULocale(id).getDisplayCountry(uloc);
    }

    /** Localised name of this locale's own script ({@code ""} when it has none). */
    public String script() {
        return script(subtags.script);
    }

    /**
     * Localised script name ({@code "Hans"} → {@code "Simplified"}).
     *
     * <p>Uses the contextual CLDR {@code Scripts} table like the PHP port and
     * JS's {@code Intl.DisplayNames}. ICU4J only exposes that table through the
     * deprecated {@code getDisplayScriptInContext}; every non-deprecated API
     * returns the stand-alone variant ({@code "Simplified Han"}) and would
     * disagree with the other three ports.
     */
    @SuppressWarnings("deprecation")
    public String script(String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        String titled = Character.toUpperCase(code.charAt(0)) + code.substring(1).toLowerCase(Locale.ROOT);
        return ULocale.getDisplayScriptInContext("und_" + titled, uloc);
    }

    /** Localised calendar name ({@code "buddhist"} → {@code "Buddhist Calendar"}). */
    public String calendar(String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        return LocaleDisplayNames.getInstance(uloc).keyValueDisplayName("calendar", code);
    }

    /** Text direction of this locale: {@code "rtl"} or {@code "ltr"}. */
    public String direction() {
        return direction(locale);
    }

    /**
     * Text direction of a language: {@code "rtl"} or {@code "ltr"}.
     *
     * <p>Script-based (via likely-subtags maximisation), so minority RTL
     * languages with no locale-level layout data are detected too.
     */
    public String direction(String language) {
        if (language == null || language.isEmpty()) {
            return "ltr";
        }
        try {
            return new ULocale(language.replace('-', '_')).isRightToLeft() ? "rtl" : "ltr";
        } catch (RuntimeException e) {
            return "ltr";
        }
    }

    /** Country flag emoji for this locale's region ({@code ""} when it has none). */
    public String flag() {
        return flag(subtags.region);
    }

    /** Country flag emoji for a region ({@code "AU"} → {@code "🇦🇺"}). Pure codepoint math. */
    public String flag(String country) {
        String cc = country == null ? "" : country.toUpperCase(Locale.ROOT);
        if (cc.length() != 2 || cc.charAt(0) < 'A' || cc.charAt(0) > 'Z' || cc.charAt(1) < 'A' || cc.charAt(1) > 'Z') {
            return "";
        }
        // 0x1F1E6 (regional indicator A) minus 'A'.
        int offset = 0x1F1E6 - 'A';
        return new String(Character.toChars(cc.charAt(0) + offset))
                + new String(Character.toChars(cc.charAt(1) + offset));
    }

    /** Localised name of the {@code currency} modifier's currency. */
    public String currency() {
        return currency(null, false, false);
    }

    /** Localised currency name ({@code "AUD"} → {@code "Australian Dollar"}). */
    public String currency(String code) {
        return currency(code, false, false);
    }

    /**
     * Localised currency name (default) or symbol.
     *
     * @param code ISO 4217 code; {@code null} falls back to the {@code currency} modifier.
     * @param symbol return the disambiguated symbol ({@code "A$"}) instead of the name.
     * @param strict throw on an unknown code instead of echoing it back.
     */
    public String currency(String code, boolean symbol, boolean strict) {
        String ccy = code != null ? code : modifiers.currency;
        ccy = ccy == null ? "" : ccy.toUpperCase(Locale.ROOT).trim();
        if (ccy.isEmpty()) {
            return "";
        }
        Currency cur;
        try {
            cur = Currency.getInstance(ccy);
        } catch (IllegalArgumentException e) {
            if (strict) {
                throw new InvalidArgumentException("\"" + ccy + "\" is not a valid currency code.");
            }
            return ccy;
        }
        // ICU falls back to the bare code when the Currencies table has no entry —
        // the same signal the other ports use to echo back / raise.
        if (strict && cur.getName(uloc, Currency.LONG_NAME, (boolean[]) null).equals(ccy)) {
            throw new InvalidArgumentException("\"" + ccy + "\" is not a valid currency code.");
        }
        return cur.getName(uloc, symbol ? Currency.SYMBOL_NAME : Currency.LONG_NAME, (boolean[]) null);
    }

    // ------------------------------------------------------------------ //
    // numbers
    // ------------------------------------------------------------------ //

    /** Format a number with the locale's default decimal format. */
    public String number(double value) {
        return number(value, null);
    }

    /**
     * Format a number with optional portable controls — {@code minimumIntegerDigits},
     * {@code minimum}/{@code maximumFractionDigits}, {@code minimum}/{@code maximumSignificantDigits},
     * {@code roundingMode}, {@code roundingIncrement}, {@code useGrouping}. The JS-only
     * options ({@code signDisplay}, {@code notation}, …) are accepted but ignored.
     */
    public String number(double value, Map<String, Object> options) {
        NumberFormat fmt = NumberFormat.getInstance(uloc);
        applyNumberOptions(fmt, options);
        return fmt.format(value);
    }

    /** Format a fraction as a percentage ({@code 0.2} → {@code "20%"}). */
    public String percentage(double value) {
        return percentage(value, 3, null);
    }

    /** Format a fraction as a percentage with at most {@code precision} fraction digits. */
    public String percentage(double value, int precision) {
        return percentage(value, precision, null);
    }

    /** Format a fraction as a percentage; see {@link #number(double, Map)} for the options. */
    public String percentage(double value, int precision, Map<String, Object> options) {
        NumberFormat fmt = NumberFormat.getPercentInstance(uloc);
        fmt.setMaximumFractionDigits(precision);
        applyNumberOptions(fmt, options);
        return fmt.format(value);
    }

    /**
     * Formats a monetary value using the {@code currency} modifier (inferred
     * from the region when the locale has one).
     *
     * @return the formatted amount, or {@code ""} when no currency is available.
     */
    public String money(double value) {
        return money(value, null, null, false);
    }

    /** Formats a monetary value in the given ISO 4217 currency. */
    public String money(double value, String code) {
        return money(value, code, null, false);
    }

    /**
     * Formats a monetary value.
     *
     * @param code ISO 4217 code; {@code null} falls back to the {@code currency} modifier.
     * @param precision exact fraction digits, or {@code null} for the currency's default.
     * @param strict throw instead of returning {@code ""} when no currency is available.
     */
    public String money(double value, String code, Integer precision, boolean strict) {
        return money(value, code, precision, strict, null);
    }

    /** Formats a monetary value; see {@link #number(double, Map)} for the options. */
    public String money(double value, String code, Integer precision, boolean strict,
                        Map<String, Object> options) {
        String ccy = code != null ? code : modifiers.currency;
        ccy = ccy == null ? "" : ccy.toUpperCase(Locale.ROOT);
        if (ccy.isEmpty()) {
            if (strict) {
                throw new InvalidArgumentException(
                        "No currency provided. Pass a code or set the `currency` modifier.");
            }
            return "";
        }
        // A malformed code makes ICU throw a raw IllegalArgumentException; reject it
        // up front so every port raises the same branded error.
        if (!CURRENCY_CODE.matcher(ccy).matches()) {
            throw new InvalidArgumentException("\"" + ccy + "\" is not a valid currency code.");
        }
        NumberFormat fmt = NumberFormat.getCurrencyInstance(uloc);
        fmt.setCurrency(Currency.getInstance(ccy));
        if (precision != null) {
            fmt.setMinimumFractionDigits(precision);
            fmt.setMaximumFractionDigits(precision);
        }
        applyNumberOptions(fmt, options);
        return fmt.format(value);
    }

    /**
     * Returns a localised number symbol ({@code "decimal"}, {@code "percent"}, …).
     *
     * <p>Accepts any {@code DecimalFormatSymbols} name (case-insensitive; the
     * {@code _symbol}/{@code _separator}/{@code _sign} suffixes and separators
     * are ignored).
     */
    public String symbol(String name) {
        String key = name.toLowerCase(Locale.ROOT).replaceAll("[_\\s-]", "");
        for (String suffix : new String[]{"symbol", "separator", "sign"}) {
            if (key.endsWith(suffix) && key.length() > suffix.length()) {
                key = key.substring(0, key.length() - suffix.length());
            }
        }
        DecimalFormatSymbols dfs = new DecimalFormatSymbols(uloc);
        switch (key) {
            case "decimal": return dfs.getDecimalSeparatorString();
            case "grouping": case "group": return dfs.getGroupingSeparatorString();
            case "pattern": return String.valueOf(dfs.getPatternSeparator());
            case "percent": return dfs.getPercentString();
            case "permill": case "permille": return dfs.getPerMillString();
            case "minus": return dfs.getMinusSignString();
            case "plus": return dfs.getPlusSignString();
            case "currency": return dfs.getCurrencySymbol();
            case "intlcurrency": return dfs.getInternationalCurrencySymbol();
            case "monetary": return dfs.getMonetaryDecimalSeparatorString();
            case "exponential": case "exponent": return dfs.getExponentSeparator();
            case "nan": return dfs.getNaN();
            case "infinity": return dfs.getInfinity();
            case "digit": return String.valueOf(dfs.getDigit());
            case "zerodigit": case "zero": return String.valueOf(dfs.getZeroDigit());
            case "significantdigit": case "significant": return String.valueOf(dfs.getSignificantDigit());
            case "padescape": case "pad": return String.valueOf(dfs.getPadEscape());
            default:
                throw new InvalidArgumentException("\"" + name + "\" is not a valid number-symbol name.");
        }
    }

    /** Format a measurement with a localised unit, full width. */
    public String unit(String category, String unit, double value) {
        return unit(category, unit, value, "full");
    }

    /**
     * Format a measurement with a localised unit ({@code 2.19} gigabytes).
     *
     * @param category informational grouping (e.g. {@code "digital"}); not required by ICU.
     * @param unit ICU unit identifier, e.g. {@code "gigabyte"}, {@code "celsius"},
     *   {@code "mile-per-hour"}.
     * @param width {@code full}/{@code long} → wide, {@code medium} → short, {@code short} → narrow.
     */
    public String unit(String category, String unit, double value, String width) {
        // category is accepted for descriptive parity; ICU derives it from the unit.
        MeasureUnit measureUnit;
        try {
            measureUnit = MeasureUnit.forIdentifier(unit);
        } catch (RuntimeException e) {
            throw new InvalidArgumentException("\"" + unit + "\" is not a unit supported by ICU.");
        }
        return MeasureFormat.getInstance(uloc, measureWidth(width)).format(new Measure(value, measureUnit));
    }

    /** Scientific notation ({@code 12345} → {@code "1.2345E4"}). */
    public String scientific(double value) {
        return NumberFormat.getScientificInstance(uloc).format(value);
    }

    /** Compact notation ({@code 1200} → {@code "1.2K"}). */
    public String compact(double value) {
        return compact(value, "short");
    }

    /** Compact notation; {@code "full"}/{@code "long"} width gives {@code "1.2 thousand"}. */
    public String compact(double value, String width) {
        Notation notation = "full".equals(width) || "long".equals(width)
                ? Notation.compactLong()
                : Notation.compactShort();
        return NumberFormatter.withLocale(uloc).notation(notation).format(value).toString();
    }

    /** Ordinal text ({@code 1} → {@code "1st"}). Uses ICU RBNF. */
    public String ordinal(long number) {
        return new RuleBasedNumberFormat(uloc, RuleBasedNumberFormat.ORDINAL).format(number);
    }

    /** Spell a number out ({@code 42} → {@code "forty-two"}). Uses ICU RBNF. */
    public String spellout(double number) {
        return new RuleBasedNumberFormat(uloc, RuleBasedNumberFormat.SPELLOUT).format(number);
    }

    /** Format an undirected duration in seconds as the clock form ({@code "339:17:20"}). */
    public String duration(double seconds) {
        return duration(seconds, false);
    }

    /**
     * Format an undirected duration in seconds.
     *
     * @param withWords spell the units out where the locale's RBNF has a
     *   {@code %with-words} ruleset. Directed ("3 days ago") form:
     *   {@link #relativeDuration(double, String)}.
     */
    @SuppressWarnings("deprecation") // RBNF DURATION rules are deprecated in ICU, but they
    // are what produces the "339:17:20" clock form every other port emits (via the
    // same rules); MeasureFormat has no equivalent for a bare seconds count.
    public String duration(double seconds, boolean withWords) {
        RuleBasedNumberFormat fmt = new RuleBasedNumberFormat(uloc, RuleBasedNumberFormat.DURATION);
        if (withWords) {
            try {
                fmt.setDefaultRuleSet("%with-words");
            } catch (RuntimeException e) {
                // The locale's duration rules have no %with-words set; keep the default.
            }
        }
        return fmt.format(seconds);
    }

    /** Format a multi-unit duration map with abbreviated units ({@code "3 hr, 5 min"}). */
    public String duration(Map<String, ?> parts) {
        return duration(parts, false);
    }

    /**
     * Format a multi-unit duration ({@code {hours: 3, minutes: 5}} →
     * {@code "3 hours, 5 minutes"} with {@code withWords}). Accepted units:
     * years, months, weeks, days, hours, minutes, seconds, milliseconds.
     */
    public String duration(Map<String, ?> parts, boolean withWords) {
        MeasureFormat fmt = MeasureFormat.getInstance(uloc,
                withWords ? MeasureFormat.FormatWidth.WIDE : MeasureFormat.FormatWidth.SHORT);
        List<String> pieces = new ArrayList<>();
        for (String unit : DURATION_UNITS) {
            Number amount = (Number) parts.get(unit);
            if (amount != null && amount.doubleValue() != 0) {
                MeasureUnit mu = MeasureUnit.forIdentifier(unit.substring(0, unit.length() - 1));
                pieces.add(fmt.format(new Measure(amount.doubleValue(), mu)));
            }
        }
        if (pieces.isEmpty()) {
            return "";
        }
        return ListFormatter.getInstance(uloc, ListFormatter.Type.UNITS,
                withWords ? ListFormatter.Width.WIDE : ListFormatter.Width.SHORT).format(pieces);
    }

    // ------------------------------------------------------------------ //
    // dates & times
    // ------------------------------------------------------------------ //

    /**
     * Format a date and/or time using the locale's conventions.
     *
     * @param dateWidth / timeWidth: {@code none}/{@code short}/{@code medium}/{@code long}/{@code full}.
     */
    public String moment(Date value, String dateWidth, String timeWidth) {
        int d = dateStyle(dateWidth);
        int t = dateStyle(timeWidth);
        ULocale loc = calendarLocale(null);
        DateFormat fmt;
        if (d < 0 && t < 0) {
            return "";
        } else if (t < 0) {
            fmt = DateFormat.getDateInstance(d, loc);
        } else if (d < 0) {
            fmt = DateFormat.getTimeInstance(t, loc);
        } else {
            fmt = DateFormat.getDateTimeInstance(d, t, loc);
        }
        if (modifiers.timeZone != null) {
            fmt.setTimeZone(TimeZone.getTimeZone(modifiers.timeZone));
        }
        return fmt.format(value);
    }

    /** See {@link #moment(Date, String, String)}. */
    public String moment(Instant value, String dateWidth, String timeWidth) {
        return moment(Date.from(value), dateWidth, timeWidth);
    }

    /** Format just the date part of a moment (short width). */
    public String date(Date value) {
        return moment(value, "short", "none");
    }

    /** Format just the date part of a moment. */
    public String date(Date value, String width) {
        return moment(value, width, "none");
    }

    /** See {@link #date(Date, String)}. */
    public String date(Instant value, String width) {
        return moment(Date.from(value), width, "none");
    }

    /** Format just the time (clock) part of a moment (short width). */
    public String time(Date value) {
        return moment(value, "none", "short");
    }

    /** Format just the time (clock) part of a moment. */
    public String time(Date value, String width) {
        return moment(value, "none", width);
    }

    /** See {@link #time(Date, String)}. */
    public String time(Instant value, String width) {
        return moment(Date.from(value), "none", width);
    }

    /** Format a moment with a raw ICU pattern ({@code "yyyy-MM-dd"}). */
    public String formatMoment(Date value, String pattern) {
        return formatMoment(value, pattern, null);
    }

    /** Format a moment with a raw ICU pattern, forcing a calendar ({@code "gregorian"}, …). */
    public String formatMoment(Date value, String pattern, String calendar) {
        SimpleDateFormat fmt = new SimpleDateFormat(pattern, calendarLocale(calendar));
        if (modifiers.timeZone != null) {
            fmt.setTimeZone(TimeZone.getTimeZone(modifiers.timeZone));
        }
        return fmt.format(value);
    }

    /** Format a moment range with medium-width dates ({@code "Feb 2 – 5, 2020"}). */
    public String dateRange(Date start, Date end) {
        return dateRange(start, end, "medium", "none");
    }

    /** Format a moment range; supports the documented width combinations only. */
    public String dateRange(Date start, Date end, String dateWidth, String timeWidth) {
        dateStyle(dateWidth);
        dateStyle(timeWidth);
        String skeleton = RANGE_SKELETONS.get(dateWidth + "|" + timeWidth);
        if (skeleton == null) {
            throw new InvalidArgumentException("dateRange supports the documented width combinations only.");
        }
        DateIntervalFormat fmt = DateIntervalFormat.getInstance(skeleton, calendarLocale(null));
        return fmt.format(new DateInterval(start.getTime(), end.getTime()));
    }

    // ------------------------------------------------------------------ //
    // collation
    // ------------------------------------------------------------------ //

    /** Locale-aware comparison of two strings (negative / zero / positive). */
    public int compare(String a, String b) {
        return compare(a, b, null);
    }

    /**
     * Locale-aware comparison with optional collation tailoring — {@code numeric}
     * (boolean) and {@code caseFirst} ({@code "upper"}/{@code "lower"}/{@code "false"}).
     */
    public int compare(String a, String b, Map<String, Object> options) {
        Collator collator = Collator.getInstance(uloc);
        applyCollationOptions(collator, options);
        return collator.compare(a, b);
    }

    /** A new list sorted by the locale's collation rules. */
    public List<String> sort(Collection<String> items) {
        return sort(items, (Map<String, Object>) null);
    }

    /** A new sorted list with optional collation tailoring (see {@link #compare(String, String, Map)}). */
    public List<String> sort(Collection<String> items, Map<String, Object> options) {
        Collator collator = Collator.getInstance(uloc);
        applyCollationOptions(collator, options);
        List<String> out = new ArrayList<>(items);
        out.sort(collator);
        return out;
    }

    /**
     * Sort a collection of arbitrary objects by a string key, using the locale's
     * collation rules. Mirrors PHP's {@code sort($items, $key)} overload.
     *
     * @param key extracts the string to sort each item by.
     */
    public <T> List<T> sort(Collection<T> items, Function<T, String> key) {
        return sort(items, key, null);
    }

    /** Sort by key with optional collation tailoring (see {@link #compare(String, String, Map)}). */
    public <T> List<T> sort(Collection<T> items, Function<T, String> key, Map<String, Object> options) {
        Collator collator = Collator.getInstance(uloc);
        applyCollationOptions(collator, options);
        List<T> out = new ArrayList<>(items);
        out.sort((a, b) -> collator.compare(key.apply(a), key.apply(b)));
        return out;
    }

    /** Locale-aware substring test ignoring case and accents. */
    public boolean contains(String haystack, String needle) {
        return contains(haystack, needle, "base", null);
    }

    /** Locale-aware substring test with an explicit sensitivity. */
    public boolean contains(String haystack, String needle, String sensitivity) {
        return contains(haystack, needle, sensitivity, null);
    }

    /**
     * Locale-aware substring test (accents/case can be ignored).
     *
     * @param sensitivity {@code base} (ignore case & accents), {@code accent},
     *   {@code case}, or {@code variant} (exact).
     */
    public boolean contains(String haystack, String needle, String sensitivity, Map<String, Object> options) {
        if (needle.isEmpty()) {
            return true;
        }
        Collator collator = Collator.getInstance(uloc);
        // Set strength before applying user options so that caseFirst/numeric
        // tailoring from options is always applied on top of the sensitivity baseline.
        switch (sensitivity) {
            case "base":
                collator.setStrength(Collator.PRIMARY);
                break;
            case "accent":
                collator.setStrength(Collator.SECONDARY);
                break;
            case "case":
                // Distinguish case but not accents: PRIMARY strength so "à" = "a",
                // plus case level so "a" ≠ "A". Mirrors PHP's Collator::PRIMARY +
                // CASE_LEVEL ON, and matches the JS Intl.Collator "case" definition.
                collator.setStrength(Collator.PRIMARY);
                ((RuleBasedCollator) collator).setCaseLevel(true);
                break;
            case "variant":
                // All differences matter: base, accents, case. TERTIARY matches PHP
                // and the JS Intl definition; IDENTICAL would additionally reject
                // strings that differ only in Unicode normalisation form.
                collator.setStrength(Collator.TERTIARY);
                break;
            default:
                throw new InvalidArgumentException("\"" + sensitivity + "\" is not a valid sensitivity.");
        }
        applyCollationOptions(collator, options);
        List<String> hay = graphemes(haystack);
        int needLen = graphemes(needle).size();
        for (int i = 0; i + needLen <= hay.size(); i++) {
            if (collator.compare(String.join("", hay.subList(i, i + needLen)), needle) == 0) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ //
    // text segmentation
    // ------------------------------------------------------------------ //

    /** Split text into grapheme clusters (combining marks / emoji stay intact). */
    public List<String> splitGraphemes(String text) {
        return graphemes(text);
    }

    /** Split text into words (drops whitespace/punctuation). */
    public List<String> splitWords(String text) {
        BreakIterator it = BreakIterator.getWordInstance(uloc);
        it.setText(text);
        List<String> out = new ArrayList<>();
        int start = it.first();
        for (int end = it.next(); end != BreakIterator.DONE; start = end, end = it.next()) {
            if (it.getRuleStatus() != BreakIterator.WORD_NONE) {
                out.add(text.substring(start, end));
            }
        }
        return out;
    }

    /** Split text into sentences using the locale's boundary rules. */
    public List<String> splitSentences(String text) {
        BreakIterator it = BreakIterator.getSentenceInstance(uloc);
        it.setText(text);
        List<String> out = new ArrayList<>();
        int start = it.first();
        for (int end = it.next(); end != BreakIterator.DONE; start = end, end = it.next()) {
            String piece = text.substring(start, end).trim();
            if (!piece.isEmpty()) {
                out.add(piece);
            }
        }
        return out;
    }

    /** Truncate to at most {@code maxGraphemes} graphemes on a word boundary. */
    public String ellipsize(String text, int maxGraphemes) {
        return ellipsize(text, maxGraphemes, "…");
    }

    /** Truncate to at most {@code maxGraphemes} graphemes with a custom ellipsis. */
    public String ellipsize(String text, int maxGraphemes, String ellipsis) {
        List<String> graphemes = graphemes(text);
        if (graphemes.size() <= maxGraphemes) {
            return text;
        }
        int budget = Math.max(0, maxGraphemes - graphemes(ellipsis).size());
        String head = String.join("", graphemes.subList(0, budget));
        // Prefer to cut at the last word-break boundary that still fits (like the
        // JS/PHP ports); fall back to a mid-word cut when no boundary fits.
        BreakIterator it = BreakIterator.getWordInstance(uloc);
        it.setText(head);
        int boundary = it.preceding(head.length());
        if (boundary > 0) {
            String cut = head.substring(0, boundary).stripTrailing();
            if (!cut.isEmpty()) {
                return cut + ellipsis;
            }
        }
        return head.stripTrailing() + ellipsis;
    }

    // ------------------------------------------------------------------ //
    // messages, plurals, lists
    // ------------------------------------------------------------------ //

    /** Format an ICU MessageFormat pattern with named placeholders. */
    public String message(String pattern, Map<String, Object> args) {
        return new MessageFormat(pattern, uloc).format(args);
    }

    /** Format an ICU MessageFormat pattern with positional ({@code {0}}) placeholders. */
    public String message(String pattern, Object... args) {
        return new MessageFormat(pattern, uloc).format(args);
    }

    /** The LDML cardinal plural category for a value ({@code 1} → {@code "one"}). */
    public String pluralCategory(double value) {
        return pluralCategory(value, false);
    }

    /** The LDML plural category; {@code ordinal} uses ordinal rules (1st/2nd/3rd …). */
    public String pluralCategory(double value, boolean ordinal) {
        PluralRules.PluralType type = ordinal ? PluralRules.PluralType.ORDINAL : PluralRules.PluralType.CARDINAL;
        return PluralRules.forLocale(uloc, type).select(value);
    }

    /** Join a list the locale's way ({@code "A, B, and C"}). */
    public String join(Collection<String> items) {
        return join(items, "conjunction", "full");
    }

    /** Join a list; {@code type} is {@code conjunction} (and), {@code disjunction} (or), or {@code unit}. */
    public String join(Collection<String> items, String type) {
        return join(items, type, "full");
    }

    /** Join a list the locale's way with an explicit type and width. */
    public String join(Collection<String> items, String type, String width) {
        ListFormatter.Type listType;
        switch (type == null ? "" : type) {
            case "conjunction": listType = ListFormatter.Type.AND; break;
            case "disjunction": listType = ListFormatter.Type.OR; break;
            case "unit": listType = ListFormatter.Type.UNITS; break;
            default:
                throw new InvalidArgumentException("\"" + type + "\" is not a valid list type.");
        }
        return ListFormatter.getInstance(uloc, listType, listWidth(width)).format(items);
    }

    /** Wrap text in the locale's quotation marks ({@code “x”} in en, {@code «x»} in fa). */
    public String quote(String text) {
        Object start = get(Bundle.LOCALE, "delimiters", "quotationStart");
        Object end = get(Bundle.LOCALE, "delimiters", "quotationEnd");
        return (start == null ? "\"" : start) + text + (end == null ? "\"" : end);
    }

    // ------------------------------------------------------------------ //
    // relative durations & ranges
    // ------------------------------------------------------------------ //

    /** Directed duration, always numeric ({@code (-3, "day")} → {@code "3 days ago"}). */
    public String relativeDuration(double amount, String unit) {
        return relativeDuration(amount, unit, "always");
    }

    /**
     * Render a directed duration.
     *
     * @param amount signed — negative = past ({@code "… ago"}), positive = future ({@code "in …"}).
     * @param unit {@code second}/{@code minute}/{@code hour}/{@code day}/{@code week}/{@code month}/{@code quarter}/{@code year}.
     * @param numeric {@code "always"} ({@code "1 day ago"}) or {@code "auto"}
     *   ({@code "yesterday"} — ICU4J exposes the word forms the Python port can't reach).
     */
    public String relativeDuration(double amount, String unit, String numeric) {
        RelativeDateTimeUnit rel = relativeUnit(unit);
        RelativeDateTimeFormatter fmt = RelativeDateTimeFormatter.getInstance(uloc);
        if ("auto".equals(numeric)) {
            return fmt.format(amount, rel);
        }
        if (!"always".equals(numeric)) {
            throw new InvalidArgumentException("\"" + numeric + "\" is not a valid numeric mode (use always/auto).");
        }
        return fmt.formatNumeric(amount, rel);
    }

    /** Directed duration between a moment and now, largest sensible unit, word forms allowed. */
    public String relativeDurationBetween(Date target) {
        return relativeDurationBetween(target, null, "auto");
    }

    /** Directed duration between two moments ({@code "in 5 days"}, {@code "3 days ago"}). */
    public String relativeDurationBetween(Date target, Date reference) {
        return relativeDurationBetween(target, reference, "auto");
    }

    /**
     * Directed duration <strong>between two moments</strong>, computed as
     * {@code target − reference} ({@code reference} defaults to now) and
     * rendered in the largest sensible unit.
     */
    public String relativeDurationBetween(Date target, Date reference, String numeric) {
        double ref = (reference == null ? System.currentTimeMillis() : reference.getTime()) / 1000.0;
        double amount = target.getTime() / 1000.0 - ref;
        double[] sizes = {60, 60, 24, 7, 4.34524, 12};
        String[] units = {"second", "minute", "hour", "day", "week", "month"};
        for (int i = 0; i < sizes.length; i++) {
            if (Math.abs(amount) < sizes[i]) {
                return relativeDuration(Math.round(amount), units[i], numeric);
            }
            amount /= sizes[i];
        }
        return relativeDuration(Math.round(amount), "year", numeric);
    }

    /** Format a numeric range ({@code "3–5"}). */
    public String numberRange(double start, double end) {
        return NumberRangeFormatter.withLocale(uloc).formatRange(start, end).toString();
    }

    /** Format a monetary range with the {@code currency} modifier ({@code ""} if none). */
    public String moneyRange(double start, double end) {
        return moneyRange(start, end, null);
    }

    /** Format a monetary range ({@code "$3.00 – $5.00"}). {@code ""} if no currency. */
    public String moneyRange(double start, double end, String code) {
        String ccy = code != null ? code : modifiers.currency;
        ccy = ccy == null ? "" : ccy.toUpperCase(Locale.ROOT);
        if (ccy.isEmpty()) {
            return "";
        }
        if (!CURRENCY_CODE.matcher(ccy).matches()) {
            throw new InvalidArgumentException("\"" + ccy + "\" is not a valid currency code.");
        }
        return NumberRangeFormatter.withLocale(uloc)
                .numberFormatterBoth(NumberFormatter.with().unit(Currency.getInstance(ccy)))
                .formatRange(start, end)
                .toString();
    }

    // ------------------------------------------------------------------ //
    // locale metadata
    // ------------------------------------------------------------------ //

    /** A new Cosmo with likely subtags added ({@code "en"} → {@code "en_Latn_US"}). */
    public Cosmo addLikelySubtags() {
        return new Cosmo(ULocale.addLikelySubtags(uloc).getName(), modifiers);
    }

    /** A new Cosmo with likely subtags removed ({@code "en_Latn_US"} → {@code "en"}). */
    public Cosmo removeLikelySubtags() {
        return new Cosmo(ULocale.minimizeSubtags(uloc).getName(), modifiers);
    }

    /** Localised month names (full width), following the active calendar. */
    public List<String> monthNames() {
        return monthNames("full");
    }

    /** Localised month names, following the active calendar (e.g. Persian for {@code fa_IR}). */
    public List<String> monthNames(String width) {
        ULocale loc = calendarLocale(null);
        DateFormatSymbols symbols = new DateFormatSymbols(Calendar.getInstance(loc), loc);
        return nonEmpty(symbols.getMonths(DateFormatSymbols.FORMAT, symbolWidth(width)));
    }

    /** Localised weekday names (full width), <strong>Sunday first</strong> (ICU symbol order). */
    public List<String> weekdayNames() {
        return weekdayNames("full");
    }

    /** Localised weekday names, <strong>Sunday first</strong> (ICU symbol order). */
    public List<String> weekdayNames(String width) {
        DateFormatSymbols symbols = new DateFormatSymbols(uloc);
        // ICU returns 8 entries with index 0 empty and 1..7 = Sunday..Saturday.
        return nonEmpty(symbols.getWeekdays(DateFormatSymbols.FORMAT, symbolWidth(width)));
    }

    /**
     * Week conventions of the locale's region: first day, minimal days, and —
     * unlike the Python port — the weekend days, via ICU's region week data.
     */
    public WeekInfo weekInfo() {
        String region = subtags.region.isEmpty()
                ? ULocale.addLikelySubtags(uloc).getCountry()
                : subtags.region;
        Calendar.WeekData wd = Calendar.getWeekDataForRegion(region.isEmpty() ? "001" : region);
        List<Integer> weekend = new ArrayList<>();
        // Whole days only; sub-day onset/cease offsets are ignored.
        for (int day = wd.weekendOnset; ; day = day % 7 + 1) {
            weekend.add(isoDay(day));
            if (day == wd.weekendCease) {
                break;
            }
        }
        return new WeekInfo(isoDay(wd.firstDayOfWeek), wd.minimalDaysInFirstWeek, weekend);
    }

    /** Display name of the {@code timeZone} modifier (or the system zone), long style. */
    public String timeZoneName() {
        return timeZoneName("long");
    }

    /**
     * Display name of the {@code timeZone} modifier (or the system zone).
     *
     * @param style {@code long}/{@code short}/{@code longOffset}/{@code shortOffset}/
     *   {@code longGeneric}/{@code shortGeneric}.
     */
    public String timeZoneName(String style) {
        TimeZone tz = modifiers.timeZone != null
                ? TimeZone.getTimeZone(modifiers.timeZone)
                : TimeZone.getDefault();
        int tzStyle;
        switch (style == null ? "" : style) {
            case "long": tzStyle = TimeZone.LONG; break;
            case "short": tzStyle = TimeZone.SHORT; break;
            case "longOffset": tzStyle = TimeZone.LONG_GMT; break;
            case "shortOffset": tzStyle = TimeZone.SHORT_GMT; break;
            case "longGeneric": tzStyle = TimeZone.LONG_GENERIC; break;
            case "shortGeneric": tzStyle = TimeZone.SHORT_GENERIC; break;
            default:
                throw new InvalidArgumentException("\"" + style + "\" is not a valid time-zone name style.");
        }
        return tz.getDisplayName(false, tzStyle, uloc);
    }

    /**
     * Generic localised display name — one entry point over the dedicated lookups.
     *
     * @param type {@code language}, {@code region}, {@code script}, {@code calendar},
     *   or {@code currency}.
     */
    public String displayName(String type, String code) {
        switch (type == null ? "" : type) {
            case "language": return language(code);
            case "region": return country(code);
            case "script": return script(code);
            case "calendar": return calendar(code);
            case "currency": return currency(code);
            default:
                throw new InvalidArgumentException("\"" + type + "\" is not a display-name type "
                        + "(use language/region/script/calendar/currency).");
        }
    }

    /**
     * Values the runtime's ICU supports for {@code key} (e.g. all IANA time zones).
     *
     * <p>Supported keys: {@code timeZone}, {@code collation}, {@code numberingSystem},
     * {@code unit}, {@code currency}, {@code calendar}. ICU4J can enumerate the last
     * two — an enumeration PyICU lacks, so the Python port raises for them.
     */
    public List<String> supportedValues(String key) {
        switch (key == null ? "" : key) {
            case "timeZone":
                return new ArrayList<>(Arrays.asList(TimeZone.getAvailableIDs()));
            case "collation":
                return new ArrayList<>(Arrays.asList(Collator.getKeywordValues("collation")));
            case "numberingSystem":
                return new ArrayList<>(Arrays.asList(NumberingSystem.getAvailableNames()));
            case "unit": {
                List<String> out = new ArrayList<>();
                for (String type : MeasureUnit.getAvailableTypes()) {
                    for (MeasureUnit u : MeasureUnit.getAvailable(type)) {
                        out.add(u.getSubtype());
                    }
                }
                return out;
            }
            case "currency":
                return new ArrayList<>(Arrays.asList(
                        Currency.getKeywordValuesForLocale("currency", ULocale.ROOT, false)));
            case "calendar":
                return new ArrayList<>(Arrays.asList(
                        Calendar.getKeywordValuesForLocale("calendar", ULocale.ROOT, false)));
            case "transliterator":
                return Collections.list(Transliterator.getAvailableIDs());
            default:
                throw new InvalidArgumentException("\"" + key + "\" is not a valid key "
                        + "(use timeZone/collation/numberingSystem/unit/currency/calendar/transliterator).");
        }
    }

    // ------------------------------------------------------------------ //
    // locale negotiation (LocaleMatcher — beyond the JS/PHP platforms)
    // ------------------------------------------------------------------ //

    /**
     * The supported locale that best serves this Cosmo's locale, using CLDR
     * language-distance data — e.g. {@code en_AU} picks {@code en-GB} over
     * {@code en-US}, and {@code sr-Latn} is served better by {@code hr} than
     * by {@code sr-Cyrl}. Falls back to the first supported locale when
     * nothing matches well.
     *
     * @return the matching entry exactly as it appears in {@code supported}.
     */
    public String bestMatch(Collection<String> supported) {
        Map<ULocale, String> originals = ulocales(supported);
        ULocale match = matcher(originals.keySet()).getBestMatch(uloc);
        return originals.get(match);
    }

    private static Map<ULocale, String> ulocales(Collection<String> supported) {
        if (supported == null || supported.isEmpty()) {
            throw new InvalidArgumentException("At least one supported locale is required.");
        }
        Map<ULocale, String> out = new LinkedHashMap<>();
        for (String tag : supported) {
            out.putIfAbsent(toULocale(tag), tag);
        }
        return out;
    }

    private static LocaleMatcher matcher(Collection<ULocale> supported) {
        // The default match (when nothing comes close) is the first supported locale.
        return LocaleMatcher.builder().setSupportedULocales(supported).build();
    }

    // ------------------------------------------------------------------ //
    // transliteration & spoof detection (no JS equivalent)
    // ------------------------------------------------------------------ //

    /**
     * Run an ICU transform over the text — script conversion, romanisation,
     * accent folding ({@code "Any-Latin; Latin-ASCII"} makes ASCII slugs).
     *
     * @param id a compound ICU transliterator id; see
     *   {@code supportedValues("transliterator")} for the building blocks.
     */
    public String transliterate(String text, String id) {
        Transliterator transform;
        try {
            transform = Transliterator.getInstance(id);
        } catch (RuntimeException e) {
            throw new InvalidArgumentException("\"" + id + "\" is not a valid transliterator id.");
        }
        return transform.transliterate(text);
    }

    /** Romanise text ({@code "Москва"} → {@code "Moskva"}); shorthand for {@code Any-Latin}. */
    public String romanize(String text) {
        return transliterate(text, "Any-Latin");
    }

    /**
     * Whether two strings are visually confusable ({@code "paypal"} vs a
     * Cyrillic {@code "раураl"}) per UTS #39. Locale-independent.
     */
    public boolean confusable(String a, String b) {
        return new SpoofChecker.Builder().build().areConfusable(a, b) != 0;
    }

    /**
     * Whether a string fails ICU's default spoof checks (mixed scripts,
     * restriction level, invisible characters) per UTS #39.
     */
    public boolean suspicious(String text) {
        return new SpoofChecker.Builder().build().failsChecks(text);
    }

    // ------------------------------------------------------------------ //
    // alphabetic index (contact-list buckets)
    // ------------------------------------------------------------------ //

    /**
     * Group strings under locale-correct index headers (A–Z in en, 가나다 in ko,
     * あかさ in ja, with the right under/overflow buckets). Buckets keep the
     * locale's label order; empty buckets are omitted; items are collated.
     */
    public Map<String, List<String>> indexBuckets(Collection<String> names) {
        AlphabeticIndex<String> index = new AlphabeticIndex<>(uloc);
        for (String name : names) {
            index.addRecord(name, name);
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (AlphabeticIndex.Bucket<String> bucket : index) {
            if (bucket.size() == 0) {
                continue;
            }
            List<String> items = new ArrayList<>(bucket.size());
            for (AlphabeticIndex.Record<String> record : bucket) {
                items.add(record.getData());
            }
            out.put(bucket.getLabel(), items);
        }
        return out;
    }

    // ------------------------------------------------------------------ //
    // locale-aware parsing (the inverse formatters; JS Intl cannot parse)
    // ------------------------------------------------------------------ //

    /** Parse a localised number ({@code "1.234,56"} in de → {@code 1234.56}). */
    public double parseNumber(String text) {
        try {
            return NumberFormat.getInstance(uloc).parse(text).doubleValue();
        } catch (ParseException e) {
            throw new InvalidArgumentException("\"" + text + "\" cannot be parsed as a number in " + locale + ".");
        }
    }

    /**
     * Parse a localised monetary string ({@code "$12.30"} → 12.3 USD).
     *
     * @return an ICU {@link CurrencyAmount}: {@code getNumber()} and {@code getCurrency()}.
     */
    public CurrencyAmount parseMoney(String text) {
        CurrencyAmount amount =
                NumberFormat.getCurrencyInstance(uloc).parseCurrency(text, new ParsePosition(0));
        if (amount == null) {
            throw new InvalidArgumentException("\"" + text + "\" cannot be parsed as money in " + locale + ".");
        }
        return amount;
    }

    /** Parse a short-width localised date. */
    public Date parseDate(String text) {
        return parseDate(text, "short");
    }

    /** Parse a localised date written at the given width ({@code short}…{@code full}). */
    public Date parseDate(String text, String width) {
        int style = dateStyle(width);
        if (style < 0) {
            throw invalidWidth(width);
        }
        DateFormat fmt = DateFormat.getDateInstance(style, calendarLocale(null));
        if (modifiers.timeZone != null) {
            fmt.setTimeZone(TimeZone.getTimeZone(modifiers.timeZone));
        }
        try {
            return fmt.parse(text);
        } catch (ParseException e) {
            throw new InvalidArgumentException("\"" + text + "\" cannot be parsed as a date in " + locale + ".");
        }
    }

    /** Parse a moment with a raw ICU pattern (the inverse of {@link #formatMoment}). */
    public Date parseMoment(String text, String pattern) {
        SimpleDateFormat fmt = new SimpleDateFormat(pattern, calendarLocale(null));
        if (modifiers.timeZone != null) {
            fmt.setTimeZone(TimeZone.getTimeZone(modifiers.timeZone));
        }
        try {
            return fmt.parse(text);
        } catch (ParseException e) {
            throw new InvalidArgumentException("\"" + text + "\" does not match the pattern \"" + pattern + "\".");
        }
    }

    // ------------------------------------------------------------------ //
    // person names (ICU 73+; no other port's platform exposes this yet)
    // ------------------------------------------------------------------ //

    /** Format a person's name with medium length and formal formality. */
    public String personName(Map<String, String> fields) {
        return personName(fields, "medium", "formal");
    }

    /**
     * Locale-aware person-name formatting from CLDR person-name data:
     * surname-first locales (ja/zh/hu), locale-correct initials and spacing,
     * formality variants — zero hardcoded rules.
     *
     * <p><strong>ICU technology preview:</strong> the underlying API may shift
     * between ICU releases; this method's own surface will stay stable.
     *
     * @param fields name fields — {@code given}, {@code surname}, and optionally
     *   {@code title}, {@code given2}, {@code surname2}, {@code generation},
     *   {@code credentials}, plus {@code locale}: the locale <em>of the name
     *   itself</em> (e.g. {@code "ja"} so 山田/太郎 renders surname-first).
     * @param length {@code short}/{@code medium}/{@code long}.
     * @param formality {@code formal}/{@code informal}.
     */
    @SuppressWarnings("deprecation") // the PersonName API is an ICU technology preview;
    // ICU annotates it deprecated until it graduates. There is no stable alternative.
    public String personName(Map<String, String> fields, String length, String formality) {
        SimplePersonName.Builder name = SimplePersonName.builder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (entry.getKey().equals("locale")) {
                name.setLocale(toULocale(entry.getValue()).toLocale());
                continue;
            }
            PersonName.NameField field;
            try {
                field = PersonName.NameField.valueOf(entry.getKey().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new InvalidArgumentException("\"" + entry.getKey() + "\" is not a person-name field "
                        + "(use title/given/given2/surname/surname2/generation/credentials/locale).");
            }
            name.addField(field, Collections.emptyList(), entry.getValue());
        }
        PersonNameFormatter.Length nameLength;
        switch (length == null ? "" : length) {
            case "short": nameLength = PersonNameFormatter.Length.SHORT; break;
            case "medium": nameLength = PersonNameFormatter.Length.MEDIUM; break;
            case "long": nameLength = PersonNameFormatter.Length.LONG; break;
            default:
                throw new InvalidArgumentException("\"" + length + "\" is not a valid name length (use short/medium/long).");
        }
        PersonNameFormatter.Formality nameFormality;
        switch (formality == null ? "" : formality) {
            case "formal": nameFormality = PersonNameFormatter.Formality.FORMAL; break;
            case "informal": nameFormality = PersonNameFormatter.Formality.INFORMAL; break;
            default:
                throw new InvalidArgumentException("\"" + formality + "\" is not a valid formality (use formal/informal).");
        }
        PersonNameFormatter fmt = PersonNameFormatter.builder()
                .setLocale(uloc.toLocale())
                .setLength(nameLength)
                .setFormality(nameFormality)
                .build();
        return fmt.formatToString(name.build());
    }

    // ------------------------------------------------------------------ //
    // case transforms
    // ------------------------------------------------------------------ //

    /** Locale-aware upper-casing (e.g. Turkish dotted/dotless I). */
    public String upper(String text) {
        return UCharacter.toUpperCase(uloc, text);
    }

    /** Locale-aware lower-casing. */
    public String lower(String text) {
        return UCharacter.toLowerCase(uloc, text);
    }

    // ------------------------------------------------------------------ //
    // helpers
    // ------------------------------------------------------------------ //

    /** This locale with the {@code calendar} override (or modifier) applied as an ICU keyword. */
    private ULocale calendarLocale(String calendar) {
        String cal = calendar != null ? calendar : modifiers.calendar;
        if (cal == null) {
            return uloc;
        }
        return new ULocale(ULocale.setKeywordValue(locale, "calendar", cal));
    }

    /** Apply a portable number-options map to an ICU NumberFormat. */
    private static void applyNumberOptions(NumberFormat fmt, Map<String, Object> options) {
        // ICU's legacy formatters default to HALF_EVEN (banker's rounding) whereas the
        // JS port inherits Intl.NumberFormat's HALF_EXPAND (round half away from zero).
        // Default to HALF_EXPAND so money/number/percentage round identically across
        // ports; an explicit roundingMode option below still overrides this.
        fmt.setRoundingMode(com.ibm.icu.math.BigDecimal.ROUND_HALF_UP);
        if (options == null || options.isEmpty()) {
            return;
        }
        for (String key : options.keySet()) {
            if (!KNOWN_NUMBER_OPTIONS.contains(key)) {
                throw new InvalidArgumentException("\"" + key + "\" is not a valid number option.");
            }
        }
        if (options.containsKey("minimumIntegerDigits")) {
            fmt.setMinimumIntegerDigits(asInt(options.get("minimumIntegerDigits")));
        }
        if (options.containsKey("minimumFractionDigits")) {
            fmt.setMinimumFractionDigits(asInt(options.get("minimumFractionDigits")));
        }
        if (options.containsKey("maximumFractionDigits")) {
            fmt.setMaximumFractionDigits(asInt(options.get("maximumFractionDigits")));
        }
        DecimalFormat df = fmt instanceof DecimalFormat ? (DecimalFormat) fmt : null;
        // ICU treats significant-digit limits as mutually exclusive with fraction
        // digits; the setters turn the significant-digit mode on.
        if (df != null
                && (options.containsKey("minimumSignificantDigits") || options.containsKey("maximumSignificantDigits"))) {
            df.setSignificantDigitsUsed(true);
            if (options.containsKey("minimumSignificantDigits")) {
                df.setMinimumSignificantDigits(asInt(options.get("minimumSignificantDigits")));
            }
            if (options.containsKey("maximumSignificantDigits")) {
                df.setMaximumSignificantDigits(asInt(options.get("maximumSignificantDigits")));
            }
        }
        if (options.containsKey("useGrouping")) {
            fmt.setGroupingUsed(truthy(options.get("useGrouping")));
        }
        Object increment = options.get("roundingIncrement");
        if (increment != null && df != null) {
            // Match the JS/Intl contract: the increment is expressed in units of the
            // last fraction digit (e.g. increment 5 at 2 fraction digits → step 0.05),
            // whereas ICU's setRoundingIncrement takes the literal step value.
            int scale = options.containsKey("maximumFractionDigits")
                    ? asInt(options.get("maximumFractionDigits")) : 0;
            // stripTrailingZeros: ICU adopts the increment's scale for display, so
            // 5.0 -> "0.050" would force three fraction digits instead of two.
            df.setRoundingIncrement(java.math.BigDecimal.valueOf(((Number) increment).doubleValue())
                    .movePointLeft(scale).stripTrailingZeros());
        }
        Object mode = options.get("roundingMode");
        if (mode != null) {
            fmt.setRoundingMode(roundingMode((String) mode));
        }
    }

    private static int roundingMode(String name) {
        switch (name) {
            case "ceil": return com.ibm.icu.math.BigDecimal.ROUND_CEILING;
            case "floor": return com.ibm.icu.math.BigDecimal.ROUND_FLOOR;
            case "expand": return com.ibm.icu.math.BigDecimal.ROUND_UP;
            case "trunc": return com.ibm.icu.math.BigDecimal.ROUND_DOWN;
            case "halfExpand": return com.ibm.icu.math.BigDecimal.ROUND_HALF_UP;
            case "halfTrunc": return com.ibm.icu.math.BigDecimal.ROUND_HALF_DOWN;
            case "halfEven": return com.ibm.icu.math.BigDecimal.ROUND_HALF_EVEN;
            default:
                throw new InvalidArgumentException("\"" + name + "\" is not a valid rounding mode "
                        + "(use ceil/floor/expand/trunc/halfExpand/halfTrunc/halfEven).");
        }
    }

    /** Apply portable collation tailoring (numeric, caseFirst) to an ICU Collator. */
    private static void applyCollationOptions(Collator collator, Map<String, Object> options) {
        if (options == null || options.isEmpty()) {
            return;
        }
        for (String key : options.keySet()) {
            if (!KNOWN_COLLATION_OPTIONS.contains(key)) {
                throw new InvalidArgumentException("\"" + key + "\" is not a valid collation option.");
            }
        }
        RuleBasedCollator rbc = (RuleBasedCollator) collator;
        if (options.containsKey("numeric")) {
            rbc.setNumericCollation(truthy(options.get("numeric")));
        }
        Object caseFirst = options.get("caseFirst");
        if (caseFirst != null) {
            switch ((String) caseFirst) {
                case "upper": rbc.setUpperCaseFirst(true); break;
                case "lower": rbc.setLowerCaseFirst(true); break;
                case "false": rbc.setUpperCaseFirst(false); break;
                default:
                    throw new InvalidArgumentException("\"" + caseFirst + "\" is not a valid caseFirst value.");
            }
        }
    }

    private static int asInt(Object v) {
        return ((Number) v).intValue();
    }

    private static boolean truthy(Object v) {
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue() != 0;
        }
        if (v instanceof String) {
            return !((String) v).isEmpty();
        }
        return v != null;
    }

    /** Split into grapheme clusters; Java strings are UTF-16, matching ICU offsets. */
    private List<String> graphemes(String text) {
        BreakIterator it = BreakIterator.getCharacterInstance(uloc);
        it.setText(text);
        List<String> out = new ArrayList<>();
        int start = it.first();
        for (int end = it.next(); end != BreakIterator.DONE; start = end, end = it.next()) {
            out.add(text.substring(start, end));
        }
        return out;
    }

    private static MeasureFormat.FormatWidth measureWidth(String width) {
        switch (width == null ? "" : width) {
            case "none": case "long": case "full": return MeasureFormat.FormatWidth.WIDE;
            case "medium": return MeasureFormat.FormatWidth.SHORT;
            case "short": return MeasureFormat.FormatWidth.NARROW;
            default: throw invalidWidth(width);
        }
    }

    private static int dateStyle(String width) {
        switch (width == null ? "" : width) {
            case "none": return -1;
            case "short": return DateFormat.SHORT;
            case "medium": return DateFormat.MEDIUM;
            case "long": return DateFormat.LONG;
            case "full": return DateFormat.FULL;
            default: throw invalidWidth(width);
        }
    }

    // full/long -> widest, medium -> abbreviated, short -> narrow. Mirrors the
    // JS port's width mapping and is reused for lists and month/weekday names.
    private static int symbolWidth(String width) {
        switch (width == null ? "" : width) {
            case "none": case "long": case "full": return DateFormatSymbols.WIDE;
            case "medium": return DateFormatSymbols.ABBREVIATED;
            case "short": return DateFormatSymbols.NARROW;
            default: throw invalidWidth(width);
        }
    }

    private static ListFormatter.Width listWidth(String width) {
        switch (width == null ? "" : width) {
            case "none": case "long": case "full": return ListFormatter.Width.WIDE;
            case "medium": return ListFormatter.Width.SHORT;
            case "short": return ListFormatter.Width.NARROW;
            default: throw invalidWidth(width);
        }
    }

    private static RelativeDateTimeUnit relativeUnit(String unit) {
        switch (unit == null ? "" : unit) {
            case "second": return RelativeDateTimeUnit.SECOND;
            case "minute": return RelativeDateTimeUnit.MINUTE;
            case "hour": return RelativeDateTimeUnit.HOUR;
            case "day": return RelativeDateTimeUnit.DAY;
            case "week": return RelativeDateTimeUnit.WEEK;
            case "month": return RelativeDateTimeUnit.MONTH;
            case "quarter": return RelativeDateTimeUnit.QUARTER;
            case "year": return RelativeDateTimeUnit.YEAR;
            default:
                throw new InvalidArgumentException("\"" + unit + "\" is not a valid relative unit.");
        }
    }

    private static InvalidArgumentException invalidWidth(String width) {
        return new InvalidArgumentException(
                "\"" + width + "\" is not a valid format width (use none/short/medium/long/full).");
    }

    /** ICU day (1=Sunday..7=Saturday) -> ISO day (1=Monday..7=Sunday). */
    private static int isoDay(int icuDay) {
        return ((icuDay + 5) % 7) + 1;
    }

    private static List<String> nonEmpty(String[] values) {
        List<String> out = new ArrayList<>(values.length);
        for (String v : values) {
            if (v != null && !v.isEmpty()) {
                out.add(v);
            }
        }
        return out;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    /** Parse a BCP-47 or underscore id the same way the constructor does. */
    private static ULocale toULocale(String tag) {
        return new ULocale((tag == null ? "" : tag).replace('-', '_').trim());
    }
}
