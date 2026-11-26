package com.kokouliss.listanything.feature.lists

import com.kokouliss.listanything.core.domain.model.ListItem
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class FinishesListTest {

    private fun item(id: String, checked: Boolean, isHeader: Boolean = false) =
        ListItem(id, id, checked, null, "Panos", null, null, 0, isHeader = isHeader)

    @Test
    fun theLastOpenItemOfALongEnoughListFinishesIt() {
        val items = listOf(item("h", false, isHeader = true), item("a", true), item("b", true), item("c", false))
        assertTrue(finishesList(items, "c"))
    }

    @Test
    fun notWhileSomethingElseIsOpen() {
        assertFalse(finishesList(listOf(item("a", true), item("b", false), item("c", false)), "c"))
    }

    @Test
    fun notOnAListOfTwo() {
        assertFalse(finishesList(listOf(item("a", true), item("b", false)), "b"))
    }
}
