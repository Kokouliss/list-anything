package com.kokouliss.listanything.core.domain.model

/** One list inside a space: groceries, places to eat, things to do. */
data class SharedList(
    val id: String,
    val spaceId: String,
    val name: String,
    val emoji: String,
    /**
     * Set on a wish list by the person the gifts are for. That person's app then hides who said
     * "I'll take this", so the gift stays a surprise.
     */
    val giftForId: String? = null,
    val giftForName: String? = null,
    /**
     * How long a deleted item stays at the bottom of the list, where anyone can bring it back.
     * 0 deletes it right away, [KEEP_DELETED_FOREVER] keeps it until someone removes it.
     */
    val keepDeletedSeconds: Long = 0,
    /** What the items of this list can have. A grocery list needs no map pin or "I'll take this". */
    val features: Set<ListFeature> = ListFeature.entries.toSet(),
    val checkedPlacement: CheckedPlacement = CheckedPlacement.BOTTOM,
    /** Checked items are removed by themselves this long after they were checked. 0 never removes them. */
    val clearCheckedSeconds: Long = 0,
    val sort: ListSort = ListSort.MANUAL,
    /** ISO 4217 code of the prices on this list. */
    val currency: String = "EUR",
    /** Members who want no notifications about this list. */
    val mutedBy: Set<String> = emptySet(),
)

const val KEEP_DELETED_FOREVER = -1L

/** [key] is what the database stores, so renaming an entry does not change saved lists. */
enum class ListFeature(val key: String) {
    QUANTITY("quantity"),
    PRICE("price"),
    LINK("link"),
    DUE_DATE("due"),
    PLACE("place"),
    PHOTO("photo"),
    VOICE("voice"),
    CLAIM("claim"),
}

/** Ready-made feature sets for the kinds of list people make, with the icon a new one starts with. */
enum class ListKind(val emoji: String, val features: Set<ListFeature>) {
    SHOPPING("🛒", setOf(ListFeature.QUANTITY, ListFeature.PRICE, ListFeature.LINK, ListFeature.PHOTO, ListFeature.VOICE)),
    TODO("✅", setOf(ListFeature.DUE_DATE, ListFeature.LINK, ListFeature.PHOTO, ListFeature.VOICE)),
    WISH_LIST("🎁", setOf(ListFeature.PRICE, ListFeature.LINK, ListFeature.PHOTO, ListFeature.CLAIM)),
    PLACES("✈️", setOf(ListFeature.PLACE, ListFeature.LINK, ListFeature.PHOTO, ListFeature.VOICE)),
    NOTES("💡", emptySet()),
    EVERYTHING("📝", ListFeature.entries.toSet()),
}

enum class CheckedPlacement(val key: String) { BOTTOM("bottom"), IN_PLACE("inPlace"), HIDDEN("hidden") }

enum class ListSort(val key: String) { MANUAL("manual"), ALPHABETICAL("alpha"), DUE_DATE("due") }

/** Address of a list. Items are always reached through their list. */
data class ListRef(
    val spaceId: String,
    val listId: String,
)
