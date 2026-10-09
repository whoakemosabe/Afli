package app.afli.model

import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One hour of conditions at a spot. NaN means "not known". */
data class Hour(
    val t: Long,
    val airTemp: Double = Double.NaN,
    val precip: Double = Double.NaN,
    val cloud: Double = Double.NaN,
    val pressure: Double = Double.NaN,
    val wind: Double = Double.NaN,
    val windDir: Double = Double.NaN,
    val gust: Double = Double.NaN,
    val wave: Double = Double.NaN,
    val sst: Double = Double.NaN,
    val seaLevel: Double = Double.NaN,
)

/** Where he's fishing. [facing] is the compass bearing from the shore out to the water. */
data class Spot(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val water: Water = Water.SEA,
    val facing: Double? = null,
    val sheltered: Boolean = false,
    val builtIn: Boolean = false,
)

enum class Bite(val label: String) { GREAT("Great"), OK("OK"), SLOW("Slow") }

enum class Safety(val label: String) { SAFE("Safe"), CAREFUL("Careful"), STAY_HOME("Stay home") }

/** One "why" chip: a short label, a plain sentence, and whether it helps. */
data class Reason(val label: String, val detail: String, val good: Boolean, val weight: Double)

data class HourScore(
    val t: Long,
    val score: Int,
    val bite: Bite,
    val safety: Safety,
    val safetyWhy: String,
    val best: Species?,
    val perSpecies: List<Pair<Species, Int>>,
    val reasons: List<Reason>,
    /** 0..1 how fast the tide is moving, or null if unknown or a lake. */
    val tideFlow: Double?,
    val sunElevation: Double,
)

data class Window(val start: Long, val end: Long, val peak: Int)

/**
 * The bite score. Every factor is a plain multiplier; the ones that can stop fishing on their
 * own (a fish that isn't there, a lake at night) multiply all the way to zero, like Ljós. The
 * biggest movers become the "why" chips. This is the explainable baseline the catch log will
 * later tune.
 */
object Model {

    fun scoreAll(hours: List<Hour>, spot: Spot, lakeWaterTemp: Double = Double.NaN): List<HourScore> {
        if (hours.isEmpty()) return emptyList()
        val flows = tideFlows(hours)
        return hours.indices.map { i -> score(hours, i, spot, flows[i], lakeWaterTemp) }
    }

    /** 0..1 tide flow per hour: |dh/dt| relative to the fastest flow within ±13 h. */
    fun tideFlows(hours: List<Hour>): List<Double?> {
        val rate = hours.indices.map { i ->
            val a = hours.getOrNull(i - 1)?.seaLevel ?: Double.NaN
            val b = hours.getOrNull(i + 1)?.seaLevel ?: Double.NaN
            val dt = if (i in 1 until hours.lastIndex) (hours[i + 1].t - hours[i - 1].t) / 3_600_000.0 else Double.NaN
            if (a.isNaN() || b.isNaN() || dt.isNaN() || dt <= 0) Double.NaN else abs(b - a) / dt
        }
        return rate.indices.map { i ->
            val r = rate[i]
            if (r.isNaN()) return@map null
            val from = max(0, i - 13)
            val to = min(rate.lastIndex, i + 13)
            val peak = (from..to).map { rate[it] }.filter { !it.isNaN() }.maxOrNull() ?: return@map null
            if (peak <= 0.0) null else (r / peak).coerceIn(0.0, 1.0)
        }
    }

