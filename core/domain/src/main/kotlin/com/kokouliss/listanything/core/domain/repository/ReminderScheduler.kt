package com.kokouliss.listanything.core.domain.repository

import com.kokouliss.listanything.core.domain.model.ListRef

/**
 * Rings this phone about an item at a chosen moment, and once a day about everything that is due.
 * The other phones of a space hear about an item's reminder from firebase/functions.
 */
interface ReminderScheduler {
    /** Replaces any earlier reminder for the item. */
    fun schedule(list: ListRef, itemId: String, atMillis: Long)

    fun cancel(itemId: String)

    /** The hour of the day, 0 to 23, of the daily reminder about due items. */
    val dailyHour: Int

    /** Moves the daily reminder to [hour], from its next time on. */
    fun setDailyHour(hour: Int)
}
