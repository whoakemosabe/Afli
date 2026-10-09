package app.afli.model

enum class Water { SEA, LAKE }

/**
 * A fish Afli can score. Temperatures are water temperatures in °C: zero below [lo], rising to
 * full at [optLo], full up to [optHi], falling to zero at [hi]. [reach] is how often it's within
 * casting range from shore (deep-water fish are rare from piers). Facts come from English
 * Wikipedia and Hafrannsóknastofnun (checked 2026-10-09); the numbers are starting points that
 * the catch log will tune.
 */
data class Species(
    val id: String,
    val en: String,
    val icelandic: String,
    val water: Water,
    val lo: Double,
    val optLo: Double,
    val optHi: Double,
    val hi: Double,
    val reach: Double,
    val nightBonus: Double = 0.0,
    /** Calendar months (1..12) it can be fished, or null for all year. */
    val months: IntRange? = null,
    val fact: String,
) {
    /** 0..1 how comfortable this fish is at water temperature [t]. Unknown temperature → 0.75. */
    fun temperatureFit(t: Double): Double {
        if (t.isNaN()) return 0.75
        return when {
            t <= lo || t >= hi -> 0.0
            t < optLo -> (t - lo) / (optLo - lo)
            t <= optHi -> 1.0
            else -> (hi - t) / (hi - optHi)
        }
    }
}

object Fish {
    val sea = listOf(
        Species(
            "ufsi", "Saithe", "Ufsi", Water.SEA, lo = 2.0, optLo = 6.0, optHi = 11.0, hi = 15.0, reach = 1.0,
            fact = "Young saithe live close to shore, especially around rocks. The classic harbour fish.",
        ),
        Species(
            "thorskur", "Cod", "Þorskur", Water.SEA, lo = 0.0, optLo = 4.0, optHi = 9.0, hi = 14.0, reach = 0.8,
            nightBonus = 0.15,
            fact = "Cod come shallower at night and dislike sudden drops in water temperature.",
        ),
        Species(
            "makrill", "Mackerel", "Makríll", Water.SEA, lo = 4.5, optLo = 8.0, optHi = 14.0, hi = 18.0, reach = 0.9,
            fact = "Mackerel like water above 8 °C. Around Iceland they're a summer fish, near the surface.",
        ),
        Species(
            "marhnutur", "Shorthorn sculpin", "Marhnútur", Water.SEA, lo = -1.0, optLo = 2.0, optHi = 10.0, hi = 14.0, reach = 1.0,
            fact = "Lives in seaweed and on rocky bottoms right from the shore. Very common in harbours.",
        ),
        Species(
            "ysa", "Haddock", "Ýsa", Water.SEA, lo = 2.0, optLo = 4.0, optHi = 10.0, hi = 12.0, reach = 0.25,
            fact = "Usually 80–200 m deep, so rare from shore. Best chance at deep-water piers.",
        ),
        Species(
            "skarkoli", "Plaice", "Skarkoli", Water.SEA, lo = 2.0, optLo = 6.0, optHi = 12.0, hi = 16.0, reach = 0.5,
            fact = "Young plaice live on shallow sandy bottoms; bigger ones are deeper.",
        ),
        Species(
            "steinbitur", "Atlantic wolffish", "Steinbítur", Water.SEA, lo = -1.0, optLo = 2.0, optHi = 8.0, hi = 11.0, reach = 0.2,
            fact = "Lives 20–500 m deep on rocky bottoms and rarely moves. Rare from shore.",
        ),
    )

    val lake = listOf(
        Species(
            "bleikja", "Arctic char", "Bleikja", Water.LAKE, lo = 1.0, optLo = 4.0, optHi = 12.0, hi = 16.0, reach = 1.0,
            months = 5..9,
            fact = "Lake fishing in Iceland needs a permit (Veiðikortið or the local club) and runs about May to September.",
        ),
        Species(
            "urridi", "Brown trout", "Urriði", Water.LAKE, lo = 2.0, optLo = 6.0, optHi = 14.0, hi = 18.0, reach = 1.0,
            months = 5..9,
            fact = "Lake fishing in Iceland needs a permit (Veiðikortið or the local club) and runs about May to September.",
        ),
    )

    val all = sea + lake

    fun byId(id: String): Species? = all.firstOrNull { it.id == id }

    fun forWater(water: Water) = if (water == Water.SEA) sea else lake
}
