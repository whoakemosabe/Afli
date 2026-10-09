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
        Tips.bind(Repo.store())
        UpdateWatch.createChannel(this)
        UpdateWatch.schedule(this)
        UpdateWatch.waitingVersion.value = UpdateWatch.waiting(this)
        handleIntent(intent)
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
        handleIntent(intent)
    }

    /** The "update ready" notification asks to open the update section. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(UpdateWatch.EXTRA_OPEN_UPDATES, false) == true) {
            intent.removeExtra(UpdateWatch.EXTRA_OPEN_UPDATES)
            UpdateWatch.openUpdates.value = true
        }
    }
}
