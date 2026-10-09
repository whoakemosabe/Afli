package app.afli.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.afli.model.Hour
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * The tide over the next day: a smooth curve that draws itself in from left to right, the water
 * shaded beneath, and a brass marker for now. Along the bottom, a time axis with a tick every
 * three hours and the day's name at midnight. Highs and lows show their time, found between the
 * hourly points by fitting a curve through the three around each turn.
 */
@Composable
fun TideCurve(hours: List<Hour>, now: Long, modifier: Modifier = Modifier, key: Any? = null) {
    val pts = remember(hours, now) {
        hours.filter { it.t in (now - 6 * 3_600_000L)..(now + 24 * 3_600_000L) && !it.seaLevel.isNaN() }
    }
    val draw = remember(key) { Animatable(0f) }
    LaunchedEffect(key, pts.size) {
        draw.snapTo(0f)
        draw.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        if (pts.size < 3) return@Canvas
        val t0 = pts.first().t.toFloat()
        val t1 = pts.last().t.toFloat()
        val lo = pts.minOf { it.seaLevel }.toFloat()
        val hi = pts.maxOf { it.seaLevel }.toFloat()
        val span = (hi - lo).coerceAtLeast(0.2f)
        val axis = size.height - 18.dp.toPx() // the time labels sit below this line
        val top = 20.dp.toPx()
        val bottom = axis - 20.dp.toPx()
        fun x(t: Long) = (t - t0) / (t1 - t0) * size.width
        fun y(v: Double) = bottom - (v.toFloat() - lo) / span * (bottom - top)
        val line = Path()
        pts.forEachIndexed { i, h ->
            val px = x(h.t)
            val py = y(h.seaLevel)
            if (i == 0) line.moveTo(px, py) else {
                val p = pts[i - 1]
                val cx = (x(p.t) + px) / 2
                line.cubicTo(cx, y(p.seaLevel), cx, py, px, py)
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, axis)
            lineTo(0f, axis)
            close()
        }

        // Time axis: faint grid every 3 hours, labels underneath, the day at midnight.
        val nx = x(now)
        val gap = 30.dp.toPx() // keep tick labels clear of the "Now" label
        drawLine(C.line, Offset(0f, axis), Offset(size.width, axis), 1.dp.toPx())
        val cal = Calendar.getInstance().apply {
            timeInMillis = t0.toLong()
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            while (get(Calendar.HOUR_OF_DAY) % 3 != 0 || timeInMillis < t0.toLong()) add(Calendar.HOUR_OF_DAY, 1)
        }
        while (cal.timeInMillis <= t1.toLong()) {
            val px = x(cal.timeInMillis)
            val h = cal.get(Calendar.HOUR_OF_DAY)
            val midnight = h == 0
            drawLine(
                if (midnight) C.mist.copy(alpha = 0.35f) else C.line.copy(alpha = 0.10f),
                Offset(px, top - 6.dp.toPx()), Offset(px, axis), 1.dp.toPx(),
            )
            drawLine(C.mist.copy(alpha = 0.5f), Offset(px, axis), Offset(px, axis + 3.dp.toPx()), 1.dp.toPx())
            val text = if (midnight) DayName.format(cal.time) else "%02d".format(Locale.US, h)
            if (kotlin.math.abs(px - nx) > gap) axisLabel(measurer, text, px, axis + 4.dp.toPx(), if (midnight) C.foam else C.faint)
            cal.add(Calendar.HOUR_OF_DAY, 3)
        }

        clipRect(right = size.width * draw.value) {
            drawPath(fill, Brush.verticalGradient(listOf(Color(0x552E8FB5), Color(0x002E8FB5)), startY = top, endY = axis))
            drawPath(line, C.sea, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        }
        // Highs and lows, with the time of the turn.
        for (i in 1 until pts.lastIndex) {
            val a = pts[i - 1].seaLevel
            val b = pts[i].seaLevel
            val c = pts[i + 1].seaLevel
            val isHigh = b >= a && b > c
            val isLow = b <= a && b < c
            if (!isHigh && !isLow) continue
            // Peak of the parabola through the three hourly points, in hours from the middle one.
            val curve = a - 2 * b + c
            val shift = if (curve != 0.0) ((a - c) / (2 * curve)).coerceIn(-0.5, 0.5) else 0.0
            val at = pts[i].t + (shift * 3_600_000L).toLong()
            val px = x(at)
            if (px > size.width * draw.value) continue
            drawCircle(C.foam.copy(alpha = 0.8f), 2.5.dp.toPx(), Offset(px, y(b)))
            val text = (if (isHigh) "High " else "Low ") + Clock.format(Date(roundTo10(at)))
            label(measurer, text, Offset(px, if (isHigh) y(b) - 18.dp.toPx() else y(b) + 5.dp.toPx()))
        }
        if (nx in 0f..size.width) {
            drawLine(C.brass.copy(alpha = 0.7f), Offset(nx, top - 8.dp.toPx()), Offset(nx, axis), 1.5.dp.toPx())
            val ny = pts.minByOrNull { kotlin.math.abs(it.t - now) }?.let { y(it.seaLevel) } ?: bottom
            drawCircle(C.brass, 5.dp.toPx(), Offset(nx, ny))
            drawCircle(C.navy, 2.dp.toPx(), Offset(nx, ny))
            axisLabel(measurer, "Now", nx, axis + 4.dp.toPx(), C.brass)
        }
    }
}

private val Clock = SimpleDateFormat("HH:mm", Locale.UK)
private val DayName = SimpleDateFormat("EEE", Locale.UK)
private fun roundTo10(t: Long): Long { val step = 600_000L; return (t + step / 2) / step * step }

private fun androidx.compose.ui.graphics.drawscope.DrawScope.axisLabel(m: TextMeasurer, text: String, x: Float, y: Float, color: Color) {
    val r = m.measure(text, T.small.copy(color = color, fontSize = 11.sp, lineHeight = 13.sp))
    val left = (x - r.size.width / 2f).coerceIn(0f, size.width - r.size.width)
    drawText(r, topLeft = Offset(left, y))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.label(m: TextMeasurer, text: String, at: Offset) {
    val r = m.measure(text, T.small.copy(color = C.mist))
    drawText(r, topLeft = Offset((at.x - r.size.width / 2f).coerceIn(0f, size.width - r.size.width), at.y.coerceIn(0f, size.height - r.size.height)))
}

/**
 * Wind as a compass: the needle shows where the wind comes from and settles with a spring.
 * The brass tick marks which way this spot's water is (if known).
 */
@Composable
fun WindDial(fromDeg: Double, facing: Double?, modifier: Modifier = Modifier) {
    val target = if (fromDeg.isNaN()) 0f else fromDeg.toFloat()
    // Unwrap so the needle takes the short way round.
    val last = remember { floatArrayOf(target) }
    var t = target
    while (t - last[0] > 180f) t -= 360f
    while (t - last[0] < -180f) t += 360f
    last[0] = t
    val angle by animateFloatAsState(t, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow), label = "wind")
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val r = size.minDimension / 2f - 10.dp.toPx()
        val c = center
        drawCircle(C.line, r, c, style = Stroke(1.dp.toPx()))
        for (i in 0 until 36) {
            val a = Math.toRadians(i * 10.0)
            val inner = if (i % 9 == 0) r - 10.dp.toPx() else r - 5.dp.toPx()
            drawLine(
                if (i % 9 == 0) C.mist else C.line,
                Offset(c.x + (inner * sin(a)).toFloat(), c.y - (inner * cos(a)).toFloat()),
                Offset(c.x + (r * sin(a)).toFloat(), c.y - (r * cos(a)).toFloat()),
                1.dp.toPx(),
            )
        }
        listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0).forEach { (s, d) ->
            val a = Math.toRadians(d)
            val rr = r - 22.dp.toPx()
            val res = measurer.measure(s, T.small.copy(color = C.mist))
            drawText(res, topLeft = Offset(c.x + (rr * sin(a)).toFloat() - res.size.width / 2f, c.y - (rr * cos(a)).toFloat() - res.size.height / 2f))
        }
        if (facing != null) {
            val a = Math.toRadians(facing)
            drawLine(
                C.brass,
                Offset(c.x + ((r + 2.dp.toPx()) * sin(a)).toFloat(), c.y - ((r + 2.dp.toPx()) * cos(a)).toFloat()),
                Offset(c.x + ((r + 9.dp.toPx()) * sin(a)).toFloat(), c.y - ((r + 9.dp.toPx()) * cos(a)).toFloat()),
                3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        if (!fromDeg.isNaN()) {
            rotate(angle, c) {
                // Tail at the side the wind comes from, arrow pointing where it goes.
                val tail = Offset(c.x, c.y - r + 14.dp.toPx())
                val head = Offset(c.x, c.y + r - 18.dp.toPx())
                drawLine(C.foam, tail, head, 2.5.dp.toPx(), cap = StrokeCap.Round)
                val p = Path().apply {
                    moveTo(head.x, head.y + 8.dp.toPx())
                    lineTo(head.x - 6.dp.toPx(), head.y - 4.dp.toPx())
                    lineTo(head.x + 6.dp.toPx(), head.y - 4.dp.toPx())
                    close()
                }
                drawPath(p, C.foam)
                drawCircle(C.brass, 4.dp.toPx(), tail)
            }
        }
    }
}

/** Hour labels under the forecast bars: every 6 hours (00, 06, 12, 18), centred on their bar. */
@Composable
fun HourAxis(times: List<Long>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        if (times.isEmpty()) return@Canvas
        val w = size.width / times.size
        val cal = Calendar.getInstance()
        times.forEachIndexed { i, t ->
            cal.timeInMillis = t
            val h = cal.get(Calendar.HOUR_OF_DAY)
            if (h % 6 == 0) axisLabel(measurer, "%02d".format(Locale.US, h), i * w + w / 2, 0f, C.faint)
        }
    }
}

/** Hour bars for the forecast strip, coloured by score; faded where it's "Stay home". */
@Composable
fun ScoreBars(scores: List<Int>, unsafe: List<Boolean>, selected: Int?, modifier: Modifier = Modifier) {
    val grow = remember(scores) { Animatable(0f) }
    LaunchedEffect(scores) { grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        if (scores.isEmpty()) return@Canvas
        val w = size.width / scores.size
        scores.forEachIndexed { i, s ->
            val h = (size.height * (0.06f + 0.94f * s / 100f)) * grow.value
            val col = if (unsafe.getOrElse(i) { false }) C.bad.copy(alpha = 0.35f) else C.score(s).copy(alpha = if (selected == null || selected == i) 0.95f else 0.45f)
            drawRoundRect(
                col,
                topLeft = Offset(i * w + w * 0.18f, size.height - h),
                size = androidx.compose.ui.geometry.Size(w * 0.64f, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.32f),
            )
        }
    }
}
