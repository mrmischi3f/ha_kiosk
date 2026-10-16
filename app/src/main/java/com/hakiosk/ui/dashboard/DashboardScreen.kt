@file:OptIn(ExperimentalFoundationApi::class)

package com.hakiosk.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.hakiosk.data.double
import com.hakiosk.data.ha.CalendarEvent
import com.hakiosk.data.ha.ForecastDay
import com.hakiosk.data.ha.HaConnection
import com.hakiosk.data.ha.HaState
import com.hakiosk.data.settings.WatchItem
import com.hakiosk.data.sports.Game
import com.hakiosk.data.sports.GameState
import com.hakiosk.data.stocks.StockQuote
import com.hakiosk.data.string
import com.hakiosk.ui.Destination
import com.hakiosk.ui.components.EntityControlDialog
import com.hakiosk.ui.components.EntityTile
import com.hakiosk.ui.components.Panel
import com.hakiosk.ui.components.tapAction
import com.hakiosk.ui.components.weatherIcon
import com.hakiosk.ui.components.weatherLabel
import com.hakiosk.ui.formatNumber
import com.hakiosk.ui.formatPercent
import com.hakiosk.ui.formatPrice
import com.hakiosk.ui.kioskViewModel
import com.hakiosk.ui.rememberNow
import com.hakiosk.ui.theme.KioskColors
import com.hakiosk.ui.timeFormatter
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun DashboardScreen(onNavigate: (Destination) -> Unit) {
    val vm = kioskViewModel { DashboardViewModel(it) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    var dialogEntityId by remember { mutableStateOf<String?>(null) }

    val onTileTap: (HaState) -> Unit = { e ->
        val action = tapAction(e)
        if (action != null) vm.call(e.entityId, action) else dialogEntityId = e.entityId
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Left: clock, weather, scores
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ClockPanel(state.use24h, Modifier.fillMaxWidth())
                WeatherPanel(state.weather, state.forecast, Modifier.fillMaxWidth())
                ScoresPanel(
                    games = state.games,
                    onClick = { onNavigate(Destination.Sports) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
            // Middle: Home Assistant quick controls
            Column(Modifier.weight(1.25f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!state.haConfigured) {
                    ConnectPanel(onOpenSettings = { onNavigate(Destination.Settings) }, modifier = Modifier.fillMaxSize())
                } else {
                    if (state.favorites.isNotEmpty()) {
                        Panel(title = "Pinned", icon = Icons.Filled.Star, modifier = Modifier.fillMaxWidth()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(state.favorites, key = { it.entityId }) { e ->
                                    EntityTile(
                                        entity = e,
                                        onTap = { onTileTap(e) },
                                        onLongPress = { dialogEntityId = e.entityId },
                                        modifier = Modifier.width(150.dp),
                                        height = 96.dp,
                                    )
                                }
                            }
                        }
                    }
                    LightsPanel(
                        state = state,
                        onTap = onTileTap,
                        onLongPress = { dialogEntityId = it.entityId },
                        onShowAll = { onNavigate(Destination.Home) },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
            // Right: calendar
            CalendarPanel(
                events = state.events,
                calendarIds = state.calendarIds,
                use24h = state.use24h,
                haConfigured = state.haConfigured,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        TickerStrip(state.tickers, onClick = { onNavigate(Destination.Stocks) })
    }

    dialogEntityId?.let { id ->
        val entity = state.entity(id)
        if (entity != null) {
            EntityControlDialog(
                entity = entity,
                isFavorite = id in state.favoriteIds,
                onToggleFavorite = { vm.toggleFavorite(id) },
                onCall = { vm.call(id, it) },
                onDismiss = { dialogEntityId = null },
            )
        }
    }
}

@Composable
private fun ClockPanel(use24h: Boolean, modifier: Modifier = Modifier) {
    val now by rememberNow()
    Panel(modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                now.format(timeFormatter(use24h)),
                fontSize = 76.sp,
                fontWeight = FontWeight.Light,
                lineHeight = 76.sp,
            )
            if (!use24h) {
                Text(
                    now.format(DateTimeFormatter.ofPattern("a", Locale.getDefault())),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 6.dp, bottom = 12.dp),
                )
            }
        }
        Text(
            now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
                .replaceFirstChar { it.titlecase(Locale.getDefault()) },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeatherPanel(weather: HaState?, forecast: List<ForecastDay>, modifier: Modifier = Modifier) {
    Panel(modifier = modifier) {
        if (weather == null) {
            Text(
                "No weather entity found in Home Assistant",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Panel
        }
        val a = weather.attributes
        val unit = a.string("temperature_unit") ?: "°"
        val unitShort = if (unit.startsWith("°")) "°" else unit
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                weatherIcon(weather.state),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                a.double("temperature")?.let { formatNumber(it, 0) + unitShort } ?: "--",
                fontSize = 40.sp,
                fontWeight = FontWeight.Light,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(weatherLabel(weather.state), style = MaterialTheme.typography.titleSmall)
                val today = forecast.firstOrNull()
                val details = listOfNotNull(
                    today?.let { f ->
                        listOfNotNull(
                            f.high?.let { "H ${formatNumber(it, 0)}°" },
                            f.low?.let { "L ${formatNumber(it, 0)}°" },
                        ).joinToString(" ").takeIf { it.isNotBlank() }
                    },
                    a.double("humidity")?.let { "${it.toInt()}% hum." },
                )
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val upcoming = forecast.filter { !it.date.isBefore(LocalDate.now()) }.drop(1).take(5)
        if (upcoming.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                upcoming.forEach { day ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            weatherIcon(day.condition),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp).padding(vertical = 2.dp),
                        )
                        Text(
                            listOfNotNull(day.high, day.low).joinToString("/") { formatNumber(it, 0) + "°" },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectPanel(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Panel(modifier = modifier, title = "Home Assistant", icon = Icons.Filled.Lightbulb) {
        Text(
            "Connect your Home Assistant instance to see your lights, devices and calendars here.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenSettings) { Text("Open settings") }
    }
}

@Composable
private fun LightsPanel(
    state: DashboardUiState,
    onTap: (HaState) -> Unit,
    onLongPress: (HaState) -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(
        modifier = modifier,
        title = if (state.usageBased) "Most used lights" else "Lights",
        icon = Icons.Filled.Lightbulb,
        action = {
            Text(
                "All devices",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onShowAll).padding(4.dp),
            )
        },
    ) {
        when {
            state.connection is HaConnection.Failed && state.topLights.isEmpty() -> Text(
                (state.connection as HaConnection.Failed).message,
                color = MaterialTheme.colorScheme.error,
            )
            state.connection != HaConnection.Connected && state.topLights.isEmpty() -> Text(
                "Connecting to Home Assistant…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.topLights.isEmpty() -> Text(
                "No lights found",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.topLights, key = { it.entityId }) { e ->
                    EntityTile(
                        entity = e,
                        onTap = { onTap(e) },
                        onLongPress = { onLongPress(e) },
                        isFavorite = e.entityId in state.favoriteIds,
                        height = 100.dp,
                    )
                }
            }
        }
    }
}

private val CALENDAR_COLORS = listOf(
    Color(0xFF8AB4F8), Color(0xFFF6C26B), Color(0xFF7FD8C6), Color(0xFFF28B82),
    Color(0xFFC58AF9), Color(0xFFFDD663), Color(0xFF81C995),
)

@Composable
private fun CalendarPanel(
    events: List<CalendarEvent>,
    calendarIds: List<String>,
    use24h: Boolean,
    haConfigured: Boolean,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val eventDays = remember(events) {
        buildSet {
            events.forEach { e ->
                var d = e.start.toLocalDate()
                // All-day events end at midnight of the following day (exclusive).
                val last = if (e.allDay) e.end.toLocalDate().minusDays(1) else e.end.toLocalDate()
                var guard = 0
                while (!d.isAfter(last) && guard++ < 62) {
                    add(d)
                    d = d.plusDays(1)
                }
                add(e.start.toLocalDate())
            }
        }
    }
    val title = today.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }

    Panel(modifier = modifier, title = title, icon = Icons.Filled.CalendarMonth) {
        MiniMonth(today, eventDays)
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))

        val upcoming = events.filter { !it.end.toLocalDate().isBefore(today) && it.start.toLocalDate().isBefore(today.plusDays(14)) }
        if (upcoming.isEmpty()) {
            Text(
                if (haConfigured) "Nothing scheduled in the next two weeks" else "Connect Home Assistant to see calendars",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Panel
        }
        val grouped = upcoming.groupBy { e -> e.start.toLocalDate().let { if (it.isBefore(today)) today else it } }.toSortedMap()
        val timeFmt = timeFormatter(use24h)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            grouped.forEach { (date, dayEvents) ->
                item(key = "h-$date") {
                    Text(
                        dayLabel(date, today),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(dayEvents) { e ->
                    val color = CALENDAR_COLORS[calendarIds.indexOf(e.calendarId).coerceAtLeast(0) % CALENDAR_COLORS.size]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(4.dp).height(34.dp).background(color, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                e.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (e.allDay) "All day" else "${e.start.format(timeFmt)} – ${e.end.format(timeFmt)}" +
                                    (e.location?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
}

@Composable
private fun MiniMonth(today: LocalDate, eventDays: Set<LocalDate>) {
    val locale = Locale.getDefault()
    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
    val first = today.withDayOfMonth(1)
    val offset = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val cells = offset + today.lengthOfMonth()
    val rows = (cells + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                Text(
                    firstDayOfWeek.plus(i.toLong()).getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val dayNumber = r * 7 + c - offset + 1
                    Box(Modifier.weight(1f).height(26.dp), contentAlignment = Alignment.Center) {
                        if (dayNumber in 1..today.lengthOfMonth()) {
                            val date = first.withDayOfMonth(dayNumber)
                            val isToday = date == today
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .aspectRatio(1f)
                                    .background(
                                        if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    dayNumber.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = when {
                                        isToday -> MaterialTheme.colorScheme.onPrimary
                                        date.isBefore(today) -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                            if (date in eventDays && !isToday) {
                                Box(
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .size(4.dp)
                                        .background(MaterialTheme.colorScheme.secondary, CircleShape),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoresPanel(games: List<Game>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Panel(
        modifier = modifier.clickable(onClick = onClick),
        title = if (games.any { it.state == GameState.LIVE }) "Live scores" else "Scores",
        icon = Icons.Filled.EmojiEvents,
    ) {
        if (games.isEmpty()) {
            Text(
                "No games today in your leagues",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Panel
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = true) {
            items(games, key = { "${it.league.id}-${it.id}" }) { g -> CompactGameRow(g) }
        }
    }
}

@Composable
private fun CompactGameRow(g: Game) {
    val zone = ZoneId.systemDefault()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            g.league.shortName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
            maxLines = 1,
        )
        AsyncImage(model = g.away.logo, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            " ${g.away.abbreviation}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        Text(
            if (g.state == GameState.PRE) {
                g.start.atZone(zone).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            } else {
                "${g.away.score ?: "-"} – ${g.home.score ?: "-"}"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (g.state == GameState.LIVE) KioskColors.Live else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "${g.home.abbreviation} ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        AsyncImage(model = g.home.logo, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun TickerStrip(tickers: List<Pair<WatchItem, StockQuote?>>, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 16.dp)) {
            if (tickers.isEmpty()) {
                Text(
                    "Add stocks in the Stocks tab to see your portfolio ticker here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tickers.forEach { (item, quote) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.symbol, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.width(8.dp))
                            if (quote == null) {
                                Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                val up = quote.change >= 0
                                Text(formatPrice(quote.price, quote.currency), style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    (if (up) "▲ " else "▼ ") + formatPercent(quote.changePercent),
                                    color = if (up) KioskColors.Positive else KioskColors.Negative,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