    fun score(hours: List<Hour>, i: Int, spot: Spot, tideFlow: Double?, lakeWaterTemp: Double = Double.NaN): HourScore {
        val h = hours[i]
        val sun = Astro.sunElevation(h.t, spot.lat, spot.lon)
        val reasons = mutableListOf<Reason>()
        val isLake = spot.water == Water.LAKE

        // Tide: moving water is feeding water. Spring tides move more.
        val tide = if (isLake) 1.0 else {
            val spring = Astro.springIndex(h.t)
            val f = if (tideFlow == null) 0.85 else 0.65 + 0.35 * tideFlow
            val m = f * (0.94 + 0.08 * spring)
            when {
                tideFlow == null -> {}
                tideFlow >= 0.6 -> reasons += Reason("Tide's moving", "Fish feed more when the water is flowing in or out.", true, m)
                tideFlow <= 0.25 -> reasons += Reason("Slack tide", "The tide is turning, so the water is barely moving. Fish often go quiet.", false, m)
            }
            if (spring > 0.8) reasons += Reason("Big tides", "Around new and full moon the tides are bigger and the water moves faster.", true, 1.04)
            m
        }

        // Light: dawn and dusk are best; Iceland's summer nights barely get dark.
        val light = when {
            sun in -6.0..8.0 -> {
                reasons += Reason("Dawn or dusk", "Low light is when many fish come closer to shore to feed.", true, 1.08)
                1.0
            }
            sun < -6.0 -> {
                if (isLake) {
                    reasons += Reason("Night", "No lake fishing at night under the Veiðikortið rules.", false, 0.0)
                    0.0
                } else {
                    reasons += Reason("Dark", "It's dark. Cod come shallower at night; most other fish slow down.", false, 0.85)
                    0.85
                }
            }
            else -> {
                val clear = if (h.cloud.isNaN()) 0.5 else 1 - h.cloud / 100.0
                val glare = clear * min(1.0, (sun - 8.0) / 30.0)
                val v = 0.92 - 0.12 * glare
                if (v < 0.86) reasons += Reason("Bright sun", "Strong sun on clear water makes fish shy. Clouds help.", false, v)
                if (!h.cloud.isNaN() && h.cloud > 70) reasons += Reason("Cloudy", "Cloud cover dims the light, which can make fish bolder.", true, 1.03)
                v
            }
        }

        // Pressure: a gentle fall before weather is good; fast changes are not.
        val past = hours.getOrNull(i - 3)?.pressure ?: Double.NaN
        val d3 = if (h.pressure.isNaN() || past.isNaN()) Double.NaN else h.pressure - past
        val pressure = when {
            d3.isNaN() -> 0.95
            d3 < -4.0 -> 0.85.also { reasons += Reason("Storm coming", "Pressure is dropping fast. Fish often feed early, then shut down.", false, it) }
            d3 < -0.8 -> 1.0.also { reasons += Reason("Pressure falling", "A slow drop in air pressure often gets fish feeding.", true, 1.06) }
            d3 <= 1.0 -> 0.94
            else -> 0.84.also { reasons += Reason("Pressure rising", "Rising pressure after a front often means a slow bite.", false, it) }
        }

        // Wind: some chop helps, too much makes it hard to fish.
        val wind = when {
            h.wind.isNaN() -> 0.95
            h.wind < 1.5 -> 0.9.also { reasons += Reason("Flat calm", "No wind and flat water can make fish cautious.", false, it) }
            h.wind <= 8.0 -> {
                val f = spot.facing
                if (f != null && !h.windDir.isNaN()) {
                    val onshore = cos((h.windDir - f) * PI / 180.0)
                    if (onshore > 0.5) {
                        reasons += Reason("Wind in your face", "Wind blowing onto the shore pushes food in, so fish follow.", true, 1.05)
                        1.0
                    } else if (onshore < -0.5) {
                        reasons += Reason("Wind at your back", "Wind from behind makes casting easy.", true, 1.02)
                        0.97
                    } else 0.98
                } else 0.98
            }
            h.wind <= 12.0 -> 0.85.also { reasons += Reason("Windy", "Strong wind makes it hard to feel bites and cast well.", false, it) }
            else -> 0.6.also { reasons += Reason("Very windy", "It's too windy to fish well.", false, it) }
        }

        // Water clarity: heavy rain muddies the water; big waves stir it up at open coast.
        val rain48 = (max(0, i - 48) until i).sumOf { j -> hours[j].precip.takeUnless { it.isNaN() } ?: 0.0 }
        var clarity = 1.0
        if (rain48 > 25.0) {
            clarity *= 0.82
            reasons += Reason("Murky water", "Lots of rain in the last two days has made the water cloudy.", false, 0.82)
        }
        val wave = if (spot.sheltered) h.wave * 0.3 else h.wave
        if (!isLake && !wave.isNaN() && wave > 2.0) {
            clarity *= 0.8
            reasons += Reason("Rough water", "Big waves stir up the bottom and make fishing from shore hard.", false, 0.8)
        }

        // Water temperature for presence: sea temperature, or a stand-in for lakes.
        val waterTemp = if (isLake) lakeWaterTemp else h.sst
        val tempBefore = hours.getOrNull(i - 72)?.sst ?: Double.NaN
        val coldSnap = !isLake && !waterTemp.isNaN() && !tempBefore.isNaN() && waterTemp - tempBefore < -1.5
        if (coldSnap) reasons += Reason("Water got colder", "The sea cooled quickly. Cod don't like sudden drops.", false, 0.85)

        val month = Instant.ofEpochMilli(h.t).atZone(ZoneOffset.UTC).monthValue
        val activity = tide * light * pressure * wind * clarity
        val ranked = Fish.forWater(spot.water).map { s ->
            val inSeason = s.months?.contains(month) ?: true
            var p = s.temperatureFit(waterTemp) * s.reach * if (inSeason) 1.0 else 0.0
            if (sun < -6.0) p *= (1 + s.nightBonus)
            if (coldSnap && s.id == "thorskur") p *= 0.85
            s to (100 * p * activity).roundToInt().coerceIn(0, 100)
        }.sortedByDescending { it.second }
        val top = ranked.firstOrNull()
        val score = top?.second ?: 0

        if (isLake && top != null && top.first.months?.contains(month) == false) {
            reasons += Reason("Out of season", "Lake fishing is roughly May to September.", false, 0.0)
        }
        if (!isLake) {
            val mack = Fish.byId("makrill")!!
            if (!waterTemp.isNaN() && waterTemp < 7.0) {
                reasons += Reason("Too cold for mackerel", "Mackerel like the sea above 8 °C. It's ${"%.1f".format(waterTemp)} °C.", false, 0.97)
            } else if (mack.temperatureFit(waterTemp) >= 1.0) {
                reasons += Reason("Mackerel weather", "The sea is above 8 °C, warm enough for mackerel.", true, 1.05)
            }
        }

        val (safety, why) = safety(h, spot)
        val bite = when {
            score >= 60 -> Bite.GREAT
            score >= 35 -> Bite.OK
            else -> Bite.SLOW
        }
        return HourScore(
            t = h.t,
            score = score,
            bite = bite,
            safety = safety,
            safetyWhy = why,
            best = top?.first?.takeIf { top.second > 0 },
            perSpecies = ranked,
            reasons = reasons.sortedByDescending { abs(ln(max(it.weight, 0.01))) }.distinctBy { it.label },
            tideFlow = if (isLake) null else tideFlow,
            sunElevation = sun,
        )
    }

