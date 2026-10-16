package com.hakiosk.data.ha

import com.hakiosk.data.array
import com.hakiosk.data.bool
import com.hakiosk.data.double
import com.hakiosk.data.obj
import com.hakiosk.data.objects
import com.hakiosk.data.string
import com.hakiosk.data.toJsonElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Keeps a live mirror of all Home Assistant entity states over the WebSocket API and exposes
 * helpers for service calls, history (for "most used" lights), calendars and weather forecasts.
 */
class HomeAssistantRepository(
    private val http: OkHttpClient,
    private val json: Json,
    private val scope: CoroutineScope,
) {
    private val wsHttp = http.newBuilder()
        .pingInterval(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val _states = MutableStateFlow<Map<String, HaState>>(emptyMap())
    val states: StateFlow<Map<String, HaState>> = _states.asStateFlow()

    private val _connection = MutableStateFlow<HaConnection>(HaConnection.NotConfigured)
    val connection: StateFlow<HaConnection> = _connection.asStateFlow()

    private val _registry = MutableStateFlow(HaRegistry())
    val registry: StateFlow<HaRegistry> = _registry.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** One-off error messages from service calls, for snackbars. */
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private data class Config(val baseUrl: String, val token: String) {
        val wsUrl: String get() = baseUrl.replaceFirst("http", "ws") + "/api/websocket"
    }

    private data class PendingUpdate(val state: HaState?)

    @Volatile private var config: Config? = null
    @Volatile private var client: HaWebSocketClient? = null
    private var connectionJob: Job? = null

    /** state_changed events are batched so a chatty power sensor doesn't recompose the UI 50x/sec. */
    private val pendingUpdates = ConcurrentHashMap<String, PendingUpdate>()

    init {
        scope.launch {
            while (true) {
                delay(400)
                flushPendingUpdates()
            }
        }
    }

    @Synchronized
    fun configure(url: String, token: String) {
        val newConfig = if (url.isBlank() || token.isBlank()) null else Config(normalizeUrl(url), token.trim())
        if (newConfig == config && connectionJob?.isActive == true) return
        config = newConfig
        connectionJob?.cancel()
        client?.close()
        client = null
        pendingUpdates.clear()
        _states.value = emptyMap()
        _registry.value = HaRegistry()
        if (newConfig == null) {
            _connection.value = HaConnection.NotConfigured
            return
        }
        connectionJob = scope.launch { connectLoop(newConfig) }
    }

    /** Forces an immediate reconnect, e.g. after the user taps "retry". */
    fun reconnect() {
        val cfg = config ?: return
        synchronized(this) {
            connectionJob?.cancel()
            client?.close()
            connectionJob = scope.launch { connectLoop(cfg) }
        }
    }

    private suspend fun connectLoop(cfg: Config) {
        var backoff = 2_000L
        while (true) {
            _connection.value = HaConnection.Connecting
            val ws = HaWebSocketClient(wsHttp, json, cfg.wsUrl, cfg.token)
            client = ws
            try {
                ws.connect()
                val states = ws.command(buildJsonObject { put("type", "get_states") })
                _states.value = json.decodeFromJsonElement(ListSerializer(HaState.serializer()), states)
                    .associateBy { it.entityId }
                ws.subscribeEvents("state_changed") { event -> onStateChanged(event) }
                _connection.value = HaConnection.Connected
                backoff = 2_000L
                scope.launch { loadRegistry(ws) }
                ws.awaitClosed()
                _connection.value = HaConnection.Failed("Connection lost, reconnecting…")
            } catch (e: CancellationException) {
                ws.close()
                throw e
            } catch (e: HaAuthException) {
                _connection.value = HaConnection.Failed(e.message ?: "Authentication failed", authError = true)
                backoff = 60_000L
            } catch (e: Exception) {
                _connection.value = HaConnection.Failed(e.message ?: e.javaClass.simpleName)
            }
            ws.close()
            if (client === ws) client = null
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(60_000L)
        }
    }

    private fun onStateChanged(event: JsonObject) {
        val data = event.obj("data") ?: return
        val entityId = data.string("entity_id") ?: return
        val newState = data.obj("new_state")?.let {
            runCatching { json.decodeFromJsonElement(HaState.serializer(), it) }.getOrNull()
        }
        pendingUpdates[entityId] = PendingUpdate(newState)
    }

    private fun flushPendingUpdates() {
        if (pendingUpdates.isEmpty()) return
        val batch = HashMap<String, PendingUpdate>()
        for (key in pendingUpdates.keys) {
            val value = pendingUpdates[key] ?: continue
            if (pendingUpdates.remove(key, value)) batch[key] = value
        }
        if (batch.isEmpty()) return
        _states.update { current ->
            val next = current.toMutableMap()
            batch.forEach { (id, update) ->
                if (update.state == null) next.remove(id) else next[id] = update.state
            }
            next
        }
    }

    private suspend fun loadRegistry(ws: HaWebSocketClient) {
        try {
            val areas = json.decodeFromJsonElement(
                ListSerializer(HaArea.serializer()),
                ws.command(typeOnly("config/area_registry/list")),
            )
            val devices = json.decodeFromJsonElement(
                ListSerializer(HaDevice.serializer()),
                ws.command(typeOnly("config/device_registry/list")),
            )
            val entities = json.decodeFromJsonElement(
                ListSerializer(HaEntityEntry.serializer()),
                ws.command(typeOnly("config/entity_registry/list"), timeoutMs = 45_000),
            )
            val deviceArea = devices.mapNotNull { d -> d.areaId?.let { d.id to it } }.toMap()
            val entityArea = entities.mapNotNull { e ->
                (e.areaId ?: e.deviceId?.let(deviceArea::get))?.let { e.entityId to it }
            }.toMap()
            val hidden = entities
                .filter { it.hiddenBy != null || it.disabledBy != null || it.entityCategory != null }
                .map { it.entityId }
                .toSet()
            _registry.value = HaRegistry(areas.associate { it.areaId to it.name }, entityArea, hidden)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Registry is optional (used for area grouping); ignore failures.
        }
    }

    private fun typeOnly(type: String) = buildJsonObject { put("type", type) }

    // region Service calls

    suspend fun callService(
        domain: String,
        service: String,
        entityId: String? = null,
        data: Map<String, Any?> = emptyMap(),
        returnResponse: Boolean = false,
    ): JsonElement {
        val ws = client ?: throw IOException("Not connected to Home Assistant")
        val payload = buildJsonObject {
            put("type", "call_service")
            put("domain", domain)
            put("service", service)
            put("service_data", data.toJsonElement())
            if (entityId != null) putJsonObject("target") { put("entity_id", entityId) }
            if (returnResponse) put("return_response", true)
        }
        return ws.command(payload)
    }

    /** Fire-and-forget service call; failures are reported on [errors]. */
    fun call(entityId: String, call: ServiceCall) {
        scope.launch {
            try {
                callService(call.domain, call.service, entityId, call.data)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errors.tryEmit("${call.domain}.${call.service} failed: ${e.message}")
            }
        }
    }

    // endregion

    /** Turns HA-relative paths (e.g. entity_picture "/api/media_player_proxy/…") into full URLs. */
    fun absoluteUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val base = config?.baseUrl ?: return null
        return base + (if (path.startsWith("/")) path else "/$path")
    }

    /** Browses a media player's library. Pass null ids for the root. */
    suspend fun browseMedia(entityId: String, contentId: String?, contentType: String?): BrowseItem {
        val ws = client ?: throw IOException("Not connected to Home Assistant")
        val result = ws.command(
            buildJsonObject {
                put("type", "media_player/browse_media")
                put("entity_id", entityId)
                if (contentId != null && contentType != null) {
                    put("media_content_id", contentId)
                    put("media_content_type", contentType)
                }
            },
            timeoutMs = 30_000,
        ) as? JsonObject ?: throw IOException("Empty library response")
        return parseBrowseItem(result)
    }

    private fun parseBrowseItem(o: JsonObject): BrowseItem = BrowseItem(
        title = o.string("title") ?: "",
        mediaClass = o.string("media_class") ?: "",
        contentId = o.string("media_content_id") ?: "",
        contentType = o.string("media_content_type") ?: "",
        canPlay = o.bool("can_play") == true,
        canExpand = o.bool("can_expand") == true,
        thumbnail = o.string("thumbnail"),
        children = o.array("children")?.objects()?.map { parseBrowseItem(it) } ?: emptyList(),
    )

    // region REST helpers

    private fun apiUrl(path: String): HttpUrl.Builder {
        val cfg = config ?: throw IOException("Home Assistant is not configured")
        val base = cfg.baseUrl.toHttpUrlOrNull() ?: throw IOException("Invalid Home Assistant URL")
        return base.newBuilder().addPathSegments(path)
    }

    private suspend fun getJson(url: HttpUrl): JsonElement = withContext(Dispatchers.IO) {
        val cfg = config ?: throw IOException("Home Assistant is not configured")
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${cfg.token}")
            .header("Content-Type", "application/json")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for ${url.encodedPath}")
            json.parseToJsonElement(response.body?.string().orEmpty())
        }
    }

    /**
     * Counts how many times each light changed between on/off in the last [lookbackDays] days,
     * using the history API. This is how the dashboard picks the "most used" lights.
     */
    suspend fun fetchLightUsage(lookbackDays: Int): Map<String, Int> {
        if (config == null) return emptyMap()
        val lights = states.value.keys.filter { it.startsWith("light.") }
        if (lights.isEmpty()) return emptyMap()
        val start = Instant.now()
            .minus(Duration.ofDays(lookbackDays.toLong()))
            .truncatedTo(ChronoUnit.SECONDS)
        val url = apiUrl("api/history/period")
            .addPathSegment(start.toString())
            .addQueryParameter("filter_entity_id", lights.joinToString(","))
            .addQueryParameter("minimal_response", null)
            .addQueryParameter("no_attributes", null)
            .build()
        val result = getJson(url) as? JsonArray ?: return emptyMap()
        val counts = HashMap<String, Int>()
        for (series in result) {
            val points = (series as? JsonArray)?.objects() ?: continue
            val entityId = points.firstOrNull()?.string("entity_id") ?: continue
            var last: String? = null
            var changes = 0
            for (point in points) {
                val s = point.string("state")
                if (s != "on" && s != "off") continue
                if (last != null && s != last) changes++
                last = s
            }
            counts[entityId] = (counts[entityId] ?: 0) + changes
        }
        return counts
    }

    suspend fun fetchCalendars(): List<HaCalendar> {
        val result = getJson(apiUrl("api/calendars").build()) as? JsonArray ?: return emptyList()
        return result.objects().mapNotNull { o ->
            val id = o.string("entity_id") ?: return@mapNotNull null
            HaCalendar(id, o.string("name") ?: id.substringAfter('.'))
        }
    }

    suspend fun fetchCalendarEvents(
        calendars: List<HaCalendar>,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): List<CalendarEvent> {
        val zone = ZoneId.systemDefault()
        val events = mutableListOf<CalendarEvent>()
        for (calendar in calendars) {
            val url = apiUrl("api/calendars")
                .addPathSegment(calendar.entityId)
                .addQueryParameter("start", from.toInstant().toString())
                .addQueryParameter("end", to.toInstant().toString())
                .build()
            val result = runCatching { getJson(url) as? JsonArray }.getOrNull() ?: continue
            for (o in result.objects()) {
                val (start, allDay) = parseEventTime(o.obj("start"), zone) ?: continue
                val (end, _) = parseEventTime(o.obj("end"), zone) ?: (start.plusHours(1) to allDay)
                events += CalendarEvent(
                    summary = o.string("summary") ?: "(No title)",
                    start = start,
                    end = end,
                    allDay = allDay,
                    location = o.string("location")?.takeIf { it.isNotBlank() },
                    calendarId = calendar.entityId,
                    calendarName = calendar.name,
                )
            }
        }
        return events.sortedBy { it.start }
    }

    private fun parseEventTime(o: JsonObject?, zone: ZoneId): Pair<ZonedDateTime, Boolean>? {
        if (o == null) return null
        o.string("dateTime")?.let { raw ->
            val parsed = runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(zone) }.getOrNull()
                ?: runCatching { ZonedDateTime.parse(raw).withZoneSameInstant(zone) }.getOrNull()
                ?: runCatching { LocalDateTime.parse(raw).atZone(zone) }.getOrNull()
            return parsed?.let { it to false }
        }
        o.string("date")?.let { raw ->
            return runCatching { LocalDate.parse(raw).atStartOfDay(zone) to true }.getOrNull()
        }
        return null
    }

    /** Daily forecast via the weather.get_forecasts service (HA 2023.9+). */
    suspend fun fetchDailyForecast(weatherEntity: String): List<ForecastDay> {
        val response = callService(
            domain = "weather",
            service = "get_forecasts",
            entityId = weatherEntity,
            data = mapOf("type" to "daily"),
            returnResponse = true,
        ) as? JsonObject ?: return emptyList()
        val forecast = response.obj("response")?.obj(weatherEntity)?.array("forecast") ?: return emptyList()
        val zone = ZoneId.systemDefault()
        return forecast.objects().mapNotNull { f ->
            val raw = f.string("datetime") ?: return@mapNotNull null
            val date = runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(zone).toLocalDate() }.getOrNull()
                ?: runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
                ?: return@mapNotNull null
            ForecastDay(
                date = date,
                condition = f.string("condition") ?: "",
                high = f.double("temperature"),
                low = f.double("templow"),
                precipitationProbability = f.double("precipitation_probability"),
            )
        }
    }

    // endregion

    companion object {
        fun normalizeUrl(raw: String): String {
            var url = raw.trim().trimEnd('/')
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
            return url
        }
    }
}
