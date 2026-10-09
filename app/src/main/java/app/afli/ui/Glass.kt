package app.afli.ui

import android.os.Build
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow

/*
 * Liquid glass on Kyant's Backdrop 2.x (Compose Multiplatform). The page behind (the animated
 * sea) is recorded with layerBackdrop and redrawn through a short chain: a touch more colour, a
 * frost, and the library's lens, which bends a rounded edge with depth and colour fringing.
 *
 * 2.0 removed the Android-only effect(RenderEffect) path, so custom shaders go through
 * runtimeShaderEffect instead; the dissolve below is the ported version of Ljós's and can be
 * dropped back into Ljós and Skjálfti. The lens and dissolve need Android 13; Kyant skips them
 * on older phones, and the surface tint is made stronger there so text stays readable.
 */

val glassFull: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** What every glass piece on the page refracts. */
val LocalBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Fades the frosted copy out over the pane's last stretch, (1 - x)², so there's no edge. */
private const val Dissolve = """
uniform shader content;
uniform float2 offset;
uniform float fadeTop;
uniform float fadeBottom;

half4 main(float2 coord) {
    float y = coord.y + offset.y;
    float x = clamp((y - fadeTop) / max(fadeBottom - fadeTop, 1.0), 0.0, 1.0);
    float a = (1.0 - x) * (1.0 - x);
    return content.eval(coord) * half(a);
}
"""

/** Kyant 2.x port of the Ljós/Skjálfti dissolve: no RenderEffect, no SDK check needed here. */
fun BackdropEffectScope.dissolve(fadeTop: Float, fadeBottom: Float) {
    val pad = padding
    runtimeShaderEffect("AfliDissolve", Dissolve, "content") {
        setFloatUniform("offset", -pad, -pad)
        setFloatUniform("fadeTop", fadeTop)
        setFloatUniform("fadeBottom", fadeBottom)
    }
}

/**
 * A card of liquid glass. Frosted enough to carry text, with a lens around its rounded edge.
 * [press] (0..1, read while drawing) deepens the lens so the glass bends under the finger.
 */
fun Modifier.glassCard(
    backdrop: LayerBackdrop,
    shape: CornerBasedShape = RoundedCornerShape(26.dp),
    press: () -> Float = { 0f },
    frost: Dp = 10.dp,
    tint: Color = Color(0x2E06111D),
    glow: Color? = null,
    layer: (GraphicsLayerScope.() -> Unit)? = null,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    layerBlock = layer,
    effects = {
        colorControls(saturation = 1.25f)
        blur(frost.toPx())
        val p = press()
        lens((20.dp + 10.dp * p).toPx(), (34.dp + 26.dp * p).toPx(), depthEffect = true, chromaticAberration = true)
    },
    highlight = { Highlight(width = 1.dp, style = HighlightStyle.Default(angle = 45f)) },
    // A glow colour turns the shadow into a soft halo, used to make tips stand out.
    // A glow blends the dark drop shadow into a soft halo as its alpha rises, so it never jumps.
    shadow = {
        val a = glow?.alpha ?: 0f
        Shadow(
            radius = 22.dp + 8.dp * a,
            color = androidx.compose.ui.graphics.lerp(Color.Black.copy(alpha = 0.28f), (glow ?: Color.Black).copy(alpha = 0.45f), a),
        )
    },
    onDrawSurface = {
        drawRect(if (glassFull) tint else tint.copy(alpha = 0.82f))
        drawRect(Brush.verticalGradient(listOf(Color(0x18FFFFFF), Color(0x04FFFFFF))))
    },
)

/** Barely frosted glass for buttons, pills and chips: mostly lens, like iOS 26 controls. */
fun Modifier.glassControl(
    backdrop: LayerBackdrop,
    shape: CornerBasedShape = RoundedCornerShape(50),
    press: () -> Float = { 0f },
    tint: Color = Color(0x2605080F),
    layer: (GraphicsLayerScope.() -> Unit)? = null,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    layerBlock = layer,
    effects = {
        colorControls(saturation = 1.3f)
        blur(2.dp.toPx())
        val p = press()
        lens((12.dp + 6.dp * p).toPx(), (24.dp + 20.dp * p).toPx(), depthEffect = true, chromaticAberration = true)
    },
    highlight = { Highlight(style = HighlightStyle.Default(angle = 45f)) },
    shadow = { Shadow(radius = 14.dp, color = Color.Black.copy(alpha = 0.22f)) },
    onDrawSurface = { drawRect(if (glassFull) tint else Color(0x5506111D)) },
)

/** A sheet rising from the bottom: heavy frost, thick bent rim along its rounded top. */
fun Modifier.glassSheet(backdrop: LayerBackdrop, radius: Dp = 30.dp): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { RoundedCornerShape(topStart = radius, topEnd = radius) },
    effects = {
        colorControls(saturation = 1.15f)
        blur(16.dp.toPx())
        lens(28.dp.toPx(), 40.dp.toPx(), depthEffect = true, chromaticAberration = true)
    },
    highlight = { Highlight(style = HighlightStyle.Default(angle = 45f)) },
    shadow = null,
    onDrawSurface = {
        drawRect(if (glassFull) Color(0x8C07121F) else Color(0xE607121F))
        drawRect(Brush.verticalGradient(listOf(Color(0x1AFFFFFF), Color(0x05FFFFFF))))
    },
)

/**
 * The frosted band behind the status bar and title: no lens, dissolves at the bottom. The pane
 * reaches [margin] past the top and sides of the screen and the page is mirrored into that
 * margin, so the blur never pulls in empty space at the edges (which left a thin, clear strip
 * along the very top). Same fix as Ljós and Skjálfti.
 */
@Composable
fun GlassHeader(backdrop: LayerBackdrop, height: Dp, modifier: Modifier = Modifier, fade: Dp = 28.dp, margin: Dp = 40.dp) {
    val density = LocalDensity.current
    val fadePx = with(density) { fade.toPx() }
    val m = with(density) { margin.roundToPx() }
    val screenW = remember { floatArrayOf(0f) }
    Box(modifier.fillMaxWidth().height(height)) {
        Box(
            Modifier
                .layout { measurable, constraints ->
                    screenW[0] = constraints.maxWidth.toFloat()
                    val p = measurable.measure(Constraints.fixed(constraints.maxWidth + m * 2, constraints.maxHeight + m))
                    layout(constraints.maxWidth, constraints.maxHeight) { p.place(-m, -m) }
                }
                .drawPlainBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(0.dp) },
                    effects = {
                        colorControls(saturation = 1.2f)
                        blur(18.dp.toPx())
                        dissolve(size.height - fadePx, size.height)
                    },
                    onDrawBackdrop = { drawPage ->
                        drawPage()
                        val mf = m.toFloat()
                        val right = mf + screenW[0]
                        withTransform({ scale(-1f, 1f, pivot = Offset(mf, 0f)) }) { drawPage() }
                        withTransform({ scale(-1f, 1f, pivot = Offset(right, 0f)) }) { drawPage() }
                        withTransform({ scale(1f, -1f, pivot = Offset(0f, mf)) }) { drawPage() }
                    },
                    onDrawSurface = {
                        val h = size.height
                        val k = ((h - fadePx) / h).coerceIn(0f, 1f)
                        val tint = if (glassFull) Color(0x5207121F) else Color(0xCC07121F)
                        drawRect(Brush.verticalGradient(0f to tint, k to tint.copy(alpha = tint.alpha * 0.6f), 1f to Color.Transparent))
                    },
                ),
        )
    }
}
