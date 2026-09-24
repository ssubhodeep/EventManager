package com.techexactly.eventmanager.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.techexactly.eventmanager.R
import com.techexactly.eventmanager.data.repository.AuthRepository
import com.techexactly.eventmanager.data.repository.FcmTokenRepository
import com.techexactly.eventmanager.databinding.ActivityMainBinding
import com.techexactly.eventmanager.ui.auth.LoginActivity
import com.techexactly.eventmanager.ui.dashboard.DashboardFragment
import com.techexactly.eventmanager.ui.events.EventListFragment
import com.techexactly.eventmanager.ui.events.EventListViewModel
import com.techexactly.eventmanager.ui.events.SyncStatus
import com.techexactly.eventmanager.util.applyNavigationBarInsets
import com.techexactly.eventmanager.util.applyStatusBarInsets
import com.techexactly.eventmanager.util.enableEdgeToEdge
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Post-login shell: hosts the two main destinations (Events, Dashboard) behind a
 * BottomNavigationView using plain FragmentTransactions - no separate Navigation Component
 * graph, since two flat destinations don't need one.
 *
 * Also owns the top-of-screen offline/syncing banner. It reads the *same* [EventListViewModel]
 * instance EventListFragment uses (scoped to this Activity, and picked up there via
 * activityViewModels()) so there's one Firestore listener behind both, not two.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val eventListViewModel: EventListViewModel by viewModels()

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var fcmTokenRepository: FcmTokenRepository

    private lateinit var eventListFragment: EventListFragment
    private lateinit var dashboardFragment: DashboardFragment
    private lateinit var activeFragment: Fragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(lightStatusBarIcons = false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        applyStatusBarInsets(binding.toolbar)
        applyNavigationBarInsets(binding.bottomNav)

        if (savedInstanceState == null) {
            eventListFragment = EventListFragment()
            dashboardFragment = DashboardFragment()
            supportFragmentManager.beginTransaction()
                .add(binding.fragmentContainer.id, dashboardFragment, TAG_DASHBOARD)
                .hide(dashboardFragment)
                .add(binding.fragmentContainer.id, eventListFragment, TAG_EVENTS)
                .commit()
            activeFragment = eventListFragment
            binding.toolbar.title = "Events"
        } else {
            // The FragmentManager already restored these fragment instances by tag; grab
            // the *same* instances rather than constructing new orphaned ones, otherwise
            // later show()/hide() calls below throw ("Fragment not added").
            eventListFragment = supportFragmentManager.findFragmentByTag(TAG_EVENTS) as EventListFragment
            dashboardFragment = supportFragmentManager.findFragmentByTag(TAG_DASHBOARD) as DashboardFragment
            activeFragment = if (dashboardFragment.isVisible) dashboardFragment else eventListFragment
            binding.toolbar.title = if (activeFragment === dashboardFragment) "Dashboard" else "Events"
        }

        binding.bottomNav.selectedItemId = if (activeFragment === dashboardFragment) R.id.nav_dashboard else R.id.nav_events

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_events -> {
                    showFragment(eventListFragment)
                    binding.toolbar.title = "Events"
                    true
                }
                R.id.nav_dashboard -> {
                    showFragment(dashboardFragment)
                    binding.toolbar.title = "Dashboard"
                    true
                }
                else -> false
            }
        }

        observeSyncStatus()
        syncFcmToken()
    }

    /** This device's current FCM token might have been generated before this user ever logged
     *  in (onNewToken alone would then have had no uid to save it under), so re-sync it here -
     *  the one place every signed-in session passes through, whether from a fresh login/signup
     *  or a persisted session skipping straight past the login screen. */
    private fun syncFcmToken() {
        lifecycleScope.launch { fcmTokenRepository.syncCurrentToken() }
    }

    private fun observeSyncStatus() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                eventListViewModel.syncStatus.collect { status -> renderSyncBanner(status) }
            }
        }
    }

    private fun renderSyncBanner(status: SyncStatus) {
        when {
            !status.isOnline -> showBanner(
                text = "You're offline — changes will sync automatically",
                backgroundColorRes = R.color.offline_bg,
                textColorRes = R.color.offline_text,
                iconColorRes = R.color.offline_icon
            )
            status.hasPendingWrites -> showBanner(
                text = "Syncing…",
                backgroundColorRes = R.color.syncing_bg,
                textColorRes = R.color.syncing_text,
                iconColorRes = R.color.syncing_text
            )
            else -> binding.syncBanner.visibility = View.GONE
        }
    }

    private fun showBanner(text: String, backgroundColorRes: Int, textColorRes: Int, iconColorRes: Int) {
        binding.syncBanner.visibility = View.VISIBLE
        binding.syncBanner.setBackgroundColor(ContextCompat.getColor(this, backgroundColorRes))
        binding.syncBannerText.text = text
        binding.syncBannerText.setTextColor(ContextCompat.getColor(this, textColorRes))
        binding.syncBannerIcon.imageTintList =
            android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, iconColorRes))
    }

    private fun showFragment(target: Fragment) {
        if (target === activeFragment) return
        supportFragmentManager.beginTransaction()
            .hide(activeFragment)
            .show(target)
            .commit()
        activeFragment = target
        invalidateOptionsMenu()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)

        val searchItem = menu.findItem(R.id.action_search)
        // Search only makes sense on the Events tab.
        searchItem.isVisible = activeFragment === eventListFragment

        (searchItem.actionView as? SearchView)?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true
            override fun onQueryTextChange(newText: String?): Boolean {
                eventListFragment.setSearchQuery(newText.orEmpty())
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_logout) {
            // Deregister this device's FCM token from the outgoing account *before* signing out
            // (deleteCurrentToken needs auth.currentUser to know whose fcmTokens to clean up) -
            // otherwise a shared device would keep receiving this account's reminder pushes
            // after logout.
            lifecycleScope.launch {
                fcmTokenRepository.deleteCurrentToken()
                authRepository.logout()
                startActivity(
                    Intent(this@MainActivity, LoginActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                finish()
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val TAG_EVENTS = "events"
        private const val TAG_DASHBOARD = "dashboard"
    }
}
