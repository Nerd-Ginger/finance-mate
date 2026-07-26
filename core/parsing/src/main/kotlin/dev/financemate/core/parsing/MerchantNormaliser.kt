package dev.financemate.core.parsing

import dev.financemate.core.model.MerchantKey

/**
 * Reduces raw bank-statement descriptions to a stable merchant key.
 *
 * This is the load-bearing piece of the whole analysis engine. Bank descriptors
 * are noisy in consistent ways — a payment processor prefix, a store number, a
 * city and state, a reference number, sometimes a date — and none of that varies
 * with *who* you paid:
 *
 * ```
 * SQ *BLUE BOTTLE COFFEE #47 OAKLAND CA   ->  BLUE BOTTLE COFFEE
 * SQ *BLUE BOTTLE COFFEE #12 SAN FRANCISCO CA -> BLUE BOTTLE COFFEE
 * ```
 *
 * If those two produce different keys, the recurring-payment detector sees two
 * unrelated one-off charges instead of one repeating merchant, and every feature
 * built on top of it — subscriptions, duplicates, price rises — degrades
 * silently rather than failing loudly. That is why this class is small, ordered,
 * heavily tested, and deliberately conservative: **it would rather leave noise in
 * than merge two genuinely different merchants.**
 *
 * Over-merging is the dangerous direction. Leaving "SAFEWAY FUEL" separate from
 * "SAFEWAY" is a minor annoyance; merging "AMERICAN AIRLINES" into "AMERICAN
 * EXPRESS" would corrupt the user's data in a way they may never notice.
 */
public object MerchantNormaliser {

    /** Two-letter US state and territory codes, used to strip trailing locations. */
    private val US_STATE_CODES: Set<String> = setOf(
        "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA", "HI", "ID",
        "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS",
        "MO", "MT", "NE", "NV", "NH", "NJ", "NM", "NY", "NC", "ND", "OH", "OK",
        "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV",
        "WI", "WY", "DC", "PR", "VI", "GU", "AS", "MP",
    )

    /**
     * Payment-processor and aggregator prefixes. These identify who moved the
     * money, not who was paid, so they carry no merchant information at all.
     */
    private val PROCESSOR_PREFIX = Regex(
        """^(?:SQ|SQC|TST|TOAST|PAYPAL|PP|PY|SP|IC|EB|WPY|CLOVER|LEVELUP|GOOGLE|GOOGLE PAY|APPLE PAY|VENMO|CASH APP|ZELLE)\s*\*+\s*""",
    )

    /**
     * Transaction-type noise the bank prepends. Ordered longest-first so
     * "DEBIT CARD PURCHASE" is consumed before a bare "PURCHASE" could match.
     */
    private val TYPE_PREFIXES: List<Regex> = listOf(
        """^PURCHASE AUTHORIZED ON \d{1,2}/\d{1,2}\s*""",
        """^RECURRING (?:PAYMENT|DEBIT CARD|TRANSFER)\s*""",
        """^(?:PRE)?AUTHORIZED (?:PAYMENT|DEBIT|CREDIT)\s*""",
        """^DEBIT CARD (?:PURCHASE|PAYMENT)\s*""",
        """^CREDIT CARD (?:PURCHASE|PAYMENT)\s*""",
        """^(?:POS|PIN) (?:DEBIT|PURCHASE|CREDIT)\s*""",
        """^CHECK\s?CARD (?:PURCHASE|PAYMENT)?\s*""",
        """^(?:ACH|EFT) (?:DEBIT|CREDIT|PAYMENT|WITHDRAWAL)\s*""",
        """^ELECTRONIC (?:PAYMENT|WITHDRAWAL|DEPOSIT)\s*""",
        """^ONLINE (?:PAYMENT|TRANSFER|PURCHASE)\s*""",
        """^BILL\s?PAY(?:MENT)?\s*""",
        """^(?:VISA|MASTERCARD|MC|AMEX|DISCOVER)\s+(?:PURCHASE|PAYMENT|DEBIT)\s*""",
        """^MERCHANT PURCHASE\s*""",
        """^POINT OF SALE (?:DEBIT|PURCHASE)\s*""",
    ).map { Regex(it) }

