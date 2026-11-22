package com.kokouliss.listanything.core.data.firebase

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FirebaseFirestore
import com.kokouliss.listanything.core.domain.model.CheckedPlacement
import com.kokouliss.listanything.core.domain.model.Drawing
import com.kokouliss.listanything.core.domain.model.ImageData
import com.kokouliss.listanything.core.domain.model.ItemLocation
import com.kokouliss.listanything.core.domain.model.ListFeature
import com.kokouliss.listanything.core.domain.model.ListItem
import com.kokouliss.listanything.core.domain.model.ListSort
import com.kokouliss.listanything.core.domain.model.MAX_QUANTITY
import com.kokouliss.listanything.core.domain.model.Member
import com.kokouliss.listanything.core.domain.model.Repeat
import com.kokouliss.listanything.core.domain.model.SharedList
import com.kokouliss.listanything.core.domain.model.Space
import com.kokouliss.listanything.core.domain.model.Stroke
import com.kokouliss.listanything.core.domain.model.normalizeLink
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy

// Document layout. firebase/firestore.rules validates the same names; change both together.
//
// spaces/{spaceId}                          name, ownerId, memberIds[], members{uid: {name}}, inviteCode, invitedEmails[], createdAt
// invites/{inviteCode}                      spaceId
// spaces/{spaceId}/lists/{listId}           name, emoji, giftForId?, giftForName?, createdAt, and the settings keepDeletedSeconds?,
//                                           features?[], checkedItems?, clearCheckedSeconds?, sort?, currency?, mutedBy?[],
//                                           shopping?{by, name, at}: who last said "I'm going shopping", and when
// .../lists/{listId}/items/{itemId}         text, checked, checkedByName?, createdByName, thumbnail?, location?{latitude, longitude, name?}, link?,
//                                           note?, quantity?, priceCents?, dueDay?, claimedById?, claimedByName?, header?, order?,
//                                           createdById?, checkedById?, createdAt, deletedAt?, checkedAt?, remindAt?, remindById?, repeat?,
//                                           watchedBy?[]: who hears at once when someone else checks it
// .../items/{itemId}/photo/full             data (JPEG bytes)
// .../items/{itemId}/voice/full             data (AAC bytes in an MP4 container); the item has voiceSeconds? while it exists
// spaces/{spaceId}/drawings/{drawingId}     title, strokes[{color, points[]}], createdByName, createdAt
// spaces/{spaceId}/suggestions/all          texts{lower-case text: text}. Every item text ever added to the space.
// users/{uid}/tokens/{token}                updatedAt. One per device that gets this person's notifications.
//
// createdById and checkedById tell firebase/functions who not to notify about a change, and
// remindById whose own phone already rings at remindAt.

internal object Collections {
    const val SPACES = "spaces"
    const val INVITES = "invites"
    const val LISTS = "lists"
    const val ITEMS = "items"
    const val PHOTO = "photo"
    const val VOICE = "voice"
    const val DRAWINGS = "drawings"
    const val SUGGESTIONS = "suggestions"
    const val USERS = "users"
    const val TOKENS = "tokens"
}

/** Id of the single document under an item's `photo` or `voice` collection that holds the attachment. */
internal const val FULL_PHOTO_ID = "full"

/** Id of the one document under a space's `suggestions` collection. */
internal const val SUGGESTIONS_ID = "all"

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
    const val KEEP_DELETED_SECONDS = "keepDeletedSeconds"
    const val DELETED_AT = "deletedAt"
    const val CHECKED_AT = "checkedAt"
    const val REMIND_AT = "remindAt"
    const val REMIND_BY_ID = "remindById"
    const val REPEAT = "repeat"
    const val SHOPPING = "shopping"
    const val BY = "by"
    const val AT = "at"
    const val FEATURES = "features"
    const val CHECKED_ITEMS = "checkedItems"
    const val CLEAR_CHECKED_SECONDS = "clearCheckedSeconds"
    const val SORT = "sort"
    const val CURRENCY = "currency"
    const val MUTED_BY = "mutedBy"
    const val WATCHED_BY = "watchedBy"
    const val DATA = "data"
    const val VOICE_SECONDS = "voiceSeconds"
    const val TITLE = "title"
    const val STROKES = "strokes"
    const val COLOR = "color"
    const val POINTS = "points"
    const val TEXTS = "texts"
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
    // The time of a "going shopping" that has not reached the server yet is estimated, so the button rests at once.
    val shopping = get(Fields.SHOPPING, ServerTimestampBehavior.ESTIMATE) as? Map<*, *>
    return SharedList(
        id = id,
        spaceId = spaceId,
        name = getString(Fields.NAME).orEmpty(),
        emoji = getString(Fields.EMOJI).orEmpty(),
        giftForId = getString(Fields.GIFT_FOR_ID),
        giftForName = getString(Fields.GIFT_FOR_NAME),
        keepDeletedSeconds = getLong(Fields.KEEP_DELETED_SECONDS) ?: 0,
        features = (get(Fields.FEATURES) as? List<*>)?.mapNotNullTo(HashSet()) { key -> ListFeature.entries.firstOrNull { it.key == key } }
            ?: ListFeature.entries.toSet(),
        checkedPlacement = CheckedPlacement.entries.firstOrNull { it.key == getString(Fields.CHECKED_ITEMS) } ?: CheckedPlacement.BOTTOM,
        clearCheckedSeconds = getLong(Fields.CLEAR_CHECKED_SECONDS) ?: 0,
        sort = ListSort.entries.firstOrNull { it.key == getString(Fields.SORT) } ?: ListSort.MANUAL,
        currency = getString(Fields.CURRENCY) ?: "EUR",
        mutedBy = (get(Fields.MUTED_BY) as? List<*>)?.filterIsInstanceTo(HashSet<String>()) ?: emptySet(),
        shoppingAtMillis = (shopping?.get(Fields.AT) as? Timestamp)?.toDate()?.time,
        shoppingByName = shopping?.get(Fields.NAME) as? String,
    )
}

