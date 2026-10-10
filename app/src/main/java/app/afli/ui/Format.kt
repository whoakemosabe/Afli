package app.afli.ui

import app.afli.L
import app.afli.model.Spot
import app.afli.num
import app.afli.t
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/*
 * Times, days, directions and numbers in the app's language. Iceland and most of Europe write
 * 24-hour times, so both languages use HH:mm; Icelandic gets its own day and month names and a
 * decimal comma.
 */

private fun at(t: Long): ZonedDateTime = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault())

fun clock(t: Long): String = DateTimeFormatter.ofPattern("HH:mm").format(at(t))

fun hourOf(t: Long): Int = at(t).hour

/** "Sat" or "lau." */
fun shortDay(t: Long): String = DateTimeFormatter.ofPattern("EEE", L.locale).format(at(t))

/** "Today", "Tomorrow", else the short day name. */
fun dayWord(ms: Long): String {
    val d = at(ms).toLocalDate()
    val today = LocalDate.now()
    return when (d) {
        today -> t("Today", "Í dag")
        today.plusDays(1) -> t("Tomorrow", "Á morgun")
        else -> shortDay(ms)
    }
}

/** "Sat 18:00" or "lau. 18:00". */
fun dayClock(t: Long): String = "${shortDay(t)} ${clock(t)}"

/** "Saturday 10 Oct" or "laugardagur 10. okt." */
fun longDay(date: LocalDate): String =
    DateTimeFormatter.ofPattern(if (L.isl) "EEEE d. MMM" else "EEEE d MMM", L.locale).format(date)
        .replaceFirstChar { if (it.isLowerCase() && !L.isl) it.titlecase(Locale.UK) else it.toString() }

/** Compass point the wind or water is at: N, NE… or N, NA… in Icelandic. */
fun compass(deg: Double): String {
    if (deg.isNaN()) return "–"
    val names = if (L.isl) listOf("N", "NA", "A", "SA", "S", "SV", "V", "NV") else listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return names[(((deg % 360) + 360) % 360 / 45.0).roundToInt() % 8]
}

/** Whole or [digits]-decimal number in the app's language; "–" if unknown. */
fun fmt(v: Double, digits: Int = 0): String = num(v, digits)

/** "20 min" / "1 h 5 min", or "20 mín." / "1 klst. 5 mín." */
fun duration(mins: Int): String =
    if (mins < 60) t("$mins min", "$mins mín.")
    else t("${mins / 60} h ${mins % 60} min", "${mins / 60} klst. ${mins % 60} mín.")

/** Built-in spots have a name in both languages; others keep the name the phone gave them. */
val Spot.label: String get() = spotLabel(id, name)

fun spotLabel(id: String, name: String): String = when (id) {
    // Built-in names in both languages, unless he's renamed them.
    "keflavik" -> if (name == "Keflavík harbour") t("Keflavík harbour", "Keflavíkurhöfn") else name
    "njardvik" -> if (name == "Njarðvík harbour") t("Njarðvík harbour", "Njarðvíkurhöfn") else name
    // A new spot is called "Here" until the phone finds its place name.
    else -> if (name == "Here") t("Here", "Hér") else name
}
