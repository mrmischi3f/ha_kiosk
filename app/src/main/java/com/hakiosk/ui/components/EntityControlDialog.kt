@file:OptIn(ExperimentalLayoutApi::class)
@file:Suppress("DEPRECATION")

package com.hakiosk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hakiosk.data.double
import com.hakiosk.data.ha.HaState
import com.hakiosk.data.ha.ServiceCall
import com.hakiosk.data.string
import com.hakiosk.data.stringList
import com.hakiosk.ui.formatNumber
import com.hakiosk.ui.humanize
import com.hakiosk.ui.prettyState
import com.hakiosk.ui.relativeTime
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt

/** Full controls for a single entity, opened by long-pressing (or tapping) a tile. */
@Composable
fun EntityControlDialog(
    entity: HaState,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onCall: (ServiceCall) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(entityIcon(entity), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entity.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        prettyState(entity),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (isFavorite) "Unpin from dashboard" else "Pin to dashboard",
                        tint = if (isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (entity.domain) {
                    "light" -> LightControls(entity, onCall)
                    "switch", "input_boolean", "automation" -> PowerRow(entity, onCall)
                    "fan" -> FanControls(entity, onCall)
                    "cover" -> CoverControls(entity, onCall)
                    "climate" -> ClimateControls(entity, onCall)
                    "media_player" -> MediaControls(entity, onCall)
                    "lock" -> ActionButtons(
                        listOf("Lock" to ServiceCall("lock", "lock"), "Unlock" to ServiceCall("lock", "unlock")),
                        onCall,
                    )
                    "vacuum" -> ActionButtons(
                        listOf(
                            "Start" to ServiceCall("vacuum", "start"),
                            "Pause" to ServiceCall("vacuum", "pause"),
                            "Dock" to ServiceCall("vacuum", "return_to_base"),
                        ),
                        onCall,
                    )
                    "scene" -> ActionButtons(listOf("Activate" to ServiceCall("scene", "turn_on")), onCall)
                    "script" -> ActionButtons(listOf("Run" to ServiceCall("script", "turn_on")), onCall)
                    "button", "input_button" -> ActionButtons(listOf("Press" to ServiceCall(entity.domain, "press")), onCall)
                    else -> SensorDetails(entity)
                }
                Text(
                    "Last changed ${relativeTime(entity.lastChanged)} · ${entity.entityId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun PowerRow(e: HaState, onCall: (ServiceCall) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Power", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = e.isActive,
            onCheckedChange = { on -> onCall(ServiceCall(e.domain, if (on) "turn_on" else "turn_off")) },
        )
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onCommit: (Float) -> Unit,
    steps: Int = 0,
) {
    var current by remember(value) { mutableFloatStateOf(value.coerceIn(range.start, range.endInclusive)) }
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(format(current), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { onCommit(current) },
            valueRange = range,
            steps = steps,
        )
    }
}

private val COLOR_PRESETS = listOf(
    listOf(255, 180, 107), // warm white
    listOf(255, 255, 255),
    listOf(255, 64, 64),
    listOf(255, 140, 0),
    listOf(255, 220, 0),
    listOf(60, 220, 90),
    listOf(0, 190, 255),
    listOf(60, 90, 255),
    listOf(170, 70, 255),
    listOf(255, 80, 180),
)

@Composable
private fun LightControls(e: HaState, onCall: (ServiceCall) -> Unit) {
    PowerRow(e, onCall)
    if (e.supportsBrightness) {
        LabeledSlider(
            label = "Brightness",
            value = (e.brightnessPct ?: 0).toFloat(),
            range = 1f..100f,
            format = { "${it.roundToInt()}%" },
            onCommit = { onCall(ServiceCall("light", "turn_on", mapOf("brightness_pct" to it.roundToInt()))) },
        )
    }
    if (e.supportsColorTemp) {
        val min = e.attributes.double("min_color_temp_kelvin") ?: 2000.0
        val max = e.attributes.double("max_color_temp_kelvin") ?: 6500.0
        val current = e.attributes.double("color_temp_kelvin") ?: ((min + max) / 2)
        LabeledSlider(
            label = "Color temperature",
            value = current.toFloat(),
            range = min.toFloat()..max.toFloat(),
            format = { "${it.roundToInt()} K" },
            onCommit = { onCall(ServiceCall("light", "turn_on", mapOf("color_temp_kelvin" to it.roundToInt()))) },
        )
    }
    if (e.supportsColor) {
        Text("Color", style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            COLOR_PRESETS.forEach { rgb ->
                Box(
                    Modifier
                        .size(36.dp)
                        .background(Color(rgb[0], rgb[1], rgb[2]), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { onCall(ServiceCall("light", "turn_on", mapOf("rgb_color" to rgb))) },
                )
            }
        }
    }
}

@Composable
private fun FanControls(e: HaState, onCall: (ServiceCall) -> Unit) {
    PowerRow(e, onCall)
    val pct = e.attributes.double("percentage")
    if (pct != null || e.attributes.containsKey("percentage_step")) {
        LabeledSlider(
            label = "Speed",
            value = (pct ?: 0.0).toFloat(),
            range = 0f..100f,
            format = { "${it.roundToInt()}%" },
            onCommit = { onCall(ServiceCall("fan", "set_percentage", mapOf("percentage" to it.roundToInt()))) },
        )
    }
}

@Composable
private fun CoverControls(e: HaState, onCall: (ServiceCall) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { onCall(ServiceCall("cover", "open_cover")) }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Open")
        }
        FilledTonalIconButton(onClick = { onCall(ServiceCall("cover", "stop_cover")) }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.Stop, contentDescription = "Stop")
        }
        FilledTonalIconButton(onClick = { onCall(ServiceCall("cover", "close_cover")) }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close")
        }
    }
    e.attributes.double("current_position")?.let { pos ->
        LabeledSlider(
            label = "Position",
            value = pos.toFloat(),
            range = 0f..100f,
            format = { "${it.roundToInt()}%" },
            onCommit = { onCall(ServiceCall("cover", "set_cover_position", mapOf("position" to it.roundToInt()))) },
        )
    }
}

