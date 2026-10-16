package com.hakiosk.data.stocks

import com.hakiosk.data.array
import com.hakiosk.data.double
import com.hakiosk.data.getJson
import com.hakiosk.data.long
import com.hakiosk.data.obj
import com.hakiosk.data.objects
import com.hakiosk.data.settings.AppSettings
import com.hakiosk.data.string
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant

/**
 * Stock data from Yahoo Finance's public (unofficial, key-less) endpoints:
 *  - search: query2.finance.yahoo.com/v1/finance/search
 *  - quotes & charts: query1.finance.yahoo.com/v8/finance/chart/{symbol}
 * If Yahoo ever starts blocking these, this is the only file that needs to change.
 */
class StocksRepository(
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
) {
    private val _quotes = MutableStateFlow<Map<String, StockQuote>>(emptyMap())
    val quotes: StateFlow<Map<String, StockQuote>> = _quotes.asStateFlow()

    private val _lastUpdated = MutableStateFlow<Instant?>(null)
    val lastUpdated: StateFlow<Instant?> = _lastUpdated.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val requestLimiter = Semaphore(4)

    fun start(settings: StateFlow<AppSettings>) {
        scope.launch {
            settings
                .filter { it.loaded }
                .map { s -> s.watchlist.map { it.symbol } to s.stockRefreshSec.coerceAtLeast(15) }
                .distinctUntilChanged()
                .collectLatest { (symbols, intervalSec) ->
                    while (true) {
                        refreshQuotes(symbols)
                        withTimeoutOrNull(intervalSec * 1000L) { refreshRequests.first() }
                    }
                }
        }
    }

    fun refreshNow() {
        refreshRequests.tryEmit(Unit)
    }

    private suspend fun refreshQuotes(symbols: List<String>) {
        if (symbols.isEmpty()) {
            _quotes.value = emptyMap()
            _error.value = null
            return
        }
        val results = coroutineScope {
            symbols.map { symbol ->
                async {
                    requestLimiter.withPermit {
                        try {
                            fetchQuote(symbol)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }.awaitAll()
        }
        val previous = _quotes.value
        val next = LinkedHashMap<String, StockQuote>()
        symbols.forEachIndexed { i, symbol ->
            (results[i] ?: previous[symbol])?.let { next[symbol] = it }
        }
        _quotes.value = next
        if (results.all { it == null }) {
            _error.value = "Couldn't reach Yahoo Finance"
        } else {
            _error.value = null
            _lastUpdated.value = Instant.now()
        }
    }

    suspend fun search(query: String): List<TickerSearchResult> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val root = http.getJson(
            "https://query2.finance.yahoo.com/v1/finance/search?q=$q&quotesCount=12&newsCount=0&listsCount=0"
        ) as? JsonObject ?: return emptyList()
        return root.array("quotes")?.objects()?.mapNotNull { o ->
            val symbol = o.string("symbol") ?: return@mapNotNull null
            TickerSearchResult(
                symbol = symbol,
                name = o.string("shortname") ?: o.string("longname") ?: symbol,
                exchange = o.string("exchDisp") ?: o.string("exchange") ?: "",
                type = o.string("typeDisp") ?: o.string("quoteType") ?: "",
            )
        } ?: emptyList()
    }

    suspend fun fetchQuote(symbol: String): StockQuote {
        val result = fetchChartResult(symbol, ChartRange.D1)
        val meta = result.obj("meta") ?: throw IOException("No quote for $symbol")
        val price = meta.double("regularMarketPrice") ?: throw IOException("No price for $symbol")
        val previousClose = meta.double("previousClose") ?: meta.double("chartPreviousClose") ?: price
        return StockQuote(
            symbol = meta.string("symbol") ?: symbol,
            name = meta.string("shortName") ?: meta.string("longName") ?: symbol,
            price = price,
            previousClose = previousClose,
            currency = meta.string("currency") ?: "USD",
            exchange = meta.string("fullExchangeName") ?: meta.string("exchangeName") ?: "",
            dayHigh = meta.double("regularMarketDayHigh"),
            dayLow = meta.double("regularMarketDayLow"),
            volume = meta.long("regularMarketVolume"),
            fiftyTwoWeekHigh = meta.double("fiftyTwoWeekHigh"),
            fiftyTwoWeekLow = meta.double("fiftyTwoWeekLow"),
            marketTime = meta.long("regularMarketTime")?.let { Instant.ofEpochSecond(it) },
            intraday = parseSeries(result).closes,
        )
    }

    suspend fun fetchChart(symbol: String, range: ChartRange): ChartSeries {
        val result = fetchChartResult(symbol, range)
        val series = parseSeries(result)
        val meta = result.obj("meta")
        val baseline = if (range == ChartRange.D1) {
            meta?.double("previousClose") ?: meta?.double("chartPreviousClose")
        } else {
            meta?.double("chartPreviousClose")
        }
        return series.copy(baseline = baseline ?: series.closes.firstOrNull())
    }

    private suspend fun fetchChartResult(symbol: String, range: ChartRange): JsonObject {
        val s = URLEncoder.encode(symbol, "UTF-8")
        val root = http.getJson(
            "https://query1.finance.yahoo.com/v8/finance/chart/$s" +
                "?range=${range.range}&interval=${range.interval}&includePrePost=false"
        ) as? JsonObject ?: throw IOException("Bad response")
        val chart = root.obj("chart") ?: throw IOException("Bad response")
        return chart.array("result")?.firstOrNull() as? JsonObject
            ?: throw IOException(chart.obj("error")?.string("description") ?: "No data for $symbol")
    }

    private fun parseSeries(result: JsonObject): ChartSeries {
        val timestamps = result.array("timestamp")?.map { (it as? JsonPrimitive)?.longOrNull } ?: emptyList()
        val closes = result.obj("indicators")?.array("quote")?.firstOrNull()
            ?.let { it as? JsonObject }
            ?.array("close")
            ?.map { (it as? JsonPrimitive)?.doubleOrNull }
            ?: emptyList()
        val ts = ArrayList<Long>()
        val cs = ArrayList<Double>()
        for (i in closes.indices) {
            val c = closes[i] ?: continue
            val t = timestamps.getOrNull(i) ?: continue
            ts += t
            cs += c
        }
        return ChartSeries(ts, cs, null)
    }
}
