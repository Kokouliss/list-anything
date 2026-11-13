package com.kokouliss.listanything.core.domain.repository

import com.kokouliss.listanything.core.domain.model.Drawing
import com.kokouliss.listanything.core.domain.model.DrawingRef
import com.kokouliss.listanything.core.domain.model.Stroke
import kotlinx.coroutines.flow.Flow

/** The shared drawings of a space. Writes apply on the device at once and sync later, like items. */
interface DrawingRepository {
    fun observeDrawings(spaceId: String): Flow<List<Drawing>>

    /** Emits null once the drawing is deleted or the user is no longer a member of its space. */
    fun observeDrawing(ref: DrawingRef): Flow<Drawing?>

    /** Returns the id of the new drawing, so it can be opened right away. */
    suspend fun createDrawing(spaceId: String, title: String): String

    suspend fun addStroke(ref: DrawingRef, stroke: Stroke)

    /** Takes [stroke] off the page again, whoever drew it. */
    suspend fun removeStroke(ref: DrawingRef, stroke: Stroke)

    suspend fun clearStrokes(ref: DrawingRef)

    suspend fun deleteDrawing(ref: DrawingRef)
}
