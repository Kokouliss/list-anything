package com.kokouliss.listanything.core.data.firebase

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FirebaseFirestore
import com.kokouliss.listanything.core.domain.model.Drawing
import com.kokouliss.listanything.core.domain.model.ImageData
import com.kokouliss.listanything.core.domain.model.ItemLocation
import com.kokouliss.listanything.core.domain.model.ListItem
import com.kokouliss.listanything.core.domain.model.MAX_QUANTITY
import com.kokouliss.listanything.core.domain.model.Member
import com.kokouliss.listanything.core.domain.model.SharedList
import com.kokouliss.listanything.core.domain.model.Space
import com.kokouliss.listanything.core.domain.model.Stroke
import com.kokouliss.listanything.core.domain.model.normalizeLink
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// Document layout. firebase/firestore.rules validates the same names; change both together.
//
// spaces/{spaceId}                          name, ownerId, memberIds[], members{uid: {name}}, inviteCode, invitedEmails[], createdAt
// invites/{inviteCode}                      spaceId
// spaces/{spaceId}/lists/{listId}           name, emoji, giftForId?, giftForName?, createdAt
// .../lists/{listId}/items/{itemId}         text, checked, checkedByName?, createdByName, thumbnail?, location?{latitude, longitude, name?}, link?,
//                                           note?, quantity?, priceCents?, dueDay?, claimedById?, claimedByName?, header?, order?,
//                                           createdById?, checkedById?, createdAt
// .../items/{itemId}/photo/full             data (JPEG bytes)
// .../items/{itemId}/voice/full             data (AAC bytes in an MP4 container); the item has voiceSeconds? while it exists
// spaces/{spaceId}/drawings/{drawingId}     title, strokes[{color, points[]}], createdByName, createdAt
// users/{uid}/tokens/{token}                updatedAt. One per device that gets this person's notifications.
//
// createdById and checkedById tell firebase/functions who not to notify about a change.

internal object Collections {
    const val SPACES = "spaces"
    const val INVITES = "invites"
    const val LISTS = "lists"
    const val ITEMS = "items"
    const val PHOTO = "photo"
    const val VOICE = "voice"
    const val DRAWINGS = "drawings"
    const val USERS = "users"
    const val TOKENS = "tokens"
}

/** Id of the single document under an item's `photo` or `voice` collection that holds the attachment. */
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
    const val CHECKED_BY_ID = "checkedById"
    const val CREATED_BY_NAME = "createdByName"
    const val CREATED_BY_ID = "createdById"
    const val UPDATED_AT = "updatedAt"
    const val THUMBNAIL = "thumbnail"
    const val LOCATION = "location"
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
    const val LINK = "link"
    const val NOTE = "note"
    const val QUANTITY = "quantity"
    const val PRICE_CENTS = "priceCents"
    const val DUE_DAY = "dueDay"
    const val CLAIMED_BY_ID = "claimedById"
    const val CLAIMED_BY_NAME = "claimedByName"
    const val HEADER = "header"
    const val ORDER = "order"
    const val GIFT_FOR_ID = "giftForId"
    const val GIFT_FOR_NAME = "giftForName"
    const val DATA = "data"
    const val VOICE_SECONDS = "voiceSeconds"
    const val TITLE = "title"
    const val STROKES = "strokes"
    const val COLOR = "color"
    const val POINTS = "points"
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
        giftForId = getString(Fields.GIFT_FOR_ID),
        giftForName = getString(Fields.GIFT_FOR_NAME),
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
        note = getString(Fields.NOTE),
        quantity = getLong(Fields.QUANTITY)?.toInt()?.coerceIn(1, MAX_QUANTITY) ?: 1,
        priceCents = getLong(Fields.PRICE_CENTS),
        dueDay = getLong(Fields.DUE_DAY),
        claimedById = getString(Fields.CLAIMED_BY_ID),
        claimedByName = getString(Fields.CLAIMED_BY_NAME),
        isHeader = getBoolean(Fields.HEADER) == true,
        order = getDouble(Fields.ORDER),
        voiceSeconds = getLong(Fields.VOICE_SECONDS)?.toInt(),
    )
}

internal fun DocumentSnapshot.toDrawing(spaceId: String): Drawing? {
    if (!exists()) return null
    return Drawing(
        id = id,
        spaceId = spaceId,
        title = getString(Fields.TITLE).orEmpty(),
        strokes = (get(Fields.STROKES) as? List<*>).orEmpty().mapNotNull { it.toStroke() },
        createdByName = getString(Fields.CREATED_BY_NAME).orEmpty(),
    )
}

/**
 * The stored shape of a stroke. Undo removes a stroke by value, so [toStroke] and [toMap] must
 * round-trip exactly: colours are stored as whole numbers and points as doubles.
 */
internal fun Stroke.toMap(): Map<String, Any> =
    mapOf(Fields.COLOR to color.toLong(), Fields.POINTS to points.map { it.toDouble() })

internal fun Any?.toStroke(): Stroke? {
    val map = this as? Map<*, *> ?: return null
    val color = (map[Fields.COLOR] as? Number)?.toInt() ?: return null
    val points = (map[Fields.POINTS] as? List<*>).orEmpty().map { (it as? Number)?.toFloat() ?: return null }
    return Stroke(color, points)
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

internal fun DocumentReference.voiceNote(): DocumentReference = collection(Collections.VOICE).document(FULL_PHOTO_ID)

/** Deletes items together with their photos and voice notes, in batches under Firestore's 500-write limit. */
internal fun FirebaseFirestore.deleteItems(items: List<DocumentReference>) {
    items.chunked(150).forEach { chunk ->
        val batch = batch()
        chunk.forEach { item ->
            batch.delete(item.fullPhoto())
            batch.delete(item.voiceNote())
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
