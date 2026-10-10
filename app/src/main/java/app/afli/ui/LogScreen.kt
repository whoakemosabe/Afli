package app.afli.ui

import android.Manifest
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.afli.data.Catch
import app.afli.data.Repo
import app.afli.data.Trip
import app.afli.data.UiState
import app.afli.model.Bite
import app.afli.model.Fish
import app.afli.model.Learn
import app.afli.model.Model
import app.afli.model.Species
import app.afli.model.Water
import app.afli.t
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields

/**
 * The trip log, top to bottom: Undo (just after a delete), the summary of a trip just ended,
 * then either the Start card (the live answer for where he stands) or the live trip; then his
 * numbers in one row, a calendar of the last eight weeks, his trips grouped by week and month
 * with spot and fish filters, his fish collection, and what it all adds up to.
 */
@Composable
fun LogScreen(
    s: UiState,
    top: Dp,
    scroll: ScrollState,
    onStart: () -> Unit,
    onCatch: (String) -> Unit,
    onUndo: () -> Unit,
    onEnd: () -> Unit,
    onOpenTrip: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    LaunchedEffect(Unit) {
        Tips.maybeTour(
            "log",
            listOf(
                Tips.Step("start", t("Log every trip", "Skráðu hverja ferð"), t("Tap Start fishing when you get to the water. While you fish, you can log catches from the notification without opening the app.", "Ýttu á Byrja að veiða þegar þú mætir á staðinn. Á meðan geturðu skráð fiska beint úr tilkynningunni án þess að opna appið.")),
            ),
        )
    }
    val done = s.trips.filter { it.end != null }
    var spotFilter by remember { mutableStateOf<String?>(null) }
    var fishFilter by remember { mutableStateOf<String?>(null) }
    // A filter whose spot or fish no longer has trips quietly resets.
    if (spotFilter != null && done.none { it.spotId == spotFilter }) spotFilter = null
    if (fishFilter != null && done.none { tr -> tr.catches.any { it.species == fishFilter } }) fishFilter = null

    Column(
        Modifier.verticalScroll(scroll).padding(top = top).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // The trip just ended, read live so later edits (or a delete) show up.
        s.lastEnded?.let { e -> s.trips.firstOrNull { it.id == e.id } }?.let { ended -> SummaryCard(ended, s, onShare = { onShare(ended.id) }) }

        val active = s.activeTrip
        if (active == null) StartCard(s, onStart) else LiveTrip(active, s, onCatch, onUndo, onEnd)

        if (done.isEmpty()) {
            FirstTripCard()
        } else {
            QuickStats(done)
            SectionLabel(t("Your season", "Tímabilið þitt"))
            CalendarStrip(done)
            SectionLabel(t("Your trips", "Ferðirnar þínar"))
            Filters(done, spotFilter, fishFilter, { spotFilter = it }, { fishFilter = it })
            val shown = done.filter { (spotFilter == null || it.spotId == spotFilter) && (fishFilter == null || it.catches.any { c -> c.species == fishFilter }) }
            if (shown.isEmpty()) Text(t("No trips match.", "Engar ferðir passa."), style = T.small)
            groupTrips(shown).forEach { (label, trips) ->
                Text(label, style = T.small.copy(color = C.faint, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                trips.forEach { tr -> androidx.compose.runtime.key(tr.id) { TripCard(tr, onOpenTrip) } }
            }
        }

        SectionLabel(t("Your fish", "Fiskarnir þínir"))
        Collection(done)

        if (done.isNotEmpty()) {
            SectionLabel(t("What works for you", "Hvað virkar fyrir þig"))
            Insights(s, done)
        }
        BottomBarSpace()
    }
}

// ---------------------------------------------------------------- start and live trip

/** The live answer for where he stands, and one big button. Glows on a Great hour. */
@Composable
private fun StartCard(s: UiState, onStart: () -> Unit) {
    val view = LocalView.current
    val now = s.now
    val spot = s.spot
    val context = androidx.compose.ui.platform.LocalContext.current
    // Once he allows notifications, the catch buttons appear for the trip that just started.
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) app.afli.update.TripNotice.update(context)
    }
    GlassCard(Modifier.fillMaxWidth().coachTarget("start"), glow = if ((now?.score ?: 0) >= 60) C.brass else null) {
        Text(
            when {
                spot != null && s.atSpot -> t("You're at ${spot.label}. Start a trip?", if (spot.builtIn) "Þú ert við ${spot.label}. Byrja ferð?" else "Þú ert hér: ${spot.label}. Byrja ferð?")
                else -> t("Going fishing?", "Ertu að fara að veiða?")
            },
            style = T.title,
        )
        Spacer(Modifier.height(10.dp))
        if (now != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(now.score, now.bite, Modifier.size(76.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SafetyPill(now.safety, now.safetyWhy)
                    now.best?.let { b ->
                        FitText(t("Try ", "Prófaðu ") + b.name, T.heading.copy(fontSize = 15.sp), min = 11.sp)
                        Text(b.baitShort.toString(), style = T.small, maxLines = 2)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        } else {
            Text(t("Start a trip where you're standing.", "Byrjaðu ferð þar sem þú stendur."), style = T.small)
            Spacer(Modifier.height(12.dp))
        }
        GlassButton(
            t("Start fishing", "Byrja að veiða"),
            accent = C.brass,
            modifier = Modifier.fillMaxWidth(),
            explain = t("Starts a trip at your GPS position, saves the conditions, and puts catch buttons in your notifications.", "Byrjar ferð þar sem GPS segir að þú sért, vistar aðstæðurnar og setur aflahnappa í tilkynningarnar."),
            onClick = {
                Haptics.confirm(view)
                onStart()
                if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    runCatching { askNotify.launch(Manifest.permission.POST_NOTIFICATIONS) }
                }
            },
        )
    }
}

/**
 * The trip as it happens: a big timer; a strip of the whole trip with the tide and each fish
 * on it; when the last fish came and when the tide turns; the fish buttons; End.
 */
@Composable
private fun LiveTrip(trip: Trip, s: UiState, onCatch: (String) -> Unit, onUndo: () -> Unit, onEnd: () -> Unit) {
    val view = LocalView.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(trip.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val secs = (now - trip.start) / 1000
    val water = s.spots.firstOrNull { it.id == trip.spotId }?.water ?: s.spot?.water ?: Water.SEA
    // A little splash each time a fish is added.
    val splash = remember { Animatable(0f) }
    val seen = remember(trip.id) { intArrayOf(trip.catches.size) }
    LaunchedEffect(trip.catches.size) {
        val grew = trip.catches.size > seen[0]
        seen[0] = trip.catches.size
        if (grew) {
            splash.snapTo(1f)
            splash.animateTo(0f, tween(700, easing = FastOutSlowInEasing))
        }
    }
    GlassCard(Modifier.fillMaxWidth(), glow = C.brass) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FitText(t("Fishing at ", "Á veiðum: ") + spotLabel(trip.spotId, trip.spotName), T.small, min = 10.sp)
                Text(
                    "%d:%02d:%02d".format(secs / 3600, (secs / 60) % 60, secs % 60),
                    style = T.hero.copy(fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = 0.sp, fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                )
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                Canvas(
                    Modifier.size(72.dp).graphicsLayer {
                        val k = 1f + 0.25f * splash.value
                        scaleX = k
                        scaleY = k
                    },
                ) {
                    drawCircle(C.good.copy(alpha = 0.16f + 0.34f * splash.value))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${trip.catches.size}", style = T.title.copy(fontSize = 28.sp, color = if (trip.catches.isEmpty()) C.mist else C.good))
                    Text(t("fish", if (oneIs(trip.catches.size)) "fiskur" else "fiskar"), style = T.small.copy(fontSize = 11.sp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        TripTimeline(trip, s, now, Modifier.fillMaxWidth().height(70.dp))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            val last = trip.catches.maxOfOrNull { it.time }
            MiniFact(t("Last fish", "Síðasti fiskur"), last?.let { duration(((now - it) / 60_000).toInt()) + t(" ago", " síðan") } ?: t("none yet", "enginn enn"), Modifier.weight(1f))
            if (water == Water.SEA) {
                val turn = Model.tideTurns(s.hours).firstOrNull { it.t > now }
                MiniFact(
                    t("Tide", "Sjávarföll"),
                    turn?.let { (if (it.high) t("High in ", "Flóð eftir ") else t("Low in ", "Fjara eftir ")) + duration(((it.t - now) / 60_000).toInt()) } ?: "–",
                    Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionLabel(t("Caught one? Tap the fish", "Fékkstu fisk? Ýttu á hann"))
        // A fixed two-column grid: counts appear in place, so the buttons never jump around.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Fish.forWater(water).chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { f ->
                        val n = trip.catches.count { it.species == f.id }
                        CatchButton(f.name, n, "${f.name} (${f.other}). ${f.fact}", Modifier.weight(1f)) {
                            Haptics.confirm(view)
                            onCatch(f.id)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassButton(t("End trip", "Ljúka ferð"), accent = C.brass, onClick = {
                Haptics.confirm(view)
                onEnd()
            })
            if (trip.catches.isNotEmpty()) GlassButton(t("Undo last", "Afturkalla"), style = T.small, onClick = onUndo)
        }
    }
}

@Composable
private fun MiniFact(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label.uppercase(app.afli.L.locale), style = T.label.copy(fontSize = 10.sp), maxLines = 1)
        FitText(value, T.small.copy(color = C.foam), min = 10.sp)
    }
}

/**
 * The trip so far on one line: the tide over the trip (faint fill), a brass dot for each fish
 * at the time it came, and a marker for now. At least an hour wide so early trips aren't cramped.
 */
@Composable
private fun TripTimeline(trip: Trip, s: UiState, now: Long, modifier: Modifier) {
    Canvas(modifier) {
        val t0 = trip.start
        val t1 = maxOf(trip.end ?: now, t0 + 3_600_000L)
        fun x(ms: Long) = ((ms - t0).toFloat() / (t1 - t0)) * size.width
        val base = size.height - 14.dp.toPx()
        drawRoundRect(C.line, topLeft = Offset(0f, 0f), size = Size(size.width, base), cornerRadius = CornerRadius(10.dp.toPx()))
        // Tide.
        val pts = s.hours.filter { it.t in (t0 - 3_600_000L)..(t1 + 3_600_000L) && !it.seaLevel.isNaN() }
        if (pts.size >= 2) {
            val lo = pts.minOf { it.seaLevel }
            val hi = pts.maxOf { it.seaLevel }
            val span = (hi - lo).coerceAtLeast(0.3)
            val p = Path()
            pts.forEachIndexed { i, h ->
                val px = x(h.t)
                val py = (base - 6.dp.toPx() - ((h.seaLevel - lo) / span).toFloat() * (base - 16.dp.toPx()))
                if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
            }
            val fill = Path().apply {
                addPath(p)
                lineTo(x(pts.last().t), base)
                lineTo(x(pts.first().t), base)
                close()
            }
            clipRect(0f, 0f, size.width, base) {
                drawPath(fill, C.sea.copy(alpha = 0.22f))
                drawPath(p, C.sea.copy(alpha = 0.8f), style = Stroke(1.5.dp.toPx()))
            }
        }
        // Fish.
        trip.catches.forEach { c ->
            val cx = x(c.time).coerceIn(6.dp.toPx(), size.width - 6.dp.toPx())
            drawCircle(C.brass.copy(alpha = 0.3f), 9.dp.toPx(), Offset(cx, base / 2))
            drawCircle(C.brass, 5.dp.toPx(), Offset(cx, base / 2))
        }
        // Now.
        if (trip.end == null) {
            val nx = x(now)
            drawLine(C.foam.copy(alpha = 0.8f), Offset(nx, 0f), Offset(nx, base), 1.5.dp.toPx(), cap = StrokeCap.Round)
        }
        // Start and now/end times.
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(150, 234, 244, 248)
            textSize = 10.sp.toPx()
            isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.drawText(clock(t0), 0f, size.height - 1.dp.toPx(), paint)
        val endLabel = clock(trip.end ?: now)
        drawContext.canvas.nativeCanvas.drawText(endLabel, size.width - paint.measureText(endLabel), size.height - 1.dp.toPx(), paint)
    }
}

/** One fish to tap while fishing: its name on the left, the count in a fixed slot on the right. */
@Composable
private fun CatchButton(name: String, n: Int, explain: String, modifier: Modifier, onClick: () -> Unit) {
    val backdrop = LocalBackdrop.current
    val press = rememberPress()
    val shape = RoundedCornerShape(50)
    val tint = if (n > 0) Color(0x55D8B56A) else Color(0x2605080F)
    val m = if (backdrop != null) modifier.glassControl(backdrop, shape, press = press.amount, tint = tint, layer = press.squishLayer(0.06f))
    else modifier.squish(press, 0.06f).background(tint, shape)
    Row(
        m.pressable(press, explain, scaleBy = 0f, haptic = false, onClick = onClick).height(46.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FitText(name, T.small.copy(color = if (n > 0) C.brass else C.foam), Modifier.weight(1f), min = 10.sp)
        Text(if (n > 0) "×$n" else "", style = T.number.copy(color = C.brass), maxLines = 1, softWrap = false, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
    }
}

/** Shown after End trip: the trip in one card, with Share and a close button. */
@Composable
private fun SummaryCard(trip: Trip, s: UiState, onShare: () -> Unit) {
    val mins = (((trip.end ?: trip.start) - trip.start) / 60_000).toInt()
    GlassCard(Modifier.fillMaxWidth(), glow = C.good) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("TRIP DONE", "FERÐ LOKIÐ"), style = T.label, modifier = Modifier.weight(1f))
            Box(Modifier.size(44.dp).clickable(remember { MutableInteractionSource() }, null) { Repo.dismissSummary() }, contentAlignment = Alignment.Center) {
                Text("×", style = T.title.copy(color = C.faint))
            }
        }
        FitText("${duration(mins)} · " + fishCount(trip.catches.size), T.title, min = 14.sp)
        val parts = listOfNotNull(
            trip.catches.groupBy { it.species }.entries.sortedByDescending { it.value.size }.firstOrNull()?.let { (id, c) -> t("most: ", "mest: ") + "${Fish.byId(id)?.name ?: id} ×${c.size}" },
            tideStory(trip, s),
            trip.snapshot?.let { t("Afli said ${it.score}", "Afli spáði ${it.score}") },
        )
        Text(parts.joinToString(" · ").ifEmpty { t("A blank trip still counts.", "Ferð án afla telur líka.") }, style = T.small.copy(color = C.foam))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (trip.catches.isNotEmpty()) GlassButton(t("Share", "Deila"), accent = C.brass, style = T.small, onClick = onShare)
            // Ended by mistake? Put it back on.
            if (s.activeTrip == null && System.currentTimeMillis() - (trip.end ?: 0L) < 30 * 60_000L) GlassButton(t("Not done? Resume", "Ekki búinn? Halda áfram"), style = T.small, onClick = { Repo.resumeTrip() })
        }
    }
}

/** "all on the rising tide" style note: when most fish came, by tide direction. */
private fun tideStory(trip: Trip, s: UiState): String? {
    if (trip.catches.isEmpty() || s.hours.isEmpty()) return null
    val dirs = trip.catches.mapNotNull { c ->
        val i = s.hours.indexOfLast { it.t <= c.time }
        if (i < 0) null else Model.tideRising(s.hours, i)
    }
    if (dirs.isEmpty()) return null
    val share = dirs.count { it }.toDouble() / dirs.size
    return when {
        dirs.size == 1 -> if (share == 1.0) t("on the rising tide", "á aðfalli") else t("on the falling tide", "á útfalli")
        share == 1.0 -> t("all on the rising tide", "allir á aðfalli")
        share == 0.0 -> t("all on the falling tide", "allir á útfalli")
        share >= 0.66 -> t("most on the rising tide", "flestir á aðfalli")
        share <= 0.34 -> t("most on the falling tide", "flestir á útfalli")
        else -> null
    }
}

fun fishCount(n: Int) = t(if (n == 1) "1 fish" else "$n fish", if (oneIs(n)) "$n fiskur" else "$n fiskar")

@Composable
private fun FirstTripCard() {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(t("Your first trip", "Fyrsta ferðin"), style = T.title)
        Spacer(Modifier.height(10.dp))
        listOf(
            t("Go to the water and tap Start fishing.", "Farðu á staðinn og ýttu á Byrja að veiða."),
            t("Each time you catch one, tap the fish, here or in the notification.", "Í hvert sinn sem þú veiðir, ýttu á fiskinn, hér eða í tilkynningunni."),
            t("Tap End trip when you leave. Blank trips count too.", "Ýttu á Ljúka ferð þegar þú ferð. Ferðir án afla telja líka."),
        ).forEachIndexed { i, line ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).border(1.5.dp, C.brass, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = T.small.copy(color = C.brass, fontWeight = FontWeight.SemiBold))
                }
                Spacer(Modifier.width(12.dp))
                Text(line, style = T.body)
            }
        }
    }
}

