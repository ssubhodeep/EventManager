package com.techexactly.eventmanager.data.repository

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Wraps [FirebaseAuth] behind a small suspend-function API and turns its checked
 * exceptions into user-readable messages, so the ViewModel/UI layer never has to know
 * about Firebase exception types.
 */
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth
) {

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val isLoggedIn: Boolean
        get() = auth.currentUser != null

    suspend fun signUp(email: String, password: String): Result<FirebaseUser> = try {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: return Result.failure(IllegalStateException("Sign up failed. Please try again."))
        Result.success(user)
    } catch (e: Exception) {
        Result.failure(Exception(mapAuthError(e)))
    }

    suspend fun login(email: String, password: String): Result<FirebaseUser> = try {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: return Result.failure(IllegalStateException("Login failed. Please try again."))
        Result.success(user)
    } catch (e: Exception) {
        Result.failure(Exception(mapAuthError(e)))
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = try {
        auth.sendPasswordResetEmail(email.trim()).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(Exception(mapAuthError(e)))
    }

    fun logout() = auth.signOut()

    /** Firebase throws several typed exceptions with unfriendly default messages - map the common ones. */
    private fun mapAuthError(e: Exception): String = when (e) {
        is FirebaseNetworkException -> "No internet connection. Sign-in needs a connection the first time; your events will still work offline once you're signed in."
        is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password."
        is FirebaseAuthInvalidUserException -> "No account found for that email."
        is FirebaseAuthUserCollisionException -> "An account already exists with that email."
        is FirebaseAuthWeakPasswordException -> "Password is too weak. Use at least 6 characters."
        else -> e.localizedMessage ?: "Something went wrong. Please check your connection and try again."
    }
}
