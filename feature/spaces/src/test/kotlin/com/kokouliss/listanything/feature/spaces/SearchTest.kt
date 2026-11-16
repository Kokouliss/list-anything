package com.kokouliss.listanything.feature.spaces

import com.google.common.truth.Truth.assertThat
import com.kokouliss.listanything.core.domain.model.ListItem
import com.kokouliss.listanything.core.domain.model.SharedList
import com.kokouliss.listanything.core.domain.model.Space
import org.junit.Test

class SearchTest {

    private val space = Space("s1", "Family", "u1", "ABCD2345", emptyList(), emptyList())
    private val list = SharedList("l1", "s1", "Supermarket", "🛒")

    private fun item(id: String, text: String, note: String? = null, deleted: Boolean = false) = ListItem(
        id = id,
        text = text,
        isChecked = false,
        checkedByName = null,
        createdByName = "Panos",
        thumbnail = null,
        location = null,
        createdAtMillis = 0,
        note = note,
        deletedAtMillis = if (deleted) 1 else null,
    )

    private val sources = listOf(
        SearchSource(space, list, listOf(item("a", "Milk"), item("b", "Bread", note = "the dark one"), item("c", "Milk powder", deleted = true))),
    )

    @Test
    fun `finds items by text or note, ignoring case, and skips deleted ones`() {
        assertThat(sources.search("MILK").map { it.item?.id }).containsExactly("a")
        assertThat(sources.search("dark").map { it.item?.id }).containsExactly("b")
    }

    @Test
    fun `finds a list by its name, before its items`() {
        assertThat(sources.search("super").map { it.item }).containsExactly(null)
        assertThat(sources.search("  ")).isEmpty()
    }
}
