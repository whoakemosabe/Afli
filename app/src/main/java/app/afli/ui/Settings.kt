package app.afli.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import app.afli.L
import app.afli.Lang
import app.afli.data.Repo
import app.afli.t
import app.afli.model.Spot
import app.afli.model.Water
import app.afli.update.UpdateWatch
import app.afli.update.Updater
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private sealed class UpdateUi {
    data object Idle : UpdateUi()
    data object Checking : UpdateUi()
    data object UpToDate : UpdateUi()
    data class Available(val release: Updater.Release) : UpdateUi()
    data class Downloading(val release: Updater.Release) : UpdateUi()
    data class Ready(val file: File, val version: String, val note: String? = null) : UpdateUi()
    data class Error(val message: String) : UpdateUi()
}

/** Settings: updates (check, download, install), help, and data credits. */
@Composable
fun SettingsContent(onReplayIntro: () -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(t("Settings", "Stillingar"), style = T.title)
        LanguageCard()
        UpdateSection()
        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel(t("Help", "Hjálp"))
            Text(t("Tips show once per screen. Long-press anything for an explanation at any time.", "Ábendingar birtast einu sinni á hverjum skjá. Haltu fingri á hverju sem er til að fá útskýringu."), style = T.small)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(t("Show tips again", "Sýna ábendingar aftur"), style = T.small, onClick = {
                    Repo.store().resetTips()
                    Tips.explain(t("Tips are back. They'll show the next time you open each screen.", "Ábendingarnar eru komnar aftur. Þær birtast næst þegar þú opnar hvern skjá."))
                })
                GlassButton(t("Replay intro", "Sýna kynningu"), style = T.small, onClick = onReplayIntro)
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel(t("Data", "Gögn"))
            Text(
                t(
                    "Weather: Open-Meteo (DMI HARMONIE in Iceland). Sea, waves and tides: Open-Meteo Marine, using DWD and Météo-France models. " +
                        "Live station readings: Veðurstofa Íslands, CC BY 4.0. Fish facts: Wikipedia and Hafrannsóknastofnun.",
                    "Veður: Open-Meteo (DMI HARMONIE á Íslandi). Sjór, öldur og sjávarföll: Open-Meteo Marine, með líkönum DWD og Météo-France. " +
                        "Mælingar í rauntíma: Veðurstofa Íslands, CC BY 4.0. Um fiskana: Wikipedia og Hafrannsóknastofnun.",
                ),
                style = T.small,
            )
            Spacer(Modifier.height(6.dp))
            Text(t("Tides here are a model estimate and not for navigation.", "Sjávarföllin hér eru mat úr líkani og ekki til siglinga."), style = T.small.copy(color = C.faint))
            Spacer(Modifier.height(6.dp))
            Text(t("Version ", "Útgáfa ") + Updater.installedVersion(context), style = T.small.copy(color = C.faint))
        }
    }
}

