package com.hakiosk.data.sports

import com.hakiosk.data.array
import com.hakiosk.data.getJson
import com.hakiosk.data.obj
import com.hakiosk.data.objects
import com.hakiosk.data.settings.AppSettings
import com.hakiosk.data.string
import com.hakiosk.data.bool
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Live scores from ESPN's public scoreboard API (unofficial, key-less):
 * https://site.api.espn.com/apis/site/v2/sports/{sport}/{league}/scoreboard
 */
class SportsRepository(
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
) {
    private val _today = MutableStateFlow<List<Scoreboard>>(emptyList())
    /** Today's scoreboards for the leagues picked in settings, auto-refreshed. */
    val today: StateFlow<List<Scoreboard>> = _today.asStateFlow()

    private val _lastUpdated = MutableStateFlow<Instant?>(null)
    val lastUpdated: StateFlow<Instant?> = _lastUpdated.asStateFlow()

    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun start(settings: StateFlow<AppSettings>) {
        scope.launch {
            settings
                .filter { it.loaded }
                .map { it.sportsLeagues }
                .distinctUntilChanged()
                .collectLatest { leagueIds ->
                    val leagues = Leagues.all.filter { it.id in leagueIds }
                    while (true) {
                        val boards = fetchAll(leagues, null)
                        _today.value = boards
                        _lastUpdated.value = Instant.now()
                        val anyLive = boards.any { b -> b.games.any { it.state == GameState.LIVE } }
                        withTimeoutOrNull(if (anyLive) 30_000L else 180_000L) { refreshRequests.first() }
                    }
                }
        }
    }

    fun refreshNow() {
        refreshRequests.tryEmit(Unit)
    }

    suspend fun fetchAll(leagues: List<League>, date: LocalDate?): List<Scoreboard> = coroutineScope {
        leagues.map { league -> async { fetchScoreboard(league, date) } }.awaitAll()
    }

    suspend fun fetchScoreboard(league: League, date: LocalDate?): Scoreboard {
        val dateParam = date?.let { "?dates=" + it.format(DateTimeFormatter.BASIC_ISO_DATE) } ?: ""
        return try {
            val root = http.getJson(
                "https://site.api.espn.com/apis/site/v2/sports/${league.id}/scoreboard$dateParam"
            ) as? JsonObject
            val games = root?.array("events")?.objects()?.mapNotNull { parseGame(league, it) } ?: emptyList()
            Scoreboard(league, games)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Scoreboard(league, emptyList(), error = e.message ?: "Failed to load")
        }
    }

    private fun parseGame(league: League, event: JsonObject): Game? {
        val competition = event.array("competitions")?.firstOrNull() as? JsonObject ?: return null
        val competitors = competition.array("competitors")?.objects() ?: return null
        val home = competitors.firstOrNull { it.string("homeAway") == "home" } ?: competitors.getOrNull(0) ?: return null
        val away = competitors.firstOrNull { it.string("homeAway") == "away" } ?: competitors.getOrNull(1) ?: return null

        val statusType = (competition.obj("status") ?: event.obj("status"))?.obj("type")
        val state = when (statusType?.string("state")) {
            "in" -> GameState.LIVE
            "post" -> GameState.FINAL
            else -> GameState.PRE
        }
        val start = event.string("date")?.let { raw ->
            runCatching { Instant.parse(raw) }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
        } ?: Instant.now()

        return Game(
            id = event.string("id") ?: "${league.id}-${start.epochSecond}",
            league = league,
            start = start,
            state = state,
            statusText = statusType?.string("shortDetail") ?: statusType?.string("detail") ?: "",
            home = parseTeam(home),
            away = parseTeam(away),
            venue = competition.obj("venue")?.string("fullName"),
            broadcast = competition.array("broadcasts")?.objects()
                ?.flatMap { b -> b.array("names")?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList() }
                ?.distinct()
                ?.joinToString(", ")
                ?.takeIf { it.isNotBlank() },
        )
    }

    private fun parseTeam(c: JsonObject): TeamScore {
        val team = c.obj("team") ?: c.obj("athlete") ?: JsonObject(emptyMap())
        val logo = team.string("logo")
            ?: team.array("logos")?.objects()?.firstOrNull()?.string("href")
            ?: team.obj("flag")?.string("href")
        val score = c.string("score") ?: c.obj("score")?.string("displayValue")
        val displayName = team.string("displayName") ?: team.string("name") ?: "TBD"
        return TeamScore(
            name = displayName,
            shortName = team.string("shortDisplayName") ?: displayName,
            abbreviation = team.string("abbreviation") ?: displayName.take(3).uppercase(),
            logo = logo,
            score = score,
            winner = c.bool("winner") == true,
            record = c.array("records")?.objects()?.firstOrNull()?.string("summary"),
        )
    }
}
