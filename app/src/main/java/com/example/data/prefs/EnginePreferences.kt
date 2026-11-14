package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.satyaDataStore: DataStore<Preferences> by preferencesDataStore(name = "satya_engine_prefs")

data class EngineSettings(
    val defaultThreads: Int = 8,
    val maxConcurrentDownloads: Int = 3,
    val wifiOnly: Boolean = false,
    val speedLimitKbps: Int = 0, // 0 = Unlimited
    val autoExtractMp3FromVideo: Boolean = false,
    val darkThemeEnabled: Boolean = true,
    val pcBridgePort: Int = 8765
)

class EnginePreferences(private val context: Context) {

    private object Keys {
        val DEFAULT_THREADS = intPreferencesKey("default_threads")
        val MAX_CONCURRENT = intPreferencesKey("max_concurrent")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val SPEED_LIMIT_KBPS = intPreferencesKey("speed_limit_kbps")
        val AUTO_EXTRACT_MP3 = booleanPreferencesKey("auto_extract_mp3")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
    }

    val settingsFlow: Flow<EngineSettings> = context.satyaDataStore.data.map { prefs ->
        EngineSettings(
            defaultThreads = (prefs[Keys.DEFAULT_THREADS] ?: 8).coerceIn(1, 16),
            maxConcurrentDownloads = (prefs[Keys.MAX_CONCURRENT] ?: 3).coerceIn(1, 5),
            wifiOnly = prefs[Keys.WIFI_ONLY] ?: false,
            speedLimitKbps = prefs[Keys.SPEED_LIMIT_KBPS] ?: 0,
            autoExtractMp3FromVideo = prefs[Keys.AUTO_EXTRACT_MP3] ?: false,
            darkThemeEnabled = prefs[Keys.DARK_THEME] ?: true
        )
    }

    suspend fun setDefaultThreads(threads: Int) {
        context.satyaDataStore.edit { it[Keys.DEFAULT_THREADS] = threads.coerceIn(1, 16) }
    }

    suspend fun setMaxConcurrent(count: Int) {
        context.satyaDataStore.edit { it[Keys.MAX_CONCURRENT] = count.coerceIn(1, 5) }
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        context.satyaDataStore.edit { it[Keys.WIFI_ONLY] = enabled }
    }

    suspend fun setSpeedLimitKbps(kbps: Int) {
        context.satyaDataStore.edit { it[Keys.SPEED_LIMIT_KBPS] = kbps.coerceAtLeast(0) }
    }

    suspend fun setAutoExtractMp3(enabled: Boolean) {
        context.satyaDataStore.edit { it[Keys.AUTO_EXTRACT_MP3] = enabled }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.satyaDataStore.edit { it[Keys.DARK_THEME] = enabled }
    }
}
