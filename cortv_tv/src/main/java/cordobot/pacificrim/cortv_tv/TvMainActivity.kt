package cordobot.pacificrim.cortv_tv

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import cordobot.pacificrim.cortv_tv.ui.screens.TvMainScreen
import cordobot.pacificrim.cortv_tv.ui.theme.CorTvColors
import cordobot.pacificrim.cortv_tv.ui.theme.CorTvTheme

class TvMainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CorTvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape,
                    colors = SurfaceDefaults.colors(containerColor = CorTvColors.Background)
                ) {
                    // Changing the app language recreates the activity so every text is reloaded
                    TvMainScreen(onLanguageChanged = { recreate() })
                }
            }
        }
    }
}
