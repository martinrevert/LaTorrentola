package com.martinrevert.latorrentola.service.storage.remote

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

/**
 * Credentials for connecting to an SMB server host.
 */
data class SmbCredentials(
    val host: String,
    val username: String,
    val password: String,
    val domain: String? = null
)

/**
 * Encrypts and persists SMB credentials correlated by server IP / hostname using
 * a key secured by Android Keystore.
 */
@Singleton
class SmbCredentialStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** Normalizes a server host or IP address for uniform key correlation. */
    fun normalizeHost(host: String): String = host.trim().lowercase()

    /**
     * Encrypts and persists credentials for [credentials.host].
     */
    @Synchronized
    fun save(credentials: SmbCredentials) {
        val all = loadAllMap().toMutableMap()
        all[normalizeHost(credentials.host)] = credentials
        saveAllMap(all)
    }

    /**
     * Decrypts and retrieves saved credentials for [host], or returns null if none are saved.
     */
    @Synchronized
    fun load(host: String): SmbCredentials? {
        val normalized = normalizeHost(host)
        return loadAllMap()[normalized]
    }

    /**
     * Returns true if encrypted credentials exist for [host].
     */
    @Synchronized
    fun hasCredentials(host: String): Boolean {
        return load(host) != null
    }

    /**
     * Clears credentials for the specified [host].
     */
    @Synchronized
    fun clear(host: String) {
        val all = loadAllMap().toMutableMap()
        if (all.remove(normalizeHost(host)) != null) {
            if (all.isEmpty()) {
                clearAll()
            } else {
                saveAllMap(all)
            }
        }
    }

    /**
     * Removes all saved SMB credentials from preferences and deletes the Keystore key.
     */
    @Synchronized
    fun clearAll() {
        preferences.edit()
            .remove(KEY_IV)
            .remove(KEY_CIPHERTEXT)
            .apply()
        try {
            KeyStore.getInstance(ANDROID_KEY_STORE).apply {
                load(null)
                deleteEntry(KEY_ALIAS)
            }
        } catch (_: Exception) {}
    }

    private fun loadAllMap(): Map<String, SmbCredentials> {
        val ciphertextValue = preferences.getString(KEY_CIPHERTEXT, null) ?: return emptyMap()
        val ivValue = preferences.getString(KEY_IV, null) ?: return emptyMap()
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getExistingKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(ivValue, Base64.NO_WRAP))
            )
            val plaintext = cipher.doFinal(Base64.decode(ciphertextValue, Base64.NO_WRAP))
            val json = JSONObject(String(plaintext, Charsets.UTF_8))
            val result = mutableMapOf<String, SmbCredentials>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val hostKey = keys.next()
                val entry = json.getJSONObject(hostKey)
                result[hostKey] = SmbCredentials(
                    host = entry.optString(JSON_HOST, hostKey),
                    username = entry.optString(JSON_USERNAME),
                    password = entry.optString(JSON_PASSWORD),
                    domain = entry.optString(JSON_DOMAIN).takeUnless { it.isNullOrBlank() }
                )
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveAllMap(map: Map<String, SmbCredentials>) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val root = JSONObject()
            for ((key, creds) in map) {
                val entry = JSONObject()
                    .put(JSON_HOST, creds.host)
                    .put(JSON_USERNAME, creds.username)
                    .put(JSON_PASSWORD, creds.password)
                    .put(JSON_DOMAIN, creds.domain.orEmpty())
                root.put(key, entry)
            }
            val plaintext = root.toString().toByteArray(Charsets.UTF_8)
            val ciphertext = cipher.doFinal(plaintext)
            preferences.edit()
                .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .apply()
        } catch (_: Exception) {}
    }

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

    private fun getExistingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            ?: error("SMB credential encryption key is unavailable")
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_ALIAS = "smb_credentials_key"
        const val PREFERENCES_NAME = "smb_credentials_store"
        const val KEY_IV = "iv"
        const val KEY_CIPHERTEXT = "ciphertext"
        const val JSON_HOST = "host"
        const val JSON_USERNAME = "username"
        const val JSON_PASSWORD = "password"
        const val JSON_DOMAIN = "domain"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
