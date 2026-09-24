package com.techexactly.eventmanager.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.techexactly.eventmanager.util.NotificationHelper
import java.util.concurrent.TimeUnit

/**
 * Schedules an on-device reminder notification ~30 minutes before an event starts.
 * This is what actually fires reminders without needing any server component - call
 * [schedule] whenever an event is added/edited, and [cancel] when it's deleted.
 */
class EventReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: "Upcoming event"
        val location = inputData.getString(KEY_LOCATION).orEmpty()
        val message = if (location.isNotBlank()) "Starting soon at $location" else "Starting soon"
        val notificationId = inputData.getString(KEY_EVENT_ID)?.hashCode() ?: System.currentTimeMillis().toInt()

        NotificationHelper.showEventReminder(applicationContext, notificationId, title, message)
        return Result.success()
    }

    companion object {
        private const val KEY_EVENT_ID = "eventId"
        private const val KEY_TITLE = "title"
        private const val KEY_LOCATION = "location"
        private const val REMINDER_LEAD_TIME_MINUTES = 30L

        private fun workName(eventId: String) = "event_reminder_$eventId"

        fun schedule(context: Context, eventId: String, title: String, location: String, eventTimeMillis: Long) {
            val triggerAt = eventTimeMillis - TimeUnit.MINUTES.toMillis(REMINDER_LEAD_TIME_MINUTES)
            val delay = triggerAt - System.currentTimeMillis()
            if (delay <= 0) return // event is too soon (or in the past) for a lead-time reminder

            val data = workDataOf(
                KEY_EVENT_ID to eventId,
                KEY_TITLE to title,
                KEY_LOCATION to location
            )

            val request = OneTimeWorkRequestBuilder<EventReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .setConstraints(Constraints.Builder().build())
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(workName(eventId), ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context, eventId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(eventId))
        }
    }
}
