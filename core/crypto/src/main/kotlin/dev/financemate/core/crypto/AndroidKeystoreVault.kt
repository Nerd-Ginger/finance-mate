package dev.financemate.core.crypto

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * A [SecretVault] backed by the Android Keystore.
 *
 * ## How this protects secrets
 *
 * The AES key never leaves the Keystore, and on devices with a secure element it
 * never leaves that hardware — it cannot be extracted even from a rooted device.
 * What we store in preferences is only ciphertext, which is useless without the
 * key. So the threat this defends against is real: someone with the device's
 * filesystem (a backup, a stolen phone, a malicious app with storage access)
 * gets nothing.
 *
 * ## Why the ciphertext sits in plain SharedPreferences
 *
 * Because it does not need protecting. Encrypting already-encrypted bytes with a
 * second key adds a second thing to lose and no security. The security boundary
 * is the Keystore key, not the file the ciphertext lives in.
 *
 * ## Biometric gating
 *
 * [requireUserAuthentication] makes the key usable only after a recent unlock.
 * That suits the API key. It suits the *database* key much less well: the app
 * would fail to open its own database whenever the screen had been locked long
 * enough, including for background work. So the database key is stored without
 * it by default and the choice is left to the caller.
 */
public class AndroidKeystoreVault(
    context: Context,
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    private val requireUserAuthentication: Boolean = false,
    private val authenticationValiditySeconds: Int = 300,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SecretVault {

    private val appContext = context.applicationContext

    private val preferences: SharedPreferences by lazy {
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    override suspend fun put(alias: String, secret: ByteArray): Unit = withContext(ioDispatcher) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, resolveKey())
            }
            val ciphertext = cipher.doFinal(secret)

            // The GCM IV is generated per encryption and must be kept with the
            // ciphertext. It is not secret; reusing one would be catastrophic,
            // which is why it is never derived or fixed.
            val encoded = buildString {
                append(Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                append(IV_SEPARATOR)
                append(Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            }
            preferences.edit().putString(alias, encoded).apply()
        } catch (e: Exception) {
            throw e.asVaultFailure("Could not store secret '$alias'")
        }
    }

    override suspend fun get(alias: String): ByteArray? = withContext(ioDispatcher) {
        val encoded = preferences.getString(alias, null) ?: return@withContext null
        val separator = encoded.indexOf(IV_SEPARATOR)
        if (separator <= 0) {
            throw VaultException("Stored secret '$alias' is malformed")
        }

        try {
            val iv = Base64.decode(encoded.substring(0, separator), Base64.NO_WRAP)
            val ciphertext = Base64.decode(encoded.substring(separator + 1), Base64.NO_WRAP)

            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, resolveKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            }
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw e.asVaultFailure("Could not read secret '$alias'")
        }
    }

    override suspend fun remove(alias: String): Unit = withContext(ioDispatcher) {
        preferences.edit().remove(alias).apply()
    }

    override suspend fun contains(alias: String): Boolean = withContext(ioDispatcher) {
        preferences.contains(alias)
    }

    /**
     * Deletes the Keystore key itself, rendering every stored secret permanently
     * unreadable.
     *
     * This is the "forget everything" path — used when the user resets the app.
     * It cannot be undone, and no backup will help, which is the point.
     */
    public suspend fun destroyKeyMaterial(): Unit = withContext(ioDispatcher) {
        runCatching { keyStore.deleteEntry(keyAlias) }
        preferences.edit().clear().apply()
    }

    private fun resolveKey(): SecretKey {
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_BITS)
            // Randomised encryption is required for GCM and stops identical
            // plaintexts producing identical ciphertexts.
            .setRandomizedEncryptionRequired(true)
            .apply {
                if (requireUserAuthentication) {
                    setUserAuthenticationRequired(true)
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(authenticationValiditySeconds)
                }
            }
            .build()

        generator.init(spec)
        return generator.generateKey()
    }

    /**
     * Distinguishes "needs authentication" and "key is gone" from ordinary
     * failure, because the recovery is different for each.
     */
    private fun Exception.asVaultFailure(message: String): Exception = when (this) {
        is KeyPermanentlyInvalidatedException -> VaultAuthenticationRequiredException(
            "$message: the key was invalidated by a change to the device's " +
                "screen lock or biometric enrolment.",
            isPermanentlyInvalidated = true,
            cause = this,
        )

        is UnrecoverableKeyException -> VaultAuthenticationRequiredException(
            "$message: the key could not be recovered. Unlock the device and retry.",
            isPermanentlyInvalidated = false,
            cause = this,
        )

        is VaultException, is VaultAuthenticationRequiredException -> this

        // Never include the secret or the exception's message in the text we
        // surface; a crash reporter would then hold the thing we are protecting.
        else -> VaultException("$message (${this.javaClass.simpleName})", this)
    }

    public companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFERENCES_NAME = "financemate_vault"
        private const val DEFAULT_KEY_ALIAS = "financemate.vault.masterkey"
        private const val AES_KEY_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val IV_SEPARATOR = ':'
    }
}
