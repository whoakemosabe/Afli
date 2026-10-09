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
import kotlinx.coroutines.delay

/**
 * The trip log. One big button starts a trip where he's standing; while fishing, one tap per
 * fish logs a catch. Empty trips count: they teach Afli when fish don't bite.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogScreen(s: UiState, top: Dp, onStart: () -> Unit, onCatch: (String) -> Unit, onUndo: () -> Unit, onEnd: () -> Unit, onDelete: (String) -> Unit) {
    val view = LocalView.current
    LaunchedEffect(Unit) {
        Tips.maybeTour(
            "log",
            listOf(
                Tips.Step("start", "Log every trip", "Tap Start fishing when you get to the water. Log empty trips too; that's how Afli learns what works."),
            ),
        )
    }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(top = top).padding(horizontal = 16.dp),
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
                    Text("Going fishing?", style = T.title)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        s.spot?.let { "Start a trip at ${it.name}. Afli saves the conditions so it can learn." }
                            ?: "Start a trip where you're standing.",
                        style = T.small,
                    )
                    Spacer(Modifier.height(14.dp))
                    GlassButton(
                        "Start fishing",
                        accent = C.brass,
                        explain = "Starts a trip at your GPS position and saves the tide, wind, pressure and sea temperature right now.",
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
            SectionLabel("Your trips")
            past.forEach { TripCard(it, onDelete) }
        } else if (s.activeTrip == null) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("No trips yet", style = T.heading)
                Text("Go to the harbour and tap Start fishing. After 20 or so trips, Afli starts to learn your spots.", style = T.small)
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
        Text("Fishing at ${trip.spotName}", style = T.small)
        Text(if (mins < 60) "$mins min" else "${mins / 60} h ${mins % 60} min", style = T.hero.copy(fontSize = T.title.fontSize * 2))
        Text("${trip.catches.size} caught", style = T.title.copy(color = if (trip.catches.isEmpty()) C.mist else C.good))
        Spacer(Modifier.height(14.dp))
        SectionLabel("Caught one? Tap the fish")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Fish.forWater(water).forEach { f ->
                val n = trip.catches.count { it.species == f.id }
                GlassChip(
                    text = if (n > 0) "${f.en} ×$n" else f.en,
                    selected = n > 0,
                    explain = "${f.en} (${f.icelandic}). ${f.fact}",
                    onClick = {
                        Haptics.confirm(view)
                        onCatch(f.id)
                    },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassButton("End trip", accent = C.brass, onClick = {
                Haptics.confirm(view)
                onEnd()
            })
            if (trip.catches.isNotEmpty()) GlassButton("Undo last", style = T.small, onClick = onUndo)
        }
    }
}

@Composable
private fun TripCard(t: Trip, onDelete: (String) -> Unit) {
    var confirm by remember(t.id) { mutableStateOf(false) }
    val mins = (((t.end ?: t.start) - t.start) / 60_000).toInt()
    GlassCard(Modifier.fillMaxWidth(), explain = "Long-press shows this. Tap to delete the trip.", onClick = { confirm = !confirm }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.spotName, style = T.heading)
                Text("${dayClock(t.start)} · ${if (mins < 60) "$mins min" else "${mins / 60} h ${mins % 60} min"}", style = T.small)
            }
            Text(if (t.catches.isEmpty()) "Blank" else "${t.catches.size} fish", style = T.heading.copy(color = if (t.catches.isEmpty()) C.mist else C.good))
        }
        if (t.catches.isNotEmpty()) {
            Text(
                t.catches.groupBy { it.species }.entries.joinToString(" · ") { (id, c) -> "${Fish.byId(id)?.en ?: id} ×${c.size}" },
                style = T.small,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        t.snapshot?.let { Text("Score when you started: ${it.score}", style = T.small.copy(color = C.faint)) }
        if (confirm) {
            Spacer(Modifier.height(10.dp))
            GlassButton("Delete this trip", accent = C.bad, style = T.small, onClick = { onDelete(t.id) })
        }
    }
}
