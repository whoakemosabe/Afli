package app.afli.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth

/**
 * Press effect for anything tappable: a springy squish, the glass lens deepening under the
 * finger (via the returned press value), a haptic tick on tap, and long-press to explain.
 */
class Press(val interaction: MutableInteractionSource, val amount: () -> Float)

@Composable
fun rememberPress(): Press {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val amount by animateFloatAsState(
        if (pressed) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    // The lambda reads the animated State each time it's drawn, so glass and scale stay live.
    return remember(interaction) { Press(interaction) { amount } }
}

/**
 * Shrinks this node, glass and all, while [press] is down. Put it *before* the glass modifier in
 * the chain so the whole pane squishes, not just the text inside it.
 */
fun Modifier.squish(press: Press, by: Float): Modifier = graphicsLayer(press.squishLayer(by))

/** The same squish as a layer block, handed to the glass so its refraction stays lined up. */
fun Press.squishLayer(by: Float, byY: Float = by): GraphicsLayerScope.() -> Unit = {
    val k = amount()
    scaleX = 1f - by * k
    scaleY = 1f - byY * k
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressable(
    press: Press,
    explain: String? = null,
    scaleBy: Float = 0.04f,
    haptic: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val view = LocalView.current
    return this
        .let { if (scaleBy > 0f) it.squish(press, scaleBy) else it }
        .combinedClickable(
            interactionSource = press.interaction,
            indication = null,
            onLongClick = explain?.let {
                {
                    Haptics.reveal(view)
                    Tips.explain(it)
                }
            },
            onClick = {
                if (haptic) Haptics.tap(view)
                onClick()
            },
        )
}

/** Long-press only (for numbers and dials that do nothing on tap). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.explains(text: String): Modifier {
    val view = LocalView.current
    val press = rememberPress()
    return this
        .graphicsLayer {
            val s = 1f - 0.03f * press.amount()
            scaleX = s
            scaleY = s
        }
        .combinedClickable(
            interactionSource = press.interaction,
            indication = null,
            onLongClick = {
                Haptics.reveal(view)
                Tips.explain(text)
            },
            onClick = {},
        )
}

/** A glass card that reacts to touch. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = RoundedCornerShape(26.dp),
    padding: PaddingValues = PaddingValues(18.dp),
    explain: String? = null,
    glow: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalBackdrop.current
    val press = rememberPress()
    val squish = if (onClick != null || explain != null) press.squishLayer(0.02f) else null
    // The glow fades in and out rather than snapping, keeping its last colour while it fades.
    val lastGlow = remember { mutableStateOf(glow ?: C.brass) }
    if (glow != null) lastGlow.value = glow
    // Starts at zero, so a card that appears already glowing (an opened panel, a tip) fades in too.
    val glowAnim = remember { Animatable(0f) }
    LaunchedEffect(glow != null) {
        glowAnim.animateTo(
            if (glow != null) 1f else 0f,
            if (glow != null) tween(750, delayMillis = 140, easing = FastOutSlowInEasing) else tween(320, easing = FastOutSlowInEasing),
        )
    }
    val g = glowAnim.value
    val gc = lastGlow.value
    val shownGlow = if (g > 0.01f) gc.copy(alpha = g) else null
    var m = modifier
    m = if (backdrop != null) m.glassCard(backdrop, shape, press = press.amount, glow = shownGlow, layer = squish)
    else m.let { if (squish != null) it.graphicsLayer(squish) else it }.background(Color(0xCC07121F), shape)
    if (shownGlow != null) m = m.border(1.dp, Brush.verticalGradient(listOf(gc.copy(alpha = 0.95f * g), gc.copy(alpha = 0.35f * g))), shape)
    m = when {
        onClick != null -> m.pressable(press, explain, scaleBy = 0f, onClick = onClick)
        explain != null -> m.pressable(press, explain, scaleBy = 0f, haptic = false, onClick = {})
        else -> m
    }
    Column(m.padding(padding), content = content)
}

/** A glass pill button. */
@Composable
fun GlassButton(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = C.foam,
    style: TextStyle = T.heading,
    explain: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val backdrop = LocalBackdrop.current
    val press = rememberPress()
    val shape = RoundedCornerShape(50)
    val m = if (backdrop != null) modifier.glassControl(backdrop, shape, press = press.amount, layer = press.squishLayer(0.06f))
    else modifier.squish(press, 0.06f).background(Color(0x5506111D), shape)
    Row(
        m.pressable(press, explain, scaleBy = 0f, onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = style.copy(color = accent))
    }
}

/** A small glass chip, e.g. a "why" reason or a spot. */
@Composable
fun GlassChip(
    text: String,
    modifier: Modifier = Modifier,
    dot: Color? = null,
    selected: Boolean = false,
    explain: String? = null,
    onClick: () -> Unit = {},
) {
    val backdrop = LocalBackdrop.current
    val press = rememberPress()
    val shape = RoundedCornerShape(50)
    val tint = if (selected) Color(0x55D8B56A) else Color(0x2605080F)
    val m = if (backdrop != null) modifier.glassControl(backdrop, shape, press = press.amount, tint = tint, layer = press.squishLayer(0.07f))
    else modifier.squish(press, 0.07f).background(tint.copy(alpha = 0.6f), shape)
    Row(
        m.pressable(press, explain, scaleBy = 0f, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(8.dp).background(dot, CircleShape))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = T.small.copy(color = if (selected) C.brass else C.foam))
    }
}

/**
 * A round glass button with one of Afli's icons, 44 dp so it's easy to hit. [spin] turns the
 * icon continuously (Refresh while loading) and lets it finish its turn when it stops.
 */
@Composable
fun GlassIconButton(
    icon: Icon,
    explain: String,
    modifier: Modifier = Modifier,
    spin: Boolean = false,
    onClick: () -> Unit,
) {
    val backdrop = LocalBackdrop.current
    val press = rememberPress()
    val rot = remember { Animatable(0f) }
    LaunchedEffect(spin) {
        if (spin) while (true) rot.animateTo(rot.value + 360f, tween(900, easing = LinearEasing))
        else rot.animateTo(kotlin.math.ceil(rot.value / 360f) * 360f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
    }
    val m = if (backdrop != null) modifier.size(44.dp).glassControl(backdrop, CircleShape, press = press.amount, layer = press.squishLayer(0.1f))
    else modifier.size(44.dp).squish(press, 0.1f).background(Color(0x5506111D), CircleShape)
    Box(
        m.border(1.dp, Rim, CircleShape).pressable(press, explain, scaleBy = 0f, onClick = onClick).semantics { contentDescription = explain },
        contentAlignment = Alignment.Center,
    ) {
        AfliIcon(icon, C.foam, Modifier.size(22.dp).graphicsLayer { rotationZ = rot.value })
    }
}

/**
 * The crisp rim on floating glass: bright where light catches the top-left, nearly gone along
 * the sides, a warm brass glint bottom-right.
 */
val Rim = Brush.linearGradient(
    0f to Color(0xCCEAF4F8),
    0.35f to Color(0x26EAF4F8),
    0.65f to Color(0x1A2E8FB5),
    1f to Color(0xB3D8B56A),
)

/**
 * One line of text that shrinks (down to [min]) rather than getting cut off, for tight spots
 * like fish names and tile values. Only shrinks when it has to.
 */
@Composable
fun FitText(text: String, style: TextStyle, modifier: Modifier = Modifier, min: androidx.compose.ui.unit.TextUnit = 10.sp) {
    androidx.compose.foundation.text.BasicText(
        text,
        modifier = modifier,
        style = style,
        maxLines = 1,
        softWrap = false,
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = min, maxFontSize = style.fontSize, stepSize = 0.5.sp),
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    // A short brass bar, the title in clear caps, and a hairline that fades out to the right.
    Row(modifier.fillMaxWidth().padding(start = 2.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(14.dp).background(C.brass, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Text(
            text.uppercase(app.afli.L.locale),
            style = T.label.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp, color = C.foam),
            maxLines = 1,
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(C.brass.copy(alpha = 0.45f), Color.Transparent))))
    }
}

@Composable
fun Centered(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) =
    Box(modifier, contentAlignment = Alignment.Center, content = content)
