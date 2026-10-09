package app.afli.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
            onClick = { Tips.dismiss() },
        ) {
            Text(last.value, style = T.body)
            Spacer(Modifier.height(4.dp))
            Text("Tap to close", style = T.small.copy(color = C.faint))
        }
    }
}

/** Dims the screen, cuts a soft hole around the target and shows the step in a glass bubble. */
@Composable
fun CoachOverlay() {
    val tour by Tips.tour
    val stepIndex by Tips.step
    val t = tour ?: return
    val step = t.second.getOrNull(stepIndex) ?: return
    val target = Tips.targets[step.target]
    val density = LocalDensity.current
    val shown by animateFloatAsState(1f, tween(300), label = "coach")
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = shown }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { Tips.next() },
    ) {
        val pad = with(density) { 10.dp.toPx() }
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color(0xB3020810))
            if (target != null) {
                drawRoundRect(
                    color = Color.Black,
                    topLeft = Offset(target.left - pad, target.top - pad),
                    size = androidx.compose.ui.geometry.Size(target.width + pad * 2, target.height + pad * 2),
                    cornerRadius = CornerRadius(28f * density.density),
                    blendMode = BlendMode.Clear,
                )
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
            GlassCard(Modifier.widthIn(max = 420.dp), shape = RoundedCornerShape(24.dp), onClick = { Tips.next() }) {
                Text(step.title, style = T.heading.copy(color = C.brass))
                Spacer(Modifier.height(6.dp))
                Text(step.text, style = T.body)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${stepIndex + 1} of ${t.second.size}", style = T.small, modifier = Modifier.weight(1f))
                    Text(if (stepIndex == t.second.lastIndex) "Got it" else "Next", style = T.heading.copy(color = C.brass))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Skip tips",
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
