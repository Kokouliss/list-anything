package com.kokouliss.listanything.widget

import com.google.common.truth.Truth.assertThat
import com.kokouliss.listanything.core.domain.model.ListItem
import com.kokouliss.listanything.core.domain.model.ListSort
import org.junit.Test

class WidgetItemsTest {
    private fun item(id: String, text: String, checked: Boolean = false, header: Boolean = false, deletedAt: Long? = null, quantity: Int = 1) =
        ListItem(id, text, checked, null, "Panos", null, null, createdAtMillis = id.toLong(), isHeader = header, deletedAtMillis = deletedAt, quantity = quantity)

    @Test
    fun `only open items, in the list's order`() {
        val items = listOf(
            item("1", "Bread"),
            item("2", "Milk", checked = true),
            item("3", "Dairy", header = true),
            item("4", "Yoghurt", deletedAt = 5L),
            item("5", "apples"),
        )
        assertThat(items.openItems(ListSort.MANUAL).map { it.text }).containsExactly("Bread", "apples").inOrder()
        // A section keeps its items under its heading, so A to Z sorts only inside each section.
        assertThat(items.openItems(ListSort.ALPHABETICAL).map { it.text }).containsExactly("Bread", "apples").inOrder()
        assertThat(items.filter { !it.isHeader }.openItems(ListSort.ALPHABETICAL).map { it.text }).containsExactly("apples", "Bread").inOrder()
    }

    @Test
    fun `quantity goes in front of the text`() {
        assertThat(item("1", "Milk", quantity = 2).widgetText()).isEqualTo("2 × Milk")
        assertThat(item("1", "Milk").widgetText()).isEqualTo("Milk")
    }
}
