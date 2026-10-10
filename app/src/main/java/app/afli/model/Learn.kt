package app.afli.model

import app.afli.data.Trip
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * What the trip log teaches, kept deliberately modest. Each fish starts from how often it's
 * within reach of the shore; every fish he logs at a spot shifts that spot's mix toward what he
 * actually catches there. With few trips the starting point dominates (a prior worth [PRIOR]
 * fish), so one lucky catch can't swing the score, and the nudge is capped at ±[CAP].
 */
object Learn {
    private const val PRIOR = 8.0
    private const val CAP = 0.3
    private const val NEAR_M = 300.0

    private fun metres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val x = (lon2 - lon1) * cos(Math.toRadians((lat1 + lat2) / 2)) * 111_320.0
        val y = (lat2 - lat1) * 110_570.0
        return sqrt(x * x + y * y)
    }

    /** Trips at this spot: logged against it, or started within 300 m of it. */
    fun tripsAt(spot: Spot, trips: List<Trip>) = trips.filter { it.end != null && (it.spotId == spot.id || metres(spot.lat, spot.lon, it.lat, it.lon) <= NEAR_M) }

    /** Per-species multipliers for this spot from his catches; empty until at least 3 fish. */
    fun boost(spot: Spot, trips: List<Trip>): Map<String, Double> {
        val fish = Fish.forWater(spot.water)
        val catches = tripsAt(spot, trips).flatMap { it.catches }.filter { c -> fish.any { it.id == c.species } }
        if (catches.size < 3) return emptyMap()
        val reachSum = fish.sumOf { it.reach }
        val n = catches.size.toDouble()
        return fish.associate { f ->
            val prior = f.reach / reachSum
            val c = catches.count { it.species == f.id }.toDouble()
            val post = (c + PRIOR * prior) / (n + PRIOR)
            f.id to (post / prior).coerceIn(1 - CAP, 1 + CAP)
        }
    }

    /** Fish per hour over a set of finished trips, or NaN with no time fished. */
    fun rate(trips: List<Trip>): Double {
        val hours = trips.sumOf { ((it.end ?: it.start) - it.start) / 3_600_000.0 }
        return if (hours < 0.05) Double.NaN else trips.sumOf { it.catches.size } / hours
    }
}
