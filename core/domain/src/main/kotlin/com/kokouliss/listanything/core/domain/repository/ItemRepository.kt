package com.kokouliss.listanything.core.domain.repository

import com.kokouliss.listanything.core.domain.model.ImageData
import com.kokouliss.listanything.core.domain.model.ItemLocation
import com.kokouliss.listanything.core.domain.model.ListItem
import com.kokouliss.listanything.core.domain.model.ListRef
import kotlinx.coroutines.flow.Flow

/**
 * The items of a list. Writes are applied on the device at once and synced when a connection is
 * available, so they also work with no signal in the shop.
 */
interface ItemRepository {
    fun observeItems(list: ListRef): Flow<List<ListItem>>

    suspend fun addItem(list: ListRef, text: String)

    suspend fun updateText(list: ListRef, itemId: String, text: String)

    suspend fun setChecked(list: ListRef, itemId: String, checked: Boolean)

    suspend fun setLocation(list: ListRef, itemId: String, location: ItemLocation?)

    /** [link] must come from `normalizeLink`. Null removes the link. */
    suspend fun setLink(list: ListRef, itemId: String, link: String?)

    /** Shrinks the picture at [photoUri] and attaches it to the item, replacing any earlier photo. */
    suspend fun setPhoto(list: ListRef, itemId: String, photoUri: String)

    suspend fun removePhoto(list: ListRef, itemId: String)

    /** The full-size photo, or null when the item has none or it has not synced to this device yet. */
    suspend fun loadPhoto(list: ListRef, itemId: String): ImageData?

    /** Deletes the items together with their photos. */
    suspend fun deleteItems(list: ListRef, itemIds: List<String>)
}
