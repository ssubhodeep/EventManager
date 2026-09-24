package com.techexactly.eventmanager.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.techexactly.eventmanager.data.repository.FcmTokenRepository
import com.techexactly.eventmanager.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles push notifications sent via Firebase Cloud Messaging.
 *
 * [onMessageReceived] is invoked by the OS for a *data* message whenever this process is alive,
 * whether the app is in the foreground, backgrounded, or the user is on some other screen
 * entirely - there's no separate "foreground" code path to maintain, because we always build and
 * show the notification ourselves here rather than letting the OS auto-display a "notification"
 * payload (which it only does while the app is backgrounded). That's also why the scheduled
 * Cloud Function in /functions sends data-only messages: it guarantees this method - and
 * therefore consistent notification styling - fires every time, in every app state.
 *
 * It can be exercised immediately via Firebase console > Engage > Messaging > "New notification"
 * targeting the app, or by sending a message through the Cloud Messaging API using a token
 * logged in [onNewToken] - though a real device address for automatic reminders comes from
 * [FcmTokenRepository], which persists it to Firestore so a server can find it.
 *
 * For reminders that work without any backend at all (e.g. no Cloud Functions deployed), see
 * [EventReminderWorker], which schedules a local notification on-device when an event is
 * created/edited.
 */
@AndroidEntryPoint
class EventFcmService : FirebaseMessagingService() {

    @Inject
    lateinit var fcmTokenRepository: FcmTokenRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Event reminder"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "You have an upcoming event."
        val notificationId = message.data["eventId"]?.hashCode() ?: System.currentTimeMillis().toInt()

        NotificationHelper.showEventReminder(
            context = applicationContext,
            notificationId = notificationId,
            title = title,
            message = body
        )
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
//        android.util.Log.d(TAG, "FCM token refreshed: $token")
        scope.launch { fcmTokenRepository.saveToken(token) }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val TAG = "EventFcmService"
    }
}
