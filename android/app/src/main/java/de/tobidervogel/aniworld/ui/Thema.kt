package de.tobidervogel.aniworld.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Fast schwarz mit einem warmen Orange als einzigem Akzent. Bewusst nicht das Material-Standardlila,
 * das ist genau der „KI-gemacht“-Look, den Tobi nicht will (gleiche Überlegung wie bei Live Up).
 */
object Farbe {
    val hintergrund = Color(0xFF0C0D10)
    val flaeche = Color(0xFF15161B)
    val flaeche2 = Color(0xFF1E1F26)
    val linie = Color(0xFF2A2B33)
    val text = Color(0xFFF2F2F5)
    val text2 = Color(0xFFA1A2AD)
    val text3 = Color(0xFF6E6F7A)
    val akzent = Color(0xFFFF8A3D)
    val akzentFlaeche = Color(0xFF3A2416)
    val fehler = Color(0xFFFF5C6C)
    val gut = Color(0xFF5BD69A)
}

private val farben = darkColorScheme(
    primary = Farbe.akzent,
    onPrimary = Color(0xFF1A0E05),
    primaryContainer = Farbe.akzentFlaeche,
    onPrimaryContainer = Farbe.akzent,
    secondary = Farbe.text2,
    onSecondary = Farbe.hintergrund,
    secondaryContainer = Farbe.flaeche2,
    onSecondaryContainer = Farbe.text,
    background = Farbe.hintergrund,
    onBackground = Farbe.text,
    surface = Farbe.hintergrund,
    onSurface = Farbe.text,
    surfaceVariant = Farbe.flaeche2,
    onSurfaceVariant = Farbe.text2,
    surfaceContainerLowest = Farbe.hintergrund,
    surfaceContainerLow = Farbe.flaeche,
    surfaceContainer = Farbe.flaeche,
    surfaceContainerHigh = Farbe.flaeche2,
    surfaceContainerHighest = Farbe.linie,
    outline = Farbe.linie,
    outlineVariant = Farbe.linie,
    error = Farbe.fehler,
    onError = Color.Black,
)

private val schrift = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.3).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 19.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    )
}

@Composable
fun Thema(inhalt: @Composable () -> Unit) {
    MaterialTheme(colorScheme = farben, typography = schrift, content = inhalt)
}
