package com.kokouliss.listanything.widget

import com.kokouliss.listanything.core.domain.model.ListItem
import com.kokouliss.listanything.core.domain.model.ListSort
import com.kokouliss.listanything.core.domain.model.sortedForDisplay

/** What the home-screen widget shows of a list: the items still to do, in the list's own order. */
internal fun List<ListItem>.openItems(sort: ListSort): List<ListItem> =
    sortedForDisplay(sort).filter { !it.isChecked && !it.isDeleted && !it.isHeader }

/** The row text: "2 × Milk" when more than one is wanted, like the list's own rows. */
internal fun ListItem.widgetText(): String = if (quantity > 1) "$quantity × $text" else text
