package com.hakiosk.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dehaze
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.hakiosk.data.ha.HaState
import com.hakiosk.data.ha.ServiceCall
import com.hakiosk.data.string
import com.hakiosk.ui.theme.KioskColors

fun entityIcon(e: HaState): ImageVector = when (e.domain) {
    "light" -> if (e.isActive) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb
    "switch" -> Icons.Filled.PowerSettingsNew
    "input_boolean" -> if (e.isActive) Icons.Filled.ToggleOn else Icons.Filled.ToggleOff
    "fan" -> Icons.Filled.Toys
    "cover" -> Icons.Filled.ViewDay
    "climate" -> Icons.Filled.Thermostat
    "media_player" -> if (e.attributes.string("device_class") == "tv") Icons.Filled.Tv else Icons.Filled.MusicNote
    "lock" -> if (e.state == "locked") Icons.Filled.Lock else Icons.Filled.LockOpen
    "scene" -> Icons.Filled.Palette
    "script" -> Icons.Filled.PlayCircleFilled
    "automation" -> Icons.Filled.Schedule
    "button", "input_button" -> Icons.Filled.TouchApp
    "vacuum" -> Icons.Filled.CleaningServices
    "person" -> Icons.Filled.Person
    "weather" -> weatherIcon(e.state)
    "binary_sensor" -> if (e.isActive || e.state == "on") Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked
    "sensor" -> when (e.attributes.string("device_class")) {
        "temperature" -> Icons.Filled.Thermostat
        "humidity", "moisture" -> Icons.Filled.Opacity
        "power", "energy", "voltage", "current" -> Icons.Filled.Bolt
        "battery" -> Icons.Filled.BatteryFull
        "illuminance" -> Icons.Filled.WbSunny
        else -> Icons.Filled.Speed
    }
    else -> Icons.Filled.Widgets
}

fun weatherIcon(condition: String): ImageVector = when (condition) {
    "sunny" -> Icons.Filled.WbSunny
    "clear-night" -> Icons.Filled.NightsStay
    "partlycloudy" -> Icons.Filled.WbCloudy
    "cloudy" -> Icons.Filled.Cloud
    "fog" -> Icons.Filled.Dehaze
    "rainy", "pouring" -> Icons.Filled.Umbrella
    "lightning", "lightning-rainy" -> Icons.Filled.FlashOn
    "snowy", "snowy-rainy" -> Icons.Filled.AcUnit
    "hail" -> Icons.Filled.Grain
    "windy", "windy-variant" -> Icons.Filled.Waves
    "exceptional" -> Icons.Filled.Warning
    else -> Icons.Filled.Cloud
}

fun weatherLabel(condition: String): String = when (condition) {
    "clear-night" -> "Clear"
    "partlycloudy" -> "Partly cloudy"
    "lightning-rainy" -> "Thunderstorms"
    "snowy-rainy" -> "Sleet"
    "windy-variant" -> "Windy"
    else -> condition.replace('-', ' ').replaceFirstChar { it.uppercase() }
}

/** Accent color used when an entity is "active". */
fun entityAccent(e: HaState, primary: Color): Color = when (e.domain) {
    "light" -> KioskColors.Warm
    "lock" -> if (e.state == "unlocked") KioskColors.Negative else primary
    "climate" -> when (e.state) {
        "heat" -> Color(0xFFFF8A65)
        "cool" -> Color(0xFF64B5F6)
        else -> primary
    }
    else -> primary
}

/** What a single tap on a tile does. Null means "open the control dialog". */
fun tapAction(e: HaState): ServiceCall? = when (e.domain) {
    "light", "switch", "input_boolean", "fan", "automation" -> ServiceCall(e.domain, "toggle")
    "scene", "script" -> ServiceCall(e.domain, "turn_on")
    "button", "input_button" -> ServiceCall(e.domain, "press")
    "media_player" -> if (e.state == "playing" || e.state == "paused") ServiceCall(e.domain, "media_play_pause") else null
    else -> null
}
