package app.afli.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The sea behind every screen: deep navy with slow swells and faint caustic light, drawn on the
 * GPU. The glass panes refract this. Older phones (before Android 13) get a still gradient.
 * [calm] slows it right down when the system asks for less motion.
 */
private const val Sea = """
uniform float2 res;
uniform float t;

// Caustic web: the bright, wobbly net of light you see on a sea floor.
float caustic(float2 q, float time) {
    float2 p = q;
    float c = 1.0;
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        p = p + float2(sin(p.y * 1.7 + time * 0.6 + fi), cos(p.x * 1.5 - time * 0.5 + fi * 1.3)) * 0.55;
        c *= 0.5 + 0.5 * sin(p.x + p.y);
    }
    return pow(1.0 - abs(c * 2.0 - 1.0), 6.0);
}

half4 main(float2 px) {
    float2 uv = px / res;
    float aspect = res.y / res.x;
    float swell = sin(uv.x * 5.0 + t * 0.35) * 0.025 + sin(uv.x * 11.0 - t * 0.5) * 0.012;
    float y = clamp(uv.y + swell, 0.0, 1.0);

    // Lit teal water near the top, deep navy below.
    float3 shallow = float3(0.09, 0.34, 0.46);
    float3 mid = float3(0.035, 0.17, 0.27);
    float3 deep = float3(0.012, 0.045, 0.085);
    float3 col = mix(shallow, mid, smoothstep(0.0, 0.45, y));
    col = mix(col, deep, smoothstep(0.4, 1.0, y));

    // Sun shafts slanting down from the surface.
    float shafts = 0.0;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float x = uv.x + uv.y * (0.25 + fi * 0.07) + sin(t * 0.07 + fi * 2.1) * 0.15;
        shafts += pow(0.5 + 0.5 * sin(x * (9.0 + fi * 5.0) + fi * 1.7 + t * 0.12), 12.0);
    }
    col += float3(0.30, 0.55, 0.62) * shafts * 0.16 * (1.0 - smoothstep(0.0, 0.85, uv.y));

    // Caustics, strongest near the surface, fading with depth.
    float c = caustic(float2(uv.x * 7.0, uv.y * 7.0 * aspect), t * 0.8);
    col += float3(0.45, 0.80, 0.85) * c * 0.22 * (1.0 - smoothstep(0.1, 0.95, uv.y));

    // A few slow bubbles rising.
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        float bx = fract(sin(fi * 12.9898) * 43758.5453) * 0.9 + 0.05 + sin(t * 0.6 + fi) * 0.01;
        float by = 1.0 - fract(t * (0.025 + fi * 0.006) + fi * 0.17);
        float2 d = float2((uv.x - bx) / aspect, uv.y - by);
        float r = 0.006 + fi * 0.0012;
        float len = length(d);
        // A thin bright rim: inside radius r, minus the inner part.
        float ring = (1.0 - smoothstep(r * 0.55, r, len)) - (1.0 - smoothstep(r * 0.2, r * 0.55, len));
        col += float3(0.7, 0.9, 0.95) * ring * 0.35;
    }

    // Soft brass glow low down, like harbour lights on the water.
    float g = (uv.y - 0.82) * 6.0;
    col += float3(0.85, 0.71, 0.42) * exp(-g * g) * 0.06;

    return half4(half3(col), 1.0);
}
"""

@Composable
fun SeaBackground(modifier: Modifier = Modifier, calm: Boolean = false, still: Boolean = false) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) AnimatedSea(modifier, calm, still)
    else Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF175874), Color(0xFF092B44), Color(0xFF030C16))))
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AnimatedSea(modifier: Modifier, calm: Boolean, still: Boolean) {
    val shader = remember { RuntimeShader(Sea) }
    val brush = remember { ShaderBrush(shader) }
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(calm, still) {
        // Still: one frame of the sea, no animation (saves battery); keeps the current moment.
        if (still) return@LaunchedEffect
        // Carry on from where the sea was, so turning it back on doesn't jump.
        val base = time.floatValue
        val start = withFrameNanos { it }
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                // 30 frames a second is plenty for a slow sea, and every frame it moves, every
                // glass pane has to re-bend it. Scrolling still runs at the screen's full rate.
                if (now - last >= 32_000_000L) {
                    last = now
                    val s = (now - start) / 1_000_000_000f
                    time.floatValue = base + if (calm) s * 0.15f else s
                }
            }
        }
    }
    // Its own GPU layer: the sea is painted once per step and the screen and every glass pane
    // reuse that picture, instead of each one running the sea shader again.
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        shader.setFloatUniform("res", size.width, size.height)
        shader.setFloatUniform("t", time.floatValue)
        drawRect(brush)
    }
}
