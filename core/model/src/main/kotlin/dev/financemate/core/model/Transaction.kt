package dev.financemate.core.model

import dev.financemate.core.money.Money
import java.time.LocalDate

/**
 * A single posted transaction in the ledger.
 *
 * ## Sign convention
 *
 * [amount] is negative for money leaving the account and positive for money
 * arriving, **for every account type without exception**. Credit-card statements
 * that report purchases as positive are flipped at parse time, not carried
 * through and reasoned about later.
 *
 * One convention applied at exactly one boundary is the only way sums stay
 * trustworthy; the alternative is every downstream calculation second-guessing
 * what the sign means for this particular account.
 */
public data class Transaction(
    val id: TransactionId,
    val accountId: AccountId,
    val postedDate: LocalDate,
    val amount: Money,
    /** The description exactly as the bank supplied it. Never edited. */
    val rawDescription: String,
    /** Normalised merchant key derived from [rawDescription]. */
    val merchantKey: MerchantKey,
    val categoryId: CategoryId? = null,
    /**
     * Identity fingerprint used to recognise this transaction on re-import.
     * See `DedupHasher`.
     */
    val dedupHash: String,
    val importBatchId: ImportBatchId? = null,
    /**
     * Bank-supplied unique id (OFX `FITID`). When present this is a far stronger
     * dedup signal than the computed hash, because it survives the bank
     * rewording a description between statement downloads.
     */
    val institutionTransactionId: String? = null,
    val isPending: Boolean = false,
    /**
     * True when this is one leg of a movement between the user's own accounts.
     *
     * Transfers must be excluded from spending totals. Counting a credit-card
     * payment as an expense double-counts every purchase on that card.
     */
    val isTransfer: Boolean = false,
    val notes: String? = null,
    val tags: Set<String> = emptySet(),
) {
    val isDebit: Boolean get() = amount.isNegative
    val isCredit: Boolean get() = amount.isPositive
}
