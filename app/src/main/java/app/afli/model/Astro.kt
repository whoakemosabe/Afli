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
}