    /**
     * Noise removed from anywhere in the string. Order matters: dates and phone
     * numbers are stripped before generic long-digit runs, so their structure can
     * still be recognised.
     */
    private val INLINE_NOISE: List<Pair<Regex, String>> = listOf(
        // "#47", "# 1234", "STORE 00123"
        Regex("""#\s*\d+""") to " ",
        Regex("""\bSTORE\s+\d+\b""") to " ",
        // Phone numbers in various shapes: 866-579-7172, 8665797172, 800 555 1212
        Regex("""\b\d{3}[-.\s]\d{3}[-.\s]\d{4}\b""") to " ",
        Regex("""\b(?:800|833|844|855|866|877|888)[-.\s]?\d{7}\b""") to " ",
        // Embedded dates: 05/12, 05/12/24, 2024-05-12
        Regex("""\b\d{1,2}/\d{1,2}(?:/\d{2,4})?\b""") to " ",
        Regex("""\b\d{4}-\d{2}-\d{2}\b""") to " ",
        // Masked card fragments: XXXX1234, ****5678
        Regex("""\b[X*]{2,}\d+\b""") to " ",
        // Web noise: WWW.NETFLIX.COM -> NETFLIX
        Regex("""\bWWW\.""") to " ",
        Regex("""\.(?:COM|NET|ORG|CO|IO|APP)\b""") to " ",
        // Reference/auth numbers: any run of 4+ digits, and alphanumeric ids that
        // mix letters and digits in a way brand names do not (e.g. 2A3B4C5D6).
        Regex("""\b\d{4,}\b""") to " ",
        Regex("""\b(?=[A-Z0-9]*\d)(?=[A-Z0-9]*[A-Z])[A-Z0-9]{6,}\b""") to " ",
    )

    /**
     * Trailing tokens that are pure noise once the rest is stripped.
     * Applied repeatedly until stable.
     */
    private val TRAILING_NOISE = Regex(
        """[\s\-]+(?:USA?|US|INC|LLC|LTD|CO|CORP|COM|RECURRING|PAYMENT|PMT|PURCHASE|DEBIT|CREDIT|CARD|ONLINE|BILL)$""",
    )

    /**
     * Canonical names for brands that appear under several descriptors.
     *
     * Kept deliberately short and exact-match-on-normalised-form. A fuzzy version
     * of this map is exactly how you end up merging unrelated merchants, so
     * entries are only added when the mapping is unambiguous.
     */
    private val BRAND_ALIASES: Map<String, String> = mapOf(
        "AMZN MKTP" to "AMAZON",
        "AMZN MKTP US" to "AMAZON",
        "AMAZON MKTPL" to "AMAZON",
        "AMAZON MARKETPLACE" to "AMAZON",
        "AMAZON PRIME" to "AMAZON PRIME",
        "AMZN DIGITAL" to "AMAZON DIGITAL",
        "WHOLEFDS" to "WHOLE FOODS",
        "WHOLE FOODS MKT" to "WHOLE FOODS",
        "WM SUPERCENTER" to "WALMART",
        "WAL MART" to "WALMART",
        "WALMART COM" to "WALMART",
        "SBUX" to "STARBUCKS",
        "MCDONALDS F" to "MCDONALDS",
        "UBER TRIP" to "UBER",
        "UBER EATS" to "UBER EATS",
        "LYFT RIDE" to "LYFT",
        "NFLX" to "NETFLIX",
        "SPOTIFY USA" to "SPOTIFY",
        "GOOGLE STORAGE" to "GOOGLE STORAGE",
        "TARGET T" to "TARGET",
        "THE HOME DEPOT" to "HOME DEPOT",
        "TRADER JOE S" to "TRADER JOES",
    )

