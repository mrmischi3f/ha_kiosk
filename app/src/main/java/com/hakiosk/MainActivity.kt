package com.hakiosk

import android.app.ActivityManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.hakiosk.ui.KioskRoot
import com.hakiosk.ui.LocalAppContainer
import com.hakiosk.ui.theme.KioskTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        val container = appContainer

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                container.settings
                    .filter { it.loaded }
                    .map { it.lockTask }
                    .distinctUntilChanged()
                    .collect { applyLockTask(it) }
            }
        }

        setContent {
            KioskTheme {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    KioskRoot()
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    /**
     * Screen pinning. Without device-owner provisioning Android shows a one-time confirmation;
     * if the app is provisioned as device owner (via adb / MDM) pinning becomes silent and unescapable.
     */
    private fun applyLockTask(enabled: Boolean) {
        runCatching {
            val am = getSystemService(ActivityManager::class.java)
            val locked = am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
            if (enabled && !locked) startLockTask()
            if (!enabled && locked) stopLockTask()
        }
    }
}
