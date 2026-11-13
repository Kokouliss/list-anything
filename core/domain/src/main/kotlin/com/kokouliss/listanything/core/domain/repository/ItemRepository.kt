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

    /**
     * [link] must come from `normalizeLink`. [isHeader] makes a section heading instead of an item.
     * [order] places it somewhere other than the end of the list.
     */
    suspend fun addItem(
        list: ListRef,
        text: String,
        link: String? = null,
        isHeader: Boolean = false,
        order: Double? = null,
    )

    suspend fun updateText(list: ListRef, itemId: String, text: String)

    suspend fun setChecked(list: ListRef, itemId: String, checked: Boolean)

    suspend fun setLocation(list: ListRef, itemId: String, location: ItemLocation?)

    /** [link] must come from `normalizeLink`. Null removes the link. */
    suspend fun setLink(list: ListRef, itemId: String, link: String?)

    /** Null removes the note. */
    suspend fun setNote(list: ListRef, itemId: String, note: String?)

    suspend fun setQuantity(list: ListRef, itemId: String, quantity: Int)

    /** Null removes the price. */
    suspend fun setPrice(list: ListRef, itemId: String, priceCents: Long?)

    /** [dueDay] counts days since 1 January 1970. Null removes the due date. */
    suspend fun setDueDay(list: ListRef, itemId: String, dueDay: Long?)

    /** Marks the item as taken by the signed-in user, or frees it again. */
    suspend fun setClaimed(list: ListRef, itemId: String, claimed: Boolean)

    /** [order] comes from `orderAfterMove`. */
    suspend fun setOrder(list: ListRef, itemId: String, order: Double)

    /** Open items in any of the user's lists that are due on [throughDay] or earlier. Needs a connection. */
    suspend fun loadDueItems(throughDay: Long): List<ListItem>

    /** Shrinks the picture at [photoUri] and attaches it to the item, replacing any earlier photo. */
    suspend fun setPhoto(list: ListRef, itemId: String, photoUri: String)

    suspend fun removePhoto(list: ListRef, itemId: String)

    /** The full-size photo, or null when the item has none or it has not synced to this device yet. */
    suspend fun loadPhoto(list: ListRef, itemId: String): ImageData?

    /** Attaches the recording in the file at [audioPath], replacing any earlier voice note. */
    suspend fun setVoiceNote(list: ListRef, itemId: String, audioPath: String, seconds: Int)

    suspend fun removeVoiceNote(list: ListRef, itemId: String)

    /** The recording, or null when the item has none or it has not synced to this device yet. */
    suspend fun loadVoiceNote(list: ListRef, itemId: String): ByteArray?

    /** Deletes the items together with their photos. */
    suspend fun deleteItems(list: ListRef, itemIds: List<String>)
}
