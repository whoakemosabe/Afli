package app.afli.data

import android.content.Context
import app.afli.Tx
import android.location.Location
import app.afli.model.Astro
import app.afli.model.Fish
import app.afli.model.Hour
import app.afli.model.HourScore
import app.afli.model.Model
import app.afli.model.Spot
import app.afli.model.Water
import app.afli.model.Window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class UiState(
    val loading: Boolean = true,
    val error: Tx? = null,
    val gps: Location? = null,
    val gpsAsked: Boolean = false,
    val spots: List<Spot> = emptyList(),
    /** The spot being shown: the saved spot he's standing at, "Here", or one he picked. */
    val spot: Spot? = null,
    val atSpot: Boolean = false,
    val forecast: Forecast? = null,
    /** The forecast hours as scored: nudged toward the live station near now. Show these. */
    val hours: List<Hour> = emptyList(),
    val live: Live? = null,
    /** Measured sea temperature nearby (Hafrannsóknastofnun), if any. */
    val sea: SeaReading? = null,
    /** The Coast Guard harbour whose tide table is used, or null when tides come from the sea model. */
    val tidePort: String? = null,
    /** True when the numbers shown are the last saved forecast because the network failed. */
    val offline: Boolean = false,
    /** The trip just deleted, kept for a few seconds so it can be undone. */
    val lastDeleted: Trip? = null,
    /** The trip just ended, for the summary card in the log. */
    val lastEnded: Trip? = null,
    val scores: List<HourScore> = emptyList(),
    val nowIndex: Int = 0,
    val window: Window? = null,
    val trips: List<Trip> = emptyList(),
    val activeTrip: Trip? = null,
) {
    val now: HourScore? get() = scores.getOrNull(nowIndex)

    /** Everything that belongs to one spot's forecast, emptied. */
    fun cleared() = copy(forecast = null, hours = emptyList(), scores = emptyList(), nowIndex = 0, window = null, live = null, sea = null, tidePort = null)
}

/**
 * Holds what the screens show. GPS picks the water: the saved spot he's standing at (within
 * 300 m), otherwise "Here". Forecasts are fetched for that position and scored on the phone.
 */
object Repo {
    val state = MutableStateFlow(UiState())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var db: Store
    private var job: Job? = null

    private lateinit var appContext: Context

    fun init(context: Context) {
        if (::db.isInitialized) return
        appContext = context.applicationContext
        db = Store(context.applicationContext)
        val trips = db.trips()
        state.update { it.copy(spots = db.spots(), trips = trips, activeTrip = trips.firstOrNull { t -> t.end == null }) }
    }

    fun store(): Store = db

    /** Find where he is, then load and score that water. */
    fun refresh(context: Context, useGps: Boolean = true) {
        job?.cancel()
        job = scope.launch {
            state.update { it.copy(loading = true, error = null) }
            val app = context.applicationContext
            val fix = if (useGps && Locator.hasPermission(app)) runCatching { Locator.current(app) }.getOrNull() else state.value.gps
            val spots = db.spots()
            val chosen = db.selectedSpot?.let { id -> spots.firstOrNull { it.id == id } }
            val near = fix?.let { db.spotNear(it.latitude, it.longitude) }
            val spot = when {
                near != null -> near
                chosen != null -> chosen
                fix != null -> Spot("here", runCatching { Locator.nameFor(app, fix.latitude, fix.longitude) }.getOrDefault("Here"), fix.latitude, fix.longitude)
                else -> spots.first()
            }
            state.update {
                val moved = it.spot?.id != spot.id
                // A different water: drop the old numbers so nothing on screen belongs to the last spot.
                (if (moved) it.cleared() else it).copy(gps = fix, gpsAsked = true, spots = spots, spot = spot, atSpot = near != null)
            }
            load(spot)
        }
    }

    /** Show a particular spot (from the spot chips). */
    fun choose(context: Context, spot: Spot) {
        db.selectedSpot = if (spot.id == "here") null else spot.id
        job?.cancel()
        job = scope.launch {
            state.update { (if (it.spot?.id != spot.id) it.cleared() else it).copy(spot = spot, atSpot = false, loading = true, error = null) }
            load(spot)
        }
    }

