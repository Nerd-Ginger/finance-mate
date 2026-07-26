package dev.financemate.core.crypto

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DatabaseKeyProviderTest {

    private val vault = InMemorySecretVault()
    private val provider = DatabaseKeyProvider(vault)

    @Test
    fun `generates a 256-bit key on first run`() = runTest {
        val key = provider.getOrCreate()
        key.size shouldBe DatabaseKeyProvider.KEY_LENGTH_BYTES
        key.size shouldBe 32
    }

    @Test
    fun `returns the same key on subsequent runs`() = runTest {
        // If this ever returned a fresh key, the existing ledger would become
        // permanently unreadable — the worst possible failure in this class.
        val first = provider.getOrCreate()
        val second = provider.getOrCreate()
        second shouldBe first
    }

    @Test
    fun `generated keys are not predictable`() = runTest {
        val a = DatabaseKeyProvider(InMemorySecretVault()).getOrCreate()
        val b = DatabaseKeyProvider(InMemorySecretVault()).getOrCreate()
        a shouldNotBe b
    }

    @Test
    fun `key is not all zeroes`() = runTest {
        // Cheap guard against a SecureRandom that failed to seed.
        val key = provider.getOrCreate()
        key.all { it == 0.toByte() } shouldBe false
    }

    @Test
    fun `exists reports first run correctly`() = runTest {
        provider.exists() shouldBe false
        provider.getOrCreate()
        provider.exists() shouldBe true
    }

    @Test
    fun `refuses to regenerate over a corrupt key`() = runTest {
        // Silently replacing a malformed key would orphan the existing database.
        // Failing loudly gives the user a chance to restore rather than
        // discovering their history is gone.
        vault.seed(SecretVault.ALIAS_DATABASE_KEY, ByteArray(16))

        val error = shouldThrow<VaultException> { provider.getOrCreate() }
        error.message!!.contains("Refusing to regenerate") shouldBe true

        // And the bad key is left untouched, not overwritten.
        vault.get(SecretVault.ALIAS_DATABASE_KEY)!!.size shouldBe 16
    }

    @Test
    fun `destroy removes the key`() = runTest {
        provider.getOrCreate()
        provider.destroy()
        provider.exists() shouldBe false
    }

    @Test
    fun `propagates vault failures rather than generating a replacement key`() = runTest {
        vault.seed(SecretVault.ALIAS_DATABASE_KEY, ByteArray(32))
        vault.failOnGet = VaultAuthenticationRequiredException(
            "locked",
            isPermanentlyInvalidated = false,
        )

        // A locked vault must not be mistaken for an empty one. Generating a new
        // key here would be indistinguishable from data loss.
        shouldThrow<VaultAuthenticationRequiredException> { provider.getOrCreate() }
    }
}

class InMemorySecretVaultTest {

    private val vault = InMemorySecretVault()

    @Test
    fun `round-trips a secret`() = runTest {
        vault.put("alias", byteArrayOf(1, 2, 3))
        vault.get("alias") shouldBe byteArrayOf(1, 2, 3)
    }

    @Test
    fun `returns null for an unknown alias`() = runTest {
        vault.get("nope") shouldBe null
    }

    @Test
    fun `contains does not require reading the secret`() = runTest {
        // The UI shows "AI configured" without prompting for biometrics, so
        // contains() must not decrypt.
        vault.put("alias", byteArrayOf(9))
        vault.contains("alias") shouldBe true
        vault.readCount shouldBe 0
    }

    @Test
    fun `remove deletes the secret`() = runTest {
        vault.put("alias", byteArrayOf(9))
        vault.remove("alias")
        vault.contains("alias") shouldBe false
    }

    @Test
    fun `stored secrets are defensively copied`() = runTest {
        // A caller mutating its array afterwards must not corrupt the vault.
        val original = byteArrayOf(1, 2, 3)
        vault.put("alias", original)
        original[0] = 99
        vault.get("alias") shouldBe byteArrayOf(1, 2, 3)
    }
}
