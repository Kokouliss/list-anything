package com.kokouliss.listanything.feature.lists

import com.google.common.truth.Truth.assertThat
import com.kokouliss.listanything.core.domain.model.ListItem
import org.junit.Test

class SuggestionsTest {

    private fun item(text: String, checked: Boolean = false) =
        ListItem(text, text, checked, null, "Panos", null, null, 0)

    private val history = listOf("Almond milk", "Milk", "milk powder", "Bread", "Oat Milk")

    @Test
    fun `offers texts that contain the draft, starting ones first, without the draft itself`() {
        assertThat(suggestionsFor("mil", history, emptyList()))
            .containsExactly("Milk", "milk powder", "Almond milk", "Oat Milk").inOrder()
        assertThat(suggestionsFor("milk", history, emptyList())).doesNotContain("Milk")
        assertThat(suggestionsFor("  ", history, emptyList())).isEmpty()
    }

    @Test
    fun `leaves out what is still open on the list, but offers checked items again`() {
        val items = listOf(item("Milk"), item("Oat Milk", checked = true))
        assertThat(suggestionsFor("milk", history, items)).containsExactly("milk powder", "Almond milk", "Oat Milk").inOrder()
    }

    @Test
    fun `stops at six`() {
        val many = (1..9).map { "Item $it" }
        assertThat(suggestionsFor("item", many, emptyList())).hasSize(6)
    }
}
