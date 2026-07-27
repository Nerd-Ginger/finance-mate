package dev.financemate.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.financemate.core.data.dao.AccountDao
import dev.financemate.core.data.dao.CategoryDao
import dev.financemate.core.data.dao.EgressLogDao
import dev.financemate.core.data.dao.ImportBatchDao
import dev.financemate.core.data.dao.MerchantClassificationDao
import dev.financemate.core.data.dao.TransactionDao
import dev.financemate.core.data.entity.AccountEntity
import dev.financemate.core.data.entity.CategoryEntity
import dev.financemate.core.data.entity.EgressLogEntity
import dev.financemate.core.data.entity.ImportBatchEntity
import dev.financemate.core.data.entity.MerchantClassificationEntity
import dev.financemate.core.data.entity.TransactionEntity

/**
 * The encrypted ledger.
 *
 * Schemas are exported to `core/data/schemas` so migrations can be tested
 * against the real previous schema rather than against someone's recollection
 * of it. Losing a user's financial history to a bad migration is unrecoverable —
 * they cannot re-download statements from five years ago — so migrations here
 * are held to a higher standard than usual.
 */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        ImportBatchEntity::class,
        MerchantClassificationEntity::class,
        EgressLogEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
public abstract class FinanceMateDatabase : RoomDatabase() {

    public abstract fun accountDao(): AccountDao

    public abstract fun categoryDao(): CategoryDao

    public abstract fun transactionDao(): TransactionDao

    public abstract fun importBatchDao(): ImportBatchDao

    public abstract fun merchantClassificationDao(): MerchantClassificationDao

    public abstract fun egressLogDao(): EgressLogDao

    public companion object {
        public const val NAME: String = "financemate.db"
    }
}
