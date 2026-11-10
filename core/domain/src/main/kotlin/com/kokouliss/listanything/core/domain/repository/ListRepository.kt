package com.kokouliss.listanything.core.domain.repository

import com.kokouliss.listanything.core.domain.model.ListRef
import com.kokouliss.listanything.core.domain.model.SharedList
import kotlinx.coroutines.flow.Flow

/** The lists inside a space. Every member of the space can read and change them. */
interface ListRepository {
    fun observeLists(spaceId: String): Flow<List<SharedList>>

    /** Emits null once the list is deleted or the user loses access to its space. */
    fun observeList(ref: ListRef): Flow<SharedList?>

    suspend fun createList(spaceId: String, name: String, emoji: String)

    suspend fun updateList(ref: ListRef, name: String, emoji: String)

    /**
     * Turns the list into a wish list for the signed-in user, whose app then hides who takes
     * which item, or back into a normal list.
     */
    suspend fun setGiftForMe(ref: ListRef, enabled: Boolean)

    /** Deletes the list together with its items and their photos. */
    suspend fun deleteList(ref: ListRef)
}
