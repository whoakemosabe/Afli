package app.afli.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.afli.data.Feeds
import app.afli.data.UiState
import app.afli.model.Reason
import app.afli.model.Water
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val hm = DateTimeFormatter.ofPattern("HH:mm")
private val dayHm = DateTimeFormatter.ofPattern("EEE HH:mm")

fun clock(t: Long): String = hm.format(Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()))
fun dayClock(t: Long): String = dayHm.format(Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()))

fun compass(deg: Double): String {
    if (deg.isNaN()) return "–"
    val names = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return names[(((deg % 360) + 360) % 360 / 45.0).roundToInt() % 8]
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NowScreen(
    s: UiState,
    top: androidx.compose.ui.unit.Dp,
    scroll: ScrollState,
    onSpot: (app.afli.model.Spot) -> Unit,
    onFixSpot: () -> Unit,
    onRetry: () -> Unit,
) {
    val now = s.now
    val spot = s.spot
    LaunchedEffect(now != null) {
        if (now != null) Tips.maybeTour(
            "now",
            listOf(
                Tips.Step("score", "Your bite score", "Big number = good time to fish. It mixes tide, light, wind, pressure and water temperature."),
                Tips.Step("safety", "Is it safe?", "This checks wind gusts and waves on their own. Stay home means stay home."),
                Tips.Step("why", "Why this score", "These chips say what's helping or hurting. Long-press anything in Afli to have it explained."),
            ),
        )
    }
    Column(
        Modifier
            .verticalScroll(scroll)
            .padding(top = top)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Spot chips: where Afli thinks he is, then saved spots.
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val list = buildList {
                if (spot != null && spots(s).none { it.id == spot.id }) add(spot)
                addAll(spots(s))
            }
            list.forEach { sp ->
                GlassChip(
                    text = sp.name,
                    selected = sp.id == spot?.id,
                    dot = if (sp.water == Water.LAKE) C.sea else null,
                    explain = if (sp.water == Water.LAKE) "${sp.name} is a lake. Lake fishing needs a permit." else "Tap to see the forecast for ${sp.name}.",
                    onClick = { onSpot(sp) },
                )
            }
        }

        if (s.error != null && now == null) {
            GlassCard(Modifier.fillMaxWidth(), onClick = onRetry) {
                Text("No forecast yet", style = T.title)
                Spacer(Modifier.height(6.dp))
                Text(s.error, style = T.body)
                Spacer(Modifier.height(10.dp))
                Text("Tap to try again", style = T.heading.copy(color = C.brass))
            }
        }

        if (now == null && s.error == null) {
            // A slow breathing pulse so it's clearly working, not stuck.
            val pulse by rememberInfiniteTransition(label = "loading").animateFloat(
                0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
            )
            GlassCard(Modifier.fillMaxWidth()) {
                Text(if (s.gpsAsked) "Reading the sea…" else "Finding where you are…", style = T.title, modifier = Modifier.graphicsLayer { alpha = pulse })
                Spacer(Modifier.height(6.dp))
                Text("Pulling the forecast, tides and sea temperature.", style = T.small)
            }
        }

        if (now != null && spot != null) {
            // The big answer.
            val shown by animateIntAsState(now.score, tween(900), label = "score")
            GlassCard(
                Modifier.fillMaxWidth().coachTarget("score"),
                explain = "Bite score from 0 to 100. Over 60 is great, 35–60 is OK, under 35 is slow. It's a guide, not a promise; logging trips makes it smarter.",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (s.atSpot) "You're at ${spot.name}" else spot.name, style = T.small)
                        Text("$shown", style = T.hero.copy(color = C.score(now.score)))
                        Text(now.bite.label, style = T.title.copy(color = C.bite(now.bite)))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        now.best?.let { b ->
                            Text("Best bet", style = T.label)
                            Text(b.en, style = T.heading)
                            Text(b.icelandic, style = T.small)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SafetyPill(now.safety, now.safetyWhy, Modifier.coachTarget("safety"))
            }

            // Why.
            if (now.reasons.isNotEmpty()) {
                Column(Modifier.coachTarget("why")) {
                    SectionLabel("Why")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        now.reasons.take(6).forEach { r -> ReasonChip(r) }
                    }
                }
            }

            // Next good window.
            s.window?.let { w ->
                val nowMs = System.currentTimeMillis()
                GlassCard(
                    Modifier.fillMaxWidth(),
                    explain = "The best stretch in the next two days that isn't 'Stay home'.",
                ) {
                    SectionLabel("Next best window")
                    val starts = if (w.start <= nowMs) "Now" else dayClock(w.start)
                    Text("$starts – ${clock(w.end)}", style = T.title)
                    Text("Peak score ${w.peak}", style = T.small)
                }
            }

            // Tide.
            val hours = s.forecast?.hours.orEmpty()
            if (spot.water == Water.SEA && hours.any { !it.seaLevel.isNaN() }) {
                GlassCard(
                    Modifier.fillMaxWidth(),
                    explain = "Tide height for the next day from Open-Meteo's sea model. Times run along the bottom; the brass line is now. High and low times are worked out between hourly points, so treat them as within about 15 minutes, and it's approximate near the coast.",
                ) {
                    SectionLabel("Tide")
                    val flow = now.tideFlow
                    Text(
                        when {
                            flow == null -> "Tide data is patchy here."
                            flow > 0.6 -> "Moving fast"
                            flow > 0.3 -> "Moving"
                            else -> "Turning (slack)"
                        },
                        style = T.heading,
                    )
                    Spacer(Modifier.height(8.dp))
                    TideCurve(hours, nowMs(), Modifier.fillMaxWidth().height(160.dp), key = spot.id)
                    Text("A model estimate. Not for navigation.", style = T.small.copy(color = C.faint))
                }
            }

            // Wind and the rest.
            val h = hours.getOrNull(s.nowIndex)
            if (h != null) {
                GlassCard(Modifier.fillMaxWidth()) {
                    SectionLabel("Conditions")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WindDial(
                            h.windDir, spot.facing,
                            Modifier.size(140.dp).explains("Wind compass. The arrow shows where the wind is blowing to; the brass tick on the rim is which way the water is from your spot."),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Stat("Wind", "${fmt(h.wind)} m/s ${compass(h.windDir)}", "Average wind speed and where it's coming from.")
                            Stat("Gusts", "${fmt(h.gust)} m/s", "The strongest short bursts of wind. Over 15 m/s is hard work on the shore.")
                            Stat("Air", "${fmt(h.airTemp)} °C", "Air temperature.")
                            if (spot.water == Water.SEA) {
                                Stat("Sea", "${fmt(h.sst)} °C", "Sea surface temperature (model). Mackerel want 8 °C or more.")
                                Stat("Waves", "${fmt(h.wave, 1)} m", "Wave height offshore. Harbours feel much less.")
                            }
                            Stat("Pressure", "${fmt(h.pressure)} hPa", "Air pressure. A slow fall often gets fish feeding.")
                        }
                    }
                    s.live?.let { l ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Live: ${l.station} (${l.distanceKm.roundToInt()} km) ${fmt(l.wind)} m/s, gusts ${fmt(l.gust)}. The next 12 hours are corrected to match.",
                            style = T.small,
                        )
                    }
                }
            }

            // This spot.
            GlassCard(Modifier.fillMaxWidth()) {
                SectionLabel("This spot")
                Text(
                    buildString {
                        append(if (spot.water == Water.LAKE) "Lake" else if (spot.sheltered) "Sea, sheltered harbour" else "Sea, open coast")
                        append(" · ")
                        append(if (spot.facing != null) "water to the ${compass(spot.facing)}" else "water direction not set")
                    },
                    style = T.body,
                )
                Spacer(Modifier.height(10.dp))
                GlassButton("Fix spot", accent = C.brass, explain = "Point your phone at the water to set its direction, and say if it's a harbour or a lake.", onClick = onFixSpot)
            }

            Text(
                "Forecast: ${s.forecast?.weatherModel ?: "Open-Meteo"} · Sea: Open-Meteo Marine" +
                    if (Feeds.inIceland(spot.lat, spot.lon)) " · Live: Veðurstofa Íslands" else "",
                style = T.small.copy(color = C.faint),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }
        BottomBarSpace()
    }
}

