package dev.financemate.core.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations, in order.
 *
 * Written by hand and against the exported schema in `core/data/schemas` rather
 * than generated, because the cost of getting one wrong is a user's financial
 * history — which, unlike most app data, cannot be re-fetched. Banks do not keep
 * statements forever.
 *
 * The statements are held as constants so `MigrationSchemaTest` can compare them
 * to what Room exported. A mismatch between the two is the classic migration
 * bug: it works on a fresh install, where Room creates the table from the
 * entity, and fails only for users who upgrade — the ones with data.
 */
internal object Migrations {

    /**
     * The egress log table, verbatim from Room's exported v2 schema.
     *
     * `${'$'}{TABLE_NAME}` in the export is substituted for the real name here.
     */
    const val CREATE_EGRESS_LOG: String =
        "CREATE TABLE IF NOT EXISTS `egress_log` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`occurredAtEpochMillis` INTEGER NOT NULL, " +
            "`featureId` TEXT NOT NULL, " +
            "`endpoint` TEXT NOT NULL, " +
            "`modelId` TEXT NOT NULL, " +
            "`payload` TEXT NOT NULL, " +
            "`payloadBytes` INTEGER NOT NULL, " +
            "`outcome` TEXT, " +
            "`responseBytes` INTEGER, " +
            "`inputTokens` INTEGER, " +
            "`outputTokens` INTEGER, " +
            "`failureReason` TEXT)"

    const val CREATE_EGRESS_LOG_INDEX: String =
        "CREATE INDEX IF NOT EXISTS `index_egress_log_occurredAtEpochMillis` " +
            "ON `egress_log` (`occurredAtEpochMillis`)"

    /**
     * Adds the egress log.
     *
     * Purely additive: a new table and its index, no existing table touched. A
     * user upgrading from v1 starts with an empty log, which is truthful — the
     * app had no network code before this version, so there is nothing it could
     * be failing to show them.
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_EGRESS_LOG)
            db.execSQL(CREATE_EGRESS_LOG_INDEX)
        }
    }

    /** Every migration, for [DatabaseFactory] to register. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
