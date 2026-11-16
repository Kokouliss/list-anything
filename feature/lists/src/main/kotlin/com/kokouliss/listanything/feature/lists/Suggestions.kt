package com.kokouliss.listanything.feature.lists

import com.kokouliss.listanything.core.domain.model.ListItem

private const val MAX_SUGGESTIONS = 6

/**
 * Texts added to the space before that contain [draft], the ones that start with it first, at most
 * six. Nothing for a blank draft. The draft itself and texts still open on the list are left out.
 */
internal fun suggestionsFor(draft: String, history: List<String>, items: List<ListItem>): List<String> {
    val typed = draft.trim()
    if (typed.isEmpty()) return emptyList()
    val open = items.filterNot { it.isChecked }.mapTo(HashSet()) { it.text.lowercase() }
    return history
        .filter { it.contains(typed, ignoreCase = true) && !it.equals(typed, ignoreCase = true) && it.lowercase() !in open }
        .sortedWith(compareBy<String> { !it.startsWith(typed, ignoreCase = true) }.thenBy { it.lowercase() })
        .take(MAX_SUGGESTIONS)
}
