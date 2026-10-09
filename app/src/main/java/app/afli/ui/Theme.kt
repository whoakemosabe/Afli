package app.afli.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.afli.model.Bite
import app.afli.model.Safety

/** Nautical palette: deep navy water, brass fittings, sea foam. */
object C {
    val navy = Color(0xFF07121F)
    val deep = Color(0xFF0B2235)
    val sea = Color(0xFF2E8FB5)
    val foam = Color(0xFFEAF4F8)
    val mist = Color(0xB3EAF4F8)
    val faint = Color(0x80EAF4F8)
    val brass = Color(0xFFD8B56A)
    val good = Color(0xFF5BD38A)
    val ok = Color(0xFFF2C14E)
    val bad = Color(0xFFF06A5D)
    val line = Color(0x26EAF4F8)

    fun bite(b: Bite) = when (b) {
        Bite.GREAT -> good
        Bite.OK -> ok
        Bite.SLOW -> mist
    }

    fun safety(s: Safety) = when (s) {
        Safety.SAFE -> good
        Safety.CAREFUL -> ok
        Safety.STAY_HOME -> bad
    }

    fun score(v: Int) = when {
        v >= 60 -> good
        v >= 35 -> ok
        else -> Color(0xFF7FA6BC)
    }
}

object T {
    private val sans = FontFamily.SansSerif
    val hero = TextStyle(fontFamily = sans, fontWeight = FontWeight.Light, fontSize = 96.sp, color = C.foam, letterSpacing = (-3).sp)
    val title = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = C.foam)
    val heading = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = C.foam)
    val body = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp, color = C.foam)
    val small = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, color = C.mist)
    val label = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 1.2.sp, color = C.brass)
    val number = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = C.foam)
}
