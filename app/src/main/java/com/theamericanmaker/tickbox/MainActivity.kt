// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

            TickboxTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                TickboxNavHost()
            }
        }
    }
}
