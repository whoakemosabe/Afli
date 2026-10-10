package app.afli.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.afli.data.Repo
import app.afli.data.Store
import app.afli.data.UiState
import app.afli.model.Learn
import app.afli.model.Spot
import app.afli.model.Water
import app.afli.t
import kotlin.math.atan2
import kotlin.math.cos

/**
 * Every spot: how far away and which way it is, how many trips and fish he's had there. Show it,
 * rename it, open it in a maps app, or delete it (built-in spots reset instead).
 */
@Composable
fun SpotsContent(s: UiState, onShow: (Spot) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(t("Your spots", "Staðirnir þínir"), style = T.title)
        Text(
            t("Spots are saved when you start a trip somewhere new. Rename them, open them in a maps app, or delete the ones you don't need.", "Staðir vistast þegar þú byrjar ferð á nýjum stað. Endurnefndu þá, opnaðu í kortaappi eða eyddu þeim sem þú þarft ekki."),
            style = T.small,
        )
        s.spots.forEach { sp -> SpotRow(sp, s, onShow) }
    }
}

@Composable
private fun SpotRow(sp: Spot, s: UiState, onShow: (Spot) -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var editing by remember(sp.id) { mutableStateOf(false) }
    var name by remember(sp.id, sp.name) { mutableStateOf(sp.label) }
    var confirm by remember(sp.id) { mutableStateOf(false) }
    val trips = Learn.tripsAt(sp, s.trips)
    val fish = trips.sumOf { it.catches.size }
    val gps = s.gps
    GlassCard(Modifier.fillMaxWidth(), glow = if (s.spot?.id == sp.id) C.brass else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (editing) {
                    Box(Modifier.fillMaxWidth().border(1.dp, C.brass.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                        BasicTextField(name, onValueChange = { name = it.take(40) }, singleLine = true, textStyle = T.heading, cursorBrush = SolidColor(C.brass))
                    }
                } else {
                    FitText(sp.label, T.heading, min = 10.sp)
                }
                Text(
                    listOfNotNull(
                        if (sp.water == Water.LAKE) t("Lake", "Vatn") else if (sp.sheltered) t("Harbour", "Höfn") else t("Open coast", "Opin strönd"),
                        gps?.let {
                            val km = Store.distanceM(it.latitude, it.longitude, sp.lat, sp.lon) / 1000
                            val bearing = Math.toDegrees(atan2((sp.lon - it.longitude) * cos(Math.toRadians(it.latitude)), sp.lat - it.latitude))
                            "${fmt(km, if (km < 10) 1 else 0)} km ${compass((bearing + 360) % 360)}"
                        },
                        if (trips.isEmpty()) t("no trips yet", "engar ferðir enn") else tripCount(trips.size) + ", " + fishCount(fish),
                    ).joinToString(" · "),
                    style = T.small.copy(fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (editing) {
                GlassChip(t("Save name", "Vista nafn"), selected = true, onClick = {
                    Repo.renameSpot(sp.id, name)
                    Haptics.confirm(view)
                    editing = false
                })
                GlassChip(t("Cancel", "Hætta við"), onClick = { name = sp.label; editing = false })
            } else {
                GlassChip(t("Show", "Sýna"), onClick = { onShow(sp) })
                GlassChip(t("Rename", "Endurnefna"), onClick = { editing = true })
                GlassChip(t("Map", "Kort"), onClick = {
                    val label = Uri.encode(sp.label)
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${sp.lat},${sp.lon}?q=${sp.lat},${sp.lon}($label)"))) }
                })
            }
        }
        if (!editing) {
            Spacer(Modifier.height(8.dp))
            val builtIn = sp.builtIn
            Text(
                if (confirm) (if (builtIn) t("Tap again to reset", "Ýttu aftur til að endurstilla") else t("Tap again to delete", "Ýttu aftur til að eyða"))
                else (if (builtIn) t("Reset to built-in", "Endurstilla") else t("Delete spot", "Eyða stað")),
                style = T.small.copy(color = C.bad),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .let { m ->
                        m.then(
                            Modifier.pressable(rememberPress(), null, scaleBy = 0.04f) {
                                if (confirm) {
                                    Repo.deleteSpot(context, sp.id)
                                    confirm = false
                                } else confirm = true
                            },
                        )
                    },
            )
        }
    }
}
