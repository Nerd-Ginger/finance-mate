package dev.financemate.core.crypto

/**
 * Stores small secrets — the database key, the user's Anthropic API key —
 * so that they are never written to disk in plaintext.
 *
 * This is an interface rather than a concrete class for two reasons: the real
 * implementation needs a device to run (Android Keystore has no JVM equivalent),
 * and anything that handles secrets should be substitutable in tests so those
 * tests do not need a device either.
 */
public interface SecretVault {

    /** Stores [secret] under [alias], replacing any previous value. */
    public suspend fun put(alias: String, secret: ByteArray)

    /** Returns the secret for [alias], or null when nothing is stored. */
    public suspend fun get(alias: String): ByteArray?

    /** Removes [alias]. Succeeds whether or not anything was stored. */
    public suspend fun remove(alias: String)

    /**
     * Whether [alias] holds a secret.
     *
     * Deliberately does not decrypt, so the UI can show "AI is configured"
     * without triggering a biometric prompt.
     */
    public suspend fun contains(alias: String): Boolean

    public companion object {
        /** Alias for the SQLCipher passphrase protecting the ledger. */
        public const val ALIAS_DATABASE_KEY: String = "financemate.database.key"

        /** Alias for the user's Anthropic API key. */
        public const val ALIAS_ANTHROPIC_API_KEY: String = "financemate.anthropic.apikey"
    }
}

/** Raised when the vault cannot complete an operation. */
public class VaultException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Raised when a secret exists but cannot be decrypted because the user has not
 * authenticated, or because the key was invalidated.
 *
 * Android permanently invalidates a Keystore key when the user adds or removes a
 * biometric enrolment or changes their screen lock. That is a security feature,
 * not a bug — but it means the app must be able to tell "you need to unlock"
 * apart from "this secret is gone forever", because the recovery differs: one is
 * a prompt, the other is re-entering the API key or restoring a backup.
 */
public class VaultAuthenticationRequiredException(
    message: String,
    public val isPermanentlyInvalidated: Boolean,
    cause: Throwable? = null,
) : Exception(message, cause)
