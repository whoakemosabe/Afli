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
import app.afli.t
import java.util.Calendar
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
fun TideCurve(hours: List<Hour>, now: Long, modifier: Modifier = Modifier, key: Any? = null, markerLabel: String = t("Now", "Núna")) {
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
            val text = if (midnight) shortDay(cal.timeInMillis) else "%02d".format(Locale.US, h)
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
            val text = (if (isHigh) t("High ", "Flóð ") else t("Low ", "Fjara ")) + clock(roundTo10(at))
            label(measurer, text, Offset(px, if (isHigh) y(b) - 18.dp.toPx() else y(b) + 5.dp.toPx()))
        }
        if (nx in 0f..size.width) {
            drawLine(C.brass.copy(alpha = 0.7f), Offset(nx, top - 8.dp.toPx()), Offset(nx, axis), 1.5.dp.toPx())
            val ny = pts.minByOrNull { kotlin.math.abs(it.t - now) }?.let { y(it.seaLevel) } ?: bottom
            drawCircle(C.brass, 5.dp.toPx(), Offset(nx, ny))
            drawCircle(C.navy, 2.dp.toPx(), Offset(nx, ny))
            axisLabel(measurer, markerLabel, nx, axis + 4.dp.toPx(), C.brass)
        }
    }
}

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
        listOf("N" to 0.0, t("E", "A") to 90.0, "S" to 180.0, t("W", "V") to 270.0).forEach { (s, d) ->
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
fun HourAxis(times: List<Long>, modifier: Modifier = Modifier, points: Boolean = false) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        if (times.isEmpty()) return@Canvas
        // Bars sit in the middle of their slot; a line chart's points run edge to edge.
        fun x(i: Int) = if (points) i * size.width / (times.size - 1).coerceAtLeast(1) else (i + 0.5f) * size.width / times.size
        val cal = Calendar.getInstance()
        times.forEachIndexed { i, ms ->
            cal.timeInMillis = ms
            val h = cal.get(Calendar.HOUR_OF_DAY)
            if (h % 6 == 0) axisLabel(measurer, if (h == 0) shortDay(ms) else "%02d".format(Locale.US, h), x(i), 0f, if (h == 0) C.mist else C.faint)
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

/** A thin rounded bar filled to [fraction], for chances and progress. */
@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow), label = "bar")
    Canvas(modifier) {
        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
        drawRoundRect(C.line, cornerRadius = r)
        if (f > 0.005f) drawRoundRect(color, size = androidx.compose.ui.geometry.Size(maxOf(size.height, size.width * f), size.height), cornerRadius = r)
    }
}

/**
 * A small line chart: [values] left to right (NaN gaps skipped), shaded underneath, with an
 * optional brass marker at index [marker]. [bars] draws columns instead (rain). [floor] keeps
 * the scale from zooming into tiny changes.
 */
@Composable
fun Sparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
    color: Color = C.sea,
    marker: Int? = null,
    bars: Boolean = false,
    floor: Double = 0.0,
    zeroBased: Boolean = false,
) {
    val grow = remember(values.size) { Animatable(0f) }
    LaunchedEffect(values.size) { grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        val ok = values.filter { !it.isNaN() }
        if (ok.size < 2) return@Canvas
        var lo = if (zeroBased) 0.0 else ok.min()
        var hi = ok.max()
        if (hi - lo < floor) {
            val mid = (hi + lo) / 2
            lo = if (zeroBased) 0.0 else mid - floor / 2
            hi = lo + floor
        }
        val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        val pad = 3.dp.toPx()
        val w = size.width
        val hgt = size.height - pad * 2
        fun x(i: Int) = if (bars) (i + 0.5f) * w / values.size else i * w / (values.size - 1).coerceAtLeast(1)
        fun y(v: Double) = pad + hgt - ((v - lo) / span).toFloat() * hgt
        clipRect(right = size.width * grow.value) {
            if (bars) {
                val bw = w / values.size * 0.6f
                values.forEachIndexed { i, v ->
                    if (v.isNaN() || v <= 0) return@forEachIndexed
                    drawRoundRect(color.copy(alpha = 0.85f), topLeft = Offset(x(i) - bw / 2, y(v)), size = androidx.compose.ui.geometry.Size(bw, size.height - pad - y(v)), cornerRadius = androidx.compose.ui.geometry.CornerRadius(bw / 3))
                }
            } else {
                val line = Path()
                var started = false
                values.forEachIndexed { i, v ->
                    if (v.isNaN()) return@forEachIndexed
                    if (!started) { line.moveTo(x(i), y(v)); started = true } else line.lineTo(x(i), y(v))
                }
                val first = values.indexOfFirst { !it.isNaN() }
                val last = values.indexOfLast { !it.isNaN() }
                val fill = Path().apply {
                    addPath(line)
                    lineTo(x(last), size.height)
                    lineTo(x(first), size.height)
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0f))))
                drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
        if (marker != null && marker in values.indices) {
            val mx = x(marker)
            drawLine(C.brass.copy(alpha = 0.6f), Offset(mx, 0f), Offset(mx, size.height), 1.2.dp.toPx())
            val v = values[marker]
            if (!v.isNaN() && !bars) {
                drawCircle(C.brass, 3.5.dp.toPx(), Offset(mx, y(v)))
                drawCircle(C.navy, 1.4.dp.toPx(), Offset(mx, y(v)))
            }
        }
    }
}

