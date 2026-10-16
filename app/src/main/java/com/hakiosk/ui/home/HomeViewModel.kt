package com.hakiosk.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hakiosk.AppContainer
import com.hakiosk.data.ha.HaConnection
import com.hakiosk.data.ha.HaState
import com.hakiosk.data.ha.ServiceCall
import com.hakiosk.ui.humanize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DomainFilter(val label: String, val domains: Set<String>) {
    All("All", emptySet()),
    Lights("Lights", setOf("light")),
    Switches("Switches", setOf("switch", "input_boolean")),
    Climate("Climate", setOf("climate")),
    Covers("Covers", setOf("cover")),
    Media("Media", setOf("media_player")),
    Fans("Fans", setOf("fan")),
    Locks("Locks", setOf("lock")),
    Scenes("Scenes & scripts", setOf("scene", "script")),
    Sensors("Sensors", setOf("sensor", "binary_sensor")),
    Other("Other", setOf("vacuum", "button", "input_button", "automation", "person"));

    companion object {
        val supportedDomains: Set<String> = entries.flatMap { it.domains }.toSet()

        fun forDomain(domain: String): DomainFilter? = entries.firstOrNull { domain in it.domains }
    }
}

/** Order of domains inside a section: controllable things first, sensors last. */
private val DOMAIN_ORDER = listOf(
    "light", "switch", "input_boolean", "fan", "cover", "climate", "media_player", "lock",
    "vacuum", "scene", "script", "button", "input_button", "automation", "person",
    "binary_sensor", "sensor",
)

data class EntitySection(val title: String, val entities: List<HaState>)

data class HomeUiState(
    val configured: Boolean = false,
    val connection: HaConnection = HaConnection.NotConfigured,
    val sections: List<EntitySection> = emptyList(),
    val filter: DomainFilter = DomainFilter.All,
    val availableFilters: List<DomainFilter> = listOf(DomainFilter.All),
    val query: String = "",
    val groupByArea: Boolean = true,
    val hasAreas: Boolean = false,
    val favoriteIds: Set<String> = emptySet(),
    val entityCount: Int = 0,
) {
    fun entity(id: String): HaState? =
        sections.asSequence().flatMap { it.entities.asSequence() }.firstOrNull { it.entityId == id }
}

private data class Controls(val query: String, val filter: DomainFilter, val groupByArea: Boolean)

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val ha = container.homeAssistant
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(DomainFilter.All)
    private val groupByArea = MutableStateFlow(true)

    private val controls = combine(query, filter, groupByArea) { q, f, g -> Controls(q, f, g) }

    val uiState: StateFlow<HomeUiState> = combine(
        ha.states, ha.registry, container.settings, ha.connection, controls,
    ) { states, registry, settings, connection, c ->
        val visible = states.values.filter {
            it.domain in DomainFilter.supportedDomains && it.entityId !in registry.hidden
        }
        val available = listOf(DomainFilter.All) +
            DomainFilter.entries.filter { f -> f != DomainFilter.All && visible.any { it.domain in f.domains } }
        val activeFilter = if (c.filter in available) c.filter else DomainFilter.All
        val q = c.query.trim().lowercase()
        val filtered = visible
            .filter { activeFilter == DomainFilter.All || it.domain in activeFilter.domains }
            .filter { q.isEmpty() || it.name.lowercase().contains(q) || it.entityId.contains(q) }
            .sortedWith(
                compareBy<HaState> { DOMAIN_ORDER.indexOf(it.domain).let { i -> if (i < 0) 99 else i } }
                    .thenBy { it.name.lowercase() }
            )

        val hasAreas = registry.areas.isNotEmpty()
        val sections = if (c.groupByArea && hasAreas) {
            filtered.groupBy { registry.areaName(it.entityId) }
                .entries
                .sortedWith(compareBy<Map.Entry<String?, List<HaState>>>({ it.key == null }, { it.key?.lowercase() }))
                .map { (area, list) -> EntitySection(area ?: "Unassigned", list) }
        } else {
            filtered.groupBy { DomainFilter.forDomain(it.domain)?.label ?: humanize(it.domain) }
                .map { (title, list) -> EntitySection(title, list) }
        }

        HomeUiState(
            configured = settings.haConfigured,
            connection = connection,
            sections = sections,
            filter = activeFilter,
            availableFilters = available,
            query = c.query,
            groupByArea = c.groupByArea,
            hasAreas = hasAreas,
            favoriteIds = settings.favoriteEntities.toSet(),
            entityCount = filtered.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setFilter(value: DomainFilter) {
        filter.value = value
    }

    fun toggleGroupByArea() {
        groupByArea.value = !groupByArea.value
    }

    fun call(entityId: String, call: ServiceCall) = ha.call(entityId, call)

    fun reconnect() = ha.reconnect()

    fun toggleFavorite(entityId: String) {
        viewModelScope.launch { container.settingsRepository.toggleFavorite(entityId) }
    }
}
