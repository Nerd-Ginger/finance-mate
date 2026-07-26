package dev.financemate.core.crypto

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A [SecretVault] that keeps secrets in memory, for tests.
 *
 * It stores plaintext, which is exactly right for a test double: the thing under
 * test is the *contract* — that a secret written can be read back, that removal
 * works, that `contains` does not require decryption. Keystore behaviour itself
 * can only be verified on a device, and that belongs in an instrumentation test.
 *
 * Failures can be simulated via [failOnGet] and [failOnPut] so callers can be
 * tested against a locked or invalidated vault without one.
 */
public class InMemorySecretVault(
    private val secrets: MutableMap<String, ByteArray> = mutableMapOf(),
) : SecretVault {

    private val mutex = Mutex()

    /** When set, [get] throws this instead of returning. */
    public var failOnGet: Exception? = null

    /** When set, [put] throws this instead of storing. */
    public var failOnPut: Exception? = null

    /** Number of successful [get] calls, for asserting a secret is not re-read needlessly. */
    public var readCount: Int = 0
        private set

    override suspend fun put(alias: String, secret: ByteArray) {
        failOnPut?.let { throw it }
        mutex.withLock { secrets[alias] = secret.copyOf() }
    }

    override suspend fun get(alias: String): ByteArray? {
        failOnGet?.let { throw it }
        return mutex.withLock {
            secrets[alias]?.copyOf()?.also { readCount++ }
        }
    }

    override suspend fun remove(alias: String) {
        mutex.withLock { secrets.remove(alias) }
    }

    override suspend fun contains(alias: String): Boolean =
        mutex.withLock { secrets.containsKey(alias) }

    /** Test helper: seed a secret without going through [put]. */
    public fun seed(alias: String, secret: ByteArray) {
        secrets[alias] = secret.copyOf()
    }

    public fun clear() {
        secrets.clear()
        readCount = 0
        failOnGet = null
        failOnPut = null
    }
}
