package dev.financemate.core.crypto

import java.security.SecureRandom

/**
 * Supplies the passphrase that encrypts the ledger database.
 *
 * The key is 256 bits of [SecureRandom] output, generated once on first launch
 * and then sealed in the [SecretVault]. It is deliberately *not* derived from
 * anything the user types: a passphrase people can remember is a passphrase that
 * can be guessed, and there is no need for one here because the Keystore already
 * binds the secret to the device.
 *
 * ## The trade-off this makes explicit
 *
 * Because the key lives only in the device's Keystore, **it cannot be exported,
 * and neither can the database.** A copied database file is unreadable on any
 * other device — including the user's next phone.
 *
 * That is the right default for a file full of someone's financial history, but
 * it makes the app's own encrypted export the only migration path, so that
 * export is a requirement rather than a nice-to-have. It is why cloud backup is
 * switched off in the manifest: a backup that silently cannot be restored is
 * worse than no backup, because the user believes they are covered.
 */
public class DatabaseKeyProvider(
    private val vault: SecretVault,
    private val alias: String = SecretVault.ALIAS_DATABASE_KEY,
) {

    /**
     * Returns the database key, creating and storing one if this is first run.
     *
     * Callers should hold the result for as short a time as possible and avoid
     * copying it; SQLCipher zeroes the array it is handed.
     */
    public suspend fun getOrCreate(): ByteArray {
        vault.get(alias)?.let { existing ->
            if (existing.size == KEY_LENGTH_BYTES) return existing
            // A stored key of the wrong length means something is corrupt.
            // Silently regenerating would orphan the existing database, so this
            // fails loudly instead.
            throw VaultException(
                "Stored database key has an unexpected length (${existing.size} bytes). " +
                    "Refusing to regenerate, because doing so would make the existing " +
                    "ledger permanently unreadable.",
            )
        }

        val fresh = ByteArray(KEY_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        vault.put(alias, fresh)
        return fresh
    }

    /** Whether a database key already exists — i.e. whether this is first run. */
    public suspend fun exists(): Boolean = vault.contains(alias)

    /**
     * Destroys the database key.
     *
     * The ledger becomes permanently unreadable. Only call this alongside
     * deleting the database file itself.
     */
    public suspend fun destroy() {
        vault.remove(alias)
    }

    public companion object {
        /** 256 bits, matching SQLCipher's AES-256. */
        public const val KEY_LENGTH_BYTES: Int = 32
    }
}
