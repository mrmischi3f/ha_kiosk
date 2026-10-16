# HA Kiosk

A native Android (Jetpack Compose) kiosk app that turns a 10.1" digital picture frame into a
Home Assistant wall hub. Built for landscape tablets around 1280×800.

## Features

| Tab | What it shows |
| --- | --- |
| **Dashboard** | Big clock and date, weather and 5-day forecast (from your HA `weather.*` entity), your **most-used lights** (ranked from HA history), pinned entities, a month calendar with an agenda (HA calendars), live scores, and a scrolling stock ticker. |
| **Home** | Every Home Assistant entity, grouped by area or by type, with search and filter chips. Tap to toggle; long-press for full controls (brightness, colour temp, colour, climate setpoint and HVAC mode, cover position, media transport and volume, locks, vacuums, scenes, scripts…). Long-press → ★ pins an entity to the dashboard. |
| **Music** | Spotify via Home Assistant: album art, title/artist, progress, play/pause/skip, shuffle/repeat, volume, Spotify Connect device switching, and a library browser (playlists, albums, artists, podcasts) where tapping an item plays it. The dashboard shows a mini player while something is playing. |
| **Stocks** | Search any ticker (stocks, ETFs, indices, crypto, FX), keep a watchlist, add optional share count and average cost for portfolio value and gain/loss, sparklines, and a detail chart (1D/5D/1M/6M/YTD/1Y/5Y). |
| **Scores** | Live scores from ESPN for the leagues you choose (Premier League, LaLiga, Serie A, Bundesliga, Ligue 1, UCL, UEL, MLS, Liga Argentina, Brasileirão, Liga MX, Libertadores, World Cup, NBA, WNBA, NCAA, NFL, MLB, NHL). Yesterday/today/tomorrow, live-only filter, and auto-refresh every 30 s while games are live. |
| **Settings** | HA URL and token, dashboard options, calendars and weather entity, stock refresh rate, leagues, and kiosk options. |

### Kiosk behaviour
- Full-screen immersive mode, screen kept on, landscape.
- Can be set as the device **Home app** (launcher). It then starts on boot and the frame's own launcher stays hidden.
- Back always returns to the dashboard. After N idle seconds the app jumps back to the dashboard.
- **Night mode:** between the hours you choose, after 1 minute idle the screen dims to a black, low-brightness clock. Tap to wake.
- Optional **screen pinning** (lock task) so nobody can leave the app.

## Build & install

Requirements: Android Studio (Ladybug or newer) with JDK 17+.

1. Open the `ha-kiosk` folder in Android Studio and let Gradle sync. AGP and library versions are in `gradle/libs.versions.toml`, so bump them there if Studio suggests upgrades.
2. Enable developer options and USB debugging on the frame (Settings → About → tap *Build number* 7×).
3. Run the `app` configuration, or build an APK with `./gradlew assembleDebug` (it ends up in `app/build/outputs/apk/debug/app-debug.apk`) and install it with `adb install -r app-debug.apk`.

`minSdk` is 26 (Android 8.0), so it should run on pretty much any cheap frame.

## First-time setup on the frame

1. Open **Settings** (gear at the bottom of the side rail).
2. **Home Assistant URL:** e.g. `http://192.168.1.10:8123` or `http://homeassistant.local:8123` (plain HTTP on your LAN works).
3. **Token:** in Home Assistant go to your profile → *Security* → *Long-lived access tokens* → *Create token*. Paste it and tap **Save & connect**. The dot above the gear turns green once connected.
4. Optionally pick calendars, the weather entity and leagues.
5. Tap **Set as Home app** and choose *HA Kiosk* → *Always*.

Tip: create a dedicated non-admin HA user for the frame and make the token from that user.

## Spotify setup

The app controls Spotify through Home Assistant, so the frame needs no Spotify login or developer keys.

1. In Home Assistant: **Settings → Devices & services → Add integration → Spotify** and sign in. The integration walks you through creating Spotify application credentials.
2. HA creates `media_player.spotify_<your_user>`. The Music tab picks it up automatically. To use a different player (Sonos, Cast…), pick it under **Settings → Music**.
3. Playback control requires **Spotify Premium**. Spotify only lists Connect devices that are online, so open Spotify on a speaker or phone once if the device list is empty.

## How "most used lights" works

The dashboard calls HA's history API (`/api/history/period`) for every `light.*` entity over the last N days (7 by default, configurable), counts on↔off changes per light, and shows the top ones. It refreshes hourly. Until there's history, lights that are currently on are listed first.

## Data sources

| Data | Source | Notes |
| --- | --- | --- |
| Home Assistant states and controls | HA WebSocket API (`/api/websocket`) | Live updates, no polling |
| History and calendars | HA REST API | `/api/history/period`, `/api/calendars` |
| Music | HA `media_player` entity + `media_player/browse_media` | Spotify via HA's Spotify integration |
| Weather forecast | `weather.get_forecasts` service | HA 2023.9+ |
| Stocks | Yahoo Finance public endpoints (`query1/query2.finance.yahoo.com`) | No API key. Unofficial: if Yahoo changes them, only `data/stocks/StocksRepository.kt` needs updating |
| Sports | ESPN public scoreboard API (`site.api.espn.com`) | No API key. Unofficial: only `data/sports/SportsRepository.kt` would need updating |

## Project layout

```
app/src/main/java/com/hakiosk/
├── KioskApp.kt, AppContainer.kt      # app + manual DI container
├── MainActivity.kt, BootReceiver.kt  # immersive/kiosk activity, start on boot
├── data/
│   ├── ha/        # WebSocket client, repository (states, services, history, calendars, forecast)
│   ├── stocks/    # Yahoo search/quotes/charts
│   ├── sports/    # ESPN scoreboards + league catalogue
│   └── settings/  # DataStore-backed settings (incl. watchlist, pins)
└── ui/
    ├── KioskRoot.kt   # side rail navigation, idle → dashboard, night mode
    ├── dashboard/ home/ music/ stocks/ sports/ settings/
    ├── components/    # entity tiles + control dialog, charts, panels
    └── theme/
```

## Notes and limits

- The app is designed for landscape. Android 16+ ignores orientation locks on large screens, so on such devices the layout may also appear in portrait.
- Starting on boot is reliable only when the app is the default Home app (Android 10+ blocks background activity starts otherwise).
- Screen pinning without device-owner provisioning shows Android's "App is pinned" confirmation and can be exited with Back + Overview. For a fully locked kiosk, provision the app as device owner via `adb shell dpm set-device-owner` (requires adding a DeviceAdminReceiver).
- Yahoo quotes can be delayed up to 15 minutes depending on the exchange.
