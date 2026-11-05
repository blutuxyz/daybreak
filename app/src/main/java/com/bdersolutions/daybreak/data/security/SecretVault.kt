package com.bdersolutions.daybreak.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the long-lived WebUntis mobile key encrypted with an AES/GCM key that is
 * generated and kept inside the Android Keystore. The ciphertext lives in private
 * SharedPreferences; the key material never leaves the Keystore.
 */
class SecretVault(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun encrypt(plainText: String): String? {
        if (plainText.isBlank()) return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val payload = ByteArray(cipher.iv.size + encrypted.size)
            System.arraycopy(cipher.iv, 0, payload, 0, cipher.iv.size)
            System.arraycopy(encrypted, 0, payload, cipher.iv.size, encrypted.size)
            Base64.encodeToString(payload, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun decrypt(payload: String?): String? {
        if (payload.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(payload, Base64.NO_WRAP)
            if (bytes.size <= IV_LENGTH_BYTES) return null
            val iv = bytes.copyOfRange(0, IV_LENGTH_BYTES)
            val cipherText = bytes.copyOfRange(IV_LENGTH_BYTES, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun getStoredSecret(): String? = decrypt(preferences.getString(KEY_SECRET, null))

    @Synchronized
    fun storeSecret(secret: String): Boolean {
        val encrypted = encrypt(secret) ?: return false
        return preferences.edit().putString(KEY_SECRET, encrypted).commit()
    }

    /** The WebUntis password, kept so class services and notices can sign in on the web. */
    @Synchronized
    fun getStoredPassword(): String? = decrypt(preferences.getString(KEY_PASSWORD, null))

    @Synchronized
    fun storePassword(password: String): Boolean {
        val encrypted = encrypt(password) ?: return false
        return preferences.edit().putString(KEY_PASSWORD, encrypted).commit()
    }

    @Synchronized
    fun clearPassword() {
        preferences.edit().remove(KEY_PASSWORD).commit()
    }

    @Synchronized
    fun clear() {
        preferences.edit().remove(KEY_SECRET).remove(KEY_PASSWORD).commit()
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "daybreak_webuntis_secret_v1"
        const val PREFS_NAME = "daybreak_secure"
        const val KEY_SECRET = "webuntis_secret"
        const val KEY_PASSWORD = "webuntis_password"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val TAG_LENGTH_BITS = 128
    }
}
