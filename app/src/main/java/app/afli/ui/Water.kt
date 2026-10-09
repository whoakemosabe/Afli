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

/**
 * The sea behind every screen: deep navy with slow swells and faint caustic light, drawn on the
 * GPU. The glass panes refract this. Older phones (before Android 13) get a still gradient.
 * [calm] slows it right down when the system asks for less motion.
 */
private const val Sea = """
uniform float2 res;
uniform float t;

half4 main(float2 p) {
    float2 uv = p / res;
    float swell = sin(uv.x * 5.0 + t * 0.35) * 0.025 + sin(uv.x * 11.0 - t * 0.5) * 0.012;
    float y = clamp(uv.y + swell, 0.0, 1.0);
    float3 top = float3(0.043, 0.165, 0.255);
    float3 bottom = float3(0.016, 0.051, 0.094);
    float3 col = mix(top, bottom, smoothstep(0.0, 1.0, y));
    // Caustics: two drifting interference patterns, brightest near the surface.
    float c1 = sin((uv.x + swell) * 31.0 + t * 0.7) * sin(uv.y * 23.0 - t * 0.45);
    float c2 = sin((uv.x - swell) * 17.0 - t * 0.4) * sin(uv.y * 41.0 + t * 0.6);
    float c = pow(max(c1 * c2, 0.0), 3.0) * 0.12 * (1.0 - y * 0.8);
    col += float3(0.35, 0.65, 0.75) * c;
    // A soft brass glow low on the horizon, like harbour lights on the water.
    float g = (uv.y - 0.82) * 6.0;
    float glow = exp(-g * g) * 0.05;
    col += float3(0.85, 0.71, 0.42) * glow;
    return half4(half3(col), 1.0);
}
"""

@Composable
fun SeaBackground(modifier: Modifier = Modifier, calm: Boolean = false) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) AnimatedSea(modifier, calm)
    else Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0B2A41), Color(0xFF04101C))))
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AnimatedSea(modifier: Modifier, calm: Boolean) {
    val shader = remember { RuntimeShader(Sea) }
    val brush = remember { ShaderBrush(shader) }
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(calm) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val s = (now - start) / 1_000_000_000f
                time.floatValue = if (calm) s * 0.15f else s
            }
        }
    }
    Canvas(modifier) {
        shader.setFloatUniform("res", size.width, size.height)
        shader.setFloatUniform("t", time.floatValue)
        drawRect(brush)
    }
}
