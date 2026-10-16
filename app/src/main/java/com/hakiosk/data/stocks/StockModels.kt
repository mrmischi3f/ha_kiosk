package com.hakiosk.data.stocks

import java.time.Instant

data class TickerSearchResult(
    val symbol: String,
    val name: String,
    val exchange: String,
    val type: String,
)

data class StockQuote(
    val symbol: String,
    val name: String,
    val price: Double,
    val previousClose: Double,
    val currency: String,
    val exchange: String,
    val dayHigh: Double?,
    val dayLow: Double?,
    val volume: Long?,
    val fiftyTwoWeekHigh: Double?,
    val fiftyTwoWeekLow: Double?,
    val marketTime: Instant?,
    /** Intraday closes, used for sparklines. */
    val intraday: List<Double>,
) {
    val change: Double get() = price - previousClose
    val changePercent: Double get() = if (previousClose != 0.0) change / previousClose * 100.0 else 0.0
}

enum class ChartRange(val label: String, val range: String, val interval: String) {
    D1("1D", "1d", "5m"),
    D5("5D", "5d", "15m"),
    M1("1M", "1mo", "1d"),
    M6("6M", "6mo", "1d"),
    YTD("YTD", "ytd", "1d"),
    Y1("1Y", "1y", "1d"),
    Y5("5Y", "5y", "1wk"),
}

data class ChartSeries(
    val timestamps: List<Long>,
    val closes: List<Double>,
    /** Close before the first point (used as the baseline for change over the range). */
    val baseline: Double?,
)
