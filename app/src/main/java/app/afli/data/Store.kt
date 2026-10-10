package app.afli.data

import android.content.Context
import app.afli.model.Spot
import app.afli.model.Water
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.cos
import kotlin.math.sqrt

/** One catch inside a trip. */
data class Catch(val species: String, val time: Long, val sizeCm: Int? = null)

/** Conditions saved the moment a trip starts, so the log can learn later. */
data class Snapshot(
    val score: Int,
    val wind: Double,
    val windDir: Double,
    val pressure: Double,
    val pressure3h: Double,
    val sst: Double,
    val wave: Double,
    val tideFlow: Double,
    val sunElevation: Double,
)

data class Trip(
    val id: String,
    val spotId: String,
    val spotName: String,
    val lat: Double,
    val lon: Double,
    val start: Long,
    val end: Long?,
    val catches: List<Catch>,
    val snapshot: Snapshot?,
)

/**
 * Everything Afli keeps is on the phone: saved spots, trips and small settings. Plain JSON files
 * in the app's private folder. No account, no server.
 */
class Store(context: Context) {
    private val dir = context.filesDir
    private val prefs = context.getSharedPreferences("afli", Context.MODE_PRIVATE)

    // Approximate harbour positions. They only matter until he logs trips there, when the spot
    // moves to where he actually stands.
    val defaults = listOf(
        Spot("keflavik", "Keflavík harbour", 64.0035, -22.5560, Water.SEA, sheltered = true, builtIn = true),
        Spot("njardvik", "Njarðvík harbour", 63.9790, -22.5440, Water.SEA, sheltered = true, builtIn = true),
        Spot("kleifarvatn", "Kleifarvatn", 63.9300, -21.9900, Water.LAKE, builtIn = true),
        Spot("seltjorn", "Seltjörn", 63.9740, -22.4980, Water.LAKE, builtIn = true),
    )

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) = prefs.edit().putBoolean("onboarded", v).apply()

    fun tipSeen(key: String) = prefs.getBoolean("tip_$key", false)
    fun markTip(key: String) = prefs.edit().putBoolean("tip_$key", true).apply()
    fun resetTips() {
        val e = prefs.edit()
        prefs.all.keys.filter { it.startsWith("tip_") }.forEach { e.remove(it) }
        e.apply()
    }

    /** "en" or "is" once chosen in Settings; null means follow the phone's language. */
    var lang: String?
        get() = prefs.getString("lang", null)
        set(v) = prefs.edit().putString("lang", v).apply()

    var selectedSpot: String?
        get() = prefs.getString("spot", null)
        set(v) = prefs.edit().putString("spot", v).apply()

    // ---- spots ----

    private val spotsFile get() = File(dir, "spots.json")

    /** Spots he has saved or corrected, merged over the built-in defaults by id. */
    fun spots(): List<Spot> {
        val saved = runCatching { JSONArray(spotsFile.readText()) }.getOrNull()
        val mine = saved?.let { a -> (0 until a.length()).map { spotFrom(a.getJSONObject(it)) } } ?: emptyList()
        val ids = mine.map { it.id }.toSet()
        return mine + defaults.filter { it.id !in ids }
    }

    fun saveSpot(spot: Spot) {
        val all = spots().filter { it.id != spot.id } + spot
        val a = JSONArray()
        all.filter { !it.builtIn || it != defaults.firstOrNull { d -> d.id == it.id } }.forEach { a.put(spotJson(it)) }
        spotsFile.writeText(a.toString())
    }

    /** The saved spot within [meters] of a position, if any. */
    fun spotNear(lat: Double, lon: Double, meters: Double = 300.0): Spot? =
        spots().map { it to distanceM(lat, lon, it.lat, it.lon) }.filter { it.second <= meters }.minByOrNull { it.second }?.first

    private fun spotJson(s: Spot) = JSONObject()
        .put("id", s.id).put("name", s.name).put("lat", s.lat).put("lon", s.lon)
        .put("water", s.water.name).put("facing", s.facing ?: JSONObject.NULL)
        .put("sheltered", s.sheltered).put("builtIn", s.builtIn)

    private fun spotFrom(o: JSONObject) = Spot(
        id = o.getString("id"),
        name = o.getString("name"),
        lat = o.getDouble("lat"),
        lon = o.getDouble("lon"),
        water = runCatching { Water.valueOf(o.getString("water")) }.getOrDefault(Water.SEA),
        facing = if (o.isNull("facing")) null else o.optDouble("facing"),
        sheltered = o.optBoolean("sheltered"),
        builtIn = o.optBoolean("builtIn"),
    )

    // ---- trips ----

    private val tripsFile get() = File(dir, "trips.json")

    fun trips(): List<Trip> {
        val a = runCatching { JSONArray(tripsFile.readText()) }.getOrNull() ?: return emptyList()
        return (0 until a.length()).mapNotNull { runCatching { tripFrom(a.getJSONObject(it)) }.getOrNull() }
            .sortedByDescending { it.start }
    }

    fun saveTrip(trip: Trip) {
        val all = trips().filter { it.id != trip.id } + trip
        val a = JSONArray()
        all.sortedBy { it.start }.forEach { a.put(tripJson(it)) }
        tripsFile.writeText(a.toString())
    }

    /** Deletes every trip. */
    fun clearTrips() {
        tripsFile.delete()
    }

    /** Forgets the spots saved from trips; the built-in harbours and lakes stay. */
    fun forgetSpots() {
        spotsFile.delete()
        selectedSpot = null
    }

    fun deleteTrip(id: String) {
        val a = JSONArray()
        trips().filter { it.id != id }.sortedBy { it.start }.forEach { a.put(tripJson(it)) }
        tripsFile.writeText(a.toString())
    }

    private fun tripJson(t: Trip): JSONObject {
        val c = JSONArray()
        t.catches.forEach { c.put(JSONObject().put("species", it.species).put("time", it.time).put("sizeCm", it.sizeCm ?: JSONObject.NULL)) }
        return JSONObject()
            .put("id", t.id).put("spotId", t.spotId).put("spotName", t.spotName)
            .put("lat", t.lat).put("lon", t.lon).put("start", t.start).put("end", t.end ?: JSONObject.NULL)
            .put("catches", c)
            .put("snapshot", t.snapshot?.let { s ->
                JSONObject().put("score", s.score).put("wind", s.wind.orNull()).put("windDir", s.windDir.orNull())
                    .put("pressure", s.pressure.orNull()).put("pressure3h", s.pressure3h.orNull()).put("sst", s.sst.orNull())
                    .put("wave", s.wave.orNull()).put("tideFlow", s.tideFlow.orNull()).put("sun", s.sunElevation.orNull())
            } ?: JSONObject.NULL)
    }

    private fun Double.orNull(): Any = if (isNaN()) JSONObject.NULL else this

    private fun JSONObject.d(k: String) = if (isNull(k)) Double.NaN else optDouble(k, Double.NaN)

    private fun tripFrom(o: JSONObject): Trip {
        val c = o.optJSONArray("catches") ?: JSONArray()
        return Trip(
            id = o.getString("id"),
            spotId = o.optString("spotId"),
            spotName = o.optString("spotName"),
            lat = o.optDouble("lat"),
            lon = o.optDouble("lon"),
            start = o.getLong("start"),
            end = if (o.isNull("end")) null else o.getLong("end"),
            catches = (0 until c.length()).map { i ->
                val x = c.getJSONObject(i)
                Catch(x.getString("species"), x.getLong("time"), if (x.isNull("sizeCm")) null else x.optInt("sizeCm"))
            },
            snapshot = o.optJSONObject("snapshot")?.let { s ->
                Snapshot(s.optInt("score"), s.d("wind"), s.d("windDir"), s.d("pressure"), s.d("pressure3h"), s.d("sst"), s.d("wave"), s.d("tideFlow"), s.d("sun"))
            },
        )
    }

    companion object {
        fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val x = (lon2 - lon1) * cos(Math.toRadians((lat1 + lat2) / 2)) * 111_320.0
            val y = (lat2 - lat1) * 110_570.0
            return sqrt(x * x + y * y)
        }
    }
}
