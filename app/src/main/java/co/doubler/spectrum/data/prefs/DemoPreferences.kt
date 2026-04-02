package co.doubler.spectrum.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    val isDemoEnabled: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[DEMO_MODE_KEY] ?: false }

    suspend fun setDemoEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[DEMO_MODE_KEY] = enabled }
    }

    companion object {
        private val DEMO_MODE_KEY = booleanPreferencesKey("demo_mode_enabled")
    }
}
