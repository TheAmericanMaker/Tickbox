// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    val ocrHintShown: Flow<Boolean> = dataStore.data.map { it[OCR_HINT_SHOWN] ?: false }

    val dictationDisclosureAcknowledged: Flow<Boolean> =
        dataStore.data.map { it[DICTATION_DISCLOSURE_ACKNOWLEDGED] ?: false }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { ThemeMode.fromName(it[THEME_MODE]) }

    val dynamicColor: Flow<Boolean> = dataStore.data.map { it[DYNAMIC_COLOR] ?: true }

    suspend fun setOcrHintShown() {
        dataStore.edit { it[OCR_HINT_SHOWN] = true }
    }

    suspend fun acknowledgeDictationDisclosure() {
        dataStore.edit { it[DICTATION_DISCLOSURE_ACKNOWLEDGED] = true }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    private companion object {
        val OCR_HINT_SHOWN = booleanPreferencesKey("ocr_hint_shown")
        val DICTATION_DISCLOSURE_ACKNOWLEDGED = booleanPreferencesKey("dictation_disclosure_acknowledged")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }
}
