package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

enum class AppThemeOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val previewColor: Long,
    val isFreeForever: Boolean
) {
    FOREST_TEAL("FOREST_TEAL", "Forest Teal", "Default calm palette", 0xFF0F766E, true),
    OCEAN_COBALT("OCEAN_COBALT", "Ocean Cobalt", "Crisp deep-blue focus", 0xFF1D4ED8, false),
    SUNSET_TERRACOTTA("SUNSET_TERRACOTTA", "Sunset Terracotta", "Warm energizing clay", 0xFFC2410C, false),
    MIDNIGHT_LAVENDER("MIDNIGHT_LAVENDER", "Midnight Lavender", "Soft twilight violet", 0xFF6D28D9, false);

    companion object {
        fun fromId(id: String?): AppThemeOption =
            entries.firstOrNull { it.id == id } ?: FOREST_TEAL
    }
}

private val ForestLightScheme = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = TealOnPrimaryLight,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = OnTealContainerLight,
    secondary = EmeraldSecondaryLight,
    onSecondary = EmeraldOnSecondaryLight,
    secondaryContainer = EmeraldContainerLight,
    onSecondaryContainer = OnEmeraldContainerLight,
    tertiary = AmberTertiaryLight,
    onTertiary = AmberOnTertiaryLight,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = OnAmberContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnBackgroundLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight
)

private val ForestDarkScheme = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = TealOnPrimaryDark,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = OnTealContainerDark,
    secondary = EmeraldSecondaryDark,
    onSecondary = EmeraldOnSecondaryDark,
    secondaryContainer = EmeraldContainerDark,
    onSecondaryContainer = OnEmeraldContainerDark,
    tertiary = AmberTertiaryDark,
    onTertiary = AmberOnTertiaryDark,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = OnAmberContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnBackgroundDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark
)

private fun customScheme(theme: AppThemeOption, dark: Boolean): ColorScheme = when (theme) {
    AppThemeOption.FOREST_TEAL -> if (dark) ForestDarkScheme else ForestLightScheme
    AppThemeOption.OCEAN_COBALT -> if (dark) {
        ForestDarkScheme.copy(
            primary = OceanPrimaryDark,
            onPrimary = OceanOnPrimaryDark,
            primaryContainer = OceanContainerDark,
            onPrimaryContainer = OceanOnContainerDark,
            background = OceanBackgroundDark,
            surface = OceanSurfaceDark
        )
    } else {
        ForestLightScheme.copy(
            primary = OceanPrimaryLight,
            onPrimary = Color.White,
            primaryContainer = OceanContainerLight,
            onPrimaryContainer = OceanOnContainerLight,
            background = OceanBackgroundLight,
            surfaceVariant = OceanSurfaceVariantLight
        )
    }
    AppThemeOption.SUNSET_TERRACOTTA -> if (dark) {
        ForestDarkScheme.copy(
            primary = SunsetPrimaryDark,
            onPrimary = SunsetOnPrimaryDark,
            primaryContainer = SunsetContainerDark,
            onPrimaryContainer = SunsetOnContainerDark,
            background = SunsetBackgroundDark,
            surface = SunsetSurfaceDark
        )
    } else {
        ForestLightScheme.copy(
            primary = SunsetPrimaryLight,
            onPrimary = Color.White,
            primaryContainer = SunsetContainerLight,
            onPrimaryContainer = SunsetOnContainerLight,
            background = SunsetBackgroundLight,
            surfaceVariant = SunsetSurfaceVariantLight
        )
    }
    AppThemeOption.MIDNIGHT_LAVENDER -> if (dark) {
        ForestDarkScheme.copy(
            primary = LavenderPrimaryDark,
            onPrimary = LavenderOnPrimaryDark,
            primaryContainer = LavenderContainerDark,
            onPrimaryContainer = LavenderOnContainerDark,
            background = LavenderBackgroundDark,
            surface = LavenderSurfaceDark
        )
    } else {
        ForestLightScheme.copy(
            primary = LavenderPrimaryLight,
            onPrimary = Color.White,
            primaryContainer = LavenderContainerLight,
            onPrimaryContainer = LavenderOnContainerLight,
            background = LavenderBackgroundLight,
            surfaceVariant = LavenderSurfaceVariantLight
        )
    }
}

val HabitWorksShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun HabitWorksTheme(
    selectedTheme: AppThemeOption = AppThemeOption.FOREST_TEAL,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        selectedTheme != AppThemeOption.FOREST_TEAL -> customScheme(selectedTheme, darkTheme)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> customScheme(AppThemeOption.FOREST_TEAL, darkTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HabitWorksTypography,
        shapes = HabitWorksShapes,
        content = content
    )
}
