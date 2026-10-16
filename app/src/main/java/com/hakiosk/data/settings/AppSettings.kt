package com.hakiosk.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class WatchItem(
    val symbol: String,
    val name: String = "",
    val exchange: String = "",
    /** Optional portfolio position, used to show market value and gain/loss. */
    val shares: Double? = null,
    val avgCost: Double? = null,
)

data class AppSettings(
    /** False until the first read from disk finishes. */
    val loaded: Boolean = false,

    // Home Assistant
    val haUrl: String = "",
    val haToken: String = "",
    val topLightsCount: Int = 6,
    val usageLookbackDays: Int = 7,
    /** Calendars shown on the dashboard. Empty means "all calendars". */
    val calendarEntities: Set<String> = emptySet(),
    /** Weather entity for the dashboard. Empty means "first weather.* entity found". */
    val weatherEntity: String = "",
    /** Media player for the Music tab. Empty means "first media_player.spotify_* entity". */
    val musicEntity: String = "",
    /** Entities pinned to the dashboard (long-press a tile → pin). */
    val favoriteEntities: List<String> = emptyList(),

    // Stocks
    val watchlist: List<WatchItem> = emptyList(),
    val stockRefreshSec: Int = 60,

    // Sports
    val sportsLeagues: Set<String> = DEFAULT_LEAGUES,

    // Kiosk
    val use24hClock: Boolean = true,
    val idleTimeoutSec: Int = 120,
    val nightModeEnabled: Boolean = false,
    val nightStartHour: Int = 23,
    val nightEndHour: Int = 7,
    val nightBrightness: Float = 0.02f,
    val lockTask: Boolean = false,
) {
    val haConfigured: Boolean get() = haUrl.isNotBlank() && haToken.isNotBlank()

    companion object {
        val DEFAULT_LEAGUES = setOf(
            "soccer/eng.1",
            "soccer/uefa.champions",
            "basketball/nba",
            "football/nfl",
        )
    }
}
