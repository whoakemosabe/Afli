package app.afli.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.afli.Prefs
import app.afli.data.UiState
import app.afli.model.Astro
import app.afli.model.Hour
import app.afli.model.HourScore
import app.afli.model.Model
import app.afli.model.Safety
import app.afli.model.Spot
import app.afli.model.TideTurn
import app.afli.model.Water
import app.afli.model.Window
import app.afli.t
import app.afli.tempNum
import app.afli.tempUnit
import app.afli.windNum
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Everything one day card needs, worked out once. */
private class Day(
    val date: LocalDate,
    val index: Int,
    val scores: List<HourScore>,
    val hours: List<Hour>,
    val best: HourScore?,
    val window: Window?,
    val turns: List<TideTurn>,
    val rise: Long?,
    val set: Long?,
    val stayHome: Boolean,
    val worstSafety: Safety,
)

/**
 * The week ahead. At the top, the best day and a strip of seven days to jump between. Each day
 * card gives the best window, the fish to try and what with, the tides, the sun, and the range
 * of wind, air and waves, then hour bars you can drag along: bars coloured by bite score, dark
 * hours shaded, dawn and dusk in brass, high and low tide marked, the wind as a thin line, and
 * Stay home hours hatched red. Further days carry a "less sure" badge.
 */
@Composable
fun ForecastScreen(s: UiState, top: Dp, scroll: ScrollState, onOpenNow: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val spot = s.spot
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val view = LocalView.current
    val days = remember(s.scores, s.hours, spot?.id) {
        if (spot == null) emptyList() else buildDays(s, spot, now, zone)
    }
    val cardY = remember { mutableStateMapOf<Int, Float>() }
    fun jumpTo(d: Day) {
        val y = cardY[d.index] ?: return
        val target = (y - with(density) { (top + 8.dp).toPx() }).toInt().coerceAtLeast(0)
        scope.launch { scroll.animateScrollTo(target, spring(stiffness = Spring.StiffnessMediumLow)) }
    }

    Column(
        Modifier.verticalScroll(scroll).padding(top = top).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (days.isEmpty() || spot == null) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t("No forecast yet", "Engin spá enn"), style = T.title)
                Text(t("Open the Now tab to load one.", "Opnaðu Núna til að sækja hana."), style = T.small)
            }
        } else {
            BestDayCard(days) { jumpTo(it) }
            WeekStrip(days) {
                Haptics.segment(view)
                jumpTo(it)
            }
            val nowLimit = s.scores.getOrNull(s.nowIndex)?.t?.plus(47 * 3_600_000L)
            days.forEach { d ->
                DayCard(d, spot, nowLimit, onOpenNow, Modifier.onGloballyPositioned { cardY[d.index] = it.positionInParent().y })
            }
        }
        BottomBarSpace()
    }
}

private fun buildDays(s: UiState, spot: Spot, now: Long, zone: ZoneId): List<Day> {
    val byT = s.hours.associateBy { it.t }
    val turns = if (spot.water == Water.SEA) Model.tideTurns(s.hours) else emptyList()
    return s.scores.filter { it.t >= now - 3_600_000L }
        .groupBy { Instant.ofEpochMilli(it.t).atZone(zone).toLocalDate() }
        .toSortedMap().entries.take(7)
        .mapIndexed { i, (date, sc) ->
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = start + 86_400_000L
            val sun = Astro.sunDay(start + 3_600_000L, spot.lat, spot.lon, zone)
            val ok = sc.filter { it.safety != Safety.STAY_HOME }
            Day(
                date = date,
                index = i,
                scores = sc,
                hours = sc.mapNotNull { byT[it.t] },
                best = ok.maxByOrNull { it.score },
                window = Model.nextWindow(sc, sc.first().t, 24),
                turns = turns.filter { it.t in start until end },
                rise = sun.rise,
                set = sun.set,
                stayHome = sc.any { it.safety == Safety.STAY_HOME },
                worstSafety = sc.maxOf { it.safety },
            )
        }
}

