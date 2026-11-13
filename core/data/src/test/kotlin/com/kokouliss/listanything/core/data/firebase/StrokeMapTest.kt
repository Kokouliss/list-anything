package com.kokouliss.listanything.core.data.firebase

import com.google.common.truth.Truth.assertThat
import com.kokouliss.listanything.core.domain.model.Stroke
import org.junit.Test

class StrokeMapTest {

    @Test
    fun `a stroke survives the trip through its stored shape unchanged`() {
        val stroke = Stroke(0xFF1F1F1F.toInt(), listOf(0.1234f, 0.5f, 0.9f, 1.3333f))
        val stored = stroke.toMap()

        assertThat(stored.toStroke()).isEqualTo(stroke)
        // Undo removes the stored value by equality, so a second encoding must match the first.
        assertThat(stored.toStroke()?.toMap()).isEqualTo(stored)
    }

    @Test
    fun `a stroke with a missing colour or a non-number point is dropped`() {
        assertThat(mapOf(Fields.POINTS to listOf(0.1)).toStroke()).isNull()
        assertThat(mapOf(Fields.COLOR to 1L, Fields.POINTS to listOf(0.1, "x")).toStroke()).isNull()
        assertThat("not a map".toStroke()).isNull()
    }
}
