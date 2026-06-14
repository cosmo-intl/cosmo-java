package com.miloun.cosmo;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors the Python port's test expectations. Where ICU wording is volatile
 * across versions we assert structure rather than a brittle literal.
 */
class CosmoTest {

    /** A fixed instant: 2020-02-02T00:13:20Z. */
    private static final Date TS = new Date(1_580_602_400_000L);

    private static final Modifiers UTC = new Modifiers(null, null, "UTC");

    // ------------------------------------------------------------------ //
    // construction & subtags
    // ------------------------------------------------------------------ //

    @Test
    void canonicalisesLocaleIds() {
        assertEquals("en_AU", new Cosmo("en-AU").locale);
        assertEquals("en_AU", new Cosmo("en_AU").locale);
        Cosmo fa = new Cosmo("fa-IR");
        assertEquals("fa", fa.subtags.language);
        assertEquals("IR", fa.subtags.region);
    }

    @Test
    void infersCurrencyFromRegion() {
        assertEquals("AUD", new Cosmo("en_AU").modifiers.currency);
        assertEquals("USD", new Cosmo("en_US").modifiers.currency);
        assertNull(new Cosmo("en").modifiers.currency); // no region -> no inference
    }

    @Test
    void modifierOverrideBeatsInference() {
        Cosmo c = new Cosmo("en_AU", new Modifiers(null, "EUR", null));
        assertEquals("EUR", c.modifiers.currency);
    }

    // ------------------------------------------------------------------ //
    // key -> value lookups
    // ------------------------------------------------------------------ //

    @Test
    void language() {
        assertEquals("English", new Cosmo("en").language("en"));
        assertEquals("انگلیسی", new Cosmo("fa").language("en"));
        assertEquals("", new Cosmo("en").language(""));
    }

    @Test
    void countryAndScript() {
        assertEquals("Australia", new Cosmo("en").country("AU"));
        assertEquals("", new Cosmo("en").country(""));
        assertEquals("Latin", new Cosmo("en").script("Latn"));
        assertTrue(new Cosmo("en").script("Hans").contains("Simplified"));
    }

    @Test
    void calendarAndDirection() {
        assertEquals("Buddhist Calendar", new Cosmo("en").calendar("buddhist"));
        assertEquals("ltr", new Cosmo("en").direction());
        assertEquals("rtl", new Cosmo("fa").direction());
        assertEquals("rtl", new Cosmo("en").direction("ar"));
    }

    @Test
    void flag() {
        assertEquals("🇦🇺", new Cosmo("en_AU").flag());
        assertEquals("🇺🇸", new Cosmo("en").flag("US"));
        assertEquals("", new Cosmo("en").flag("X"));
    }

    @Test
    void currencyNameAndSymbol() {
        Cosmo c = new Cosmo("en_US");
        assertEquals("Australian Dollar", c.currency("AUD"));
        assertEquals("A$", c.currency("AUD", true, false));
        assertEquals("ZZZ", c.currency("ZZZ")); // echoes unknown back
        assertThrows(CosmoException.class, () -> c.currency("ZZZ", false, true));
    }

    // ------------------------------------------------------------------ //
    // numbers
    // ------------------------------------------------------------------ //

    @Test
    void numberAndPercentage() {
        assertEquals("1,234,567.89", new Cosmo("en").number(1234567.89));
        assertEquals("20%", new Cosmo("en").percentage(0.2));
        // halfExpand (round half away from zero) — matches the JS port's Intl default.
        assertEquals("12.35%", new Cosmo("en").percentage(0.12345, 2));
    }

    @Test
    void money() {
        assertEquals("$1,234.50", new Cosmo("en_AU").money(1234.5));
        assertEquals("€1,234.50", new Cosmo("en_US").money(1234.5, "EUR"));
        assertEquals("$1,235", new Cosmo("en_US").money(1234.9, "USD", 0, false));
        assertEquals("", new Cosmo("en").money(100)); // no currency available
    }

