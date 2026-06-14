# Cosmo

Ergonomic application localisation for Java, powered by [ICU4J](https://unicode-org.github.io/icu/userguide/icu4j/).

Cosmo is a thin, ergonomic layer over [ICU4J](https://unicode-org.github.io/icu/userguide/icu4j/),
the reference [ICU](https://icu.unicode.org/) implementation for Java. Give it a
locale (and optionally a time zone) and it formats numbers, money, dates, units,
lists and messages exactly the way your users expect. There is **no bundled locale
data** — every result comes straight from ICU and [CLDR](https://cldr.unicode.org/),
covering all languages, scripts, calendars and time zones.

Cosmo is implemented consistently across four languages — the same concepts, method
names and behaviour, each built directly on its platform's ICU:
[JavaScript](https://github.com/cosmo-intl/cosmo-js) ([docs](https://cosmo.miloun.com/?lang=js)) ·
[Python](https://github.com/cosmo-intl/cosmo-python) ([docs](https://cosmo.miloun.com/?lang=python)) ·
**Java** ·
[PHP](https://github.com/salarmehr/cosmopolitan) ([docs](https://cosmo.miloun.com/?lang=php)).

📖 **Full documentation, API reference and live playground:** https://cosmo.miloun.com/?lang=java

## Requirements

- Java 11+
- [`com.ibm.icu:icu4j`](https://central.sonatype.com/artifact/com.ibm.icu/icu4j) (declared transitively)

## Install

```xml
<dependency>
  <groupId>com.miloun</groupId>
  <artifactId>cosmo</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Quick start

```java
import com.miloun.cosmo.Cosmo;

new Cosmo("es_ES").money(11000.4, "EUR");   // "11.000,40 €"
new Cosmo("en").percentage(0.2);            // "20%"
new Cosmo("en_AU").money(1234.5);           // "$1,234.50"  (currency inferred from region)
new Cosmo("en").spellout(42);               // "forty-two"
new Cosmo("fa").language("en");             // "انگلیسی"
```

`"en-AU"` and `"en_AU"` are both accepted (canonicalised by ICU), as are
[BCP-47](https://www.rfc-editor.org/info/bcp47) [Unicode extensions](https://unicode.org/reports/tr35/#u_Extension)
(`fa-IR-u-nu-latn-ca-buddhist`).

## What you get

- **Locale display names** — languages, regions, scripts, calendars and currencies, plus emoji flags and writing direction.
- **Numbers & money** — decimals, percentages, currencies (inferred from the region), units, compact notation, scientific, ranges, plus spelled-out and ordinal text.
- **Dates & times** — locale formats in any calendar (Gregorian, Persian, Buddhist…), custom ICU patterns, durations, date ranges, and relative times.
- **Text** — locale-aware sort and search, word/sentence/grapheme segmentation, case mapping and quotation marks.
- **Messages** — [ICU MessageFormat](https://unicode-org.github.io/icu/userguide/format_parse/messages/) (`plural`, `selectordinal`, `select`).
- **Parsing & transforms** — the inverse formatters for numbers, money and dates, transliteration, UTS #39 spoof checks, locale negotiation, person-name formatting and contact-list index buckets.
- **Raw ICU access** — resource-bundle lookups for data the high-level methods don't cover.

See the [full API reference](https://cosmo.miloun.com/api-reference/?lang=java) for every method,
the [platform notes](https://cosmo.miloun.com/platform-notes/) for ICU4J specifics, and
[resources](https://cosmo.miloun.com/resources/) for ICU/CLDR references.

## Build & test

```sh
mvn test
```

## Errors

All library errors extend `CosmoException` (a `RuntimeException`):

- `InvalidArgumentException` — bad caller input (an unknown currency code, …)
- `UnsupportedException` — the ICU4J build can't perform the operation

## License

MIT © Aiden Adrian