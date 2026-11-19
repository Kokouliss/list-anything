package com.kokouliss.listanything.core.domain.repository

import com.kokouliss.listanything.core.domain.model.ImageData
import com.kokouliss.listanything.core.domain.model.ItemLocation
import com.kokouliss.listanything.core.domain.model.ListItem
import com.kokouliss.listanything.core.domain.model.ListRef
import com.kokouliss.listanything.core.domain.model.Repeat
import kotlinx.coroutines.flow.Flow

/**
 * The items of a list. Writes are applied on the device at once and synced when a connection is
 * available, so they also work with no signal in the shop.
 */
interface ItemRepository {
    fun observeItems(list: ListRef): Flow<List<ListItem>>

    /** Texts that members added to any list of the space before, for the suggestions under the add field. */
    fun observeSuggestions(spaceId: String): Flow<List<String>>

    /**
     * [link] must come from `normalizeLink`. [isHeader] makes a section heading instead of an item.
     * [order] places it somewhere other than the end of the list. [quantity] is how many to get.
     */
    suspend fun addItem(
        list: ListRef,
        text: String,
        link: String? = null,
        isHeader: Boolean = false,
        order: Double? = null,
        quantity: Int = 1,
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

    /**
     * Open items in any of the user's lists that are due on [throughDay] or earlier. Checked items
     * that repeat and whose day has come are unchecked first and count as due. Needs a connection.
     */
    suspend fun loadDueItems(throughDay: Long): List<ListItem>

    /** The item as stored now, or null when it is gone. Reads the copy on the device when offline. */
    suspend fun loadItem(list: ListRef, itemId: String): ListItem?

    /** Sets or, with null, removes the moment the item reminds about itself. Removing the due day removes it too. */
    suspend fun setRemindAt(list: ListRef, itemId: String, remindAtMillis: Long?)

    /** How often the item comes round again, counted from its due day. Null stops it; so does removing the due day. */
    suspend fun setRepeat(list: ListRef, itemId: String, repeat: Repeat?)

    /**
     * Unchecks an item that repeats and makes [dueDay], from `returnsOn`, its due day. A reminder
     * keeps its time of day on the new day.
     */
    suspend fun uncheckForNextRound(list: ListRef, item: ListItem, dueDay: Long)

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

    /** Moves the items to the deleted section at the bottom of the list, or brings them back from it. */
    suspend fun setDeleted(list: ListRef, itemIds: List<String>, deleted: Boolean)

    /** Deletes the items together with their photos. */
    suspend fun deleteItems(list: ListRef, itemIds: List<String>)
}
