package app.afli.model

import app.afli.L
import app.afli.Tx

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
    val fact: Tx,
    /** What to fish with, from shore. */
    val bait: Tx,
    /** The same in a few words, for places with room for one line. */
    val baitShort: Tx = bait,
) {
    /** The name in the app's language; [other] is the name in the other one. */
    val name: String get() = if (L.isl) icelandic else en
    val other: String get() = if (L.isl) en else icelandic

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
    // Bait and lures: saithe and mackerel from britishseafishing.co.uk (coalfish, mackerel) and
    // Vísir's report on spoons at Keflavík harbour (2019); cod and wolffish from shore anglers'
    // reports in Iceland (talkseafishing.co.uk); plaice from britishseafishing.co.uk; haddock
    // herring bait from Icelandic anglers on hugi.is. Lake rules vary by lake, so we say check.
    val sea = listOf(
        Species(
            "ufsi", "Saithe", "Ufsi", Water.SEA, lo = 2.0, optLo = 6.0, optHi = 11.0, hi = 15.0, reach = 1.0,
            fact = Tx(
                "Young saithe live close to shore in schools, especially around rocks. The classic harbour fish.",
                "Smáufsi heldur sig í torfum nálægt landi, sérstaklega við grjót. Klassíski bryggjufiskurinn.",
            ),
            bait = Tx(
                "Small spinner or spoon, or mackerel feathers. A strip of fish on the hook works too.",
                "Lítill spúnn eða makrílslóði. Fiskbiti á krók virkar líka.",
            ),
            baitShort = Tx("Small spinner or feathers", "Lítill spúnn eða slóði"),
        ),
        Species(
            "thorskur", "Cod", "Þorskur", Water.SEA, lo = 0.0, optLo = 4.0, optHi = 9.0, hi = 14.0, reach = 0.8,
            nightBonus = 0.15,
            fact = Tx(
                "Cod come shallower at night and dislike sudden drops in water temperature.",
                "Þorskurinn kemur grynnra á nóttunni og kann illa við snögga kólnun sjávar.",
            ),
            bait = Tx(
                "Bait on the bottom: mussel, worm or a strip of mackerel. Best at dusk and after dark.",
                "Beita við botninn: kræklingur, maðkur eða makrílbiti. Best í ljósaskiptunum og eftir að dimmir.",
            ),
            baitShort = Tx("Mussel or worm on the bottom", "Kræklingur eða maðkur við botn"),
        ),
        Species(
            "makrill", "Mackerel", "Makríll", Water.SEA, lo = 4.5, optLo = 8.0, optHi = 14.0, hi = 18.0, reach = 0.9,
            fact = Tx(
                "Mackerel like water above 8 °C. Around Iceland they're a summer fish, near the surface.",
                "Makríllinn vill sjó yfir 8 °C. Hér við land er hann sumarfiskur og syndir ofarlega.",
            ),
            bait = Tx(
                "A shiny spoon or mackerel feathers, cast out and wound in fast near the surface.",
                "Glansandi spúnn eða makrílslóði. Kastaðu út og dragðu hratt inn rétt undir yfirborðinu.",
            ),
            baitShort = Tx("Spoon or mackerel feathers", "Spúnn eða makrílslóði"),
        ),
        Species(
            "marhnutur", "Shorthorn sculpin", "Marhnútur", Water.SEA, lo = -1.0, optLo = 2.0, optHi = 10.0, hi = 14.0, reach = 1.0,
            fact = Tx(
                "Lives in seaweed and on rocky bottoms right from the shore. Very common in harbours.",
                "Lifir í þaranum og á grýttum botni alveg upp við land. Mjög algengur í höfnum.",
            ),
            bait = Tx(
                "Almost any bait dropped down by the seaweed: a bit of fish, shrimp or mussel.",
                "Nánast hvaða beita sem er við þarann: fiskbiti, rækja eða kræklingur.",
            ),
            baitShort = Tx("Any bait by the seaweed", "Hvaða beita sem er við þarann"),
        ),
        Species(
            "ysa", "Haddock", "Ýsa", Water.SEA, lo = 2.0, optLo = 4.0, optHi = 10.0, hi = 12.0, reach = 0.25,
            fact = Tx(
                "Usually 80–200 m deep, so rare from shore. Best chance at deep-water piers.",
                "Heldur sig oftast á 80–200 m dýpi og veiðist því sjaldan frá landi. Mestar líkur við djúpar bryggjur.",
            ),
            bait = Tx(
                "Herring or mussel on small hooks near the bottom.",
                "Síld eða kræklingur á litlum krókum við botninn.",
            ),
            baitShort = Tx("Herring or mussel, small hooks", "Síld eða kræklingur, litlir krókar"),
        ),
        Species(
            "skarkoli", "Plaice", "Skarkoli", Water.SEA, lo = 2.0, optLo = 6.0, optHi = 12.0, hi = 16.0, reach = 0.5,
            fact = Tx(
                "Young plaice live on shallow sandy bottoms; bigger ones are deeper.",
                "Ungur skarkoli lifir á grunnum sandbotni en stærri kolar eru dýpra.",
            ),
            bait = Tx(
                "Worm or mussel on small long-shank hooks over sand. Keep the bait small.",
                "Maðkur eða kræklingur á litlum leggjalöngum krókum yfir sandi. Hafðu beituna litla.",
            ),
            baitShort = Tx("Worm on sand, small hooks", "Maðkur á sandi, litlir krókar"),
        ),
        Species(
            "steinbitur", "Atlantic wolffish", "Steinbítur", Water.SEA, lo = -1.0, optLo = 2.0, optHi = 8.0, hi = 11.0, reach = 0.2,
            fact = Tx(
                "Lives 20–500 m deep on rocky bottoms and rarely moves. Rare from shore.",
                "Lifir á 20–500 m dýpi á grýttum botni og fer lítið um. Veiðist sjaldan frá landi.",
            ),
            bait = Tx(
                "Mussel and clam on a strong hook, cast out towards rocky ground.",
                "Kræklingur og skel á sterkum krók. Kastaðu út á grýttan botn.",
            ),
            baitShort = Tx("Mussel and clam, strong hook", "Kræklingur og skel, sterkur krókur"),
        ),
    )

    val lake = listOf(
        Species(
            "bleikja", "Arctic char", "Bleikja", Water.LAKE, lo = 1.0, optLo = 4.0, optHi = 12.0, hi = 16.0, reach = 1.0,
            months = 5..9,
            fact = Tx(
                "The most widespread freshwater fish in Iceland. Likes cold, clear water.",
                "Útbreiddasti ferskvatnsfiskur á Íslandi. Kann best við kalt og tært vatn.",
            ),
            bait = Tx(
                "Small spinner, fly or worm. Rules differ by lake (some are fly only), so check first.",
                "Lítill spúnn, fluga eða maðkur. Reglur eru misjafnar eftir vötnum (sum leyfa bara flugu), svo kannaðu fyrst.",
            ),
            baitShort = Tx("Small spinner, fly or worm", "Lítill spúnn, fluga eða maðkur"),
        ),
        Species(
            "urridi", "Brown trout", "Urriði", Water.LAKE, lo = 2.0, optLo = 6.0, optHi = 14.0, hi = 18.0, reach = 1.0,
            months = 5..9,
            fact = Tx(
                "Grows big in rich lakes such as Þingvallavatn, and often hunts close to the shore in low light.",
                "Verður stór í gjöfulum vötnum eins og Þingvallavatni og veiðir oft nálægt landi í lítilli birtu.",
            ),
            bait = Tx(
                "Spinner, streamer fly or worm. Rules differ by lake, so check first.",
                "Spúnn, straumfluga eða maðkur. Reglur eru misjafnar eftir vötnum, svo kannaðu fyrst.",
            ),
            baitShort = Tx("Spinner, streamer or worm", "Spúnn, straumfluga eða maðkur"),
        ),
    )

    val all = sea + lake

    fun byId(id: String): Species? = all.firstOrNull { it.id == id }

    fun forWater(water: Water) = if (water == Water.SEA) sea else lake
}
