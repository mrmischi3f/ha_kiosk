package com.hakiosk.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hakiosk.AppContainer
import com.hakiosk.data.attempt
import com.hakiosk.data.ha.CalendarEvent
import com.hakiosk.data.ha.ForecastDay
import com.hakiosk.data.ha.HaConnection
import com.hakiosk.data.ha.HaState
import com.hakiosk.data.ha.ServiceCall
import com.hakiosk.data.settings.WatchItem
import com.hakiosk.data.sports.Game
import com.hakiosk.data.sports.GameState
import com.hakiosk.data.stocks.StockQuote
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DashboardUiState(
    val haConfigured: Boolean = false,
    val connection: HaConnection = HaConnection.NotConfigured,
    val use24h: Boolean = true,
    val topLights: List<HaState> = emptyList(),
    val usageBased: Boolean = false,
    val favorites: List<HaState> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val weather: HaState? = null,
    val forecast: List<ForecastDay> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val calendarIds: List<String> = emptyList(),
    val tickers: List<Pair<WatchItem, StockQuote?>> = emptyList(),
    val games: List<Game> = emptyList(),
) {
    fun entity(id: String): HaState? =
        topLights.firstOrNull { it.entityId == id } ?: favorites.firstOrNull { it.entityId == id }
}

private data class CalendarData(val events: List<CalendarEvent>, val calendarIds: List<String>)

class DashboardViewModel(private val container: AppContainer) : ViewModel() {
    private val ha = container.homeAssistant
    private val settings = container.settings

    private val usage = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val calendar = MutableStateFlow(CalendarData(emptyList(), emptyList()))
    private val forecast = MutableStateFlow<List<ForecastDay>>(emptyList())

    private val connected = ha.connection.map { it == HaConnection.Connected }.distinctUntilChanged()

    init {
        // "Most used" lights from HA history; refreshed hourly.
        viewModelScope.launch {
            combine(connected, settings.map { it.usageLookbackDays }.distinctUntilChanged()) { c, d -> c to d }
                .collectLatest { (isConnected, days) ->
                    if (!isConnected) return@collectLatest
                    while (true) {
                        attempt { ha.fetchLightUsage(days) }?.let { usage.value = it }
                        delay(Duration.ofHours(1).toMillis())
                    }
                }
        }
        // Calendar agenda for the next two weeks; refreshed every 15 minutes.
        viewModelScope.launch {
            combine(connected, settings.map { it.calendarEntities }.distinctUntilChanged()) { c, ids -> c to ids }
                .collectLatest { (isConnected, selected) ->
                    if (!isConnected) return@collectLatest
                    while (true) {
                        attempt {
                            val all = ha.fetchCalendars()
                            val chosen = if (selected.isEmpty()) all else all.filter { it.entityId in selected }
                            val zone = ZoneId.systemDefault()
                            val from = LocalDate.now().atStartOfDay(zone)
                            val events = ha.fetchCalendarEvents(chosen, from, from.plusDays(31))
                            calendar.value = CalendarData(events, chosen.map { it.entityId })
                        }
                        delay(Duration.ofMinutes(15).toMillis())
                    }
                }
        }
        // Daily weather forecast; refreshed every 30 minutes.
        viewModelScope.launch {
            combine(connected, settings.map { it.weatherEntity }.distinctUntilChanged()) { c, w -> c to w }
                .collectLatest { (isConnected, configured) ->
                    if (!isConnected) return@collectLatest
                    while (true) {
                        val entity = configured.ifBlank {
                            ha.states.value.keys.firstOrNull { it.startsWith("weather.") }.orEmpty()
                        }
                        if (entity.isNotBlank()) {
                            attempt { ha.fetchDailyForecast(entity) }?.let { forecast.value = it }
                        }
                        delay(Duration.ofMinutes(30).toMillis())
                    }
                }
        }
    }

    private val haPart = combine(ha.states, ha.registry, usage, settings, ha.connection) { states, registry, usage, s, conn ->
        val lights = states.values.filter { it.domain == "light" && it.entityId !in registry.hidden }
        val usageBased = usage.values.any { it > 0 }
        val ranked = if (usageBased) {
            lights.sortedWith(compareByDescending<HaState> { usage[it.entityId] ?: 0 }.thenBy { it.name })
        } else {
            lights.sortedWith(compareByDescending<HaState> { it.isActive }.thenBy { it.name })
        }
        val weather = s.weatherEntity.takeIf { it.isNotBlank() }?.let { states[it] }
            ?: states.values.firstOrNull { it.domain == "weather" }
        DashboardUiState(
            haConfigured = s.haConfigured,
            connection = conn,
            use24h = s.use24hClock,
            topLights = ranked.take(s.topLightsCount),
            usageBased = usageBased,
            favorites = s.favoriteEntities.mapNotNull { states[it] },
            favoriteIds = s.favoriteEntities.toSet(),
            weather = weather,
        ) to s.watchlist
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        haPart,
        combine(calendar, forecast) { cal, fc -> cal to fc },
        container.stocks.quotes,
        container.sports.today,
    ) { (base, watchlist), (cal, fc), quotes, boards ->
        base.copy(
            forecast = fc,
            events = cal.events,
            calendarIds = cal.calendarIds,
            tickers = watchlist.map { it to quotes[it.symbol] },
            games = pickGames(boards.flatMap { it.games }),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    /** Live games first, then upcoming (next 24h), then the most recent finals. */
    private fun pickGames(games: List<Game>): List<Game> {
        val now = Instant.now()
        val live = games.filter { it.state == GameState.LIVE }
        val upcoming = games
            .filter { it.state == GameState.PRE && it.start.isBefore(now.plus(Duration.ofHours(24))) }
            .sortedBy { it.start }
        val finals = games.filter { it.state == GameState.FINAL }.sortedByDescending { it.start }
        return (live + upcoming + finals).take(8)
    }

    fun call(entityId: String, call: ServiceCall) = ha.call(entityId, call)

    fun toggleFavorite(entityId: String) {
        viewModelScope.launch { container.settingsRepository.toggleFavorite(entityId) }
    }
}
