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
)

/** Address of a list. Items are always reached through their list. */
data class ListRef(
    val spaceId: String,
    val listId: String,
)
