package app.afli.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.afli.data.Feeds
import app.afli.data.UiState
import app.afli.model.Astro
import app.afli.model.Bite
import app.afli.model.Fish
import app.afli.model.Hour
import app.afli.model.HourScore
import app.afli.model.Model
import app.afli.model.Reason
import app.afli.model.Safety
import app.afli.model.Species
import app.afli.model.Spot
import app.afli.model.Water
import app.afli.t
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * Which hour the Now screen is showing. Null is "now"; swiping the time strip or tapping
 * "Next best" moves it. The header's mini score reads it too.
 */
object Scrub {
    var at by mutableStateOf<Long?>(null)

    /** A time the strip should glide to (from tapping "Next best" or "Back to now"). */
    var jump by mutableStateOf<Long?>(null)

    fun index(s: UiState): Int {
        val a = at ?: return s.nowIndex
        val i = s.scores.indexOfFirst { it.t == a }
        return if (i >= s.nowIndex) i else s.nowIndex
    }

    fun reset() {
        at = null
        jump = null
    }
}

/**
 * Now: the spot chips, then one card that answers "should I go, for what, when, with what"
 * (score ring, best fish, tide, best time, bait, and a strip to swipe through the next two
 * days), the why chips, a grid of condition tiles that open into charts, and a row of fish
 * cards. Pull down to reload.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NowScreen(
    s: UiState,
    top: Dp,
    scroll: ScrollState,
    onSpot: (Spot) -> Unit,
    onFixSpot: () -> Unit,
    onRetry: () -> Unit,
) {
    val spot = s.spot
    val view = LocalView.current
    val sel = Scrub.index(s)
    val now = s.scores.getOrNull(sel)
    val hours = s.forecast?.hours.orEmpty()
    val h = hours.getOrNull(sel)

    LaunchedEffect(spot?.id) { Scrub.reset() }
    LaunchedEffect(s.now != null) {
        // "now2": the tour gained the time strip, so it shows once more for anyone who saw the old one.
        if (s.now != null) Tips.maybeTour(
            "now2",
            listOf(
                Tips.Step("score", t("Your bite score", "Tökulíkurnar"), t("Big number = good time to fish. It mixes tide, light, wind, pressure and water temperature.", "Há tala = góður tími til að veiða. Hún blandar saman sjávarföllum, birtu, vindi, loftþrýstingi og sjávarhita.")),
                Tips.Step("strip", t("Swipe through time", "Flettu í gegnum tímann"), t("Drag this strip to see any hour in the next two days. Everything on the screen changes to match.", "Dragðu þessa ræmu til að sjá hvaða klukkutíma sem er næstu tvo daga. Allt á skjánum breytist með.")),
                Tips.Step("safety", t("Is it safe?", "Er óhætt að veiða?"), t("This checks wind gusts and waves on their own. Stay home means stay home.", "Þetta skoðar vindhviður og öldur sérstaklega. Vertu heima þýðir vertu heima.")),
                Tips.Step("why", t("Why this score", "Af hverju"), t("These chips say what's helping or hurting. Long-press anything in Afli to have it explained.", "Þessir miðar segja hvað hjálpar og hvað ekki. Haltu fingri á hverju sem er í Afla til að fá útskýringu.")),
            ),
        )
    }

    // Pull to refresh, with Afli's own glass indicator. Only spins for pulls, not other loads.
    val pull = rememberPullToRefreshState()
    var pulling by remember { mutableStateOf(false) }
    LaunchedEffect(s.loading) { if (!s.loading) pulling = false }
    LaunchedEffect(pull) {
        snapshotFlow { pull.distanceFraction >= 1f }.distinctUntilChanged().collect { if (it) Haptics.tap(view) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pullToRefresh(isRefreshing = pulling && s.loading, state = pull, onRefresh = {
                pulling = true
                Haptics.confirm(view)
                onRetry()
            }),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(top = top)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SpotChips(s, onSpot)

            if (s.error != null && s.now == null) {
                GlassCard(Modifier.fillMaxWidth(), onClick = onRetry) {
                    Text(t("No forecast yet", "Engin spá enn"), style = T.title)
                    Spacer(Modifier.height(6.dp))
                    Text(s.error.toString(), style = T.body)
                    Spacer(Modifier.height(10.dp))
                    Text(t("Tap to try again", "Ýttu til að reyna aftur"), style = T.heading.copy(color = C.brass))
                }
            }

            if (s.now == null && s.error == null) {
                // A slow breathing pulse so it's clearly working, not stuck.
                val pulse by rememberInfiniteTransition(label = "loading").animateFloat(
                    0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
                )
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        if (s.gpsAsked) t("Reading the sea…", "Les í sjóinn…") else t("Finding where you are…", "Finn hvar þú ert…"),
                        style = T.title,
                        modifier = Modifier.graphicsLayer { alpha = pulse },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(t("Pulling the forecast, tides and sea temperature.", "Sæki spána, sjávarföllin og sjávarhitann."), style = T.small)
                }
            }

            if (now != null && spot != null && h != null) {
                HeroCard(s, spot, now, sel, hours)

                if (now.reasons.isNotEmpty()) {
                    Column(Modifier.coachTarget("why")) {
                        SectionLabel(t("Why", "Af hverju"))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            now.reasons.take(6).forEach { r -> ReasonChip(r) }
                        }
                    }
                }

                ConditionTiles(s, spot, hours, sel, now)
                FishRow(s, spot)
                SpotCard(spot, onFixSpot)

                Text(
                    t("Forecast", "Spá") + ": ${s.forecast?.weatherModel ?: "Open-Meteo"} · " + t("Sea", "Sjór") + ": Open-Meteo Marine" +
                        if (Feeds.inIceland(spot.lat, spot.lon)) " · " + t("Live", "Rauntími") + ": Veðurstofa Íslands" else "",
                    style = T.small.copy(color = C.faint),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
            BottomBarSpace()
        }
        PullIndicator(pull, pulling && s.loading, top)
    }
}

/** The score in the header once the big ring has scrolled away: a tiny ring, number, word. */
@Composable
fun MiniScore(hs: HourScore, isNow: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val col = C.score(hs.score)
    Row(
        modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(26.dp)) {
            val w = 3.dp.toPx()
            val tl = Offset(w, w)
            val sz = Size(size.width - 2 * w, size.height - 2 * w)
            drawArc(C.line, 135f, 270f, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
            drawArc(col, 135f, 270f * hs.score / 100f, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
        }
        Spacer(Modifier.width(6.dp))
        Column {
            Text("${hs.score}", style = T.heading.copy(color = col, lineHeight = 17.sp))
            Text(if (isNow) hs.bite.label else clock(hs.t), style = T.small.copy(fontSize = 10.sp, lineHeight = 12.sp))
        }
    }
}

// ---------------------------------------------------------------- spot chips

@Composable
private fun SpotChips(s: UiState, onSpot: (Spot) -> Unit) {
    val spot = s.spot
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val list = buildList {
            if (spot != null && s.spots.none { it.id == spot.id }) add(spot)
            addAll(s.spots)
        }
        list.forEach { sp ->
            GlassChip(
                text = sp.label,
                selected = sp.id == spot?.id,
                dot = if (sp.water == Water.LAKE) C.sea else null,
                explain = if (sp.water == Water.LAKE) t("${sp.label} is a lake. Lake fishing needs a permit.", "${sp.label} er vatn. Til að veiða í vötnum þarf veiðileyfi.")
                else t("Tap to see the forecast for ${sp.label}.", "Ýttu til að sjá spána fyrir þennan stað."),
                onClick = { onSpot(sp) },
            )
        }
    }
}

