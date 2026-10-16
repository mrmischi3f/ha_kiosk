package com.hakiosk.ui.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.hakiosk.data.double
import com.hakiosk.data.ha.HaState
import com.hakiosk.data.string
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/** Playback position in seconds, extrapolated from media_position(_updated_at) while playing. */
@Composable
fun rememberPlaybackPosition(player: HaState?): State<Double> = produceState(0.0, player) {
    while (true) {
        value = currentPosition(player)
        if (player?.state != "playing") break
        delay(1_000)
    }
}

fun currentPosition(player: HaState?): Double {
    val a = player?.attributes ?: return 0.0
    val position = a.double("media_position") ?: return 0.0
    val duration = a.double("media_duration")
    if (player.state != "playing") return position
    val updatedAt = a.string("media_position_updated_at")
        ?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
        ?: return position
    val elapsed = Duration.between(updatedAt, Instant.now()).toMillis() / 1000.0
    val pos = position + elapsed.coerceAtLeast(0.0)
    return if (duration != null) pos.coerceAtMost(duration) else pos
}

fun formatDuration(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

val HaState.isPlayingOrPaused: Boolean get() = state == "playing" || state == "paused"
