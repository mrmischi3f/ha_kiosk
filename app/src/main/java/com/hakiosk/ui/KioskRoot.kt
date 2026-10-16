@file:Suppress("DEPRECATION")

package com.hakiosk.ui

import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hakiosk.data.ha.HaConnection
import com.hakiosk.ui.dashboard.DashboardScreen
import com.hakiosk.ui.home.HomeScreen
import com.hakiosk.ui.settings.SettingsScreen
import com.hakiosk.ui.sports.SportsScreen
import com.hakiosk.ui.stocks.StocksScreen
import com.hakiosk.ui.theme.KioskColors
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Destination(val label: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Filled.Dashboard),
    Home("Home", Icons.Filled.Home),
    Stocks("Stocks", Icons.Filled.ShowChart),
    Sports("Scores", Icons.Filled.EmojiEvents),
    Settings("Settings", Icons.Filled.Settings),
}

@Composable
fun KioskRoot() {
    val container = LocalAppContainer.current
    val settings by container.settings.collectAsStateWithLifecycle()
    val connection by container.homeAssistant.connection.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(Destination.Dashboard) }
    var lastInteraction by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var nightActive by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val activity = LocalContext.current.findActivity()

    // Back always returns to the dashboard and never leaves the kiosk.
    BackHandler { destination = Destination.Dashboard }

    LaunchedEffect(Unit) {
        container.homeAssistant.errors.collect { snackbar.showSnackbar(it) }
    }

    // Idle handling: jump back to the dashboard, and enter night mode when configured.
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            val s = container.settings.value
            val idleMs = SystemClock.elapsedRealtime() - lastInteraction
            if (s.idleTimeoutSec > 0 &&
                idleMs > s.idleTimeoutSec * 1000L &&
                destination != Destination.Dashboard &&
                destination != Destination.Settings
            ) {
                destination = Destination.Dashboard
            }
            nightActive = s.nightModeEnabled &&
                isNightTime(LocalTime.now(), s.nightStartHour, s.nightEndHour) &&
                idleMs > 60_000
        }
    }

    LaunchedEffect(nightActive, settings.nightBrightness) {
        activity?.window?.let { window ->
            window.attributes = window.attributes.apply {
                screenBrightness = if (nightActive) settings.nightBrightness.coerceIn(0.01f, 1f)
                else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastInteraction = SystemClock.elapsedRealtime()
                        nightActive = false
                    }
                }
            }
    ) {
        Row(Modifier.fillMaxSize()) {
            KioskRail(
                current = destination,
                connection = connection,
                use24h = settings.use24hClock,
                onSelect = { destination = it },
            )
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (destination) {
                    Destination.Dashboard -> DashboardScreen(onNavigate = { destination = it })
                    Destination.Home -> HomeScreen(onOpenSettings = { destination = Destination.Settings })
                    Destination.Stocks -> StocksScreen()
                    Destination.Sports -> SportsScreen()
                    Destination.Settings -> SettingsScreen()
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        if (nightActive) NightClock(settings.use24hClock)
    }
}

fun isNightTime(now: LocalTime, startHour: Int, endHour: Int): Boolean = when {
    startHour == endHour -> false
    startHour < endHour -> now.hour in startHour until endHour
    else -> now.hour >= startHour || now.hour < endHour
}

fun timeFormatter(use24h: Boolean): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm", Locale.getDefault())

@Composable
private fun KioskRail(
    current: Destination,
    connection: HaConnection,
    use24h: Boolean,
    onSelect: (Destination) -> Unit,
) {
    val now by rememberNow()
    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Spacer(Modifier.height(12.dp))
        Text(
            now.format(timeFormatter(use24h)),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        Destination.entries.filter { it != Destination.Settings }.forEach { d ->
            NavigationRailItem(
                selected = d == current,
                onClick = { onSelect(d) },
                icon = { Icon(d.icon, contentDescription = d.label) },
                label = { Text(d.label) },
            )
        }
        Spacer(Modifier.weight(1f))
        val dotColor = when (connection) {
            HaConnection.Connected -> KioskColors.Positive
            HaConnection.Connecting -> KioskColors.Warm
            HaConnection.NotConfigured -> Color.Gray
            is HaConnection.Failed -> KioskColors.Negative
        }
        Box(Modifier.size(10.dp).background(dotColor, CircleShape))
        Spacer(Modifier.height(4.dp))
        NavigationRailItem(
            selected = current == Destination.Settings,
            onClick = { onSelect(Destination.Settings) },
            icon = { Icon(Destination.Settings.icon, contentDescription = "Settings") },
            label = { Text("Settings") },
        )
        Spacer(Modifier.height(12.dp))
    }
}

/** Dim, black screen with a clock, shown at night when nobody is using the screen. */
@Composable
private fun NightClock(use24h: Boolean) {
    val now by rememberNow()
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                now.format(timeFormatter(use24h)),
                color = Color(0xFF5A6270),
                fontSize = 140.sp,
                fontWeight = FontWeight.Thin,
            )
            Text(
                now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                color = Color(0xFF3E444E),
                fontSize = 24.sp,
            )
        }
    }
}
