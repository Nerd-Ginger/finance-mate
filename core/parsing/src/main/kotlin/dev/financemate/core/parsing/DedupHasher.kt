package dev.financemate.core.parsing

import dev.financemate.core.model.AccountId
import dev.financemate.core.model.ParsedTransaction
import java.security.MessageDigest
import java.time.LocalDate

/**
 * Computes the identity fingerprint used to recognise a transaction on re-import.
 *
 * ## Why this matters more than it looks
 *
 * Statement exports overlap. A user downloading "last 90 days" every month
 * re-imports two thirds of what they already have. If those rows are not
 * recognised, spending silently doubles — and because the totals still look
 * plausible, the user may not notice until their budget is meaningless.
 * Re-importing an overlapping statement must be a no-op.
 *
 * ## What goes into the hash, and what deliberately does not
 *
 * Included: account, posted date, exact amount, and the *normalised* description.
 *
 * The description is normalised first because banks reword descriptors between
 * downloads — a pending charge that posts later often gains a reference number
 * or loses a processor prefix. Hashing the raw text would make those look like
 * new transactions. Hashing the normalised form survives the rewording.
 *
 * Excluded: category, notes, tags, pending flag. Those are user- or
 * state-dependent, and including them would make the same transaction hash
 * differently after the user edits it.
 *
 * ## The sequence number
 *
 * Genuine same-day duplicates exist — two $3.75 coffees at the same shop on the
 * same day are two transactions, not one. The caller passes an `occurrence`
 * index so identical rows within a single import each get a distinct hash, while
 * still matching their counterpart on re-import (because the ordering of
 * identical rows within a statement is stable).
 */
public object DedupHasher {

    /**
     * ASCII unit separator. Cannot occur in any of the hashed fields, so field
     * boundaries are unambiguous: "AB" + "C" must never collide with "A" + "BC".
     */
    private val FIELD_SEPARATOR: String = Char(31).toString()

    /**
     * @param occurrence zero-based index among otherwise-identical rows in the
     *   same import. Callers should assign this by counting duplicates in source
     *   order — see [assignHashes].
     */
    public fun hash(
        accountId: AccountId,
        postedDate: LocalDate,
        amountMinorUnits: Long,
        rawDescription: String,
        occurrence: Int = 0,
    ): String {
        val payload = listOf(
            accountId.value,
            postedDate.toString(),
            amountMinorUnits.toString(),
            MerchantNormaliser.normalise(rawDescription).value,
            occurrence.toString(),
        ).joinToString(separator = FIELD_SEPARATOR)
        return sha256Hex(payload)
    }

    /**
     * Assigns a hash to every parsed row, numbering identical rows in source
     * order so genuine same-day repeats are preserved rather than collapsed.
     *
     * Returns hashes positionally aligned with [transactions].
     */
    public fun assignHashes(
        accountId: AccountId,
        transactions: List<ParsedTransaction>,
    ): List<String> {
        val seen = mutableMapOf<String, Int>()
        return transactions.map { txn ->
            val identity = listOf(
                txn.postedDate.toString(),
                txn.amount.minorUnits.toString(),
                MerchantNormaliser.normalise(txn.rawDescription).value,
            ).joinToString(separator = FIELD_SEPARATOR)

            val occurrence = seen.getOrDefault(identity, 0)
            seen[identity] = occurrence + 1

            hash(
                accountId = accountId,
                postedDate = txn.postedDate,
                amountMinorUnits = txn.amount.minorUnits,
                rawDescription = txn.rawDescription,
                occurrence = occurrence,
            )
        }
    }

    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
