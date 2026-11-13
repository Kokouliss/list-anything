package com.kokouliss.listanything.core.domain.model

data class ListItem(
    val id: String,
    val text: String,
    val isChecked: Boolean,
    val checkedByName: String?,
    val createdByName: String,
    /** Small preview of the attached photo. The full photo is loaded on demand. */
    val thumbnail: ImageData?,
    val location: ItemLocation?,
    val createdAtMillis: Long,
    /** A web address that says more about the item, for example the product page. Always http or https. */
    val link: String? = null,
    /** More about the item, for example "size M, the blue one". */
    val note: String? = null,
    /** How many to get. */
    val quantity: Int = 1,
    /** Price of one piece, in cents. */
    val priceCents: Long? = null,
    /** The day the item is due, counted in days since 1 January 1970 like `LocalDate.toEpochDay`. */
    val dueDay: Long? = null,
    /** Who said "I'll take this", so nobody else gets the same thing. */
    val claimedById: String? = null,
    val claimedByName: String? = null,
    /** A section title, shown as a heading above the items that follow it. It has no checkbox. */
    val isHeader: Boolean = false,
    /** The position chosen by dragging. An item that was never moved has none and sits where it was added. */
    val order: Double? = null,
    /** Length of the attached voice note. The recording itself is loaded on demand. */
    val voiceSeconds: Int? = null,
) {
    // shortcut: positions are plain numbers halved on every move, so a gap between two items added
    // one second apart takes about twenty moves into it before two items tie. Renumber the list then.
    val sortKey: Double get() = order ?: createdAtMillis.toDouble()
}

data class ItemLocation(
    val latitude: Double,
    val longitude: Double,
    /** Name of the place when the pin was dropped on a named spot on the map. */
    val name: String?,
)

/** Encoded image bytes (JPEG). Compared by content so Compose can skip rows that did not change. */
class ImageData(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean = other is ImageData && bytes.contentEquals(other.bytes)
    override fun hashCode(): Int = bytes.contentHashCode()
}

const val MAX_ITEM_LENGTH = 200
const val MAX_NOTE_LENGTH = 500
const val MAX_QUANTITY = 999
const val MAX_LINK_LENGTH = 2000

/** A voice note stops by itself at this length, which keeps it well under Firestore's document size. */
const val MAX_VOICE_SECONDS = 120

private val WEB_SCHEME = Regex("^https?://", RegexOption.IGNORE_CASE)

// A host with a dot and no "user@" part, an optional port, then anything without spaces.
private val WEB_LINK = Regex("^https?://[^/?#@:\\s]+\\.[^/?#@:\\s]+(:\\d+)?([/?#]\\S*)?$")

/**
 * Returns [input] as a web address that is safe to open, or null when it is not one. "shop.gr/gift"
 * gets https:// in front. Anything that is not http or https is refused, because other members tap
 * these links and other schemes can start arbitrary app actions.
 */
fun normalizeLink(input: String): String? {
    val text = input.trim()
    val scheme = WEB_SCHEME.find(text)?.value
    val link = if (scheme == null) "https://$text" else scheme.lowercase() + text.removePrefix(scheme)
    return link.takeIf { it.length <= MAX_LINK_LENGTH && WEB_LINK.matches(it) }
}

/**
 * The items in the order chosen by dragging. Inside each section, which is a heading with the items
 * under it, open items come first and checked ones after them.
 */
fun List<ListItem>.sortedForDisplay(): List<ListItem> {
    var section = 0
    return sortedBy { it.sortKey }
        .map { item ->
            if (item.isHeader) section++
            section to item
        }
        // The sort is stable, so rows that compare equal keep the dragged order.
        .sortedWith(compareBy({ it.first }, { it.second.isChecked }))
        .map { it.second }
}

/** Room left around an item that is moved to the very start or end of a list. */
private const val ORDER_STEP = 1024.0

/** A position between two neighbours. Null means there is no neighbour on that side. */
fun orderBetween(before: Double?, after: Double?): Double = when {
    before != null && after != null -> (before + after) / 2
    before != null -> before + ORDER_STEP
    else -> (after ?: 0.0) - ORDER_STEP
}

/**
 * The [ListItem.order] that keeps the item [movedId] where it sits in [displayed], the list as it
 * looks right after a drag. Null when there is nothing to change: the item is gone, or it is the
 * only one of its kind.
 */
fun orderAfterMove(displayed: List<ListItem>, movedId: String): Double? {
    val moved = displayed.firstOrNull { it.id == movedId } ?: return null
    // Open and checked items are sorted apart, so only headings and rows of the same kind say where it landed.
    val peers = displayed.filter { it.isHeader || it.isChecked == moved.isChecked }
    val index = peers.indexOf(moved)
    val before = peers.getOrNull(index - 1)
    val after = peers.getOrNull(index + 1)
    return if (before == null && after == null) null else orderBetween(before?.sortKey, after?.sortKey)
}

/**
 * The [ListItem.order] for a new item at the end of the section under the heading [headerId]. Null
 * when new items land there anyway: the section is the last one, or there is no such heading.
 */
fun orderAtEndOfSection(items: List<ListItem>, headerId: String?): Double? {
    val sorted = items.sortedBy { it.sortKey }
    val start = sorted.indexOfFirst { it.isHeader && it.id == headerId }
    if (start < 0) return null
    val nextHeader = sorted.drop(start + 1).firstOrNull { it.isHeader } ?: return null
    return orderBetween(sorted.last { it.sortKey < nextHeader.sortKey }.sortKey, nextHeader.sortKey)
}

/** What the priced items cost together, in cents, or null when none has a price. */
fun List<ListItem>.totalCents(): Long? =
    mapNotNull { item -> item.priceCents?.let { it * item.quantity } }.takeIf { it.isNotEmpty() }?.sum()

private val PRICE = Regex("^(\\d{1,7})(?:[.,](\\d{1,2}))?$")

/** Reads "12", "12,5" or "12.50" as cents. Null when the text is not a price. */
fun parsePriceCents(input: String): Long? {
    val (whole, fraction) = PRICE.matchEntire(input.trim())?.destructured ?: return null
    return whole.toLong() * 100 + fraction.padEnd(2, '0').toLong()
}

/** A link shared from another app, as the name and web address of a new item. */
data class SharedContent(val text: String, val link: String?)

private val URL_IN_TEXT = Regex("https?://\\S+", RegexOption.IGNORE_CASE)

/**
 * Turns what another app shares into an item. Browsers send the address as [text] and the page
 * title as [subject]; shop apps send one sentence with the address somewhere in it. Null when
 * there is nothing to add.
 */
fun parseSharedText(text: String?, subject: String?): SharedContent? {
    val address = URL_IN_TEXT.find(text.orEmpty())?.value
    val link = address?.let(::normalizeLink)
    val words = if (address == null) text.orEmpty() else text.orEmpty().replace(address, " ")
    val name = listOf(subject.orEmpty(), words, link.orEmpty())
        .map { it.trim().replace(Regex("\\s+"), " ") }
        .firstOrNull { it.isNotEmpty() }
        ?: return null
    return SharedContent(name.take(MAX_ITEM_LENGTH), link)
}