/** English or Icelandic. Switches everything at once and remembers the choice. */
@Composable
private fun LanguageCard() {
    val view = LocalView.current
    GlassCard(Modifier.fillMaxWidth()) {
        SectionLabel(t("Language", "Tungumál"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Lang.EN to "English", Lang.IS to "Íslenska").forEach { (lang, name) ->
                GlassChip(name, selected = L.lang == lang, onClick = {
                    if (L.lang != lang) {
                        L.lang = lang
                        Repo.store().lang = lang.code
                        Haptics.confirm(view)
                    }
                })
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(t("Fish names show in both languages either way.", "Nöfn fiskanna sjást á báðum málum."), style = T.small)
    }
}

@Composable
private fun UpdateSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateUi>(UpdateUi.Idle) }
    var progress by remember { mutableFloatStateOf(0f) }
    val version = remember { Updater.installedVersion(context) }

    fun runCheck() {
        state = UpdateUi.Checking
        scope.launch {
            val r = Updater.check(context)
            state = when (r) {
                Updater.Check.UpToDate -> UpdateUi.UpToDate
                is Updater.Check.Available -> UpdateUi.Available(r.release)
                is Updater.Check.Failed -> UpdateUi.Error(r.message)
            }
            when (r) {
                Updater.Check.UpToDate -> UpdateWatch.remember(context, null)
                is Updater.Check.Available -> UpdateWatch.remember(context, r.release.version)
                is Updater.Check.Failed -> {}
            }
        }
    }

    LaunchedEffect(Unit) {
        if (UpdateWatch.waiting(context) != null) runCheck()
    }

    GlassCard(Modifier.fillMaxWidth()) {
        SectionLabel(t("Updates", "Uppfærslur"))
        Text(t("Version ", "Útgáfa ") + version, style = T.heading)
        val line = when (val s = state) {
            UpdateUi.Idle -> t("New versions come from GitHub releases.", "Nýjar útgáfur koma frá GitHub.")
            UpdateUi.Checking -> t("Checking…", "Athuga…")
            UpdateUi.UpToDate -> t("You're on the latest version.", "Þú ert með nýjustu útgáfuna.")
            is UpdateUi.Available -> t("Version ${s.release.version} is available.", "Útgáfa ${s.release.version} er komin.")
            is UpdateUi.Downloading -> t("Downloading ", "Sæki ") + "${s.release.version}… ${(progress * 100).toInt()}%"
            is UpdateUi.Ready -> s.note ?: t("Downloaded ${s.version}. Tap Install.", "Búið að sækja ${s.version}. Ýttu á Setja upp.")
            is UpdateUi.Error -> s.message
        }
        Text(
            line,
            style = T.small.copy(
                color = when (state) {
                    is UpdateUi.Error -> C.bad
                    is UpdateUi.Available, is UpdateUi.Ready -> C.brass
                    UpdateUi.UpToDate -> C.good
                    else -> C.mist
                },
            ),
        )
        Spacer(Modifier.height(12.dp))
        when (val s = state) {
            is UpdateUi.Available -> GlassButton(t("Download ", "Sækja ") + s.release.version, accent = C.brass, onClick = {
                state = UpdateUi.Downloading(s.release)
                progress = 0f
                scope.launch {
                    state = try {
                        UpdateUi.Ready(Updater.download(context, s.release) { progress = it }, s.release.version)
                    } catch (e: Exception) {
                        UpdateUi.Error(t("Download failed. Try again.", "Niðurhal mistókst. Reyndu aftur."))
                    }
                }
            })
            is UpdateUi.Ready -> GlassButton(t("Install", "Setja upp"), accent = C.good, onClick = {
                // Without "install unknown apps" Android opens that setting; keep the file so the
                // next tap retries the install, not the download.
                if (!Updater.install(context, s.file)) state = s.copy(note = t("Allow Afli to install updates, then tap Install again.", "Leyfðu Afla að setja upp uppfærslur og ýttu svo aftur á Setja upp."))
            })
            is UpdateUi.Downloading -> ProgressBar(progress)
            else -> GlassButton(
                if (state == UpdateUi.Checking) t("Checking…", "Athuga…") else t("Check for updates", "Leita að uppfærslum"),
                onClick = { if (state != UpdateUi.Checking) runCheck() },
            )
        }
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    val shown by animateFloatAsState(progress, tween(260), label = "download")
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(C.line, cornerRadius = r)
        if (shown > 0.002f) drawRoundRect(C.brass, size = Size(size.width * shown, size.height), cornerRadius = r)
    }
}

/**
 * The update banner across the top of every screen when a new version is waiting. One tap
 * downloads it (progress fills the banner), the next installs it. Tap the × to hide it until
 * the next version.
 */
/** The version whose banner was closed with ×, so the page can give the space back. */
var bannerHidden by mutableStateOf<String?>(null)

@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val waiting by UpdateWatch.waitingVersion.collectAsState()
    var state by remember { mutableStateOf<UpdateUi>(UpdateUi.Idle) }
    var progress by remember { mutableFloatStateOf(0f) }
    val v = waiting
    androidx.compose.animation.AnimatedVisibility(
        visible = v != null && bannerHidden != v,
        modifier = modifier,
        enter = androidx.compose.animation.slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { -it } +
            androidx.compose.animation.fadeIn(tween(200)),
        exit = androidx.compose.animation.slideOutVertically(tween(220)) { -it } + androidx.compose.animation.fadeOut(tween(180)),
    ) {
        val shown by animateFloatAsState(progress, tween(260), label = "bannerProgress")
        GlassCard(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            glow = C.brass,
            explain = t("A new version of Afli is out. Tap to download it, then tap again to install. It installs over this one; your trips are kept.", "Ný útgáfa af Afla er komin. Ýttu til að sækja hana og aftur til að setja upp. Hún fer yfir þessa og ferðirnar þínar haldast."),
            onClick = {
                when (val st = state) {
                    UpdateUi.Idle, is UpdateUi.Error -> {
                        state = UpdateUi.Checking
                        scope.launch {
                            when (val r = Updater.check(context)) {
                                is Updater.Check.Available -> {
                                    state = UpdateUi.Downloading(r.release)
                                    progress = 0f
                                    state = try {
                                        UpdateUi.Ready(Updater.download(context, r.release) { progress = it }, r.release.version)
                                    } catch (e: Exception) {
                                        UpdateUi.Error(t("Download failed. Tap to try again.", "Niðurhal mistókst. Ýttu til að reyna aftur."))
                                    }
                                    if (state is UpdateUi.Ready) Haptics.confirm(view)
                                }
                                Updater.Check.UpToDate -> {
                                    UpdateWatch.remember(context, null)
                                    state = UpdateUi.UpToDate
                                }
                                is Updater.Check.Failed -> state = UpdateUi.Error(r.message)
                            }
                        }
                    }
                    is UpdateUi.Ready -> {
                        if (!Updater.install(context, st.file)) state = st.copy(note = t("Allow Afli to install updates, then tap again.", "Leyfðu Afla að setja upp uppfærslur og ýttu svo aftur."))
                    }
                    else -> {}
                }
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("Afli $v is ready", "Afli $v er tilbúinn"), style = T.heading.copy(color = C.brass))
                    Text(
                        when (val st = state) {
                            UpdateUi.Idle -> t("Tap to download", "Ýttu til að sækja")
                            UpdateUi.Checking -> t("Checking…", "Athuga…")
                            is UpdateUi.Downloading -> t("Downloading… ", "Sæki… ") + "${(progress * 100).toInt()}%"
                            is UpdateUi.Ready -> st.note ?: t("Downloaded. Tap to install", "Sótt. Ýttu til að setja upp")
                            is UpdateUi.Error -> st.message
                            UpdateUi.UpToDate -> t("You're up to date", "Þú ert með nýjustu útgáfuna")
                            is UpdateUi.Available -> t("Tap to download", "Ýttu til að sækja")
                        },
                        style = T.small,
                    )
                }
                Text(
                    "×",
                    style = T.title.copy(color = C.mist),
                    modifier = Modifier
                        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { bannerHidden = v }
                        .padding(start = 12.dp, end = 4.dp),
                )
            }
            if (state is UpdateUi.Downloading) {
                Spacer(Modifier.height(8.dp))
                Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(C.line, cornerRadius = r)
                    drawRoundRect(C.brass, size = Size(size.width * shown, size.height), cornerRadius = r)
                }
            }
        }
    }
}

