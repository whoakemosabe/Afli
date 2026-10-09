package app.afli.ui

import android.provider.Settings as SystemSettings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.afli.data.Repo
import app.afli.update.UpdateWatch
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.roundToInt

private enum class Sheet { SETTINGS, FIX_SPOT }

private val tabs = listOf("Now", "Forecast", "Log", "Guide")

@Composable
fun AfliApp() {
    val context = LocalContext.current
    val view = LocalView.current
    val s by Repo.state.collectAsState()
    val backdrop = rememberLayerBackdrop()
    var onboarded by remember { mutableStateOf(Repo.store().onboarded) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val calm = remember { SystemSettings.Global.getFloat(context.contentResolver, SystemSettings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val openUpdates by UpdateWatch.openUpdates.collectAsState()

    LaunchedEffect(onboarded) { if (onboarded) Repo.refresh(context) }
    LaunchedEffect(openUpdates) {
        if (openUpdates) {
            sheet = Sheet.SETTINGS
            UpdateWatch.openUpdates.value = false
        }
    }
    BackHandler(enabled = sheet != null || tab != 0) {
        if (sheet != null) sheet = null else tab = 0
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerH = statusTop + 64.dp
    // The update banner sits under the header; the page makes room for it while it's shown.
    val waitingUpdate by UpdateWatch.waitingVersion.collectAsState()
    val bannerSpace by animateDpAsState(if (waitingUpdate != null && waitingUpdate != bannerHidden) 84.dp else 0.dp, tween(320), label = "banner")
    val contentTop = headerH + 8.dp + bannerSpace
    val touring by Tips.tour
    val blur by animateDpAsState(
        when {
            sheet != null -> 18.dp
            touring != null -> 12.dp
            else -> 0.dp
        },
        tween(320),
        label = "pageBlur",
    )
    // A sharp recording of the page, so a tip's spotlight can show its target unblurred.
    val pageLayer = rememberGraphicsLayer()

    // Two backdrops: cards in the scroll refract the sea; everything that floats above the
    // page (header, bottom bar, chips, sheets, tips) refracts the page itself, sea and content
    // together, so the glass visibly bends and frosts what slides under it.
    val pageBackdrop = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize().background(C.navy)) {
        Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop)) {
            SeaBackground(Modifier.fillMaxSize().layerBackdrop(backdrop), calm)
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                if (!onboarded) {
                    Onboarding {
                        Repo.store().onboarded = true
                        onboarded = true
                    }
                } else Box(Modifier.fillMaxSize().blur(blur).backdropSource(pageLayer)) {
                    AnimatedContent(
                        tab,
                        transitionSpec = {
                            (fadeIn(tween(260)) + scaleIn(tween(320), initialScale = 0.985f)) togetherWith
                                (fadeOut(tween(160)) + scaleOut(tween(200), targetScale = 1.01f))
                        },
                        label = "tab",
                        modifier = Modifier.fillMaxSize(),
                    ) { t ->
                        when (t) {
                            0 -> NowScreen(
                                s, contentTop,
                                onSpot = { Repo.choose(context, it) },
                                onFixSpot = { sheet = Sheet.FIX_SPOT },
                                onRetry = { Repo.refresh(context) },
                            )
                            1 -> ForecastScreen(s, contentTop)
                            2 -> LogScreen(
                                s, contentTop,
                                onStart = { Repo.startTrip(context) },
                                onCatch = { Repo.addCatch(it) },
                                onUndo = { Repo.undoCatch() },
                                onEnd = { Repo.endTrip() },
                                onDelete = { Repo.deleteTrip(it) },
                            )
                            else -> GuideScreen(contentTop)
                        }
                    }
                }
            }
        }

        if (onboarded) CompositionLocalProvider(LocalBackdrop provides pageBackdrop) {
            // Frosted header band: the page scrolls under it and shows through, blurred.
            Box(Modifier.fillMaxWidth().height(headerH + 24.dp).blur(blur).glassHeader(pageBackdrop))
            Row(
                Modifier.fillMaxWidth().blur(blur).statusBarsPadding().height(64.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tabs[tab], style = T.title)
                    Text(
                        when {
                            s.loading -> "Updating…"
                            s.forecast != null -> "Updated ${clock(s.forecast!!.fetchedAt)}"
                            else -> " "
                        },
                        style = T.small,
                    )
                }
                GlassChip("Refresh", explain = "Finds where you are again and reloads the forecast.", onClick = { Repo.refresh(context) })
                androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                GlassChip("Settings", explain = "Updates, tips and data credits.", onClick = { sheet = Sheet.SETTINGS })
            }

            UpdateBanner(Modifier.padding(top = headerH + 4.dp).blur(blur))

            BottomBar(tab, Modifier.align(Alignment.BottomCenter)) {
                if (it != tab) {
                    Haptics.segment(view)
                    tab = it
                }
            }

            ExplainBubble(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 92.dp))

            SheetHost(sheet != null, onClose = { sheet = null }) {
                when (sheet) {
                    Sheet.SETTINGS -> SettingsContent(onReplayIntro = {
                        sheet = null
                        Repo.store().onboarded = false
                        onboarded = false
                    })
                    Sheet.FIX_SPOT -> s.spot?.let { SpotFixContent(it) { sheet = null } }
                    null -> {}
                }
            }

            CoachOverlay(pageLayer)
        }
    }
}

