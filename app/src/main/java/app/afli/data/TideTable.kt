package app.afli.data

import android.content.Context
import app.afli.model.Hour
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * The Icelandic Coast Guard's tide tables (Landhelgisgæslan, Sjávarfallatöflur): every high and
 * low at Reykjavík for the year, in Icelandic time (GMT), heights in metres above chart datum.
 * One CSV per year in assets/tides (reykjavik_2026.csv, …); drop in next year's when it's out.
 *
 * Nearby harbours use the Coast Guard's own corrections (Table II): a time shift and a height
 * change that slides between neap and spring tides. Between a high and a low the sea level
 * follows the half-cosine the tables' own Table III is built on. Spots further than [REACH_KM]
 * from a listed harbour keep the Open-Meteo sea model.
 */
object TideTable {
    /** A turn of the tide at Reykjavík: time (ms, UTC) and height above chart datum (m). */
    data class Turn(val t: Long, val h: Double)

    /** A harbour from Table II: minutes after Reykjavík; height changes for spring/neap highs and lows. */
    data class Port(
        val name: String,
        val lat: Double,
        val lon: Double,
        val minutes: Int,
        val springHigh: Double,
        val neapHigh: Double,
        val neapLow: Double,
        val springLow: Double,
    )

    /** Reykjavík's mean spring/neap highs and lows (Table II), for sliding the corrections. */
    private const val MHWS = 4.0
    private const val MHWN = 3.0
    private const val MLWN = 1.3
    private const val MLWS = 0.2

    /** Mean sea level above chart datum at Reykjavík (2.2 m), so heights match the sea model's. */
    private const val MEAN = 2.2

    private const val REACH_KM = 25.0

    // Table II, 2026 edition. Degrees and minutes converted to decimal.
    val ports = listOf(
        Port("Reykjavík", 64 + 9 / 60.0, -(21 + 56 / 60.0), 0, 0.0, 0.0, 0.0, 0.0),
        Port("Hafnarfjörður", 64 + 4 / 60.0, -(21 + 57 / 60.0), -2, 0.0, 0.0, 0.0, 0.0),
        Port("Keflavík", 64 + 0 / 60.0, -(22 + 33 / 60.0), -2, -0.1, -0.1, 0.0, 0.0),
        Port("Sandgerði", 64 + 2 / 60.0, -(22 + 43 / 60.0), -8, -0.3, -0.2, -0.1, 0.0),
        Port("Grindavík", 63 + 50 / 60.0, -(22 + 26 / 60.0), -28, -0.7, -0.5, -0.3, 0.0),
        Port("Akranes", 64 + 19 / 60.0, -(22 + 6 / 60.0), 1, 0.0, 0.0, 0.0, 0.0),
    )

    @Volatile private var turns: List<Turn>? = null

    fun load(context: Context): List<Turn> = turns ?: synchronized(this) {
        turns ?: run {
            val am = context.assets
            val files = am.list("tides").orEmpty().filter { it.startsWith("reykjavik_") && it.endsWith(".csv") }.sorted()
            files.flatMap { f -> am.open("tides/$f").bufferedReader().use { parse(it.readText()) } }.sortedBy { it.t }.also { turns = it }
        }
    }

    fun parse(csv: String): List<Turn> = csv.lineSequence().mapNotNull { line ->
        val p = line.trim().split(',')
        if (p.size != 2) return@mapNotNull null
        val t = runCatching { LocalDateTime.parse(p[0]).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull() ?: return@mapNotNull null
        Turn(t, p[1].toDoubleOrNull() ?: return@mapNotNull null)
    }.toList()

    private fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val x = (lon2 - lon1) * cos(Math.toRadians((lat1 + lat2) / 2)) * 111.32
        val y = (lat2 - lat1) * 110.57
        return sqrt(x * x + y * y)
    }

    /** Average sea-level air pressure in south-west Iceland (hPa), which the tables assume. */
    const val MEAN_PRESSURE = 1006.0

    /** Metres the sea stands above the table for air pressure [hPa]: 1 cm per hPa below average. */
    fun pressureSetup(hPa: Double): Double = (MEAN_PRESSURE - hPa) * 0.01

    /** The listed harbour nearest to a spot, if it's close enough to use. */
    fun portNear(lat: Double, lon: Double): Port? =
        ports.minByOrNull { km(lat, lon, it.lat, it.lon) }?.takeIf { km(lat, lon, it.lat, it.lon) <= REACH_KM }

    /** Reykjavík's turns moved to [port]: shifted in time, heights corrected for spring or neap. */
    fun turnsFor(port: Port, rvk: List<Turn>): List<Turn> = rvk.mapIndexed { i, tr ->
        val prev = rvk.getOrNull(i - 1)?.h ?: Double.NaN
        val next = rvk.getOrNull(i + 1)?.h ?: Double.NaN
        val high = (prev.isNaN() || tr.h > prev) && (next.isNaN() || tr.h > next)
        val dh = if (high) {
            val k = ((tr.h - MHWN) / (MHWS - MHWN)).coerceIn(0.0, 1.0)
            port.neapHigh + (port.springHigh - port.neapHigh) * k
        } else {
            val k = ((MLWN - tr.h) / (MLWN - MLWS)).coerceIn(0.0, 1.0)
            port.neapLow + (port.springLow - port.neapLow) * k
        }
        Turn(tr.t + port.minutes * 60_000L, tr.h + dh)
    }

    /** Sea level (m above mean sea level) at time [t] from the turns around it, or NaN outside them. */
    fun levelAt(turns: List<Turn>, t: Long): Double {
        var lo = 0
        var hi = turns.lastIndex
        if (turns.size < 2 || t < turns[0].t || t > turns[hi].t) return Double.NaN
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (turns[mid].t <= t) lo = mid else hi = mid
        }
        val a = turns[lo]
        val b = turns[hi]
        val f = (t - a.t).toDouble() / (b.t - a.t).coerceAtLeast(1)
        return a.h + (b.h - a.h) * (1 - cos(PI * f)) / 2 - MEAN
    }

    /**
     * Replaces the sea model's level with the tide table wherever the table covers the hour.
     * Returns null if the spot isn't near a listed harbour or the table doesn't reach these dates.
     */
    fun apply(context: Context, hours: List<Hour>, lat: Double, lon: Double): Pair<List<Hour>, Port>? {
        val port = portNear(lat, lon) ?: return null
        val local = turnsFor(port, load(context))
        if (local.isEmpty()) return null
        var any = false
        val out = hours.map { h ->
            val lv = levelAt(local, h.t)
            if (lv.isNaN()) h else {
                any = true
                h.copy(seaLevel = lv)
            }
        }
        return if (any) out to port else null
    }
}