    @Test
    void moneyErrors() {
        assertThrows(CosmoException.class,
                () -> new Cosmo("en").money(100, null, null, true));
        assertThrows(InvalidArgumentException.class,
                () -> new Cosmo("en_US").money(100, "EURO"));
    }

    @Test
    void scientificAndCompact() {
        assertEquals("1.2345E4", new Cosmo("en").scientific(12345));
        assertEquals("1.2K", new Cosmo("en").compact(1200));
        assertEquals("1.2 million", new Cosmo("en").compact(1200000, "long"));
    }

    @Test
    void ordinalAndSpellout() {
        Cosmo c = new Cosmo("en");
        assertEquals("1st", c.ordinal(1));
        assertEquals("2nd", c.ordinal(2));
        assertEquals("forty-two", c.spellout(42));
    }

    // ------------------------------------------------------------------ //
    // dates & times
    // ------------------------------------------------------------------ //

    @Test
    void momentDateTime() {
        Cosmo c = new Cosmo("en_US", UTC);
        assertEquals("February 2, 2020", c.date(TS, "long"));
        // ICU 72+ uses U+202F before AM/PM; normalise so the assertion is version-stable.
        assertEquals("12:13 AM", c.time(TS, "short").replace(' ', ' '));
        assertEquals("", c.moment(TS, "none", "none"));
        assertThrows(InvalidArgumentException.class, () -> c.date(TS, "bogus"));
    }

    @Test
    void persianCalendarIsImplicit() {
        // fa_IR resolves to the Persian calendar without an explicit modifier.
        assertTrue(new Cosmo("fa_IR", UTC).date(TS, "long").contains("۱۳۹۸"));
    }

    // ------------------------------------------------------------------ //
    // collation
    // ------------------------------------------------------------------ //

    @Test
    void compareAndSort() {
        Cosmo c = new Cosmo("en");
        assertTrue(c.compare("a", "b") < 0);
        assertTrue(c.compare("b", "a") > 0);
        assertEquals(Arrays.asList("apple", "banana", "cherry"),
                c.sort(Arrays.asList("banana", "apple", "cherry")));
    }

    // ------------------------------------------------------------------ //
    // messages, plurals, lists
    // ------------------------------------------------------------------ //

    @Test
    void messagePositionalAndNamed() {
        Cosmo c = new Cosmo("en");
        assertEquals("Bob has 3 items",
                c.message("{0} has {1, plural, one {# item} other {# items}}", "Bob", 3));
        assertEquals("1 item", c.message("{0, plural, one {# item} other {# items}}", 1));
        Map<String, Object> args = new HashMap<>();
        args.put("name", "Sue");
        args.put("n", 2);
        assertEquals("Sue likes 2 cats",
                c.message("{name} likes {n, plural, one {# cat} other {# cats}}", args));
    }

    @Test
    void pluralCategory() {
        Cosmo c = new Cosmo("en");
        assertEquals("one", c.pluralCategory(1));
        assertEquals("other", c.pluralCategory(2));
        assertEquals("one", c.pluralCategory(1, true));
        assertEquals("two", c.pluralCategory(2, true));
        assertEquals("few", c.pluralCategory(3, true));
    }

    @Test
    void join() {
        Cosmo c = new Cosmo("en");
        assertEquals("A, B, and C", c.join(Arrays.asList("A", "B", "C"))); // Oxford comma per CLDR
        assertEquals("A, B, or C", c.join(Arrays.asList("A", "B", "C"), "disjunction"));
        assertThrows(CosmoException.class, () -> c.join(Arrays.asList("A"), "bogus"));
    }

    // ------------------------------------------------------------------ //
    // relative durations & ranges
    // ------------------------------------------------------------------ //

