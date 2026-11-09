package com.kokouliss.listanything.core.data.firebase

import android.util.Log
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
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
    private val db: FirebaseFirestore,
) : ListRepository {

    override fun observeLists(spaceId: String): Flow<List<SharedList>> =
        db.lists(spaceId).orderBy(Fields.CREATED_AT).snapshots().map { snapshot ->
            snapshot.documents.mapNotNull { it.toSharedList(spaceId) }
        }

    override fun observeList(ref: ListRef): Flow<SharedList?> =
        db.list(ref).snapshots()
            .map { it.toSharedList(ref.spaceId) }
            .catch { e -> if (e is FirebaseFirestoreException) emit(null) else throw e }

    override suspend fun createList(spaceId: String, name: String, emoji: String) {
        db.lists(spaceId).document()
            .set(
                mapOf(
                    Fields.NAME to name,
                    Fields.EMOJI to emoji,
                    Fields.CREATED_AT to FieldValue.serverTimestamp(),
                ),
            )
            .logFailure("create list")
    }

    override suspend fun updateList(ref: ListRef, name: String, emoji: String) {
        db.list(ref).update(Fields.NAME, name, Fields.EMOJI, emoji).logFailure("update list")
    }

    override suspend fun deleteList(ref: ListRef) {
        try {
            // Reads from the local cache when offline, so this also works without a connection.
            val items = db.items(ref).get().await().documents.map { it.reference }
            db.deleteItems(items)
            db.list(ref).delete().logFailure("delete list")
        } catch (e: FirebaseFirestoreException) {
            Log.w("ListAnythingSync", "Could not delete list", e)
        }
    }
}