// ---------------------------------------------------------------- the hero card

@Composable
private fun HeroCard(s: UiState, spot: Spot, now: HourScore, sel: Int, hours: List<Hour>) {
    val isNow = sel == s.nowIndex
    val view = LocalView.current
    GlassCard(Modifier.fillMaxWidth().coachTarget("score")) {
        // Where and when, and is it safe.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (s.atSpot) t("You're at ${spot.label}", if (spot.builtIn) "Þú ert við ${spot.label}" else "Þú ert hér: ${spot.label}") else spot.label,
                    style = T.small,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isNow) t("RIGHT NOW", "NÚNA") else "${dayWord(now.t)} ${clock(now.t)}".uppercase(Locale.ROOT),
                        style = T.label,
                    )
                    AnimatedVisibility(!isNow, enter = fadeIn(), exit = fadeOut()) {
                        Text(
                            "  ·  " + t("Back to now", "Aftur í núna"),
                            style = T.label.copy(color = C.mist),
                            modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) {
                                Haptics.tap(view)
                                Scrub.jump = s.scores.getOrNull(s.nowIndex)?.t
                            },
                        )
                    }
                }
            }
            SafetyPill(now.safety, now.safetyWhy, Modifier.coachTarget("safety"))
        }
        Spacer(Modifier.height(14.dp))

        // Score ring and the three best fish.
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(now.score, now.bite, Modifier.size(128.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("BEST FISH", "BESTU FISKARNIR"), style = T.label)
                val ranked = now.perSpecies.filter { it.second > 0 }.take(3).ifEmpty { now.perSpecies.take(3) }
                ranked.forEach { (sp, v) -> FishChance(sp, v) }
            }
        }
        Spacer(Modifier.height(14.dp))

        // Tide, best time, bait.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (spot.water == Water.SEA) tideLine(hours, sel)?.let { InfoLine(t("Tide", "Sjávarföll"), it) }
            s.window?.let { w ->
                val nowMs = System.currentTimeMillis()
                val from = if (w.start <= nowMs) t("now", "núna") else "${dayWord(w.start).lowercase(app.afli.L.locale)} ${clock(w.start)}"
                InfoLine(
                    t("Best", "Best"),
                    "$from – ${clock(w.end)} · ${w.peak}  ›",
                    accent = true,
                    onClick = {
                        Haptics.tap(view)
                        Scrub.jump = maxOf(w.start, s.scores.getOrNull(s.nowIndex)?.t ?: w.start)
                    },
                )
            }
            now.best?.let { b -> InfoLine(t("Try", "Prófaðu"), b.bait.toString()) }
        }
        Spacer(Modifier.height(12.dp))
        TimeStrip(s, sel, Modifier.coachTarget("strip"))
    }
}

