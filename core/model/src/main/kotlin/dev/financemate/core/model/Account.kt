package dev.financemate.core.model

import dev.financemate.core.money.CurrencyCode

public data class Account(
    val id: AccountId,
    val displayName: String,
    val institution: String,
    val type: AccountType,
    val currency: CurrencyCode,
    /**
     * Last few digits of the account number, for the user to tell two accounts
     * apart. Deliberately a partial mask: FinanceMate never stores a full
     * account number, because it has no use for one and storing it would create
     * risk with no upside.
     */
    val mask: String? = null,
    val isArchived: Boolean = false,
) {
    init {
        require(displayName.isNotBlank()) { "Account display name must not be blank" }
        require(mask == null || mask.length <= 4) {
            "Account mask must be at most 4 characters; FinanceMate does not store full account numbers"
        }
    }
}

public enum class AccountType {
    CHECKING,
    SAVINGS,
    CREDIT_CARD,
    CASH,
    INVESTMENT,
    LOAN,
    ;

    /**
     * True when a positive balance represents money owed rather than money held.
     *
     * Sign conventions differ between asset and liability accounts, and getting
     * this wrong flips the sign of every credit-card transaction — which is why
     * it lives on the type rather than being decided ad hoc at each call site.
     */
    public val isLiability: Boolean
        get() = this == CREDIT_CARD || this == LOAN
}
