package app.afli.data

import app.afli.model.Hour
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/** Live readings from the nearest Veðurstofa station (Iceland only). */
data class Live(
    val station: String,
    val distanceKm: Double,
    val time: String,
    val wind: Double,
    val gust: Double,
    val windDir: Double,
    val airTemp: Double,
    val pressure: Double,
)

data class Forecast(
    val hours: List<Hour>,
    val weatherModel: String,
    val fetchedAt: Long,
)

/**
 * The phone pulls everything itself; there's no server. Open-Meteo for the forecast (DMI's 2 km
 * HARMONIE model in Iceland, best available elsewhere) and the sea (waves, sea temperature, sea
 * level with tides), and Veðurstofa Íslands for live station readings in Iceland.
 */
object Feeds {
    private const val UA = "Afli (Android; github.com/whoakemosabe/Afli)"

    /** Rough box around Iceland, where DMI HARMONIE and Veðurstofa apply. */
    fun inIceland(lat: Double, lon: Double) = lat in 62.8..67.0 && lon in -25.5..-12.5

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept", "application/json")
        try {
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code from ${URL(url).host}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun f(x: Double) = String.format(Locale.US, "%.4f", x)

    fun forecastUrl(lat: Double, lon: Double): String {
        val model = if (inIceland(lat, lon)) "&models=dmi_seamless" else ""
        return "https://api.open-meteo.com/v1/forecast?latitude=${f(lat)}&longitude=${f(lon)}" +
            "&hourly=temperature_2m,precipitation,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
            "&wind_speed_unit=ms&timeformat=unixtime&past_days=7&forecast_days=7$model"
    }

    fun marineUrl(lat: Double, lon: Double) =
        "https://marine-api.open-meteo.com/v1/marine?latitude=${f(lat)}&longitude=${f(lon)}" +
            "&hourly=wave_height,sea_surface_temperature,sea_level_height_msl" +
            "&timeformat=unixtime&past_days=7&forecast_days=7&cell_selection=sea"

    suspend fun forecast(lat: Double, lon: Double): Forecast = withContext(Dispatchers.IO) {
        coroutineScope {
            val wx = async { get(forecastUrl(lat, lon)) }
            // The sea is optional: inland lakes have no marine data.
            val sea = async { runCatching { get(marineUrl(lat, lon)) }.getOrNull() }
            parse(wx.await(), sea.await(), if (inIceland(lat, lon)) "DMI HARMONIE (2 km)" else "Open-Meteo best match")
        }
    }

    fun parse(weatherJson: String, marineJson: String?, model: String): Forecast {
        val wx = JSONObject(weatherJson).getJSONObject("hourly")
        val times = wx.getJSONArray("time")
        val sea = marineJson?.let { runCatching { JSONObject(it).getJSONObject("hourly") }.getOrNull() }
        val seaIndex = HashMap<Long, Int>()
        sea?.optJSONArray("time")?.let { t -> for (i in 0 until t.length()) seaIndex[t.getLong(i)] = i }
        fun JSONObject?.at(key: String, i: Int?): Double {
            if (this == null || i == null) return Double.NaN
            val a: JSONArray = optJSONArray(key) ?: return Double.NaN
            return if (i < a.length() && !a.isNull(i)) a.optDouble(i, Double.NaN) else Double.NaN
        }
        val hours = (0 until times.length()).map { i ->
            val t = times.getLong(i)
            val si = seaIndex[t]
            Hour(
                t = t * 1000,
                airTemp = wx.at("temperature_2m", i),
                precip = wx.at("precipitation", i),
                cloud = wx.at("cloud_cover", i),
                pressure = wx.at("pressure_msl", i),
                wind = wx.at("wind_speed_10m", i),
                windDir = wx.at("wind_direction_10m", i),
                gust = wx.at("wind_gusts_10m", i),
                wave = sea.at("wave_height", si),
                sst = sea.at("sea_surface_temperature", si),
                seaLevel = sea.at("sea_level_height_msl", si),
            )
        }
        return Forecast(hours, model, System.currentTimeMillis())
    }

    private data class Station(val id: Int, val name: String, val lat: Double, val lon: Double)

    @Volatile private var stations: List<Station>? = null

    private fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val x = (lon2 - lon1) * cos(Math.toRadians((lat1 + lat2) / 2)) * 111.32
        val y = (lat2 - lat1) * 110.57
        return sqrt(x * x + y * y)
    }

    private fun JSONObject.num(vararg keys: String): Double {
        for (k in keys) if (has(k) && !isNull(k)) return optDouble(k, Double.NaN)
        return Double.NaN
    }

    /**
     * Newest 10-minute reading from the nearest Veðurstofa station within 40 km that has
     * reported in the last 3 hours. One call gets the latest reading from every station; the
     * station list (cached) gives their positions.
     */
    suspend fun live(lat: Double, lon: Double): Live? = withContext(Dispatchers.IO) {
        if (!inIceland(lat, lon)) return@withContext null
        val list = stations ?: run {
            val a = JSONArray(get("https://api.vedur.is/weather/stations?active=true"))
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                val sLat = o.num("lat", "latitude")
                val sLon = o.num("lon", "lng", "longitude").let { if (it > 12) -it else it }
                if (sLat.isNaN() || sLon.isNaN()) null else Station(o.optInt("station"), o.optString("name"), sLat, sLon)
            }.also { stations = it }
        }
        val byId = list.associateBy { it.id }
        val latest = JSONArray(get("https://api.vedur.is/weather/observations/aws/10min/latest"))
        val readings = (0 until latest.length()).mapNotNull { i ->
            val o = latest.getJSONObject(i)
            val st = byId[o.optInt("station")] ?: return@mapNotNull null
            val wind = o.num("f")
            if (wind.isNaN()) null else Triple(st, o, km(lat, lon, st.lat, st.lon))
        }
        val (st, o, d) = readings.minByOrNull { it.third } ?: return@withContext null
        if (d > 40) return@withContext null
        Live(
            station = st.name,
            distanceKm = d,
            time = o.optString("time"),
            wind = o.num("f"),
            gust = o.num("fg", "fx"),
            windDir = o.num("d"),
            airTemp = o.num("t"),
            pressure = o.num("p", "ps"),
        )
    }

    /** True if two readings are about the same, used to sanity-check station data. */
    fun close(a: Double, b: Double, tol: Double) = !a.isNaN() && !b.isNaN() && abs(a - b) <= tol
}
