package com.hakiosk.ui.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hakiosk.AppContainer
import com.hakiosk.data.ha.BrowseItem
import com.hakiosk.data.ha.HaConnection
import com.hakiosk.data.ha.HaState
import com.hakiosk.data.ha.ServiceCall
import com.hakiosk.data.ha.resolveMusicPlayer
import com.hakiosk.data.string
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowseState(
    /** Navigation stack; the last item is the folder being shown. */
    val stack: List<BrowseItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
) {
    val current: BrowseItem? get() = stack.lastOrNull()
}

data class MusicUiState(
    val configured: Boolean = false,
    val connection: HaConnection = HaConnection.NotConfigured,
    val player: HaState? = null,
    val artUrl: String? = null,
    val browse: BrowseState = BrowseState(),
)

class MusicViewModel(private val container: AppContainer) : ViewModel() {
    private val ha = container.homeAssistant
    private val browse = MutableStateFlow(BrowseState())
    private var browseJob: Job? = null

    private val player = combine(ha.states, container.settings) { states, s ->
        resolveMusicPlayer(states, s.musicEntity)
    }

    init {
        // Load the library root whenever the player changes or HA (re)connects.
        viewModelScope.launch {
            combine(player.map { it?.entityId }.distinctUntilChanged(), ha.connection) { id, c -> id to c }
                .map { (id, c) -> if (c == HaConnection.Connected) id else null }
                .distinctUntilChanged()
                .collect { id ->
                    browse.value = BrowseState()
                    if (id != null) load(id, null)
                }
        }
    }

    val uiState: StateFlow<MusicUiState> = combine(
        player, container.settings, ha.connection, browse,
    ) { p, s, c, b ->
        MusicUiState(
            configured = s.haConfigured,
            connection = c,
            player = p,
            artUrl = ha.absoluteUrl(p?.attributes?.string("entity_picture")),
            browse = b,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MusicUiState())

    private fun load(entityId: String, item: BrowseItem?) {
        browseJob?.cancel()
        browseJob = viewModelScope.launch {
            browse.update { it.copy(loading = true, error = null) }
            try {
                val result = ha.browseMedia(entityId, item?.contentId, item?.contentType)
                browse.update { s ->
                    s.copy(stack = if (item == null) listOf(result) else s.stack + result, loading = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                browse.update { it.copy(loading = false, error = e.message ?: "Couldn't load library") }
            }
        }
    }

    /** Folders open; anything else playable starts playing on the player. */
    fun open(item: BrowseItem) {
        val id = uiState.value.player?.entityId ?: return
        if (item.canExpand) load(id, item) else if (item.canPlay) play(item)
    }

    fun play(item: BrowseItem) {
        val id = uiState.value.player?.entityId ?: return
        ha.call(
            id,
            ServiceCall(
                "media_player",
                "play_media",
                mapOf("media_content_id" to item.contentId, "media_content_type" to item.contentType),
            ),
        )
    }

    fun back() {
        browseJob?.cancel()
        browse.update { s -> s.copy(stack = s.stack.dropLast(1).ifEmpty { s.stack }, loading = false, error = null) }
    }

    fun retryBrowse() {
        val id = uiState.value.player?.entityId ?: return
        val stack = browse.value.stack
        if (stack.size <= 1) {
            browse.value = BrowseState()
            load(id, null)
        } else {
            browse.update { it.copy(stack = stack.dropLast(1)) }
            load(id, stack.last())
        }
    }

    fun command(service: String, data: Map<String, Any?> = emptyMap()) {
        val id = uiState.value.player?.entityId ?: return
        ha.call(id, ServiceCall("media_player", service, data))
    }
}
