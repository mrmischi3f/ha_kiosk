package com.hakiosk.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.kioskDataStore: DataStore<Preferences> by preferencesDataStore(name = "kiosk_settings")

class SettingsRepository(context: Context, private val json: Json) {

    private val store = context.applicationContext.kioskDataStore
    private val watchlistSerializer = ListSerializer(WatchItem.serializer())
    private val stringListSerializer = ListSerializer(String.serializer())

    private object Keys {
        val haUrl = stringPreferencesKey("ha_url")
        val haToken = stringPreferencesKey("ha_token")
        val topLightsCount = intPreferencesKey("top_lights_count")
        val usageLookbackDays = intPreferencesKey("usage_lookback_days")
        val calendarEntities = stringSetPreferencesKey("calendar_entities")
        val weatherEntity = stringPreferencesKey("weather_entity")
        val musicEntity = stringPreferencesKey("music_entity")
        val favoriteEntities = stringPreferencesKey("favorite_entities")
        val watchlist = stringPreferencesKey("watchlist")
        val stockRefreshSec = intPreferencesKey("stock_refresh_sec")
        val sportsLeagues = stringSetPreferencesKey("sports_leagues")
        val use24hClock = booleanPreferencesKey("use_24h_clock")
        val idleTimeoutSec = intPreferencesKey("idle_timeout_sec")
        val nightModeEnabled = booleanPreferencesKey("night_mode_enabled")
        val nightStartHour = intPreferencesKey("night_start_hour")
        val nightEndHour = intPreferencesKey("night_end_hour")
        val nightBrightness = floatPreferencesKey("night_brightness")
        val lockTask = booleanPreferencesKey("lock_task")
    }

    val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    suspend fun toggleFavorite(entityId: String) = update { s ->
        val favorites = if (entityId in s.favoriteEntities) s.favoriteEntities - entityId
        else s.favoriteEntities + entityId
        s.copy(favoriteEntities = favorites)
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            loaded = true,
            haUrl = this[Keys.haUrl] ?: d.haUrl,
            haToken = this[Keys.haToken] ?: d.haToken,
            topLightsCount = this[Keys.topLightsCount] ?: d.topLightsCount,
            usageLookbackDays = this[Keys.usageLookbackDays] ?: d.usageLookbackDays,
            calendarEntities = this[Keys.calendarEntities] ?: d.calendarEntities,
            weatherEntity = this[Keys.weatherEntity] ?: d.weatherEntity,
            musicEntity = this[Keys.musicEntity] ?: d.musicEntity,
            favoriteEntities = this[Keys.favoriteEntities]
                ?.let { runCatching { json.decodeFromString(stringListSerializer, it) }.getOrNull() }
                ?: d.favoriteEntities,
            watchlist = this[Keys.watchlist]
                ?.let { runCatching { json.decodeFromString(watchlistSerializer, it) }.getOrNull() }
                ?: d.watchlist,
            stockRefreshSec = this[Keys.stockRefreshSec] ?: d.stockRefreshSec,
            sportsLeagues = this[Keys.sportsLeagues] ?: d.sportsLeagues,
            use24hClock = this[Keys.use24hClock] ?: d.use24hClock,
            idleTimeoutSec = this[Keys.idleTimeoutSec] ?: d.idleTimeoutSec,
            nightModeEnabled = this[Keys.nightModeEnabled] ?: d.nightModeEnabled,
            nightStartHour = this[Keys.nightStartHour] ?: d.nightStartHour,
            nightEndHour = this[Keys.nightEndHour] ?: d.nightEndHour,
            nightBrightness = this[Keys.nightBrightness] ?: d.nightBrightness,
            lockTask = this[Keys.lockTask] ?: d.lockTask,
        )
    }

    private fun MutablePreferences.write(s: AppSettings) {
        this[Keys.haUrl] = s.haUrl.trim()
        this[Keys.haToken] = s.haToken.trim()
        this[Keys.topLightsCount] = s.topLightsCount
        this[Keys.usageLookbackDays] = s.usageLookbackDays
        this[Keys.calendarEntities] = s.calendarEntities
        this[Keys.weatherEntity] = s.weatherEntity
        this[Keys.musicEntity] = s.musicEntity
        this[Keys.favoriteEntities] = json.encodeToString(stringListSerializer, s.favoriteEntities)
        this[Keys.watchlist] = json.encodeToString(watchlistSerializer, s.watchlist)
        this[Keys.stockRefreshSec] = s.stockRefreshSec
        this[Keys.sportsLeagues] = s.sportsLeagues
        this[Keys.use24hClock] = s.use24hClock
        this[Keys.idleTimeoutSec] = s.idleTimeoutSec
        this[Keys.nightModeEnabled] = s.nightModeEnabled
        this[Keys.nightStartHour] = s.nightStartHour
        this[Keys.nightEndHour] = s.nightEndHour
        this[Keys.nightBrightness] = s.nightBrightness
        this[Keys.lockTask] = s.lockTask
    }
}