private fun spots(s: UiState) = s.spots

private fun nowMs() = System.currentTimeMillis()

fun fmt(v: Double, digits: Int = 0): String =
    if (v.isNaN()) "–" else if (digits == 0) v.roundToInt().toString() else "%.${digits}f".format(v)

@Composable
private fun Stat(label: String, value: String, explain: String) {
    Row(Modifier.explains(explain), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = T.small, modifier = Modifier.width(72.dp))
        Text(value, style = T.number)
    }
}

@Composable
fun SafetyPill(safety: app.afli.model.Safety, why: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val col = C.safety(safety)
    Column(modifier.animateContentSize()) {
        GlassChip(
            text = safety.label,
            dot = col,
            selected = false,
            explain = "Safety looks only at gusts, waves and cold. It never mixes into the bite score.",
            onClick = { open = !open },
        )
        AnimatedVisibility(open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Text(why, style = T.small, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
        }
    }
}

@Composable
private fun ReasonChip(r: Reason) {
    GlassChip(
        text = r.label,
        dot = if (r.good) C.good else C.bad,
        explain = r.detail,
        onClick = { Tips.explain(r.detail) },
    )
}

@Composable
fun Dot(color: androidx.compose.ui.graphics.Color, size: androidx.compose.ui.unit.Dp = 8.dp) =
    Box(Modifier.size(size).background(color, CircleShape))

val cardPad = PaddingValues(18.dp)
val roundCard = RoundedCornerShape(26.dp)