// ---------------------------------------------------------------- numbers and calendar

@Composable
private fun QuickStats(done: List<Trip>) {
    val hours = done.sumOf { ((it.end ?: it.start) - it.start) / 3_600_000.0 }
    val fish = done.sumOf { it.catches.size }
    GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Big(t("Trips", "Ferðir"), "${done.size}", Modifier.weight(1f))
            Big(t("Hours", "Klst."), fmt(hours, if (hours < 10) 1 else 0), Modifier.weight(1f))
            Big(t("Fish", "Fiskar"), "$fish", Modifier.weight(1f))
            Big(t("Per hour", "Á klst."), fmt(Learn.rate(done), 1), Modifier.weight(1f))
        }
    }
}

@Composable
private fun Big(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FitText(value, T.title.copy(fontSize = 22.sp), min = 14.sp)
        FitText(label, T.small.copy(fontSize = 11.sp, color = C.faint), min = 9.sp)
    }
}

/**
 * The last eight weeks, Monday to Sunday down each column: empty, a trip with no fish
 * (outline), or fish (brass, brighter with more). Today is outlined.
 */
@Composable
private fun CalendarStrip(done: List<Trip>) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val firstMonday = today.minusDays((today.dayOfWeek.value - 1).toLong()).minusWeeks(7)
    val byDay = done.groupBy { Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() }.filterKeys { !it.isBefore(firstMonday) }
    val maxFish = byDay.values.maxOfOrNull { d -> d.sumOf { it.catches.size } }?.coerceAtLeast(1) ?: 1
    GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (w in 0 until 8) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (d in 0 until 7) {
                        val day = firstMonday.plusDays((w * 7 + d).toLong())
                        val trips = byDay[day].orEmpty()
                        val fish = trips.sumOf { it.catches.size }
                        val shape = RoundedCornerShape(4.dp)
                        var m = Modifier.fillMaxWidth().aspectRatio(1f).clip(shape).background(
                            when {
                                day.isAfter(today) -> Color.Transparent
                                fish > 0 -> C.brass.copy(alpha = 0.35f + 0.65f * fish / maxFish)
                                else -> C.line
                            },
                        )
                        if (trips.isNotEmpty() && fish == 0) m = m.border(1.dp, C.brass.copy(alpha = 0.7f), shape)
                        if (day == today) m = m.border(1.dp, C.foam, shape)
                        Box(m)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val tripsIn = done.count { !Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate().isBefore(firstMonday) }
            Text(tripCount(tripsIn) + t(" in 8 weeks", " á 8 vikum"), style = T.small.copy(fontSize = 11.sp, color = C.faint), modifier = Modifier.weight(1f))
            Box(Modifier.size(10.dp).border(1.dp, C.brass.copy(alpha = 0.7f), RoundedCornerShape(2.dp)))
            Text(" " + t("blank", "án afla") + "   ", style = T.small.copy(fontSize = 11.sp, color = C.faint))
            Box(Modifier.size(10.dp).background(C.brass, RoundedCornerShape(2.dp)))
            Text(" " + t("fish", "afli"), style = T.small.copy(fontSize = 11.sp, color = C.faint))
        }
    }
}

