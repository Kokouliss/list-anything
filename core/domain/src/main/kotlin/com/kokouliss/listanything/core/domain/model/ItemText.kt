package com.kokouliss.listanything.core.domain.model

import java.text.Normalizer

private val ACCENTS = Regex("\\p{Mn}+")

/**
 * The text as search, suggestions and sorting compare it: no accents, no capitals and one kind of
 * sigma, so "Γάλα", "γαλα" and "ΓΑΛΑ" are the same word.
 */
fun String.folded(): String =
    ACCENTS.replace(Normalizer.normalize(this, Normalizer.Form.NFD), "").lowercase().replace('ς', 'σ')

/** The most lines one paste turns into items. */
const val MAX_PASTED_LINES = 100

// A dash or dot, a "[ ]" box and a "1." in front of a line, as lists copied from notes and chats have.
private val BULLET = Regex("^\\s*(?:[-*•·–—]\\s+)?(?:\\[[ xX]?]\\s+)?(?:\\d{1,3}[.)]\\s+)?")

/**
 * The lines of a pasted text as item texts, without bullets, numbering and empty lines. Fewer than
 * two means there is nothing to split.
 */
fun pastedLines(text: String): List<String> = text.lineSequence()
    .map { BULLET.replaceFirst(it, "").trim().take(MAX_ITEM_LENGTH) }
    .filter { it.isNotEmpty() }
    .take(MAX_PASTED_LINES)
    .toList()

// Weights and sizes that follow a number without being what is counted: "2 kg potatoes" is one item.
private val UNITS = setOf(
    "g", "gr", "kg", "ml", "l", "lt", "cl", "oz", "lb", "m", "cm", "mm",
    "γρ", "γρ.", "κιλο", "κιλα", "λιτρο", "λιτρα", "τεμ", "τεμ.",
)

// The times sign as people type it: x, × or the Greek χ.
private val TIMES_FIRST = Regex("^(\\d{1,3})\\s*[x×χ]\\s+(.+)$", RegexOption.IGNORE_CASE)
private val TIMES_LAST = Regex("^(.+?)\\s+[x×χ]\\s*(\\d{1,3})$", RegexOption.IGNORE_CASE)
private val NUMBER_FIRST = Regex("^(\\d{1,3})\\s+(.+)$")

/**
 * Reads how many from the name of a new item: "2 milk", "2x milk" and "milk x2" are two of "milk".
 * Everything else is one of itself.
 */
// shortcut: a bare number in front counts unless a unit follows, so "7 Up" becomes seven of "Up".
// The row shows "7 × Up" at once and the item sheet puts it right. Drop the bare form if it bites.
fun parseQuantity(text: String): Pair<Int, String> {
    val name = text.trim()
    val (count, rest) = TIMES_FIRST.matchEntire(name)?.destructured?.let { (count, rest) -> count to rest }
        ?: TIMES_LAST.matchEntire(name)?.destructured?.let { (rest, count) -> count to rest }
        ?: NUMBER_FIRST.matchEntire(name)?.destructured
            ?.takeIf { (count, rest) -> count.toInt() > 1 && rest.substringBefore(' ').folded() !in UNITS }
            ?.let { (count, rest) -> count to rest }
        ?: return 1 to name
    val quantity = count.toInt()
    return if (quantity in 1..MAX_QUANTITY) quantity to rest.trim() else 1 to name
}
