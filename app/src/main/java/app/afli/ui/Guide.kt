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
import androidx.compose.foundation.ScrollState
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
import app.afli.L
import app.afli.Lang
import app.afli.t
import app.afli.windText
import app.afli.windNum
import app.afli.tempText
import app.afli.tempNum
import app.afli.tempUnit
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/** The in-app manual: one short card per feature, plus the fish. */
@Composable
fun GuideScreen(top: Dp, scroll: ScrollState) {
    Column(
        Modifier.verticalScroll(scroll).padding(top = top).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        guide.forEach { (title, body) ->
            GlassCard(Modifier.fillMaxWidth()) {
                Text(title, style = T.heading.copy(color = C.brass))
                Spacer(Modifier.height(6.dp))
                Text(body, style = T.body)
            }
        }
        SectionLabel(t("The fish", "Fiskarnir"))
        (Fish.sea + Fish.lake).forEach { f ->
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(f.name, style = T.heading)
                    Spacer(Modifier.width(8.dp))
                    Text(f.other, style = T.small)
                }
                Spacer(Modifier.height(4.dp))
                Text(f.fact.toString(), style = T.small)
                Spacer(Modifier.height(4.dp))
                Text(t("Try: ", "Prófaðu: ") + f.bait, style = T.small.copy(color = C.foam))
                Text(
                    t("Happiest in ", "Kýs ") + "${tempNum(f.optLo)}–${tempNum(f.optHi)} $tempUnit" + t(" water", "") + if (f.reach < 0.5) t(" · rare from shore", " · veiðist sjaldan frá landi") else "",
                    style = T.small.copy(color = C.faint),
                )
            }
        }
        BottomBarSpace()
    }
}

private val guide: List<Pair<String, String>>
    get() = listOf(
        t("The bite score", "Tökulíkurnar") to t(
            "A number from 0 to 100 for how likely fish are to bite at this water. Over 60 is great, 35 to 60 is OK, under 35 is slow. It mixes moving tide, light, air pressure, wind and water temperature, and whether each fish likes the water today.",
            "Tala frá 0 upp í 100 um hversu líklegt er að fiskur taki á þessum stað. Yfir 60 er frábært, 35 til 60 ágætt og undir 35 rólegt. Hún blandar saman straumi, birtu, loftþrýstingi, vindi og sjávarhita, og hvort hverjum fiski líki vatnið í dag.",
        ),
        t("Swipe through time", "Flettu í gegnum tímann") to t(
            "Under the score is a strip of the next 48 hours. Drag it and the whole Now screen changes to that hour: score, fish, tide, wind. Tap Best to jump to the best stretch, or Back to now.",
            "Undir tölunni er ræma með næstu 48 tímum. Dragðu hana og allur Núna-skjárinn sýnir þann tíma: líkur, fiska, sjávarföll og vind. Ýttu á Best til að hoppa á besta tímann, eða Aftur í núna.",
        ),
        t("Best fish and what to use", "Bestu fiskarnir og agnið") to t(
            "The score card shows the three fish most likely to bite and what to fish with for the top one. Swipe the fish cards further down for every fish, its best hour and bait.",
            "Spjaldið sýnir þrjá fiska sem eru líklegastir til að taka og hvað á að nota fyrir þann efsta. Flettu fiskaspjöldunum neðar til að sjá alla fiskana, besta tímann og beituna.",
        ),
        t("Safe, Careful, Stay home", "Öruggt, Varúð, Vertu heima") to t(
            "Checked on its own from wind gusts, waves and cold. A great bite score never makes a dangerous day safe. Stay home means stay home.",
            "Metið sérstaklega út frá hviðum, öldum og kulda. Góðar tökulíkur gera hættulegan dag aldrei öruggan. Vertu heima þýðir vertu heima.",
        ),
        t("Why chips", "Af hverju") to t(
            "Under the score, green dots help and red dots hurt. Tap or long-press one to read what it means.",
            "Undir tölunni hjálpa grænir punktar og rauðir draga niður. Ýttu á einn til að lesa hvað hann þýðir.",
        ),
        t("Conditions", "Aðstæður") to t(
            "Wind, tide, sea, pressure, light and air as tiles. Tap one to open its chart; tap again to close it.",
            "Vindur, sjávarföll, sjór, loftþrýstingur, birta og loft á reitum. Ýttu á reit til að opna línuritið og aftur til að loka.",
        ),
        t("Where you are", "Hvar þú ert") to t(
            "Afli uses GPS to find your water. Spots you fish at are saved automatically when you start a trip there. Keflavík and Njarðvík harbours, Kleifarvatn and Seltjörn are built in.",
            "Afli notar GPS til að finna veiðistaðinn þinn. Staðir vistast sjálfkrafa þegar þú byrjar ferð þar. Keflavíkurhöfn, Njarðvíkurhöfn, Kleifarvatn og Seltjörn eru innbyggð.",
        ),
        t("Fix spot", "Stilla stað") to t(
            "Point your phone at the water and tap Set, so Afli knows when the wind is onshore or offshore. Say if it's a harbour, open coast or a lake.",
            "Beindu símanum þangað sem þú kastar og ýttu á Stilla svo Afli viti hvenær vindur blæs að landi eða frá. Segðu hvort þetta sé höfn, opin strönd eða vatn.",
        ),
        t("Tides", "Sjávarföll") to t(
            "At Keflavík, Njarðvík and the other Reykjanes harbours the tides come from the Coast Guard's tide tables; elsewhere from a sea model, which is less exact near the shore. Times run along the bottom, with the time of each high and low. Fish usually feed best while the water is moving, not at the turn.",
            "Við Keflavík, Njarðvík og aðrar hafnir á Reykjanesi koma sjávarföllin úr sjávarfallatöflum Landhelgisgæslunnar; annars staðar úr líkani sem er ónákvæmara nálægt landi. Tíminn er neðst og tími hvers flóðs og fjöru er merktur. Fiskurinn tekur yfirleitt best þegar sjórinn er á hreyfingu, ekki á liggjandanum.",
        ),
        t("Log every trip", "Skráðu hverja ferð") to t(
            "Tap Start fishing at the water, tap a fish each time you catch one, and End trip when you leave. Log the empty trips too: they show when fish don't bite.",
            "Ýttu á Byrja að veiða á staðnum, ýttu á fisk í hvert sinn sem þú veiðir og Ljúka ferð þegar þú ferð. Skráðu líka ferðir þar sem ekkert veiddist: þær sýna hvenær fiskurinn tekur ekki.",
        ),
        t("Forecast", "Spá") to t(
            "Seven days of hour bars coloured by bite score. Red-tinted bars are Stay home. Tap a bar for that hour, then See in Now for the full picture. Days far ahead are less certain.",
            "Sjö dagar af súlum, einni fyrir hvern tíma, litaðar eftir líkum. Rauðleitar súlur þýða Vertu heima. Ýttu á súlu til að sjá þann tíma og svo Sjá í Núna fyrir alla myndina. Dagar langt fram í tímann eru óvissari.",
        ),
        t("Lakes", "Vötn") to t(
            "Lake fishing in Iceland needs a permit (Veiðikortið or the local club) and the landowner's OK. No fishing at night, and the season is about May to September. Some lakes are fly only.",
            "Til að veiða í vötnum þarf veiðileyfi (Veiðikortið eða veiðifélag staðarins) og leyfi landeiganda. Engin veiði á nóttunni og tímabilið er um maí til september. Í sumum vötnum má bara veiða á flugu.",
        ),
        t("Language", "Tungumál") to t(
            "Settings → Language switches Afli between English and Icelandic. Fish names always show in both.",
            "Stillingar → Tungumál skiptir Afla á milli ensku og íslensku. Nöfn fiskanna sjást alltaf á báðum málum.",
        ),
        t("Updates", "Uppfærslur") to t(
            "Afli checks for new versions by itself. When one is out, a banner shows at the top: tap it to download, tap again to install. Settings → Updates does the same.",
            "Afli leitar sjálfur að nýjum útgáfum. Þegar ný er komin birtist borði efst: ýttu til að sækja og aftur til að setja upp. Stillingar → Uppfærslur gera það sama.",
        ),
    )

