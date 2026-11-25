package io.github.onneau.sceneradio.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Cyan80 = Color(0xFF67E8F9)
val CyanGrey80 = Color(0xFFA5F3FC)
val LightCyan80 = Color(0xFFCFFAFE)

val Cyan40 = Color(0xFF0891B2)
val CyanGrey40 = Color(0xFF0E7490)
val DarkCyan40 = Color(0xFF155E75)

val DarkBackground = Color(0xFF090D16)
val DarkSurface = Color(0xFF131D2E)
val DarkCard = Color(0xFF1B283E)

private val DarkColorScheme = darkColorScheme(
    primary = Cyan80,
    secondary = CyanGrey80,
    tertiary = LightCyan80,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkCard,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Cyan40,
    secondary = CyanGrey40,
    tertiary = DarkCyan40,
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F5F9),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A)
)

@Composable
fun SceneRadioTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
