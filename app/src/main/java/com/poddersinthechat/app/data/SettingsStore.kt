package com.poddersinthechat.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) {
    AUTO("Auto"),
    DARK("Dark"),
    LIGHT("Light")
}

/**
 * User preferences, backed by SharedPreferences and exposed as StateFlows so
 * the UI reacts to changes immediately (e.g. switching the theme).
 *
 * Shared between the app class, the player manager and the view model —
 * everyone reads/writes the same keys through this single wrapper.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ----- Theme -----

    private val _themeMode = MutableStateFlow(readThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    private fun readThemeMode(): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: ThemeMode.DARK.name) }
            .getOrDefault(ThemeMode.DARK)

    /** Resolved dark-theme flag for the current [themeMode] (AUTO = system). */
    fun isDarkTheme(systemInDark: Boolean): Boolean = when (_themeMode.value) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.AUTO -> systemInDark
    }

    // ----- Playback -----

    /** Speed applied when a new episode starts (user can still cycle in-player). */
    private val _playbackSpeed = MutableStateFlow(prefs.getFloat(KEY_SPEED, 1.0f))
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    fun setPlaybackSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_SPEED, speed).apply()
        _playbackSpeed.value = speed
    }

    private val _autoPlayNext = MutableStateFlow(prefs.getBoolean(KEY_AUTO_PLAY_NEXT, true))
    val autoPlayNext: StateFlow<Boolean> = _autoPlayNext.asStateFlow()

    fun setAutoPlayNext(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_PLAY_NEXT, enabled).apply()
        _autoPlayNext.value = enabled
    }

    // ----- Daily feed refresh -----

    private val _autoRefreshEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_REFRESH, true))
    val autoRefreshEnabled: StateFlow<Boolean> = _autoRefreshEnabled.asStateFlow()

    fun setAutoRefreshEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_REFRESH, enabled).apply()
        _autoRefreshEnabled.value = enabled
    }

    private val _refreshHourOfDay = MutableStateFlow(prefs.getInt(KEY_REFRESH_HOUR, DEFAULT_REFRESH_HOUR))
    val refreshHourOfDay: StateFlow<Int> = _refreshHourOfDay.asStateFlow()

    fun setRefreshHourOfDay(hour: Int) {
        prefs.edit().putInt(KEY_REFRESH_HOUR, hour.coerceIn(0, 23)).apply()
        _refreshHourOfDay.value = hour.coerceIn(0, 23)
    }

    // ----- Offline downloads -----

    /** 0 = never auto-delete; otherwise days to keep a download. */
    private val _retentionDays = MutableStateFlow(prefs.getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS))
    val retentionDays: StateFlow<Int> = _retentionDays.asStateFlow()

    fun setRetentionDays(days: Int) {
        prefs.edit().putInt(KEY_RETENTION_DAYS, days.coerceAtLeast(0)).apply()
        _retentionDays.value = days.coerceAtLeast(0)
    }

    companion object {
        const val PREFS_NAME = "podders_settings"
        const val DEFAULT_RETENTION_DAYS = 7
        const val DEFAULT_REFRESH_HOUR = 12

        private const val KEY_THEME = "theme_mode"
        private const val KEY_SPEED = "playback_speed"
        private const val KEY_AUTO_PLAY_NEXT = "auto_play_next"
        private const val KEY_AUTO_REFRESH = "auto_refresh_enabled"
        private const val KEY_REFRESH_HOUR = "refresh_hour_of_day"
        private const val KEY_RETENTION_DAYS = "download_retention_days"
    }
}