@Composable
private fun ClimateControls(e: HaState, onCall: (ServiceCall) -> Unit) {
    val a = e.attributes
    val step = a.double("target_temp_step") ?: 0.5
    val min = a.double("min_temp") ?: 7.0
    val max = a.double("max_temp") ?: 35.0
    val target = a.double("temperature")
    a.double("current_temperature")?.let {
        Text("Current: ${formatNumber(it, 1)}°", style = MaterialTheme.typography.bodyLarge)
    }
    if (target != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = {
                    onCall(ServiceCall("climate", "set_temperature", mapOf("temperature" to (target - step).coerceIn(min, max))))
                },
                modifier = Modifier.size(56.dp),
            ) { Icon(Icons.Filled.Remove, contentDescription = "Lower") }
            Text(
                "${formatNumber(target, 1)}°",
                fontSize = 44.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            FilledTonalIconButton(
                onClick = {
                    onCall(ServiceCall("climate", "set_temperature", mapOf("temperature" to (target + step).coerceIn(min, max))))
                },
                modifier = Modifier.size(56.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = "Raise") }
        }
    }
    val modes = a.stringList("hvac_modes")
    if (modes.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.forEach { mode ->
                FilterChip(
                    selected = e.state == mode,
                    onClick = { onCall(ServiceCall("climate", "set_hvac_mode", mapOf("hvac_mode" to mode))) },
                    label = { Text(humanize(mode)) },
                )
            }
        }
    }
}

@Composable
private fun MediaControls(e: HaState, onCall: (ServiceCall) -> Unit) {
    val a = e.attributes
    PowerRow(e.copy(state = if (e.state == "off" || e.state == "standby") "off" else "on"), onCall)
    a.string("media_title")?.let { title ->
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            a.string("media_artist")?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    ) {
        FilledTonalIconButton(onClick = { onCall(ServiceCall("media_player", "media_previous_track")) }, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
        }
        FilledTonalIconButton(onClick = { onCall(ServiceCall("media_player", "media_play_pause")) }, modifier = Modifier.size(64.dp)) {
            Icon(if (e.state == "playing") Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play/pause")
        }
        FilledTonalIconButton(onClick = { onCall(ServiceCall("media_player", "media_next_track")) }, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Next")
        }
    }
    a.double("volume_level")?.let { vol ->
        LabeledSlider(
            label = "Volume",
            value = (vol * 100).toFloat(),
            range = 0f..100f,
            format = { "${it.roundToInt()}%" },
            onCommit = { onCall(ServiceCall("media_player", "volume_set", mapOf("volume_level" to it / 100.0))) },
        )
    }
}

@Composable
private fun ActionButtons(actions: List<Pair<String, ServiceCall>>, onCall: (ServiceCall) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        actions.forEach { (label, call) ->
            FilledTonalButton(onClick = { onCall(call) }) { Text(label) }
        }
    }
}

private val HIDDEN_ATTRIBUTES = setOf(
    "friendly_name", "icon", "unit_of_measurement", "device_class", "state_class",
    "entity_picture", "supported_features", "attribution",
)

@Composable
private fun SensorDetails(e: HaState) {
    Text(
        prettyState(e),
        fontSize = 40.sp,
        fontWeight = FontWeight.Light,
    )
    val attrs = e.attributes.entries
        .filter { it.key !in HIDDEN_ATTRIBUTES && it.value is JsonPrimitive }
        .take(10)
    if (attrs.isNotEmpty()) {
        HorizontalDivider()
        attrs.forEach { (key, value) ->
            Row(Modifier.fillMaxWidth()) {
                Text(humanize(key), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(
                    (value as JsonPrimitive).content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}
