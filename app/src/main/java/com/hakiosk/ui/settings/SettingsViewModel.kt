package com.hakiosk.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hakiosk.AppContainer
import com.hakiosk.data.attempt
import com.hakiosk.data.ha.HaCalendar
import com.hakiosk.data.ha.HaConnection
import com.hakiosk.data.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val connection: HaConnection = HaConnection.NotConfigured,
    val calendars: List<HaCalendar> = emptyList(),
    /** entity_id to friendly name */
    val weatherEntities: List<Pair<String, String>> = emptyList(),
    val mediaPlayers: List<Pair<String, String>> = emptyList(),
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val ha = container.homeAssistant
    private val calendars = MutableStateFlow<List<HaCalendar>>(emptyList())

    init {
        viewModelScope.launch {
            ha.connection.map { it == HaConnection.Connected }.distinctUntilChanged().collectLatest { connected ->
                if (connected) attempt { ha.fetchCalendars() }?.let { calendars.value = it }
            }
        }
    }

    private val weatherEntities = ha.states
        .map { states ->
            states.values.filter { it.domain == "weather" }.map { it.entityId to it.name }.sortedBy { it.second }
        }
        .distinctUntilChanged()

    private val mediaPlayers = ha.states
        .map { states ->
            states.values.filter { it.domain == "media_player" }.map { it.entityId to it.name }.sortedBy { it.second }
        }
        .distinctUntilChanged()

    val uiState: StateFlow<SettingsUiState> = combine(
        container.settings, ha.connection, calendars, weatherEntities, mediaPlayers,
    ) { s, c, cals, weather, players ->
        SettingsUiState(s, c, cals, weather, players)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { container.settingsRepository.update(transform) }
    }

    fun saveConnection(url: String, token: String) = update { it.copy(haUrl = url.trim(), haToken = token.trim()) }

    fun reconnect() = ha.reconnect()
}