// ---------------------------------------------------------------- trips

@Composable
private fun Filters(done: List<Trip>, spot: String?, fish: String?, onSpot: (String?) -> Unit, onFish: (String?) -> Unit) {
    val spots = done.groupBy { it.spotId }.entries.sortedByDescending { it.value.size }.map { it.key to spotLabel(it.key, it.value.first().spotName) }
    val species = done.flatMap { it.catches }.map { it.species }.distinct().mapNotNull { Fish.byId(it) }
    if (spots.size < 2 && species.size < 2) return
    Row(
        Modifier.bleed(16.dp).fadeEdges(16.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassChip(t("All", "Allt"), selected = spot == null && fish == null, onClick = { onSpot(null); onFish(null) })
        if (spots.size >= 2) spots.forEach { (id, name) -> GlassChip(name, selected = spot == id, onClick = { onSpot(if (spot == id) null else id) }) }
        if (species.size >= 2) species.forEach { f -> GlassChip(f.name, selected = fish == f.id, dot = C.brass, onClick = { onFish(if (fish == f.id) null else f.id) }) }
    }
}

/** "This week", "Last week", then month names, newest first. */
private fun groupTrips(trips: List<Trip>): List<Pair<String, List<Trip>>> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    fun week(d: LocalDate) = d.get(IsoFields.WEEK_BASED_YEAR) * 100 + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    val thisWeek = week(today)
    val lastWeek = week(today.minusWeeks(1))
    val monthFmt = DateTimeFormatter.ofPattern("LLLL yyyy", app.afli.L.locale)
    return trips.sortedByDescending { it.start }.groupBy { tr ->
        val d = Instant.ofEpochMilli(tr.start).atZone(zone).toLocalDate()
        when (week(d)) {
            thisWeek -> t("This week", "Þessi vika")
            lastWeek -> t("Last week", "Síðasta vika")
            else -> monthFmt.format(d).replaceFirstChar { it.titlecase(app.afli.L.locale) }
        }
    }.toList()
}

