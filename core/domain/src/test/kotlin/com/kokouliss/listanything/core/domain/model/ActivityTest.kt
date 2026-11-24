package com.kokouliss.listanything.core.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ActivityTest {

    private val minute = 60_000L

    private fun line(
        id: String,
        at: Long,
        kind: ActivityKind = ActivityKind.CHECKED,
        by: String = "u2",
        list: String = "l1",
        texts: List<String> = listOf(id),
        was: String? = null,
        hiddenFrom: String? = null,
    ) = Activity(id, kind, by, "Old name", list, "Old list", texts, at, was, hiddenFrom)

    @Test
    fun `one person doing the same thing on one list in one go is one line, in the order it happened`() {
        val history = listOf(line("bread", 10 * minute), line("eggs", 5 * minute), line("milk", 0)).forMember("u1", emptyMap(), emptyMap())

        assertThat(history).hasSize(1)
        assertThat(history.single().texts).containsExactly("milk", "eggs", "bread").inOrder()
        assertThat(history.single().id).isEqualTo("bread")
    }

    @Test
    fun `another person, another kind, another list or a long pause starts a new line`() {
        val history = listOf(
            line("a", 100 * minute),
            line("b", 99 * minute, by = "u3"),
            line("c", 98 * minute, by = "u3", kind = ActivityKind.ADDED),
            line("d", 97 * minute, by = "u3", kind = ActivityKind.ADDED, list = "l2"),
            line("e", 10 * minute, by = "u3", kind = ActivityKind.ADDED, list = "l2"),
        ).forMember("u1", emptyMap(), emptyMap())

        assertThat(history.map { it.id }).containsExactly("a", "b", "c", "d", "e").inOrder()
    }

    @Test
    fun `a rename keeps its own line, and the same item changed twice is named once`() {
        val history = listOf(
            line("q2", 3 * minute, kind = ActivityKind.CHANGED, texts = listOf("Milk")),
            line("q1", 2 * minute, kind = ActivityKind.CHANGED, texts = listOf("Milk")),
            line("r", minute, kind = ActivityKind.CHANGED, texts = listOf("Milk"), was = "Mlk"),
        ).forMember("u1", emptyMap(), emptyMap())

        assertThat(history.map { it.id }).containsExactly("q2", "r").inOrder()
        assertThat(history.first().texts).containsExactly("Milk")
    }

    @Test
    fun `lines on a wish list are hidden from the person it is for, and people and lists show their current names`() {
        val history = listOf(line("lego", minute, hiddenFrom = "u1"), line("milk", 0, by = "gone", list = "deleted"))

        assertThat(history.forMember("u1", mapOf("u2" to "Maria"), mapOf("l1" to "🛒 Supermarket")).map { it.id }).containsExactly("milk")
        val forMaria = history.forMember("u2", mapOf("u2" to "Maria"), mapOf("l1" to "🛒 Supermarket"))
        assertThat(forMaria.map { it.byName to it.listName })
            .containsExactly("Maria" to "🛒 Supermarket", "Old name" to "Old list").inOrder()
    }
}
