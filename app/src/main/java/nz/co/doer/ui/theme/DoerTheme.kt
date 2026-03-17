package nz.co.doer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Primary = Color(0xFF667685)
private val PrimaryDark = Color(0xFF4A5A67)
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

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = White,
    primaryContainer = PrimaryDark,
    secondary = Primary,
    background = Color(0xFF1C1C1E),
    surface = Color(0xFF1C1C1E),
    onBackground = White,
    onSurface = White,
)

@Composable
fun DoerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
