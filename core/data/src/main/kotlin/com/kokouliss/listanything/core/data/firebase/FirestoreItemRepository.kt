package com.kokouliss.listanything.core.data.firebase

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import com.kokouliss.listanything.core.data.di.IoDispatcher
import com.kokouliss.listanything.core.domain.model.ImageData
import com.kokouliss.listanything.core.domain.model.ItemLocation
import com.kokouliss.listanything.core.domain.model.ListItem
import com.kokouliss.listanything.core.domain.model.ListRef
import com.kokouliss.listanything.core.domain.repository.ItemRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

internal class FirestoreItemRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val photoCompressor: PhotoCompressor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ItemRepository {

    override fun observeItems(list: ListRef): Flow<List<ListItem>> =
        db.items(list).orderBy(Fields.CREATED_AT).snapshots().map { snapshot ->
            snapshot.documents.mapNotNull { it.toListItem() }
        }

    override suspend fun addItem(list: ListRef, text: String, link: String?, isHeader: Boolean, order: Double?) {
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
                },
            )
            .logFailure("add item")
    }

    override suspend fun updateText(list: ListRef, itemId: String, text: String) {
        item(list, itemId).update(Fields.TEXT, text).logFailure("edit item")
    }

    override suspend fun setChecked(list: ListRef, itemId: String, checked: Boolean) {
        val user = auth.requireUser()
        item(list, itemId)
            .update(
                Fields.CHECKED, checked,
                Fields.CHECKED_BY_NAME, if (checked) user.memberName else FieldValue.delete(),
                Fields.CHECKED_BY_ID, if (checked) user.uid else FieldValue.delete(),
            )
            .logFailure("check item")
    }

    override suspend fun setLocation(list: ListRef, itemId: String, location: ItemLocation?) {
        val value: Any = if (location == null) {
            FieldValue.delete()
        } else {
            buildMap {
                put(Fields.LATITUDE, location.latitude)
                put(Fields.LONGITUDE, location.longitude)
                location.name?.let { put(Fields.NAME, it) }
            }
        }
        item(list, itemId).update(Fields.LOCATION, value).logFailure("set location")
    }

    override suspend fun setLink(list: ListRef, itemId: String, link: String?) =
        setField(list, itemId, Fields.LINK, link)

    override suspend fun setNote(list: ListRef, itemId: String, note: String?) =
        setField(list, itemId, Fields.NOTE, note)

    // One is the default and is not stored.
    override suspend fun setQuantity(list: ListRef, itemId: String, quantity: Int) =
        setField(list, itemId, Fields.QUANTITY, quantity.takeIf { it > 1 })

    override suspend fun setPrice(list: ListRef, itemId: String, priceCents: Long?) =
        setField(list, itemId, Fields.PRICE_CENTS, priceCents)

    override suspend fun setDueDay(list: ListRef, itemId: String, dueDay: Long?) =
        setField(list, itemId, Fields.DUE_DAY, dueDay)

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
                list.reference.collection(Collections.ITEMS)
                    .whereLessThanOrEqualTo(Fields.DUE_DAY, throughDay)
                    .get().await()
                    .documents.mapNotNull { it.toListItem() }
                    .filter { !it.isChecked }
            }
        }
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
    }

    override suspend fun removePhoto(list: ListRef, itemId: String) {
        val item = item(list, itemId)
        db.batch()
            .delete(item.fullPhoto())
            .update(item, Fields.THUMBNAIL, FieldValue.delete())
            .commit()
            .logFailure("remove photo")
    }

    override suspend fun loadPhoto(list: ListRef, itemId: String): ImageData? =
        try {
            item(list, itemId).fullPhoto().get().await().getBlob(Fields.DATA)?.let { ImageData(it.toBytes()) }
        } catch (e: FirebaseFirestoreException) {
            // Offline and not in the local cache yet.
            null
        }

    override suspend fun deleteItems(list: ListRef, itemIds: List<String>) {
        db.deleteItems(itemIds.map { item(list, it) })
    }

    private fun item(list: ListRef, itemId: String) = db.items(list).document(itemId)

    /** A null [value] removes the field. */
    private fun setField(list: ListRef, itemId: String, field: String, value: Any?) {
        item(list, itemId).update(field, value ?: FieldValue.delete()).logFailure("set $field")
    }
}
