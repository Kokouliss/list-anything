package com.kokouliss.listanything.core.domain.model

/** One list inside a space: groceries, places to eat, things to do. */
data class SharedList(
    val id: String,
    val spaceId: String,
    val name: String,
    val emoji: String,
    /**
     * Set on a wish list by the person the gifts are for. That person's app then hides who said
     * "I'll take this", so the gift stays a surprise.
     */
    val giftForId: String? = null,
    val giftForName: String? = null,
    /**
     * How long a deleted item stays at the bottom of the list, where anyone can bring it back.
     * 0 deletes it right away, [KEEP_DELETED_FOREVER] keeps it until someone removes it.
     */
    val keepDeletedSeconds: Long = 0,
)

const val KEEP_DELETED_FOREVER = -1L

/** Address of a list. Items are always reached through their list. */
data class ListRef(
    val spaceId: String,
    val listId: String,
)
