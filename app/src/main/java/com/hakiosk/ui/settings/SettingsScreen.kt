@file:OptIn(ExperimentalLayoutApi::class)

package com.hakiosk.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hakiosk.BuildConfigInfo
import com.hakiosk.data.ha.HaConnection
import com.hakiosk.data.sports.Leagues
import com.hakiosk.ui.components.Panel
import com.hakiosk.ui.kioskViewModel
import com.hakiosk.ui.theme.KioskColors
import kotlin.math.roundToInt

@Composable
fun SettingsScreen() {
    val vm = kioskViewModel { SettingsViewModel(it) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val s = state.settings
    val context = LocalContext.current

    Box(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

            HomeAssistantSection(state, onSave = vm::saveConnection, onReconnect = vm::reconnect)

            Panel(title = "Dashboard", modifier = Modifier.fillMaxWidth()) {
                Stepper("Lights on the dashboard", s.topLightsCount, 2..16, 2) { v -> vm.update { it.copy(topLightsCount = v) } }
                Stepper(
                    "\"Most used\" looks back",
                    s.usageLookbackDays, 1..30, 1,
                    format = { "$it days" },
                ) { v -> vm.update { it.copy(usageLookbackDays = v) } }

                SectionLabel("Weather")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = s.weatherEntity.isBlank(),
                        onClick = { vm.update { it.copy(weatherEntity = "") } },
                        label = { Text("Automatic") },
                    )
                    state.weatherEntities.forEach { (id, name) ->
                        FilterChip(
                            selected = s.weatherEntity == id,
                            onClick = { vm.update { it.copy(weatherEntity = id) } },
                            label = { Text(name) },
                        )
                    }
                }

                SectionLabel("Calendars (none selected = show all)")
                if (state.calendars.isEmpty()) {
                    Hint("No calendars found. Add a calendar integration (Google, CalDAV, Local…) in Home Assistant.")
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.calendars.forEach { cal ->
                            FilterChip(
                                selected = cal.entityId in s.calendarEntities,
                                onClick = {
                                    vm.update {
                                        it.copy(
                                            calendarEntities = if (cal.entityId in it.calendarEntities) it.calendarEntities - cal.entityId
                                            else it.calendarEntities + cal.entityId
                                        )
                                    }
                                },
                                label = { Text(cal.name) },
                            )
                        }
                    }
                }
                if (s.favoriteEntities.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Hint("${s.favoriteEntities.size} pinned entities (long-press any tile to pin/unpin)", Modifier.weight(1f))
                        OutlinedButton(onClick = { vm.update { it.copy(favoriteEntities = emptyList()) } }) { Text("Clear pins") }
                    }
                }
            }

            Panel(title = "Stocks", modifier = Modifier.fillMaxWidth()) {
                Stepper(
                    "Refresh every",
                    s.stockRefreshSec, 15..900, 15,
                    format = { "$it s" },
                ) { v -> vm.update { it.copy(stockRefreshSec = v) } }
                Hint("Quotes come from Yahoo Finance and may be delayed up to 15 minutes depending on the exchange.")
            }

            Panel(title = "Sports leagues", modifier = Modifier.fillMaxWidth()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Leagues.all.forEach { league ->
                        FilterChip(
                            selected = league.id in s.sportsLeagues,
                            onClick = {
                                vm.update {
                                    it.copy(
                                        sportsLeagues = if (league.id in it.sportsLeagues) it.sportsLeagues - league.id
                                        else it.sportsLeagues + league.id
                                    )
                                }
                            },
                            label = { Text(league.name) },
                        )
                    }
                }
            }

            Panel(title = "Display & kiosk", modifier = Modifier.fillMaxWidth()) {
                SwitchRow("24-hour clock", s.use24hClock) { v -> vm.update { it.copy(use24hClock = v) } }
                Stepper(
                    "Return to dashboard when idle",
                    s.idleTimeoutSec, 0..900, 30,
                    format = { if (it == 0) "Never" else if (it < 60) "$it s" else "${it / 60} min ${if (it % 60 != 0) "${it % 60} s" else ""}".trim() },
                ) { v -> vm.update { it.copy(idleTimeoutSec = v) } }

                SwitchRow(
                    "Night mode",
                    s.nightModeEnabled,
                    subtitle = "Dims the screen and shows a minimal clock when idle at night. Tap to wake.",
                ) { v -> vm.update { it.copy(nightModeEnabled = v) } }
                if (s.nightModeEnabled) {
                    Stepper("Night starts", s.nightStartHour, 0..23, 1, format = { "%02d:00".format(it) }) { v ->
                        vm.update { it.copy(nightStartHour = v) }
                    }
                    Stepper("Night ends", s.nightEndHour, 0..23, 1, format = { "%02d:00".format(it) }) { v ->
                        vm.update { it.copy(nightEndHour = v) }
                    }
                    var brightness by remember(s.nightBrightness) { mutableStateOf(s.nightBrightness) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Night brightness", modifier = Modifier.weight(1f))
                        Text("${(brightness * 100).roundToInt()}%", fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = brightness,
                        onValueChange = { brightness = it },
                        onValueChangeFinished = { vm.update { it.copy(nightBrightness = brightness) } },
                        valueRange = 0.01f..0.5f,
                    )
                }

                SwitchRow(
                    "Pin the app (screen pinning)",
                    s.lockTask,
                    subtitle = "Blocks leaving the app with Home/Recents. Android asks for confirmation the first time.",
                ) { v -> vm.update { it.copy(lockTask = v) } }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { openSystemScreen(context, Settings.ACTION_HOME_SETTINGS) }) { Text("Set as Home app") }
                    OutlinedButton(onClick = { openSystemScreen(context, Settings.ACTION_SETTINGS) }) { Text("Android settings") }
                }
                Hint("Setting this app as the Home app makes it start on boot and keeps the frame's stock launcher out of the way.")
            }

            Text(
                "HA Kiosk ${BuildConfigInfo.versionName(context)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HomeAssistantSection(
    state: SettingsUiState,
    onSave: (String, String) -> Unit,
    onReconnect: () -> Unit,
) {
    val s = state.settings
    var url by remember { mutableStateOf(s.haUrl) }
    var token by remember { mutableStateOf(s.haToken) }
    var showToken by remember { mutableStateOf(false) }

    // Fill the fields once the stored values have been read from disk.
    LaunchedEffect(s.loaded) {
        if (s.loaded) {
            if (url.isBlank()) url = s.haUrl
            if (token.isBlank()) token = s.haToken
        }
    }

    Panel(title = "Home Assistant", modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL") },
            placeholder = { Text("http://homeassistant.local:8123") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Long-lived access token") },
            singleLine = true,
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showToken = !showToken }) {
                    Icon(
                        if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showToken) "Hide token" else "Show token",
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Hint("Create a token in Home Assistant: your profile → Security → Long-lived access tokens → Create token.")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onSave(url, token) },
                enabled = url.isNotBlank() && token.isNotBlank(),
            ) { Text(if (url.trim() == s.haUrl && token.trim() == s.haToken) "Saved" else "Save & connect") }
            if (state.connection is HaConnection.Failed) {
                OutlinedButton(onClick = onReconnect) { Text("Retry") }
            }
            val (text, color) = when (val c = state.connection) {
                HaConnection.Connected -> "Connected" to KioskColors.Positive
                HaConnection.Connecting -> "Connecting…" to KioskColors.Warm
                HaConnection.NotConfigured -> "Not configured" to MaterialTheme.colorScheme.onSurfaceVariant
                is HaConnection.Failed -> c.message to KioskColors.Negative
            }
            Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(12.dp))
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Hint(it) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Stepper(
    title: String,
    value: Int,
    range: IntRange,
    step: Int,
    format: (Int) -> String = { it.toString() },
    onChange: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(
            onClick = { onChange((value - step).coerceIn(range.first, range.last)) },
            enabled = value > range.first,
        ) { Icon(Icons.Filled.Remove, contentDescription = "Decrease") }
        Text(
            format(value),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.widthIn(min = 96.dp).padding(horizontal = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        FilledTonalIconButton(
            onClick = { onChange((value + step).coerceIn(range.first, range.last)) },
            enabled = value < range.last,
        ) { Icon(Icons.Filled.Add, contentDescription = "Increase") }
    }
}

private fun openSystemScreen(context: Context, action: String) {
    val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
}
