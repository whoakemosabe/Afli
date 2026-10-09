package app.afli.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.afli.data.UiState
import app.afli.model.HourScore
import app.afli.model.Safety
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dayName = DateTimeFormatter.ofPattern("EEEE d MMM")

/**
 * The week ahead, one card per day. Each bar is an hour, coloured by bite score; red-tinted bars
 * are "Stay home". Tap a bar to see that hour. Days further out fade a little: forecasts get
 * less sure with time.
 */
@Composable
fun ForecastScreen(s: UiState, top: Dp, scroll: ScrollState) {
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val days = remember(s.scores) {
        s.scores.filter { it.t >= now - 3_600_000L }
            .groupBy { Instant.ofEpochMilli(it.t).atZone(zone).toLocalDate() }
            .toSortedMap()
            .entries.take(7)
    }
    Column(
        Modifier.verticalScroll(scroll).padding(top = top).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (days.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("No forecast yet", style = T.title)
                Text("Open the Now tab to load one.", style = T.small)
            }
        }
        days.forEachIndexed { i, (date, hours) ->
            DayCard(date, hours, fade = i >= 3, today = i == 0)
        }
        if (days.size > 3) {
            Text(
                "Days 4–7 are less certain, so treat them as a rough guide.",
                style = T.small.copy(color = C.faint),
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        BottomBarSpace()
    }
}

@Composable
private fun DayCard(date: LocalDate, hours: List<HourScore>, fade: Boolean, today: Boolean) {
    var sel by remember(hours) { mutableStateOf<Int?>(null) }
    val view = LocalView.current
    val best = hours.maxByOrNull { if (it.safety == Safety.STAY_HOME) -1 else it.score }
    GlassCard(Modifier.fillMaxWidth().alpha(if (fade) 0.85f else 1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (today) "Today" else dayName.format(date), style = T.heading)
                best?.let { Text("Best ${clock(it.t)} · ${it.score}", style = T.small) }
            }
            if (hours.any { it.safety == Safety.STAY_HOME }) Text("Stay home at times", style = T.small.copy(color = C.bad))
        }
        Spacer(Modifier.height(12.dp))
        ScoreBars(
            hours.map { it.score },
            hours.map { it.safety == Safety.STAY_HOME },
            sel,
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .pointerInput(hours) {
                    detectTapGestures { p ->
                        val i = (p.x / size.width * hours.size).toInt().coerceIn(0, hours.lastIndex)
                        Haptics.segment(view)
                        sel = if (sel == i) null else i
                    }
                },
        )
        HourAxis(hours.map { it.t }, Modifier.fillMaxWidth().padding(top = 4.dp).height(14.dp))
        AnimatedContent(sel, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "hour") { i ->
            val h = i?.let { hours.getOrNull(it) }
            if (h == null) {
                Text("Tap a bar to see that hour.", style = T.small.copy(color = C.faint), modifier = Modifier.padding(top = 8.dp))
            } else {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${clock(h.t)}  ", style = T.heading)
                        Text("${h.score} ${h.bite.label}", style = T.heading.copy(color = C.bite(h.bite)))
                        Spacer(Modifier.weight(1f))
                        Dot(C.safety(h.safety))
                        Text("  ${h.safety.label}", style = T.small)
                    }
                    h.best?.let { Text("Best bet: ${it.en} (${it.icelandic})", style = T.small) }
                    Text(h.reasons.take(3).joinToString(" · ") { it.label }, style = T.small)
                }
            }
        }
    }
}