    private suspend fun load(spot: Spot) {
        // Show the last saved forecast for this spot straight away, while the new one loads.
        val cached = db.cachedForecast(spot.id)?.takeIf { System.currentTimeMillis() - it.fetchedAt < 3 * 86_400_000L }
        if (cached != null && state.value.scores.isEmpty()) {
            val scored = withContext(Dispatchers.Default) { scoreFor(cached, spot, null, null) }
            state.update { it.copy(forecast = cached, scores = scored.scores, nowIndex = scored.nowIndex, hours = scored.hours, tidePort = scored.tidePort, window = Model.nextWindow(scored.scores, System.currentTimeMillis())) }
        }
        try {
            val fc = Feeds.forecast(spot.lat, spot.lon)
            db.saveForecast(spot.id, fc)
            val live = runCatching { Feeds.live(spot.lat, spot.lon) }.getOrNull()
            val sea = runCatching { Feeds.seaTemp(spot.lat, spot.lon) }.getOrNull()
            val scored = withContext(Dispatchers.Default) { scoreFor(fc, spot, live, sea) }
            state.update {
                it.copy(
                    loading = false, offline = false, forecast = fc, live = live, sea = sea, tidePort = scored.tidePort,
                    scores = scored.scores, nowIndex = scored.nowIndex, hours = scored.hours,
                    window = Model.nextWindow(scored.scores, System.currentTimeMillis()),
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (cached != null) {
                // No signal: keep the saved forecast, rescored for the current hour, and say so.
                val scored = withContext(Dispatchers.Default) { scoreFor(cached, spot, null, null) }
                state.update {
                    it.copy(
                        loading = false, offline = true, error = null, forecast = cached, live = null, sea = null, tidePort = scored.tidePort,
                        scores = scored.scores, nowIndex = scored.nowIndex, hours = scored.hours,
                        window = Model.nextWindow(scored.scores, System.currentTimeMillis()),
                    )
                }
            } else {
                state.update { it.copy(loading = false, error = Tx("Couldn't load the forecast. Check your connection and pull down to try again.", "Náði ekki í spána. Athugaðu nettenginguna og dragðu niður til að reyna aftur.")) }
            }
        }
    }

    private class Scored(val scores: List<HourScore>, val nowIndex: Int, val hours: List<Hour>, val tidePort: String?)

    /**
     * Scores every hour. Before scoring, the forecast's current wind and gusts are nudged toward
     * the live station reading, fading out over 12 hours. Pressure gets the same offset for the
     * past and next 12 hours, fading only after that, so the correction itself never shows up
     * as a pressure trend (the score reads the 3-hour change).
     */
    private fun scoreFor(fc: Forecast, spot: Spot, live: Live?, sea: SeaReading?): Scored {
        val now = System.currentTimeMillis()
        val nowIndex = fc.hours.indexOfLast { it.t <= now }.coerceAtLeast(0)
        var hours = fc.hours
        if (live != null && hours.isNotEmpty()) {
            val h0 = hours[nowIndex]
            val dWind = if (live.wind.isNaN() || h0.wind.isNaN()) 0.0 else live.wind - h0.wind
            val dGust = if (live.gust.isNaN() || h0.gust.isNaN()) 0.0 else live.gust - h0.gust
            val dP = if (live.pressure.isNaN() || h0.pressure.isNaN()) 0.0 else live.pressure - h0.pressure
            hours = hours.mapIndexed { i, h ->
                val k = i - nowIndex
                val wWind = if (k < 0 || k > 12) 0.0 else 1.0 - k / 12.0
                val wP = when {
                    k <= 12 -> 1.0
                    k >= 36 -> 0.0
                    else -> 1.0 - (k - 12) / 24.0
                }
                h.copy(
                    wind = if (wWind > 0) (h.wind + dWind * wWind).coerceAtLeast(0.0) else h.wind,
                    gust = if (wWind > 0) (h.gust + dGust * wWind).coerceAtLeast(0.0) else h.gust,
                    pressure = h.pressure + dP * wP,
                )
            }
        }
        // Tides from the Coast Guard tables at harbours they cover; the sea model elsewhere.
        var tidePort: String? = null
        if (spot.water == Water.SEA) {
            TideTable.apply(appContext, hours, spot.lat, spot.lon)?.let { (h, port) ->
                // The tables assume average air pressure. Per the Coast Guard, a 10 hPa fall lifts
                // the sea about 0.1 m (and a rise lowers it), so the table level is shifted by that.
                hours = h.map { hr -> if (hr.pressure.isNaN() || hr.seaLevel.isNaN()) hr else hr.copy(seaLevel = hr.seaLevel + TideTable.pressureSetup(hr.pressure)) }
                tidePort = port.name
            }
        }
        // Measured sea temperature: shift the model's sea temperature by the difference now.
        // The sea changes slowly, so the full offset holds for two days and fades by day five.
        if (sea != null && spot.water == Water.SEA) {
            val i0 = hours.indexOfLast { it.t <= sea.time }.coerceAtLeast(0)
            val model0 = hours.getOrNull(i0)?.sst ?: Double.NaN
            if (!model0.isNaN()) {
                val dT = sea.temp - model0
                hours = hours.mapIndexed { i, h ->
                    val k = i - nowIndex
                    val w = when {
                        k <= 48 -> 1.0
                        k >= 120 -> 0.0
                        else -> 1.0 - (k - 48) / 72.0
                    }
                    if (h.sst.isNaN()) h else h.copy(sst = h.sst + dT * w)
                }
            }
        }
        val lakeTemp = if (spot.water == Water.LAKE) {
            // A lake's surface follows the past week's air temperature, a little warmer in summer.
            hours.filter { it.t in (now - 7 * 86_400_000L)..now && !it.airTemp.isNaN() }.map { it.airTemp }.average().let { if (it.isNaN()) it else it + 1.0 }
        } else Double.NaN
        val boost = app.afli.model.Learn.boost(spot, db.trips())
        return Scored(Model.scoreAll(hours, spot, lakeTemp, boost), nowIndex, hours, tidePort)
    }

    // ---- spots ----

    fun saveSpot(context: Context, spot: Spot) {
        val s = if (spot.id == "here") spot.copy(id = "spot-" + UUID.randomUUID().toString().take(8)) else spot
        db.saveSpot(s)
        state.update { it.copy(spots = db.spots(), spot = s) }
        db.selectedSpot = s.id
        scope.launch {
            val fc = state.value.forecast ?: return@launch
            val scored = withContext(Dispatchers.Default) { scoreFor(fc, s, state.value.live, state.value.sea) }
            state.update { it.copy(scores = scored.scores, nowIndex = scored.nowIndex, hours = scored.hours, tidePort = scored.tidePort, window = Model.nextWindow(scored.scores, System.currentTimeMillis())) }
        }
    }

    // ---- trips ----

    fun startTrip(context: Context) {
        val st = state.value
        val spot = st.spot ?: return
        val gps = st.gps
        var target = spot
        // Trips build spots: a new place becomes a saved spot named by the phone.
        if (spot.id == "here") {
            target = spot.copy(id = "spot-" + UUID.randomUUID().toString().take(8), lat = gps?.latitude ?: spot.lat, lon = gps?.longitude ?: spot.lon)
            db.saveSpot(target)
        }
        val now = st.now
        val h = st.hours.getOrNull(st.nowIndex)
        val h3 = st.hours.getOrNull(st.nowIndex - 3)
        val trip = Trip(
            id = UUID.randomUUID().toString(),
            spotId = target.id,
            spotName = target.name,
            lat = gps?.latitude ?: target.lat,
            lon = gps?.longitude ?: target.lon,
            start = System.currentTimeMillis(),
            end = null,
            catches = emptyList(),
            snapshot = Snapshot(
                score = now?.score ?: 0,
                wind = h?.wind ?: Double.NaN,
                windDir = h?.windDir ?: Double.NaN,
                pressure = h?.pressure ?: Double.NaN,
                pressure3h = h3?.pressure ?: Double.NaN,
                sst = h?.sst ?: Double.NaN,
                wave = h?.wave ?: Double.NaN,
                tideFlow = now?.tideFlow ?: Double.NaN,
                sunElevation = now?.sunElevation ?: Astro.sunElevation(System.currentTimeMillis(), target.lat, target.lon),
            ),
        )
        db.saveTrip(trip)
        state.update { it.copy(activeTrip = trip, trips = db.trips(), spots = db.spots(), spot = target, lastEnded = null) }
        app.afli.update.TripNotice.update(appContext)
    }

    fun addCatch(speciesId: String) {
        val t = state.value.activeTrip ?: return
        if (Fish.byId(speciesId) == null) return
        val updated = t.copy(catches = t.catches + Catch(speciesId, System.currentTimeMillis()))
        db.saveTrip(updated)
        state.update { it.copy(activeTrip = updated, trips = db.trips()) }
        app.afli.update.TripNotice.update(appContext)
    }

    fun undoCatch() {
        val t = state.value.activeTrip ?: return
        if (t.catches.isEmpty()) return
        val updated = t.copy(catches = t.catches.dropLast(1))
        db.saveTrip(updated)
        state.update { it.copy(activeTrip = updated, trips = db.trips()) }
        app.afli.update.TripNotice.update(appContext)
    }

    fun endTrip() {
        val t = state.value.activeTrip ?: return
        val done = t.copy(end = System.currentTimeMillis())
        db.saveTrip(done)
        state.update { it.copy(activeTrip = null, trips = db.trips(), lastEnded = done) }
        app.afli.update.TripNotice.update(appContext)
        rescore()
    }

    fun clearTrips() {
        db.clearTrips()
        state.update { it.copy(trips = emptyList(), activeTrip = null) }
    }

    fun forgetSpots(context: Context) {
        db.forgetSpots()
        state.update { it.copy(spots = db.spots()) }
        refresh(context)
    }

    fun deleteTrip(id: String) {
        val gone = db.trips().firstOrNull { it.id == id }
        db.deleteTrip(id)
        state.update { it.copy(trips = db.trips(), activeTrip = if (it.activeTrip?.id == id) null else it.activeTrip, lastDeleted = gone) }
        rescore()
    }

    /** Puts back the trip that was just deleted. */
    fun undoDelete() {
        val t = state.value.lastDeleted ?: return
        db.saveTrip(t)
        state.update { it.copy(trips = db.trips(), lastDeleted = null, activeTrip = if (t.end == null) t else it.activeTrip) }
        rescore()
    }

    fun dismissSummary() {
        state.update { it.copy(lastEnded = null) }
    }

    fun forgetDeleted() {
        val t = state.value.lastDeleted ?: return
        // The trip is gone for good now, so its photos can go too.
        t.photos.forEach { java.io.File(db.photoDir, it).delete() }
        state.update { it.copy(lastDeleted = null) }
    }

    /** Saves changes to a trip (sizes, note, photos, catches). */
    fun updateTrip(trip: Trip) {
        db.saveTrip(trip)
        state.update { it.copy(trips = db.trips(), activeTrip = if (it.activeTrip?.id == trip.id) trip else it.activeTrip) }
    }

    /** Saves a photo for a trip as a JPEG in the app's own folder. */
    fun addPhoto(trip: Trip, bitmap: android.graphics.Bitmap) {
        val name = "${trip.id}-${System.currentTimeMillis()}.jpg"
        runCatching {
            java.io.File(db.photoDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
            updateTrip(trip.copy(photos = trip.photos + name))
        }
    }

    fun removePhoto(trip: Trip, name: String) {
        java.io.File(db.photoDir, name).delete()
        updateTrip(trip.copy(photos = trip.photos - name))
    }

    fun photoFile(name: String) = java.io.File(db.photoDir, name)

    // ---- spots ----

    fun renameSpot(id: String, name: String) {
        db.renameSpot(id, name)
        state.update { st -> st.copy(spots = db.spots(), spot = st.spot?.let { sp -> if (sp.id == id) db.spots().firstOrNull { it.id == id } ?: sp else sp }) }
    }

    fun deleteSpot(context: Context, id: String) {
        db.deleteSpot(id)
        val wasShown = state.value.spot?.id == id
        state.update { it.copy(spots = db.spots()) }
        if (wasShown) refresh(context)
    }

    /** Scores the current forecast again (after trips change what Afli has learned). */
    fun rescore() {
        val st = state.value
        val fc = st.forecast ?: return
        val spot = st.spot ?: return
        scope.launch {
            val scored = withContext(Dispatchers.Default) { scoreFor(fc, spot, st.live, st.sea) }
            state.update { it.copy(scores = scored.scores, nowIndex = scored.nowIndex, hours = scored.hours, tidePort = scored.tidePort, window = Model.nextWindow(scored.scores, System.currentTimeMillis())) }
        }
    }

    /**
     * A quick forecast and score for one spot, for the background bite alerts. No live station
     * correction; tide tables and what he's caught there still apply.
     */
    suspend fun scoreInBackground(context: Context, spot: Spot): List<HourScore> {
        init(context)
        val fc = Feeds.forecast(spot.lat, spot.lon)
        db.saveForecast(spot.id, fc)
        return withContext(Dispatchers.Default) { scoreFor(fc, spot, null, null).scores }
    }

    /** The spot alerts watch: the one he last chose, else the one he last fished, else Keflavík. */
    fun alertSpot(): Spot {
        val spots = db.spots()
        return db.selectedSpot?.let { id -> spots.firstOrNull { it.id == id } }
            ?: db.trips().firstOrNull()?.let { t -> spots.firstOrNull { it.id == t.spotId } }
            ?: spots.first()
    }
}
