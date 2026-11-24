package com.kokouliss.listanything.core.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
    /** When the item was deleted. Set while it waits at the bottom of the list to be restored or removed. */
    val deletedAtMillis: Long? = null,
    /** When the item was checked, for lists that clear checked items by themselves. */
    val checkedAtMillis: Long? = null,
    /** A moment on the due day when the item rings: for whoever takes it, or for everyone when nobody does. */
    val remindAtMillis: Long? = null,
    /** How often the item comes round again. Counted from its due day, so it needs one. */
    val repeat: Repeat? = null,
    /** Who added and who checked the item. Null on items older than these fields. */
    val createdById: String? = null,
    val checkedById: String? = null,
    /** The members who asked to hear when someone else crosses the item off. Each sees only their own. */
    val watchedBy: Set<String> = emptySet(),
    /** The newest change to the item is saved on this phone but has not reached the cloud yet. */
    val isPending: Boolean = false,
) {
    val isDeleted: Boolean get() = deletedAtMillis != null

    // shortcut: positions are plain numbers halved on every move, so a gap between two items added
    // one second apart takes about twenty moves into it before two items tie. Renumber the list then.
    val sortKey: Double get() = order ?: createdAtMillis.toDouble()
}

/** How often an item comes round again. [key] is what the database stores. */
enum class Repeat(val key: String) {
    DAILY("day"),
    WEEKLY("week"),
    MONTHLY("month"),
    YEARLY("year"),
    ;

    /**
     * The first day of this rhythm after [after], counting from [fromDay]; always at least one step
     * on. Both are counted like `dueDay`. Steps are taken from [fromDay] itself, so the 31st of a
     * month comes back on the 31st after a February.
     */
    fun next(fromDay: Long, after: Long): Long {
        val start = LocalDate.ofEpochDay(fromDay)
        var steps = 1L
        while (true) {
            val day = when (this) {
                DAILY -> start.plusDays(steps)
                WEEKLY -> start.plusWeeks(steps)
                MONTHLY -> start.plusMonths(steps)
                YEARLY -> start.plusYears(steps)
            }.toEpochDay()
            if (day > after) return day
            steps++
        }
    }
}

/**
 * This item with the names its people use now, from [names] (member id to name). An item keeps the
 * name someone had when they added, checked or took it; a person not in [names] keeps that one.
 */
fun ListItem.withNames(names: Map<String, String>): ListItem = copy(
    createdByName = createdById?.let(names::get) ?: createdByName,
    checkedByName = checkedByName?.let { checkedById?.let(names::get) ?: it },
    claimedByName = claimedByName?.let { claimedById?.let(names::get) ?: it },
)

/**
 * The day a checked item that repeats comes back unchecked, with that day as its new due day: the
 * first day of its rhythm after the day it was checked. Null for every other item. Nothing changes
 * on the item until then, so unchecking it by hand leaves it as it was.
 */
fun ListItem.returnsOn(zone: ZoneId = ZoneId.systemDefault()): Long? {
    val rhythm = repeat ?: return null
    val due = dueDay ?: return null
    if (!isChecked || isDeleted) return null
    val checkedDay = checkedAtMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toEpochDay() } ?: due
    return rhythm.next(due, after = checkedDay)
}

/** The checked items whose day to come back is [today] or earlier, each with its new due day. */
fun List<ListItem>.returning(today: Long, zone: ZoneId = ZoneId.systemDefault()): List<Pair<ListItem, Long>> =
    mapNotNull { item -> item.returnsOn(zone)?.takeIf { it <= today }?.let { item to it } }

/** How far a list is: the items still open, out of all of them. Headings and deleted items do not count. */
data class ListProgress(val open: Int, val total: Int)

fun List<ListItem>.progress(): ListProgress {
    val items = filter { !it.isHeader && !it.isDeleted }
    return ListProgress(open = items.count { !it.isChecked }, total = items.size)
}

/** The item on the list that a new one called [text] would double: the same words, whatever the accents and capitals. */
fun List<ListItem>.sameAs(text: String): ListItem? {
    val wanted = text.folded()
    return firstOrNull { !it.isHeader && !it.isDeleted && it.text.folded() == wanted }
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

/** The same time of day as [remindAtMillis], on [toDay] (counted like `dueDay`), in [zone]. */
fun moveReminder(remindAtMillis: Long, toDay: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    LocalDate.ofEpochDay(toDay)
        .atTime(Instant.ofEpochMilli(remindAtMillis).atZone(zone).toLocalTime())
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

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
 * The items as the list shows them. Each section, a heading with the items under it, stays together
 * with its heading on top. Inside it, checked items drop to the end unless [checked] says otherwise,
 * and [sort] orders the rest; the dragged order is kept where nothing else decides.
 */
fun List<ListItem>.sortedForDisplay(
    sort: ListSort = ListSort.MANUAL,
    checked: CheckedPlacement = CheckedPlacement.BOTTOM,
): List<ListItem> {
    var section = 0
    val bySection = sortedBy { it.sortKey }.map { item ->
        if (item.isHeader) section++
        section to item
    }
    val bySort: Comparator<Pair<Int, ListItem>> = when (sort) {
        ListSort.MANUAL -> Comparator { _, _ -> 0 }
        // Without accents and capitals, so "άλφα" sorts with "αλφα". Greek and Latin each in their own order.
        ListSort.ALPHABETICAL -> compareBy { it.second.text.folded() }
        ListSort.DUE_DATE -> compareBy { it.second.dueDay ?: Long.MAX_VALUE }
    }
    // The sort is stable, so rows that compare equal keep the dragged order.
    return bySection
        .sortedWith(
            compareBy<Pair<Int, ListItem>>({ it.first }, { !it.second.isHeader })
                .thenBy { checked == CheckedPlacement.BOTTOM && it.second.isChecked }
                .then(bySort),
        )
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
 * The [ListItem.order] for a new item at the end of the section under the heading [headerId]. With
 * no such heading, the item belongs to no section: it goes above the first heading, since the end of
 * the list is inside the last section. Null when new items land in the right place anyway.
 */
fun orderAtEndOfSection(items: List<ListItem>, headerId: String?): Double? {
    val sorted = items.sortedBy { it.sortKey }
    // -1 without the heading, so the search below starts at the top of the list.
    val start = sorted.indexOfFirst { it.isHeader && it.id == headerId }
    val nextHeader = sorted.drop(start + 1).firstOrNull { it.isHeader } ?: return null
    return orderBetween(sorted.lastOrNull { it.sortKey < nextHeader.sortKey }?.sortKey, nextHeader.sortKey)
}

/**
 * The deleted items whose time on the list is up under [SharedList.keepDeletedSeconds]. None when
 * the list keeps them until someone removes them.
 */
fun List<ListItem>.expiredDeleted(keepDeletedSeconds: Long, nowMillis: Long): List<ListItem> =
    if (keepDeletedSeconds == KEEP_DELETED_FOREVER) {
        emptyList()
    } else {
        filter { item -> item.deletedAtMillis?.let { it + keepDeletedSeconds * 1000 <= nowMillis } == true }
    }

/**
 * The checked items that have been checked for longer than [clearCheckedSeconds]. None when that is
 * 0. An item that repeats is never one of them: it is waiting to come back.
 */
fun List<ListItem>.staleChecked(clearCheckedSeconds: Long, nowMillis: Long): List<ListItem> =
    if (clearCheckedSeconds <= 0) {
        emptyList()
    } else {
        filter { item ->
            item.isChecked && item.repeat == null && item.checkedAtMillis?.let { it + clearCheckedSeconds * 1000 <= nowMillis } == true
        }
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
