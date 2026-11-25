package com.kokouliss.listanything.core.ui.component

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class LoaderTest {

    @Test
    fun bulletsArriveOneAfterTheOtherAndLeaveTogether() {
        // A loop starts with nothing there and ends with nothing there, so it repeats without a jump.
        for (index in 0..2) assertEquals(0f, arrival(0f, index))
        assertEquals(1f, leaving(1f))
        // Part of the way in, the first bullet is ahead of the second, and the second of the third.
        assertTrue(arrival(0.2f, 0) > arrival(0.2f, 1) && arrival(0.2f, 1) > arrival(0.2f, 2))
        // No tick before its bullet is in place.
        for (index in 0..2) {
            for (step in 0..100) {
                val t = step / 100f
                if (ticked(t, index) > 0f) assertEquals(1f, arrival(t, index))
            }
        }
    }

    @Test
    fun aPhoneWithAnimationsOffShowsEverythingInPlace() {
        for (index in 0..2) {
            assertEquals(1f, arrival(LOADER_AT_REST, index))
            assertEquals(1f, ticked(LOADER_AT_REST, index))
        }
        assertEquals(0f, leaving(LOADER_AT_REST))
    }
}
