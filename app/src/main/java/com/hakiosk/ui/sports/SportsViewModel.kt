package com.hakiosk.ui.sports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hakiosk.AppContainer
import com.hakiosk.data.sports.Game
import com.hakiosk.data.sports.GameState
import com.hakiosk.data.sports.League
import com.hakiosk.data.sports.Leagues
import com.hakiosk.data.sports.Scoreboard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

data class LeagueSection(val league: League, val games: List<Game>, val error: String?)

data class SportsUiState(
    val selectedLeagues: List<League> = emptyList(),
    val leagueFilter: String? = null,
    val dayOffset: Int = 0,
    val liveOnly: Boolean = false,
    val sections: List<LeagueSection> = emptyList(),
    val liveCount: Int = 0,
    val loading: Boolean = false,
    val lastUpdated: Instant? = null,
)

class SportsViewModel(private val container: AppContainer) : ViewModel() {
    private val sports = container.sports

    private val leagueFilter = MutableStateFlow<String?>(null)
    private val dayOffset = MutableStateFlow(0)
    private val liveOnly = MutableStateFlow(false)
    private val otherDay = MutableStateFlow<List<Scoreboard>?>(null)
    private val loading = MutableStateFlow(false)
    private val reload = MutableStateFlow(0)

    private val selectedLeagues = container.settings
        .map { s -> Leagues.all.filter { it.id in s.sportsLeagues } }
        .distinctUntilChanged()

    init {
        // Days other than today are fetched on demand (today comes from the auto-refreshing repo).
        viewModelScope.launch {
            combine(dayOffset, selectedLeagues, reload) { d, l, _ -> d to l }.collectLatest { (offset, leagues) ->
                if (offset == 0) {
                    otherDay.value = null
                    return@collectLatest
                }
                loading.value = true
                otherDay.value = null
                try {
                    otherDay.value = sports.fetchAll(leagues, LocalDate.now().plusDays(offset.toLong()))
                } finally {
                    loading.value = false
                }
            }
        }
    }

    private val filters = combine(leagueFilter, dayOffset, liveOnly) { f, d, l -> Triple(f, d, l) }

    val uiState: StateFlow<SportsUiState> = combine(
        selectedLeagues, sports.today, otherDay, filters, combine(loading, sports.lastUpdated) { l, u -> l to u },
    ) { leagues, today, other, (filter, offset, live), (isLoading, updated) ->
        val boards = if (offset == 0) today else other.orEmpty()
        val activeFilter = filter?.takeIf { id -> leagues.any { it.id == id } }
        val sections = boards
            .filter { activeFilter == null || it.league.id == activeFilter }
            .map { b ->
                val games = b.games
                    .filter { !live || it.state == GameState.LIVE }
                    .sortedWith(compareBy<Game> { stateOrder(it.state) }.thenBy { it.start })
                LeagueSection(b.league, games, b.error)
            }
            .filter { it.games.isNotEmpty() || it.error != null }
        SportsUiState(
            selectedLeagues = leagues,
            leagueFilter = activeFilter,
            dayOffset = offset,
            liveOnly = live,
            sections = sections,
            liveCount = boards.sumOf { b -> b.games.count { it.state == GameState.LIVE } },
            loading = isLoading || (offset == 0 && updated == null),
            lastUpdated = updated,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SportsUiState())

    private fun stateOrder(s: GameState) = when (s) {
        GameState.LIVE -> 0
        GameState.PRE -> 1
        GameState.FINAL -> 2
    }

    fun setLeagueFilter(id: String?) {
        leagueFilter.value = id
    }

    fun setDayOffset(value: Int) {
        dayOffset.value = value
    }

    fun toggleLiveOnly() {
        liveOnly.value = !liveOnly.value
    }

    fun refresh() {
        if (dayOffset.value == 0) sports.refreshNow() else reload.value++
    }

    fun toggleLeague(id: String) {
        viewModelScope.launch {
            container.settingsRepository.update { s ->
                s.copy(sportsLeagues = if (id in s.sportsLeagues) s.sportsLeagues - id else s.sportsLeagues + id)
            }
        }
    }
}
