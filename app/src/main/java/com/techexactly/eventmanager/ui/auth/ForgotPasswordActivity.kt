package com.techexactly.eventmanager.ui.auth

import android.os.Bundle
import android.view.View
import com.google.android.material.snackbar.Snackbar
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.techexactly.eventmanager.databinding.ActivityForgotPasswordBinding
import com.techexactly.eventmanager.util.applyNavigationBarInsets
import com.techexactly.eventmanager.util.applyStatusBarInsets
import com.techexactly.eventmanager.util.enableEdgeToEdgeForNightAwareBackground
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeForNightAwareBackground()
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyStatusBarInsets(binding.root)
        applyNavigationBarInsets(binding.root)

        binding.resetButton.setOnClickListener {
            viewModel.resetPassword(binding.emailInput.text?.toString().orEmpty())
        }

        binding.backToLoginText.setOnClickListener { finish() }

        observeState()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility = if (state is AuthUiState.Loading) View.VISIBLE else View.GONE
                    binding.resetButton.isEnabled = state !is AuthUiState.Loading

                    when (state) {
                        is AuthUiState.ResetEmailSent -> {
                            Snackbar.make(binding.root, "Reset link sent. Check your inbox.", Snackbar.LENGTH_LONG).show()
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
