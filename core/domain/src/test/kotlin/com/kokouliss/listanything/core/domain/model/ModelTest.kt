package com.kokouliss.listanything.core.domain.model

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
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
    fun `deleted items expire after the keep time, right away at zero, never when kept until removed`() {
        val old = item("old", isChecked = false, createdAt = 1).copy(deletedAtMillis = 1_000)
        val fresh = old.copy(id = "fresh", deletedAtMillis = 50_000)
        val live = old.copy(id = "live", deletedAtMillis = null)
        val items = listOf(old, fresh, live)

        assertThat(items.expiredDeleted(keepDeletedSeconds = 10, nowMillis = 20_000)).containsExactly(old)
        assertThat(items.expiredDeleted(keepDeletedSeconds = 0, nowMillis = 60_000)).containsExactly(old, fresh)
        assertThat(items.expiredDeleted(keepDeletedSeconds = KEEP_DELETED_FOREVER, nowMillis = Long.MAX_VALUE)).isEmpty()
    }

    @Test
    fun `a list can keep checked items in place or sort by name or due date, with the heading on top`() {
        val items = listOf(
            item("Bakery", isChecked = false, createdAt = 1, isHeader = true),
            item("pie", isChecked = true, createdAt = 2).copy(dueDay = 5),
            item("cake", isChecked = false, createdAt = 3),
            item("Bread", isChecked = false, createdAt = 4).copy(dueDay = 3),
        )

        assertThat(items.sortedForDisplay(checked = CheckedPlacement.IN_PLACE).map { it.id })
            .containsExactly("Bakery", "pie", "cake", "Bread").inOrder()
        assertThat(items.sortedForDisplay(ListSort.ALPHABETICAL).map { it.id })
            .containsExactly("Bakery", "Bread", "cake", "pie").inOrder()
        assertThat(items.sortedForDisplay(ListSort.DUE_DATE, CheckedPlacement.IN_PLACE).map { it.id })
            .containsExactly("Bakery", "Bread", "pie", "cake").inOrder()
    }

    @Test
    fun `a reminder keeps its time of day when it moves to another due day`() {
        val utc = ZoneId.of("UTC")
        val nineOnDay100 = 100 * 86_400_000L + 9 * 3_600_000L

        assertThat(moveReminder(nineOnDay100, toDay = 103, zone = utc)).isEqualTo(103 * 86_400_000L + 9 * 3_600_000L)
    }

    @Test
    fun `checked items go stale after the clearing time and never when it is off`() {
        val old = item("old", isChecked = true, createdAt = 1).copy(checkedAtMillis = 1_000)
        val items = listOf(old, old.copy(id = "fresh", checkedAtMillis = 50_000), old.copy(id = "open", isChecked = false))

        assertThat(items.staleChecked(clearCheckedSeconds = 10, nowMillis = 20_000)).containsExactly(old)
        assertThat(items.staleChecked(clearCheckedSeconds = 0, nowMillis = Long.MAX_VALUE)).isEmpty()
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
    }

    @Test
    fun `a new item for no section lands above the first heading`() {
        val bakery = item("Bakery", isChecked = false, createdAt = 10, isHeader = true)
        val butcher = item("Butcher", isChecked = false, createdAt = 30, isHeader = true)
        val loose = item("bag", isChecked = false, createdAt = 5)

        assertThat(orderAtEndOfSection(listOf(bakery, butcher), null)).isLessThan(10.0)
        assertThat(orderAtEndOfSection(listOf(loose, bakery, butcher), null)).isEqualTo(7.5)
        // A heading that someone deleted counts as no section.
        assertThat(orderAtEndOfSection(listOf(loose, bakery, butcher), "gone")).isEqualTo(7.5)
        assertThat(orderAtEndOfSection(listOf(loose), null)).isNull()
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

    @Test
    fun `accents, capitals and the final sigma do not tell two words apart`() {
        assertThat("Γάλα".folded()).isEqualTo("γαλα")
        assertThat("ΓΑΛΑ".folded()).isEqualTo("γαλα")
        assertThat("Πατάτες".folded()).isEqualTo("πατατεσ")
        assertThat("Crème Brûlée".folded()).isEqualTo("creme brulee")

        val items = listOf("ωμέγα", "Άλφα", "βήτα", "αλφάδι").mapIndexed { i, text ->
            item("$i", isChecked = false, createdAt = i.toLong()).copy(text = text)
        }
        assertThat(items.sortedForDisplay(ListSort.ALPHABETICAL).map { it.text })
            .containsExactly("Άλφα", "αλφάδι", "βήτα", "ωμέγα").inOrder()
        assertThat(items.sameAs("αλφα")?.text).isEqualTo("Άλφα")
        assertThat(items.sameAs("γάμμα")).isNull()
    }

    @Test
    fun `a pasted list becomes one text per line, without bullets and numbers`() {
        assertThat(pastedLines("- Milk\n• Bread\n\n1. Eggs\n2) Feta\n- [ ] Oil\n  plain  "))
            .containsExactly("Milk", "Bread", "Eggs", "Feta", "Oil", "plain").inOrder()
        assertThat(pastedLines("2 milk")).containsExactly("2 milk")
        assertThat(pastedLines("a\n".repeat(500))).hasSize(MAX_PASTED_LINES)
    }

    @Test
    fun `a number in the name of a new item says how many`() {
        assertThat(parseQuantity("2 milk")).isEqualTo(2 to "milk")
        assertThat(parseQuantity("3x γάλα")).isEqualTo(3 to "γάλα")
        assertThat(parseQuantity("milk x3")).isEqualTo(3 to "milk")
        assertThat(parseQuantity("γάλα χ2")).isEqualTo(2 to "γάλα")
        assertThat(parseQuantity("4 × eggs")).isEqualTo(4 to "eggs")
        // A weight is not a count, and neither is a year or a name with a digit in it.
        assertThat(parseQuantity("2 kg potatoes")).isEqualTo(1 to "2 kg potatoes")
        assertThat(parseQuantity("2 κιλά πατάτες")).isEqualTo(1 to "2 κιλά πατάτες")
        assertThat(parseQuantity("2027 calendar")).isEqualTo(1 to "2027 calendar")
        assertThat(parseQuantity("1 milk")).isEqualTo(1 to "1 milk")
        assertThat(parseQuantity("milk")).isEqualTo(1 to "milk")
        assertThat(parseQuantity("xbox")).isEqualTo(1 to "xbox")
        assertThat(parseQuantity("0x milk")).isEqualTo(1 to "0x milk")
    }

    @Test
    fun `a checked item that repeats comes back on the first day of its rhythm after the check`() {
        val monday = java.time.LocalDate.of(2026, 11, 23).toEpochDay()
        val utc = ZoneId.of("UTC")
        fun checkedOn(day: Long, repeat: Repeat?) = item("a", isChecked = true, createdAt = 1)
            .copy(dueDay = monday, repeat = repeat, checkedAtMillis = day * 86_400_000L + 3_600_000L)

        // Done early, on time and a week late: always the next Monday that is still ahead.
        assertThat(checkedOn(monday - 2, Repeat.WEEKLY).returnsOn(utc)).isEqualTo(monday + 7)
        assertThat(checkedOn(monday, Repeat.WEEKLY).returnsOn(utc)).isEqualTo(monday + 7)
        assertThat(checkedOn(monday + 9, Repeat.WEEKLY).returnsOn(utc)).isEqualTo(monday + 14)
        assertThat(checkedOn(monday, Repeat.DAILY).returnsOn(utc)).isEqualTo(monday + 1)
        // The 31st stays the 31st after a short month.
        val jan31 = java.time.LocalDate.of(2027, 1, 31).toEpochDay()
        assertThat(Repeat.MONTHLY.next(jan31, after = jan31)).isEqualTo(java.time.LocalDate.of(2027, 2, 28).toEpochDay())
        assertThat(Repeat.MONTHLY.next(jan31, after = jan31 + 40)).isEqualTo(java.time.LocalDate.of(2027, 3, 31).toEpochDay())
        assertThat(Repeat.YEARLY.next(monday, after = monday)).isEqualTo(java.time.LocalDate.of(2027, 11, 23).toEpochDay())

        // Open items, items without a rhythm and items without a day stay where they are.
        assertThat(checkedOn(monday, null).returnsOn(utc)).isNull()
        assertThat(checkedOn(monday, Repeat.WEEKLY).copy(isChecked = false).returnsOn(utc)).isNull()
        assertThat(checkedOn(monday, Repeat.WEEKLY).copy(dueDay = null).returnsOn(utc)).isNull()

        val items = listOf(checkedOn(monday, Repeat.WEEKLY), checkedOn(monday, Repeat.MONTHLY).copy(id = "b"))
        assertThat(items.returning(today = monday + 6, utc)).isEmpty()
        assertThat(items.returning(today = monday + 7, utc).map { (item, day) -> item.id to day }).containsExactly("a" to monday + 7)
        // It is not cleared with the other checked items while it waits.
        assertThat(items.staleChecked(clearCheckedSeconds = 1, nowMillis = Long.MAX_VALUE)).isEmpty()
    }

    @Test
    fun `progress counts open items out of all, without headings and deleted ones`() {
        val items = listOf(
            item("h", isChecked = false, createdAt = 0, isHeader = true),
            item("a", isChecked = false, createdAt = 1),
            item("b", isChecked = true, createdAt = 2),
            item("c", isChecked = false, createdAt = 3).copy(deletedAtMillis = 5),
        )
        assertThat(items.progress()).isEqualTo(ListProgress(open = 1, total = 2))
        assertThat(emptyList<ListItem>().progress()).isEqualTo(ListProgress(0, 0))
    }

    @Test
    fun `a list knows what it is set up for from its features`() {
        val list = SharedList("l", "s", "Shop", "🛒")
        assertThat(list.kind).isEqualTo(ListKind.EVERYTHING)
        assertThat(list.copy(features = ListKind.TODO.features).kind).isEqualTo(ListKind.TODO)
        assertThat(list.copy(features = emptySet()).kind).isEqualTo(ListKind.NOTES)
        assertThat(list.copy(features = setOf(ListFeature.PRICE)).kind).isEqualTo(ListKind.EVERYTHING)
    }

    @Test
    fun `items show the names their people use now, and keep the old one for someone who left`() {
        val names = mapOf("u1" to "Panos K", "u2" to "Maria")
        val item = item("milk", isChecked = true, createdAt = 1).copy(
            createdById = "u1", checkedById = "u2", checkedByName = "maria.p", claimedById = "u3", claimedByName = "Nikos",
        )

        val named = item.withNames(names)

        assertThat(named.createdByName).isEqualTo("Panos K")
        assertThat(named.checkedByName).isEqualTo("Maria")
        assertThat(named.claimedByName).isEqualTo("Nikos")
        // An unchecked item that still has a checker's id gets no name back.
        assertThat(item.copy(checkedByName = null).withNames(names).checkedByName).isNull()
        // Items from before ids were stored keep their name.
        assertThat(item.copy(createdById = null).withNames(names).createdByName).isEqualTo("Panos")
    }

    private fun item(id: String, isChecked: Boolean, createdAt: Long, isHeader: Boolean = false) =
        ListItem(id, id, isChecked, null, "Panos", null, null, createdAt, isHeader = isHeader)
}
