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

    override suspend fun addItem(list: ListRef, text: String) {
        db.items(list).document()
            .set(
                mapOf(
                    Fields.TEXT to text,
                    Fields.CHECKED to false,
                    Fields.CREATED_BY_NAME to auth.requireUser().memberName,
                    Fields.CREATED_AT to FieldValue.serverTimestamp(),
                ),
            )
            .logFailure("add item")
    }

    override suspend fun updateText(list: ListRef, itemId: String, text: String) {
        item(list, itemId).update(Fields.TEXT, text).logFailure("edit item")
    }

    override suspend fun setChecked(list: ListRef, itemId: String, checked: Boolean) {
        item(list, itemId)
            .update(
                Fields.CHECKED, checked,
                Fields.CHECKED_BY_NAME, if (checked) auth.requireUser().memberName else FieldValue.delete(),
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
}
