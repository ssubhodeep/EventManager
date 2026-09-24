package com.techexactly.eventmanager

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestore
import com.techexactly.eventmanager.util.NotificationHelper
import dagger.hilt.android.HiltAndroidApp

/**
 * @HiltAndroidApp roots the Hilt dependency graph for the whole app - every @AndroidEntryPoint
 * Activity/Fragment and every @HiltViewModel below it resolves its dependencies (repositories,
 * ConnectivityObserver, the Firebase SDK instances from di/AppModule.kt) through the component
 * this generates.
 */
@HiltAndroidApp
class EventManagerApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Firestore actually enables persistent (disk) cache by default on Android, but the
        // assignment explicitly calls for offline persistence, so it's configured here
        // explicitly rather than relying on the default so it's easy to point to in review.
        // Using the Firebase.firestore accessor directly (rather than an injected instance)
        // keeps this independent of Hilt's field-injection timing in Application.onCreate();
        // it's still the same singleton di/AppModule.kt hands out everywhere else, since both
        // paths resolve to FirebaseFirestore.getInstance() under the hood.
        Firebase.firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()

        NotificationHelper.createNotificationChannel(this)
    }
}