/** A tiny compass for a tile: a ring and an arrow showing where the wind blows to. */
@Composable
fun MiniWind(fromDeg: Double, modifier: Modifier = Modifier) {
    val target = if (fromDeg.isNaN()) 0f else fromDeg.toFloat()
    val angle by animateFloatAsState(target, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow), label = "miniWind")
    Canvas(modifier) {
        val r = size.minDimension / 2f - 2.dp.toPx()
        drawCircle(C.line, r, center, style = Stroke(1.5.dp.toPx()))
        drawCircle(C.mist.copy(alpha = 0.5f), 1.5.dp.toPx(), Offset(center.x, center.y - r))
        if (fromDeg.isNaN()) return@Canvas
        rotate(angle, center) {
            val tail = Offset(center.x, center.y - r * 0.62f)
            val head = Offset(center.x, center.y + r * 0.62f)
            drawLine(C.foam, tail, head, 2.dp.toPx(), cap = StrokeCap.Round)
            drawPath(Path().apply {
                moveTo(head.x, head.y + 4.dp.toPx())
                lineTo(head.x - 4.dp.toPx(), head.y - 3.dp.toPx())
                lineTo(head.x + 4.dp.toPx(), head.y - 3.dp.toPx())
                close()
            }, C.foam)
            drawCircle(C.brass, 2.5.dp.toPx(), tail)
        }
    }
}

/** Two rolling wave lines, taller for bigger waves. */
@Composable
fun WaveGlyph(waveM: Double, modifier: Modifier = Modifier) {
    val amp = if (waveM.isNaN()) 0.3f else (0.2f + waveM.toFloat() / 4f).coerceIn(0.2f, 1f)
    Canvas(modifier) {
        listOf(0.42f to C.sea, 0.7f to C.sea.copy(alpha = 0.55f)).forEach { (yf, col) ->
            val p = Path()
            val steps = 24
            for (i in 0..steps) {
                val x = i / steps.toFloat() * size.width
                val y = size.height * yf + sin(i / steps.toDouble() * 2 * Math.PI * 1.5).toFloat() * size.height * 0.16f * amp
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, col, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/** The horizon with the sun at its height: above the line by day, dimmed below at night. */
@Composable
fun SunGlyph(elevation: Double, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val horizon = size.height * 0.62f
        drawLine(C.mist.copy(alpha = 0.6f), Offset(0f, horizon), Offset(size.width, horizon), 1.5.dp.toPx(), cap = StrokeCap.Round)
        val e = elevation.toFloat().coerceIn(-20f, 40f)
        val y = horizon - e / 40f * horizon * 0.95f
        val up = elevation > -0.833
        val c = Offset(size.width / 2, y)
        if (up) drawCircle(C.brass.copy(alpha = 0.25f), 9.dp.toPx(), c)
        drawCircle(if (up) C.brass else C.mist.copy(alpha = 0.35f), 5.dp.toPx(), c)
    }
}

/**
 * The sun's height through one day: shaded brass where the light is low enough for fishing
 * (−6° to 8°), the horizon line, sunrise and sunset labelled, and a marker at [marker].
 */
@Composable
fun SunCurve(dayStart: Long, lat: Double, lon: Double, marker: Long, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val pts = remember(dayStart, lat, lon) {
        (0..144).map { i -> val tt = dayStart + i * 600_000L; tt to app.afli.model.Astro.sunElevation(tt, lat, lon) }
    }
    val day = remember(dayStart, lat, lon) { app.afli.model.Astro.sunDay(dayStart + 3_600_000L, lat, lon) }
    Canvas(modifier) {
        val axis = size.height - 16.dp.toPx()
        val lo = minOf(-20.0, pts.minOf { it.second })
        val hi = maxOf(20.0, pts.maxOf { it.second })
        fun x(tt: Long) = (tt - dayStart) / 86_400_000f * size.width
        fun y(e: Double) = (axis - (e - lo) / (hi - lo) * axis).toFloat()
        // Low-light bands.
        day.lowLight.forEach { (a, b) ->
            drawRect(C.brass.copy(alpha = 0.16f), topLeft = Offset(x(a), 0f), size = androidx.compose.ui.geometry.Size(x(b) - x(a), axis))
        }
        drawLine(C.mist.copy(alpha = 0.4f), Offset(0f, y(0.0)), Offset(size.width, y(0.0)), 1.dp.toPx())
        val line = Path()
        pts.forEachIndexed { i, (tt, e) -> if (i == 0) line.moveTo(x(tt), y(e)) else line.lineTo(x(tt), y(e)) }
        drawPath(line, C.brass, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        for (h in listOf(0, 6, 12, 18)) {
            axisLabel(measurer, "%02d".format(Locale.US, h), x(dayStart + h * 3_600_000L) + if (h == 0) 8.dp.toPx() else 0f, axis + 3.dp.toPx(), C.faint)
        }
        day.rise?.let { label(measurer, "↑ " + clock(it), Offset(x(it), y(0.0) + 4.dp.toPx())) }
        day.set?.let { label(measurer, "↓ " + clock(it), Offset(x(it), y(0.0) + 4.dp.toPx())) }
        val mx = x(marker)
        if (mx in 0f..size.width) {
            drawLine(C.foam.copy(alpha = 0.7f), Offset(mx, 0f), Offset(mx, axis), 1.2.dp.toPx())
            val e = app.afli.model.Astro.sunElevation(marker, lat, lon)
            drawCircle(C.foam, 4.dp.toPx(), Offset(mx, y(e)))
        }
    }
}
