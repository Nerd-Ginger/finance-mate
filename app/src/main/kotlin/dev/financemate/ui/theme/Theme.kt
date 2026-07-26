package dev.financemate.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Green40 = Color(0xFF2E6B33)
private val Green80 = Color(0xFF9BD49F)
private val Sand40 = Color(0xFF6B5E2E)
private val Sand80 = Color(0xFFD4C79B)
private val Red40 = Color(0xFFB3261E)

private val LightColors = lightColorScheme(
    primary = Green40,
    secondary = Sand40,
    error = Red40,
)

private val DarkColors = darkColorScheme(
    primary = Green80,
    secondary = Sand80,
)

@Composable
fun FinanceMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