    /**
     * Produces the stable key used for grouping. Uppercase, punctuation-free.
     *
     * Never returns blank: if stripping removes everything (a descriptor that was
     * nothing but a reference number), the cleaned original is used instead, so
     * the transaction still groups with its own kind rather than vanishing.
     */
    public fun normalise(rawDescription: String): MerchantKey {
        val cleaned = clean(rawDescription)
        val resolved = BRAND_ALIASES[cleaned] ?: cleaned
        if (resolved.isNotBlank()) return MerchantKey(resolved)

        // Everything was stripped. Fall back to the raw text reduced to
        // alphanumerics so the key is still deterministic and non-blank.
        val fallback = rawDescription.uppercase()
            .replace(Regex("""[^A-Z0-9]+"""), " ")
            .trim()
        return MerchantKey(fallback.ifBlank { "UNKNOWN" })
    }

    /**
     * A human-friendly rendering of the merchant, for display in lists.
     *
     * Title-cased, with short words left lowercase except at the start, and
     * all-caps acronyms of three characters or fewer preserved.
     */
    public fun displayName(rawDescription: String): String {
        val key = normalise(rawDescription).value
        val minorWords = setOf("OF", "THE", "AND", "AT", "IN", "ON", "FOR", "TO")
        return key.split(" ")
            .filter { it.isNotBlank() }
            .mapIndexed { index, word ->
                when {
                    word.length <= 3 && word.all { it.isDigit() || it.isUpperCase() } &&
                        word !in minorWords -> word
                    index > 0 && word in minorWords -> word.lowercase()
                    else -> word.lowercase().replaceFirstChar { it.uppercase() }
                }
            }
            .joinToString(" ")
    }

    private fun clean(rawDescription: String): String {
        var text = rawDescription.uppercase().trim()

        // 1. Processor prefix, possibly stacked ("PAYPAL *SQ *MERCHANT").
        repeat(times = 3) {
            val stripped = text.replaceFirst(PROCESSOR_PREFIX, "")
            if (stripped == text) return@repeat
            text = stripped.trim()
        }

        // 2. Transaction-type prefix.
        for (prefix in TYPE_PREFIXES) {
            val stripped = text.replaceFirst(prefix, "")
            if (stripped != text) {
                text = stripped.trim()
                break
            }
        }

        // 3. Inline noise.
        for ((pattern, replacement) in INLINE_NOISE) {
            text = pattern.replace(text, replacement)
        }

        // 4. Trailing location: "<CITY> <STATE>".
        text = stripTrailingLocation(text.trim())

        // 5. Punctuation to spaces, then collapse.
        text = text.replace(Regex("""[^A-Z0-9]+"""), " ").trim()

        // 6. Trailing filler, applied until stable.
        var previous: String
        do {
            previous = text
            text = TRAILING_NOISE.replace(text, "").trim()
        } while (text != previous)

        return text.replace(Regex("""\s+"""), " ").trim()
    }

    /**
     * Removes a trailing `<CITY> <STATE>` location.
     *
     * Two rules keep this safe:
     *
     * 1. **A city is only removed when a state code follows it.** Without that
     *    anchor there is no way to tell a location from part of the merchant
     *    name, so nothing is removed. `NEW YORK LIFE` keeps its city because
     *    "LIFE" is not a state code.
     * 2. **Only positively identified city names are removed.** `SAFEWAY CA`
     *    loses the state and keeps "SAFEWAY", because "SAFEWAY" is not a city.
     *    A heuristic that dropped "the token before the state" would delete the
     *    merchant entirely.
     *
     * Multi-word cities are matched greedily so "SAN FRANCISCO" is consumed
     * whole rather than leaving a stray "SAN".
     */
    private fun stripTrailingLocation(text: String): String {
        val tokens = text.split(Regex("""\s+""")).filter { it.isNotBlank() }.toMutableList()
        if (tokens.size < 2 || tokens.last() !in US_STATE_CODES) return tokens.joinToString(" ")

        // Drop the state code.
        tokens.removeAt(tokens.size - 1)

        // Then drop a known city name immediately before it, longest match first.
        for (length in UsCityNames.MAX_TOKENS downTo 1) {
            if (tokens.size <= length) continue // never strip away the whole merchant
            val candidate = tokens.subList(tokens.size - length, tokens.size).joinToString(" ")
            if (candidate in UsCityNames.NAMES) {
                repeat(length) { tokens.removeAt(tokens.size - 1) }
                break
            }
        }

        return tokens.joinToString(" ")
    }
}
