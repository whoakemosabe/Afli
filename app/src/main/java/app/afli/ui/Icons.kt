package app.afli.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/**
 * Afli's own line icons, drawn on a 24-unit grid so they stay crisp at any size and take any
 * colour (mist when idle, brass when chosen). No icon font or extra library needed.
 */
enum class Icon { NOW, FORECAST, LOG, GUIDE, REFRESH, SETTINGS }

@Composable
fun AfliIcon(icon: Icon, color: Color, modifier: Modifier) {
    Canvas(modifier) { draw(icon, color) }
}

private fun DrawScope.draw(icon: Icon, color: Color) {
    val u = size.minDimension / 24f
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    val stroke = Stroke(1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (icon) {
        // A gauge with its needle up and to the right: "how's it looking right now".
        Icon.NOW -> {
            drawArc(color, 150f, 240f, false, p(4f, 5f), Size(16f * u, 16f * u), style = stroke)
            val a = Math.toRadians(-55.0)
            drawLine(color, p(12f, 13f), p(12f + 6f * cos(a).toFloat(), 13f + 6f * sin(a).toFloat()), stroke.width, StrokeCap.Round)
            drawCircle(color, 1.8f * u, p(12f, 13f))
        }
        // Three bars of different heights on a baseline: the days ahead.
        Icon.FORECAST -> {
            drawLine(color, p(4f, 20f), p(20f, 20f), stroke.width, StrokeCap.Round)
            listOf(7f to 12f, 12f to 6f, 17f to 9f).forEach { (x, top) ->
                drawLine(color, p(x, 16.5f), p(x, top), 3f * u, StrokeCap.Round)
            }
        }
        // A logbook with a fish on the page.
        Icon.LOG -> {
            val book = Path().apply {
                moveTo(6f * u, 3.5f * u); lineTo(18f * u, 3.5f * u); lineTo(18f * u, 20.5f * u); lineTo(6f * u, 20.5f * u); close()
            }
            drawPath(book, color, style = stroke)
            drawLine(color, p(9f, 3.5f), p(9f, 20.5f), stroke.width * 0.8f, StrokeCap.Round)
            val fish = Path().apply {
                moveTo(10.8f * u, 12f * u)
                quadraticTo(13.2f * u, 9.2f * u, 15.6f * u, 12f * u)
                quadraticTo(13.2f * u, 14.8f * u, 10.8f * u, 12f * u)
                moveTo(15.6f * u, 12f * u); lineTo(16.8f * u, 10.6f * u); lineTo(16.8f * u, 13.4f * u); close()
            }
            drawPath(fish, color, style = Stroke(1.4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // A compass: ring and needle.
        Icon.GUIDE -> {
            drawCircle(color, 9f * u, p(12f, 12f), style = stroke)
            val needle = Path().apply {
                moveTo(15.6f * u, 8.4f * u); lineTo(13.3f * u, 13.3f * u); lineTo(8.4f * u, 15.6f * u); lineTo(10.7f * u, 10.7f * u); close()
            }
            drawPath(needle, color, style = Stroke(1.5f * u, join = StrokeJoin.Round))
            drawPath(Path().apply { moveTo(15.6f * u, 8.4f * u); lineTo(13.3f * u, 13.3f * u); lineTo(10.7f * u, 10.7f * u); close() }, color)
        }
        // A circular arrow.
        Icon.REFRESH -> {
            val r = 7f
            drawArc(color, 30f, 285f, false, p(12f - r, 12f - r), Size(2 * r * u, 2 * r * u), style = stroke)
            val end = Math.toRadians(315.0)
            val tip = p(12f + r * cos(end).toFloat(), 12f + r * sin(end).toFloat())
            val d = Offset(-sin(end).toFloat(), cos(end).toFloat()) // direction of travel
            val n = Offset(d.y, -d.x)
            val head = Path().apply {
                moveTo(tip.x + d.x * 3.2f * u, tip.y + d.y * 3.2f * u)
                lineTo(tip.x - d.x * 1.2f * u + n.x * 3f * u, tip.y - d.y * 1.2f * u + n.y * 3f * u)
                lineTo(tip.x - d.x * 1.2f * u - n.x * 3f * u, tip.y - d.y * 1.2f * u - n.y * 3f * u)
                close()
            }
            drawPath(head, color)
        }
        // A ship's wheel: hub, rim and eight spokes poking past it.
        Icon.SETTINGS -> {
            drawCircle(color, 6.2f * u, p(12f, 12f), style = stroke)
            drawCircle(color, 1.9f * u, p(12f, 12f))
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0 + 22.5)
                val c = cos(a).toFloat()
                val s = sin(a).toFloat()
                drawLine(color, p(12f + 2.6f * c, 12f + 2.6f * s), p(12f + 9.6f * c, 12f + 9.6f * s), 1.6f * u, StrokeCap.Round)
            }
        }
    }
}
