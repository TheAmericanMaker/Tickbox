// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Smart Toolkit carried a five-preset colour enum to feed a theme picker in its
 * settings screen. Tickbox keeps one seed instead: Material You where the platform
 * supports it, this palette otherwise.
 */
private val SeedPrimaryLight = Color(0xFF3D6B4F)
private val SeedSecondaryLight = Color(0xFF52634F)
private val SeedTertiaryLight = Color(0xFF39656B)

private val SeedPrimaryDark = Color(0xFFA3D0AF)
private val SeedSecondaryDark = Color(0xFFB9CCB4)
private val SeedTertiaryDark = Color(0xFFA1CED5)

val TickboxTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
)

@Composable
fun TickboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> darkColorScheme(
            primary = SeedPrimaryDark,
            secondary = SeedSecondaryDark,
            tertiary = SeedTertiaryDark,
        )

        else -> lightColorScheme(
            primary = SeedPrimaryLight,
            secondary = SeedSecondaryLight,
            tertiary = SeedTertiaryLight,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TickboxTypography,
        content = content,
    )
}
