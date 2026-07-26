package dev.financemate.core.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
public data class AccountEntity(
    @PrimaryKey public val id: String,
    public val displayName: String,
    public val institution: String,
    public val type: String,
    public val currency: String,
    /** At most four digits. A full account number is never stored. */
    public val mask: String?,
    public val isArchived: Boolean = false,
)

@Entity(
    tableName = "categories",
    indices = [Index("parentId")],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
public data class CategoryEntity(
    @PrimaryKey public val id: String,
    public val name: String,
    public val kind: String,
    public val parentId: String? = null,
    public val isEssential: Boolean = false,
    public val isArchived: Boolean = false,
)

@Entity(
    tableName = "import_batches",
    indices = [Index("accountId")],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
public data class ImportBatchEntity(
    @PrimaryKey public val id: String,
    public val accountId: String,
    public val source: String,
    public val importedAtEpochMillis: Long,
    public val fileName: String?,
    public val rowsParsed: Int,
    public val rowsImported: Int,
    public val rowsDuplicate: Int,
    public val rowsFailed: Int,
)

/**
 * A posted transaction.
 *
 * ## The two unique indices are the point of this table
 *
 * `dedupHash` is unique, so a re-imported statement cannot create a second copy
 * of a transaction **even if the application logic is wrong**. Enforcing this in
 * the schema rather than in Kotlin means the guarantee survives refactors,
 * concurrent imports, and future callers who forget to check.
 *
 * `institutionTransactionId` is unique per account. SQLite treats NULLs as
 * distinct in a unique index, which is exactly the behaviour wanted here: the
 * constraint applies to formats that supply a bank id (OFX) and simply does not
 * apply to those that do not (CSV, QIF), with no special-casing.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["dedupHash"], unique = true),
        Index(value = ["accountId", "institutionTransactionId"], unique = true),
        Index(value = ["accountId", "postedDate"]),
        Index(value = ["merchantKey"]),
        Index(value = ["categoryId"]),
        Index(value = ["importBatchId"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ImportBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["importBatchId"],
            // Deleting a batch record must not delete the transactions it
            // created; undoing an import is an explicit operation, not a
            // side effect of tidying up batch history.
            onDelete = ForeignKey.SET_NULL,
            // Deferred so the constraint is checked at commit rather than at
            // each statement. The import pipeline cannot know a batch's row
            // counts until after it has attempted the inserts, so it writes the
            // transactions first and the batch summary second. Both happen
            // inside one transaction, so the reference is always satisfied by
            // the time it matters.
            deferred = true,
        ),
    ],
)
public data class TransactionEntity(
    @PrimaryKey public val id: String,
    public val accountId: String,
    /** Epoch day. Stored as an integer so date ranges index and sort cheaply. */
    public val postedDate: Long,
    /** Whole minor units. Never a floating-point value. */
    public val amountMinorUnits: Long,
    public val currency: String,
    public val rawDescription: String,
    public val merchantKey: String,
    public val categoryId: String? = null,
    public val dedupHash: String,
    public val importBatchId: String? = null,
    public val institutionTransactionId: String? = null,
    public val isPending: Boolean = false,
    public val isTransfer: Boolean = false,
    public val notes: String? = null,
    /** Comma-separated; tags are a small, display-only set. */
    public val tags: String? = null,
)
