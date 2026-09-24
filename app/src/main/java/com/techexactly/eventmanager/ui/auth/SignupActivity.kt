package com.techexactly.eventmanager.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.google.android.material.snackbar.Snackbar
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.techexactly.eventmanager.databinding.ActivitySignupBinding
import com.techexactly.eventmanager.ui.main.MainActivity
import com.techexactly.eventmanager.util.applyNavigationBarInsets
import com.techexactly.eventmanager.util.applyStatusBarInsets
import com.techexactly.eventmanager.util.enableEdgeToEdgeForNightAwareBackground
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeForNightAwareBackground()
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyStatusBarInsets(binding.root)
        applyNavigationBarInsets(binding.root)

        binding.signupButton.setOnClickListener {
            viewModel.signup(
                binding.emailInput.text?.toString().orEmpty(),
                binding.passwordInput.text?.toString().orEmpty(),
                binding.confirmPasswordInput.text?.toString().orEmpty()
            )
        }

        binding.loginText.setOnClickListener { finish() }

        observeState()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility = if (state is AuthUiState.Loading) View.VISIBLE else View.GONE
                    binding.signupButton.isEnabled = state !is AuthUiState.Loading

                    when (state) {
                        is AuthUiState.LoggedIn -> {
                            startActivity(
                                Intent(this@SignupActivity, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                            finish()
                        }
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
}