// ---------------------------------------------------------------- top of the page

@Composable
private fun BestDayCard(days: List<Day>, onOpen: (Day) -> Unit) {
    val best = days.filter { it.best != null }.maxByOrNull { it.best!!.score } ?: return
    val b = best.best!!
    GlassCard(
        Modifier.fillMaxWidth(),
        explain = t("The day with the highest bite score this week, leaving out Stay home hours.", "Dagurinn með hæstu tökulíkurnar í vikunni. Tímar merktir Vertu heima eru ekki taldir með."),
        onClick = { onOpen(best) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreBadge(b.score, 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                FitText(t("BEST DAY THIS WEEK", "BESTI DAGUR VIKUNNAR"), T.label, min = 10.sp)
                Text(
                    "${if (best.index == 0) t("Today", "Í dag") else longDay(best.date)} · ${clock(b.t)}",
                    style = T.heading,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    b.best?.let { "${it.name} · " + it.baitShort.toString() } ?: "–",
                    style = T.small,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("›", style = T.title.copy(color = C.brass))
        }
    }
}

/** Seven equal day chips: day name, best score in a small ring, a safety dot. Tap to jump. */
@Composable
private fun WeekStrip(days: List<Day>, onPick: (Day) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { d ->
            val press = rememberPress()
            val backdrop = LocalBackdrop.current
            val shape = RoundedCornerShape(18.dp)
            val m = if (backdrop != null) Modifier.weight(1f).glassControl(backdrop, shape, press = press.amount, layer = press.squishLayer(0.08f))
            else Modifier.weight(1f).squish(press, 0.08f)
            Column(
                m.pressable(press, t("Jump to ", "Skoða dag: ") + longDay(d.date), scaleBy = 0f, haptic = false) { onPick(d) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (d.index == 0) t("Today", "Í dag") else shortDay(d.scores.first().t),
                    style = T.small.copy(fontSize = 11.sp, lineHeight = 13.sp, color = if (d.index == 0) C.brass else C.mist),
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.height(4.dp))
                ScoreBadge(d.best?.score ?: 0, 30.dp, small = true)
                Spacer(Modifier.height(4.dp))
                Dot(C.safety(d.worstSafety), 6.dp)
            }
        }
    }
}

/** A score inside a ring that fills to it, coloured like the score. */
@Composable
private fun ScoreBadge(score: Int, size: Dp, small: Boolean = false) {
    val col = C.score(score)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = (if (small) 2.5f else 4f) * density
            val tl = Offset(w / 2, w / 2)
            val sz = Size(this.size.width - w, this.size.height - w)
            drawArc(C.line, 135f, 270f, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
            if (score > 0) drawArc(col, 135f, 270f * score / 100f, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text("$score", style = (if (small) T.number.copy(fontSize = 11.sp) else T.heading.copy(fontSize = 18.sp)).copy(color = col), maxLines = 1, softWrap = false)
    }
}

// ---------------------------------------------------------------- one day

@Composable
private fun DayCard(d: Day, spot: Spot, nowLimit: Long?, onOpenNow: (Long) -> Unit, modifier: Modifier) {
    var sel by remember(d.scores.firstOrNull()?.t) { mutableStateOf<Int?>(null) }
    val sea = spot.water == Water.SEA
    val sure = when {
        d.index <= 1 -> null
        d.index <= 3 -> t("Less sure", "Óvissara")
        else -> t("Rough guide", "Gróft mat")
    }
    GlassCard(modifier.fillMaxWidth()) {
        // Day, how sure, safety.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FitText(if (d.index == 0) t("Today", "Í dag") else longDay(d.date), T.heading, min = 10.sp)
                Text(sure ?: t("Good confidence", "Nokkuð áreiðanlegt"), style = T.small.copy(fontSize = 11.sp, color = C.faint), maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            SafetyTag(d.worstSafety, d.stayHome)
        }
        Spacer(Modifier.height(12.dp))

        // The day in facts: two fixed columns.
        val b = d.best
        val facts = buildList {
            add(t("Best", "Best") to (d.window?.let { "${clock(it.start)}–${clock(it.end)} · ${it.peak}" } ?: "–"))
            add(t("Fish", "Fiskur") to (b?.best?.name ?: "–"))
            if (sea) add(t("Tides", "Sjávarföll") to tideSummary(d.turns))
            add(t("Sun", "Sól") to sunSummary(d))
            add(t("Wind", "Vindur") to rangeOf(d.hours.map { it.wind }) { windNum(it) } + " " + Prefs.wind.label + gustNote(d.hours))
            add(t("Air", "Lofthiti") to rangeOf(d.hours.map { it.airTemp }) { tempNum(it) } + " " + tempUnit)
            if (sea) add(t("Waves", "Öldur") to (d.hours.map { if (spot.sheltered) it.wave * 0.3 else it.wave }.filter { !it.isNaN() }.maxOrNull()?.let { t("up to ", "allt að ") + fmt(it, 1) + " m" } ?: "–"))
        }
        facts.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                pair.forEach { (k, v) -> Fact(k, v, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row {
            Text(t("TRY", "PRÓFAÐU"), style = T.label.copy(fontSize = 11.sp), modifier = Modifier.width(72.dp).padding(top = 1.dp), maxLines = 1, softWrap = false)
            val view = LocalView.current
            Text(
                b?.best?.let { "${it.baitShort}  ›" } ?: "–",
                style = T.small.copy(color = C.brass),
                modifier = Modifier.weight(1f).clickable(remember { MutableInteractionSource() }, null) {
                    b?.best?.let { f -> Haptics.tap(view); Tips.explain("${f.name}: ${f.bait}") }
                },
            )
        }
        Spacer(Modifier.height(12.dp))

        // Hour bars you can drag along.
        DayBars(d, sel, Modifier.fillMaxWidth().height(86.dp)) { sel = it }
        HourAxis(d.scores.map { it.t }, Modifier.fillMaxWidth().padding(top = 4.dp).height(14.dp))
        Legend(sea)

        // The chosen hour, in a fixed-height area so nothing below moves.
        AnimatedContent(sel, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, label = "hour", modifier = Modifier.fillMaxWidth().height(150.dp)) { i ->
            val h = i?.let { d.scores.getOrNull(it) }
            val hr = i?.let { d.hours.getOrNull(it) }
            if (h == null) {
                Text(t("Drag or tap along the bars to see each hour.", "Dragðu fingurinn eftir súlunum eða ýttu á þær til að sjá hvern klukkutíma."), style = T.small.copy(color = C.faint), modifier = Modifier.padding(top = 10.dp))
            } else {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${clock(h.t)}  ", style = T.heading)
                        Text("${h.score} ${h.bite.label}", style = T.heading.copy(color = C.bite(h.bite)), maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        Dot(C.safety(h.safety))
                        Text("  ${h.safety.label}", style = T.small, maxLines = 1, softWrap = false)
                    }
                    // Three facts side by side, each with room for its own value.
                    Row(Modifier.fillMaxWidth()) {
                        Fact(t("Fish", "Fiskur"), h.best?.name ?: "–", Modifier.weight(1.2f))
                        Fact(t("Wind", "Vindur"), hr?.let { "${windNum(it.wind)} ${Prefs.wind.label} ${compass(it.windDir)}" } ?: "–", Modifier.weight(1f))
                        Fact(t("Air", "Lofthiti"), hr?.let { "${tempNum(it.airTemp)} $tempUnit" } ?: "–", Modifier.weight(0.8f))
                    }
                    Text(h.reasons.take(3).joinToString(" · ") { it.label }.ifEmpty { "–" }, style = T.small.copy(color = C.faint), maxLines = 2)
                    if (nowLimit != null && h.t <= nowLimit) {
                        Text(
                            t("See this hour in Now  ›", "Sjá þennan tíma í Núna  ›"),
                            style = T.small.copy(color = C.brass),
                            modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) { onOpenNow(h.t) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(end = 8.dp)) {
        Text(label.uppercase(app.afli.L.locale), style = T.label.copy(fontSize = 10.sp, lineHeight = 12.sp), maxLines = 1)
        Text(value, style = T.small.copy(color = C.foam), maxLines = 2)
    }
}

@Composable
private fun SafetyTag(worst: Safety, any: Boolean) {
    val col = C.safety(worst)
    Row(
        Modifier.border(1.dp, col.copy(alpha = 0.6f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(col, 7.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            when (worst) {
                Safety.SAFE -> t("Safe all day", "Öruggt allan daginn")
                Safety.CAREFUL -> t("Careful at times", "Varúð á köflum")
                Safety.STAY_HOME -> if (any) t("Stay home at times", "Vertu heima hluta dags") else Safety.STAY_HOME.label
            },
            style = T.small.copy(fontSize = 12.sp, color = col),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun Legend(sea: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(C.brass.copy(alpha = 0.5f), t("Dawn/dusk", "Ljósaskipti"))
        LegendItem(Color(0x99000814), t("Dark", "Myrkur"))
        LegendItem(C.foam.copy(alpha = 0.8f), t("Wind", "Vindur"), line = true)
        if (sea) Text("▲▼ " + t("tide", "flóð/fjara"), style = T.small.copy(fontSize = 10.sp, color = C.faint), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun LegendItem(color: Color, label: String, line: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 12.dp, height = 8.dp)) {
            if (line) drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2 * density, cap = StrokeCap.Round)
            else drawRoundRect(color, cornerRadius = CornerRadius(2 * density))
        }
        Spacer(Modifier.width(4.dp))
        Text(label, style = T.small.copy(fontSize = 10.sp, color = C.faint), maxLines = 1, softWrap = false)
    }
}

private fun tideSummary(turns: List<TideTurn>): String =
    if (turns.isEmpty()) "–" else turns.joinToString(" ") { (if (it.high) "▲" else "▼") + clock((it.t + 300_000L) / 600_000L * 600_000L) }

private fun sunSummary(d: Day): String =
    listOfNotNull(d.rise?.let { "↑" + clock(it) }, d.set?.let { "↓" + clock(it) }).joinToString(" ").ifEmpty { "–" }

private fun rangeOf(v: List<Double>, f: (Double) -> String): String {
    val ok = v.filter { !it.isNaN() }
    if (ok.isEmpty()) return "–"
    val a = f(ok.min())
    val b = f(ok.max())
    return if (a == b) a else "$a–$b"
}

private fun gustNote(hours: List<Hour>): String {
    val g = hours.map { it.gust }.filter { !it.isNaN() }.maxOrNull() ?: return ""
    return t(", gusts ", ", hviður ") + windNum(g)
}

/**
 * The day's hour bars. Behind them, dark hours are shaded and the low-light hours tinted brass;
 * each bar is coloured by bite score, Stay home hours are hatched red; a thin line traces the
 * wind (full height = 15 m/s), and small triangles mark high and low tide. Drag or tap to pick
 * an hour; it ticks as it moves.
 */
@Composable
private fun DayBars(d: Day, selected: Int?, modifier: Modifier, onSelect: (Int?) -> Unit) {
    val view = LocalView.current
    val grow = remember(d.scores) { Animatable(0f) }
    LaunchedEffect(d.scores) { grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    val n = d.scores.size
    Canvas(
        modifier
            .pointerInput(d.scores, selected) {
                detectTapGestures { p ->
                    val i = (p.x / size.width * n).toInt().coerceIn(0, n - 1)
                    Haptics.segment(view)
                    onSelect(if (selected == i) null else i)
                }
            }
            .pointerInput(d.scores) {
                var last = -1
                detectHorizontalDragGestures(
                    onDragStart = { p ->
                        last = (p.x / size.width * n).toInt().coerceIn(0, n - 1)
                        onSelect(last)
                        Haptics.scrub(view)
                    },
                ) { change, _ ->
                    val i = (change.position.x / size.width * n).toInt().coerceIn(0, n - 1)
                    if (i != last) {
                        last = i
                        onSelect(i)
                        Haptics.scrub(view)
                    }
                }
            },
    ) {
        if (n == 0) return@Canvas
        val w = size.width / n
        val top = 10.dp.toPx()
        val h = size.height - top
        // Light behind the bars.
        d.scores.forEachIndexed { i, s ->
            val e = s.sunElevation
            val c = when {
                e < -6 -> Color(0x66000814)
                e < 8 -> C.brass.copy(alpha = 0.14f)
                else -> null
            }
            if (c != null) drawRect(c, topLeft = Offset(i * w, top), size = Size(w + 0.5f, h))
        }
        clipRect(right = size.width * grow.value) {
            d.scores.forEachIndexed { i, s ->
                val bh = h * (0.06f + 0.94f * s.score / 100f)
                val x = i * w + w * 0.18f
                val bw = w * 0.64f
                val y = size.height - bh
                val dim = selected != null && selected != i
                if (s.safety == Safety.STAY_HOME) {
                    drawRoundRect(C.bad.copy(alpha = if (dim) 0.18f else 0.3f), topLeft = Offset(x, y), size = Size(bw, bh), cornerRadius = CornerRadius(bw / 3))
                    clipRect(x, y, x + bw, size.height) {
                        var k = -bh
                        while (k < bw) {
                            drawLine(C.bad.copy(alpha = if (dim) 0.4f else 0.8f), Offset(x + k, size.height), Offset(x + k + bh, y), 1.2.dp.toPx())
                            k += 4.dp.toPx()
                        }
                    }
                } else {
                    drawRoundRect(C.score(s.score).copy(alpha = if (dim) 0.4f else 0.95f), topLeft = Offset(x, y), size = Size(bw, bh), cornerRadius = CornerRadius(bw / 3))
                }
            }
            // Wind line.
            val path = Path()
            var started = false
            d.hours.forEachIndexed { i, hr ->
                if (hr.wind.isNaN()) return@forEachIndexed
                val px = (i + 0.5f) * w
                val py = top + h - (hr.wind / 15.0).coerceIn(0.0, 1.0).toFloat() * h
                if (!started) {
                    path.moveTo(px, py)
                    started = true
                } else path.lineTo(px, py)
            }
            drawPath(path, C.foam.copy(alpha = 0.75f), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
        }
        // Tide marks along the top.
        val t0 = d.scores.first().t
        d.turns.forEach { tr ->
            val px = ((tr.t - t0) / 3_600_000f + 0.5f) * w
            if (px < 0 || px > size.width) return@forEach
            val sz = 4.dp.toPx()
            val p = Path().apply {
                if (tr.high) {
                    moveTo(px, 0f); lineTo(px - sz, sz * 1.6f); lineTo(px + sz, sz * 1.6f)
                } else {
                    moveTo(px, sz * 1.6f); lineTo(px - sz, 0f); lineTo(px + sz, 0f)
                }
                close()
            }
            drawPath(p, C.sea)
        }
        // Selected hour outline.
        if (selected != null) {
            drawRoundRect(C.foam.copy(alpha = 0.7f), topLeft = Offset(selected * w + 1, top), size = Size(w - 2, h), cornerRadius = CornerRadius(4.dp.toPx()), style = Stroke(1.dp.toPx()))
        }
    }
}
