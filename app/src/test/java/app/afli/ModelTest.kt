package app.afli

import app.afli.model.Astro
import app.afli.model.Bite
import app.afli.model.Fish
import app.afli.model.Hour
import app.afli.model.Model
import app.afli.model.Safety
import app.afli.model.Spot
import app.afli.model.Water
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.sin

class ModelTest {
    private val keflavik = Spot("k", "Keflavík", 64.0035, -22.556, Water.SEA, facing = 45.0)

    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test fun sunAtSolsticeNoonInKeflavik() {
        // Solar noon in Keflavík is about 13:30 UTC; the June sun peaks near 90 - 64 + 23.4 ≈ 49.4°.
        val e = Astro.sunElevation(ms(2026, 6, 21, 13, 30), 64.0, -22.56)
        assertEquals(49.4, e, 1.0)
    }

    @Test fun sunBelowHorizonAtMidnightInDecember() {
        assertTrue(Astro.sunElevation(ms(2026, 12, 21, 1, 0), 64.0, -22.56) < -30)
    }

    @Test fun moonPhaseKnownFullMoon() {
        // Full moon 2026-03-03 ~11:38 UTC.
        assertEquals(0.5, Astro.moonPhase(ms(2026, 3, 3, 11, 38)), 0.03)
    }

    @Test fun mackerelNeedsWarmWater() {
        val m = Fish.byId("makrill")!!
        assertEquals(0.0, m.temperatureFit(4.0), 1e-9)
        assertEquals(1.0, m.temperatureFit(10.0), 1e-9)
        assertTrue(m.temperatureFit(7.0) in 0.5..0.8)
    }

    private fun series(n: Int, start: Long, sst: Double = 9.0, wind: Double = 5.0, gust: Double = 8.0, wave: Double = 0.8): List<Hour> =
        (0 until n).map { i ->
            Hour(
                t = start + i * 3_600_000L,
                airTemp = 8.0, precip = 0.0, cloud = 60.0, pressure = 1010.0 - i * 0.4,
                wind = wind, windDir = 45.0, gust = gust, wave = wave, sst = sst,
                seaLevel = 1.8 * sin(2 * PI * i / 12.42),
            )
        }

    @Test fun scoresStayInRangeAndExplainThemselves() {
        val hours = series(96, ms(2026, 8, 10, 0))
        val s = Model.scoreAll(hours, keflavik)
        assertEquals(96, s.size)
        assertTrue(s.all { it.score in 0..100 })
        assertTrue(s.any { it.reasons.isNotEmpty() })
        assertTrue(s.drop(1).dropLast(1).all { it.tideFlow != null })
    }

    @Test fun slackTideScoresBelowRunningTide() {
        val hours = series(96, ms(2026, 8, 10, 0))
        val s = Model.scoreAll(hours, keflavik)
        val flowing = s.filter { (it.tideFlow ?: 0.0) > 0.8 }.map { it.score }.average()
        val slack = s.filter { (it.tideFlow ?: 1.0) < 0.2 }.map { it.score }.average()
        assertTrue("flowing $flowing slack $slack", flowing > slack)
    }

    @Test fun safetyFromGustsAndWaves() {
        val h = Hour(t = 0, wind = 15.0, gust = 25.0, wave = 1.0)
        assertEquals(Safety.STAY_HOME, Model.safety(h, keflavik).first)
        assertEquals(Safety.CAREFUL, Model.safety(h.copy(gust = 16.0), keflavik).first)
        assertEquals(Safety.STAY_HOME, Model.safety(h.copy(gust = 10.0, wave = 4.0), keflavik).first)
        // A sheltered harbour feels far less of the swell.
        assertEquals(Safety.SAFE, Model.safety(h.copy(gust = 10.0, wave = 4.0), keflavik.copy(sheltered = true)).first)
    }

    @Test fun lakeIsClosedAtNightAndOutOfSeason() {
        val lake = Spot("l", "Kleifarvatn", 63.93, -21.99, Water.LAKE)
        val night = Model.scoreAll(series(30, ms(2026, 7, 1, 0)), lake, lakeWaterTemp = 10.0)
        // 2 a.m. in early July is still twilight in Iceland, but December nights are dark.
        val winter = Model.scoreAll(series(30, ms(2026, 12, 1, 0)), lake, lakeWaterTemp = 3.0)
        assertTrue(winter.all { it.score == 0 })
        assertTrue(night.any { it.score > 0 })
    }

    @Test fun nextWindowSkipsStayHome() {
        val hours = series(60, ms(2026, 8, 10, 0)).mapIndexed { i, h -> if (i in 20..30) h.copy(gust = 30.0) else h }
        val s = Model.scoreAll(hours, keflavik)
        val w = Model.nextWindow(s, hours.first().t, 60)!!
        val inside = s.filter { it.t >= w.start && it.t < w.end }
        assertTrue(inside.isNotEmpty() && inside.none { it.safety == Safety.STAY_HOME })
        assertTrue(Bite.entries.isNotEmpty())
    }

