package com.kokouliss.listanything.core.domain.model

/** One list inside a space: groceries, places to eat, things to do. */
data class SharedList(
    val id: String,
    val spaceId: String,
    val name: String,
    val emoji: String,
)

/** Address of a list. Items are always reached through their list. */
data class ListRef(
    val spaceId: String,
    val listId: String,
)
