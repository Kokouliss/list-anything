package com.kokouliss.listanything.core.data.firebase

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.kokouliss.listanything.core.domain.model.ACTIVITY_KEEP_DAYS
import com.kokouliss.listanything.core.domain.model.Activity
import com.kokouliss.listanything.core.domain.model.ActivityKind
import com.kokouliss.listanything.core.domain.model.ListRef
import com.kokouliss.listanything.core.domain.repository.ActivityRepository
import com.kokouliss.listanything.core.domain.repository.ActivityRepository.Companion.ACTIVITY_SHOWN
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal fun FirebaseFirestore.activity(spaceId: String): CollectionReference =
    collection(Collections.SPACES).document(spaceId).collection(Collections.ACTIVITY)

/**
 * One document per thing a member did, written by the device that did it, as a separate write so
 * a rejected history line never takes the change itself down. Firestore deletes each one at its
 * expireAt (a TTL policy in firebase/firestore.indexes.json) and a visit reads only the newest
 * [ACTIVITY_SHOWN], so the history costs one write per change and a capped read per visit.
 */
internal class FirestoreActivityRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) : ActivityRepository {

    override fun observeActivity(spaceId: String): Flow<List<Activity>> =
        db.activity(spaceId).orderBy(Fields.AT, Query.Direction.DESCENDING).limit(ACTIVITY_SHOWN).snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull { it.toActivity() } }

    /** Notes that the signed-in user did [kind] to the items called [texts]. Nothing for none. */
    suspend fun note(list: ListRef, kind: ActivityKind, texts: List<String>, was: String? = null) {
        if (texts.isEmpty()) return
        val user = auth.currentUser ?: return
        // From this device only: the list is open or was just written, and a history line is not worth a read.
        val listDoc = db.list(list).cached()
        db.activity(list.spaceId).document()
            .set(
                buildMap {
                    put(Fields.KIND, kind.key)
                    put(Fields.BY_ID, user.uid)
                    put(Fields.BY_NAME, user.memberName)
                    put(Fields.LIST_ID, list.listId)
                    put(Fields.LIST_NAME, listDoc?.getString(Fields.NAME).orEmpty())
                    // shortcut: one line names at most MAX_TEXTS items; clearing more at once loses the rest.
                    put(Fields.TEXTS, texts.take(MAX_TEXTS))
                    was?.let { put(Fields.WAS, it) }
                    // A wish list stays a surprise in the history too.
                    listDoc?.getString(Fields.GIFT_FOR_ID)?.let { put(Fields.HIDDEN_FROM_ID, it) }
                    put(Fields.AT, FieldValue.serverTimestamp())
                    put(Fields.EXPIRE_AT, Timestamp(Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(ACTIVITY_KEEP_DAYS.toLong()))))
                },
            )
            .logFailure("note activity")
    }

    /** Notes that the signed-in user made or deleted [list]. Call it while the list is still on this device. */
    suspend fun noteList(list: ListRef, kind: ActivityKind) {
        val name = db.list(list).cached()?.getString(Fields.NAME) ?: return
        note(list, kind, listOf(name))
    }

    private companion object {
        /** Mirrors the limit in firebase/firestore.rules. */
        const val MAX_TEXTS = 50
    }
}

private fun DocumentSnapshot.toActivity(): Activity? {
    val kind = ActivityKind.entries.firstOrNull { it.key == getString(Fields.KIND) } ?: return null
    return Activity(
        id = id,
        kind = kind,
        byId = getString(Fields.BY_ID).orEmpty(),
        byName = getString(Fields.BY_NAME).orEmpty(),
        listId = getString(Fields.LIST_ID).orEmpty(),
        listName = getString(Fields.LIST_NAME).orEmpty(),
        texts = (get(Fields.TEXTS) as? List<*>).orEmpty().filterIsInstance<String>(),
        // A line that has not reached the server yet is estimated, so it shows as just now.
        atMillis = getTimestamp(Fields.AT, ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L,
        was = getString(Fields.WAS),
        hiddenFromId = getString(Fields.HIDDEN_FROM_ID),
    )
}
