package com.kokouliss.listanything.core.domain.repository

import com.kokouliss.listanything.core.domain.model.ListFeature
import com.kokouliss.listanything.core.domain.model.ListRef
import com.kokouliss.listanything.core.domain.model.SharedList
import kotlinx.coroutines.flow.Flow

/** The lists inside a space. Every member of the space can read and change them. */
interface ListRepository {
    fun observeLists(spaceId: String): Flow<List<SharedList>>

    /** Emits null once the list is deleted or the user loses access to its space. */
    fun observeList(ref: ListRef): Flow<SharedList?>

    suspend fun createList(spaceId: String, name: String, emoji: String, features: Set<ListFeature>)

    suspend fun updateList(ref: ListRef, name: String, emoji: String)

    /**
     * Turns the list into a wish list for the signed-in user, whose app then hides who takes
     * which item, or back into a normal list.
     */
    suspend fun setGiftForMe(ref: ListRef, enabled: Boolean)

    /** Saves the settings of [list]: features, checked items, sort, currency and how long deleted items stay. */
    suspend fun updateSettings(ref: ListRef, list: SharedList)

    /** Turns notifications about the list off, or on again, for the signed-in user. */
    suspend fun setMuted(ref: ListRef, muted: Boolean)

    /**
     * Notes on the list that the signed-in user is going shopping now. firebase/functions then
     * tells everyone else in the space at once.
     */
    suspend fun announceShopping(ref: ListRef)

    /**
     * Makes a copy of the list, with its settings and items, in another space the user is a member
     * of. Photos, voice notes, who checked what and who takes what stay behind.
     */
    suspend fun copyList(from: ListRef, toSpaceId: String)

    /** Deletes the list together with its items and their photos. */
    suspend fun deleteList(ref: ListRef)
}
