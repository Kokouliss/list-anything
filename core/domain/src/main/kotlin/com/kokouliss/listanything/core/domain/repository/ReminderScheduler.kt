package com.kokouliss.listanything.core.domain.repository

import com.kokouliss.listanything.core.domain.model.ListRef

/** Rings this phone about an item at a chosen moment. Only the phone that set a reminder rings. */
interface ReminderScheduler {
    /** Replaces any earlier reminder for the item. */
    fun schedule(list: ListRef, itemId: String, atMillis: Long)

    fun cancel(itemId: String)
}
