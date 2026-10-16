package com.hakiosk.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Browser-like UA; Yahoo in particular rejects requests without one. */
const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 12; Tablet) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/128.0.0.0 Safari/537.36"

suspend fun OkHttpClient.getJson(url: String): JsonElement = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", BROWSER_USER_AGENT)
        .header("Accept", "application/json")
        .build()
    newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
        Json.parseToJsonElement(response.body?.string().orEmpty())
    }
}
