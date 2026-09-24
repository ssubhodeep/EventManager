package com.techexactly.eventmanager.ui.events

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.techexactly.eventmanager.databinding.ActivityAddEditEventBinding
import com.techexactly.eventmanager.util.ConnectivityObserver
import com.techexactly.eventmanager.util.DateTimeUtils
import com.techexactly.eventmanager.util.NotificationHelper
import com.techexactly.eventmanager.util.applyNavigationBarInsets
import com.techexactly.eventmanager.util.applyStatusBarInsets
import com.techexactly.eventmanager.util.enableEdgeToEdge
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AddEditEventActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditEventBinding
    private val viewModel: AddEditEventViewModel by viewModels()

    @Inject
    lateinit var connectivityObserver: ConnectivityObserver

    private var eventId: String? = null
    private var selectedDateTimeMillis: Long = 0L

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(lightStatusBarIcons = false)
        binding = ActivityAddEditEventBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyStatusBarInsets(binding.toolbar)
        applyNavigationBarInsets(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NotificationHelper.hasNotificationPermission(this)) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        eventId = intent.getStringExtra(EXTRA_EVENT_ID)
        binding.toolbar.title = if (eventId.isNullOrBlank()) "Add event" else "Edit event"
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.dateTimeInput.setOnClickListener { showDateTimePicker() }

        binding.saveButton.setOnClickListener {
            viewModel.save(
                eventId = eventId,
                title = binding.titleInput.text?.toString().orEmpty(),
                description = binding.descriptionInput.text?.toString().orEmpty(),
                location = binding.locationInput.text?.toString().orEmpty(),
                dateTimeMillis = selectedDateTimeMillis
            )
        }

        eventId?.let { viewModel.loadEvent(it) }

        observeState()
        observeConnectivity()
    }

    private fun showDateTimePicker() {
        val cal = DateTimeUtils.calendarFor(selectedDateTimeMillis)
        DatePickerDialog(
            this,
            { _, year, month, day ->
                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        selectedDateTimeMillis = DateTimeUtils.combine(year, month, day, hour, minute)
                        binding.dateTimeInput.setText(DateTimeUtils.formatForDisplay(selectedDateTimeMillis))
                    },
                    cal.get(java.util.Calendar.HOUR_OF_DAY),
                    cal.get(java.util.Calendar.MINUTE),
                    false
                ).show()
            },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis() - 1000
        }.show()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.loadedEvent.collect { event ->
                        event ?: return@collect
                        binding.titleInput.setText(event.title)
                        binding.descriptionInput.setText(event.description)
                        binding.locationInput.setText(event.location)
                        selectedDateTimeMillis = event.dateTimeMillis
                        binding.dateTimeInput.setText(DateTimeUtils.formatForDisplay(event.dateTimeMillis))
                    }
                }
                launch {
                    viewModel.uiState.collect { state ->
                        binding.progressBar.visibility = if (state is AddEditUiState.Loading) View.VISIBLE else View.GONE
                        binding.saveButton.isEnabled = state !is AddEditUiState.Loading

                        when (state) {
                            is AddEditUiState.Saved -> {
                                Toast.makeText(this@AddEditEventActivity, "Event saved", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                            is AddEditUiState.Error -> Toast.makeText(this@AddEditEventActivity, state.message, Toast.LENGTH_LONG).show()
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    /** Lets the form show an honest "you're offline" note - a save here still succeeds instantly
     *  against Firestore's local cache and syncs once the connection is back (see EventRepository). */
    private fun observeConnectivity() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                connectivityObserver.observe().collect { isOnline ->
                    binding.offlineBanner.visibility = if (isOnline) View.GONE else View.VISIBLE
                }
            }
        }
    }

    companion object {
        const val EXTRA_EVENT_ID = "extra_event_id"
    }
}
