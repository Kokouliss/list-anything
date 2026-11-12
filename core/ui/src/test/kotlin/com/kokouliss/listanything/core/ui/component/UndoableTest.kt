package com.kokouliss.listanything.core.ui.component

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Test

class UndoableTest {

    private val written = mutableListOf<String>()
    private val undoable = Undoable<String>(CoroutineScope(Dispatchers.Unconfined)) { written += it }

    @Test
    fun undoWritesNothingAndAStaleKeepChangesNothing() {
        undoable.remove("a")
        val a = undoable.pending.value!!.token
        undoable.undo(a)
        assertNull(undoable.pending.value)

        undoable.remove("b")
        val b = undoable.pending.value!!.token
        undoable.keep(a)
        assertEquals(emptyList(), written)

        undoable.keep(b)
        undoable.keep(b)
        assertEquals(listOf("b"), written)
        assertNull(undoable.pending.value)
    }

    @Test
    fun aNewerRemovalWritesTheOlderOneAndFlushWritesTheRest() {
        undoable.remove("a")
        undoable.remove("b")
        assertEquals(listOf("a"), written)

        undoable.flush()
        assertEquals(listOf("a", "b"), written)
    }
}
