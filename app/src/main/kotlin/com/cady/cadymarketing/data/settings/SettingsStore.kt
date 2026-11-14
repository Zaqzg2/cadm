package com.cady.cadymarketing.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cady.cadymarketing.domain.engine.GatewayConfig
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** What the Studio remembers between sessions. Null means "never chosen". */
data class StudioPrefs(
    val channel: String? = null,
    val tone: String? = null,
    val angle: String? = null,
    val designId: Int? = null,
)

/** Small per-device settings. They never sync to Firestore. */
@Singleton
class SettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val preferences: Flow<Preferences> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }

    /** The last email used to sign in, so the login field is pre-filled after a sign-out. */
    val lastEmail: Flow<String> = preferences.map { it[LAST_EMAIL].orEmpty() }

    suspend fun setLastEmail(email: String) {
        dataStore.edit { it[LAST_EMAIL] = email }
    }

    /** The AI gateway link and token: kept on this device only. */
    val gatewayConfig: Flow<GatewayConfig> = preferences.map {
        GatewayConfig(url = it[GATEWAY_URL].orEmpty(), token = it[GATEWAY_TOKEN].orEmpty())
    }

    suspend fun setGatewayConfig(config: GatewayConfig) {
        dataStore.edit {
            it[GATEWAY_URL] = config.url.trim()
            it[GATEWAY_TOKEN] = config.token.trim()
        }
    }

    val studioPrefs: Flow<StudioPrefs> = preferences.map {
        StudioPrefs(
            channel = it[STUDIO_CHANNEL],
            tone = it[STUDIO_TONE],
            angle = it[STUDIO_ANGLE],
            designId = it[STUDIO_DESIGN],
        )
    }

    suspend fun setStudioPrefs(prefs: StudioPrefs) {
        dataStore.edit { store ->
            prefs.channel?.let { store[STUDIO_CHANNEL] = it }
            prefs.tone?.let { store[STUDIO_TONE] = it }
            prefs.angle?.let { store[STUDIO_ANGLE] = it }
            prefs.designId?.let { store[STUDIO_DESIGN] = it }
        }
    }

    /** The phrases used recently (see PhraseMemory), oldest first. */
    val usedPhrases: Flow<List<String>> = preferences.map { store ->
        store[USED_PHRASES].orEmpty().split(SEPARATOR).filter { it.isNotEmpty() }
    }

    suspend fun setUsedPhrases(phrases: List<String>) {
        dataStore.edit { it[USED_PHRASES] = phrases.joinToString(SEPARATOR) }
    }

    private companion object {
        const val SEPARATOR = "\u001F"
        val LAST_EMAIL = stringPreferencesKey("last_email")
        val GATEWAY_URL = stringPreferencesKey("gateway_url")
        val GATEWAY_TOKEN = stringPreferencesKey("gateway_token")
        val STUDIO_CHANNEL = stringPreferencesKey("studio_channel")
        val STUDIO_TONE = stringPreferencesKey("studio_tone")
        val STUDIO_ANGLE = stringPreferencesKey("studio_angle")
        val STUDIO_DESIGN = intPreferencesKey("studio_design")
        val USED_PHRASES = stringPreferencesKey("used_phrases")
    }
}
