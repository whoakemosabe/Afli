package app.afli.model

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sun and moon maths on the phone, no network. Accurate to well under a degree for the sun,
 * which is all the light factor needs, and to a few hours for the moon phase.
 */
object Astro {
    private const val DAY_MS = 86_400_000.0
    private fun rad(d: Double) = d * PI / 180.0
    private fun deg(r: Double) = r * 180.0 / PI
    private fun norm360(x: Double) = ((x % 360.0) + 360.0) % 360.0

    /** Days since J2000.0 (2000-01-01 12:00 UTC). */
    private fun daysJ2000(epochMs: Long) = epochMs / DAY_MS + 2440587.5 - 2451545.0

    /** Sun elevation above the horizon in degrees (negative below). */
    fun sunElevation(epochMs: Long, lat: Double, lon: Double): Double {
        val d = daysJ2000(epochMs)
        val g = rad(norm360(357.529 + 0.98560028 * d))
        val q = norm360(280.459 + 0.98564736 * d)
        val l = rad(q + 1.915 * sin(g) + 0.020 * sin(2 * g))
        val e = rad(23.439 - 0.00000036 * d)
        val ra = atan2(cos(e) * sin(l), cos(l))
        val dec = asin(sin(e) * sin(l))
        val gmstHours = ((18.697374558 + 24.06570982441908 * d) % 24.0 + 24.0) % 24.0
        val ha = rad(gmstHours * 15.0 + lon) - ra
        val phi = rad(lat)
        return deg(asin(sin(phi) * sin(dec) + cos(phi) * cos(dec) * cos(ha)))
    }

    private const val SYNODIC = 29.530588853
    private const val KNOWN_NEW_MOON_JD = 2451550.26 // 2000-01-06 18:14 UTC

    /** Moon phase 0..1: 0 new, 0.25 first quarter, 0.5 full, 0.75 last quarter. */
    fun moonPhase(epochMs: Long): Double {
        val jd = epochMs / DAY_MS + 2440587.5
        val p = ((jd - KNOWN_NEW_MOON_JD) / SYNODIC) % 1.0
        return if (p < 0) p + 1.0 else p
    }

    /** Fraction of the moon that is lit, 0..1. */
    fun moonIllumination(epochMs: Long): Double = (1 - cos(2 * PI * moonPhase(epochMs))) / 2

    /**
     * How "springy" the tides are, 0 (neap) .. 1 (spring). Spring tides follow new and full moon
     * by about a day and a half, so the phase is shifted back by that much.
     */
    fun springIndex(epochMs: Long): Double {
        val p = moonPhase(epochMs - (1.5 * DAY_MS).toLong())
        return (1 + cos(4 * PI * p)) / 2
    }

    /**
     * Sunrise and sunset on the local day containing [anyMs], and the stretches when the sun is
     * between 6° below and 8° above the horizon: the low light the score likes. Found by
     * stepping through the day two minutes at a time, so times are good to about a minute.
     */
    fun sunDay(anyMs: Long, lat: Double, lon: Double, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): SunDay {
        val start = java.time.Instant.ofEpochMilli(anyMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val end = start + DAY_MS.toLong()
        val step = 120_000L
        var rise: Long? = null
        var set: Long? = null
        val low = mutableListOf<Pair<Long, Long>>()
        var lowFrom: Long? = null
        var prevT = start
        var prev = sunElevation(start, lat, lon)
        var everUp = prev > HORIZON
        var everDown = prev <= HORIZON
        if (prev in -6.0..8.0) lowFrom = start
        var t = start + step
        while (t <= end) {
            val e = sunElevation(t, lat, lon)
            fun cross(level: Double): Long = prevT + ((level - prev) / (e - prev) * (t - prevT)).toLong()
            if (prev <= HORIZON && e > HORIZON && rise == null) rise = cross(HORIZON)
            if (prev > HORIZON && e <= HORIZON) set = cross(HORIZON)
            val wasLow = prev in -6.0..8.0
            val isLow = e in -6.0..8.0
            if (!wasLow && isLow) lowFrom = cross(if (e < prev) 8.0 else -6.0)
            if (wasLow && !isLow) {
                low += (lowFrom ?: start) to cross(if (e > prev) 8.0 else -6.0)
                lowFrom = null
            }
            if (e > HORIZON) everUp = true else everDown = true
            prev = e
            prevT = t
            t += step
        }
        lowFrom?.let { low += it to end }
        return SunDay(rise, set, allDay = !everDown, never = !everUp, lowLight = low)
    }

    /** The sun's centre is 0.833° below the horizon at sunrise (refraction plus its radius). */
    private const val HORIZON = -0.833
}

data class SunDay(
    val rise: Long?,
    val set: Long?,
    /** The sun never sets (midsummer far north). */
    val allDay: Boolean,
    /** The sun never rises. */
    val never: Boolean,
    /** Dawn and dusk stretches, sun between −6° and 8°. */
    val lowLight: List<Pair<Long, Long>>,
)
