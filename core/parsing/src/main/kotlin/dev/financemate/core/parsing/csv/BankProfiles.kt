package dev.financemate.core.parsing.csv

/**
 * Built-in CSV layouts for common US banks.
 *
 * These exist so the usual case needs no configuration. When a file's header
 * matches a known profile the user imports it directly; when it does not,
 * [ColumnDetector] guesses and the user confirms in the mapping screen.
 *
 * The field that matters most in each profile is [ColumnMapping.invertSign].
 * Card issuers are split on what a positive amount means, and there is no way to
 * tell from the data alone — a $50 positive figure is a $50 purchase at Amex and
 * a $50 refund at Chase. Get it wrong and every purchase becomes income.
 */
public object BankProfiles {

    public data class BankProfile(
        val id: String,
        val displayName: String,
        /**
         * Header names that identify this layout. Matching is case-insensitive
         * and ignores punctuation; all of these must be present.
         */
        val headerSignature: Set<String>,
        val mapping: ColumnMapping,
        val notes: String? = null,
    )

    // Chase checking: Details,Posting Date,Description,Amount,Type,Balance,Check or Slip #
    public val CHASE_CHECKING: BankProfile = BankProfile(
        id = "chase-checking",
        displayName = "Chase (checking)",
        headerSignature = setOf("details", "posting date", "description", "amount", "type"),
        mapping = ColumnMapping(
            dateColumn = 1,
            descriptionColumns = listOf(2),
            amountColumn = 3,
            balanceColumn = 5,
        ),
    )

    // Chase credit card: Transaction Date,Post Date,Description,Category,Type,Amount,Memo
    public val CHASE_CREDIT: BankProfile = BankProfile(
        id = "chase-credit",
        displayName = "Chase (credit card)",
        headerSignature = setOf("transaction date", "post date", "description", "category", "amount"),
        mapping = ColumnMapping(
            // Post date is what appears on the statement, so reconciliation lines up.
            dateColumn = 1,
            descriptionColumns = listOf(2),
            amountColumn = 5,
        ),
        notes = "Chase reports purchases as negative, matching FinanceMate's convention.",
    )

    // Bank of America: Date,Description,Amount,Running Bal.
    public val BANK_OF_AMERICA: BankProfile = BankProfile(
        id = "bofa",
        displayName = "Bank of America",
        headerSignature = setOf("date", "description", "amount", "running bal"),
        mapping = ColumnMapping(
            dateColumn = 0,
            descriptionColumns = listOf(1),
            amountColumn = 2,
            balanceColumn = 3,
        ),
    )

    // American Express: Date,Description,Amount
    public val AMEX: BankProfile = BankProfile(
        id = "amex",
        displayName = "American Express",
        headerSignature = setOf("date", "description", "amount"),
        mapping = ColumnMapping(
            dateColumn = 0,
            descriptionColumns = listOf(1),
            amountColumn = 2,
            invertSign = true,
        ),
        notes = "Amex reports charges as POSITIVE. Sign is inverted on import.",
    )

    // Discover: Trans. Date,Post Date,Description,Amount,Category
    public val DISCOVER: BankProfile = BankProfile(
        id = "discover",
        displayName = "Discover",
        headerSignature = setOf("trans date", "post date", "description", "amount", "category"),
        mapping = ColumnMapping(
            dateColumn = 1,
            descriptionColumns = listOf(2),
            amountColumn = 3,
            invertSign = true,
        ),
        notes = "Discover reports purchases as POSITIVE. Sign is inverted on import.",
    )

    // Capital One: Transaction Date,Posted Date,Card No.,Description,Category,Debit,Credit
    public val CAPITAL_ONE: BankProfile = BankProfile(
        id = "capital-one",
        displayName = "Capital One",
        headerSignature = setOf("transaction date", "posted date", "description", "debit", "credit"),
        mapping = ColumnMapping(
            dateColumn = 1,
            descriptionColumns = listOf(3),
            debitColumn = 5,
            creditColumn = 6,
        ),
        notes = "Separate debit and credit columns; only one is populated per row.",
    )

    /**
     * Wells Fargo exports have **no header row**: `"03/14/2026","-12.50","*","","STARBUCKS"`.
     *
     * Without a header there is nothing to match on, so this profile is offered
     * to the user by name rather than detected automatically. Guessing it from
     * shape alone would risk applying it to some other bank's headerless export.
     */
    public val WELLS_FARGO: BankProfile = BankProfile(
        id = "wells-fargo",
        displayName = "Wells Fargo",
        headerSignature = emptySet(),
        mapping = ColumnMapping(
            dateColumn = 0,
            descriptionColumns = listOf(4),
            amountColumn = 1,
            hasHeaderRow = false,
        ),
        notes = "No header row. Selected manually rather than auto-detected.",
    )

    public val ALL: List<BankProfile> = listOf(
        CHASE_CHECKING,
        CHASE_CREDIT,
        BANK_OF_AMERICA,
        CAPITAL_ONE,
        DISCOVER,
        // Amex last: its signature (date/description/amount) is a subset of
        // several others, so more specific profiles must be tried first.
        AMEX,
        WELLS_FARGO,
    )

    /**
     * Profiles that can be recognised from a header row with no help from the
     * user.
     *
     * Smaller than [ALL], because a profile with an empty signature — Wells
     * Fargo, whose export has no header row at all — can only be chosen by hand.
     * The source screen tells the user how many banks are recognised
     * automatically, and that number is counted from here rather than typed into
     * the copy, so adding a profile cannot leave the promise stale.
     */
    public val AUTO_DETECTED: List<BankProfile> = ALL.filter { it.headerSignature.isNotEmpty() }

    /**
     * Finds the profile whose signature the header row satisfies.
     *
     * Profiles with an empty signature are never matched here — they exist for
     * manual selection only.
     */
    public fun matching(headerFields: List<String>): BankProfile? {
        val normalised = headerFields.map { normaliseHeader(it) }.toSet()
        return ALL.firstOrNull { profile ->
            profile.headerSignature.isNotEmpty() &&
                profile.headerSignature.all { required -> normalised.contains(required) }
        }
    }

    public fun byId(id: String): BankProfile? = ALL.firstOrNull { it.id == id }

    /** Lower-cases and strips punctuation so "Running Bal." matches "running bal". */
    internal fun normaliseHeader(header: String): String =
        header.lowercase()
            .replace(Regex("""[^a-z0-9 ]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
