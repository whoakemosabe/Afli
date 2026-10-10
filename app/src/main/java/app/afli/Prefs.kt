package app.afli

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** How wind speeds are shown. Afli always works in m/s inside; this is only for display. */
enum class WindUnit(val code: String, val label: String, val factor: Double) {
    MS("ms", "m/s", 1.0),
    KMH("kmh", "km/h", 3.6),
    KN("kn", "kn", 1.943844),
}

/**
 * The user's settings, as Compose state so everything redraws the moment one changes. Saved in
 * the same preferences file as the rest of Afli.
 */
object Prefs {
    var wind by mutableStateOf(WindUnit.MS)
    var fahrenheit by mutableStateOf(false)
    var haptics by mutableStateOf(true)
    var animatedSea by mutableStateOf(true)

    private fun sp(context: Context) = context.getSharedPreferences("afli", Context.MODE_PRIVATE)

    fun load(context: Context) {
        val p = sp(context)
        wind = WindUnit.entries.firstOrNull { it.code == p.getString("windUnit", null) } ?: WindUnit.MS
        fahrenheit = p.getBoolean("fahrenheit", false)
        haptics = p.getBoolean("haptics", true)
        animatedSea = p.getBoolean("animatedSea", true)
    }

    fun save(context: Context) {
        sp(context).edit()
            .putString("windUnit", wind.code)
            .putBoolean("fahrenheit", fahrenheit)
            .putBoolean("haptics", haptics)
            .putBoolean("animatedSea", animatedSea)
            .apply()
    }

    /** True when text needs converting from Afli's built-in m/s and °C. */
    val converting: Boolean get() = wind != WindUnit.MS || fahrenheit
}

/** A wind speed (m/s in) as a number in the chosen unit. */
fun windNum(ms: Double): String = num(ms * Prefs.wind.factor)

/** A wind speed with its unit, e.g. "8 m/s" or "29 km/h". */
fun windText(ms: Double): String = "${windNum(ms)} ${Prefs.wind.label}"

/** A temperature (°C in) as a number in the chosen unit. */
fun tempNum(c: Double, digits: Int = 0): String = num(if (Prefs.fahrenheit) c * 9 / 5 + 32 else c, digits)

val tempUnit: String get() = if (Prefs.fahrenheit) "°F" else "°C"

/** A temperature with its unit, e.g. "9 °C" or "48 °F". */
fun tempText(c: Double, digits: Int = 0): String = "${tempNum(c, digits)} $tempUnit"

private val NUM = """-?\d+(?:[.,]\d+)?"""
private val TEMP_RANGE = Regex("""($NUM)–($NUM) °C""")
private val TEMP = Regex("""($NUM) °C""")
private val WIND = Regex("""($NUM) m/s""")

private fun parseNum(s: String) = s.replace(',', '.').toDouble()
private fun decimals(s: String) = s.substringAfter(',', s.substringAfter('.', "")).let { if (s.contains(',') || s.contains('.')) it.length else 0 }

/**
 * Rewrites the m/s and °C written into Afli's text (explanations, safety, fish facts) into the
 * chosen units, so one setting changes everything consistently.
 */
fun convertUnits(text: String): String {
    if (!Prefs.converting) return text
    var s = text
    if (Prefs.fahrenheit) {
        s = TEMP_RANGE.replace(s) { m ->
            val (a, b) = m.destructured
            "${tempNum(parseNum(a), decimals(a))}–${tempNum(parseNum(b), decimals(b))} °F"
        }
        s = TEMP.replace(s) { m -> "${tempNum(parseNum(m.groupValues[1]), decimals(m.groupValues[1]))} °F" }
    }
    if (Prefs.wind != WindUnit.MS) {
        s = WIND.replace(s) { m -> windText(parseNum(m.groupValues[1])) }
    }
    return s
}
