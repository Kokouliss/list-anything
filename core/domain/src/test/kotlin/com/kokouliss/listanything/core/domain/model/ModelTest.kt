package com.kokouliss.listanything.core.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModelTest {

    @Test
    fun `a long stroke is thinned to about the limit and keeps both ends`() {
        val points = (0 until 1000).toList()
        val thinned = points.thinnedTo(400)

        assertThat(thinned.size).isAtMost(401)
        assertThat(thinned.first()).isEqualTo(0)
        assertThat(thinned.last()).isEqualTo(999)
        assertThat((0 until 10).toList().thinnedTo(400)).hasSize(10)
    }

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
    fun `links get https in front and the scheme in lower case`() {
        assertThat(normalizeLink(" shop.gr/gift?id=1 ")).isEqualTo("https://shop.gr/gift?id=1")
        assertThat(normalizeLink("HTTP://shop.gr:8080/a|b")).isEqualTo("http://shop.gr:8080/a|b")
    }

    @Test
    fun `text that is not a web address is not a link`() {
        assertThat(normalizeLink("")).isNull()
        assertThat(normalizeLink("just some words")).isNull()
        assertThat(normalizeLink("https://shop.gr/" + "x".repeat(MAX_LINK_LENGTH))).isNull()
    }

    @Test
    fun `links that could do more than open a web page are refused`() {
        assertThat(normalizeLink("javascript:alert(1)")).isNull()
        assertThat(normalizeLink("intent://scan/#Intent;scheme=zxing;end")).isNull()
        assertThat(normalizeLink("file:///sdcard/secret.txt")).isNull()
        // Shows a trusted name but opens evil.example.
        assertThat(normalizeLink("https://bank.com@evil.example")).isNull()
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

    @Test
    fun `an invite link gives back its code and other text gives none`() {
        assertThat(InviteCode.fromLink(InviteCode.link("ABCD2345"))).isEqualTo("ABCD2345")
        assertThat(InviteCode.fromLink("https://evil.example/join/ABCD2345")).isNull()
        assertThat(InviteCode.fromLink(InviteCode.link("ABC"))).isNull()
    }

    @Test
    fun `inside a section open items come before checked ones`() {
        val items = listOf(
            item("milk", isChecked = true, createdAt = 1),
            item("bread", isChecked = false, createdAt = 2),
            item("Bakery", isChecked = false, createdAt = 3, isHeader = true),
            item("cake", isChecked = true, createdAt = 4),
            item("pie", isChecked = false, createdAt = 5),
        )

        assertThat(items.sortedForDisplay().map { it.id })
            .containsExactly("bread", "milk", "Bakery", "pie", "cake").inOrder()
    }

    @Test
    fun `a dragged item gets a position between its new neighbours`() {
        val a = item("a", isChecked = false, createdAt = 10)
        val b = item("b", isChecked = false, createdAt = 20)
        val c = item("c", isChecked = false, createdAt = 30)
        val done = item("done", isChecked = true, createdAt = 5)

        assertThat(orderAfterMove(listOf(a, c, b, done), "c")).isEqualTo(15.0)
        assertThat(orderAfterMove(listOf(c, a, b, done), "c")).isLessThan(10.0)
        // Dropped among the checked items: it stays an open item, after the last open one.
        assertThat(orderAfterMove(listOf(a, b, done, c), "c")).isGreaterThan(20.0)
        assertThat(orderAfterMove(listOf(a, b), "gone")).isNull()
    }

    @Test
    fun `a new item for a section lands before the next heading`() {
        val items = listOf(
            item("Bakery", isChecked = false, createdAt = 10, isHeader = true),
            item("pie", isChecked = false, createdAt = 20),
            item("Butcher", isChecked = false, createdAt = 30, isHeader = true),
        )

        assertThat(orderAtEndOfSection(items, "Bakery")).isEqualTo(25.0)
        assertThat(orderAtEndOfSection(items, "Butcher")).isNull()
        assertThat(orderAtEndOfSection(items, null)).isNull()
    }

    @Test
    fun `prices are read with a comma or a point and add up with the quantity`() {
        assertThat(parsePriceCents("12")).isEqualTo(1200)
        assertThat(parsePriceCents("12,5")).isEqualTo(1250)
        assertThat(parsePriceCents(" 0.99 ")).isEqualTo(99)
        assertThat(parsePriceCents("12.999")).isNull()
        assertThat(parsePriceCents("-3")).isNull()
        assertThat(parsePriceCents("")).isNull()

        val items = listOf(
            item("a", isChecked = false, createdAt = 1).copy(priceCents = 250, quantity = 3),
            item("b", isChecked = false, createdAt = 2).copy(priceCents = 100),
            item("c", isChecked = false, createdAt = 3),
        )
        assertThat(items.totalCents()).isEqualTo(850)
        assertThat(items.takeLast(1).totalCents()).isNull()
    }

    @Test
    fun `shared text becomes an item name and a link`() {
        assertThat(parseSharedText("https://shop.gr/p/1", "Blue scarf"))
            .isEqualTo(SharedContent("Blue scarf", "https://shop.gr/p/1"))
        assertThat(parseSharedText("Look at this scarf https://shop.gr/p/1", null))
            .isEqualTo(SharedContent("Look at this scarf", "https://shop.gr/p/1"))
        assertThat(parseSharedText("https://shop.gr/p/1", null))
            .isEqualTo(SharedContent("https://shop.gr/p/1", "https://shop.gr/p/1"))
        assertThat(parseSharedText("buy milk", null)).isEqualTo(SharedContent("buy milk", null))
        assertThat(parseSharedText("  ", null)).isNull()
        assertThat(parseSharedText("x".repeat(300), null)?.text).hasLength(MAX_ITEM_LENGTH)
    }

    private fun item(id: String, isChecked: Boolean, createdAt: Long, isHeader: Boolean = false) =
        ListItem(id, id, isChecked, null, "Panos", null, null, createdAt, isHeader = isHeader)
}