    @Test
    void relativeDuration() {
        Cosmo c = new Cosmo("en");
        assertEquals("3 days ago", c.relativeDuration(-3, "day"));
        assertEquals("in 2 hours", c.relativeDuration(2, "hour"));
        // ICU4J exposes the word forms PyICU can't reach.
        assertEquals("yesterday", c.relativeDuration(-1, "day", "auto"));
        assertThrows(CosmoException.class, () -> c.relativeDuration(1, "fortnight"));
    }

    @Test
    void relativeDurationBetween() {
        Cosmo c = new Cosmo("en");
        Date base = Date.from(java.time.Instant.parse("2020-01-01T12:00:00Z"));
        assertEquals("in 5 days", c.relativeDurationBetween(
                Date.from(java.time.Instant.parse("2020-01-06T12:00:00Z")), base));
        assertEquals("3 days ago", c.relativeDurationBetween(
                Date.from(java.time.Instant.parse("2019-12-29T12:00:00Z")), base));
        assertEquals("yesterday", c.relativeDurationBetween(
                Date.from(java.time.Instant.parse("2019-12-31T12:00:00Z")), base)); // auto word form
    }

    @Test
    void numberAndMoneyRanges() {
        Cosmo c = new Cosmo("en_US");
        assertEquals("3–5", c.numberRange(3, 5));
        assertEquals("$3.00 – $5.00", c.moneyRange(3, 5, "USD"));
        assertEquals("", new Cosmo("en").moneyRange(3, 5)); // no currency
    }

    // ------------------------------------------------------------------ //
    // locale metadata
    // ------------------------------------------------------------------ //

    @Test
    void likelySubtags() {
        assertEquals("en_Latn_US", new Cosmo("en").addLikelySubtags().locale);
        assertEquals("en", new Cosmo("en_Latn_US").removeLikelySubtags().locale);
    }

    @Test
    void monthAndWeekdayNames() {
        Cosmo en = new Cosmo("en");
        assertEquals("January", en.monthNames().get(0));
        assertEquals(12, en.monthNames().size());
        assertEquals("Sunday", en.weekdayNames().get(0)); // Sunday-first
        assertEquals(7, en.weekdayNames().size());
        assertTrue(new Cosmo("fa_IR").monthNames().contains("فروردین")); // Persian calendar months
    }

    @Test
    void weekInfo() {
        WeekInfo gb = new Cosmo("en_GB").weekInfo();
        assertEquals(1, gb.firstDay); // Monday (ISO)
        assertEquals(Arrays.asList(6, 7), gb.weekend); // Saturday + Sunday
        assertEquals(7, new Cosmo("en_US").weekInfo().firstDay); // Sunday
    }

    // ------------------------------------------------------------------ //
    // factories & locale extensions
    // ------------------------------------------------------------------ //

    @Test
    void fromSubtagsAndAcceptLanguage() {
        assertEquals("en_AU", Cosmo.fromSubtags(new Subtags("en", "", "AU")).locale);
        assertEquals("fr_CH", Cosmo.fromAcceptLanguage("fr-CH, en;q=0.9, de;q=0.7").locale);
        assertEquals("de", Cosmo.fromAcceptLanguage("en;q=0.2, de;q=0.8").subtags.language);
    }

    @Test
    void honoursNumberingSystemExtension() {
        // Regression: createCanonical() on the raw id mangled -u-nu-thai into a variant.
        assertEquals("๑,๒๓๔,๕๖๗.๘๙", new Cosmo("th-TH-u-nu-thai").number(1234567.89));
    }

    // ------------------------------------------------------------------ //
    // number & collation options
    // ------------------------------------------------------------------ //

