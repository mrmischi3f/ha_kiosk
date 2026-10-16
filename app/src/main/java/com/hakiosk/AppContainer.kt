package com.hakiosk

import android.content.Context
import com.hakiosk.data.ha.HomeAssistantRepository
import com.hakiosk.data.settings.AppSettings
import com.hakiosk.data.settings.SettingsRepository
import com.hakiosk.data.sports.SportsRepository
import com.hakiosk.data.stocks.StocksRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Simple manual dependency container shared by the whole app. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val settingsRepository = SettingsRepository(context, json)

    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(appScope, SharingStarted.Eagerly, AppSettings())

    val homeAssistant = HomeAssistantRepository(httpClient, json, appScope)
    val stocks = StocksRepository(httpClient, appScope)
    val sports = SportsRepository(httpClient, appScope)

    init {
        appScope.launch {
            settings
                .filter { it.loaded }
                .map { it.haUrl to it.haToken }
                .distinctUntilChanged()
                .collect { (url, token) -> homeAssistant.configure(url, token) }
        }
        stocks.start(settings)
        sports.start(settings)
    }
}
