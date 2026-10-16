package com.hakiosk.ui

import com.hakiosk.data.double
import com.hakiosk.data.ha.HaState
import com.hakiosk.data.string
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

fun currencySymbol(code: String?): String =
    code?.let { runCatching { Currency.getInstance(it).getSymbol(Locale.getDefault()) }.getOrNull() } ?: ""

fun formatPrice(value: Double, currency: String? = null): String {
    val digits = if (abs(value) < 1) 4 else 2
    return currencySymbol(currency) + String.format(Locale.getDefault(), "%,.${digits}f", value)
}

fun formatNumber(value: Double, digits: Int = 2): String =
    String.format(Locale.getDefault(), "%,.${digits}f", value)

fun formatSigned(value: Double, digits: Int = 2): String =
    (if (value >= 0) "+" else "") + String.format(Locale.getDefault(), "%,.${digits}f", value)

fun formatPercent(value: Double): String = formatSigned(value) + "%"

fun formatCompact(value: Long): String = when {
    value >= 1_000_000_000L -> String.format(Locale.getDefault(), "%.2fB", value / 1e9)
    value >= 1_000_000L -> String.format(Locale.getDefault(), "%.2fM", value / 1e6)
    value >= 1_000L -> String.format(Locale.getDefault(), "%.1fK", value / 1e3)
    else -> value.toString()
}

fun humanize(raw: String): String =
    raw.replace('_', ' ').replace('-', ' ').replaceFirstChar { it.titlecase(Locale.getDefault()) }

fun relativeTime(iso: String?): String {
    if (iso == null) return ""
    val instant = runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull() ?: return ""
    val d = Duration.between(instant, Instant.now())
    return when {
        d.toMinutes() < 1 -> "just now"
        d.toMinutes() < 60 -> "${d.toMinutes()} min ago"
        d.toHours() < 24 -> "${d.toHours()} h ago"
        else -> "${d.toDays()} d ago"
    }
}

private fun formatSensorValue(state: String): String {
    val n = state.toDoubleOrNull() ?: return humanize(state)
    return if (n == Math.floor(n) && abs(n) < 1e9) n.toLong().toString() else formatNumber(n, 1)
}

/** Short state text shown on entity tiles. */
fun prettyState(e: HaState): String {
    if (e.state == "unavailable") return "Unavailable"
    val a = e.attributes
    return when (e.domain) {
        "light" -> if (e.isActive) e.brightnessPct?.let { "On · $it%" } ?: "On" else "Off"
        "sensor" -> {
            if (a.string("device_class") == "timestamp") relativeTime(e.state).ifBlank { e.state }
            else listOfNotNull(formatSensorValue(e.state), e.unit).joinToString(" ")
        }
        "climate" -> {
            val current = a.double("current_temperature")?.let { formatNumber(it, 1) + "°" }
            val target = a.double("temperature")?.let { formatNumber(it, 1) + "°" }
            listOfNotNull(humanize(e.state), current, target?.let { "→ $it" }).joinToString(" ")
        }
        "cover" -> {
            val pos = a.double("current_position")?.toInt()
            if (pos != null && e.state == "open") "Open · $pos%" else humanize(e.state)
        }
        "media_player" -> a.string("media_title")?.let { title ->
            listOfNotNull(title, a.string("media_artist")).joinToString(" — ")
        } ?: humanize(e.state)
        "fan" -> if (e.isActive) a.double("percentage")?.let { "On · ${it.toInt()}%" } ?: "On" else "Off"
        "scene", "script", "button", "input_button" -> when (e.domain) {
            "script" -> if (e.state == "on") "Running" else "Tap to run"
            else -> "Tap to activate"
        }
        "binary_sensor" -> binarySensorText(e)
        else -> humanize(e.state)
    }
}

private fun binarySensorText(e: HaState): String {
    val on = e.state == "on"
    return when (e.attributes.string("device_class")) {
        "door", "window", "garage_door", "opening" -> if (on) "Open" else "Closed"
        "motion", "occupancy", "presence" -> if (on) "Detected" else "Clear"
        "moisture" -> if (on) "Wet" else "Dry"
        "lock" -> if (on) "Unlocked" else "Locked"
        "connectivity" -> if (on) "Connected" else "Disconnected"
        "battery" -> if (on) "Low" else "Normal"
        else -> if (on) "On" else "Off"
    }
}
