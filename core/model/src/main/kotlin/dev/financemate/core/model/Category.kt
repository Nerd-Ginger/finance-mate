package dev.financemate.core.model

public data class Category(
    val id: CategoryId,
    val name: String,
    val kind: CategoryKind,
    val parentId: CategoryId? = null,
    /** Nominal spending categories the user cannot easily avoid. Drives 50/30/20. */
    val isEssential: Boolean = false,
    val isArchived: Boolean = false,
) {
    init {
        require(name.isNotBlank()) { "Category name must not be blank" }
        require(parentId != id) { "Category '$name' cannot be its own parent" }
    }
}

public enum class CategoryKind {
    INCOME,
    EXPENSE,

    /**
     * Movement between the user's own accounts. Never counted as income or
     * spending — see [Transaction.isTransfer].
     */
    TRANSFER,
}
