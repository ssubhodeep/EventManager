package com.techexactly.eventmanager.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.techexactly.eventmanager.data.model.Event
import com.techexactly.eventmanager.data.model.EventsSnapshot
import com.techexactly.eventmanager.data.model.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Single source of truth for event data. Everything is stored per-user at
 * `users/{uid}/events/{eventId}` so Firestore security rules can simply check
 * `request.auth.uid == uid` (see README for the matching rules snippet).
 *
 * [observeEvents] uses a real-time snapshot listener wrapped in [callbackFlow] so the
 * event list (and the dashboard stats derived from it) update live when a document is
 * added/edited/deleted - including by another device, and including from the local
 * offline cache before the write round-trips to the server. It listens with
 * [MetadataChanges.INCLUDE] specifically so the UI can tell "showing cached data" and
 * "still syncing a local write" apart from a genuine error, which is the core of making
 * this screen offline-first rather than just offline-tolerant.
 */
class EventRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Not signed in.")

    private fun eventsCollection(): CollectionReference =
        firestore.collection("users").document(requireUid()).collection("events")

    /** Real-time, reverse-chronological stream of the signed-in user's events. */
    fun observeEvents(): Flow<Resource<EventsSnapshot>> = callbackFlow {
        trySend(Resource.Loading)

        val registration = eventsCollection()
            .orderBy("dateTimeMillis", Query.Direction.DESCENDING)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.localizedMessage ?: "Failed to load events."))
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val events = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Event::class.java)?.copy(id = doc.id)
                }
                trySend(
                    Resource.Success(
                        EventsSnapshot(
                            events = events,
                            isFromCache = snapshot.metadata.isFromCache,
                            hasPendingWrites = snapshot.metadata.hasPendingWrites()
                        )
                    )
                )
            }

        awaitClose { registration.remove() }
    }

    suspend fun getEvent(eventId: String): Result<Event?> = try {
        val doc = eventsCollection().document(eventId).get().await()
        Result.success(doc.toObject(Event::class.java)?.copy(id = doc.id))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun addEvent(event: Event): Result<String> = try {
        val withOwner = event.copy(userId = requireUid())
        val ref = eventsCollection().add(withOwner.toFirestoreMap()).await()
        Result.success(ref.id)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun updateEvent(event: Event): Result<Unit> = try {
        require(event.id.isNotBlank()) { "Cannot update an event with no id." }
        val withOwner = event.copy(userId = requireUid())
        eventsCollection().document(event.id).set(withOwner.toFirestoreMap()).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Re-creates a specific event by id - used to restore an event after "Undo" on delete. */
    suspend fun restoreEvent(event: Event): Result<Unit> = updateEvent(event)

    suspend fun deleteEvent(eventId: String): Result<Unit> = try {
        eventsCollection().document(eventId).delete().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