/** "Rising · high 14:20" from the hour shown, or null for no tide data. */
private fun tideLine(hours: List<Hour>, i: Int): String? {
    val rising = Model.tideRising(hours, i) ?: return null
    val t0 = hours[i].t
    val next = Model.tideTurns(hours).firstOrNull { it.t > t0 }
    val dir = if (rising) t("Rising", "Aðfall") else t("Falling", "Útfall")
    return if (next == null) dir else {
        val turn = if (next.high) t("high", "flóð") else t("low", "fjara")
        "$dir · $turn ${clock(roundTo10(next.t))}"
    }
}

private fun roundTo10(t: Long): Long = (t + 300_000L) / 600_000L * 600_000L

@Composable
private fun InfoLine(label: String, text: String, accent: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(remember { MutableInteractionSource() }, null, onClick = onClick) else it },
        verticalAlignment = Alignment.Top,
    ) {
        Text(label.uppercase(app.afli.L.locale), style = T.label.copy(fontSize = 11.sp), modifier = Modifier.width(76.dp).padding(top = 2.dp))
        Text(text, style = T.small.copy(color = if (accent) C.brass else C.foam), modifier = Modifier.weight(1f))
    }
}

/**
 * The bite score as an instrument: a brass-tipped ring that fills to the score with a soft glow,
 * small ticks at the OK (35) and Great (60) marks, the number and word inside.
 */
