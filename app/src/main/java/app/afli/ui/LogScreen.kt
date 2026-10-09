package app.afli.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
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
fun LogScreen(s: UiState, top: Dp, scroll: ScrollState, onStart: () -> Unit, onCatch: (String) -> Unit, onUndo: () -> Unit, onEnd: () -> Unit, onDelete: (String) -> Unit) {
    val view = LocalView.current
    LaunchedEffect(Unit) {
        Tips.maybeTour(
            "log",
            listOf(
                Tips.Step("start", t("Log every trip", "Skráðu hverja ferð"), t("Tap Start fishing when you get to the water. Log empty trips too; that's how Afli learns what works.", "Ýttu á Byrja að veiða þegar þú mætir á staðinn. Skráðu líka ferðir þar sem ekkert veiddist; þannig lærir Afli hvað virkar.")),
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
                        s.spot?.let { t("Start a trip at ${it.label}. Afli saves the conditions so it can learn.", "Byrjaðu ferð á þessum stað (${it.label}). Afli vistar aðstæðurnar svo hann geti lært.") }
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

        val past = s.trips.filter { it.end != null }
        if (past.isNotEmpty()) {
            SectionLabel(t("Your trips", "Ferðirnar þínar"))
            past.forEach { TripCard(it, onDelete) }
        } else if (s.activeTrip == null) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t("No trips yet", "Engar ferðir enn"), style = T.heading)
                Text(t("Go to the harbour and tap Start fishing. After 20 or so trips, Afli starts to learn your spots.", "Farðu niður á bryggju og ýttu á Byrja að veiða. Eftir um 20 ferðir fer Afli að læra á staðina þína."), style = T.small)
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Fish.forWater(water).forEach { f ->
                val n = trip.catches.count { it.species == f.id }
                GlassChip(
                    text = if (n > 0) "${f.name} ×$n" else f.name,
                    selected = n > 0,
                    explain = "${f.name} (${f.other}). ${f.fact}",
                    onClick = {
                        Haptics.confirm(view)
                        onCatch(f.id)
                    },
                )
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
private fun TripCard(trip: Trip, onDelete: (String) -> Unit) {
    var confirm by remember(trip.id) { mutableStateOf(false) }
    val mins = (((trip.end ?: trip.start) - trip.start) / 60_000).toInt()
    GlassCard(Modifier.fillMaxWidth(), explain = t("Tap a trip to show the delete button.", "Ýttu á ferð til að sjá eyða-hnappinn."), onClick = { confirm = !confirm }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(spotLabel(trip.spotId, trip.spotName), style = T.heading)
                Text("${dayWord(trip.start)} ${clock(trip.start)} · ${duration(mins)}", style = T.small)
            }
            Text(if (trip.catches.isEmpty()) t("Blank", "Ekkert") else t("${trip.catches.size} fish", if (oneIs(trip.catches.size)) "${trip.catches.size} fiskur" else "${trip.catches.size} fiskar"), style = T.heading.copy(color = if (trip.catches.isEmpty()) C.mist else C.good))
        }
        if (trip.catches.isNotEmpty()) {
            Text(
                trip.catches.groupBy { it.species }.entries.joinToString(" · ") { (id, c) -> "${Fish.byId(id)?.name ?: id} ×${c.size}" },
                style = T.small,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        trip.snapshot?.let { Text(t("Score when you started: ", "Líkur í byrjun ferðar: ") + it.score, style = T.small.copy(color = C.faint)) }
        if (confirm) {
            Spacer(Modifier.height(10.dp))
            GlassButton(t("Delete this trip", "Eyða þessari ferð"), accent = C.bad, style = T.small, onClick = { onDelete(trip.id) })
        }
    }
}

/** Icelandic uses the singular for numbers ending in 1, except 11 (1, 21, 31… fiskur). */
private fun oneIs(n: Int) = n % 10 == 1 && n % 100 != 11
