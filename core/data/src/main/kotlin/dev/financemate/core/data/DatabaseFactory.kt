package dev.financemate.core.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.financemate.core.crypto.DatabaseKeyProvider
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Builds the encrypted [FinanceMateDatabase].
 *
 * The passphrase comes from the hardware-backed Keystore via
 * [DatabaseKeyProvider], so the database file is meaningless without the device
 * that created it. Copying `financemate.db` off the phone yields ciphertext.
 */
public object DatabaseFactory {

    /**
     * Opens the ledger.
     *
     * @param passphrase 32 bytes from [DatabaseKeyProvider.getOrCreate].
     *   **SQLCipher zeroes this array**, so callers must not reuse it; fetch a
     *   fresh copy from the provider for each open.
     */
    public suspend fun create(
        context: Context,
        passphrase: ByteArray,
        databaseName: String = FinanceMateDatabase.NAME,
    ): FinanceMateDatabase {
        require(passphrase.size == DatabaseKeyProvider.KEY_LENGTH_BYTES) {
            "Expected a ${DatabaseKeyProvider.KEY_LENGTH_BYTES}-byte passphrase, " +
                "got ${passphrase.size}"
        }

        // Must precede any SQLCipher use; loads the native library.
        System.loadLibrary("sqlcipher")

        return Room.databaseBuilder(
            context.applicationContext,
            FinanceMateDatabase::class.java,
            databaseName,
        )
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .addCallback(ForeignKeyEnforcement)
            // No fallbackToDestructiveMigration, deliberately. Room's destructive
            // fallback silently drops every table when a migration is missing.
            // For a ledger the user cannot reconstruct — banks do not keep
            // statements forever — crashing is the better failure: it is
            // recoverable, and silent data loss is not.
            .build()
    }

    /**
     * Room disables foreign keys by default on some paths; this turns them on for
     * every connection.
     *
     * Without it, deleting an account would leave its transactions behind as
     * orphans that still count towards spending totals.
     */
    private object ForeignKeyEnforcement : androidx.room.RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys = ON")
        }
    }
}