@Composable
fun ScoreRing(score: Int, bite: Bite, modifier: Modifier = Modifier) {
    val shown by animateIntAsState(score, tween(700), label = "ringNum")
    val sweep by animateFloatAsState(score / 100f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow), label = "ring")
    val col by animateColorAsState(C.score(score), tween(500), label = "ringCol")
    Box(
        modifier.explains(
            t(
                "Bite score from 0 to 100. Over 60 is great, 35–60 is OK, under 35 is slow. It's a guide, not a promise; logging trips makes it smarter.",
                "Tökulíkur frá 0 upp í 100. Yfir 60 er frábært, 35–60 ágætt og undir 35 rólegt. Þetta er leiðbeining, ekki loforð; skráðu ferðirnar og spáin batnar.",
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 9.dp.toPx()
            val inset = w / 2 + 6.dp.toPx()
            val tl = Offset(inset, inset)
            val sz = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(C.line, 135f, 270f, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
            if (sweep > 0.004f) {
                drawArc(col.copy(alpha = 0.22f), 135f, 270f * sweep, false, tl, sz, style = Stroke(w * 2.4f, cap = StrokeCap.Round))
                drawArc(col, 135f, 270f * sweep, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
            }
            val r = sz.width / 2
            val c = center
            listOf(35, 60).forEach { v ->
                val a = Math.toRadians(135.0 + 270.0 * v / 100.0)
                val r1 = r + w / 2 + 2.dp.toPx()
                val r2 = r1 + 4.dp.toPx()
                drawLine(
                    C.brass.copy(alpha = 0.8f),
                    Offset(c.x + (r1 * kotlin.math.cos(a)).toFloat(), c.y + (r1 * kotlin.math.sin(a)).toFloat()),
                    Offset(c.x + (r2 * kotlin.math.cos(a)).toFloat(), c.y + (r2 * kotlin.math.sin(a)).toFloat()),
                    1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$shown", style = T.hero.copy(fontSize = 50.sp, lineHeight = 52.sp, letterSpacing = (-1.5).sp, color = col))
            Text(bite.label, style = T.small.copy(color = col, fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun FishChance(sp: Species, v: Int) {
    Row(Modifier.explains("${sp.name} (${sp.other}). ${sp.fact}\n\n" + t("Try: ", "Prófaðu: ") + sp.bait), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(sp.name, style = T.heading.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sp.other, style = T.small.copy(fontSize = 11.sp, lineHeight = 13.sp, color = C.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Bar(v / 100f, C.score(v), Modifier.width(48.dp).height(6.dp))
        Text("$v", style = T.number.copy(fontSize = 13.sp), textAlign = TextAlign.End, modifier = Modifier.width(30.dp))
    }
}

@Composable
fun SafetyPill(safety: Safety, why: String, modifier: Modifier = Modifier) {
    GlassChip(
        text = safety.label,
        dot = C.safety(safety),
        modifier = modifier,
        explain = t("Safety looks only at gusts, waves and cold. It never mixes into the bite score.", "Öryggið skoðar bara hviður, öldur og kulda. Það blandast aldrei inn í tökulíkurnar."),
        onClick = { Tips.explain(why) },
    )
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

// ---------------------------------------------------------------- time strip

/**
 * The next 48 hours as a row of little score bars under a fixed brass needle. Drag it and it
 * snaps hour by hour with a tick; the hour under the needle is what the whole screen shows.
 * Tap an hour to glide to it.
 */
@Composable
private fun TimeStrip(s: UiState, sel: Int, modifier: Modifier = Modifier) {
    val from = s.nowIndex
    val to = min(s.scores.lastIndex, from + 47)
    if (to <= from) return
    val items = s.scores.subList(from, to + 1)
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (sel - from).coerceIn(0, items.lastIndex))
    val fling = rememberSnapFlingBehavior(list, SnapPosition.Center)
    var gliding by remember { mutableStateOf(false) }
    val centered by remember {
        derivedStateOf {
            val info = list.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index
        }
    }
    // Dragging: whatever hour sits under the needle becomes the shown hour.
    LaunchedEffect(centered) {
        val i = centered ?: return@LaunchedEffect
        if (gliding) return@LaunchedEffect
        val next = if (i == 0) null else items.getOrNull(i)?.t
        if (Scrub.at != next) {
            Scrub.at = next
            Haptics.scrub(view)
        }
    }
    // A requested jump (Next best, Back to now): glide there, then settle on it.
    LaunchedEffect(Scrub.jump) {
        val j = Scrub.jump ?: return@LaunchedEffect
        val i = items.indexOfFirst { it.t >= j }.let { if (it < 0) 0 else it }
        gliding = true
        list.animateScrollToItem(i)
        gliding = false
        Scrub.at = if (i == 0) null else items[i].t
        Scrub.jump = null
    }
    val itemW = 30.dp
    BoxWithConstraints(modifier.fillMaxWidth().height(66.dp)) {
        val pad = (maxWidth - itemW) / 2
        LazyRow(
            state = list,
            flingBehavior = fling,
            contentPadding = PaddingValues(horizontal = pad),
            modifier = Modifier.fillMaxSize().fadeEdges(),
        ) {
            itemsIndexed(items, key = { _, it -> it.t }) { i, hs ->
                HourTick(hs, i, i == centered, Modifier.width(itemW).fillMaxHeight()) {
                    Haptics.tap(view)
                    scope.launch {
                        gliding = true
                        list.animateScrollToItem(i)
                        gliding = false
                        Scrub.at = if (i == 0) null else items[i].t
                    }
                }
            }
        }
        // The needle.
        Canvas(Modifier.align(Alignment.Center).width(14.dp).fillMaxHeight()) {
            val x = size.width / 2
            val barsBottom = size.height - 18.dp.toPx()
            drawLine(C.brass, Offset(x, 6.dp.toPx()), Offset(x, barsBottom + 2.dp.toPx()), 1.5.dp.toPx(), cap = StrokeCap.Round)
            drawPath(Path().apply {
                moveTo(x - 5.dp.toPx(), 0f)
                lineTo(x + 5.dp.toPx(), 0f)
                lineTo(x, 6.dp.toPx())
                close()
            }, C.brass)
        }
    }
}

@Composable
private fun HourTick(hs: HourScore, i: Int, selected: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val hr = hourOf(hs.t)
    val unsafe = hs.safety == Safety.STAY_HOME
    val a by animateFloatAsState(if (selected) 1f else 0.5f, tween(160), label = "tick")
    Column(
        modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onTap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.width(30.dp).weight(1f).padding(top = 8.dp)) {
            val bh = (4.dp.toPx() + (size.height - 4.dp.toPx()) * hs.score / 100f)
            val bw = 10.dp.toPx()
            val col = if (unsafe) C.bad else C.score(hs.score)
            drawRoundRect(
                col.copy(alpha = a * if (unsafe) 0.7f else 1f),
                topLeft = Offset((size.width - bw) / 2, size.height - bh),
                size = Size(bw, bh),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(bw / 2),
            )
        }
        val label = when {
            i == 0 -> t("Now", "Núna")
            hr == 0 -> shortDay(hs.t)
            hr % 3 == 0 -> "%02d".format(Locale.US, hr)
            else -> ""
        }
        Text(
            label,
            style = T.small.copy(fontSize = 10.sp, lineHeight = 12.sp, color = if (i == 0) C.brass else if (hr == 0) C.foam else C.faint),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.height(16.dp).padding(top = 3.dp),
        )
    }
}

/** Fades the left and right ends of a strip so it slides in and out of nowhere. */
private fun Modifier.fadeEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.horizontalGradient(0f to Color.Transparent, 0.12f to Color.Black, 0.88f to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

// ---------------------------------------------------------------- condition tiles

private class Tile(
    val key: String,
    val label: String,
    val value: String,
    val sub: String,
    val explain: String,
    val mini: @Composable (Modifier) -> Unit,
    val full: @Composable () -> Unit,
)

/**
 * Conditions as a two-column grid of glass tiles: a label, the big value, one line under it, and
 * a little picture. Tap a tile and a panel opens under its row with the full chart; tap again
 * (or another tile) to switch or close.
 */
@Composable
private fun ConditionTiles(s: UiState, spot: Spot, hours: List<Hour>, sel: Int, now: HourScore) {
    val h = hours[sel]
    val tiles = buildTiles(s, spot, hours, sel, h, now)
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val view = LocalView.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(t("Conditions", "Aðstæður"))
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { tile ->
                    TileCard(tile, open == tile.key, Modifier.weight(1f).fillMaxHeight()) {
                        Haptics.segment(view)
                        open = if (open == tile.key) null else tile.key
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            val inRow = row.firstOrNull { it.key == open }
            AnimatedVisibility(
                inRow != null,
                enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(tween(220)) + fadeOut(tween(160)),
            ) {
                // Keep showing the last open tile while this panel closes.
                val last = remember { arrayOfNulls<String>(1) }
                if (inRow != null) last[0] = inRow.key
                GlassCard(Modifier.fillMaxWidth(), glow = C.brass) {
                    AnimatedContent(last[0], transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "tilePanel") { k ->
                        Column {
                            val tile = tiles.firstOrNull { it.key == k } ?: return@Column
                            Text(tile.label.uppercase(app.afli.L.locale), style = T.label)
                            Spacer(Modifier.height(8.dp))
                            tile.full()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TileCard(tile: Tile, open: Boolean, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(
        modifier,
        shape = RoundedCornerShape(22.dp),
        padding = PaddingValues(14.dp),
        explain = tile.explain,
        glow = if (open) C.brass else null,
        onClick = onClick,
    ) {
        Row {
            Column(Modifier.weight(1f)) {
                Text(tile.label.uppercase(app.afli.L.locale), style = T.label.copy(fontSize = 11.sp), maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(tile.value, style = T.title.copy(fontSize = 20.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            tile.mini(Modifier.size(40.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(tile.sub, style = T.small.copy(fontSize = 12.sp, lineHeight = 16.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun buildTiles(s: UiState, spot: Spot, hours: List<Hour>, sel: Int, h: Hour, now: HourScore): List<Tile> {
    val sea = spot.water == Water.SEA
    val out = mutableListOf<Tile>()
    fun slice(back: Int, ahead: Int): Pair<List<Hour>, Int> {
        val a = (sel - back).coerceAtLeast(0)
        val b = (sel + ahead).coerceAtMost(hours.lastIndex)
        return hours.subList(a, b + 1) to (sel - a)
    }

    // Wind.
    out += Tile(
        "wind", t("Wind", "Vindur"),
        "${fmt(h.wind)} m/s",
        "${compass(h.windDir)} · " + t("gusts", "hviður") + " ${fmt(h.gust)}",
        t("Average wind and where it's from. Gusts are the strongest bursts; over 15 m/s is hard work on the shore.", "Meðalvindur og úr hvaða átt. Hviður eru snörpustu vindkviðurnar; yfir 15 m/s er erfitt að veiða frá landi."),
        mini = { m -> MiniWind(h.windDir, m) },
        full = {
            val (sl, mk) = slice(0, 24)
            Row(verticalAlignment = Alignment.CenterVertically) {
                WindDial(h.windDir, spot.facing, Modifier.size(132.dp).explains(t("The arrow shows where the wind is blowing to; the brass tick on the rim is which way the water is from your spot.", "Örin sýnir hvert vindurinn blæs; gyllta strikið á hringnum sýnir í hvaða átt þú kastar.")))
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Stat(t("Wind", "Vindur"), "${fmt(h.wind)} m/s")
                    Stat(t("Gusts", "Hviður"), "${fmt(h.gust)} m/s")
                    Stat(t("From", "Átt"), "${compass(h.windDir)} (${fmt(h.windDir)}°)")
                    spot.facing?.let { f ->
                        val on = kotlin.math.cos(Math.toRadians(h.windDir - f))
                        Stat(t("At you", "Miðað við þig"), if (on > 0.5) t("Onshore", "Að landi") else if (on < -0.5) t("Offshore", "Frá landi") else t("Side-on", "Á hlið"))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(t("Next 24 hours", "Næsta sólarhring"), style = T.small)
            Sparkline(sl.map { it.wind }, Modifier.fillMaxWidth().height(56.dp), C.foam, marker = mk, floor = 4.0, zeroBased = true)
            HourAxis(sl.map { it.t }, Modifier.fillMaxWidth().height(14.dp))
            s.live?.let { l ->
                Spacer(Modifier.height(8.dp))
                Text(
                    t(
                        "Live: ${l.station} (${fmt(l.distanceKm)} km) ${fmt(l.wind)} m/s, gusts ${fmt(l.gust)}. The next 12 hours are corrected to match.",
                        "Rauntími: ${l.station} (${fmt(l.distanceKm)} km) ${fmt(l.wind)} m/s, hviður ${fmt(l.gust)}. Næstu 12 tímar eru leiðréttir miðað við mælinguna.",
                    ),
                    style = T.small,
                )
            }
        },
    )

    // Tide.
    if (sea && hours.any { !it.seaLevel.isNaN() }) {
        val rising = Model.tideRising(hours, sel)
        val flow = now.tideFlow
        val turns = Model.tideTurns(hours)
        val next = turns.firstOrNull { it.t > h.t }
        out += Tile(
            "tide", t("Tide", "Sjávarföll"),
            when {
                flow != null && flow <= 0.25 -> t("Slack", "Liggjandi")
                rising == true -> t("Rising", "Aðfall")
                rising == false -> t("Falling", "Útfall")
                else -> "–"
            },
            next?.let { (if (it.high) t("High ", "Flóð ") else t("Low ", "Fjara ")) + clock(roundTo10(it.t)) } ?: t("Turn unknown", "Óvíst"),
            t("Whether the tide is coming in or going out, and when it next turns. Moving water feeds fish; slack water at the turn is often quiet.", "Hvort það er aðfall eða útfall og hvenær fallið snýst næst. Straumurinn ber æti að fiskinum; liggjandinn er oft rólegur."),
            mini = { m ->
                val (sl, mk) = slice(6, 12)
                Sparkline(sl.map { it.seaLevel }, m, C.sea, marker = mk)
            },
            full = {
                TideCurve(hours, h.t, Modifier.fillMaxWidth().height(160.dp), key = spot.id, markerLabel = if (sel == s.nowIndex) t("Now", "Núna") else clock(h.t))
                Spacer(Modifier.height(8.dp))
                val upcoming = turns.filter { it.t > h.t }.take(4)
                if (upcoming.isNotEmpty()) Text(
                    upcoming.joinToString("  ·  ") { (if (it.high) t("High ", "Flóð ") else t("Low ", "Fjara ")) + "${dayWord(it.t).let { d -> if (d == t("Today", "Í dag")) "" else "$d " }}${clock(roundTo10(it.t))}" },
                    style = T.small.copy(color = C.foam),
                )
                val spring = Astro.springIndex(h.t)
                Text(
                    when {
                        spring > 0.8 -> t("Big tides (spring tides, near new or full moon): the water moves fast.", "Stórstreymi (nálægt nýju eða fullu tungli): sjórinn streymir hratt.")
                        spring < 0.2 -> t("Small tides (neap tides, near half moon): gentler flow.", "Smástreymi (nálægt hálfu tungli): hægari straumur.")
                        else -> t("Medium tides this week.", "Meðalstreymi þessa dagana.")
                    },
                    style = T.small,
                )
                Text(t("A model estimate. Not for navigation.", "Mat úr líkani. Ekki ætlað til siglinga."), style = T.small.copy(color = C.faint))
            },
        )
    }

    // Sea.
    if (sea) {
        val wave = h.wave
        out += Tile(
            "sea", t("Sea", "Sjór"),
            "${fmt(h.sst, 1)} °C",
            t("Waves", "Öldur") + " ${fmt(wave, 1)} m" + if (spot.sheltered) t(" · less in harbour", " · minni í höfn") else "",
            t("Sea surface temperature and offshore wave height (model). Harbours feel only about a third of the waves.", "Sjávarhiti við yfirborð og ölduhæð úti fyrir (líkan). Í höfnum er aldan bara um þriðjungur af því."),
            mini = { m -> WaveGlyph(if (spot.sheltered) wave * 0.3 else wave, m) },
            full = {
                val (past, mk) = slice(7 * 24, 48)
                Text(t("Sea temperature, last week and next two days", "Sjávarhiti, síðustu viku og næstu tvo daga"), style = T.small)
                Sparkline(past.map { it.sst }, Modifier.fillMaxWidth().height(56.dp), C.sea, marker = mk, floor = 1.5)
                Spacer(Modifier.height(8.dp))
                val (wv, wk) = slice(0, 24)
                Text(t("Waves, next 24 hours", "Öldur, næsta sólarhring"), style = T.small)
                Sparkline(wv.map { it.wave }, Modifier.fillMaxWidth().height(48.dp), C.foam, marker = wk, floor = 1.0, zeroBased = true)
                HourAxis(wv.map { it.t }, Modifier.fillMaxWidth().height(14.dp))
                Spacer(Modifier.height(8.dp))
                val happy = Fish.sea.filter { it.temperatureFit(h.sst) >= 1.0 }
                Text(
                    if (happy.isEmpty()) t("No fish is at its favourite temperature right now.", "Enginn fiskur er við kjörhita núna.")
                    else t("Comfortable for: ", "Kjörhiti fyrir: ") + happy.joinToString(", ") { it.name },
                    style = T.small.copy(color = C.foam),
                )
            },
        )
    }

    // Pressure.
    val past3 = hours.getOrNull(sel - 3)?.pressure ?: Double.NaN
    val d3 = if (h.pressure.isNaN() || past3.isNaN()) Double.NaN else h.pressure - past3
    out += Tile(
        "pressure", t("Pressure", "Loftþrýstingur"),
        "${fmt(h.pressure)} hPa",
        when {
            d3.isNaN() -> "–"
            d3 < -0.8 -> "↓ ${fmt(-d3, 1)} " + t("in 3 h", "á 3 klst.")
            d3 > 1.0 -> "↑ ${fmt(d3, 1)} " + t("in 3 h", "á 3 klst.")
            else -> t("Steady", "Stöðugur")
        },
        t("Air pressure and how it changed over 3 hours. A slow fall often gets fish feeding; a fast fall means a storm is coming.", "Loftþrýstingur og breytingin síðustu 3 tíma. Hægt fall fær fiskinn oft til að taka; hratt fall boðar óveður."),
        mini = { m ->
            val (sl, mk) = slice(12, 12)
            Sparkline(sl.map { it.pressure }, m, C.mist, marker = mk, floor = 4.0)
        },
        full = {
            val (sl, mk) = slice(24, 24)
            Text(t("Yesterday to tomorrow", "Frá í gær fram á morgun"), style = T.small)
            Sparkline(sl.map { it.pressure }, Modifier.fillMaxWidth().height(72.dp), C.foam, marker = mk, floor = 6.0)
            HourAxis(sl.map { it.t }, Modifier.fillMaxWidth().height(14.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                t("Falling slowly: often good. Falling fast: a storm is near. Rising after a front: often a slow bite.", "Hægt fall: oft gott. Hratt fall: óveður nálgast. Hækkun eftir skil: oft dræm taka."),
                style = T.small,
            )
        },
    )

    // Light.
    val sun = now.sunElevation
    val day = Astro.sunDay(h.t, spot.lat, spot.lon)
    val tomorrow = Astro.sunDay(h.t + 86_400_000L, spot.lat, spot.lon)
    val up = sun > -0.833
    out += Tile(
        "light", t("Light", "Birta"),
        when {
            sun < -6 -> t("Dark", "Myrkur")
            sun < -0.833 -> t("Twilight", "Rökkur")
            sun < 8 -> t("Low sun", "Lág sól")
            else -> t("Daylight", "Dagsbirta")
        },
        when {
            day.allDay -> t("Sun up all day", "Sól allan sólarhringinn")
            day.never -> t("No sunrise today", "Engin sólarupprás í dag")
            up -> (day.set?.takeIf { it > h.t } ?: tomorrow.set)?.let { t("Sunset ", "Sólsetur ") + clock(it) } ?: "–"
            else -> (day.rise?.takeIf { it > h.t } ?: tomorrow.rise)?.let { t("Sunrise ", "Sólarupprás ") + clock(it) } ?: "–"
        },
        t("How bright it is. Dawn and dusk (the brass bands) are when many fish come close to feed.", "Hversu bjart er. Í ljósaskiptunum (gylltu böndunum) koma margir fiskar nær landi til að éta."),
        mini = { m -> SunGlyph(sun, m) },
        full = {
            val start = Instant.ofEpochMilli(h.t).atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            SunCurve(start, spot.lat, spot.lon, h.t, Modifier.fillMaxWidth().height(110.dp))
            Spacer(Modifier.height(8.dp))
            if (day.lowLight.isNotEmpty()) Text(
                t("Best light: ", "Besta birtan: ") + day.lowLight.joinToString(" · ") { (a, b) -> "${clock(a)}–${clock(b)}" },
                style = T.small.copy(color = C.brass),
            )
            Text(
                listOfNotNull(day.rise?.let { t("Sunrise ", "Sólarupprás ") + clock(it) }, day.set?.let { t("Sunset ", "Sólsetur ") + clock(it) }).joinToString(" · "),
                style = T.small,
            )
        },
    )

    // Air.
    val feels = Model.feelsLike(h.airTemp, h.wind)
    val rain = h.precip
    out += Tile(
        "air", t("Air", "Loft"),
        "${fmt(h.airTemp)} °C",
        when {
            !feels.isNaN() && h.airTemp - feels >= 2 -> t("Feels like ", "Vindkæling ") + "${fmt(feels)} °C"
            !rain.isNaN() && rain >= 0.1 -> t("Rain ", "Úrkoma ") + "${fmt(rain, 1)} mm"
            !h.cloud.isNaN() -> t("Cloud ", "Ský ") + "${fmt(h.cloud)}%"
            else -> "–"
        },
        t("Air temperature, how cold it feels in the wind, rain and cloud. Dress for the 'feels like' number.", "Lofthiti, hversu kalt er í vindinum, úrkoma og skýjahula. Klæddu þig eftir vindkælingunni."),
        mini = { m ->
            val (sl, mk) = slice(0, 24)
            Sparkline(sl.map { it.airTemp }, m, C.ok, marker = mk, floor = 3.0)
        },
        full = {
            val (sl, mk) = slice(0, 24)
            Text(t("Temperature, next 24 hours", "Hiti, næsta sólarhring"), style = T.small)
            Sparkline(sl.map { it.airTemp }, Modifier.fillMaxWidth().height(56.dp), C.ok, marker = mk, floor = 3.0)
            Spacer(Modifier.height(6.dp))
            Text(t("Rain (mm per hour)", "Úrkoma (mm á klst.)"), style = T.small)
            Sparkline(sl.map { it.precip }, Modifier.fillMaxWidth().height(40.dp), C.sea, bars = true, floor = 1.0, zeroBased = true)
            HourAxis(sl.map { it.t }, Modifier.fillMaxWidth().height(14.dp))
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat(t("Feels", "Vindkæling"), "${fmt(feels)} °C")
                Stat(t("Cloud", "Ský"), "${fmt(h.cloud)}%")
            }
        },
    )
    return out
}

@Composable
private fun Stat(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = T.small, modifier = Modifier.width(64.dp))
        Text(value, style = T.number)
    }
}

// ---------------------------------------------------------------- fish row

/**
 * Every fish for this water as a row of glass cards you swipe sideways, best chance first:
 * its best hour in the next day, whether the water suits it, and what to fish with.
 */
@Composable
private fun FishRow(s: UiState, spot: Spot) {
    val nowT = s.scores.getOrNull(s.nowIndex)?.t ?: return
    val next = s.scores.filter { it.t in nowT..(nowT + 24 * 3_600_000L) }
    if (next.isEmpty()) return
    val water = s.forecast?.hours?.getOrNull(s.nowIndex)?.sst ?: Double.NaN
    val rows = Fish.forWater(spot.water).map { sp ->
        val best = next.maxByOrNull { hs -> hs.perSpecies.firstOrNull { it.first.id == sp.id }?.second ?: 0 }
        val v = best?.perSpecies?.firstOrNull { it.first.id == sp.id }?.second ?: 0
        Triple(sp, v, best?.t)
    }.sortedByDescending { it.second }
    Column {
        SectionLabel(t("Fish in the next 24 hours", "Fiskar næsta sólarhring"))
        Row(
            Modifier
                .bleed(16.dp)
                .horizontalScroll(rememberScrollState())
                .height(IntrinsicSize.Min)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            rows.forEach { (sp, v, at) -> FishCard(sp, v, at, if (spot.water == Water.SEA) water else Double.NaN, Modifier.width(248.dp).fillMaxHeight()) }
        }
    }
}

/** Lets a row reach the screen edges past the page's side padding. */
private fun Modifier.bleed(by: Dp): Modifier = layout { m, c ->
    val extra = by.roundToPx() * 2
    val p = m.measure(c.copy(minWidth = c.minWidth + extra, maxWidth = c.maxWidth + extra))
    layout(c.maxWidth, p.height) { p.place(-by.roundToPx(), 0) }
}

@Composable
private fun FishCard(sp: Species, v: Int, at: Long?, water: Double, modifier: Modifier) {
    GlassCard(modifier, padding = PaddingValues(16.dp), explain = "${sp.name} (${sp.other}). ${sp.fact}") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(sp.name, style = T.heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sp.other, style = T.small.copy(color = C.faint), maxLines = 1)
            }
            Box(Modifier.size(44.dp).border(2.dp, C.score(v).copy(alpha = 0.8f), CircleShape), contentAlignment = Alignment.Center) {
                Text("$v", style = T.number.copy(color = C.score(v)))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (v > 0 && at != null) t("Best ", "Best ") + "${dayWord(at).lowercase(app.afli.L.locale)} ${clock(at)}" else t("Not in the next day", "Ekki næsta sólarhring"),
            style = T.small.copy(color = C.foam),
        )
        if (!water.isNaN()) {
            val fit = sp.temperatureFit(water)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Dot(if (fit >= 1.0) C.good else if (fit > 0.3) C.ok else C.bad, 7.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    t("Sea ", "Sjór ") + "${fmt(water, 1)} °C · " + t("likes ", "kýs ") + "${fmt(sp.optLo)}–${fmt(sp.optHi)} °C",
                    style = T.small,
                )
            }
        }
        val note = when {
            sp.months != null -> t("Season May–September", "Tímabil maí–september")
            sp.reach < 0.5 -> t("Rare from shore", "Veiðist sjaldan frá landi")
            else -> null
        }
        note?.let { Text(it, style = T.small.copy(color = C.faint), modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(10.dp))
        Text(t("TRY", "PRÓFAÐU"), style = T.label.copy(fontSize = 11.sp))
        Spacer(Modifier.height(2.dp))
        Text(sp.bait.toString(), style = T.small.copy(color = C.foam))
    }
}

// ---------------------------------------------------------------- this spot

@Composable
private fun SpotCard(spot: Spot, onFixSpot: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        SectionLabel(t("This spot", "Þessi staður"))
        Text(
            buildString {
                append(
                    if (spot.water == Water.LAKE) t("Lake", "Vatn")
                    else if (spot.sheltered) t("Sea, sheltered harbour", "Sjór, skjólgóð höfn")
                    else t("Sea, open coast", "Sjór, opin strönd"),
                )
                append(" · ")
                append(if (spot.facing != null) t("water to the ${compass(spot.facing)}", "snýr í ${compass(spot.facing)}") else t("water direction not set", "stefna ekki stillt"))
            },
            style = T.body,
        )
        Spacer(Modifier.height(10.dp))
        GlassButton(
            t("Fix spot", "Stilla stað"),
            accent = C.brass,
            explain = t("Point your phone at the water to set its direction, and say if it's a harbour or a lake.", "Beindu símanum þangað sem þú kastar til að stilla stefnuna og segðu hvort þetta sé höfn eða vatn."),
            onClick = onFixSpot,
        )
    }
}

// ---------------------------------------------------------------- pull to refresh

/** Afli's pull indicator: a glass bubble with the refresh arrow that turns as you pull. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.PullIndicator(state: PullToRefreshState, refreshing: Boolean, top: Dp) {
    val backdrop = LocalBackdrop.current
    val spin by rememberInfiniteTransition(label = "pullSpin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "spin",
    )
    val shape = CircleShape
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .padding(top = top - 44.dp)
            .size(40.dp)
            .graphicsLayer {
                val f = state.distanceFraction
                translationY = f.coerceAtMost(1.5f) * 56.dp.toPx()
                alpha = f.coerceIn(0f, 1f)
                val sc = 0.6f + 0.4f * f.coerceIn(0f, 1f)
                scaleX = sc
                scaleY = sc
            }
            .let { if (backdrop != null) it.glassControl(backdrop, shape) else it }
            .border(1.dp, Rim, shape),
        contentAlignment = Alignment.Center,
    ) {
        AfliIcon(
            Icon.REFRESH, C.brass,
            Modifier.size(20.dp).graphicsLayer { rotationZ = if (refreshing) spin else state.distanceFraction * 300f },
        )
    }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) =
    Box(Modifier.size(size).border(size / 2, color, CircleShape))
