package com.kokouliss.listanything.core.domain.model

/**
 * A sketch page that everyone in the space draws on. Coordinates are fractions of the page width,
 * so every screen shows the same picture; the page is [PAGE_HEIGHT] wide-units tall.
 */
data class Drawing(
    val id: String,
    val spaceId: String,
    val title: String,
    val strokes: List<Stroke>,
    val createdByName: String,
)

/** One pen movement. [points] alternates x and y. [color] is ARGB. */
data class Stroke(val color: Int, val points: List<Float>)

data class DrawingRef(val spaceId: String, val drawingId: String)

/** Height of a page as a fraction of its width: a portrait sheet. */
const val PAGE_HEIGHT = 4f / 3f

/** Strokes a page holds before it is full. Keeps the document far under Firestore's 1 MiB. */
const val MAX_STROKES = 500

/** About how many points one stroke keeps. A long slow line is thinned, not cut. */
const val MAX_STROKE_POINTS = 400

const val MAX_DRAWING_TITLE_LENGTH = 60

/** Every n-th element so that about [max] remain, always keeping the first and the last. */
fun <T> List<T>.thinnedTo(max: Int): List<T> {
    if (size <= max) return this
    val step = (size + max - 1) / max
    return filterIndexed { index, _ -> index % step == 0 || index == lastIndex }
}