/** A trip: photo or score ring, spot and time, fish counts, and when the fish came. */
@Composable
private fun TripCard(trip: Trip, onOpen: (String) -> Unit) {
    val mins = (((trip.end ?: trip.start) - trip.start) / 60_000).toInt()
    val img by rememberPhoto(trip.photos.firstOrNull(), 200)
    GlassCard(
        Modifier.fillMaxWidth(),
        padding = PaddingValues(12.dp),
        explain = t("Tap to see the trip, set fish sizes, add a note or photos, or share it.", "Ýttu til að sjá ferðina, skrá stærð fiska, bæta við athugasemd eða myndum, eða deila henni."),
        onClick = { onOpen(trip.id) },
    ) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(C.deep), contentAlignment = Alignment.Center) {
                val pic = img
                when {
                    pic != null -> Image(pic, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    trip.snapshot != null -> ScoreRing(trip.snapshot.score, scoreBite(trip.snapshot.score), Modifier.size(60.dp))
                    else -> FishGlyph(C.faint, Modifier.size(34.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                FitText(spotLabel(trip.spotId, trip.spotName), T.heading, min = 11.sp)
                Text("${tripDay(trip.start)} ${clock(trip.start)} · ${duration(mins)}", style = T.small, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (trip.catches.isEmpty()) t("Blank", "Án afla")
                    else trip.catches.groupBy { it.species }.entries.sortedByDescending { it.value.size }.joinToString("  ") { (id, c) -> "${Fish.byId(id)?.name ?: id} ×${c.size}" },
                    style = T.small.copy(color = if (trip.catches.isEmpty()) C.mist else C.good),
                    maxLines = 2,
                )
                Spacer(Modifier.height(6.dp))
                MiniTimeline(trip, Modifier.fillMaxWidth().height(8.dp))
                trip.snapshot?.let { snap ->
                    Text(
                        t("Afli said ${snap.score} · you caught ${fmt(Learn.rate(listOf(trip)), 1)}/h", "Afli spáði ${snap.score} · þú veiddir ${fmt(Learn.rate(listOf(trip)), 1)}/klst."),
                        style = T.small.copy(fontSize = 11.sp, color = C.faint),
                        maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Text("›", style = T.title.copy(color = C.faint), modifier = Modifier.padding(start = 6.dp))
        }
    }
}

private fun scoreBite(score: Int) = when {
    score >= 60 -> Bite.GREAT
    score >= 35 -> Bite.OK
    else -> Bite.SLOW
}

/** A thin bar for the trip with a dot where each fish came. */
@Composable
private fun MiniTimeline(trip: Trip, modifier: Modifier) {
    Canvas(modifier) {
        val t0 = trip.start
        val t1 = (trip.end ?: t0).coerceAtLeast(t0 + 60_000L)
        val y = size.height / 2
        drawLine(C.line, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), cap = StrokeCap.Round)
        trip.catches.forEach { c ->
            val x = ((c.time - t0).toFloat() / (t1 - t0)).coerceIn(0f, 1f) * size.width
            drawCircle(C.brass, 3.5.dp.toPx(), Offset(x, y))
        }
    }
}

/** "Trip deleted · Undo", for six seconds after a delete (the timer runs in Repo). */
@Composable
fun UndoBar(deleted: Trip?, modifier: Modifier = Modifier) {
    val view = LocalView.current
    AnimatedVisibility(deleted != null, modifier = modifier, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), glow = C.brass, onClick = {
            Haptics.confirm(view)
            Repo.undoDelete()
        }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Trip deleted", "Ferð eytt"), style = T.body, modifier = Modifier.weight(1f))
                Text(t("Undo", "Afturkalla"), style = T.heading.copy(color = C.brass))
            }
        }
    }
}

