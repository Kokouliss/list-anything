package com.kokouliss.listanything.core.data.firebase

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FirebaseFirestore
import com.kokouliss.listanything.core.domain.model.ImageData
import com.kokouliss.listanything.core.domain.model.ItemLocation
import com.kokouliss.listanything.core.domain.model.ListItem
import com.kokouliss.listanything.core.domain.model.Member
import com.kokouliss.listanything.core.domain.model.SharedList
import com.kokouliss.listanything.core.domain.model.Space
import com.kokouliss.listanything.core.domain.model.normalizeLink
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// Document layout. firebase/firestore.rules validates the same names; change both together.
//
// spaces/{spaceId}                          name, ownerId, memberIds[], members{uid: {name}}, inviteCode, invitedEmails[], createdAt
// invites/{inviteCode}                      spaceId
// spaces/{spaceId}/lists/{listId}           name, emoji, createdAt
// .../lists/{listId}/items/{itemId}         text, checked, checkedByName?, createdByName, thumbnail?, location?{latitude, longitude, name?}, link?, createdAt
// .../items/{itemId}/photo/full             data (JPEG bytes)

internal object Collections {
    const val SPACES = "spaces"
    const val INVITES = "invites"
    const val LISTS = "lists"
    const val ITEMS = "items"
    const val PHOTO = "photo"
}

/** Id of the single document under an item's `photo` collection that holds the full-size picture. */
internal const val FULL_PHOTO_ID = "full"

internal object Fields {
    const val NAME = "name"
    const val OWNER_ID = "ownerId"
    const val MEMBER_IDS = "memberIds"
    const val MEMBERS = "members"
    const val INVITE_CODE = "inviteCode"
    const val INVITED_EMAILS = "invitedEmails"
    const val CREATED_AT = "createdAt"
    const val SPACE_ID = "spaceId"
    const val EMOJI = "emoji"
    const val TEXT = "text"
    const val CHECKED = "checked"
    const val CHECKED_BY_NAME = "checkedByName"
    const val CREATED_BY_NAME = "createdByName"
    const val THUMBNAIL = "thumbnail"
    const val LOCATION = "location"
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
    const val LINK = "link"
    const val DATA = "data"
}

internal fun DocumentSnapshot.toSpace(): Space? {
    if (!exists()) return null
    val ownerId = getString(Fields.OWNER_ID) ?: return null
    val members = (get(Fields.MEMBERS) as? Map<*, *>).orEmpty()
        .mapNotNull { (id, entry) ->
            val name = (entry as? Map<*, *>)?.get(Fields.NAME) as? String
            if (id is String && name != null) Member(id, name) else null
        }
        .sortedWith(compareByDescending<Member> { it.id == ownerId }.thenBy { it.name.lowercase() })
    return Space(
        id = id,
        name = getString(Fields.NAME).orEmpty(),
        ownerId = ownerId,
        inviteCode = getString(Fields.INVITE_CODE).orEmpty(),
        members = members,
        invitedEmails = (get(Fields.INVITED_EMAILS) as? List<*>).orEmpty().filterIsInstance<String>(),
    )
}

internal fun DocumentSnapshot.toSharedList(spaceId: String): SharedList? {
    if (!exists()) return null
    return SharedList(
        id = id,
        spaceId = spaceId,
        name = getString(Fields.NAME).orEmpty(),
        emoji = getString(Fields.EMOJI).orEmpty(),
    )
}

internal fun DocumentSnapshot.toListItem(): ListItem? {
    if (!exists()) return null
    val location = (get(Fields.LOCATION) as? Map<*, *>)?.let { map ->
        val latitude = (map[Fields.LATITUDE] as? Number)?.toDouble()
        val longitude = (map[Fields.LONGITUDE] as? Number)?.toDouble()
        if (latitude != null && longitude != null) {
            ItemLocation(latitude, longitude, map[Fields.NAME] as? String)
        } else {
            null
        }
    }
    return ListItem(
        id = id,
        text = getString(Fields.TEXT).orEmpty(),
        isChecked = getBoolean(Fields.CHECKED) == true,
        checkedByName = getString(Fields.CHECKED_BY_NAME),
        createdByName = getString(Fields.CREATED_BY_NAME).orEmpty(),
        thumbnail = getBlob(Fields.THUMBNAIL)?.let { ImageData(it.toBytes()) },
        location = location,
        // A just-added item has no server time yet; the estimate keeps it at the end of the list.
        createdAtMillis = getTimestamp(Fields.CREATED_AT, ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L,
        // Checked again here: the link was written by another member's device and is opened with a tap.
        link = getString(Fields.LINK)?.let(::normalizeLink),
    )
}

internal fun FirebaseAuth.userFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

internal fun FirebaseAuth.requireUser(): FirebaseUser = checkNotNull(currentUser) { "No user is signed in" }

/** The name other members see next to this person's items. */
internal val FirebaseUser.memberName: String
    get() = displayName?.takeIf { it.isNotBlank() } ?: email?.substringBefore('@') ?: "?"

/** The address email invites are matched against. The security rules require it to be verified. */
internal val FirebaseUser.verifiedEmail: String?
    get() = email?.lowercase()?.takeIf { isEmailVerified }

internal fun DocumentReference.fullPhoto(): DocumentReference = collection(Collections.PHOTO).document(FULL_PHOTO_ID)

/** Deletes items together with their photos, in batches under Firestore's 500-write limit. */
internal fun FirebaseFirestore.deleteItems(items: List<DocumentReference>) {
    items.chunked(200).forEach { chunk ->
        val batch = batch()
        chunk.forEach { item ->
            batch.delete(item.fullPhoto())
            batch.delete(item)
        }
        batch.commit().logFailure("delete items")
    }
}

/**
 * Offline-first writes are not awaited: Firestore applies them on the device at once and syncs
 * later. A rejection (for example by the security rules) arrives much later, so it is only logged.
 */
internal fun Task<*>.logFailure(action: String) {
    addOnFailureListener { Log.w("ListAnythingSync", "Could not $action", it) }
}