/** A floating capsule of liquid glass with a brass pill that springs to the selected tab. */
@Composable
private fun BottomBar(selected: Int, modifier: Modifier, onSelect: (Int) -> Unit) {
    val backdrop = LocalBackdrop.current
    val press = rememberPress()
    val shape = RoundedCornerShape(50)
    BoxWithConstraints(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .fillMaxWidth()
            .height(64.dp)
            .let { if (backdrop != null) it.glassControl(backdrop, shape, press = press.amount) else it.background(Color(0xCC07121F), shape) },
    ) {
        val w = maxWidth / tabs.size
        val density = LocalDensity.current
        val x by animateFloatAsState(
            with(density) { (w * selected).toPx() },
            spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
            label = "tabPill",
        )
        Box(
            Modifier
                .offset { IntOffset(x.roundToInt(), 0) }
                .width(w)
                .fillMaxSize()
                .padding(6.dp)
                .background(Color(0x33D8B56A), shape),
        )
        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = T.small.copy(color = if (i == selected) C.brass else C.mist),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * A glass sheet that rises from the bottom over a dimmed, blurred page. Drag it down to close:
 * from the handle, or from the content once it's scrolled to the top. Let go past a third of
 * its height, or flick down, and it closes; otherwise it springs back.
 */
@Composable
private fun SheetHost(open: Boolean, onClose: () -> Unit, content: @Composable () -> Unit) {
    val backdrop = LocalBackdrop.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    var sheetH by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(open) { if (open) drag.snapTo(0f) }
    val pulled = (drag.value / sheetH).coerceIn(0f, 1f)
    val scrim by animateFloatAsState(if (open) 1f else 0f, tween(300), label = "scrim")

    fun settle(velocity: Float) {
        scope.launch {
            if (drag.value > sheetH / 3f || velocity > 1800f) {
                Haptics.settle(view)
                onClose()
            } else {
                drag.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium), initialVelocity = velocity)
            }
        }
    }

    // Content scroll hands over to the sheet: pulling down at the top drags the sheet.
    val nested = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0 && drag.value > 0f) {
                    val used = maxOf(available.y, -drag.value)
                    scope.launch { drag.snapTo(drag.value + used) }
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && source == NestedScrollSource.UserInput) {
                    scope.launch { drag.snapTo(drag.value + available.y) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drag.value > 0f) {
                    settle(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    if (scrim > 0.01f) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = scrim * (1f - pulled * 0.7f) }
                .background(Color(0x99020810))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            open,
            enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
            exit = slideOutVertically(tween(240)) { it } + fadeOut(tween(200)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 720.dp)
                    .onSizeChanged { sheetH = it.height.toFloat().coerceAtLeast(1f) }
                    .offset { IntOffset(0, drag.value.roundToInt().coerceAtLeast(0)) }
                    .let { if (backdrop != null) it.glassSheet(backdrop) else it.background(Color(0xF207121F), RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)) }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .draggable(
                        rememberDraggableState { d -> scope.launch { drag.snapTo((drag.value + d).coerceAtLeast(0f)) } },
                        Orientation.Vertical,
                        onDragStopped = { v -> settle(v) },
                    )
                    .nestedScroll(nested)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.width(40.dp).height(4.dp).background(C.mist.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
                Box(Modifier.height(14.dp))
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    content()
                }
            }
        }
    }
}
