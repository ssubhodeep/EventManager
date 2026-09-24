package com.techexactly.eventmanager.data.model

/**
 * A single event owned by a user.
 *
 * All properties have default values because the Firestore SDK uses a no-arg
 * constructor + reflection (`DocumentSnapshot.toObject()`) to deserialize documents.
 *
 * @property id Firestore document id (blank until the document has been written once).
 * @property title Required, short label for the event.
 * @property description Optional longer description.
 * @property dateTimeMillis Event date & time, stored as epoch millis (UTC) so it sorts
 *   and compares trivially; formatted for display with [com.techexactly.eventmanager.util.DateTimeUtils].
 * @property location Optional free-text location.
 * @property userId Owner's Firebase Auth uid. Redundant with the collection path
 *   (users/{uid}/events/{id}) but kept on the model for convenience in the UI layer.
 * @property createdAt Epoch millis when the event was first created (used for tie-breaks).
 * @property reminderSent Set by the scheduled Cloud Function in /functions once it has pushed a
 *   reminder for this event, so it isn't sent twice. Always written back to `false` by
 *   [toFirestoreMap] on every add/edit (a `.set()`, not a merge) - editing an event, e.g. moving
 *   its time, deliberately makes it eligible for a reminder again.
 */
data class Event(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val dateTimeMillis: Long = 0L,
    val location: String = "",
    val userId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val reminderSent: Boolean = false
) {
    val isUpcoming: Boolean
        get() = dateTimeMillis >= System.currentTimeMillis()

    /** Converts this event to a plain map for Firestore writes (keeps `id` out of the document body). */
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "title" to title,
        "description" to description,
        "dateTimeMillis" to dateTimeMillis,
        "location" to location,
        "userId" to userId,
        "createdAt" to createdAt,
        "reminderSent" to false
    )
}