    private static Map<String, Object> opts(Object... kv) {
        Map<String, Object> out = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            out.put((String) kv[i], kv[i + 1]);
        }
        return out;
    }

    @Test
    void numberOptions() {
        Cosmo c = new Cosmo("en");
        assertEquals("2", c.number(2.9, opts("roundingMode", "floor", "maximumFractionDigits", 0)));
        assertEquals("3", c.number(2.1, opts("roundingMode", "ceil", "maximumFractionDigits", 0)));
        assertEquals("1.25", c.number(1.23, opts("roundingIncrement", 5,
                "minimumFractionDigits", 2, "maximumFractionDigits", 2)));
        assertEquals("12345", c.number(12345, opts("useGrouping", false)));
        assertEquals("120,000", c.number(123456.789, opts("maximumSignificantDigits", 2)));
        assertEquals("$10.00", c.money(9.991, "USD", null, false, opts("roundingMode", "ceil")));
        assertEquals("12.34%", c.percentage(0.12349, 2, opts("roundingMode", "floor")));
        assertThrows(CosmoException.class, () -> c.number(1, opts("roundingMode", "bogus")));
        assertThrows(CosmoException.class, () -> c.number(1, opts("bogusKey", 1)));
    }

    @Test
    void collationOptions() {
        Cosmo c = new Cosmo("en");
        assertTrue(c.compare("item2", "item10", opts("numeric", true)) < 0);
        assertEquals(Arrays.asList("item1", "item2", "item10"),
                c.sort(Arrays.asList("item10", "item2", "item1"), opts("numeric", true)));
        assertEquals(Arrays.asList("A", "a", "B", "b"),
                c.sort(Arrays.asList("b", "B", "a", "A"), opts("caseFirst", "upper")));
    }

    // ------------------------------------------------------------------ //
    // symbols, units, durations
    // ------------------------------------------------------------------ //

    @Test
    void symbol() {
        Cosmo c = new Cosmo("en");
        assertEquals(".", c.symbol("decimal"));
        assertEquals(",", c.symbol("grouping_separator"));
        assertEquals("%", c.symbol("percent"));
        assertEquals(",", new Cosmo("de").symbol("decimal"));
        assertThrows(CosmoException.class, () -> c.symbol("bogus"));
    }

    @Test
    void unit() {
        assertEquals("2.19 gigabytes", new Cosmo("en").unit("digital", "gigabyte", 2.19));
        assertTrue(new Cosmo("en").unit("digital", "gigabyte", 2.19, "short").contains("GB"));
        assertThrows(CosmoException.class, () -> new Cosmo("en").unit("x", "not-a-unit", 1));
    }

    @Test
    void duration() {
        Cosmo c = new Cosmo("en");
        assertEquals("339:17:20", c.duration(1221440));
        assertTrue(c.duration(1221440, true).contains("hours"));
        assertEquals("3 hours, 5 minutes", c.duration(opts("hours", 3, "minutes", 5), true));
        assertTrue(c.duration(opts("days", 2, "hours", 3)).contains("2 days"));
        assertEquals("", c.duration(new HashMap<>()));
    }

    // ------------------------------------------------------------------ //
    // contains & segmentation
    // ------------------------------------------------------------------ //

    @Test
    void contains() {
        Cosmo c = new Cosmo("en");
        assertTrue(c.contains("Résumé", "resume")); // base: ignore case & accents
        assertTrue(c.contains("hello world", "WORLD"));
        assertTrue(!c.contains("hello", "xyz"));
        assertTrue(c.contains("anything", ""));
        assertTrue(!c.contains("Résumé", "resume", "variant"));
    }

    @Test
    void segmentation() {
        Cosmo c = new Cosmo("en");
        assertEquals(Arrays.asList("Hello", "world", "ICU", "rocks"),
                c.splitWords("Hello, world! ICU rocks."));
        assertEquals(Arrays.asList("Hi there.", "How are you?"),
                c.splitSentences("Hi there. How are you?"));
        assertEquals(Arrays.asList("a", "👩‍👧", "b"), c.splitGraphemes("a👩‍👧b")); // ZWJ stays whole
        assertEquals(Arrays.asList(), c.splitGraphemes(""));
    }

    @Test
    void ellipsize() {
        Cosmo c = new Cosmo("en");
        assertEquals("short", c.ellipsize("short", 20));
        String out = c.ellipsize("The quick brown fox jumps", 15);
        assertTrue(out.endsWith("…"));
        assertTrue(c.splitGraphemes(out).size() <= 15);
    }

    // ------------------------------------------------------------------ //
    // quotes, case, time zones, display names
    // ------------------------------------------------------------------ //

    @Test
    void quote() {
        assertEquals("“hi”", new Cosmo("en").quote("hi"));
        assertEquals("«x»", new Cosmo("fa").quote("x"));
    }

    @Test
    void caseTransforms() {
        assertEquals("ISTANBUL", new Cosmo("en").upper("istanbul"));
        assertEquals("İSTANBUL", new Cosmo("tr").upper("istanbul")); // Turkish dotted I
        assertEquals("hello", new Cosmo("en").lower("HELLO"));
    }

    @Test
    void timeZoneName() {
        Cosmo c = new Cosmo("en", new Modifiers(null, null, "Australia/Sydney"));
        assertTrue(c.timeZoneName("long").contains("Australian Eastern"));
        assertTrue(Arrays.asList("AEST", "AEDT", "GMT+10", "GMT+11").contains(c.timeZoneName("short")));
        assertThrows(CosmoException.class, () -> c.timeZoneName("bogus"));
    }

    @Test
    void displayName() {
        Cosmo c = new Cosmo("en");
        assertEquals("French", c.displayName("language", "fr"));
        assertEquals("Japan", c.displayName("region", "JP"));
        assertTrue(c.displayName("script", "Hans").contains("Simplified"));
        assertEquals("Buddhist Calendar", c.displayName("calendar", "buddhist"));
        assertEquals("Euro", c.displayName("currency", "EUR"));
        assertThrows(CosmoException.class, () -> c.displayName("nope", "x"));
    }

    @Test
    void supportedValues() {
        Cosmo c = new Cosmo("en");
        assertTrue(c.supportedValues("timeZone").contains("Australia/Sydney"));
        assertTrue(c.supportedValues("collation").contains("standard"));
        assertTrue(c.supportedValues("numberingSystem").contains("latn"));
        assertTrue(c.supportedValues("unit").contains("gigabyte"));
        assertTrue(c.supportedValues("currency").contains("EUR")); // enumerable here, unlike PyICU
        assertTrue(c.supportedValues("calendar").contains("buddhist"));
        assertThrows(CosmoException.class, () -> c.supportedValues("bogus"));
    }

    // ------------------------------------------------------------------ //
    // date patterns & ranges, raw ICU access
    // ------------------------------------------------------------------ //

    @Test
    void formatMomentPattern() {
        assertEquals("2020-02-02", new Cosmo("en_US", UTC).formatMoment(TS, "yyyy-MM-dd"));
    }

    @Test
    void dateRange() {
        Date start = Date.from(java.time.Instant.parse("2020-02-02T12:00:00Z"));
        Date end = Date.from(java.time.Instant.parse("2020-02-05T12:00:00Z"));
        String out = new Cosmo("en_US", UTC).dateRange(start, end);
        assertTrue(out.contains("2") && out.contains("5") && out.contains("Feb"));
        assertThrows(CosmoException.class,
                () -> new Cosmo("en_US").dateRange(start, end, "full", "full"));
    }

    @Test
    void resourceBundleGet() {
        Object eur = new Cosmo("en").get(Bundle.CURRENCY, "Currencies", "EUR");
        assertEquals("Euro", ((com.ibm.icu.util.UResourceBundle) eur).get(1).getString());
        assertNull(new Cosmo("en").get(Bundle.CURRENCY, "Currencies", "NOPE"));
    }

    // ------------------------------------------------------------------ //
    // ICU4J extras — locale negotiation, transforms, spoofing, parsing, names
    // ------------------------------------------------------------------ //

    @Test
    void bestMatch() {
        // CLDR language distance: en_AU is served better by en-GB than en-US.
        assertEquals("en-GB", new Cosmo("en_AU").bestMatch(Arrays.asList("en-US", "en-GB", "fr")));
        assertEquals("fr", new Cosmo("ja").bestMatch(Arrays.asList("fr", "de"))); // fallback: first
        assertThrows(CosmoException.class, () -> new Cosmo("en").bestMatch(Arrays.asList()));
    }

    @Test
    void fromAcceptLanguageNegotiated() {
        Cosmo c = Cosmo.fromAcceptLanguage("fr-CH, en;q=0.9", Arrays.asList("en-US", "fr-FR"));
        assertEquals("fr_FR", c.locale);
        assertEquals("en-US",
                Cosmo.fromAcceptLanguage("", Arrays.asList("en-US", "fr-FR")).locale.replace('_', '-'));
    }

    @Test
    void transliterateAndRomanize() {
        Cosmo c = new Cosmo("en");
        assertEquals("Moskva", c.romanize("Москва"));
        assertEquals("Lodz cafe", c.transliterate("Łódź café", "Any-Latin; Latin-ASCII"));
        assertThrows(CosmoException.class, () -> c.transliterate("x", "Nope-Nope"));
        assertTrue(c.supportedValues("transliterator").contains("Any-Latin"));
    }

    @Test
    void spoofChecks() {
        Cosmo c = new Cosmo("en");
        assertTrue(c.confusable("paypal", "раураl")); // Cyrillic раура + l
        assertTrue(!c.confusable("hello", "world"));
        assertTrue(c.suspicious("pаypal")); // mixed Latin/Cyrillic
        assertTrue(!c.suspicious("paypal"));
    }

    @Test
    void indexBuckets() {
        Map<String, List<String>> buckets =
                new Cosmo("en").indexBuckets(Arrays.asList("banana", "apple", "Cherry", "avocado"));
        assertEquals(Arrays.asList("A", "B", "C"), new java.util.ArrayList<>(buckets.keySet()));
        assertEquals(Arrays.asList("apple", "avocado"), buckets.get("A"));
    }

    @Test
    void parsing() {
        assertEquals(1234.56, new Cosmo("de").parseNumber("1.234,56"));
        assertEquals(1234.56, new Cosmo("en").parseNumber("1,234.56"));
        com.ibm.icu.util.CurrencyAmount money = new Cosmo("en_US").parseMoney("$12.30");
        assertEquals(12.3, money.getNumber().doubleValue());
        assertEquals("USD", money.getCurrency().getCurrencyCode());
        Cosmo utc = new Cosmo("en_US", UTC);
        assertEquals("February 2, 2020", utc.date(utc.parseDate("February 2, 2020", "long"), "long"));
        assertEquals(1_580_601_600_000L, utc.parseMoment("2020-02-02", "yyyy-MM-dd").getTime());
        assertThrows(CosmoException.class, () -> new Cosmo("en").parseNumber("not a number"));
    }

    @Test
    void personName() {
        Map<String, String> name = new HashMap<>();
        name.put("given", "John");
        name.put("surname", "Smith");
        assertEquals("John Smith", new Cosmo("en").personName(name));

        Map<String, String> ja = new HashMap<>();
        ja.put("given", "太郎");
        ja.put("surname", "山田");
        ja.put("locale", "ja");
        assertEquals("山田太郎", new Cosmo("ja").personName(ja)); // surname first, no space

        Map<String, String> bad = new HashMap<>();
        bad.put("nickname", "JJ");
        assertThrows(CosmoException.class, () -> new Cosmo("en").personName(bad));
        assertThrows(CosmoException.class, () -> new Cosmo("en").personName(name, "huge", "formal"));
    }
}
