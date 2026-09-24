package com.techexactly.eventmanager.data.model

/**
 * A batch of events plus Firestore's own metadata about where they came from, so the UI can
 * show an honest "syncing..." indicator instead of pretending every read is fresh from the
 * server. This is the offline-first signal: [isFromCache] is true whenever the data shown is
 * served from Firestore's on-device persistent cache rather than a confirmed server response
 * (which is the normal, expected state while offline - it's not an error).
 *
 * @property isFromCache true if this snapshot came from the local cache rather than the server.
 * @property hasPendingWrites true if this device has local writes that haven't reached the
 *   server yet (i.e. there's something queued up to sync once connectivity returns).
 */
data class EventsSnapshot(
    val events: List<Event>,
    val isFromCache: Boolean,
    val hasPendingWrites: Boolean
)
