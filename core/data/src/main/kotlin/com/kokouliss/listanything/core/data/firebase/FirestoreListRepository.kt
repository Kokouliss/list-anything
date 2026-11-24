package com.kokouliss.listanything.core.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import com.kokouliss.listanything.core.domain.model.ActivityKind
import com.kokouliss.listanything.core.domain.model.ListFeature
import com.kokouliss.listanything.core.domain.model.ListRef
import com.kokouliss.listanything.core.domain.model.SharedList
import com.kokouliss.listanything.core.domain.repository.ListRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

internal fun FirebaseFirestore.lists(spaceId: String): CollectionReference =
    collection(Collections.SPACES).document(spaceId).collection(Collections.LISTS)

internal fun FirebaseFirestore.list(ref: ListRef): DocumentReference = lists(ref.spaceId).document(ref.listId)

internal fun FirebaseFirestore.items(ref: ListRef): CollectionReference = list(ref).collection(Collections.ITEMS)

internal class FirestoreListRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val activity: FirestoreActivityRepository,
) : ListRepository {

    override fun observeLists(spaceId: String): Flow<List<SharedList>> =
        db.lists(spaceId).orderBy(Fields.CREATED_AT).snapshots().map { snapshot ->
            snapshot.documents.mapNotNull { it.toSharedList(spaceId) }
        }

    override fun observeList(ref: ListRef): Flow<SharedList?> =
        db.list(ref).snapshots()
            .map { it.toSharedList(ref.spaceId) }
            .catch { e -> if (e is FirebaseFirestoreException) emit(null) else throw e }

    override suspend fun createList(spaceId: String, name: String, emoji: String, features: Set<ListFeature>) {
        val list = db.lists(spaceId).document()
        list
            .set(
                mapOf(
                    Fields.NAME to name,
                    Fields.EMOJI to emoji,
                    Fields.FEATURES to features.map { it.key },
                    Fields.CREATED_AT to FieldValue.serverTimestamp(),
                ),
            )
            .logFailure("create list")
        activity.noteList(ListRef(spaceId, list.id), ActivityKind.LIST_ADDED)
    }

    override suspend fun updateList(ref: ListRef, name: String, emoji: String) {
        db.list(ref).update(Fields.NAME, name, Fields.EMOJI, emoji).logFailure("update list")
    }

    override suspend fun setGiftForMe(ref: ListRef, enabled: Boolean) {
        val user = auth.requireUser()
        db.list(ref)
            .update(
                Fields.GIFT_FOR_ID, if (enabled) user.uid else FieldValue.delete(),
                Fields.GIFT_FOR_NAME, if (enabled) user.memberName else FieldValue.delete(),
            )
            .logFailure("set wish list")
    }

    override suspend fun updateSettings(ref: ListRef, list: SharedList) {
        db.list(ref).update(list.settingsMap()).logFailure("update list settings")
    }

    override suspend fun setMuted(ref: ListRef, muted: Boolean) {
        val uid = auth.requireUser().uid
        db.list(ref)
            .update(Fields.MUTED_BY, if (muted) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid))
            .logFailure("mute list")
    }

    override suspend fun announceShopping(ref: ListRef) {
        val user = auth.requireUser()
        db.list(ref)
            .update(
                Fields.SHOPPING,
                mapOf(Fields.BY to user.uid, Fields.NAME to user.memberName, Fields.AT to FieldValue.serverTimestamp()),
            )
            .logFailure("say going shopping")
    }

    override suspend fun copyList(from: ListRef, toSpaceId: String) {
        try {
            val user = auth.requireUser()
            // Both reads come from the local cache when offline, like deleteList.
            val list = db.list(from).get().await().toSharedList(from.spaceId) ?: return
            val items = db.items(from).get().await().documents.mapNotNull { it.toListItem() }.filter { !it.isDeleted }
            val copy = db.lists(toSpaceId).document()
            copy.set(
                list.settingsMap() + mapOf(
                    Fields.NAME to list.name,
                    Fields.EMOJI to list.emoji,
                    Fields.CREATED_AT to FieldValue.serverTimestamp(),
                ),
            ).logFailure("copy list")
            activity.noteList(ListRef(toSpaceId, copy.id), ActivityKind.LIST_ADDED)
            items.chunked(450).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.set(copy.collection(Collections.ITEMS).document(), it.copyMap(user)) }
                batch.commit().logFailure("copy items")
            }
        } catch (e: FirebaseFirestoreException) {
            Log.w("ListAnythingSync", "Could not copy list", e)
        }
    }

    override suspend fun deleteList(ref: ListRef) {
        try {
            activity.noteList(ref, ActivityKind.LIST_DELETED)
            // Reads from the local cache when offline, so this also works without a connection.
            val items = db.items(ref).get().await().documents.map { it.reference }
            db.deleteItems(items)
            db.list(ref).delete().logFailure("delete list")
        } catch (e: FirebaseFirestoreException) {
            Log.w("ListAnythingSync", "Could not delete list", e)
        }
    }
}
