@file:OptIn(ExperimentalLayoutApi::class)

package com.hakiosk.ui.sports

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.hakiosk.data.sports.Game
import com.hakiosk.data.sports.GameState
import com.hakiosk.data.sports.Leagues
import com.hakiosk.data.sports.Sport
import com.hakiosk.data.sports.TeamScore
import com.hakiosk.ui.kioskViewModel
import com.hakiosk.ui.theme.KioskColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SportsScreen() {
    val vm = kioskViewModel { SportsViewModel(it) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showLeaguePicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Scores", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                val updated = state.lastUpdated?.atZone(ZoneId.systemDefault())
                    ?.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                Text(
                    buildString {
                        if (state.liveCount > 0) append("${state.liveCount} live · ")
                        append(updated?.let { "Updated $it" } ?: "Loading…")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            listOf(-1 to "Yesterday", 0 to "Today", 1 to "Tomorrow").forEach { (offset, label) ->
                FilterChip(
                    selected = state.dayOffset == offset,
                    onClick = { vm.setDayOffset(offset) },
                    label = { Text(label) },
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            FilterChip(
                selected = state.liveOnly,
                onClick = vm::toggleLiveOnly,
                label = { Text("Live only") },
                modifier = Modifier.padding(start = 12.dp),
            )
            IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
            IconButton(onClick = { showLeaguePicker = true }) { Icon(Icons.Filled.Tune, contentDescription = "Choose leagues") }
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = state.leagueFilter == null,
                    onClick = { vm.setLeagueFilter(null) },
                    label = { Text("All leagues") },
                )
            }
            items(state.selectedLeagues, key = { it.id }) { league ->
                FilterChip(
                    selected = state.leagueFilter == league.id,
                    onClick = { vm.setLeagueFilter(league.id) },
                    label = { Text(league.name) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        when {
            state.selectedLeagues.isEmpty() -> Message("No leagues selected. Tap the tune icon to pick some.")
            state.loading && state.sections.isEmpty() -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.sections.isEmpty() -> Message(if (state.liveOnly) "No live games right now." else "No games on this day.")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(300.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                state.sections.forEach { section ->
                    item(key = "h-${section.league.id}", span = { GridItemSpan(maxLineSpan) }) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            Text(
                                section.league.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            section.error?.let {
                                Text(
                                    "  · couldn't load ($it)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    items(section.games, key = { "${section.league.id}-${it.id}" }) { game -> GameCard(game) }
                }
            }
        }
    }

    if (showLeaguePicker) {
        LeaguePickerDialog(
            selected = state.selectedLeagues.map { it.id }.toSet(),
            onToggle = vm::toggleLeague,
            onDismiss = { showLeaguePicker = false },
        )
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GameCard(g: Game) {
    val live = g.state == GameState.LIVE
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (live) {
                    Box(Modifier.size(8.dp).background(KioskColors.Live, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text("LIVE", style = MaterialTheme.typography.labelMedium, color = KioskColors.Live, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    when (g.state) {
                        GameState.PRE -> g.start.atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("EEE HH:mm", Locale.getDefault()))
                        else -> g.statusText
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (live) KioskColors.Live else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                g.broadcast?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(90.dp),
                    )
                }
            }
            // ESPN convention: soccer lists home first, US sports list away first.
            if (g.league.sport == Sport.SOCCER) {
                TeamRow(g.home, g)
                TeamRow(g.away, g)
            } else {
                TeamRow(g.away, g)
                TeamRow(g.home, g)
            }
            g.venue?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TeamRow(team: TeamScore, game: Game) {
    val dimmed = game.state == GameState.FINAL && !team.winner &&
        (game.home.winner || game.away.winner)
    val textColor = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = team.logo, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                team.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (team.winner) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            team.record?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (game.state != GameState.PRE) {
            Text(
                team.score ?: "-",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = if (team.winner || game.state == GameState.LIVE) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
            )
        }
    }
}

@Composable
private fun LeaguePickerDialog(selected: Set<String>, onToggle: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Leagues") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Sport.entries.forEach { sport ->
                    val leagues = Leagues.all.filter { it.sport == sport }
                    if (leagues.isEmpty()) return@forEach
                    Text(
                        sport.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        leagues.forEach { league ->
                            FilterChip(
                                selected = league.id in selected,
                                onClick = { onToggle(league.id) },
                                label = { Text(league.name) },
                            )
                        }
                    }
                }
            }
        },
    )
}
