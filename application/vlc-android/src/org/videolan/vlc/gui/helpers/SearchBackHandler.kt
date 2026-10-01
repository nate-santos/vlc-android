package org.videolan.vlc.gui.helpers

import android.os.Build
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/** Gives an open search priority over the IME without changing normal Back navigation. */
class SearchBackHandler(
    private val activity: ComponentActivity,
    private val closeSearch: () -> Unit
) : DefaultLifecycleObserver {
    private val legacyCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = closeSearch()
    }
    private val overlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        OverlayCallback(activity, closeSearch) else null
    private var enabled = false

    init {
        activity.onBackPressedDispatcher.addCallback(activity, legacyCallback)
        activity.lifecycle.addObserver(this)
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        updateRegistration()
    }

    override fun onStart(owner: LifecycleOwner) = updateRegistration()

    override fun onStop(owner: LifecycleOwner) {
        legacyCallback.isEnabled = false
        overlay?.setRegistered(false)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        legacyCallback.remove()
        overlay?.setRegistered(false)
        activity.lifecycle.removeObserver(this)
    }

    private fun updateRegistration() {
        val active = enabled && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        legacyCallback.isEnabled = active
        overlay?.setRegistered(active)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private class OverlayCallback(
        private val activity: ComponentActivity,
        closeSearch: () -> Unit
    ) {
        private val callback = OnBackInvokedCallback { closeSearch() }
        private var registered = false

        fun setRegistered(register: Boolean) {
            if (registered == register) return
            if (register) {
                // Default-priority callbacks lose to the keyboard's Back callback.
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
