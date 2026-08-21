// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.theamericanmaker.tickbox.data.ThemeMode
import com.theamericanmaker.tickbox.ui.TickboxNavHost
import com.theamericanmaker.tickbox.ui.theme.TickboxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val preferences = (application as TickboxApp).container.preferences

        setContent {
            val themeMode by preferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val dynamicColor by preferences.dynamicColor.collectAsState(initial = true)

            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // The system bars have to be told which way round the app is, and it cannot be
            // done in onCreate: enableEdgeToEdge() runs before the stored theme is read, so it
            // decides from the *system* theme. Choosing Light on a dark-themed device then left
            // a white clock on a near-white bar, and Dark on a light device the reverse (#40).
            //
            // Driven from the same `darkTheme` the theme uses, so it also follows the Appearance
            // dialog while that is still open — the setting applies live, deliberately.
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
            }

            TickboxTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                TickboxNavHost()
            }
        }
    }
}
