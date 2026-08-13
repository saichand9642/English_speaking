package com.speak.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "speak_settings")

/** User preferences. Local only; nothing here leaves the phone. */
class SettingsStore(private val context: Context) {

    data class Settings(
        val onboardingComplete: Boolean = false,
        /** Use Gemini when a key is set and the phone is online. */
        val preferOnline: Boolean = false,
        val geminiModel: String = "gemini-2.5-flash",
        /** Index into the built-in topic rotation. */
        val topicIndex: Int = 0,
        val darkTheme: DarkThemeSetting = DarkThemeSetting.SYSTEM,
        /** Speak corrections aloud as well as showing them. */
        val speakCorrections: Boolean = false
    )

    enum class DarkThemeSetting { SYSTEM, LIGHT, DARK }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            onboardingComplete = prefs[KEY_ONBOARDING] ?: false,
            preferOnline = prefs[KEY_PREFER_ONLINE] ?: false,
            geminiModel = prefs[KEY_GEMINI_MODEL] ?: "gemini-2.5-flash",
            topicIndex = prefs[KEY_TOPIC_INDEX] ?: 0,
            darkTheme = runCatching {
                DarkThemeSetting.valueOf(prefs[KEY_DARK_THEME] ?: DarkThemeSetting.SYSTEM.name)
            }.getOrDefault(DarkThemeSetting.SYSTEM),
            speakCorrections = prefs[KEY_SPEAK_CORRECTIONS] ?: false
        )
    }

    suspend fun setOnboardingComplete(complete: Boolean) = put(KEY_ONBOARDING, complete)
    suspend fun setPreferOnline(prefer: Boolean) = put(KEY_PREFER_ONLINE, prefer)
    suspend fun setSpeakCorrections(speak: Boolean) = put(KEY_SPEAK_CORRECTIONS, speak)

    suspend fun setGeminiModel(model: String) {
        context.dataStore.edit { it[KEY_GEMINI_MODEL] = model.trim() }
    }

    suspend fun setTopicIndex(index: Int) {
        context.dataStore.edit { it[KEY_TOPIC_INDEX] = index }
    }

    suspend fun setDarkTheme(setting: DarkThemeSetting) {
        context.dataStore.edit { it[KEY_DARK_THEME] = setting.name }
    }

    private suspend fun put(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }

    private companion object {
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_complete")
        val KEY_PREFER_ONLINE = booleanPreferencesKey("prefer_online")
        val KEY_GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val KEY_TOPIC_INDEX = intPreferencesKey("topic_index")
        val KEY_DARK_THEME = stringPreferencesKey("dark_theme")
        val KEY_SPEAK_CORRECTIONS = booleanPreferencesKey("speak_corrections")
    }
}
