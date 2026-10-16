package com.hakiosk.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hakiosk.data.ha.HaConnection
import com.hakiosk.ui.components.EntityControlDialog
import com.hakiosk.ui.components.EntityTile
import com.hakiosk.ui.components.tapAction
import com.hakiosk.ui.kioskViewModel

@Composable
fun HomeScreen(onOpenSettings: () -> Unit) {
    val vm = kioskViewModel { HomeViewModel(it) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    var dialogEntityId by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp).imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Home", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    when (val c = state.connection) {
                        HaConnection.Connected -> "${state.entityCount} entities"
                        HaConnection.Connecting -> "Connecting…"
                        HaConnection.NotConfigured -> "Not configured"
                        is HaConnection.Failed -> c.message
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.connection is HaConnection.Failed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.connection is HaConnection.Failed) {
                OutlinedButton(onClick = vm::reconnect) { Text("Retry") }
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Search devices") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.width(300.dp),
            )
            if (state.hasAreas) {
                FilterChip(
                    selected = state.groupByArea,
                    onClick = vm::toggleGroupByArea,
                    label = { Text("By area") },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.availableFilters) { f ->
                FilterChip(
                    selected = f == state.filter,
                    onClick = { vm.setFilter(f) },
                    label = { Text(f.label) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        when {
            !state.configured -> CenterMessage("Home Assistant isn't set up yet.") {
                Button(onClick = onOpenSettings) { Text("Open settings") }
            }
            state.sections.isEmpty() && state.connection != HaConnection.Connected -> CenterMessage(
                "Waiting for Home Assistant…"
            ) { CircularProgressIndicator() }
            state.sections.isEmpty() -> CenterMessage("Nothing matches.") {}
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(170.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                state.sections.forEach { section ->
                    item(key = "header-${section.title}", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            section.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(section.entities, key = { "${section.title}/${it.entityId}" }) { e ->
                        EntityTile(
                            entity = e,
                            onTap = {
                                val action = tapAction(e)
                                if (action != null) vm.call(e.entityId, action) else dialogEntityId = e.entityId
                            },
                            onLongPress = { dialogEntityId = e.entityId },
                            isFavorite = e.entityId in state.favoriteIds,
                        )
                    }
                }
            }
        }
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
private fun CenterMessage(text: String, action: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.TopCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action()
        }
    }
}
