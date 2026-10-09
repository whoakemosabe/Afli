package app.afli.ui

import app.afli.t

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.afli.data.Store
import kotlinx.coroutines.delay

/**
 * In-app help. Two kinds:
 * - Long-press anything to get a one-line explanation in a glass bubble ([explain]).
 * - Coach marks: the first time a screen opens, a short tour dims the screen and points a glass
 *   bubble at one thing at a time. Each tour runs once; Settings can show them again.
 */
object Tips {
    val bubble = mutableStateOf<String?>(null)
    private var serial = 0
    val serialState = mutableStateOf(0)

    fun explain(text: String) {
        bubble.value = text
        serial++
        serialState.value = serial
    }

    fun dismiss() {
        bubble.value = null
    }

    // ---- coach marks ----

    data class Step(val target: String, val title: String, val text: String)

    /** Where each target sits on screen, recorded as it's laid out. */
    val targets = mutableStateMapOf<String, Rect>()

    /** The tour on screen now, and which step. */
    val tour = mutableStateOf<Pair<String, List<Step>>?>(null)
    val step = mutableStateOf(0)

    private var store: Store? = null

    fun bind(store: Store) {
        this.store = store
    }

    /** Starts [key]'s tour if it hasn't been seen. */
    fun maybeTour(key: String, steps: List<Step>) {
        val s = store ?: return
        if (s.tipSeen(key) || tour.value != null) return
        tour.value = key to steps
        step.value = 0
    }

    fun next() {
        val t = tour.value ?: return
        if (step.value < t.second.lastIndex) step.value++ else finish()
    }

    fun finish() {
        val t = tour.value ?: return
        store?.markTip(t.first)
        tour.value = null
    }
}

/** Marks a composable as a coach-mark target. */
fun Modifier.coachTarget(key: String): Modifier = onGloballyPositioned { Tips.targets[key] = it.boundsInRoot() }

/** The long-press explanation bubble, floating above the bottom bar. */
@Composable
fun ExplainBubble(modifier: Modifier = Modifier) {
    val text by Tips.bubble
    val serial by Tips.serialState
    LaunchedEffect(serial) {
        if (text != null) {
            delay(5_500)
            Tips.dismiss()
        }
    }
    val last = remember { mutableStateOf("") }
    if (text != null) last.value = text!!
    AnimatedVisibility(
        visible = text != null,
        modifier = modifier,
        enter = fadeIn(tween(180)) + slideInVertically(tween(260)) { it / 3 } + scaleIn(tween(260), initialScale = 0.96f),
        exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 4 },
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(22.dp),
            glow = C.brass,
            onClick = { Tips.dismiss() },
        ) {
            Text(last.value, style = T.body)
            Spacer(Modifier.height(4.dp))
            Text(t("Tap to close", "Ýttu til að loka"), style = T.small.copy(color = C.faint))
        }
    }
}

/**
 * The tour: the page behind is blurred (by AfliApp) and dimmed, and the target is cut out of the
 * dim and redrawn sharp from [page], with a pulsing brass glow around it. The spotlight glides
 * from one target to the next. Tap anywhere to move on.
 */
@Composable
fun CoachOverlay(page: GraphicsLayer?) {
    val tour by Tips.tour
    val stepIndex by Tips.step
    val tr = tour ?: return
    val step = tr.second.getOrNull(stepIndex) ?: return
    val target = Tips.targets[step.target]
    val density = LocalDensity.current
    val shown = remember(tr.first) { Animatable(0f) }
    LaunchedEffect(tr.first) { shown.animateTo(1f, tween(380)) }
    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )
    val glide = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    val l by animateFloatAsState(target?.left ?: 0f, glide, label = "l")
    val tp by animateFloatAsState(target?.top ?: 0f, glide, label = "t")
    val r by animateFloatAsState(target?.right ?: 0f, glide, label = "r")
    val b by animateFloatAsState(target?.bottom ?: 0f, glide, label = "b")
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = shown.value }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { Tips.next() },
    ) {
        val pad = with(density) { 10.dp.toPx() }
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color(0x9E020810))
            if (target != null) {
                val corner = CornerRadius(28.dp.toPx())
                val topLeft = Offset(l - pad, tp - pad)
                val box = androidx.compose.ui.geometry.Size(r - l + pad * 2, b - tp + pad * 2)
                drawRoundRect(Color.Black, topLeft, box, corner, blendMode = BlendMode.Clear)
                // The target itself, sharp, from the unblurred recording of the page.
                if (page != null) {
                    val hole = Path().apply { addRoundRect(RoundRect(Rect(topLeft, box), corner)) }
                    clipPath(hole) { drawLayer(page) }
                }
                // Brass glow: soft rings fading outward, breathing gently, then a crisp rim.
                val breathe = 0.55f + 0.45f * pulse
                for (k in 1..5) {
                    val grow = k * 3.dp.toPx()
                    drawRoundRect(
                        C.brass.copy(alpha = (0.26f - k * 0.045f).coerceAtLeast(0.02f) * breathe),
                        Offset(topLeft.x - grow / 2, topLeft.y - grow / 2),
                        androidx.compose.ui.geometry.Size(box.width + grow, box.height + grow),
                        CornerRadius(corner.x + grow / 2),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
                drawRoundRect(C.brass.copy(alpha = 0.75f + 0.25f * pulse), topLeft, box, corner, style = Stroke(width = 1.5.dp.toPx()))
            }
        }
        val screenH = with(density) { maxHeight.toPx() }
        val below = target == null || target.center.y < screenH * 0.55f
        val y = when {
            target == null -> screenH * 0.4f
            below -> target.bottom + pad * 2.5f
            else -> target.top - pad * 2.5f
        }
        Column(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, if (below) y.toInt() else 0) }
                .let { if (below) it else it.offset { IntOffset(0, (y - with(density) { 170.dp.toPx() }).toInt()) } }
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GlassCard(Modifier.widthIn(max = 420.dp), shape = RoundedCornerShape(24.dp), glow = C.brass, onClick = { Tips.next() }) {
                Text(step.title, style = T.heading.copy(color = C.brass))
                Spacer(Modifier.height(6.dp))
                Text(step.text, style = T.body)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("${stepIndex + 1} of ${tr.second.size}", "${stepIndex + 1} af ${tr.second.size}"), style = T.small, modifier = Modifier.weight(1f))
                    Text(if (stepIndex == tr.second.lastIndex) t("Got it", "Skilið") else t("Next", "Áfram"), style = T.heading.copy(color = C.brass))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                t("Skip tips", "Sleppa ábendingum"),
                style = T.small,
                modifier = Modifier
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { Tips.finish() }
                    .padding(10.dp),
            )
        }
    }
}

/** A plain spacer the size of the bottom bar, so content and bubbles clear it. */
@Composable
fun BottomBarSpace() = Box(Modifier.navigationBarsPadding().height(96.dp))
