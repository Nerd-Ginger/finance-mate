package dev.financemate

import android.content.Context
import dev.financemate.ai.egress.EgressRecorder
import dev.financemate.core.crypto.AndroidKeystoreVault
import dev.financemate.core.crypto.DatabaseKeyProvider
import dev.financemate.core.crypto.SecretVault
import dev.financemate.core.data.DatabaseFactory
import dev.financemate.core.data.FinanceMateDatabase
import dev.financemate.core.data.import.ImportPipeline
import dev.financemate.core.data.repository.SavingsRepository
import dev.financemate.egress.EgressLogRepository
import dev.financemate.egress.RoomEgressRecorder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Wires the app together.
 *
 * Hand-rolled rather than using a DI framework: the graph is small enough that
 * a container is easier to read than annotations and generated code, and it
 * keeps one more annotation processor out of the build.
 *
 * The database is created lazily and exactly once, because opening it requires
 * unsealing the passphrase from the Keystore — work that should happen when the
 * ledger is first needed, not during Application.onCreate where it would block
 * startup and, with biometric gating, prompt before the user has asked for
 * anything.
 */
public class AppContainer(context: Context) {

    public val appContext: Context = context.applicationContext
    private val databaseMutex = Mutex()

    @Volatile
    private var database: FinanceMateDatabase? = null

    public val vault: SecretVault by lazy {
        AndroidKeystoreVault(appContext)
    }

    private val databaseKeyProvider: DatabaseKeyProvider by lazy {
        DatabaseKeyProvider(vault)
    }

    public suspend fun database(): FinanceMateDatabase {
        database?.let { return it }
        return databaseMutex.withLock {
            database ?: run {
                // A fresh copy each time: SQLCipher zeroes the array it is given,
                // so the provider's value must not be reused.
                val passphrase = databaseKeyProvider.getOrCreate().copyOf()
                DatabaseFactory.create(appContext, passphrase).also { database = it }
            }
        }
    }

    public suspend fun savingsRepository(): SavingsRepository = SavingsRepository(database())

    public suspend fun importPipeline(): ImportPipeline = ImportPipeline(database())

    public suspend fun egressLog(): EgressLogRepository = EgressLogRepository(database().egressLogDao())

    /**
     * The recorder every transport must be wrapped in.
     *
     * There is no unwrapped alternative exposed here on purpose: the only way to
     * obtain a transport from this container should be one that logs, so that
     * "every request is recorded" is a property of the wiring rather than of
     * everyone remembering.
     */
    public suspend fun egressRecorder(): EgressRecorder = RoomEgressRecorder(database().egressLogDao())
}
