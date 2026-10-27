package cordobot.pacificrim.cortv_tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

object CorTvColors {
    val Background = Color(0xFF0C0C16)
    val Card = Color(0xFF1B1B2A)
    val CardFocused = Color(0xFF2A2A3D)
    val Accent = Color(0xFFE21B4D)
    val Text = Color.White
    val SecondaryText = Color(0xFF8E8E9E)
    val Neutral = Color(0xFF3A3A4C)
}

@Composable
fun CorTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = CorTvColors.Accent,
            onPrimary = Color.White,
            background = CorTvColors.Background,
            onBackground = CorTvColors.Text,
            surface = CorTvColors.Background,
            onSurface = CorTvColors.Text,
            surfaceVariant = CorTvColors.Card,
            onSurfaceVariant = CorTvColors.SecondaryText,
            inverseSurface = Color.White,
            inverseOnSurface = Color.Black,
            border = CorTvColors.Accent
        ),
        content = content
    )
}
