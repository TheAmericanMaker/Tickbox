// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ui.edit

object ChecklistSuggestionProvider {

    private val suggestions = mapOf(
        listOf("grocery", "groceries", "food", "supermarket") to listOf(
            "Milk", "Eggs", "Bread", "Butter", "Chicken", "Rice", "Pasta",
            "Tomatoes", "Onions", "Potatoes", "Cheese", "Yogurt", "Apples",
            "Bananas", "Cereal", "Coffee", "Sugar", "Salt", "Olive oil"
        ),
        listOf("packing", "pack", "travel", "trip", "vacation", "luggage") to listOf(
            "Passport / ID", "Phone charger", "Toothbrush", "Toothpaste",
            "Deodorant", "Shampoo", "Medications", "Underwear", "Socks",
            "T-shirts", "Pants", "Jacket", "Sunglasses", "Sunscreen",
            "Headphones", "Book", "Water bottle", "Snacks"
        ),
        listOf("todo", "to-do", "task", "tasks", "work") to listOf(
            "Review emails", "Update documents", "Schedule meeting",
            "Follow up", "Research", "Prepare report"
        ),
        listOf("shopping", "buy", "store") to listOf(
            "Item 1", "Item 2", "Item 3"
        ),
        listOf("clean", "cleaning", "chores", "housework") to listOf(
            "Vacuum", "Mop floors", "Clean bathroom", "Kitchen counters",
            "Laundry", "Take out trash", "Dust shelves", "Clean windows"
        ),
        listOf("workout", "exercise", "gym", "fitness") to listOf(
            "Warm up (5 min)", "Cardio (20 min)", "Push-ups", "Squats",
            "Planks", "Lunges", "Cool down", "Stretch"
        ),
        listOf("meeting", "agenda", "standup") to listOf(
            "Review previous action items", "Status updates",
            "New business", "Action items", "Next steps"
        ),
        listOf("party", "birthday", "event", "celebration") to listOf(
            "Send invitations", "Order cake", "Decorations", "Music playlist",
            "Drinks", "Plates & napkins", "Games / activities", "Gift"
        ),
        listOf("camping", "camp", "hike", "hiking", "outdoor") to listOf(
            "Tent", "Sleeping bag", "Flashlight", "First aid kit",
            "Water", "Food / snacks", "Matches / lighter", "Bug spray",
            "Sunscreen", "Map / compass", "Pocket knife"
        ),
        listOf("move", "moving", "apartment") to listOf(
            "Boxes", "Tape", "Labels", "Utilities transfer",
            "Change address", "Hire movers", "Clean old place",
            "Keys for new place", "Internet setup"
        )
    )

    fun getSuggestions(title: String): List<String> {
        if (title.isBlank()) return emptyList()
        val lowerTitle = title.lowercase()
        for ((keywords, items) in suggestions) {
            if (keywords.any { lowerTitle.contains(it) }) {
                return items
            }
        }
        return emptyList()
    }
}
