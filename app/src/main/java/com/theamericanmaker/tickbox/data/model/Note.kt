// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.data.model

enum class NoteType {
    TEXT,
    CHECKLIST,
    ;

    companion object {
        /** Tolerant of unknown values, which can reach us from an imported backup. */
        fun fromName(name: String?): NoteType =
            entries.firstOrNull { it.name == name } ?: TEXT
    }
}

/** Which glyph a checklist uses for its items. */
enum class ChecklistIconStyle {
    CHECKBOX,
    CIRCLE,
    STAR,
    HEART,
    SQUARE,
    ;

    companion object {
        fun fromName(name: String?): ChecklistIconStyle =
            entries.firstOrNull { it.name == name } ?: CHECKBOX
    }
}

data class Note(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val type: NoteType = NoteType.TEXT,
    val category: String? = null,
    val colorLabel: String? = null,
    val isPinned: Boolean = false,
    val iconStyle: ChecklistIconStyle = ChecklistIconStyle.CHECKBOX,
    val checklistItems: List<ChecklistItem> = emptyList(),
    val images: List<NoteImage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class ChecklistItem(
    val id: Long = 0,
    val text: String = "",
    val isChecked: Boolean = false,
    val position: Int = 0,
    /** Currently 0 or 1. Deeper nesting is post-1.0. */
    val indentLevel: Int = 0,
)

data class NoteImage(
    val id: Long = 0,
    /** Filename only, relative to the image store directory. */
    val filePath: String = "",
    val position: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
)