    /** Safe / Careful / Stay home from gusts, waves and cold wind. Never mixed into the bite score. */
    fun safety(h: Hour, spot: Spot): Pair<Safety, String> {
        val wave = if (spot.sheltered) h.wave * 0.3 else h.wave
        val gust = if (h.gust.isNaN()) h.wind else h.gust
        return when {
            !gust.isNaN() && gust >= 22.0 -> Safety.STAY_HOME to "Gusts up to ${gust.roundToInt()} m/s. Too dangerous on the shore."
            spot.water == Water.SEA && !wave.isNaN() && wave >= 3.5 -> Safety.STAY_HOME to "Waves around ${"%.1f".format(wave)} m. Big waves can sweep people off rocks and piers."
            !gust.isNaN() && gust >= 15.0 -> Safety.CAREFUL to "Strong gusts (${gust.roundToInt()} m/s). Stay back from the edge."
            spot.water == Water.SEA && !wave.isNaN() && wave >= 2.0 -> Safety.CAREFUL to "Waves around ${"%.1f".format(wave)} m. Watch for bigger sets and keep off wet rocks."
            !h.airTemp.isNaN() && h.airTemp < -3.0 && !h.wind.isNaN() && h.wind > 8.0 -> Safety.CAREFUL to "Cold and windy. Dress warm, it feels much colder than ${h.airTemp.roundToInt()} °C."
            else -> Safety.SAFE to "Normal conditions. Still keep an eye on the waves."
        }
    }

    /** The best stretch in the next [hoursAhead] hours that isn't "Stay home". */
    fun nextWindow(scores: List<HourScore>, now: Long, hoursAhead: Int = 48): Window? {
        val ahead = scores.filter { it.t >= now - 3_600_000L && it.t <= now + hoursAhead * 3_600_000L && it.safety != Safety.STAY_HOME }
        val peak = ahead.maxByOrNull { it.score } ?: return null
        if (peak.score <= 0) return null
        val bar = max(peak.score - 10, (peak.score * 0.85).roundToInt())
        val idx = ahead.indexOf(peak)
        var a = idx
        var b = idx
        while (a > 0 && ahead[a - 1].score >= bar && ahead[a].t - ahead[a - 1].t <= 3_600_000L) a--
        while (b < ahead.lastIndex && ahead[b + 1].score >= bar && ahead[b + 1].t - ahead[b].t <= 3_600_000L) b++
        return Window(ahead[a].t, ahead[b].t + 3_600_000L, peak.score)
    }
}
