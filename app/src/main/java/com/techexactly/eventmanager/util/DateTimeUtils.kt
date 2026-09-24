package com.techexactly.eventmanager.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateTimeUtils {

    private fun displayFormat() = SimpleDateFormat("EEE, d MMM yyyy • h:mm a", Locale.getDefault())
    private fun monthKeyFormat() = SimpleDateFormat("MMM yyyy", Locale.getDefault())
    private fun monthSortFormat() = SimpleDateFormat("yyyyMM", Locale.getDefault())

    fun formatForDisplay(millis: Long): String =
        if (millis <= 0L) "" else displayFormat().format(Date(millis))

    /** Human label used to bucket events for the "events per month" chart, e.g. "Sep 2026". */
    fun monthLabel(millis: Long): String = monthKeyFormat().format(Date(millis))

    /** Sortable key (yyyyMM) so chart buckets can be ordered chronologically regardless of locale. */
    fun monthSortKey(millis: Long): String = monthSortFormat().format(Date(millis))

    /** Combines a date-picker result and a time-picker result into one epoch-millis timestamp. */
    fun combine(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(year, month, day, hour, minute, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun calendarFor(millis: Long): Calendar {
        val cal = Calendar.getInstance()
        if (millis > 0L) cal.timeInMillis = millis
        return cal
    }
}
