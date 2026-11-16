package com.kokouliss.listanything.feature.lists

import com.kokouliss.listanything.core.domain.model.ListItem
import com.kokouliss.listanything.core.domain.model.SharedList
import com.kokouliss.listanything.core.domain.model.totalCents
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

internal fun formatPrice(cents: Long, locale: Locale, currencyCode: String): String =
    NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance(currencyCode) }.format(cents / 100.0)

/** A price the way it is typed into the price field, with the comma or point of the language. */
internal fun priceInput(cents: Long, locale: Locale): String =
    "${cents / 100}${DecimalFormatSymbols.getInstance(locale).decimalSeparator}${(cents % 100).toString().padStart(2, '0')}"

internal fun formatDay(epochDay: Long, locale: Locale): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

/** The time of day of [millis] on this phone, like 18:30. */
internal fun formatTime(millis: Long, locale: Locale): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

/** One line of a list written out. The kind says how the line is set in a PDF. */
internal data class Line(val text: String, val kind: Kind) {
    enum class Kind { TITLE, HEADER, ITEM, NOTE, TOTAL }
}

/**
 * The list as it reads on paper: the title, each item with its box, quantity, price, due day and
 * link, sections as headings, notes under their item and the total at the end. [total] turns a
 * formatted amount into the total line, in the language of the phone.
 */
internal fun listAsLines(list: SharedList, items: List<ListItem>, locale: Locale, total: (String) -> String): List<Line> = buildList {
    add(Line("${list.emoji} ${list.name}", Line.Kind.TITLE))
    items.forEach { item ->
        if (item.isHeader) {
            add(Line(item.text, Line.Kind.HEADER))
        } else {
            val text = buildString {
                append(if (item.isChecked) "☑ " else "☐ ")
                append(item.text)
                if (item.quantity > 1) append(" ×").append(item.quantity)
                item.priceCents?.let { append("   ").append(formatPrice(it * item.quantity, locale, list.currency)) }
                item.dueDay?.let { append("   ").append(formatDay(it, locale)) }
                item.link?.let { append("   ").append(it) }
            }
            add(Line(text, Line.Kind.ITEM))
            item.note?.let { add(Line(it, Line.Kind.NOTE)) }
        }
    }
    items.totalCents()?.let { add(Line(total(formatPrice(it, locale, list.currency)), Line.Kind.TOTAL)) }
}

/** Plain text for the share sheet: headings and the total get a blank line above, notes are indented. */
internal fun List<Line>.asText(): String = joinToString("\n") { line ->
    when (line.kind) {
        Line.Kind.HEADER, Line.Kind.TOTAL -> "\n${line.text}"
        Line.Kind.NOTE -> "    ${line.text}"
        Line.Kind.TITLE, Line.Kind.ITEM -> line.text
    }
}
