package com.cady.cadymarketing.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.cady.cadymarketing.domain.engine.GatewayConfig
import com.cady.cadymarketing.domain.engine.ReportSources
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

    /** The card style the person chose (a CadyStyle code), or null when never chosen. */
    val uiStyle: Flow<String?> = preferences.map { it[UI_STYLE] }

    suspend fun setUiStyle(code: String) {
        dataStore.edit { it[UI_STYLE] = code }
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

    /** Where the reports read orders from (the website's and Kady's own collection and field names). */
    val reportSources: Flow<ReportSources> = preferences.map {
        val defaults = ReportSources()
        ReportSources(
            siteCollection = it[SITE_COLLECTION] ?: defaults.siteCollection,
            siteField = it[SITE_FIELD] ?: defaults.siteField,
            kadyEnabled = it[KADY_ENABLED] ?: defaults.kadyEnabled,
            kadyCollection = it[KADY_COLLECTION] ?: defaults.kadyCollection,
            kadyField = it[KADY_FIELD] ?: defaults.kadyField,
        )
    }

    suspend fun setReportSources(sources: ReportSources) {
        dataStore.edit {
            it[SITE_COLLECTION] = sources.siteCollection.trim()
            it[SITE_FIELD] = sources.siteField.trim()
            it[KADY_ENABLED] = sources.kadyEnabled
            it[KADY_COLLECTION] = sources.kadyCollection.trim()
            it[KADY_FIELD] = sources.kadyField.trim()
        }
    }

    // ------------------------------------------------------------------ phase 5: sync, reminders, role cache

    /** Sync soon after a change and whenever the app opens. On by default. */
    val autoSync: Flow<Boolean> = preferences.map { it[AUTO_SYNC] ?: true }

    suspend fun setAutoSync(on: Boolean) {
        dataStore.edit { it[AUTO_SYNC] = on }
    }

    /** A notification when an approved post's time comes. On by default. */
    val remindersOn: Flow<Boolean> = preferences.map { it[REMINDERS_ON] ?: true }

    suspend fun setRemindersOn(on: Boolean) {
        dataStore.edit { it[REMINDERS_ON] = on }
    }

    /** The check every 15 minutes for posts that wait for the manager or came back to the marketer. On by default. */
    val reviewCheckOn: Flow<Boolean> = preferences.map { it[REVIEW_CHECK_ON] ?: true }

    suspend fun setReviewCheckOn(on: Boolean) {
        dataStore.edit { it[REVIEW_CHECK_ON] = on }
    }

    /** "id|scheduledAt" of every reminder that was handed to the system, so only changes are handed over again. */
    val scheduledReminders: Flow<Set<String>> = preferences.map { it[SCHEDULED_REMINDERS].orEmpty() }

    suspend fun setScheduledReminders(keys: Set<String>) {
        dataStore.edit { it[SCHEDULED_REMINDERS] = keys }
    }

    /** The posts (id:updatedAt) the periodic check already told the person about, oldest first. */
    val notifiedKeys: Flow<List<String>> = preferences.map { store ->
        store[NOTIFIED_KEYS].orEmpty().split(SEPARATOR).filter { it.isNotEmpty() }
    }

    suspend fun setNotifiedKeys(keys: List<String>) {
        dataStore.edit { it[NOTIFIED_KEYS] = keys.joinToString(SEPARATOR) }
    }

    /** The role found at the last sign-in check, as "uid|role": lets the app open offline. */
    val cachedRole: Flow<String> = preferences.map { it[CACHED_ROLE].orEmpty() }

    suspend fun setCachedRole(value: String) {
        dataStore.edit { it[CACHED_ROLE] = value }
    }

    /** When the last backup file was written (epoch milliseconds), or 0. */
    val lastBackupAt: Flow<Long> = preferences.map { it[LAST_BACKUP_AT] ?: 0L }

    suspend fun setLastBackupAt(at: Long) {
        dataStore.edit { it[LAST_BACKUP_AT] = at }
    }

    private companion object {
        const val SEPARATOR = "\u001F"
        val LAST_EMAIL = stringPreferencesKey("last_email")
        val UI_STYLE = stringPreferencesKey("ui_style")
        val GATEWAY_URL = stringPreferencesKey("gateway_url")
        val GATEWAY_TOKEN = stringPreferencesKey("gateway_token")
        val STUDIO_CHANNEL = stringPreferencesKey("studio_channel")
        val STUDIO_TONE = stringPreferencesKey("studio_tone")
        val STUDIO_ANGLE = stringPreferencesKey("studio_angle")
        val STUDIO_DESIGN = intPreferencesKey("studio_design")
        val USED_PHRASES = stringPreferencesKey("used_phrases")
        val SITE_COLLECTION = stringPreferencesKey("report_site_collection")
        val SITE_FIELD = stringPreferencesKey("report_site_field")
        val KADY_ENABLED = booleanPreferencesKey("report_kady_enabled")
        val KADY_COLLECTION = stringPreferencesKey("report_kady_collection")
        val KADY_FIELD = stringPreferencesKey("report_kady_field")
        val AUTO_SYNC = booleanPreferencesKey("auto_sync")
        val REMINDERS_ON = booleanPreferencesKey("reminders_on")
        val REVIEW_CHECK_ON = booleanPreferencesKey("review_check_on")
        val SCHEDULED_REMINDERS = stringSetPreferencesKey("scheduled_reminders")
        val NOTIFIED_KEYS = stringPreferencesKey("notified_keys")
        val CACHED_ROLE = stringPreferencesKey("cached_role")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
    }
}
