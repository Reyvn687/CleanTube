package com.cleantube

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Vurğu rəngləri: ad, qaranlıq tema rəngi, işıq tema rəngi. */
data class Accent(val name: String, val dark: Color, val light: Color)

val Accents = listOf(
    Accent("Qırmızı", Color(0xFFFF5A52), Color(0xFFE62117)),
    Accent("Mavi", Color(0xFF5AA9FF), Color(0xFF1A73E8)),
    Accent("Yaşıl", Color(0xFF4CD08C), Color(0xFF188038)),
    Accent("Bənövşəyi", Color(0xFFB388FF), Color(0xFF7E57C2)),
    Accent("Narıncı", Color(0xFFFFA040), Color(0xFFE8710A))
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF5A52),
    onPrimary = Color.White,
    background = Color(0xFF0E0E10),
    surface = Color(0xFF0E0E10),
    surfaceVariant = Color(0xFF232327),
    onSurfaceVariant = Color(0xFFA9A9B0)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFE62117),
    onPrimary = Color.White,
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFAFAFA),
    surfaceVariant = Color(0xFFEAEAEE),
    onSurfaceVariant = Color(0xFF5F5F66)
)

/** mode: 0 sistem, 1 qaranlıq, 2 işıq, 3 AMOLED (tam qara). */
@Composable
fun CleanTubeTheme(mode: Int, accent: Int, content: @Composable () -> Unit) {
    val dark = when (mode) {
        1, 3 -> true
        2 -> false
        else -> isSystemInDarkTheme()
    }
    val a = Accents[accent.coerceIn(0, Accents.size - 1)]
    var scheme = if (dark) DarkColors.copy(primary = a.dark) else LightColors.copy(primary = a.light)
    if (mode == 3) {
        scheme = scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF1B1B1D)
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = scheme.background.toArgb()
            window.navigationBarColor = scheme.background.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
