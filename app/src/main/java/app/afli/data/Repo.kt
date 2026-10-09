package app.afli.data

import android.content.Context
import app.afli.Tx
import android.location.Location
import app.afli.model.Astro
import app.afli.model.Fish
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
    val live: Live? = null,
    val scores: List<HourScore> = emptyList(),
    val nowIndex: Int = 0,
    val window: Window? = null,
    val trips: List<Trip> = emptyList(),
    val activeTrip: Trip? = null,
) {
    val now: HourScore? get() = scores.getOrNull(nowIndex)
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

    fun init(context: Context) {
        if (::db.isInitialized) return
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
            state.update { it.copy(gps = fix, gpsAsked = true, spots = spots, spot = spot, atSpot = near != null) }
            load(spot)
        }
    }

    /** Show a particular spot (from the spot chips). */
    fun choose(context: Context, spot: Spot) {
        db.selectedSpot = if (spot.id == "here") null else spot.id
        job?.cancel()
        job = scope.launch {
            state.update { it.copy(spot = spot, atSpot = false, loading = true, error = null) }
            load(spot)
        }
    }

    private suspend fun load(spot: Spot) {
        try {
            val fc = Feeds.forecast(spot.lat, spot.lon)
            val live = runCatching { Feeds.live(spot.lat, spot.lon) }.getOrNull()
            val scored = withContext(Dispatchers.Default) { scoreFor(fc, spot, live) }
            state.update {
                it.copy(
                    loading = false, forecast = fc, live = live,
                    scores = scored.first, nowIndex = scored.second,
                    window = Model.nextWindow(scored.first, System.currentTimeMillis()),
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            state.update { it.copy(loading = false, error = Tx("Couldn't load the forecast. Check your connection and pull down to try again.", "Náði ekki í spána. Athugaðu nettenginguna og dragðu niður til að reyna aftur.")) }
        }
    }

    /**
     * Scores every hour. Before scoring, the forecast's current wind, gusts and pressure are
     * nudged toward the live station reading and the nudge fades out over 12 hours (the
     * live bias correction).
     */
    private fun scoreFor(fc: Forecast, spot: Spot, live: Live?): Pair<List<HourScore>, Int> {
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
                if (k < 0 || k > 12) h else {
                    val w = 1.0 - k / 12.0
                    h.copy(
                        wind = (h.wind + dWind * w).coerceAtLeast(0.0),
                        gust = (h.gust + dGust * w).coerceAtLeast(0.0),
                        pressure = h.pressure + dP * w,
                    )
                }
            }
        }
        val lakeTemp = if (spot.water == Water.LAKE) {
            // A lake's surface follows the past week's air temperature, a little warmer in summer.
            hours.filter { it.t in (now - 7 * 86_400_000L)..now && !it.airTemp.isNaN() }.map { it.airTemp }.average().let { if (it.isNaN()) it else it + 1.0 }
        } else Double.NaN
        return Model.scoreAll(hours, spot, lakeTemp) to nowIndex
    }

    // ---- spots ----

    fun saveSpot(context: Context, spot: Spot) {
        val s = if (spot.id == "here") spot.copy(id = "spot-" + UUID.randomUUID().toString().take(8)) else spot
        db.saveSpot(s)
        state.update { it.copy(spots = db.spots(), spot = s) }
        db.selectedSpot = s.id
        scope.launch {
            val fc = state.value.forecast ?: return@launch
            val scored = withContext(Dispatchers.Default) { scoreFor(fc, s, state.value.live) }
            state.update { it.copy(scores = scored.first, nowIndex = scored.second, window = Model.nextWindow(scored.first, System.currentTimeMillis())) }
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
        val h = st.forecast?.hours?.getOrNull(st.nowIndex)
        val h3 = st.forecast?.hours?.getOrNull(st.nowIndex - 3)
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
        state.update { it.copy(activeTrip = trip, trips = db.trips(), spots = db.spots(), spot = target) }
    }

    fun addCatch(speciesId: String) {
        val t = state.value.activeTrip ?: return
        if (Fish.byId(speciesId) == null) return
        val updated = t.copy(catches = t.catches + Catch(speciesId, System.currentTimeMillis()))
        db.saveTrip(updated)
        state.update { it.copy(activeTrip = updated, trips = db.trips()) }
    }

    fun undoCatch() {
        val t = state.value.activeTrip ?: return
        if (t.catches.isEmpty()) return
        val updated = t.copy(catches = t.catches.dropLast(1))
        db.saveTrip(updated)
        state.update { it.copy(activeTrip = updated, trips = db.trips()) }
    }

    fun endTrip() {
        val t = state.value.activeTrip ?: return
        val done = t.copy(end = System.currentTimeMillis())
        db.saveTrip(done)
        state.update { it.copy(activeTrip = null, trips = db.trips()) }
    }

    fun deleteTrip(id: String) {
        db.deleteTrip(id)
        state.update { it.copy(trips = db.trips(), activeTrip = if (it.activeTrip?.id == id) null else it.activeTrip) }
    }
}
