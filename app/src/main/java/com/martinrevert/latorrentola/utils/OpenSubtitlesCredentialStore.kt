package com.martinrevert.latorrentola.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** User account credentials for OpenSubtitles. */
data class OpenSubtitlesCredentials(
    /** OpenSubtitles account username. */
    val username: String,
    /** OpenSubtitles account password. */
    val password: String
)

/** Encrypts OpenSubtitles account credentials with a key held in Android Keystore. */
@Singleton
class OpenSubtitlesCredentialStore @Inject constructor(
    @ApplicationContext context: Context
) {
    /** Private preference file containing only encrypted account credentials. */
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** Encrypts and stores credentials without exposing plaintext in preferences. */
    fun save(credentials: OpenSubtitlesCredentials) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val plaintext = JSONObject()
            .put(JSON_USERNAME, credentials.username)
            .put(JSON_PASSWORD, credentials.password)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val ciphertext = cipher.doFinal(plaintext)
        preferences.edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .apply()
    }

    /** Decrypts the saved credentials, or returns `null` when none are configured. */
    fun load(): OpenSubtitlesCredentials? {
        val ciphertextValue = preferences.getString(KEY_CIPHERTEXT, null) ?: return null
        val ivValue = preferences.getString(KEY_IV, null)
            ?: error("OpenSubtitles credential ciphertext is missing its initialization vector")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getExistingKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(ivValue, Base64.NO_WRAP))
        )
        val plaintext = cipher.doFinal(Base64.decode(ciphertextValue, Base64.NO_WRAP))
        val json = JSONObject(String(plaintext, Charsets.UTF_8))
        return OpenSubtitlesCredentials(
            username = json.getString(JSON_USERNAME),
            password = json.getString(JSON_PASSWORD)
        )
    }

    /** Removes saved credentials and the encrypted values from preferences. */
    fun clear() {
        preferences.edit()
            .remove(KEY_IV)
            .remove(KEY_CIPHERTEXT)
            .apply()
        KeyStore.getInstance(ANDROID_KEY_STORE).apply {
            load(null)
            deleteEntry(KEY_ALIAS)
        }
    }

    /** Retrieves the existing Keystore key or creates it for the first save. */
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGenerator.generateKey()
    }

    /** Retrieves the key needed to decrypt saved credentials. */
    private fun getExistingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            ?: error("OpenSubtitles credential encryption key is unavailable")
    }

    private companion object {
        /** Android Keystore provider name. */
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        /** Authenticated encryption transformation. */
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        /** Keystore alias for the credential encryption key. */
        const val KEY_ALIAS = "opensubtitles_credentials"
        /** Isolated preference file for encrypted OpenSubtitles credentials. */
        const val PREFERENCES_NAME = "opensubtitles_credentials"
        /** Preference key for the AES-GCM initialization vector. */
        const val KEY_IV = "iv"
        /** Preference key for encrypted account JSON. */
        const val KEY_CIPHERTEXT = "ciphertext"
        /** JSON property containing the username. */
        const val JSON_USERNAME = "username"
        /** JSON property containing the password. */
        const val JSON_PASSWORD = "password"
        /** AES-GCM authentication-tag size. */
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
