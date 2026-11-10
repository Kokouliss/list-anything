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
)

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

const val MAX_LINK_LENGTH = 2000

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

/** Open items first, then checked ones, each group in the order it was added. */
fun List<ListItem>.sortedForDisplay(): List<ListItem> =
    sortedWith(compareBy<ListItem> { it.isChecked }.thenBy { it.createdAtMillis })
