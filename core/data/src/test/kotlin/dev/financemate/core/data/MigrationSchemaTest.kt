package dev.financemate.core.data

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File

/**
 * Checks each hand-written migration against the schema Room exported.
 *
 * ## The bug this exists to catch
 *
 * A migration that disagrees with the entity is invisible during development.
 * Fresh installs never run migrations — Room creates every table from the
 * entities — so the app works perfectly on every machine that wiped its data,
 * and fails only for users upgrading with real history. Those are exactly the
 * users whose data cannot be replaced.
 *
 * ## What this does not cover
 *
 * It compares DDL text, not behaviour: it will not catch a migration that
 * creates the right table but forgets to backfill it, and it says nothing about
 * SQLCipher, which needs a device. Data-moving migrations need their own tests
 * against a real database. The v1→v2 migration is purely additive, so text
 * equivalence is the whole of its correctness.
 */
class MigrationSchemaTest {

    @Test
    fun egressLogMigrationMatchesTheExportedSchema() {
        val exported = exportedTable(version = 2, tableName = "egress_log")

        exported.createSql shouldBe Migrations.CREATE_EGRESS_LOG
        exported.indexSql shouldBe listOf(Migrations.CREATE_EGRESS_LOG_INDEX)
    }

    @Test
    fun theMigrationIsAdditiveOnly() {
        // Every v1 table must survive into v2 unchanged. If one of them ever
        // needs to change, this assertion should fail and force the author to
        // write and test a real data migration rather than adding a table and
        // hoping.
        val v1 = exportedTableNames(version = 1)
        val v2 = exportedTableNames(version = 2)

        (v2 - v1) shouldBe setOf("egress_log")
        (v1 - v2) shouldBe emptySet()

        v1.forEach { table ->
            exportedTable(1, table).createSql shouldBe exportedTable(2, table).createSql
        }
    }

    // --- Reading the exported schema ---------------------------------------

    private data class ExportedTable(val createSql: String, val indexSql: List<String>)

    private fun schemaFile(version: Int): File {
        // Unit tests run with the module directory as the working directory.
        val file = File("schemas/dev.financemate.core.data.FinanceMateDatabase/$version.json")
        check(file.exists()) { "No exported schema at ${file.absolutePath}" }
        return file
    }

    private fun entities(version: Int) =
        Json.parseToJsonElement(schemaFile(version).readText())
            .jsonObject.getValue("database")
            .jsonObject.getValue("entities")
            .jsonArray.map { it.jsonObject }

    private fun exportedTableNames(version: Int): Set<String> =
        entities(version).map { it.getValue("tableName").jsonPrimitive.content }.toSet()

    private fun exportedTable(version: Int, tableName: String): ExportedTable {
        val entity = entities(version).single {
            it.getValue("tableName").jsonPrimitive.content == tableName
        }
        // Room writes the table name as a placeholder so the same DDL can be
        // reused for the temporary tables it creates during a rebuild.
        fun substitute(sql: String) = sql.replace("\${TABLE_NAME}", tableName)

        return ExportedTable(
            createSql = substitute(entity.getValue("createSql").jsonPrimitive.content),
            indexSql = entity["indices"]?.jsonArray.orEmpty().map {
                substitute(it.jsonObject.getValue("createSql").jsonPrimitive.content)
            },
        )
    }
}
