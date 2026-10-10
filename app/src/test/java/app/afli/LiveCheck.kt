package app.afli

import app.afli.data.Feeds
import app.afli.model.Model
import app.afli.model.Spot
import app.afli.model.Water
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.roundToInt

/**
 * Runs the real parsers and scoring against today's live feeds for Keflavík harbour. Only in CI
 * with LIVE_CHECK=1; the summary goes into the release notes so feed changes show up early.
 */
class LiveCheck {
    @Test fun liveFeedsParseAndScore() {
        assumeTrue(System.getenv("LIVE_CHECK") == "1")
        val spot = Spot("keflavik", "Keflavík harbour", 64.0035, -22.556, Water.SEA, sheltered = true)
        val out = StringBuilder()
        val now = System.currentTimeMillis()

        val fc = runBlocking { Feeds.forecast(spot.lat, spot.lon) }
        val liveR = runBlocking { runCatching { Feeds.live(spot.lat, spot.lon) } }
        val live = liveR.getOrNull()
        val scores = Model.scoreAll(fc.hours, spot)
        val i = fc.hours.indexOfLast { it.t <= now }.coerceAtLeast(0)
        val h = fc.hours[i]
        val s = scores[i]
        fun n(v: Double, d: Int = 1) = if (v.isNaN()) "missing" else "%.${d}f".format(v)

        out.appendLine("### Live feed check at build time (Keflavík harbour)")
        out.appendLine()
        out.appendLine("| Feed | Result |")
        out.appendLine("| --- | --- |")
        out.appendLine("| Open-Meteo forecast (${fc.weatherModel}) | ${fc.hours.size} hours; now wind ${n(h.wind)} m/s, gusts ${n(h.gust)}, pressure ${n(h.pressure, 0)} hPa |")
        out.appendLine("| Open-Meteo Marine | sea ${n(h.sst)} °C, waves ${n(h.wave)} m, sea level ${n(h.seaLevel, 2)} m; ${fc.hours.count { !it.seaLevel.isNaN() }} hours with tide |")
        out.appendLine("| Veðurstofa live | ${live?.let { "${it.station} (${it.distanceKm.roundToInt()} km): ${n(it.wind)} m/s, gusts ${n(it.gust)}, ${n(it.pressure, 0)} hPa" } ?: "missing (${liveR.exceptionOrNull()?.let { it::class.simpleName + ": " + it.message } ?: "no station within 40 km"})"} |")
        // Coast Guard tide table: the next turns at Keflavík from the bundled table.
        val rvk = app.afli.data.TideTable.parse(File("src/main/assets/tides/reykjavik_2026.csv").readText())
        val kef = app.afli.data.TideTable.ports.first { it.name == "Keflavík" }
        val turns = app.afli.data.TideTable.turnsFor(kef, rvk).filter { it.t >= now }.take(2)
        val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM HH:mm").withZone(java.time.ZoneOffset.UTC)
        out.appendLine("| Coast Guard tide table | ${if (turns.isEmpty()) "missing: the table has ended, add next year's" else turns.joinToString(", ") { (if (it.h > 2.2) "high " else "low ") + fmt.format(java.time.Instant.ofEpochMilli(it.t)) + " (" + n(it.h) + " m)" }}; table runs to ${fmt.format(java.time.Instant.ofEpochMilli(rvk.last().t))} |")
        // Hafrannsóknastofnun sea temperature sensor.
        val seaR = runBlocking { runCatching { Feeds.seaTemp(spot.lat, spot.lon) } }
        val sea = seaR.getOrNull()
        out.appendLine("| Hafrannsóknastofnun sea temperature | ${sea?.let { "${it.station} (${it.distanceKm.roundToInt()} km): ${n(it.temp)} °C at ${fmt.format(java.time.Instant.ofEpochMilli(it.time))}" } ?: "missing (${seaR.exceptionOrNull()?.let { it::class.simpleName + ": " + it.message } ?: "no recent reading within 60 km"})"} |")
        out.appendLine("| Model | score ${s.score} (${s.bite.label}), ${s.safety.label}, best ${s.best?.en ?: "none"}; why: ${s.reasons.take(3).joinToString { it.label }} |")
        File("build").mkdirs()
        File("build/live-check.md").writeText(out.toString())
        println(out)

        assertTrue("forecast has hours", fc.hours.size > 100)
        assertTrue("wind present", !h.wind.isNaN())
    }
}
