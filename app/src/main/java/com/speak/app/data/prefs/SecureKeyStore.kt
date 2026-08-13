package com.speak.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Holds the optional Gemini API key.
 *
 * The key is the one genuinely sensitive value the app ever touches, so it is
 * kept in [EncryptedSharedPreferences] behind a hardware-backed master key rather
 * than in DataStore with the ordinary settings. It is never logged, never sent
 * anywhere except Google's own endpoint, and never included in a backup export.
 */
class SecureKeyStore(context: Context) {

    private val prefs: SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrNull()

    /**
     * True when the keystore itself could not be opened. The app stays fully
     * usable in that case; only the optional online path is unavailable.
     */
    val isAvailable: Boolean get() = prefs != null

    fun geminiKey(): String? = prefs?.getString(KEY_GEMINI, null)?.takeIf { it.isNotBlank() }

    val hasGeminiKey: Boolean get() = geminiKey() != null

    fun setGeminiKey(key: String?) {
        val store = prefs ?: return
        val cleaned = key?.trim()
        store.edit().apply {
            if (cleaned.isNullOrBlank()) remove(KEY_GEMINI) else putString(KEY_GEMINI, cleaned)
        }.apply()
    }

    /** For display in settings: never shows the whole key. */
    fun maskedGeminiKey(): String? = geminiKey()?.let { key ->
        if (key.length <= 8) "•".repeat(key.length)
        else key.take(4) + "•".repeat(key.length - 8) + key.takeLast(4)
    }

    private companion object {
        const val FILE_NAME = "speak_secure_prefs"
        const val KEY_GEMINI = "gemini_api_key"
    }
}
