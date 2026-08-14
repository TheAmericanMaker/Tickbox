// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ui.edit

import androidx.compose.ui.graphics.Color

object NoteCategorizer {

    private val categories = mapOf(
        "Shopping" to listOf("grocery", "groceries", "shopping", "buy", "store", "purchase", "shop"),
        "Work" to listOf("meeting", "work", "project", "deadline", "task", "office", "client", "report", "agenda"),
        "Personal" to listOf("todo", "to-do", "reminder", "personal", "appointment", "schedule"),
        "Recipes" to listOf("recipe", "cook", "bake", "ingredients", "dinner", "lunch", "breakfast", "meal"),
        "Travel" to listOf("travel", "trip", "vacation", "flight", "hotel", "packing", "pack", "camping"),
        "Health" to listOf("workout", "exercise", "gym", "health", "doctor", "medication", "diet", "fitness"),
        "Home" to listOf("clean", "cleaning", "chores", "repair", "home", "house", "apartment", "move", "moving"),
        "Ideas" to listOf("idea", "brainstorm", "concept", "plan", "design", "draft", "notes")
    )

    private val categoryColors = mapOf(
        "Shopping" to Color(0xFF4CAF50),    // Green
        "Work" to Color(0xFF2196F3),         // Blue
        "Personal" to Color(0xFF9C27B0),     // Purple
        "Recipes" to Color(0xFFFF9800),      // Orange
        "Travel" to Color(0xFF00BCD4),       // Cyan
        "Health" to Color(0xFFF44336),       // Red
        "Home" to Color(0xFF795548),         // Brown
        "Ideas" to Color(0xFFFFEB3B)         // Yellow
    )

    fun categorize(title: String): String? {
        if (title.isBlank()) return null
        val lowerTitle = title.lowercase()
        for ((category, keywords) in categories) {
            if (keywords.any { lowerTitle.contains(it) }) {
                return category
            }
        }
        return null
    }

    fun getCategoryColor(category: String): Color {
        return categoryColors[category] ?: Color(0xFF9E9E9E)
    }

    fun getAllCategories(): List<String> = categories.keys.toList()

    // User-selectable color labels (independent of auto-categorization)
    val noteColors = linkedMapOf(
        "Red" to Color(0xFFF44336),
        "Orange" to Color(0xFFFF9800),
        "Yellow" to Color(0xFFFFEB3B),
        "Green" to Color(0xFF4CAF50),
        "Teal" to Color(0xFF009688),
        "Blue" to Color(0xFF2196F3),
        "Purple" to Color(0xFF9C27B0),
        "Pink" to Color(0xFFE91E63),
        "Brown" to Color(0xFF795548),
        "Gray" to Color(0xFF9E9E9E)
    )

    fun getNoteColor(label: String): Color? = noteColors[label]
}
