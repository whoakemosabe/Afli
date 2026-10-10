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
import app.afli.t
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter


/**
 * The week ahead, one card per day. Each bar is an hour, coloured by bite score; red-tinted bars
 * are "Stay home". Tap a bar to see that hour. Days further out fade a little: forecasts get
 * less sure with time.
 */
@Composable
fun ForecastScreen(s: UiState, top: Dp, scroll: ScrollState, onOpenNow: (Long) -> Unit) {
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
                Text(t("No forecast yet", "Engin spá enn"), style = T.title)
                Text(t("Open the Now tab to load one.", "Opnaðu Núna til að sækja hana."), style = T.small)
            }
        }
        days.forEachIndexed { i, (date, hours) ->
            DayCard(date, hours, fade = i >= 3, today = i == 0, nowLimit = s.scores.getOrNull(s.nowIndex)?.t?.plus(47 * 3_600_000L), onOpenNow = onOpenNow)
        }
        if (days.size > 3) {
            Text(
                t("Days 4–7 are less certain, so treat them as a rough guide.", "Dagar 4–7 eru óvissari, svo taktu þeim sem grófri leiðbeiningu."),
                style = T.small.copy(color = C.faint),
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        BottomBarSpace()
    }
}

@Composable
private fun DayCard(date: LocalDate, hours: List<HourScore>, fade: Boolean, today: Boolean, nowLimit: Long?, onOpenNow: (Long) -> Unit) {
    var sel by remember(hours) { mutableStateOf<Int?>(null) }
    val view = LocalView.current
    val best = hours.maxByOrNull { if (it.safety == Safety.STAY_HOME) -1 else it.score }
    GlassCard(Modifier.fillMaxWidth().alpha(if (fade) 0.85f else 1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (today) t("Today", "Í dag") else longDay(date), style = T.heading)
                best?.let { Text(t("Best ", "Best ") + "${clock(it.t)} · ${it.score}", style = T.small) }
            }
            if (hours.any { it.safety == Safety.STAY_HOME }) Text(t("Stay home at times", "Vertu heima á köflum"), style = T.small.copy(color = C.bad))
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
        // A fixed-height detail area, so tapping a bar never pushes the days below around.
        AnimatedContent(sel, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "hour", modifier = Modifier.fillMaxWidth().height(118.dp)) { i ->
            val h = i?.let { hours.getOrNull(it) }
            if (h == null) {
                Text(t("Tap a bar to see that hour.", "Ýttu á súlu til að sjá þann tíma."), style = T.small.copy(color = C.faint), modifier = Modifier.padding(top = 8.dp))
            } else {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${clock(h.t)}  ", style = T.heading)
                        Text("${h.score} ${h.bite.label}", style = T.heading.copy(color = C.bite(h.bite)))
                        Spacer(Modifier.weight(1f))
                        Dot(C.safety(h.safety))
                        Text("  ${h.safety.label}", style = T.small)
                    }
                    h.best?.let { Text(t("Best bet: ", "Best að reyna: ") + "${it.name} (${it.other})", style = T.small, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                    Text(h.reasons.take(3).joinToString(" · ") { it.label }, style = T.small, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (nowLimit != null && h.t <= nowLimit) {
                        Text(
                            t("See this hour in Now  ›", "Sjá þennan tíma í Núna  ›"),
                            style = T.small.copy(color = C.brass),
                            modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) { onOpenNow(h.t) }.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
