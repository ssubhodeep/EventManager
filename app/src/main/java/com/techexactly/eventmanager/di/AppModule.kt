package com.techexactly.eventmanager.di

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.messaging
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for the app's Firebase SDK singletons. Everything else (repositories,
 * ConnectivityObserver, ViewModels) is constructor-injected directly via @Inject and needs
 * no entry here - this module exists only because FirebaseAuth/FirebaseFirestore/FirebaseMessaging
 * come from static factory functions (Firebase.auth / Firebase.firestore / Firebase.messaging)
 * rather than an @Inject constructor Hilt could call itself.
 *
 * Each of these resolves to its SDK's own getInstance() under the hood, which the Firebase SDK
 * itself caches per FirebaseApp - so this still hands out the same singleton EventManagerApp
 * configures (Firestore's offline persistence settings) before anything else touches it.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = Firebase.auth

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = Firebase.firestore

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = Firebase.messaging
}
