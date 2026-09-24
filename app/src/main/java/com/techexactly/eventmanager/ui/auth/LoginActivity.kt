package com.techexactly.eventmanager.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.techexactly.eventmanager.databinding.ActivityLoginBinding
import com.techexactly.eventmanager.ui.main.MainActivity
import com.techexactly.eventmanager.util.applyNavigationBarInsets
import com.techexactly.eventmanager.util.applyStatusBarInsets
import com.techexactly.eventmanager.util.enableEdgeToEdgeForNightAwareBackground
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate(); shows the branded splash (see
        // Theme.EventManager.Starting) while we decide whether there's already a signed-in
        // session, so the app never shows a blank frame on a cold, possibly-offline launch.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeForNightAwareBackground()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyStatusBarInsets(binding.root)
        applyNavigationBarInsets(binding.root)

        // Persisted login: FirebaseAuth keeps the session across app restarts on its own,
        // so if a user is already signed in we skip straight to MainActivity - no network
        // round trip needed, which is what makes this an offline-friendly cold start.
        if (viewModel.isLoggedIn) {
            goToMain()
            return
        }

        binding.loginButton.setOnClickListener {
            viewModel.login(
                binding.emailInput.text?.toString().orEmpty(),
                binding.passwordInput.text?.toString().orEmpty()
            )
        }

        binding.forgotPasswordText.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        binding.signupText.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        observeState()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility =
                        if (state is AuthUiState.Loading) android.view.View.VISIBLE else android.view.View.GONE
                    binding.loginButton.isEnabled = state !is AuthUiState.Loading

                    when (state) {
                        is AuthUiState.LoggedIn -> goToMain()
                        is AuthUiState.Error -> {
                            Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                            viewModel.consumeError()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
