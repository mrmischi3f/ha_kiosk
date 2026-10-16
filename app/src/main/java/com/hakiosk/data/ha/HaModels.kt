package com.hakiosk.data.ha

import com.hakiosk.data.double
import com.hakiosk.data.string
import com.hakiosk.data.stringList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.ZonedDateTime

@Serializable
data class HaState(
    @SerialName("entity_id") val entityId: String,
    val state: String = "unknown",
    val attributes: JsonObject = JsonObject(emptyMap()),
    @SerialName("last_changed") val lastChanged: String? = null,
    @SerialName("last_updated") val lastUpdated: String? = null,
) {
    val domain: String get() = entityId.substringBefore('.')

    val name: String
        get() = attributes.string("friendly_name")
            ?: entityId.substringAfter('.').replace('_', ' ').replaceFirstChar { it.uppercase() }

    val isUnavailable: Boolean get() = state == "unavailable" || state == "unknown"

    /** True when the entity is in an "active" state (light on, door open, playing, …). */
    val isActive: Boolean
        get() = state in ACTIVE_STATES

    val unit: String? get() = attributes.string("unit_of_measurement")

    /** Light brightness as 0-100, or null when off / unsupported. */
    val brightnessPct: Int?
        get() = attributes.double("brightness")?.let { (it / 255.0 * 100).toInt().coerceIn(1, 100) }

    val supportedColorModes: List<String> get() = attributes.stringList("supported_color_modes")

    val supportsBrightness: Boolean
        get() = supportedColorModes.any { it != "onoff" } || attributes.containsKey("brightness")

    val supportsColorTemp: Boolean get() = "color_temp" in supportedColorModes

    val supportsColor: Boolean
        get() = supportedColorModes.any { it in setOf("hs", "rgb", "rgbw", "rgbww", "xy") }

    companion object {
        val ACTIVE_STATES = setOf(
            "on", "open", "opening", "closing", "playing", "unlocked", "heat", "cool",
            "heat_cool", "auto", "dry", "fan_only", "cleaning", "home", "detected",
        )
    }
}

@Serializable
data class HaArea(
    @SerialName("area_id") val areaId: String,
    val name: String,
)

@Serializable
data class HaDevice(
    val id: String,
    @SerialName("area_id") val areaId: String? = null,
)

@Serializable
data class HaEntityEntry(
    @SerialName("entity_id") val entityId: String,
    @SerialName("area_id") val areaId: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("hidden_by") val hiddenBy: String? = null,
    @SerialName("disabled_by") val disabledBy: String? = null,
    @SerialName("entity_category") val entityCategory: String? = null,
)

/** Flattened registry info: area names, entity → area, and entities that should stay hidden. */
data class HaRegistry(
    val areas: Map<String, String> = emptyMap(),
    val entityArea: Map<String, String> = emptyMap(),
    val hidden: Set<String> = emptySet(),
) {
    fun areaName(entityId: String): String? = entityArea[entityId]?.let { areas[it] }
}

data class HaCalendar(val entityId: String, val name: String)

data class CalendarEvent(
    val summary: String,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val allDay: Boolean,
    val location: String?,
    val calendarId: String,
    val calendarName: String,
)

data class ForecastDay(
    val date: LocalDate,
    val condition: String,
    val high: Double?,
    val low: Double?,
    val precipitationProbability: Double?,
)

sealed interface HaConnection {
    data object NotConfigured : HaConnection
    data object Connecting : HaConnection
    data object Connected : HaConnection
    data class Failed(val message: String, val authError: Boolean = false) : HaConnection
}

/** A node from the media_player/browse_media WebSocket command (playlists, albums, tracks…). */
data class BrowseItem(
    val title: String,
    val mediaClass: String,
    val contentId: String,
    val contentType: String,
    val canPlay: Boolean,
    val canExpand: Boolean,
    val thumbnail: String?,
    val children: List<BrowseItem>,
)

/**
 * The media player shown on the Music tab: the configured one, otherwise the Spotify entity
 * created by HA's Spotify integration (media_player.spotify_<user>).
 */
fun resolveMusicPlayer(states: Map<String, HaState>, configured: String): HaState? =
    configured.takeIf { it.isNotBlank() }?.let { states[it] }
        ?: states.values
            .filter { it.domain == "media_player" && it.entityId.contains("spotify") }
            .minByOrNull { it.entityId }

/** A Home Assistant service call, e.g. light.toggle with optional service data. */
data class ServiceCall(
    val domain: String,
    val service: String,
    val data: Map<String, Any?> = emptyMap(),
)