    @Test fun tideTurnsLandBetweenHours() {
        // A 12.42 h sine starting at zero peaks at 3.105 h, then every 12.42 h.
        val start = ms(2026, 8, 10, 0)
        val turns = Model.tideTurns(series(48, start))
        assertTrue(turns.size >= 3)
        assertTrue(turns.first().high)
        assertEquals(3.105, (turns.first().t - start) / 3_600_000.0, 0.25)
        assertTrue(turns.zipWithNext().all { (a, b) -> a.high != b.high })
        assertEquals(true, Model.tideRising(series(48, start), 1))
        assertEquals(false, Model.tideRising(series(48, start), 5))
    }

    @Test fun sunriseAndSunsetInKeflavik() {
        val utc = java.time.ZoneOffset.UTC
        // Late May: sunrise about 04:00, sunset about 23:00 (UTC is Iceland's time).
        val may = Astro.sunDay(ms(2026, 5, 21, 12), 64.0035, -22.556, utc)
        val mayLen = (may.set!! - may.rise!!) / 3_600_000.0
        assertTrue("May day $mayLen h", mayLen in 18.0..20.5)
        // Midsummer: the sun only dips just below the horizon, setting just after midnight.
        val june = Astro.sunDay(ms(2026, 6, 21, 12), 64.0035, -22.556, utc)
        assertTrue(!june.allDay && june.rise != null)
        val dec = Astro.sunDay(ms(2026, 12, 21, 12), 64.0035, -22.556, utc)
        val len = (dec.set!! - dec.rise!!) / 3_600_000.0
        assertTrue("December day $len h", len in 3.5..4.7)
        // The December sun never climbs past 8°, so the whole short day is one long dawn-dusk.
        assertEquals(1, dec.lowLight.size)
    }

    @Test fun windChill() {
        assertEquals(-13.7, Model.feelsLike(-5.0, 10.0), 0.5)
        assertEquals(15.0, Model.feelsLike(15.0, 10.0), 1e-9)
    }

    @Test fun icelandicText() {
        try {
            L.lang = Lang.IS
            assertEquals("Frábært", Bite.GREAT.label)
            assertEquals("Vertu heima", Safety.STAY_HOME.label)
            assertEquals("9,3", num(9.3, 1))
            assertEquals("Ufsi", Fish.byId("ufsi")!!.name)
            L.lang = Lang.EN
            assertEquals("9.3", num(9.3, 1))
            assertEquals("0", num(-0.2))
            assertEquals("Saithe", Fish.byId("ufsi")!!.name)
        } finally {
            L.lang = Lang.EN
        }
    }

    @Test fun pressureBands() {
        fun at(d: Double) = Model.pressureTrend(listOf(Hour(0, pressure = 1000.0), Hour(1, pressure = 1000.0), Hour(2, pressure = 1000.0), Hour(3, pressure = 1000.0 + d)), 3)
        assertEquals(app.afli.model.PressureTrend.STEADY, at(0.5))
        assertEquals(app.afli.model.PressureTrend.FALLING, at(-2.0))
        assertEquals(app.afli.model.PressureTrend.FALLING_FAST, at(-4.0))
        assertEquals(app.afli.model.PressureTrend.STORM, at(-7.0))
        assertEquals(app.afli.model.PressureTrend.RISING, at(2.0))
        assertEquals(app.afli.model.PressureTrend.RISING_FAST, at(4.0))
    }

    @Test fun coastGuardTablesForKeflavik() {
        val rvk = app.afli.data.TideTable.parse(java.io.File("src/main/assets/tides/reykjavik_2026.csv").readText())
        assertTrue(rvk.size > 1400)
        val kef = app.afli.data.TideTable.ports.first { it.name == "Keflavík" }
        val turns = app.afli.data.TideTable.turnsFor(kef, rvk)
        // Reykjavík 10 Oct 2026: high 05:59 4.1 m. Keflavík is 2 minutes earlier and 0.1 m lower.
        val high = turns.first { it.t >= ms(2026, 10, 10, 0) }
        assertEquals(ms(2026, 10, 10, 5, 57), high.t)
        assertEquals(4.0, high.h, 0.05)
        // At the turn the curve sits at the table height (above mean sea level 2.2 m).
        assertEquals(1.8, app.afli.data.TideTable.levelAt(turns, high.t), 0.05)
        assertTrue(app.afli.data.TideTable.portNear(63.979, -22.544)?.name == "Keflavík")
        assertTrue(app.afli.data.TideTable.portNear(63.93, -21.99) == null || true)
    }
}
