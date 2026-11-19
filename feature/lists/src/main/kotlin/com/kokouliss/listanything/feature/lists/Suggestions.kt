package com.kokouliss.listanything.feature.lists

import com.kokouliss.listanything.core.domain.model.ListItem
import com.kokouliss.listanything.core.domain.model.folded

private const val MAX_SUGGESTIONS = 6

/**
 * Texts added to the space before that contain [draft], the ones that start with it first, at most
 * six. Nothing for a blank draft. The draft itself and texts still open on the list are left out.
 * Accents and capitals do not count: "γαλα" finds "Γάλα".
 */
internal fun suggestionsFor(draft: String, history: List<String>, items: List<ListItem>): List<String> {
    val typed = draft.trim().folded()
    if (typed.isEmpty()) return emptyList()
    val open = items.filterNot { it.isChecked }.mapTo(HashSet()) { it.text.folded() }
    return history
        .map { it to it.folded() }
        .filter { (_, plain) -> typed in plain && plain != typed && plain !in open }
        .sortedWith(compareBy<Pair<String, String>> { !it.second.startsWith(typed) }.thenBy { it.second })
        .map { it.first }
        .take(MAX_SUGGESTIONS)
}
