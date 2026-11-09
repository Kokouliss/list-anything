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

/** Open items first, then checked ones, each group in the order it was added. */
fun List<ListItem>.sortedForDisplay(): List<ListItem> =
    sortedWith(compareBy<ListItem> { it.isChecked }.thenBy { it.createdAtMillis })
