package app.afli.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.afli.model.Fish
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/** The in-app manual: one short card per feature, plus the fish. */
@Composable
fun GuideScreen(top: Dp) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(top = top).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        guide.forEach { (title, body) ->
            GlassCard(Modifier.fillMaxWidth()) {
                Text(title, style = T.heading.copy(color = C.brass))
                Spacer(Modifier.height(6.dp))
                Text(body, style = T.body)
            }
        }
        SectionLabel("The fish")
        (Fish.sea + Fish.lake).forEach { f ->
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(f.en, style = T.heading)
                    Spacer(Modifier.width(8.dp))
                    Text(f.icelandic, style = T.small)
                }
                Spacer(Modifier.height(4.dp))
                Text(f.fact, style = T.small)
                Text(
                    "Happiest in ${f.optLo.toInt()}–${f.optHi.toInt()} °C water" + if (f.reach < 0.5) " · rare from shore" else "",
                    style = T.small.copy(color = C.faint),
                )
            }
        }
        BottomBarSpace()
    }
}

private val guide = listOf(
    "The bite score" to "A number from 0 to 100 for how likely fish are to bite right now at this water. Over 60 is great, 35 to 60 is OK, under 35 is slow. It mixes moving tide, light, air pressure, wind and water temperature, and whether each fish likes the water today.",
    "Safe, Careful, Stay home" to "Checked on its own from wind gusts, waves and cold. A great bite score never makes a dangerous day safe. Stay home means stay home.",
    "Why chips" to "Under the score, green dots help and red dots hurt. Tap or long-press one to read what it means.",
    "Where you are" to "Afli uses GPS to find your water. Spots you fish at are saved automatically when you start a trip there. Keflavík and Njarðvík harbours, Kleifarvatn and Seltjörn are built in.",
    "Fix spot" to "Point your phone at the water and tap Set, so Afli knows when the wind is onshore or offshore. Say if it's a harbour, open coast or a lake.",
    "Tides" to "The tide curve comes from a sea model and is only approximate close to the shore. Fish usually feed best while the water is moving, not at the turn.",
    "Log every trip" to "Tap Start fishing at the water, tap a fish each time you catch one, and End trip when you leave. Log the empty trips too: they teach Afli when fish don't bite.",
    "Forecast" to "Seven days of hour bars coloured by bite score. Red-tinted bars are Stay home. Tap a bar for that hour. Days far ahead are less certain.",
    "Lakes" to "Lake fishing in Iceland needs a permit (Veiðikortið or the local club) and the landowner's OK. No fishing at night, and the season is about May to September.",
    "Updates" to "Afli checks for new versions by itself and lets you know. Settings → Updates downloads and installs them.",
)

/** First launch: three swipeable glass cards, then the location request. */
@Composable
fun Onboarding(onDone: () -> Unit) {
    val view = LocalView.current
    val pages = listOf(
        Triple("This is your bite score", "Big number means a good time to fish. Afli works it out from the tide, light, wind, pressure and water temperature.", C.good),
        Triple("This says if it's safe", "Safe, Careful or Stay home, from wind gusts and waves. Stay home means stay home.", C.ok),
        Triple("Log every trip", "Tap Start fishing at the water. Log the empty trips too; that's how Afli learns what works at your spots.", C.brass),
    )
    val pager = rememberPagerState { pages.size + 1 }
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onDone() }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Text("Afli", style = T.title.copy(color = C.brass), modifier = Modifier.padding(24.dp))
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            val off = (pager.currentPage - i + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxSize().padding(horizontal = 24.dp).graphicsLayer {
                    alpha = 1f - off * 0.6f
                    scaleX = 1f - off * 0.08f
                    scaleY = 1f - off * 0.08f
                },
                contentAlignment = Alignment.Center,
            ) {
                if (i < pages.size) {
                    val (title, body, col) = pages[i]
                    GlassCard(Modifier.fillMaxWidth()) {
                        Box(Modifier.size(14.dp).background(col, CircleShape))
                        Spacer(Modifier.height(14.dp))
                        Text(title, style = T.title)
                        Spacer(Modifier.height(8.dp))
                        Text(body, style = T.body)
                    }
                } else {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text("Where's your water?", style = T.title)
                        Spacer(Modifier.height(8.dp))
                        Text("Afli uses your location to know which water you're at. It only checks while the app is open.", style = T.body)
                        Spacer(Modifier.height(16.dp))
                        GlassButton("Allow location", accent = C.brass, onClick = {
                            Haptics.confirm(view)
                            val perms = buildList {
                                add(Manifest.permission.ACCESS_FINE_LOCATION)
                                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permission.launch(perms.toTypedArray())
                        })
                        Spacer(Modifier.height(8.dp))
                        GlassButton("Not now", style = T.small, onClick = onDone)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                repeat(pages.size + 1) { i ->
                    val w by animateDpAsState(if (pager.currentPage == i) 22.dp else 8.dp, label = "dot")
                    Box(Modifier.height(8.dp).width(w).background(if (pager.currentPage == i) C.brass else C.line, CircleShape))
                }
            }
            if (pager.currentPage < pages.size) {
                GlassButton("Next", onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } })
            }
        }
    }
}
