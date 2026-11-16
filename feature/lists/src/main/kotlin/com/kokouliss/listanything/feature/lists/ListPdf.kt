package com.kokouliss.listanything.feature.lists

import android.content.Context
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import java.io.File

// A4 in PostScript points, with a margin all round.
private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 48

/**
 * Writes the list as a PDF into the app's cache and returns the file, which the file provider can
 * hand to other apps. Lines wrap, and a page break never cuts a line in half.
 */
internal fun writeListPdf(context: Context, name: String, lines: List<Line>): File {
    val text = SpannableStringBuilder()
    lines.forEachIndexed { index, line ->
        if (index > 0) text.append(if (line.kind == Line.Kind.HEADER || line.kind == Line.Kind.TOTAL) "\n\n" else "\n")
        val start = text.length
        text.append(if (line.kind == Line.Kind.NOTE) "      ${line.text}" else line.text)
        val spans = when (line.kind) {
            Line.Kind.TITLE -> listOf(RelativeSizeSpan(1.6f), StyleSpan(Typeface.BOLD))
            Line.Kind.HEADER, Line.Kind.TOTAL -> listOf(StyleSpan(Typeface.BOLD))
            Line.Kind.NOTE -> listOf(StyleSpan(Typeface.ITALIC))
            Line.Kind.ITEM -> emptyList()
        }
        spans.forEach { text.setSpan(it, start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    }
    val paint = TextPaint().apply {
        isAntiAlias = true
        textSize = 12f
    }
    val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, PAGE_WIDTH - 2 * MARGIN).setLineSpacing(4f, 1f).build()

    val pdf = PdfDocument()
    var first = 0
    while (first < layout.lineCount) {
        val top = layout.getLineTop(first)
        var end = first + 1
        while (end < layout.lineCount && layout.getLineBottom(end) - top <= PAGE_HEIGHT - 2 * MARGIN) end++
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pdf.pages.size + 1).create())
        with(page.canvas) {
            clipRect(MARGIN, MARGIN, PAGE_WIDTH - MARGIN, MARGIN + layout.getLineBottom(end - 1) - top)
            translate(MARGIN.toFloat(), (MARGIN - top).toFloat())
            layout.draw(this)
        }
        pdf.finishPage(page)
        first = end
    }
    // Letters and digits of any language stay in the file name; the rest could confuse the receiving app.
    val safeName = name.replace(Regex("[^\\p{L}\\p{N} ]"), "").trim().ifEmpty { "list" }
    val file = File(context.cacheDir, "share/$safeName.pdf").apply { parentFile?.mkdirs() }
    file.outputStream().use { pdf.writeTo(it) }
    pdf.close()
    return file
}