// ---------------------------------------------------------------- collection

/**
 * All the fish, three across. Caught ones fill in with his photo (from a trip with that fish),
 * how many and his biggest; the rest stay as grey outlines to aim for.
 */
@Composable
private fun Collection(done: List<Trip>) {
    val all = Fish.sea + Fish.lake
    val caught = done.flatMap { tr -> tr.catches.map { it to tr } }.groupBy { it.first.species }
    GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("${caught.size} of ${all.size} caught", "${caught.size} af ${all.size} veiddir"), style = T.heading, modifier = Modifier.weight(1f))
            Bar(caught.size / all.size.toFloat(), C.brass, Modifier.width(90.dp).height(6.dp))
        }
        Spacer(Modifier.height(12.dp))
        all.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { f -> FishTile(f, caught[f.id].orEmpty(), Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun FishTile(f: Species, mine: List<Pair<Catch, Trip>>, modifier: Modifier) {
    val got = mine.isNotEmpty()
    val photo = mine.map { it.second }.firstOrNull { it.photos.isNotEmpty() }?.photos?.firstOrNull()
    val img by rememberPhoto(photo, 240)
    val biggest = mine.mapNotNull { it.first.sizeCm }.maxOrNull()
    val first = mine.minOfOrNull { it.first.time }
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(if (got) C.brass.copy(alpha = 0.12f) else C.line.copy(alpha = 0.5f))
            .border(1.dp, if (got) C.brass.copy(alpha = 0.6f) else C.line, RoundedCornerShape(16.dp))
            .clickable { Tips.explain("${f.name} (${f.other}). ${f.fact}\n\n" + t("Try: ", "Prófaðu: ") + f.bait) }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.3f).clip(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            val pic = img
            if (pic != null) Image(pic, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else FishGlyph(if (got) C.brass else C.faint.copy(alpha = 0.5f), Modifier.fillMaxSize().padding(8.dp))
        }
        Spacer(Modifier.height(6.dp))
        FitText(f.name, T.small.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (got) C.foam else C.faint), min = 9.sp)
        FitText(
            when {
                !got -> t("not yet", "ekki enn")
                biggest != null -> "${mine.size}× · $biggest cm"
                else -> "${mine.size}× · " + (first?.let { tripDay(it) } ?: "")
            },
            T.small.copy(fontSize = 10.sp, color = C.faint),
            min = 8.sp,
        )
    }
}

