package com.hakiosk.data.ha

import com.hakiosk.data.bool
import com.hakiosk.data.int
import com.hakiosk.data.obj
import com.hakiosk.data.string
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class HaAuthException(message: String) : IOException(message)
class HaCommandException(message: String) : IOException(message)

/**
 * One Home Assistant WebSocket API session (https://developers.home-assistant.io/docs/api/websocket).
 * Create a new instance per connection attempt.
 */
internal class HaWebSocketClient(
    private val http: OkHttpClient,
    private val json: Json,
    private val wsUrl: String,
    private val token: String,
) {
    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonElement>>()
    private val subscriptions = ConcurrentHashMap<Int, (JsonObject) -> Unit>()
    private val authenticated = CompletableDeferred<Unit>()
    private val closed = CompletableDeferred<Unit>()

    @Volatile
    private var socket: WebSocket? = null

    /** Opens the socket and suspends until authentication succeeds. */
    suspend fun connect() {
        val request = try {
            Request.Builder().url(wsUrl).build()
        } catch (e: IllegalArgumentException) {
            throw IOException("Invalid Home Assistant URL")
        }
        socket = http.newWebSocket(request, Listener())
        withTimeoutOrNull(15_000) { authenticated.await() }
            ?: run {
                close()
                throw IOException("Timed out connecting to Home Assistant")
            }
    }

    suspend fun awaitClosed() {
        closed.await()
    }

    fun close() {
        socket?.close(1000, null)
        finish(IOException("Connection closed"))
    }

    /** Sends a command and returns its `result` payload. */
    suspend fun command(payload: JsonObject, timeoutMs: Long = 20_000): JsonElement {
        val id = nextId.getAndIncrement()
        val deferred = CompletableDeferred<JsonElement>()
        pending[id] = deferred
        try {
            send(id, payload)
            return withTimeoutOrNull(timeoutMs) { deferred.await() }
                ?: throw IOException("Home Assistant did not respond in time")
        } finally {
            pending.remove(id)
        }
    }

    suspend fun subscribeEvents(eventType: String, onEvent: (JsonObject) -> Unit) {
        val id = nextId.getAndIncrement()
        val deferred = CompletableDeferred<JsonElement>()
        subscriptions[id] = onEvent
        pending[id] = deferred
        try {
            send(id, buildJsonObject {
                put("type", "subscribe_events")
                put("event_type", eventType)
            })
            withTimeoutOrNull(15_000) { deferred.await() }
                ?: throw IOException("Subscribing to $eventType timed out")
        } catch (e: Throwable) {
            subscriptions.remove(id)
            throw e
        } finally {
            pending.remove(id)
        }
    }

    private fun send(id: Int, payload: JsonObject) {
        val message = JsonObject(payload + ("id" to JsonPrimitive(id)))
        val ws = socket ?: throw IOException("Not connected")
        if (!ws.send(message.toString())) throw IOException("Connection closed")
    }

    private fun finish(error: Throwable) {
        authenticated.completeExceptionally(error)
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
        subscriptions.clear()
        closed.complete(Unit)
    }

    private inner class Listener : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val msg = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return
            when (msg.string("type")) {
                "auth_required" -> webSocket.send(
                    buildJsonObject {
                        put("type", "auth")
                        put("access_token", token)
                    }.toString()
                )

                "auth_ok" -> authenticated.complete(Unit)

                "auth_invalid" -> {
                    authenticated.completeExceptionally(
                        HaAuthException(msg.string("message") ?: "Invalid access token")
                    )
                    webSocket.close(1000, null)
                }

                "result" -> {
                    val id = msg.int("id") ?: return
                    val deferred = pending[id] ?: return
                    if (msg.bool("success") == true) {
                        deferred.complete(msg["result"] ?: JsonNull)
                    } else {
                        val error = msg.obj("error")?.string("message") ?: "Command failed"
                        deferred.completeExceptionally(HaCommandException(error))
                    }
                }

                "event" -> {
                    val id = msg.int("id") ?: return
                    val event = msg.obj("event") ?: return
                    subscriptions[id]?.invoke(event)
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            finish(IOException("Connection closed ${reason.ifBlank { "($code)" }}"))
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            finish(IOException("Connection closed"))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val error = if (response?.code == 401 || response?.code == 403) {
                HaAuthException("Unauthorized")
            } else {
                IOException(t.message ?: "Connection failed", t)
            }
            finish(error)
        }
    }
}
