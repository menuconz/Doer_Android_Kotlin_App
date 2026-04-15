package nz.co.doer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Primary = Color(0xFF667685)
private val White = Color(0xFFFFFFFF)
private val Black = Color(0xFF000000)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = White,
    primaryContainer = Primary,
    secondary = Primary,
    background = White,
    surface = White,
    onBackground = Black,
    onSurface = Black,
)

@Composable
fun DoerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