/** A simple fish outline: body, tail, eye. */
@Composable
fun FishGlyph(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cy = h / 2
        val body = Path().apply {
            moveTo(w * 0.08f, cy)
            quadraticTo(w * 0.38f, cy - h * 0.36f, w * 0.72f, cy)
            quadraticTo(w * 0.38f, cy + h * 0.36f, w * 0.08f, cy)
            close()
        }
        val tail = Path().apply {
            moveTo(w * 0.70f, cy)
            lineTo(w * 0.94f, cy - h * 0.2f)
            lineTo(w * 0.94f, cy + h * 0.2f)
            close()
        }
        drawPath(body, color, style = Stroke(2.dp.toPx()))
        drawPath(tail, color, style = Stroke(2.dp.toPx()))
        drawCircle(color, 2.dp.toPx(), Offset(w * 0.22f, cy - h * 0.05f))
    }
}

// ---------------------------------------------------------------- insights

/**
 * What it adds up to: whether Afli's forecasts hold up for him, his best conditions, personal
 * bests, and what Afli has learned at this spot.
 */
@Composable
private fun Insights(s: UiState, done: List<Trip>) {
    val withSnap = done.filter { it.snapshot != null }
    val great = withSnap.filter { it.snapshot!!.score >= 60 }
    val slow = withSnap.filter { it.snapshot!!.score < 35 }
    val gr = Learn.rate(great)
    val sr = Learn.rate(slow)
    GlassCard(Modifier.fillMaxWidth()) {
        Text(t("DOES THE FORECAST WORK FOR YOU?", "VIRKAR SPÁIN FYRIR ÞIG?"), style = T.label.copy(fontSize = 11.sp))
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                great.isEmpty() || slow.isEmpty() || gr.isNaN() || sr.isNaN() -> t("After a few trips on both Great and Slow forecasts, this compares how you did.", "Eftir nokkrar ferðir bæði á frábærum og rólegum spám sýnir þetta hvernig þér gekk.")
                sr <= 0.01 -> t("Great forecasts gave you ${fmt(gr, 1)} fish an hour; Slow ones gave you none.", "Frábærar spár gáfu þér ${fmt(gr, 1)} fiska á klst.; rólegar engan.")
                else -> t("Great forecasts gave you ${fmt(gr, 1)} fish an hour, Slow ones ${fmt(sr, 1)} (${fmt(gr / sr, 1)}×).", "Frábærar spár gáfu þér ${fmt(gr, 1)} fiska á klst., rólegar ${fmt(sr, 1)} (${fmt(gr / sr, 1)}×).")
            },
            style = T.body,
        )
        Text(t("Trips started on a Great forecast: ${great.size} · on a Slow one: ${slow.size}", "Ferðir á frábærri spá: ${great.size} · á rólegri spá: ${slow.size}"), style = T.small.copy(fontSize = 11.sp, color = C.faint))
    }

    if (withSnap.size >= 3) {
        val tideGroups = listOf(
            t("Moving tide", "Straumur") to withSnap.filter { it.snapshot!!.tideFlow >= 0.6 },
            t("Slack tide", "Liggjandi") to withSnap.filter { it.snapshot!!.tideFlow <= 0.25 },
        )
        val lightGroups = listOf(
            t("Dawn/dusk", "Ljósaskipti") to withSnap.filter { it.snapshot!!.sunElevation in -6.0..8.0 },
            t("Daylight", "Dagsbirta") to withSnap.filter { it.snapshot!!.sunElevation > 8.0 },
            t("Dark", "Myrkur") to withSnap.filter { it.snapshot!!.sunElevation < -6.0 },
        )
        val windGroups = listOf(
            t("Light wind", "Hægur vindur") to withSnap.filter { it.snapshot!!.wind < 5 },
            t("Fresh wind", "Strekkingur") to withSnap.filter { it.snapshot!!.wind in 5.0..10.0 },
            t("Strong wind", "Hvasst") to withSnap.filter { it.snapshot!!.wind > 10 },
        )
        val parts = listOfNotNull(tideGroups.bestOf(), lightGroups.bestOf(), windGroups.bestOf())
        if (parts.isNotEmpty()) GlassCard(Modifier.fillMaxWidth()) {
            Text(t("YOUR BEST CONDITIONS", "BESTU AÐSTÆÐURNAR ÞÍNAR"), style = T.label.copy(fontSize = 11.sp))
            Spacer(Modifier.height(6.dp))
            Text(parts.joinToString(" · "), style = T.title.copy(fontSize = 19.sp))
            Text(t("Fish per hour on your own trips, by how things were when each started (trips on the right).", "Fiskar á klst. í þínum ferðum, eftir aðstæðum í upphafi hverrar (fjöldi ferða til hægri)."), style = T.small.copy(fontSize = 11.sp, color = C.faint))
            Spacer(Modifier.height(10.dp))
            RateBars(tideGroups + lightGroups + windGroups)
        }
    }

    val bests = done.flatMap { it.catches }.filter { it.sizeCm != null }.groupBy { it.species }.mapValues { e -> e.value.maxOf { it.sizeCm!! } }
    if (bests.isNotEmpty()) GlassCard(Modifier.fillMaxWidth()) {
        Text(t("PERSONAL BESTS", "PERSÓNULEG MET"), style = T.label.copy(fontSize = 11.sp))
        Spacer(Modifier.height(8.dp))
        bests.entries.sortedByDescending { it.value }.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                row.forEach { (id, cm) ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        FitText(Fish.byId(id)?.name ?: id, T.small.copy(color = C.foam), Modifier.weight(1f), min = 10.sp)
                        Text("$cm cm", style = T.number.copy(color = C.brass, fontSize = 14.sp), modifier = Modifier.padding(end = 10.dp))
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    s.spot?.let { sp ->
        val boost = Learn.boost(sp, done)
        val up = boost.filter { it.value >= 1.05 }.keys.mapNotNull { Fish.byId(it)?.name }
        Text(
            when {
                boost.isEmpty() -> t("After 3 fish at a spot, Afli starts nudging the fish there toward what you actually catch.", "Eftir 3 fiska á stað fer Afli að laga fiskana þar að því sem þú veiðir í raun.")
                up.isEmpty() -> t("At ${sp.label}, your catches match what Afli expected.", "Við ${sp.label} passar aflinn þinn við það sem Afli bjóst við.")
                else -> t("Learned at ${sp.label}: you catch ${up.joinToString(", ")} here more than average, so they score a little higher.", "Lært við ${sp.label}: þú veiðir ${up.joinToString(", ")} oftar en gengur og gerist hér, svo þeir fá aðeins hærri líkur.")
            },
            style = T.small.copy(color = C.faint),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** The best group by fish per hour, if it has a trip and beats zero. */
private fun List<Pair<String, List<Trip>>>.bestOf(): String? =
    map { (k, v) -> k to Learn.rate(v) }.filter { !it.second.isNaN() && it.second > 0 }.maxByOrNull { it.second }?.first

@Composable
private fun RateBars(groups: List<Pair<String, List<Trip>>>) {
    val rows = groups.map { (k, v) -> Triple(k, v.size, Learn.rate(v)) }.filter { it.second >= 1 && !it.third.isNaN() }
    val top = rows.maxOfOrNull { it.third }?.coerceAtLeast(0.1) ?: 1.0
    rows.forEach { (k, n, r) ->
        Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
            FitText(k, T.small.copy(fontSize = 12.sp), Modifier.width(104.dp), min = 9.sp)
            Bar((r / top).toFloat(), C.brass, Modifier.weight(1f).height(8.dp))
            Text("${fmt(r, 1)}/" + t("h", "klst."), style = T.small.copy(fontSize = 11.sp, color = C.foam), maxLines = 1, softWrap = false, modifier = Modifier.width(64.dp).padding(start = 8.dp))
            Text("$n", style = T.small.copy(fontSize = 11.sp, color = C.faint), maxLines = 1, modifier = Modifier.width(22.dp), textAlign = TextAlign.End)
        }
    }
}

/** "Today", "Tomorrow", a day name this week, or a date ("12 Oct" / "12. okt.") further back. */
fun tripDay(ms: Long): String {
    val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    return if (ChronoUnit.DAYS.between(d, LocalDate.now()) <= 6) dayWord(ms)
    else DateTimeFormatter.ofPattern(if (app.afli.L.isl) "d. MMM" else "d MMM", app.afli.L.locale).format(d)
}

fun tripCount(n: Int) = t(if (n == 1) "1 trip" else "$n trips", if (oneIs(n)) "$n ferð" else "$n ferðir")

/** Icelandic uses the singular for numbers ending in 1, except 11 (1, 21, 31… fiskur). */
private fun oneIs(n: Int) = n % 10 == 1 && n % 100 != 11
