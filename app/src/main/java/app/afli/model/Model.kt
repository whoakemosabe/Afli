package app.afli.model

import app.afli.Tx
import app.afli.num
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

enum class Bite(private val tx: Tx) {
    GREAT(Tx("Great", "Frábært")), OK(Tx("OK", "Ágætt")), SLOW(Tx("Slow", "Rólegt"));
    val label: String get() = tx.toString()
}

enum class Safety(private val tx: Tx) {
    SAFE(Tx("Safe", "Öruggt")), CAREFUL(Tx("Careful", "Varúð")), STAY_HOME(Tx("Stay home", "Vertu heima"));
    val label: String get() = tx.toString()
}

/** One "why" chip: a short label, a plain sentence, and whether it helps. Both languages. */
class Reason(private val labelTx: Tx, private val detailTx: Tx, val good: Boolean, val weight: Double) {
    val label: String get() = labelTx.toString()
    val detail: String get() = detailTx.toString()
    /** Language-independent identity, so the same reason isn't listed twice. */
    val key: String get() = labelTx.en
}

data class HourScore(
    val t: Long,
    val score: Int,
    val bite: Bite,
    val safety: Safety,
    private val safetyWhyTx: Tx,
    val best: Species?,
    val perSpecies: List<Pair<Species, Int>>,
    val reasons: List<Reason>,
    /** 0..1 how fast the tide is moving, or null if unknown or a lake. */
    val tideFlow: Double?,
    val sunElevation: Double,
) {
    val safetyWhy: String get() = safetyWhyTx.toString()
}

data class Window(val start: Long, val end: Long, val peak: Int)

