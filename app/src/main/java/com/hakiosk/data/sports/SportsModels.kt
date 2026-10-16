package com.hakiosk.data.sports

import java.time.Instant

enum class Sport { SOCCER, BASKETBALL, FOOTBALL, BASEBALL, HOCKEY }

/** [id] is the ESPN path segment, e.g. "soccer/eng.1". */
data class League(val id: String, val name: String, val shortName: String, val sport: Sport)

object Leagues {
    val all = listOf(
        League("soccer/eng.1", "Premier League", "EPL", Sport.SOCCER),
        League("soccer/esp.1", "LaLiga", "LaLiga", Sport.SOCCER),
        League("soccer/ita.1", "Serie A", "Serie A", Sport.SOCCER),
        League("soccer/ger.1", "Bundesliga", "BUN", Sport.SOCCER),
        League("soccer/fra.1", "Ligue 1", "L1", Sport.SOCCER),
        League("soccer/uefa.champions", "Champions League", "UCL", Sport.SOCCER),
        League("soccer/uefa.europa", "Europa League", "UEL", Sport.SOCCER),
        League("soccer/usa.1", "MLS", "MLS", Sport.SOCCER),
        League("soccer/arg.1", "Liga Profesional Argentina", "ARG", Sport.SOCCER),
        League("soccer/bra.1", "Brasileirão", "BRA", Sport.SOCCER),
        League("soccer/mex.1", "Liga MX", "MEX", Sport.SOCCER),
        League("soccer/conmebol.libertadores", "Copa Libertadores", "LIB", Sport.SOCCER),
        League("soccer/fifa.world", "FIFA World Cup", "WC", Sport.SOCCER),
        League("basketball/nba", "NBA", "NBA", Sport.BASKETBALL),
        League("basketball/wnba", "WNBA", "WNBA", Sport.BASKETBALL),
        League("basketball/mens-college-basketball", "NCAA Men's Basketball", "NCAAM", Sport.BASKETBALL),
        League("football/nfl", "NFL", "NFL", Sport.FOOTBALL),
        League("football/college-football", "NCAA Football", "NCAAF", Sport.FOOTBALL),
        League("baseball/mlb", "MLB", "MLB", Sport.BASEBALL),
        League("hockey/nhl", "NHL", "NHL", Sport.HOCKEY),
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): League? = byId[id]
}

enum class GameState { PRE, LIVE, FINAL }

data class TeamScore(
    val name: String,
    val shortName: String,
    val abbreviation: String,
    val logo: String?,
    val score: String?,
    val winner: Boolean,
    val record: String?,
)

data class Game(
    val id: String,
    val league: League,
    val start: Instant,
    val state: GameState,
    /** e.g. "2nd Half - 67'", "Final", "7:30 PM ET". */
    val statusText: String,
    val home: TeamScore,
    val away: TeamScore,
    val venue: String?,
    val broadcast: String?,
)

data class Scoreboard(
    val league: League,
    val games: List<Game>,
    val error: String? = null,
)
