package app.afli

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color(0xFF0B2235)))
                Box(
                    Modifier.align(Alignment.Center).size(200.dp).drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedCornerShape(32.dp) },
                        effects = {
                            blur(8.dp.toPx())
                            lens(16.dp.toPx(), 24.dp.toPx())
                        },
                    ),
                    contentAlignment = Alignment.Center,
                ) { Text("Afli", color = Color.White) }
            }
        }
    }
}
