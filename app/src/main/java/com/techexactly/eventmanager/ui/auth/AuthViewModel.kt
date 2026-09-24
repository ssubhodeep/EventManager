package com.techexactly.eventmanager.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techexactly.eventmanager.data.repository.AuthRepository
import com.techexactly.eventmanager.util.Validators
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Shared UI-state contract for Login/Signup/ForgotPassword - each screen just interprets
 * [ResetEmailSent] as "N/A" for itself. */
sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    data object LoggedIn : AuthUiState()
    data object ResetEmailSent : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

/**
 * One AuthViewModel class backs all three auth screens (Login/Signup/ForgotPassword) since
 * they share the same shape of work: validate input, call [AuthRepository], expose a single
 * result state. Each Activity gets its own instance (default ViewModelProvider scoping), so
 * there's no cross-screen state leakage.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    val isLoggedIn: Boolean
        get() = repository.isLoggedIn

    fun login(email: String, password: String) {
        Validators.emailError(email)?.let { _uiState.value = AuthUiState.Error(it); return }
        if (password.isBlank()) { _uiState.value = AuthUiState.Error("Password is required"); return }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            repository.login(email, password)
                .onSuccess { _uiState.value = AuthUiState.LoggedIn }
                .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Login failed") }
        }
    }

    fun signup(email: String, password: String, confirmPassword: String) {
        Validators.emailError(email)?.let { _uiState.value = AuthUiState.Error(it); return }
        Validators.passwordError(password)?.let { _uiState.value = AuthUiState.Error(it); return }
        Validators.confirmPasswordError(password, confirmPassword)?.let { _uiState.value = AuthUiState.Error(it); return }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            repository.signUp(email, password)
                .onSuccess { _uiState.value = AuthUiState.LoggedIn }
                .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Sign up failed") }
        }
    }

    fun resetPassword(email: String) {
        Validators.emailError(email)?.let { _uiState.value = AuthUiState.Error(it); return }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            repository.sendPasswordReset(email)
                .onSuccess { _uiState.value = AuthUiState.ResetEmailSent }
                .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Could not send reset email") }
        }
    }

    fun consumeError() {
        if (_uiState.value is AuthUiState.Error) _uiState.value = AuthUiState.Idle
    }
}
