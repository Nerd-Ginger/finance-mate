package dev.financemate.core.model

/**
 * Typed identifiers.
 *
 * These exist so an account id can never be passed where a category id is
 * expected. In a codebase where nearly every entity is keyed by a string, that
 * mistake is easy to make, silent at runtime, and produces wrong numbers rather
 * than a crash — the worst failure mode for a budgeting app.
 */
@JvmInline
public value class AccountId(public val value: String) {
    init {
        require(value.isNotBlank()) { "AccountId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
public value class TransactionId(public val value: String) {
    init {
        require(value.isNotBlank()) { "TransactionId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
public value class CategoryId(public val value: String) {
    init {
        require(value.isNotBlank()) { "CategoryId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
public value class ImportBatchId(public val value: String) {
    init {
        require(value.isNotBlank()) { "ImportBatchId must not be blank" }
    }

    override fun toString(): String = value
}

/**
 * A stable, normalised key for a merchant, derived from raw statement text.
 *
 * "SQ *BLUE BOTTLE COFFEE #47 OAKLAND CA" and "SQ *BLUE BOTTLE COFFEE #12 SF CA"
 * are the same merchant and must produce the same key, or recurring-payment
 * detection and duplicate-subscription detection both fall apart.
 *
 * Always construct via `MerchantNormaliser`, never by hand.
 */
@JvmInline
public value class MerchantKey(public val value: String) {
    init {
        require(value.isNotBlank()) { "MerchantKey must not be blank" }
    }

    override fun toString(): String = value
}
