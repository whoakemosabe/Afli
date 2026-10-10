package app.afli.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.afli.data.Trip
import androidx.compose.ui.unit.sp
import app.afli.model.Learn
import app.afli.data.Repo
import app.afli.data.UiState
import app.afli.model.Fish
import app.afli.model.Water
import app.afli.t
import kotlinx.coroutines.delay

/**
 * The trip log. One big button starts a trip where he's standing; while fishing, one tap per
 * fish logs a catch. Empty trips count: they teach Afli when fish don't bite.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogScreen(s: UiState, top: Dp, scroll: ScrollState, onStart: () -> Unit, onCatch: (String) -> Unit, onUndo: () -> Unit, onEnd: () -> Unit, onOpenTrip: (String) -> Unit) {
    val view = LocalView.current
    LaunchedEffect(Unit) {
        Tips.maybeTour(
            "log",
            listOf(
                Tips.Step("start", t("Log every trip", "Skráðu hverja ferð"), t("Tap Start fishing when you get to the water. Log empty trips too; they show when fish don't bite.", "Ýttu á Byrja að veiða þegar þú mætir á staðinn. Skráðu líka ferðir þar sem ekkert veiddist; þær sýna hvenær fiskurinn tekur ekki.")),
            ),
        )
    }
    Column(
        Modifier.verticalScroll(scroll).padding(top = top).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AnimatedContent(
            s.activeTrip,
            transitionSpec = { (fadeIn(tween(250)) + scaleIn(tween(300), initialScale = 0.97f)) togetherWith fadeOut(tween(150)) },
            contentKey = { it?.id },
            label = "trip",
        ) { trip ->
            if (trip == null) {
                GlassCard(Modifier.fillMaxWidth().coachTarget("start")) {
                    Text(t("Going fishing?", "Ertu að fara að veiða?"), style = T.title)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        s.spot?.let { t("Start a trip at ${it.label}. Afli saves the conditions with your catch.", "Byrjaðu ferð á þessum stað (${it.label}). Afli vistar aðstæðurnar með aflanum.") }
                            ?: t("Start a trip where you're standing.", "Byrjaðu ferð þar sem þú stendur."),
                        style = T.small,
                    )
                    Spacer(Modifier.height(14.dp))
                    GlassButton(
                        t("Start fishing", "Byrja að veiða"),
                        accent = C.brass,
                        explain = t("Starts a trip at your GPS position and saves the tide, wind, pressure and sea temperature right now.", "Byrjar ferð þar sem GPS segir að þú sért og vistar sjávarföll, vind, loftþrýsting og sjávarhita eins og þau eru núna."),
                        onClick = {
                            Haptics.confirm(view)
                            onStart()
                        },
                    )
                }
            } else {
                ActiveTrip(trip, s.spot?.water ?: Water.SEA, onCatch, onUndo, onEnd)
            }
        }

        // Undo, for a few seconds after a trip is deleted.
        UndoBar(s.lastDeleted)

        val past = s.trips.filter { it.end != null }
        if (past.isNotEmpty()) {
            StatsCard(s)
            SectionLabel(t("Your trips", "Ferðirnar þínar"))
            past.forEach { TripCard(it, onOpenTrip) }
        } else if (s.activeTrip == null) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t("No trips yet", "Engar ferðir enn"), style = T.heading)
                Text(t("Go to the harbour and tap Start fishing. Each trip keeps the tide, wind and score it started in, so you can see what worked.", "Farðu niður á bryggju og ýttu á Byrja að veiða. Hver ferð geymir sjávarföll, vind og tökulíkur eins og þau voru í upphafi, svo þú sjáir hvað virkaði."), style = T.small)
            }
        }
        BottomBarSpace()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveTrip(trip: Trip, water: Water, onCatch: (String) -> Unit, onUndo: () -> Unit, onEnd: () -> Unit) {
    val view = LocalView.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(trip.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val mins = ((now - trip.start) / 60_000).toInt()
    GlassCard(Modifier.fillMaxWidth()) {
        Text(t("Fishing at ", "Á veiðum: ") + spotLabel(trip.spotId, trip.spotName), style = T.small)
        Text(duration(mins), style = T.hero.copy(fontSize = T.title.fontSize * 2))
        Text(t("${trip.catches.size} caught", if (oneIs(trip.catches.size)) "${trip.catches.size} veiddur" else "${trip.catches.size} veiddir"), style = T.title.copy(color = if (trip.catches.isEmpty()) C.mist else C.good))
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

/** One fish to tap while fishing: its name on the left, the count in a fixed slot on the right. */
@Composable
private fun CatchButton(name: String, n: Int, explain: String, modifier: Modifier, onClick: () -> Unit) {
    val backdrop = LocalBackdrop.current
    val press = rememberPress()
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
    val tint = if (n > 0) androidx.compose.ui.graphics.Color(0x55D8B56A) else androidx.compose.ui.graphics.Color(0x2605080F)
    val m = if (backdrop != null) modifier.glassControl(backdrop, shape, press = press.amount, tint = tint, layer = press.squishLayer(0.06f))
    else modifier.squish(press, 0.06f).background(tint, shape)
    Row(
        m.pressable(press, explain, scaleBy = 0f, haptic = false, onClick = onClick).height(44.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FitText(name, T.small.copy(color = if (n > 0) C.brass else C.foam), Modifier.weight(1f), min = 10.sp)
        Text(if (n > 0) "×$n" else "", style = T.number.copy(color = C.brass), maxLines = 1, softWrap = false, modifier = Modifier.width(32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun TripCard(trip: Trip, onOpen: (String) -> Unit) {
    val mins = (((trip.end ?: trip.start) - trip.start) / 60_000).toInt()
    GlassCard(Modifier.fillMaxWidth(), explain = t("Tap to see the trip, set fish sizes, add a note or photos.", "Ýttu til að sjá ferðina, skrá stærð fiska, bæta við athugasemd eða myndum."), onClick = { onOpen(trip.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FitText(spotLabel(trip.spotId, trip.spotName), T.heading, min = 10.sp)
                Text("${dayWord(trip.start)} ${clock(trip.start)} · ${duration(mins)}", style = T.small, maxLines = 1)
            }
            Text(if (trip.catches.isEmpty()) t("Blank", "Ekkert") else t("${trip.catches.size} fish", if (oneIs(trip.catches.size)) "${trip.catches.size} fiskur" else "${trip.catches.size} fiskar"), style = T.heading.copy(color = if (trip.catches.isEmpty()) C.mist else C.good))
            Text("  ›", style = T.heading.copy(color = C.faint))
        }
        if (trip.catches.isNotEmpty()) {
            Text(
                trip.catches.groupBy { it.species }.entries.joinToString(" · ") { (id, c) ->
                    val sizes = c.mapNotNull { it.sizeCm }
                    "${Fish.byId(id)?.name ?: id} ×${c.size}" + if (sizes.isNotEmpty()) " (${t("biggest", "stærstur")} ${sizes.max()} cm)" else ""
                },
                style = T.small,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        val extras = listOfNotNull(
            trip.snapshot?.let { t("Score at start ", "Líkur í byrjun ") + it.score },
            if (trip.photos.isNotEmpty()) t("${trip.photos.size} photos", "${trip.photos.size} myndir") else null,
            if (trip.note.isNotBlank()) "“${trip.note.take(40)}${if (trip.note.length > 40) "…" else ""}”" else null,
        )
        if (extras.isNotEmpty()) FitText(extras.joinToString(" · "), T.small.copy(color = C.faint), min = 10.sp)
    }
}

/** "Trip deleted · Undo", for six seconds after a delete. */
@Composable
private fun UndoBar(deleted: Trip?) {
    val view = LocalView.current
    LaunchedEffect(deleted?.id) {
        if (deleted != null) {
            delay(6_000)
            Repo.forgetDeleted()
        }
    }
    androidx.compose.animation.AnimatedVisibility(
        deleted != null,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
    ) {
        GlassCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp), onClick = {
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

/**
 * What the log adds up to: trips, hours, fish and fish per hour; the best spot and top fish;
 * and when he's done best by tide, light and the score he started with. Plus what Afli has
 * learned at the current spot.
 */
@Composable
private fun StatsCard(s: UiState) {
    val done = s.trips.filter { it.end != null }
    val hours = done.sumOf { ((it.end ?: it.start) - it.start) / 3_600_000.0 }
    val fish = done.sumOf { it.catches.size }
    val rate = Learn.rate(done)
    GlassCard(Modifier.fillMaxWidth()) {
        SectionLabel(t("Your fishing", "Veiðin þín"))
        Row(Modifier.fillMaxWidth()) {
            Big(t("Trips", "Ferðir"), "${done.size}", Modifier.weight(1f))
            Big(t("Hours", "Tímar"), fmt(hours, if (hours < 10) 1 else 0), Modifier.weight(1f))
            Big(t("Fish", "Fiskar"), "$fish", Modifier.weight(1f))
            Big(t("Per hour", "Á tíma"), fmt(rate, 1), Modifier.weight(1f))
        }
        val bySpot = done.groupBy { it.spotId }.filter { it.value.size >= 2 }
            .mapValues { Learn.rate(it.value) }.filter { !it.value.isNaN() }.maxByOrNull { it.value }
        val topFish = done.flatMap { it.catches }.groupBy { it.species }.maxByOrNull { it.value.size }
        val biggest = done.flatMap { it.catches }.filter { it.sizeCm != null }.maxByOrNull { it.sizeCm!! }
        Spacer(Modifier.height(10.dp))
        bySpot?.let { (id, r) ->
            val name = done.first { it.spotId == id }.let { spotLabel(it.spotId, it.spotName) }
            StatLine(t("Best spot", "Besti staður"), "$name · ${fmt(r, 1)} " + t("fish/h", "fiskar/klst."))
        }
        topFish?.let { (id, c) -> StatLine(t("Most caught", "Mest veitt"), "${Fish.byId(id)?.name ?: id} ×${c.size}") }
        biggest?.let { StatLine(t("Biggest", "Stærstur"), "${Fish.byId(it.species)?.name ?: it.species} ${it.sizeCm} cm") }

        // When it's gone best: fish per hour by condition at the start of each trip.
        val withSnap = done.filter { it.snapshot != null }
        if (withSnap.size >= 3) {
            Spacer(Modifier.height(10.dp))
            Text(t("WHEN YOU CATCH MOST", "HVENÆR ÞÚ VEIÐIR MEST"), style = T.label.copy(fontSize = 11.sp))
            Spacer(Modifier.height(6.dp))
            val groups = listOf(
                t("Tide moving", "Sjór á hreyfingu") to withSnap.filter { (it.snapshot!!.tideFlow) >= 0.6 },
                t("Slack tide", "Liggjandi") to withSnap.filter { (it.snapshot!!.tideFlow) <= 0.25 },
                t("Dawn or dusk", "Ljósaskipti") to withSnap.filter { it.snapshot!!.sunElevation in -6.0..8.0 },
                t("Daylight", "Dagsbirta") to withSnap.filter { it.snapshot!!.sunElevation > 8.0 },
                t("Dark", "Myrkur") to withSnap.filter { it.snapshot!!.sunElevation < -6.0 },
                t("Score 60+", "Líkur 60+") to withSnap.filter { it.snapshot!!.score >= 60 },
                t("Score under 35", "Líkur undir 35") to withSnap.filter { it.snapshot!!.score < 35 },
            ).map { (k, v) -> Triple(k, v.size, Learn.rate(v)) }.filter { it.second >= 1 && !it.third.isNaN() }
            val top = groups.maxOfOrNull { it.third }?.coerceAtLeast(0.1) ?: 1.0
            groups.forEach { (k, n, r) ->
                Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(k, style = T.small.copy(fontSize = 12.sp), maxLines = 1, modifier = Modifier.width(132.dp))
                    Bar((r / top).toFloat(), C.brass, Modifier.weight(1f).height(6.dp))
                    Text("${fmt(r, 1)}/" + t("h", "klst.") + " · $n", style = T.small.copy(fontSize = 11.sp, color = C.faint), maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 8.dp))
                }
            }
            Text(t("Fish per hour, by how things were when each trip started (number of trips after the dot).", "Fiskar á klukkutíma eftir aðstæðum í upphafi hverrar ferðar (fjöldi ferða á eftir punktinum)."), style = T.small.copy(fontSize = 11.sp, color = C.faint), modifier = Modifier.padding(top = 4.dp))
        }

        // What Afli has picked up at the spot on screen.
        s.spot?.let { sp ->
            val boost = Learn.boost(sp, done)
            if (boost.isNotEmpty()) {
                val up = boost.filter { it.value >= 1.05 }.keys.mapNotNull { Fish.byId(it)?.name }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (up.isEmpty()) t("At ${sp.label}, your catches match what Afli expected.", "Við ${sp.label} passar aflinn þinn við það sem Afli bjóst við.")
                    else t("Learned at ${sp.label}: you catch ${up.joinToString(", ")} here more than average, so they score a little higher.", "Lært við ${sp.label}: þú veiðir ${up.joinToString(", ")} oftar en gengur og gerist hér, svo þeir fá aðeins hærri líkur."),
                    style = T.small.copy(color = C.foam),
                )
            } else {
                Spacer(Modifier.height(10.dp))
                Text(t("After 3 fish at a spot, Afli starts nudging the fish there toward what you actually catch.", "Eftir 3 fiska á stað fer Afli að laga fiskana þar að því sem þú veiðir í raun."), style = T.small.copy(color = C.faint))
            }
        }
    }
}

@Composable
private fun Big(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = T.title.copy(fontSize = 22.sp), maxLines = 1, softWrap = false)
        Text(label, style = T.small.copy(fontSize = 11.sp, color = C.faint), maxLines = 1)
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label.uppercase(app.afli.L.locale), style = T.label.copy(fontSize = 10.sp), maxLines = 1, modifier = Modifier.width(110.dp).padding(top = 2.dp))
        Text(value, style = T.small.copy(color = C.foam), maxLines = 2)
    }
}

/** Icelandic uses the singular for numbers ending in 1, except 11 (1, 21, 31… fiskur). */
private fun oneIs(n: Int) = n % 10 == 1 && n % 100 != 11
