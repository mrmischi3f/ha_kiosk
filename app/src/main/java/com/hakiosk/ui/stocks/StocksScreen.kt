package com.hakiosk.ui.stocks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hakiosk.data.stocks.ChartRange
import com.hakiosk.ui.components.LineChart
import com.hakiosk.ui.components.Panel
import com.hakiosk.ui.components.Sparkline
import com.hakiosk.ui.formatCompact
import com.hakiosk.ui.formatNumber
import com.hakiosk.ui.formatPercent
import com.hakiosk.ui.formatPrice
import com.hakiosk.ui.formatSigned
import com.hakiosk.ui.currencySymbol
import com.hakiosk.ui.kioskViewModel
import com.hakiosk.ui.theme.KioskColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun StocksScreen() {
    val vm = kioskViewModel { StocksViewModel(it) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    var editingSymbol by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf<String?>(null) }

    Row(
        modifier = Modifier.fillMaxSize().padding(16.dp).imePadding(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WatchlistPane(
            state = state,
            onQuery = vm::setQuery,
            onAdd = vm::add,
            onSelect = vm::select,
            onRefresh = vm::refresh,
            onMove = vm::move,
            modifier = Modifier.weight(0.42f).fillMaxHeight(),
        )
        DetailPane(
            state = state,
            onRange = vm::setRange,
            onEdit = { editingSymbol = it },
            onRemove = { confirmRemove = it },
            modifier = Modifier.weight(0.58f).fillMaxHeight(),
        )
    }

    editingSymbol?.let { symbol ->
        val row = state.rows.firstOrNull { it.item.symbol == symbol }
        if (row != null) {
            HoldingDialog(
                symbol = symbol,
                initialShares = row.item.shares,
                initialCost = row.item.avgCost,
                onSave = { shares, cost ->
                    vm.updateHolding(symbol, shares, cost)
                    editingSymbol = null
                },
                onDismiss = { editingSymbol = null },
            )
        }
    }

    confirmRemove?.let { symbol ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove $symbol?") },
            text = { Text("It will be removed from your watchlist along with any holding info.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.remove(symbol)
                    confirmRemove = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun WatchlistPane(
    state: StocksUiState,
    onQuery: (String) -> Unit,
    onAdd: (com.hakiosk.data.stocks.TickerSearchResult) -> Unit,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    onMove: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Portfolio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                val updated = state.lastUpdated?.atZone(ZoneId.systemDefault())
                    ?.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                Text(
                    state.error ?: updated?.let { "Updated $it" } ?: "Loading…",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.error != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
        }

        state.summaries.forEach { s ->
            val up = s.dayChange >= 0
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                Text(formatPrice(s.value, s.currency), fontSize = 26.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(10.dp))
                Text(
                    "${formatSigned(s.dayChange)} today",
                    color = if (up) KioskColors.Positive else KioskColors.Negative,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                s.totalGain?.let { g ->
                    Text(
                        " · ${formatSigned(g)} total",
                        color = if (g >= 0) KioskColors.Positive else KioskColors.Negative,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            placeholder = { Text("Search ticker or company (e.g. AAPL, Tesla)") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.query.isNotBlank()) {
                SearchResults(state, onAdd)
            } else if (state.rows.isEmpty()) {
                Text(
                    "Your watchlist is empty. Search above to add tickers.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.rows, key = { it.item.symbol }) { row ->
                        WatchRowItem(
                            row = row,
                            selected = row.item.symbol == state.selected?.item?.symbol,
                            onClick = { onSelect(row.item.symbol) },
                            onMove = { delta -> onMove(row.item.symbol, delta) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResults(state: StocksUiState, onAdd: (com.hakiosk.data.stocks.TickerSearchResult) -> Unit) {
    val existing = state.rows.map { it.item.symbol }.toSet()
    when {
        state.search.loading && state.search.results.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.search.error != null -> Text(state.search.error, color = MaterialTheme.colorScheme.error)
        state.search.results.isEmpty() -> Text("No matches", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.search.results) { r ->
                val added = r.symbol in existing
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !added) { onAdd(r) },
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.symbol, fontWeight = FontWeight.Bold)
                            Text(
                                listOf(r.name, r.exchange, r.type).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (added) {
                            Text("Added", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Icon(Icons.Filled.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchRowItem(row: WatchRow, selected: Boolean, onClick: () -> Unit, onMove: (Int) -> Unit) {
    val q = row.quote
    val up = (q?.change ?: 0.0) >= 0
    val color = if (up) KioskColors.Positive else KioskColors.Negative
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.item.symbol, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    q?.name ?: row.item.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (q != null && q.intraday.size > 1) {
                Sparkline(q.intraday, color, Modifier.width(72.dp).height(32.dp), baseline = q.previousClose)
                Spacer(Modifier.width(12.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(q?.let { formatPrice(it.price, it.currency) } ?: "…", fontWeight = FontWeight.SemiBold)
                if (q != null) {
                    Box(
                        Modifier
                            .background(color.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(formatPercent(q.changePercent), color = color, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (selected) {
                Column {
                    IconButton(onClick = { onMove(-1) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                    }
                    IconButton(onClick = { onMove(1) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailPane(
    state: StocksUiState,
    onRange: (ChartRange) -> Unit,
    onEdit: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(modifier = modifier) {
        val row = state.selected
        if (row == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Add a ticker to see details", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Panel
        }
        val q = row.quote
        val chart = state.chart
        val series = chart.series?.takeIf { chart.symbol == row.item.symbol }

        // Change over the selected range (or today for 1D).
        val (change, changePct) = if (series != null && chart.range != ChartRange.D1 && series.closes.isNotEmpty()) {
            val base = series.baseline ?: series.closes.first()
            val last = q?.price ?: series.closes.last()
            (last - base) to (if (base != 0.0) (last - base) / base * 100 else 0.0)
        } else {
            (q?.change ?: 0.0) to (q?.changePercent ?: 0.0)
        }
        val up = change >= 0
        val color = if (up) KioskColors.Positive else KioskColors.Negative

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.item.symbol, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(q?.name ?: row.item.name, q?.exchange?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { onEdit(row.item.symbol) }) { Icon(Icons.Filled.Edit, contentDescription = "Edit holding") }
            IconButton(onClick = { onRemove(row.item.symbol) }) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(q?.let { formatPrice(it.price, it.currency) } ?: "…", fontSize = 40.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.width(12.dp))
            Text(
                "${formatSigned(change)} (${formatPercent(changePct)}) ${if (chart.range == ChartRange.D1) "today" else chart.range.label}",
                color = color,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChartRange.entries.forEach { r ->
                FilterChip(selected = r == chart.range, onClick = { onRange(r) }, label = { Text(r.label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            when {
                series != null && series.closes.size > 1 -> {
                    LineChart(
                        values = series.closes,
                        color = color,
                        baseline = if (chart.range == ChartRange.D1) q?.previousClose ?: series.baseline else series.baseline,
                        baselineColor = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Column(Modifier.align(Alignment.TopEnd)) {
                        Text(
                            formatNumber(series.closes.max()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        formatNumber(series.closes.min()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
                chart.loading -> CircularProgressIndicator()
                chart.error != null -> Text(chart.error, color = MaterialTheme.colorScheme.error)
                else -> Text("No chart data", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (q != null) {
            Row(Modifier.fillMaxWidth()) {
                Stat("Prev close", formatNumber(q.previousClose), Modifier.weight(1f))
                Stat(
                    "Day range",
                    if (q.dayLow != null && q.dayHigh != null) "${formatNumber(q.dayLow)} – ${formatNumber(q.dayHigh)}" else "—",
                    Modifier.weight(1.3f),
                )
                Stat(
                    "52w range",
                    if (q.fiftyTwoWeekLow != null && q.fiftyTwoWeekHigh != null)
                        "${formatNumber(q.fiftyTwoWeekLow)} – ${formatNumber(q.fiftyTwoWeekHigh)}" else "—",
                    Modifier.weight(1.3f),
                )
                Stat("Volume", q.volume?.let { formatCompact(it) } ?: "—", Modifier.weight(0.9f))
            }
        }
        val shares = row.item.shares
        if (shares != null) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("Shares", formatNumber(shares, if (shares % 1.0 == 0.0) 0 else 4), Modifier.weight(1f))
                Stat("Avg cost", row.item.avgCost?.let { formatPrice(it, q?.currency) } ?: "—", Modifier.weight(1.3f))
                Stat("Value", row.marketValue?.let { formatPrice(it, q?.currency) } ?: "—", Modifier.weight(1.3f))
                val gain = row.totalGain
                Stat(
                    "Gain",
                    gain?.let { currencySymbol(q?.currency) + formatSigned(it) } ?: "—",
                    Modifier.weight(0.9f),
                    valueColor = gain?.let { if (it >= 0) KioskColors.Positive else KioskColors.Negative },
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, valueColor: androidx.compose.ui.graphics.Color? = null) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HoldingDialog(
    symbol: String,
    initialShares: Double?,
    initialCost: Double?,
    onSave: (Double?, Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    var shares by remember { mutableStateOf(initialShares?.let { formatPlain(it) } ?: "") }
    var cost by remember { mutableStateOf(initialCost?.let { formatPlain(it) } ?: "") }
    fun parse(s: String) = s.trim().replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$symbol holding") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Optional. Used to show your position's value and gain/loss.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = shares,
                    onValueChange = { shares = it },
                    label = { Text("Shares") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it },
                    label = { Text("Average cost per share") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(parse(shares), parse(cost)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatPlain(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else v.toBigDecimal().stripTrailingZeros().toPlainString()
