package com.kokouliss.listanything.core.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModelTest {

    @Test
    fun `generated invite codes are valid`() {
        repeat(50) { assertThat(InviteCode.isValid(InviteCode.generate())).isTrue() }
    }

    @Test
    fun `invite codes are normalized the way people type them`() {
        assertThat(InviteCode.normalize(" abcd-2345 ")).isEqualTo("ABCD2345")
        assertThat(InviteCode.isValid("ABCD2345")).isTrue()
    }

    @Test
    fun `invite codes with look-alike characters or a wrong length are rejected`() {
        assertThat(InviteCode.isValid("ABCD234")).isFalse()
        assertThat(InviteCode.isValid("ABCD2340")).isFalse() // 0 is not in the alphabet
        assertThat(InviteCode.isValid("abcd2345")).isFalse()
    }

    @Test
    fun `emails are trimmed and lower-cased, and non-emails are rejected`() {
        assertThat(normalizeEmail("  Maria@Gmail.com ")).isEqualTo("maria@gmail.com")
        assertThat(normalizeEmail("maria")).isNull()
        assertThat(normalizeEmail("maria@gmail")).isNull()
        assertThat(normalizeEmail("a b@gmail.com")).isNull()
    }

    @Test
    fun `open items come first, each group in the order it was added`() {
        val items = listOf(
            item("checked-early", isChecked = true, createdAt = 1),
            item("open-late", isChecked = false, createdAt = 4),
            item("checked-late", isChecked = true, createdAt = 3),
            item("open-early", isChecked = false, createdAt = 2),
        )

        assertThat(items.sortedForDisplay().map { it.id })
            .containsExactly("open-early", "open-late", "checked-early", "checked-late")
            .inOrder()
    }

    private fun item(id: String, isChecked: Boolean, createdAt: Long) =
        ListItem(id, id, isChecked, null, "Panos", null, null, createdAt)
}
