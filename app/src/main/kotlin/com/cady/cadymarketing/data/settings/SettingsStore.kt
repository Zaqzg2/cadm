package com.cady.cadymarketing.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Small per-device settings. They never sync to Firestore. */
@Singleton
class SettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** The last email used to sign in, so the login field is pre-filled after a sign-out. */
    val lastEmail: Flow<String> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences[LAST_EMAIL].orEmpty() }

    suspend fun setLastEmail(email: String) {
        dataStore.edit { preferences -> preferences[LAST_EMAIL] = email }
    }

    private companion object {
        val LAST_EMAIL = stringPreferencesKey("last_email")
    }
}
