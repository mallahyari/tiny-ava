package com.oncillaai.tinyava.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.oncillaai.tinyava.data.AppThemeMode

data class TinyAvaColors(
    val bg: Color,
    val card: Color,
    val soft: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val accentInk: Color,
    val okBg: Color,
    val okInk: Color,
    val okLine: Color,
)

private val LightPalette = TinyAvaColors(
    bg = Color(0xFFF8F5F0),
    card = Color(0xFFFFFFFF),
    soft = Color(0xFFEFE9E0),
    ink = Color(0xFF1B1714),
    muted = Color(0xFF5B534B),
    line = Color(0xFFDCD3C7),
    accent = Color(0xFFB44C22),
    accentInk = Color(0xFFFFFFFF),
    okBg = Color(0xFFE3F4EA),
    okInk = Color(0xFF14532D),
    okLine = Color(0xFF86EFAC),
)

private val DarkPalette = TinyAvaColors(
    bg = Color(0xFF12100E),
    card = Color(0xFF1D1A16),
    soft = Color(0xFF28241E),
    ink = Color(0xFFF5EFE6),
    muted = Color(0xFFB8AEA2),
    line = Color(0xFF3A342C),
    accent = Color(0xFFEB7A47),
    accentInk = Color(0xFF190D06),
    okBg = Color(0xFF112A1C),
    okInk = Color(0xFF86EFAC),
    okLine = Color(0xFF22543D),
)

private val HighContrastPalette = TinyAvaColors(
    bg = Color(0xFF000000),
    card = Color(0xFF0A0A0A),
    soft = Color(0xFF1A1A1A),
    ink = Color(0xFFFFFFFF),
    muted = Color(0xFFE5E5E5),
    line = Color(0xFFFFD60A),
    accent = Color(0xFFFFD60A),
    accentInk = Color(0xFF000000),
    okBg = Color(0xFF002B14),
    okInk = Color(0xFF72FF9E),
    okLine = Color(0xFF72FF9E),
)

val LocalAvaColors = staticCompositionLocalOf { LightPalette }

@Composable
fun TinyAvaTheme(
    mode: AppThemeMode,
    content: @Composable () -> Unit,
) {
    val palette = when (mode) {
        AppThemeMode.LIGHT -> LightPalette
        AppThemeMode.DARK -> DarkPalette
        AppThemeMode.HIGH_CONTRAST -> HighContrastPalette
    }
    val m3Scheme = if (mode == AppThemeMode.LIGHT) {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.accentInk,
            background = palette.bg,
            onBackground = palette.ink,
            surface = palette.card,
            onSurface = palette.ink,
            surfaceVariant = palette.soft,
            onSurfaceVariant = palette.muted,
            outline = palette.line,
        )
    } else {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.accentInk,
            background = palette.bg,
            onBackground = palette.ink,
            surface = palette.card,
            onSurface = palette.ink,
            surfaceVariant = palette.soft,
            onSurfaceVariant = palette.muted,
            outline = palette.line,
        )
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalAvaColors provides palette,
    ) {
        MaterialTheme(colorScheme = m3Scheme, content = content)
    }
}
