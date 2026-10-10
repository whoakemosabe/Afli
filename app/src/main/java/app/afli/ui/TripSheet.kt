package app.afli.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.afli.data.Catch
import app.afli.data.Repo
import app.afli.data.Trip
import app.afli.model.Fish
import app.afli.model.Water
import app.afli.t

/** Typical shore sizes (cm), used as the starting point when he first sets a fish's size. */
private val typicalCm = mapOf(
    "ufsi" to 30, "thorskur" to 40, "makrill" to 32, "marhnutur" to 20, "ysa" to 40,
    "skarkoli" to 28, "steinbitur" to 60, "bleikja" to 30, "urridi" to 35,
)

/**
 * One trip, opened from the log: the conditions it started in, every fish with its size (tap −
 * or + to set it), fish he forgot to log, a note, photos, and delete (with undo in the log).
 */
@Composable
fun TripContent(trip: Trip, onClose: () -> Unit) {
    val view = LocalView.current
    var confirmDelete by remember(trip.id) { mutableStateOf(false) }
    var adding by remember(trip.id) { mutableStateOf(false) }
    var note by remember(trip.id) { mutableStateOf(trip.note) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) {
            Repo.addPhoto(trip, bmp)
            Haptics.confirm(view)
        }
    }
    val mins = (((trip.end ?: System.currentTimeMillis()) - trip.start) / 60_000).toInt()
    val water = Fish.byId(trip.catches.firstOrNull()?.species ?: "")?.water
        ?: if (Repo.state.value.spots.firstOrNull { it.id == trip.spotId }?.water == Water.LAKE) Water.LAKE else Water.SEA

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            Text(spotLabel(trip.spotId, trip.spotName), style = T.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${dayWord(trip.start)} ${clock(trip.start)} · ${duration(mins)}", style = T.small)
        }

        trip.snapshot?.let { s ->
            GlassCard(Modifier.fillMaxWidth()) {
                SectionLabel(t("When you started", "Í upphafi ferðar"))
                Row(Modifier.fillMaxWidth()) {
                    TripStat(t("Score", "Líkur"), "${s.score}", Modifier.weight(1f))
                    TripStat(t("Tide", "Sjávarföll"), if (s.tideFlow.isNaN()) "–" else if (s.tideFlow >= 0.6) t("Moving", "Á hreyfingu") else if (s.tideFlow <= 0.25) t("Slack", "Liggjandi") else t("Turning", "Snýst"), Modifier.weight(1f))
                    TripStat(t("Wind", "Vindur"), app.afli.windText(s.wind), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    TripStat(t("Sea", "Sjór"), app.afli.tempText(s.sst, 1), Modifier.weight(1f))
                    TripStat(t("Light", "Birta"), if (s.sunElevation < -6) t("Dark", "Myrkur") else if (s.sunElevation < 8) t("Dawn/dusk", "Ljósaskipti") else t("Day", "Dagur"), Modifier.weight(1f))
                    TripStat(t("Pressure", "Þrýstingur"), if (s.pressure.isNaN() || s.pressure3h.isNaN()) "–" else (if (s.pressure >= s.pressure3h) "↑ " else "↓ ") + fmt(kotlin.math.abs(s.pressure - s.pressure3h), 1), Modifier.weight(1f))
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel(t("Catches", "Afli"))
            if (trip.catches.isEmpty()) Text(t("No fish this time. Blank trips count too.", "Enginn fiskur í þetta sinn. Ferðir án afla telja líka."), style = T.small)
            trip.catches.forEachIndexed { i, c ->
                CatchRow(c, onSize = { cm ->
                    Haptics.scrub(view)
                    Repo.updateTrip(trip.copy(catches = trip.catches.toMutableList().also { it[i] = c.copy(sizeCm = cm) }))
                }, onRemove = {
                    Haptics.tap(view)
                    Repo.updateTrip(trip.copy(catches = trip.catches.filterIndexed { k, _ -> k != i }))
                })
            }
            Spacer(Modifier.height(8.dp))
            if (!adding) {
                GlassButton(t("Add a fish I forgot", "Bæta við fiski sem gleymdist"), style = T.small, onClick = { adding = true })
            } else {
                Text(t("Which fish?", "Hvaða fiskur?"), style = T.small.copy(color = C.faint))
                Spacer(Modifier.height(6.dp))
                Fish.forWater(water).chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        row.forEach { f ->
                            GlassChip(f.name, onClick = {
                                Haptics.confirm(view)
                                // Placed at the end of the trip (or now, if it's still going).
                                val at = trip.end ?: System.currentTimeMillis()
                                Repo.updateTrip(trip.copy(catches = trip.catches + Catch(f.id, at)))
                                adding = false
                            })
                        }
                    }
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel(t("Note", "Athugasemd"))
            Box(
                Modifier.fillMaxWidth().border(1.dp, C.line, RoundedCornerShape(14.dp)).padding(12.dp),
            ) {
                if (note.isEmpty()) Text(t("Bait, where you stood, who you were with…", "Beita, hvar þú stóðst, með hverjum…"), style = T.small.copy(color = C.faint))
                BasicTextField(
                    note,
                    onValueChange = {
                        note = it.take(400)
                        Repo.updateTrip(trip.copy(note = note))
                    },
                    textStyle = T.body,
                    cursorBrush = SolidColor(C.brass),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel(t("Photos", "Myndir"))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                trip.photos.forEach { name -> PhotoThumb(name) { Repo.removePhoto(trip, name); Haptics.tap(view) } }
                Box(
                    Modifier.size(84.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, C.brass.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .clickable { Haptics.tap(view); runCatching { takePhoto.launch(null) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(t("+ Photo", "+ Mynd"), style = T.small.copy(color = C.brass))
                }
            }
            if (trip.photos.isNotEmpty()) Text(t("Tap a photo twice to remove it.", "Ýttu tvisvar á mynd til að fjarlægja hana."), style = T.small.copy(fontSize = 11.sp, color = C.faint), modifier = Modifier.padding(top = 6.dp))
        }

        GlassButton(
            if (confirmDelete) t("Tap again to delete this trip", "Ýttu aftur til að eyða ferðinni") else t("Delete this trip", "Eyða þessari ferð"),
            accent = C.bad,
            style = T.small,
            onClick = {
                if (confirmDelete) {
                    Haptics.confirm(view)
                    Repo.deleteTrip(trip.id)
                    onClose()
                } else confirmDelete = true
            },
        )
    }
}

@Composable
private fun TripStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = T.small.copy(fontSize = 11.sp, color = C.faint), maxLines = 1)
        Text(value, style = T.number, maxLines = 1, softWrap = false)
    }
}

/** A logged fish: name and time, a − size + stepper, and × to remove it. */
@Composable
private fun CatchRow(c: Catch, onSize: (Int?) -> Unit, onRemove: () -> Unit) {
    val f = Fish.byId(c.species)
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(f?.name ?: c.species, style = T.heading.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(clock(c.time), style = T.small.copy(fontSize = 11.sp, color = C.faint))
        }
        StepButton("−") {
            val v = c.sizeCm
            onSize(if (v == null) null else (v - 1).takeIf { it >= 5 })
        }
        Text(
            c.sizeCm?.let { "$it cm" } ?: t("size?", "stærð?"),
            style = T.number.copy(color = if (c.sizeCm == null) C.faint else C.foam),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(72.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepButton("+") { onSize((c.sizeCm ?: (typicalCm[c.species] ?: 30) - 1) + 1) }
        Spacer(Modifier.width(6.dp))
        Text(
            "×",
            style = T.title.copy(color = C.faint),
            modifier = Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onRemove).padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).border(1.dp, C.line, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = T.heading.copy(color = C.brass))
    }
}

/** A trip photo; two taps remove it. */
@Composable
private fun PhotoThumb(name: String, onRemove: () -> Unit) {
    val img: ImageBitmap? = remember(name) {
        runCatching { BitmapFactory.decodeFile(Repo.photoFile(name).path)?.asImageBitmap() }.getOrNull()
    }
    var armed by remember(name) { mutableStateOf(false) }
    Box(
        Modifier.size(84.dp).clip(RoundedCornerShape(16.dp)).background(C.deep)
            .border(if (armed) 2.dp else 0.dp, if (armed) C.bad else C.line, RoundedCornerShape(16.dp))
            .clickable { if (armed) onRemove() else armed = true },
        contentAlignment = Alignment.Center,
    ) {
        if (img != null) Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(84.dp))
        if (armed) Text(t("Remove", "Fjarlægja"), style = T.small.copy(color = C.bad), modifier = Modifier.background(C.navy.copy(alpha = 0.7f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
    }
}