/** A high or low tide at time [t] with sea level [level] (m, relative to mean sea level). */
data class TideTurn(val t: Long, val high: Boolean, val level: Double)

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
                tideFlow >= 0.6 -> reasons += Reason(Tx("Tide's moving", "Sjórinn á hreyfingu"), Tx("Fish feed more when the water is flowing in or out.", "Fiskurinn tekur betur þegar sjórinn streymir að eða frá."), true, m)
                tideFlow <= 0.25 -> reasons += Reason(Tx("Slack tide", "Liggjandi"), Tx("The tide is turning, so the water is barely moving. Fish often go quiet.", "Það eru fallaskipti og sjórinn hreyfist varla. Fiskurinn er þá oft rólegur."), false, m)
            }
            if (spring > 0.8) reasons += Reason(Tx("Big tides", "Stórstreymi"), Tx("Around new and full moon the tides are bigger and the water moves faster.", "Í kringum nýtt og fullt tungl eru sjávarföllin meiri og sjórinn streymir hraðar."), true, 1.04)
            m
        }

        // Light: dawn and dusk are best; Iceland's summer nights barely get dark.
        val light = when {
            sun in -6.0..8.0 -> {
                reasons += Reason(Tx("Dawn or dusk", "Ljósaskipti"), Tx("Low light is when many fish come closer to shore to feed.", "Í lítilli birtu kemur fiskurinn oft nær landi til að éta."), true, 1.08)
                1.0
            }
            sun < -6.0 -> {
                if (isLake) {
                    reasons += Reason(Tx("Night", "Nótt"), Tx("No lake fishing at night under the Veiðikortið rules.", "Engin veiði í vötnum á nóttunni samkvæmt reglum Veiðikortsins."), false, 0.0)
                    0.0
                } else {
                    reasons += Reason(Tx("Dark", "Myrkur"), Tx("It's dark. Cod come shallower at night; most other fish slow down.", "Það er dimmt. Þorskurinn kemur grynnra á nóttunni en flestir aðrir fiskar róast."), false, 0.85)
                    0.85
                }
            }
            else -> {
                val clear = if (h.cloud.isNaN()) 0.5 else 1 - h.cloud / 100.0
                val glare = clear * min(1.0, (sun - 8.0) / 30.0)
                val v = 0.92 - 0.12 * glare
                if (v < 0.86) reasons += Reason(Tx("Bright sun", "Sterk sól"), Tx("Strong sun on clear water makes fish shy. Clouds help.", "Sterk sól á tæru vatni gerir fiskinn styggan. Ský hjálpa."), false, v)
                if (!h.cloud.isNaN() && h.cloud > 70) reasons += Reason(Tx("Cloudy", "Skýjað"), Tx("Cloud cover dims the light, which can make fish bolder.", "Skýin dempa birtuna og þá verður fiskurinn oft djarfari."), true, 1.03)
                v
            }
        }

        // Pressure: a gentle fall before weather is good; fast changes are not.
        val past = hours.getOrNull(i - 3)?.pressure ?: Double.NaN
        val d3 = if (h.pressure.isNaN() || past.isNaN()) Double.NaN else h.pressure - past
        val pressure = when {
            d3.isNaN() -> 0.95
            d3 < -4.0 -> 0.85.also { reasons += Reason(Tx("Storm coming", "Óveður í aðsigi"), Tx("Pressure is dropping fast. Fish often feed early, then shut down.", "Loftþrýstingur fellur hratt. Fiskurinn tekur oft fyrst en hættir svo."), false, it) }
            d3 < -0.8 -> 1.0.also { reasons += Reason(Tx("Pressure falling", "Þrýstingur fellur"), Tx("A slow drop in air pressure often gets fish feeding.", "Hægt fallandi loftþrýstingur fær fiskinn oft til að taka."), true, 1.06) }
            d3 <= 1.0 -> 0.94
            else -> 0.84.also { reasons += Reason(Tx("Pressure rising", "Þrýstingur hækkar"), Tx("Rising pressure after a front often means a slow bite.", "Hækkandi þrýstingur eftir skil þýðir oft dræma töku."), false, it) }
        }

        // Wind: some chop helps, too much makes it hard to fish.
        val wind = when {
            h.wind.isNaN() -> 0.95
            h.wind < 1.5 -> 0.9.also { reasons += Reason(Tx("Flat calm", "Logn"), Tx("No wind and flat water can make fish cautious.", "Í logni og á sléttu yfirborði er fiskurinn oft var um sig."), false, it) }
            h.wind <= 8.0 -> {
                val f = spot.facing
                if (f != null && !h.windDir.isNaN()) {
                    val onshore = cos((h.windDir - f) * PI / 180.0)
                    if (onshore > 0.5) {
                        reasons += Reason(Tx("Wind in your face", "Vindur að landi"), Tx("Wind blowing onto the shore pushes food in, so fish follow.", "Vindur sem blæs að landi ber æti að og fiskurinn fylgir."), true, 1.05)
                        1.0
                    } else if (onshore < -0.5) {
                        reasons += Reason(Tx("Wind at your back", "Vindur í bakið"), Tx("Wind from behind makes casting easy.", "Vindur aftan frá auðveldar köstin."), true, 1.02)
                        0.97
                    } else 0.98
                } else 0.98
            }
            h.wind <= 12.0 -> 0.85.also { reasons += Reason(Tx("Windy", "Hvasst"), Tx("Strong wind makes it hard to feel bites and cast well.", "Í miklum vindi er erfitt að finna tökur og kasta vel."), false, it) }
            else -> 0.6.also { reasons += Reason(Tx("Very windy", "Mjög hvasst"), Tx("It's too windy to fish well.", "Það er of hvasst til að veiða vel."), false, it) }
        }

        // Water clarity: heavy rain muddies the water; big waves stir it up at open coast.
        val rain48 = (max(0, i - 48) until i).sumOf { j -> hours[j].precip.takeUnless { it.isNaN() } ?: 0.0 }
        var clarity = 1.0
        if (rain48 > 25.0) {
            clarity *= 0.82
            reasons += Reason(Tx("Murky water", "Gruggugt vatn"), Tx("Lots of rain in the last two days has made the water cloudy.", "Mikil rigning síðustu tvo daga hefur gruggað vatnið."), false, 0.82)
        }
        val wave = if (spot.sheltered) h.wave * 0.3 else h.wave
        if (!isLake && !wave.isNaN() && wave > 2.0) {
            clarity *= 0.8
            reasons += Reason(Tx("Rough water", "Mikil alda"), Tx("Big waves stir up the bottom and make fishing from shore hard.", "Stórar öldur róta upp botninum og gera veiði frá landi erfiða."), false, 0.8)
        }

        // Water temperature for presence: sea temperature, or a stand-in for lakes.
        val waterTemp = if (isLake) lakeWaterTemp else h.sst
        val tempBefore = hours.getOrNull(i - 72)?.sst ?: Double.NaN
        val coldSnap = !isLake && !waterTemp.isNaN() && !tempBefore.isNaN() && waterTemp - tempBefore < -1.5
        if (coldSnap) reasons += Reason(Tx("Water got colder", "Sjórinn kólnaði"), Tx("The sea cooled quickly. Cod don't like sudden drops.", "Sjórinn kólnaði hratt. Þorskurinn kann illa við snögga kólnun."), false, 0.85)

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
            reasons += Reason(Tx("Out of season", "Utan tímabils"), Tx("Lake fishing is roughly May to September.", "Veiði í vötnum er um það bil frá maí til september."), false, 0.0)
        }
        if (!isLake) {
            val mack = Fish.byId("makrill")!!
            if (!waterTemp.isNaN() && waterTemp < 7.0) {
                reasons += Reason(Tx("Too cold for mackerel", "Of kalt fyrir makríl"), Tx("Mackerel like the sea above 8 °C. It's ${num(waterTemp, 1)} °C.", "Makríllinn vill sjó yfir 8 °C. Sjórinn er núna ${num(waterTemp, 1)} °C."), false, 0.97)
            } else if (mack.temperatureFit(waterTemp) >= 1.0) {
                reasons += Reason(Tx("Mackerel weather", "Makrílveður"), Tx("The sea is above 8 °C, warm enough for mackerel.", "Sjórinn er yfir 8 °C, nógu hlýr fyrir makríl."), true, 1.05)
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
            safetyWhyTx = why,
            best = top?.first?.takeIf { top.second > 0 },
            perSpecies = ranked,
            reasons = reasons.sortedByDescending { abs(ln(max(it.weight, 0.01))) }.distinctBy { it.key },
            tideFlow = if (isLake) null else tideFlow,
            sunElevation = sun,
        )
    }

    /** Safe / Careful / Stay home from gusts, waves and cold wind. Never mixed into the bite score. */
    fun safety(h: Hour, spot: Spot): Pair<Safety, Tx> {
        val wave = if (spot.sheltered) h.wave * 0.3 else h.wave
        val gust = if (h.gust.isNaN()) h.wind else h.gust
        val g = num(gust)
        val w = num(wave, 1)
        return when {
            !gust.isNaN() && gust >= 22.0 -> Safety.STAY_HOME to Tx(
                "Gusts up to $g m/s. Too dangerous on the shore.",
                "Hviður allt að $g m/s. Of hættulegt að veiða frá landi.",
            )
            spot.water == Water.SEA && !wave.isNaN() && wave >= 3.5 -> Safety.STAY_HOME to Tx(
                "Waves around $w m. Big waves can sweep people off rocks and piers.",
                "Öldur um $w m. Stórar öldur geta hrifið fólk af klöppum og bryggjum.",
            )
            !gust.isNaN() && gust >= 15.0 -> Safety.CAREFUL to Tx(
                "Strong gusts ($g m/s). Stay back from the edge.",
                "Sterkar hviður ($g m/s). Haltu þig frá brúninni.",
            )
            spot.water == Water.SEA && !wave.isNaN() && wave >= 2.0 -> Safety.CAREFUL to Tx(
                "Waves around $w m. Watch for bigger sets and keep off wet rocks.",
                "Öldur um $w m. Passaðu þig á stærri öldum og haltu þig frá blautum klöppum.",
            )
            !h.airTemp.isNaN() && h.airTemp < -3.0 && !h.wind.isNaN() && h.wind > 8.0 -> Safety.CAREFUL to Tx(
                "Cold and windy. Dress warm, it feels much colder than ${num(h.airTemp)} °C.",
                "Kalt og hvasst. Klæddu þig vel, vindurinn gerir það mun kaldara en ${num(h.airTemp)} °C.",
            )
            else -> Safety.SAFE to Tx(
                "Normal conditions. Still keep an eye on the waves.",
                "Venjulegar aðstæður. Fylgstu samt með öldunum.",
            )
        }
    }

    /**
     * Highs and lows of the tide. Each turn's time is found between hourly points from the
     * parabola through the three around it, so it's good to roughly 15 minutes.
     */
    fun tideTurns(hours: List<Hour>): List<TideTurn> {
        val out = mutableListOf<TideTurn>()
        for (i in 1 until hours.lastIndex) {
            val a = hours[i - 1].seaLevel
            val b = hours[i].seaLevel
            val c = hours[i + 1].seaLevel
            if (a.isNaN() || b.isNaN() || c.isNaN()) continue
            val high = b >= a && b > c
            val low = b <= a && b < c
            if (!high && !low) continue
            val curve = a - 2 * b + c
            val shift = if (curve != 0.0) ((a - c) / (2 * curve)).coerceIn(-0.5, 0.5) else 0.0
            out += TideTurn(hours[i].t + (shift * 3_600_000L).toLong(), high, b)
        }
        return out
    }

    /** Whether the tide is coming in at hour [i]: true rising, false falling, null unknown. */
    fun tideRising(hours: List<Hour>, i: Int): Boolean? {
        val a = hours.getOrNull(i - 1)?.seaLevel ?: Double.NaN
        val b = hours.getOrNull(i + 1)?.seaLevel ?: Double.NaN
        if (a.isNaN() || b.isNaN()) return null
        return b > a
    }

    /**
     * What the cold feels like with the wind (the North American wind chill index, used by
     * Veðurstofa too). Only meaningful at 10 °C or below with some wind; otherwise the air
     * temperature itself.
     */
    fun feelsLike(airC: Double, windMs: Double): Double {
        if (airC.isNaN() || windMs.isNaN()) return airC
        val kmh = windMs * 3.6
        if (airC > 10.0 || kmh < 4.8) return airC
        val v = Math.pow(kmh, 0.16)
        return 13.12 + 0.6215 * airC - 11.37 * v + 0.3965 * airC * v
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
