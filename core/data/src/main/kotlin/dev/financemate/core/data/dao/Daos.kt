package dev.financemate.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import dev.financemate.core.data.entity.AccountEntity
import dev.financemate.core.data.entity.CategoryEntity
import dev.financemate.core.data.entity.ImportBatchEntity
import dev.financemate.core.data.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
public interface AccountDao {

    @Upsert
    public suspend fun upsert(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE isArchived = 0 ORDER BY displayName")
    public fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    public suspend fun byId(id: String): AccountEntity?

    @Query("SELECT * FROM accounts ORDER BY displayName")
    public suspend fun all(): List<AccountEntity>

    @Query("DELETE FROM accounts WHERE id = :id")
    public suspend fun delete(id: String)
}

@Dao
public interface CategoryDao {

    @Upsert
    public suspend fun upsert(category: CategoryEntity)

    @Upsert
    public suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("SELECT * FROM categories WHERE isArchived = 0 ORDER BY name")
    public fun observeActive(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    public suspend fun all(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    public suspend fun byId(id: String): CategoryEntity?
}

@Dao
public interface ImportBatchDao {

    @Insert
    public suspend fun insert(batch: ImportBatchEntity)

    @Query("SELECT * FROM import_batches WHERE accountId = :accountId ORDER BY importedAtEpochMillis DESC")
    public suspend fun forAccount(accountId: String): List<ImportBatchEntity>

    @Query("SELECT * FROM import_batches ORDER BY importedAtEpochMillis DESC LIMIT :limit")
    public suspend fun recent(limit: Int = 20): List<ImportBatchEntity>

    @Query("DELETE FROM import_batches WHERE id = :id")
    public suspend fun delete(id: String)
}

@Dao
public interface TransactionDao {

    /**
     * Inserts, ignoring rows that violate a unique index.
     *
     * `IGNORE` is what makes re-import a no-op: a transaction already present —
     * matched either by dedup fingerprint or by the bank's own id — is silently
     * skipped, and the returned row id is -1. Counting the -1s gives an exact
     * duplicate count with no extra queries.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertIgnoringDuplicates(transactions: List<TransactionEntity>): List<Long>

    @Update
    public suspend fun update(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    public suspend fun byId(id: String): TransactionEntity?

    @Query(
        """
        SELECT * FROM transactions
        WHERE accountId = :accountId
        ORDER BY postedDate DESC, id DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    public suspend fun forAccount(accountId: String, limit: Int, offset: Int = 0): List<TransactionEntity>

    @Query(
        """
        SELECT * FROM transactions
        WHERE postedDate BETWEEN :fromEpochDay AND :toEpochDay
        ORDER BY postedDate DESC, id DESC
        """,
    )
    public fun observeBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE merchantKey = :merchantKey
        ORDER BY postedDate ASC
        """,
    )
    public suspend fun forMerchant(merchantKey: String): List<TransactionEntity>

    /**
     * Every transaction, oldest first.
     *
     * The recurring-payment detector needs the full history for a merchant to
     * measure the gaps between charges, so this exists deliberately rather than
     * being paginated.
     */
    @Query("SELECT * FROM transactions ORDER BY postedDate ASC, id ASC")
    public suspend fun allChronological(): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    public suspend fun count(): Int

    @Query("SELECT dedupHash FROM transactions WHERE dedupHash IN (:hashes)")
    public suspend fun existingHashes(hashes: List<String>): List<String>

    /** Rolls back one import. See [ImportBatchEntity]. */
    @Query("DELETE FROM transactions WHERE importBatchId = :batchId")
    public suspend fun deleteByBatch(batchId: String): Int

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id IN (:ids)")
    public suspend fun assignCategory(ids: List<String>, categoryId: String?)

    @Query("UPDATE transactions SET isTransfer = :isTransfer WHERE id IN (:ids)")
    public suspend fun markTransfer(ids: List<String>, isTransfer: Boolean)

    @Query(
        """
        SELECT DISTINCT merchantKey FROM transactions
        WHERE categoryId IS NULL
        ORDER BY merchantKey
        """,
    )
    public suspend fun uncategorisedMerchants(): List<String>
}
