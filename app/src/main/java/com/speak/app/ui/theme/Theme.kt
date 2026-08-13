package com.speak.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.speak.app.data.prefs.SettingsStore

// A warm, low-contrast palette rather than Material's default blue. The app is
// used while tired and while walking, so the aim is calm and legible instead of
// bright. Dynamic colour is deliberately not used: the palette carries the
// product's character, and handing it to the wallpaper would lose that.

private val Teal = Color(0xFF2F6F62)
private val TealLight = Color(0xFF7FCBBA)
private val TealSoft = Color(0xFFE8F1EE)
private val Clay = Color(0xFFB4551F)
private val ClaySoft = Color(0xFFFBEFE6)
private val Ink = Color(0xFF1B1A18)
private val InkSoft = Color(0xFF5C5850)
private val Paper = Color(0xFFFBF9F6)
private val Line = Color(0xFFE7E2D9)

private val NightBase = Color(0xFF16150F)
private val NightSurface = Color(0xFF201E1A)
private val NightLine = Color(0xFF37342E)
private val NightInk = Color(0xFFF2EFE8)
private val NightInkSoft = Color(0xFFB6B0A4)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = TealSoft,
    onPrimaryContainer = Color(0xFF12352D),
    secondary = InkSoft,
    onSecondary = Color.White,
    secondaryContainer = Line,
    onSecondaryContainer = Ink,
    tertiary = Clay,
    onTertiary = Color.White,
    tertiaryContainer = ClaySoft,
    onTertiaryContainer = Color(0xFF5C2A0C),
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF3F0EA),
    onSurfaceVariant = InkSoft,
    outline = Line,
    outlineVariant = Color(0xFFEFEBE3),
    error = Color(0xFF9B2C1F),
    onError = Color.White,
    errorContainer = Color(0xFFFBE6E2),
    onErrorContainer = Color(0xFF5A1710)
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF265348),
    onPrimaryContainer = Color(0xFFCDEDE4),
    secondary = NightInkSoft,
    onSecondary = Color(0xFF1E1C18),
    secondaryContainer = Color(0xFF2C2A25),
    onSecondaryContainer = NightInk,
    tertiary = Color(0xFFF0A277),
    onTertiary = Color(0xFF4A1D05),
    tertiaryContainer = Color(0xFF5C2A0C),
    onTertiaryContainer = Color(0xFFFFDCC8),
    background = NightBase,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = Color(0xFF2A2823),
    onSurfaceVariant = NightInkSoft,
    outline = NightLine,
    outlineVariant = Color(0xFF2A2823),
    error = Color(0xFFF2B8AF),
    onError = Color(0xFF5A1710),
    errorContainer = Color(0xFF7A2419),
    onErrorContainer = Color(0xFFFFDAD4)
)

/**
 * Type scale tuned up a step from Material's defaults. This is read at arm's
 * length, tired, and sometimes moving.
 */
private val SpeakTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)
)

/** Minimum touch target used throughout: comfortably usable while walking. */
val MinTouchTarget = 56.dp

@Composable
fun SpeakTheme(
    darkThemeSetting: SettingsStore.DarkThemeSetting = SettingsStore.DarkThemeSetting.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (darkThemeSetting) {
        SettingsStore.DarkThemeSetting.SYSTEM -> isSystemInDarkTheme()
        SettingsStore.DarkThemeSetting.LIGHT -> false
        SettingsStore.DarkThemeSetting.DARK -> true
    }
    val colors = if (dark) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        val context = LocalContext.current
        SideEffect {
            (context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = SpeakTypography,
        content = content
    )
}
