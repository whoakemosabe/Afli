package app.afli.ui

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.afli.data.Repo
import app.afli.data.Trip
import app.afli.model.Fish
import app.afli.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A trip as a picture to send: photo (if any), the catch, the spot and day, the conditions and
 * Afli's score, on Afli's navy and brass. Sized 4:5 for stories and chats. Share renders exactly
 * what's shown to an image and hands it to the phone's share menu.
 */
@Composable
fun ShareContent(trip: Trip) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(t("Share your catch", "Deildu aflanum"), style = T.title)
        Box(
            Modifier.fillMaxWidth().drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            },
        ) {
            CatchCard(trip)
        }
        GlassButton(t("Share", "Deila"), accent = C.brass, modifier = Modifier.fillMaxWidth(), onClick = {
            Haptics.confirm(view)
            scope.launch {
              runCatching {
                // Navy behind the rounded corners, so chat apps don't show them black or white.
                val card = layer.toImageBitmap().asAndroidBitmap()
                val bmp = android.graphics.Bitmap.createBitmap(card.width, card.height, android.graphics.Bitmap.Config.ARGB_8888).also { out ->
                    android.graphics.Canvas(out).apply { drawColor(android.graphics.Color.rgb(7, 18, 31)); drawBitmap(card, 0f, 0f, null) }
                }
                val file = withContext(Dispatchers.IO) {
                    File(context.cacheDir, "shares").apply { mkdirs() }.let { dir ->
                        File(dir, "afli-${trip.id.take(8)}.png").also { f -> f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
                    }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, t("Share catch", "Deila afla")))
              }.onFailure { Tips.explain(t("Couldn't make the picture. Try again.", "Náði ekki að búa til myndina. Reyndu aftur.")) }
            }
        })
    }
}

@Composable
private fun CatchCard(trip: Trip) {
    val img by rememberPhoto(trip.photos.firstOrNull(), 1200)
    val mins = (((trip.end ?: System.currentTimeMillis()) - trip.start) / 60_000).toInt()
    val counts = trip.catches.groupBy { it.species }.entries.sortedByDescending { it.value.size }
    val biggest = trip.catches.filter { it.sizeCm != null }.maxByOrNull { it.sizeCm!! }
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier.fillMaxWidth().aspectRatio(0.8f).clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF0B2A40), Color(0xFF07121F))))
            .border(1.dp, Brush.linearGradient(listOf(Color(0xCCEAF4F8), Color(0x22EAF4F8), Color(0xB3D8B56A))), shape)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AFLI", style = T.label.copy(fontSize = 14.sp, letterSpacing = 3.sp, color = C.brass), modifier = Modifier.weight(1f))
            // A real date: the picture lives on long after "today".
            Text(java.time.format.DateTimeFormatter.ofPattern(if (app.afli.L.isl) "d. MMM yyyy" else "d MMM yyyy", app.afli.L.locale).format(java.time.Instant.ofEpochMilli(trip.start).atZone(java.time.ZoneId.systemDefault())) + " · " + clock(trip.start), style = T.small.copy(color = C.mist))
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(18.dp)).background(Color(0x22EAF4F8)),
            contentAlignment = Alignment.Center,
        ) {
            val pic = img
            if (pic != null) Image(pic, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FishGlyph(C.brass, Modifier.size(120.dp))
                Text(fishCount(trip.catches.size), style = T.title.copy(fontSize = 34.sp, color = C.foam))
            }
        }
        Spacer(Modifier.height(14.dp))
        FitText(spotLabel(trip.spotId, trip.spotName), T.title.copy(fontSize = 24.sp), min = 14.sp)
        Text(
            counts.joinToString("  ·  ") { (id, c) -> "${Fish.byId(id)?.name ?: id} ×${c.size}" }.ifEmpty { t("Blank, but out there", "Án afla, en á staðnum") },
            style = T.body.copy(color = C.good, fontWeight = FontWeight.SemiBold),
        )
        biggest?.let { Text(t("Biggest: ", "Stærstur: ") + "${Fish.byId(it.species)?.name} ${it.sizeCm} cm", style = T.small.copy(color = C.brass)) }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Mini(t("Time", "Tími"), duration(mins), Modifier.weight(1f))
            trip.snapshot?.let { s ->
                Mini(t("Wind", "Vindur"), app.afli.windText(s.wind), Modifier.weight(1f))
                Mini(t("Sea", "Sjór"), app.afli.tempText(s.sst, 1), Modifier.weight(1f))
                Mini(t("Afli said", "Afli spáði"), "${s.score}", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Mini(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label.uppercase(app.afli.L.locale), style = T.label.copy(fontSize = 9.sp, color = C.faint), maxLines = 1)
        FitText(value, T.small.copy(color = C.foam, fontWeight = FontWeight.SemiBold), min = 9.sp)
    }
}
