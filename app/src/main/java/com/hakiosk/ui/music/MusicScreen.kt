@file:Suppress("DEPRECATION")

package com.hakiosk.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.hakiosk.data.bool
import com.hakiosk.data.double
import com.hakiosk.data.ha.BrowseItem
import com.hakiosk.data.ha.HaConnection
import com.hakiosk.data.ha.HaState
import com.hakiosk.data.string
import com.hakiosk.data.stringList
import com.hakiosk.ui.LocalAppContainer
import com.hakiosk.ui.components.Panel
import com.hakiosk.ui.kioskViewModel

@Composable
fun MusicScreen(onOpenSettings: () -> Unit) {
    val vm = kioskViewModel { MusicViewModel(it) }
    val state by vm.uiState.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Panel(modifier = Modifier.weight(0.45f).fillMaxHeight()) {
            val player = state.player
            when {
                !state.configured -> Message("Connect Home Assistant first.") {
                    OutlinedButton(onClick = onOpenSettings) { Text("Open settings") }
                }
                player == null && state.connection != HaConnection.Connected -> Message("Connecting to Home Assistant…") {
                    CircularProgressIndicator()
                }
                player == null -> Message(
                    "No Spotify player found.\n\nAdd the Spotify integration in Home Assistant " +
                        "(Settings → Devices & services → Add integration → Spotify), " +
                        "or pick another media player in this app's Settings."
                ) {
                    OutlinedButton(onClick = onOpenSettings) { Text("Open settings") }
                }
                else -> NowPlayingPanel(player, state.artUrl, vm::command)
            }
        }
        Panel(
            modifier = Modifier.weight(0.55f).fillMaxHeight(),
            title = state.browse.current?.title?.ifBlank { null } ?: "Library",
            icon = Icons.Filled.LibraryMusic,
            action = {
                if (state.browse.stack.size > 1) {
                    IconButton(onClick = vm::back) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            },
        ) {
            LibraryContent(state.browse, onOpen = vm::open, onRetry = vm::retryBrowse)
        }
    }
}

@Composable
private fun Message(text: String, action: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action()
    }
}

@Composable
private fun NowPlayingPanel(
    player: HaState,
    artUrl: String?,
    command: (String, Map<String, Any?>) -> Unit,
) {
    val a = player.attributes
    val playing = player.state == "playing"
    val active = player.isPlayingOrPaused
    val position by rememberPlaybackPosition(player)
    val duration = a.double("media_duration")

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .heightIn(max = 280.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp),
            )
            if (artUrl != null && active) {
                AsyncImage(
                    model = artUrl,
                    contentDescription = "Album art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (active) a.string("media_title") ?: player.name else "Nothing playing",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (active) listOfNotNull(a.string("media_artist"), a.string("media_album_name")).joinToString(" · ")
            else "Pick something from your library or a device below",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (active && duration != null && duration > 0) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { (position / duration).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(formatDuration(position), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text(formatDuration(duration), style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val shuffle = a.bool("shuffle") == true
            IconButton(onClick = { command("shuffle_set", mapOf("shuffle" to !shuffle)) }) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { command("media_previous_track", emptyMap()) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(32.dp))
            }
            FilledIconButton(onClick = { command("media_play_pause", emptyMap()) }, modifier = Modifier.size(72.dp)) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = { command("media_next_track", emptyMap()) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(32.dp))
            }
            val repeat = a.string("repeat") ?: "off"
            IconButton(onClick = {
                val next = when (repeat) {
                    "off" -> "all"
                    "all" -> "one"
                    else -> "off"
                }
                command("repeat_set", mapOf("repeat" to next))
            }) {
                Icon(
                    if (repeat == "one") Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Repeat: $repeat",
                    tint = if (repeat != "off") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        a.double("volume_level")?.let { volume ->
            var current by remember(volume) { mutableFloatStateOf(volume.toFloat()) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.VolumeDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = current,
                    onValueChange = { current = it },
                    onValueChangeFinished = { command("volume_set", mapOf("volume_level" to current.toDouble())) },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Spotify Connect devices exposed by HA as the player's sources.
        val sources = a.stringList("source_list")
        if (sources.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Speaker, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    a.string("source")?.let { "Playing on $it" } ?: "Devices",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                sources.forEach { source ->
                    FilterChip(
                        selected = source == a.string("source"),
                        onClick = { command("select_source", mapOf("source" to source)) },
                        label = { Text(source) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryContent(browse: BrowseState, onOpen: (BrowseItem) -> Unit, onRetry: () -> Unit) {
    val ha = LocalAppContainer.current.homeAssistant
    val current = browse.current
    when {
        browse.loading && current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        browse.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(browse.error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry) { Text("Retry") }
        }
        current == null -> Text("Library unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(132.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(current.children) { item ->
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = item.canExpand || item.canPlay) { onOpen(item) }
                            .padding(4.dp)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (item.canExpand) Icons.Filled.LibraryMusic else Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ha.absoluteUrl(item.thumbnail)?.let { url ->
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            item.title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (browse.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}
