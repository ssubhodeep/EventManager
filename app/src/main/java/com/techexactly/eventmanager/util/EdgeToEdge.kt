package com.techexactly.eventmanager.util

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * This app targets an SDK level where edge-to-edge is enforced by the system (Android 15+ no
 * longer lets an app opt out), so every screen draws behind the status/navigation bars whether
 * we ask for it or not - which is exactly what made the toolbar render underneath the status bar
 * before this was wired up. [enableEdgeToEdge] makes that behavior consistent on older API
 * levels too and makes both bars fully transparent everywhere; [applyStatusBarInsets] and
 * [applyNavigationBarInsets] below are what actually keep toolbars, lists and buttons clear of
 * the bars instead of drawing underneath them.
 */
fun Activity.enableEdgeToEdge(lightStatusBarIcons: Boolean) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.isAppearanceLightStatusBars = lightStatusBarIcons
    controller.isAppearanceLightNavigationBars = lightStatusBarIcons
}

/** For toolbar-less screens whose top-of-screen background flips color with day/night. */
fun Activity.enableEdgeToEdgeForNightAwareBackground() {
    val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    enableEdgeToEdge(lightStatusBarIcons = !isNightMode)
}

/** Grows [view]'s top padding by the status bar inset, so its background can still reach the
 *  top edge while its content (a toolbar's title, a screen's first field) clears the status bar. */
fun applyStatusBarInsets(view: View) {
    val initialPadding = view.paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
        val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        v.updatePadding(top = initialPadding + statusBarInset)
        insets
    }
}

/** Grows [view]'s bottom padding by the navigation bar / gesture inset. */
fun applyNavigationBarInsets(view: View) {
    val initialPadding = view.paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
        val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        v.updatePadding(bottom = initialPadding + navBarInset)
        insets
    }
}