/**
 * Fix a spot: point the phone at the water to set its direction, and say whether it's a
 * sheltered harbour or open coast, or a lake.
 */
@Composable
fun SpotFixContent(spot: Spot, onDone: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val heading = rememberHeading(context)
    var facing by remember(spot.id) { mutableStateOf(spot.facing) }
    var sheltered by remember(spot.id) { mutableStateOf(spot.sheltered) }
    var water by remember(spot.id) { mutableStateOf(spot.water) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(t("Fix ", "Stilla ") + spot.label, style = T.title)
        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel(t("Which way is the water?", "Í hvaða átt er vatnið?"))
            Text(t("Stand at the edge, point the top of your phone at the water, and tap Set.", "Stattu á bakkanum, beindu toppi símans að vatninu og ýttu á Stilla."), style = T.small)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                WindDial(Double.NaN, heading.value?.toDouble(), Modifier.size(110.dp))
                Spacer(Modifier.size(14.dp))
                Column {
                    Text(heading.value?.let { "${it.roundToInt()}° ${compass(it.toDouble())}" } ?: t("No compass on this phone", "Enginn áttaviti í þessum síma"), style = T.number)
                    Text(facing?.let { t("Saved: water to the ${compass(it)}", "Vistað: vatnið í ${compass(it)}") } ?: t("Not set yet", "Ekki stillt enn"), style = T.small)
                    Spacer(Modifier.height(8.dp))
                    GlassButton(t("Set", "Stilla"), accent = C.brass, onClick = {
                        heading.value?.let {
                            facing = it.toDouble()
                            Haptics.confirm(view)
                        }
                    })
                }
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel(t("What kind of water?", "Hvers konar vatn?"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip(t("Harbour", "Höfn"), selected = water == Water.SEA && sheltered, onClick = { water = Water.SEA; sheltered = true })
                GlassChip(t("Open coast", "Opin strönd"), selected = water == Water.SEA && !sheltered, onClick = { water = Water.SEA; sheltered = false })
                GlassChip(t("Lake", "Vatn"), selected = water == Water.LAKE, onClick = { water = Water.LAKE; sheltered = false })
            }
            Spacer(Modifier.height(6.dp))
            Text(t("Harbours feel much less of the waves, so they're safer in a swell.", "Hafnir finna mun minna fyrir öldunni og eru því öruggari í ölduróti."), style = T.small)
        }
        GlassButton(t("Save", "Vista"), accent = C.brass, modifier = Modifier.padding(bottom = 6.dp), onClick = {
            Repo.saveSpot(context, spot.copy(facing = facing, sheltered = sheltered, water = water))
            Haptics.confirm(view)
            onDone()
        })
    }
}

/** Live compass heading in degrees (null if the phone has no rotation sensor). */
@Composable
private fun rememberHeading(context: Context): androidx.compose.runtime.State<Float?> {
    val state = remember { mutableStateOf<Float?>(null) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val rot = FloatArray(9)
        val ori = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, ori)
                val deg = ((Math.toDegrees(ori[0].toDouble()) + 360) % 360).toFloat()
                val prev = state.value
                // Smooth the jitter, taking the short way round.
                state.value = if (prev == null) deg else {
                    var d = deg - prev
                    if (d > 180) d -= 360
                    if (d < -180) d += 360
                    ((prev + d * 0.15f) + 360) % 360
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) sm?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(listener) }
    }
    return state
}
