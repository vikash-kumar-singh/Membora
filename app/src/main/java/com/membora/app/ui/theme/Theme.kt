package com.membora.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = DarkBlue,
    onPrimary = Ink,
    primaryContainer = Color(0xFF0B3157),
    onPrimaryContainer = Color(0xFFD8EDFF),
    secondary = Color(0xFFA9DB64),
    onSecondary = Ink,
    secondaryContainer = Color(0xFF23440E),
    onSecondaryContainer = Color(0xFFE1F9C9),
    tertiary = BrandCyan,
    background = DarkCanvas,
    surface = DarkPaper,
    onBackground = Color(0xFFF4FAF7),
    onSurface = Color(0xFFF4FAF7),
    surfaceVariant = Color(0xFF122C49),
    onSurfaceVariant = Color(0xFFB6C6D8),
    outline = Color(0xFF36506B),
    error = Color(0xFFFFB4A8)
)

private val LightColorScheme = lightColorScheme(
    primary = BrandBlueDark,
    onPrimary = Paper,
    primaryContainer = BlueWash,
    onPrimaryContainer = Color(0xFF003A72),
    secondary = BrandGreen,
    onSecondary = Paper,
    secondaryContainer = GreenWash,
    onSecondaryContainer = Color(0xFF244D0A),
    tertiary = BrandCyan,
    onTertiary = Ink,
    tertiaryContainer = Color(0xFFE1F8FC),
    onTertiaryContainer = Color(0xFF004B59),
    error = Coral,
    errorContainer = CoralSoft,
    background = Canvas,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEEF3F8),
    onSurfaceVariant = InkSoft,
    outline = Line
)

private val MemboraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun MemboraTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = MemboraShapes,
        content = content
    )
}
