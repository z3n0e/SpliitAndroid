@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.spliitclient.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import app.spliitclient.data.Appearance
import app.spliitclient.data.ThemeMode

/** Whether the app should be dark, according to the user's choice. */
@Composable
fun Appearance.isDark(): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

fun dynamicColorAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun SpliitTheme(appearance: Appearance, dark: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val useDynamic = appearance.dynamicColor && dynamicColorAvailable()
    val base = if (useDynamic) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        remember(appearance.accent, dark) { accentScheme(AccentColor.from(appearance.accent), dark) }
    }
    val scheme = if (dark && appearance.amoled) remember(base) { base.toAmoled() } else base
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

/**
 * Pure black background for OLED screens (pixels off = less battery).
 * Cards keep a slightly lighter, tinted surface so they stay readable.
 */
private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = lerp(surfaceContainerLow, Color.Black, 0.6f),
    surfaceContainer = lerp(surfaceContainer, Color.Black, 0.5f),
    surfaceContainerHigh = lerp(surfaceContainerHigh, Color.Black, 0.4f),
    surfaceContainerHighest = lerp(surfaceContainerHighest, Color.Black, 0.3f),
    surfaceBright = lerp(surfaceBright, Color.Black, 0.3f),
    surfaceVariant = lerp(surfaceVariant, Color.Black, 0.3f),
)
