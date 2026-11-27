package com.kokouliss.listanything.core.data.firebase

import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import com.kokouliss.listanything.core.data.di.IoDispatcher
import com.kokouliss.listanything.core.domain.model.ActivityKind
import com.kokouliss.listanything.core.domain.model.ImageData
import com.kokouliss.listanything.core.domain.model.ItemLocation
import com.kokouliss.listanything.core.domain.model.ListItem
import com.kokouliss.listanything.core.domain.model.ListRef
import com.kokouliss.listanything.core.domain.model.Repeat
import com.kokouliss.listanything.core.domain.model.moveReminder
import com.kokouliss.listanything.core.domain.model.returnsOn
import com.kokouliss.listanything.core.domain.repository.ItemRepository
import java.io.File
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

internal class FirestoreItemRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val photoCompressor: PhotoCompressor,
    private val activity: FirestoreActivityRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ItemRepository {

    override fun observeItems(list: ListRef): Flow<List<ListItem>> =
        // With metadata changes, so an item says when its change has reached the cloud.
        db.items(list).orderBy(Fields.CREATED_AT).snapshots(MetadataChanges.INCLUDE)
            .map { snapshot -> snapshot.documents.mapNotNull { it.toListItem() } }
            .distinctUntilChanged()

    override suspend fun addItem(list: ListRef, text: String, link: String?, isHeader: Boolean, order: Double?, quantity: Int) {
        val user = auth.requireUser()
        db.items(list).document()
            .set(
                buildMap {
                    put(Fields.TEXT, text)
                    put(Fields.CHECKED, false)
                    put(Fields.CREATED_BY_NAME, user.memberName)
                    put(Fields.CREATED_BY_ID, user.uid)
                    put(Fields.CREATED_AT, FieldValue.serverTimestamp())
                    link?.let { put(Fields.LINK, it) }
                    if (isHeader) put(Fields.HEADER, true)
                    order?.let { put(Fields.ORDER, it) }
                    if (quantity > 1) put(Fields.QUANTITY, quantity)
                },
            )
            .logFailure("add item")
        if (!isHeader) activity.note(list, ActivityKind.ADDED, listOf(text))
        // A separate write, so a rejected suggestion cannot take the item down with it. Field names
        // shaped like __name__ are reserved by Firestore.
        val key = text.lowercase()
        if (!isHeader && !key.matches(RESERVED_FIELD)) {
            db.suggestions(list.spaceId)
                .set(mapOf(Fields.TEXTS to mapOf(key to text)), SetOptions.merge())
                .logFailure("remember item")
        }
    }

    override fun observeSuggestions(spaceId: String): Flow<List<String>> =
        db.suggestions(spaceId).snapshots().map { snapshot ->
            (snapshot.get(Fields.TEXTS) as? Map<*, *>)?.values?.filterIsInstance<String>().orEmpty()
        }

    override suspend fun updateText(list: ListRef, itemId: String, text: String) {
        val before = named(list, itemId).singleOrNull()
        item(list, itemId).update(Fields.TEXT, text).logFailure("edit item")
        if (before != null && before.text != text) activity.note(list, ActivityKind.CHANGED, listOf(text), was = before.text)
    }

    override suspend fun setChecked(list: ListRef, itemId: String, checked: Boolean) {
        val user = auth.requireUser()
        val turned = named(list, itemId).filter { it.isChecked != checked }
        item(list, itemId)
            .update(
                Fields.CHECKED, checked,
                Fields.CHECKED_BY_NAME, if (checked) user.memberName else FieldValue.delete(),
                Fields.CHECKED_BY_ID, if (checked) user.uid else FieldValue.delete(),
                Fields.CHECKED_AT, if (checked) FieldValue.serverTimestamp() else FieldValue.delete(),
            )
            .logFailure("check item")
        activity.note(list, if (checked) ActivityKind.CHECKED else ActivityKind.UNCHECKED, turned.map { it.text })
    }

    override suspend fun setLocation(list: ListRef, itemId: String, location: ItemLocation?) {
        item(list, itemId).update(Fields.LOCATION, location?.toMap() ?: FieldValue.delete()).logFailure("set location")
        changed(list, itemId)
    }

    override suspend fun setLink(list: ListRef, itemId: String, link: String?) =
        changeField(list, itemId, Fields.LINK, link)

    override suspend fun setNote(list: ListRef, itemId: String, note: String?) =
        changeField(list, itemId, Fields.NOTE, note)

    // One is the default and is not stored.
    override suspend fun setQuantity(list: ListRef, itemId: String, quantity: Int) =
        changeField(list, itemId, Fields.QUANTITY, quantity.takeIf { it > 1 })

    override suspend fun setPrice(list: ListRef, itemId: String, priceCents: Long?) =
        changeField(list, itemId, Fields.PRICE_CENTS, priceCents)

    override suspend fun setDueDay(list: ListRef, itemId: String, dueDay: Long?) {
        if (dueDay == null) {
            // No day, no moment on it and no rhythm from it.
            item(list, itemId)
                .update(
                    Fields.DUE_DAY, FieldValue.delete(),
                    Fields.REMIND_AT, FieldValue.delete(),
                    Fields.REMIND_BY_ID, FieldValue.delete(),
                    Fields.REPEAT, FieldValue.delete(),
                )
                .logFailure("remove due day")
            changed(list, itemId)
        } else {
            changeField(list, itemId, Fields.DUE_DAY, dueDay)
        }
    }

    override suspend fun setRemindAt(list: ListRef, itemId: String, remindAtMillis: Long?) {
        item(list, itemId)
            .update(
                Fields.REMIND_AT, remindAtMillis?.let { Timestamp(Date(it)) } ?: FieldValue.delete(),
                // This phone rings by itself; firebase/functions tells the others.
                Fields.REMIND_BY_ID, if (remindAtMillis == null) FieldValue.delete() else auth.requireUser().uid,
            )
            .logFailure("set reminder")
        changed(list, itemId)
    }

    override suspend fun setRepeat(list: ListRef, itemId: String, repeat: Repeat?) =
        changeField(list, itemId, Fields.REPEAT, repeat?.key)

    override suspend fun loadItem(list: ListRef, itemId: String): ListItem? = item(list, itemId).get().await().toListItem()

    override suspend fun setWatched(list: ListRef, itemId: String, watched: Boolean) {
        val uid = auth.requireUser().uid
        item(list, itemId)
            .update(Fields.WATCHED_BY, if (watched) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid))
            .logFailure("watch item")
    }

    override suspend fun setOrder(list: ListRef, itemId: String, order: Double) =
        setField(list, itemId, Fields.ORDER, order)

    override suspend fun setClaimed(list: ListRef, itemId: String, claimed: Boolean) {
        val user = auth.requireUser()
        item(list, itemId)
            .update(
                Fields.CLAIMED_BY_ID, if (claimed) user.uid else FieldValue.delete(),
                Fields.CLAIMED_BY_NAME, if (claimed) user.memberName else FieldValue.delete(),
            )
            .logFailure("take item")
    }

    // shortcut: one query per list, because the security rules cannot check membership for a query
    // across all lists. Fine for a handful of lists; store the due items per user if that grows.
    override suspend fun loadDueItems(throughDay: Long): List<ListItem> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        val spaces = db.collection(Collections.SPACES).whereArrayContains(Fields.MEMBER_IDS, userId).get().await()
        return spaces.documents.flatMap { space ->
            space.reference.collection(Collections.LISTS).get().await().documents.flatMap { list ->
                // A quiet list reminds nobody who muted it.
                if ((list.get(Fields.MUTED_BY) as? List<*>)?.contains(userId) == true) return@flatMap emptyList()
                list.reference.collection(Collections.ITEMS)
                    .whereLessThanOrEqualTo(Fields.DUE_DAY, throughDay)
                    .get().await()
                    .documents.mapNotNull { document ->
                        val item = document.toListItem() ?: return@mapNotNull null
                        // A checked item that repeats and whose day has come is due again, from now on.
                        val back = item.returnsOn()?.takeIf { it <= throughDay } ?: return@mapNotNull item
                        val ref = ListRef(space.id, list.id)
                        uncheckForNextRound(ref, item, back)
                        item.copy(isChecked = false, dueDay = back)
                    }
                    .filter { !it.isChecked && !it.isDeleted }
            }
        }
    }

    override suspend fun uncheckForNextRound(list: ListRef, item: ListItem, dueDay: Long) {
        val remindAt = item.remindAtMillis?.let { moveReminder(it, dueDay) }
        item(list, item.id)
            .update(
                Fields.CHECKED, false,
                Fields.CHECKED_BY_NAME, FieldValue.delete(),
                Fields.CHECKED_BY_ID, FieldValue.delete(),
                Fields.CHECKED_AT, FieldValue.delete(),
                Fields.DUE_DAY, dueDay,
                // A reminder keeps its time of day on the new day.
                Fields.REMIND_AT, remindAt?.let { Timestamp(Date(it)) } ?: FieldValue.delete(),
                // No phone holds an alarm for the new day, so firebase/functions rings for everyone.
                Fields.REMIND_BY_ID, FieldValue.delete(),
            )
            .logFailure("bring item back")
    }

    override suspend fun setPhoto(list: ListRef, itemId: String, photoUri: String) {
        val photo = withContext(ioDispatcher) { photoCompressor.compress(Uri.parse(photoUri)) }
        // shortcut: photos live in Firestore so the app needs no billing account. A document holds
        // at most 1 MiB, hence the size cap in PhotoCompressor. Move the full photo to Firebase
        // Storage when full-resolution pictures or more than about 1 GiB of photos are needed.
        val item = item(list, itemId)
        db.batch()
            .set(item.fullPhoto(), mapOf(Fields.DATA to Blob.fromBytes(photo.full)))
            .update(item, Fields.THUMBNAIL, Blob.fromBytes(photo.thumbnail))
            .commit()
            .logFailure("attach photo")
        changed(list, itemId)
    }

    override suspend fun removePhoto(list: ListRef, itemId: String) {
        val item = item(list, itemId)
        db.batch()
            .delete(item.fullPhoto())
            .update(item, Fields.THUMBNAIL, FieldValue.delete())
            .commit()
            .logFailure("remove photo")
        changed(list, itemId)
    }

    override suspend fun loadPhoto(list: ListRef, itemId: String): ImageData? =
        try {
            item(list, itemId).fullPhoto().get().await().getBlob(Fields.DATA)?.let { ImageData(it.toBytes()) }
        } catch (e: FirebaseFirestoreException) {
            // Offline and not in the local cache yet.
            null
        }

    override suspend fun setVoiceNote(list: ListRef, itemId: String, audioPath: String, seconds: Int) {
        val audio = withContext(ioDispatcher) { File(audioPath).readBytes() }
        // Same home as photos: a Firestore document, capped by the rules at 800 KB. The recorder's
        // bit rate and MAX_VOICE_SECONDS keep a note under 400 KB.
        require(audio.size in 1..MAX_VOICE_BYTES) { "Voice note of ${audio.size} bytes" }
        val item = item(list, itemId)
        db.batch()
            .set(item.voiceNote(), mapOf(Fields.DATA to Blob.fromBytes(audio)))
            .update(item, Fields.VOICE_SECONDS, seconds)
            .commit()
            .logFailure("attach voice note")
        changed(list, itemId)
    }

    override suspend fun removeVoiceNote(list: ListRef, itemId: String) {
        val item = item(list, itemId)
        db.batch()
            .delete(item.voiceNote())
            .update(item, Fields.VOICE_SECONDS, FieldValue.delete())
            .commit()
            .logFailure("remove voice note")
        changed(list, itemId)
    }

    override suspend fun loadVoiceNote(list: ListRef, itemId: String): ByteArray? =
        try {
            item(list, itemId).voiceNote().get().await().getBlob(Fields.DATA)?.toBytes()
        } catch (e: FirebaseFirestoreException) {
            // Offline and not in the local cache yet.
            null
        }

    override suspend fun setDeleted(list: ListRef, itemIds: List<String>, deleted: Boolean, quietly: Boolean) {
        val moved = if (quietly) emptyList() else named(list, itemIds).filter { it.isDeleted != deleted }
        val value = if (deleted) FieldValue.serverTimestamp() else FieldValue.delete()
        itemIds.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.update(item(list, it), Fields.DELETED_AT, value) }
            batch.commit().logFailure("set deleted")
        }
        activity.note(list, if (deleted) ActivityKind.DELETED else ActivityKind.RESTORED, moved.map { it.text })
    }

    override suspend fun deleteItems(list: ListRef, itemIds: List<String>, quietly: Boolean) {
        // Items that were in the deleted section are in the history already.
        val gone = if (quietly) emptyList() else named(list, itemIds).filter { !it.isDeleted }
        db.deleteItems(itemIds.map { item(list, it) })
        activity.note(list, ActivityKind.DELETED, gone.map { it.text })
    }

    private fun item(list: ListRef, itemId: String) = db.items(list).document(itemId)

    /** The items as this device has them, so the history can name them. Section headings are left out. */
    private suspend fun named(list: ListRef, itemIds: List<String>): List<ListItem> =
        itemIds.mapNotNull { item(list, it).cached()?.toListItem() }.filter { !it.isHeader }

    private suspend fun named(list: ListRef, itemId: String) = named(list, listOf(itemId))

    private suspend fun changed(list: ListRef, itemId: String) =
        activity.note(list, ActivityKind.CHANGED, named(list, itemId).map { it.text })

    private suspend fun changeField(list: ListRef, itemId: String, field: String, value: Any?) {
        setField(list, itemId, field, value)
        changed(list, itemId)
    }

    private companion object {
        val RESERVED_FIELD = Regex("__.*__")
    }

    /** A null [value] removes the field. */
    private fun setField(list: ListRef, itemId: String, field: String, value: Any?) {
        item(list, itemId).update(field, value ?: FieldValue.delete()).logFailure("set $field")
    }
}
