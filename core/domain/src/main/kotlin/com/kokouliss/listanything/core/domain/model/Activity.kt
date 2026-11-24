package com.kokouliss.listanything.core.domain.model

/** What a member did, as the history of a space tells it. [key] is the stored name. */
enum class ActivityKind(val key: String) {
    ADDED("added"),
    CHECKED("checked"),
    UNCHECKED("unchecked"),
    CHANGED("changed"),
    DELETED("deleted"),
    RESTORED("restored"),
    LIST_ADDED("listAdded"),
    LIST_DELETED("listDeleted"),
}

/**
 * One line of a space's history: [byName] did [kind] to [texts] on the list [listName]. [texts] are
 * item names, or the list's own name for the list kinds. [was] is an item's text before it was
 * renamed. [hiddenFromId] is the person a wish list is a surprise for, who does not see the line.
 */
data class Activity(
    val id: String,
    val kind: ActivityKind,
    val byId: String,
    val byName: String,
    val listId: String,
    val listName: String,
    val texts: List<String>,
    val atMillis: Long,
    val was: String? = null,
    val hiddenFromId: String? = null,
)

/** How long a space keeps its history. firebase/firestore.rules allows a day more for a wrong clock. */
const val ACTIVITY_KEEP_DAYS = 30

/** Gaps up to this long still make one line out of the same person doing the same thing. */
private const val RUN_GAP_MILLIS = 30 * 60_000L

/**
 * The history, newest first, as [userId] sees it: lines hidden from them left out, people and lists
 * under their current names ([names] and [listNames] by id; a person or list that is gone keeps the
 * stored name), and a run of the same person doing the same thing on the same list joined into one
 * line, the way a shopping trip is one trip.
 */
fun List<Activity>.forMember(userId: String, names: Map<String, String>, listNames: Map<String, String>): List<Activity> {
    val lines = mutableListOf<Activity>()
    var previous: Activity? = null
    for (entry in this) {
        if (entry.hiddenFromId == userId) continue
        val last = lines.lastOrNull()
        val joins = last != null && previous != null && entry.was == null && last.was == null &&
            entry.byId == last.byId && entry.kind == last.kind && entry.listId == last.listId &&
            previous.atMillis - entry.atMillis <= RUN_GAP_MILLIS
        if (joins) {
            // Older first, so the line reads in the order it happened.
            lines[lines.lastIndex] = last.copy(texts = (entry.texts + last.texts).distinct())
        } else {
            lines += entry.copy(byName = names[entry.byId] ?: entry.byName, listName = listNames[entry.listId] ?: entry.listName)
        }
        previous = entry
    }
    return lines
}