/** First launch: three swipeable glass cards, then the location request. */
@Composable
fun Onboarding(onDone: () -> Unit) {
    val view = LocalView.current
    val pages = listOf(
        Triple(t("This is your bite score", "Þetta eru tökulíkurnar"), t("Big number means a good time to fish. Afli works it out from the tide, light, wind, pressure and water temperature.", "Há tala þýðir góðan tíma til að veiða. Afli reiknar hana út frá sjávarföllum, birtu, vindi, loftþrýstingi og sjávarhita."), C.good),
        Triple(t("This says if it's safe", "Hér sérðu hvort óhætt er að veiða"), t("Safe, Careful or Stay home, from wind gusts and waves. Stay home means stay home.", "Öruggt, Varúð eða Vertu heima, út frá hviðum og öldum. Vertu heima þýðir vertu heima."), C.ok),
        Triple(t("Log every trip", "Skráðu hverja ferð"), t("Tap Start fishing at the water. Log the empty trips too, so you can see what works at your spots.", "Ýttu á Byrja að veiða á staðnum. Skráðu líka ferðir þar sem ekkert veiddist svo þú sjáir hvað virkar á þínum stöðum."), C.brass),
    )
    val pager = rememberPagerState { pages.size + 1 }
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onDone() }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Afli", style = T.title.copy(color = C.brass), modifier = Modifier.weight(1f))
            // Skip goes to the location card, not past it: GPS is how Afli finds the water.
            // Language first, so the intro itself can be read in Icelandic.
            GlassChip(if (L.isl) "English" else "Íslenska", onClick = {
                L.lang = if (L.isl) Lang.EN else Lang.IS
                app.afli.data.Repo.store().lang = L.lang.code
                Haptics.confirm(view)
            })
            if (pager.currentPage < pages.size) {
                Spacer(Modifier.width(8.dp))
                GlassChip(t("Skip", "Sleppa"), onClick = { scope.launch { pager.animateScrollToPage(pages.size) } })
            }
        }
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
                        Text(t("Where's your water?", "Hvar veiðirðu?"), style = T.title)
                        Spacer(Modifier.height(8.dp))
                        Text(t("Afli uses your location to know which water you're at. It only checks while the app is open.", "Afli notar staðsetninguna til að vita á hvaða veiðistað þú ert. Hann athugar bara á meðan appið er opið."), style = T.body)
                        Spacer(Modifier.height(16.dp))
                        GlassButton(t("Allow location", "Leyfa staðsetningu"), accent = C.brass, onClick = {
                            Haptics.confirm(view)
                            val perms = buildList {
                                add(Manifest.permission.ACCESS_FINE_LOCATION)
                                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permission.launch(perms.toTypedArray())
                        })
                        Spacer(Modifier.height(8.dp))
                        GlassButton(t("Not now", "Ekki núna"), style = T.small, onClick = onDone)
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
                GlassButton(t("Next", "Áfram"), onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } })
            }
        }
    }
}