/** The settings of [list] as the list document stores them. */
internal fun SharedList.settingsMap(): Map<String, Any> = mapOf(
    Fields.KEEP_DELETED_SECONDS to keepDeletedSeconds,
    Fields.FEATURES to features.map { it.key },
    Fields.CHECKED_ITEMS to checkedPlacement.key,
    Fields.CLEAR_CHECKED_SECONDS to clearCheckedSeconds,
    Fields.SORT to sort.key,
    Fields.CURRENCY to currency,
)

internal fun ItemLocation.toMap(): Map<String, Any> = buildMap {
    put(Fields.LATITUDE, latitude)
    put(Fields.LONGITUDE, longitude)
    name?.let { put(Fields.NAME, it) }
}

/**
 * The fields of a copy of this item added by [user] to another list: its text, state and details,
 * at the same position. The photo, the voice note and who checked or takes it are not copied.
 */
internal fun ListItem.copyMap(user: FirebaseUser): Map<String, Any> = buildMap {
    put(Fields.TEXT, text)
    put(Fields.CHECKED, isChecked)
    put(Fields.CREATED_BY_NAME, user.memberName)
    put(Fields.CREATED_BY_ID, user.uid)
    put(Fields.CREATED_AT, FieldValue.serverTimestamp())
    // Every copy gets the same server time, so the position it had is written out.
    put(Fields.ORDER, sortKey)
    if (isHeader) put(Fields.HEADER, true)
    if (quantity > 1) put(Fields.QUANTITY, quantity)
    link?.let { put(Fields.LINK, it) }
    note?.let { put(Fields.NOTE, it) }
    priceCents?.let { put(Fields.PRICE_CENTS, it) }
    dueDay?.let { put(Fields.DUE_DAY, it) }
    repeat?.let { put(Fields.REPEAT, it.key) }
    location?.let { put(Fields.LOCATION, it.toMap()) }
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
        deletedAtMillis = getTimestamp(Fields.DELETED_AT, ServerTimestampBehavior.ESTIMATE)?.toDate()?.time,
        checkedAtMillis = getTimestamp(Fields.CHECKED_AT, ServerTimestampBehavior.ESTIMATE)?.toDate()?.time,
        remindAtMillis = getTimestamp(Fields.REMIND_AT)?.toDate()?.time,
        voiceSeconds = getLong(Fields.VOICE_SECONDS)?.toInt(),
        repeat = Repeat.entries.firstOrNull { it.key == getString(Fields.REPEAT) },
        createdById = getString(Fields.CREATED_BY_ID),
        checkedById = getString(Fields.CHECKED_BY_ID),
        watchedBy = (get(Fields.WATCHED_BY) as? List<*>)?.filterIsInstanceTo(HashSet<String>()) ?: emptySet(),
        isPending = metadata.hasPendingWrites(),
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

/**
 * The signed-in user, again whenever the name or the confirmed state of the email changes. Those
 * change without a new sign-in, so this follows the token (renewed on purpose after each change)
 * rather than the sign-in state, and skips the hourly renewals that change nothing.
 */
internal fun FirebaseAuth.userFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.IdTokenListener { trySend(it.currentUser) }
    addIdTokenListener(listener)
    awaitClose { removeIdTokenListener(listener) }
}.distinctUntilChangedBy { user -> user?.let { Triple(it.uid, it.displayName, it.isEmailVerified) } }

internal fun FirebaseAuth.requireUser(): FirebaseUser = checkNotNull(currentUser) { "No user is signed in" }

/** The name other members see next to this person's items. */
internal val FirebaseUser.memberName: String
    get() = displayName?.takeIf { it.isNotBlank() } ?: email?.substringBefore('@') ?: "?"

/** The address email invites are matched against. The security rules require it to be verified. */
internal val FirebaseUser.verifiedEmail: String?
    get() = email?.lowercase()?.takeIf { isEmailVerified }

internal fun DocumentReference.fullPhoto(): DocumentReference = collection(Collections.PHOTO).document(FULL_PHOTO_ID)

internal fun DocumentReference.voiceNote(): DocumentReference = collection(Collections.VOICE).document(FULL_PHOTO_ID)

internal fun FirebaseFirestore.suggestions(spaceId: String): DocumentReference =
    collection(Collections.SPACES).document(spaceId).collection(Collections.SUGGESTIONS).document(SUGGESTIONS_ID)

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
