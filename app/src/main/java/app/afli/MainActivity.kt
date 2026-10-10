package app.afli

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import app.afli.data.Repo
import app.afli.ui.AfliApp
import app.afli.ui.Tips
import app.afli.update.UpdateWatch
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        Repo.init(this)
        loadLang(this)
        Prefs.load(this)
        Tips.bind(Repo.store())
        UpdateWatch.createChannel(this)
        UpdateWatch.schedule(this)
        app.afli.update.BiteAlerts.sync(this)
        app.afli.update.TripNotice.update(this)
        UpdateWatch.waitingVersion.value = UpdateWatch.waiting(this)
        // Only a fresh launch acts on a notification tap; a rotation or a launch from recents
        // would otherwise replay it.
        if (savedInstanceState == null && (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) handleIntent(intent)
        setContent {
            // No stretch overscroll: it fights the glass and the header fade.
            CompositionLocalProvider(LocalOverscrollFactory provides null) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    AfliApp()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Opening the app asks GitHub for a new version at most hourly.
        lifecycleScope.launch { UpdateWatch.check(this@MainActivity, background = false) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** The "update ready" notification asks to open the update section. */
    private fun handleIntent(intent: Intent?) {
        // The trip notification opens the log.
        if (intent?.getBooleanExtra(app.afli.update.TripNotice.EXTRA_OPEN_LOG, false) == true) {
            intent.removeExtra(app.afli.update.TripNotice.EXTRA_OPEN_LOG)
            app.afli.ui.OpenTab.request = 2
        }
        // A bite alert opens Now on the hour it's about.
        val jump = intent?.getLongExtra(app.afli.update.BiteAlerts.EXTRA_JUMP, 0L) ?: 0L
        if (jump > 0) {
            intent?.removeExtra(app.afli.update.BiteAlerts.EXTRA_JUMP)
            // Show the spot the alert was about, then its hour.
            intent?.getStringExtra(app.afli.update.BiteAlerts.EXTRA_SPOT)?.let { id ->
                intent.removeExtra(app.afli.update.BiteAlerts.EXTRA_SPOT)
                Repo.state.value.spots.firstOrNull { it.id == id }?.let { Repo.choose(this, it) }
            }
            app.afli.ui.Scrub.jump = jump
        }
        if (intent?.getBooleanExtra(UpdateWatch.EXTRA_OPEN_UPDATES, false) == true) {
            intent.removeExtra(UpdateWatch.EXTRA_OPEN_UPDATES)
            UpdateWatch.openUpdates.value = true
        }
    }
}
