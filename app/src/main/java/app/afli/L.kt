package app.afli

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * English or Icelandic, switched in Settings and applied instantly. The language is Compose
 * state, so any text read through [t] or a [Tx] while drawing redraws by itself when it
 * changes; nothing restarts and you keep your place.
 */
enum class Lang(val code: String) { EN("en"), IS("is") }

object L {
    var lang by mutableStateOf(if (Locale.getDefault().language == "is") Lang.IS else Lang.EN)

    val isl: Boolean get() = lang == Lang.IS

    /** Icelandic writes 9,3 °C and uses its own day and month names. */
    val locale: Locale get() = if (isl) Locale.forLanguageTag("is-IS") else Locale.UK
}

/** Apply the language saved in Settings (also used by the background update check). */
fun loadLang(context: android.content.Context) {
    val code = context.getSharedPreferences("afli", android.content.Context.MODE_PRIVATE).getString("lang", null) ?: return
    Lang.entries.firstOrNull { it.code == code }?.let { L.lang = it }
}

/** Pick the text for the current language. */
fun t(en: String, isl: String): String = if (L.lang == Lang.IS) isl else en

/** A piece of text in both languages, for things worked out before they're shown (the model). */
data class Tx(val en: String, val isl: String) {
    override fun toString(): String = t(en, isl)
}

/** A number with [digits] decimals in the current language (comma in Icelandic). */
fun num(v: Double, digits: Int = 0): String {
    if (v.isNaN()) return "–"
    val s = String.format(L.locale, "%.${digits}f", v)
    // "-0" and "-0,0" read oddly; a rounded zero is just zero.
    return if (s.trimStart('-').all { it == '0' || it == '.' || it == ',' }) s.trimStart('-') else s
}
