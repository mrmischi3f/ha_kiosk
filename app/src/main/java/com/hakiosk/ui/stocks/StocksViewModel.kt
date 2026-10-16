@file:OptIn(FlowPreview::class)

package com.hakiosk.ui.stocks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hakiosk.AppContainer
import com.hakiosk.data.settings.WatchItem
import com.hakiosk.data.stocks.ChartRange
import com.hakiosk.data.stocks.ChartSeries
import com.hakiosk.data.stocks.StockQuote
import com.hakiosk.data.stocks.TickerSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

data class WatchRow(val item: WatchItem, val quote: StockQuote?) {
    val marketValue: Double? get() = item.shares?.let { s -> quote?.price?.times(s) }
    val totalGain: Double? get() {
        val shares = item.shares ?: return null
        val cost = item.avgCost ?: return null
        val price = quote?.price ?: return null
        return (price - cost) * shares
    }
}

data class PortfolioSummary(
    val currency: String,
    val value: Double,
    val dayChange: Double,
    val totalGain: Double?,
)

data class SearchState(
    val results: List<TickerSearchResult> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

data class ChartState(
    val symbol: String? = null,
    val range: ChartRange = ChartRange.D1,
    val series: ChartSeries? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

data class StocksUiState(
    val rows: List<WatchRow> = emptyList(),
    val selected: WatchRow? = null,
    val chart: ChartState = ChartState(),
    val query: String = "",
    val search: SearchState = SearchState(),
    val summaries: List<PortfolioSummary> = emptyList(),
    val lastUpdated: Instant? = null,
    val error: String? = null,
)

class StocksViewModel(private val container: AppContainer) : ViewModel() {
    private val stocks = container.stocks
    private val settings = container.settings

    private val query = MutableStateFlow("")
    private val search = MutableStateFlow(SearchState())
    private val selectedSymbol = MutableStateFlow<String?>(null)
    private val range = MutableStateFlow(ChartRange.D1)
    private val chart = MutableStateFlow(ChartState())

    /** The explicitly selected symbol, or the first one in the watchlist. */
    private val effectiveSymbol = combine(selectedSymbol, settings) { sel, s ->
        sel?.takeIf { sym -> s.watchlist.any { it.symbol == sym } } ?: s.watchlist.firstOrNull()?.symbol
    }.distinctUntilChanged()

    init {
        viewModelScope.launch {
            query.debounce(350).distinctUntilChanged().collectLatest { q ->
                if (q.isBlank()) {
                    search.value = SearchState()
                    return@collectLatest
                }
                search.value = search.value.copy(loading = true, error = null)
                search.value = try {
                    SearchState(results = stocks.search(q))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    SearchState(error = "Search failed: ${e.message}")
                }
            }
        }
        viewModelScope.launch {
            combine(effectiveSymbol, range) { s, r -> s to r }.collectLatest { (symbol, r) ->
                if (symbol == null) {
                    chart.value = ChartState(range = r)
                    return@collectLatest
                }
                chart.value = ChartState(symbol = symbol, range = r, loading = true)
                while (true) {
                    chart.value = try {
                        ChartState(symbol = symbol, range = r, series = stocks.fetchChart(symbol, r))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        chart.value.copy(loading = false, error = e.message ?: "Couldn't load chart")
                    }
                    // Keep the intraday chart moving; longer ranges don't need it.
                    if (r != ChartRange.D1 && r != ChartRange.D5) break
                    delay(60_000)
                }
            }
        }
    }

    private val base = combine(settings, stocks.quotes, effectiveSymbol, stocks.lastUpdated, stocks.error) { s, quotes, sym, updated, err ->
        val rows = s.watchlist.map { WatchRow(it, quotes[it.symbol]) }
        val summaries = rows
            .filter { it.item.shares != null && it.quote != null }
            .groupBy { it.quote!!.currency }
            .map { (currency, list) ->
                val gains = list.mapNotNull { it.totalGain }
                PortfolioSummary(
                    currency = currency,
                    value = list.sumOf { it.marketValue ?: 0.0 },
                    dayChange = list.sumOf { it.quote!!.change * (it.item.shares ?: 0.0) },
                    totalGain = if (gains.isEmpty()) null else gains.sum(),
                )
            }
        StocksUiState(
            rows = rows,
            selected = rows.firstOrNull { it.item.symbol == sym },
            summaries = summaries,
            lastUpdated = updated,
            error = err,
        )
    }

    val uiState: StateFlow<StocksUiState> = combine(base, chart, query, search) { b, c, q, srch ->
        b.copy(chart = c, query = q, search = srch)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StocksUiState())

    fun setQuery(value: String) {
        query.value = value
        if (value.isBlank()) search.value = SearchState()
    }

    fun select(symbol: String) {
        selectedSymbol.value = symbol
    }

    fun setRange(value: ChartRange) {
        range.value = value
    }

    fun refresh() = stocks.refreshNow()

    fun add(result: TickerSearchResult) {
        viewModelScope.launch {
            container.settingsRepository.update { s ->
                if (s.watchlist.any { it.symbol == result.symbol }) s
                else s.copy(watchlist = s.watchlist + WatchItem(result.symbol, result.name, result.exchange))
            }
            selectedSymbol.value = result.symbol
            setQuery("")
        }
    }

    fun remove(symbol: String) {
        viewModelScope.launch {
            container.settingsRepository.update { s -> s.copy(watchlist = s.watchlist.filterNot { it.symbol == symbol }) }
        }
    }

    fun move(symbol: String, delta: Int) {
        viewModelScope.launch {
            container.settingsRepository.update { s ->
                val list = s.watchlist.toMutableList()
                val i = list.indexOfFirst { it.symbol == symbol }
                val j = i + delta
                if (i < 0 || j !in list.indices) return@update s
                val item = list.removeAt(i)
                list.add(j, item)
                s.copy(watchlist = list)
            }
        }
    }

    fun updateHolding(symbol: String, shares: Double?, avgCost: Double?) {
        viewModelScope.launch {
            container.settingsRepository.update { s ->
                s.copy(watchlist = s.watchlist.map {
                    if (it.symbol == symbol) it.copy(shares = shares, avgCost = avgCost) else it
                })
            }
        }
    }
}
