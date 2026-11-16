package com.kokouliss.listanything.feature.lists

import com.google.common.truth.Truth.assertThat
import com.kokouliss.listanything.core.domain.model.ListItem
import com.kokouliss.listanything.core.domain.model.SharedList
import java.util.Locale
import org.junit.Test

class ListTextTest {

    @Test
    fun `a list reads as a title, headings, boxed items with their details, notes and the total`() {
        val list = SharedList("l1", "s1", "Supermarket", "🛒", currency = "EUR")
        val items = listOf(
            ListItem("h", "Bakery", false, null, "Panos", null, null, 0, isHeader = true),
            ListItem("1", "Bread", true, "Maria", "Panos", null, null, 1, quantity = 2, priceCents = 150, note = "the dark one"),
            ListItem("2", "Milk", false, null, "Panos", null, null, 2, link = "https://shop.example/milk", dueDay = 20_800),
        )

        val text = listAsLines(list, items, Locale.UK) { "Total $it" }.asText()

        assertThat(text).isEqualTo(
            """
            |🛒 Supermarket
            |
            |Bakery
            |☑ Bread ×2   €3.00
            |    the dark one
            |☐ Milk   13 Dec 2026   https://shop.example/milk
            |
            |Total €3.00
            """.trimMargin(),
        )
    }
}
