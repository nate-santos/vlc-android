package org.videolan.vlc.gui.helpers

import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/** Dismisses the search IME without collapsing search or changing its query/results. */
class SearchKeyboardBackHandler(
    private val activity: ComponentActivity,
    private val searchView: () -> View
) : DefaultLifecycleObserver {
    private val decorView = activity.window.decorView
    private var searchActive = false
    private var observingLayout = false
    private var dismissingKeyboard = false
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { updateRegistration() }
    private val legacyCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = hideKeyboard()
    }
    private val overlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        OverlayCallback(activity) { hideKeyboard() } else null

    init {
        activity.onBackPressedDispatcher.addCallback(activity, legacyCallback)
        activity.lifecycle.addObserver(this)
    }

    fun setSearchActive(active: Boolean) {
        searchActive = active
        if (!active) dismissingKeyboard = false
        updateRegistration()
    }

    override fun onStart(owner: LifecycleOwner) {
        if (!observingLayout) {
            decorView.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
            observingLayout = true
        }
        updateRegistration()
    }

    override fun onStop(owner: LifecycleOwner) {
        dismissingKeyboard = false
        stopObservingLayout()
        setRegistered(false)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        stopObservingLayout()
        setRegistered(false)
        legacyCallback.remove()
        activity.lifecycle.removeObserver(this)
    }

    private fun stopObservingLayout() {
        if (observingLayout) {
            if (decorView.viewTreeObserver.isAlive)
                decorView.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
            observingLayout = false
        }
    }

    private fun updateRegistration() {
        // Observe the decor rather than replacing the activity's existing insets listener.
        val imeVisible = ViewCompat.getRootWindowInsets(decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        if (!imeVisible) dismissingKeyboard = false
        setRegistered(searchActive && imeVisible && !dismissingKeyboard &&
            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    private fun setRegistered(active: Boolean) {
        legacyCallback.isEnabled = active
        overlay?.setRegistered(active)
    }

    private fun hideKeyboard() {
        // Do not clear the query, collapse the action view, restore the list or finish.
        dismissingKeyboard = true
        val view = searchView()
        view.clearFocus()
        WindowInsetsControllerCompat(activity.window, view).hide(WindowInsetsCompat.Type.ime())
        UiTools.setKeyboardVisibility(view, false)
        // A later Back must follow the activity's normal navigation, not get trapped here.
        setRegistered(false)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private class OverlayCallback(
        private val activity: ComponentActivity,
        hideKeyboard: () -> Unit
    ) {
        private val callback = OnBackInvokedCallback { hideKeyboard() }
        private var registered = false

        fun setRegistered(register: Boolean) {
            if (registered == register) return
            if (register) {
                // Run ahead of the IME's callback, but only while the IME is visible.
                activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback
                )
            } else {
                activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
            }
            registered = register
        }
    }
}
