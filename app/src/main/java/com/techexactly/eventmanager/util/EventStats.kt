package com.techexactly.eventmanager.util

import com.techexactly.eventmanager.data.model.Event
import java.util.TreeMap

data class DashboardStats(
    val total: Int,
    val upcoming: Int,
    val past: Int,
    val monthLabels: List<String>,
    val monthCounts: List<Int>
)

/**
 * Pure computation of dashboard stats from a list of events - deliberately free of any
 * Android/Firebase/ViewModel dependency so it can be unit tested directly (see
 * app/src/test/.../EventStatsTest.kt) without needing to fake a whole ViewModel + Firestore.
 * [com.techexactly.eventmanager.ui.dashboard.DashboardViewModel] is a thin wrapper that just
 * feeds the live Firestore stream into this function.
 */
object EventStats {

    fun compute(events: List<Event>, nowMillis: Long = System.currentTimeMillis()): DashboardStats {
        val upcoming = events.count { it.dateTimeMillis >= nowMillis }
        val past = events.size - upcoming

        // sortKey (yyyyMM) -> (display label, count); TreeMap keeps months in chronological order.
        val buckets = TreeMap<String, Pair<String, Int>>()
        for (event in events) {
            val key = DateTimeUtils.monthSortKey(event.dateTimeMillis)
            val label = DateTimeUtils.monthLabel(event.dateTimeMillis)
            val current = buckets[key]?.second ?: 0
            buckets[key] = label to (current + 1)
        }

        return DashboardStats(
            total = events.size,
            upcoming = upcoming,
            past = past,
            monthLabels = buckets.values.map { it.first },
            monthCounts = buckets.values.map { it.second }
        )
    }
}
