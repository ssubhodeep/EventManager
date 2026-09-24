package com.techexactly.eventmanager.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Persists this device's FCM registration token to Firestore at
 * `users/{uid}/fcmTokens/{token}` - the address a server needs to push a notification to this
 * specific signed-in user's device. The scheduled Cloud Function in /functions reads from here
 * to send automatic event-reminder pushes; without this, [onNewToken][com.techexactly.eventmanager.notifications.EventFcmService.onNewToken]
 * would have nowhere durable to put the token, and a server would have no address to send to.
 */
class FcmTokenRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val messaging: FirebaseMessaging
) {

    /** No-ops (successfully) if nobody is signed in yet - the token gets synced again once
     *  [syncCurrentToken] runs after login (see MainActivity). */
    suspend fun saveToken(token: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        return try {
            firestore.collection("users").document(uid)
                .collection("fcmTokens").document(token)
                .set(mapOf("token" to token, "updatedAt" to System.currentTimeMillis()))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Called once a user is signed in (see MainActivity.onCreate) - covers the case where the
     *  token was already generated (or refreshed) before this device ever logged in, so
     *  onNewToken alone would have had no uid to save it under at the time. */
    suspend fun syncCurrentToken(): Result<Unit> = try {
        val token = messaging.token.await()
t        saveToken(token)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Called right before logout (see MainActivity) - without this, a signed-out device would
     *  stay registered under the account that just logged out, so on a shared device the next
     *  person to sign in (or the person who just signed out, before this device is re-synced
     *  under a new account) could still receive that account's reminder pushes. */
    suspend fun deleteCurrentToken(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        return try {
            val token = messaging.token.await()
            firestore.collection("users").document(uid)
                .collection("fcmTokens").document(token)
                .delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "FcmTokenRepository"
    }
}
