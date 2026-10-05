package io.github.tavinathanson.alwaysblockphone

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import io.github.tavinathanson.alwaysblockphone.policy.Status

/**
 * Enforcement backend: when a friction app comes to the foreground without an active
 * session, send the user home and show the AlwaysBlockPhone screen instead.
 *
 * Privacy: the service is configured (res/xml/accessibility_service.xml) to receive only
 * window-state-change events and cannot read window content. It looks at one field,
 * the event's package name, keeps only the most recent one in memory, and never stores,
 * logs or sends anything derived from Accessibility.
 */
class BlockerService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val store by lazy { Store(this) }
    private var foregroundPackage: String? = null
    private val recheck = Runnable { foregroundPackage?.let(::enforce) }

    // Handler delays pause in deep sleep, so also recheck whenever the screen comes back.
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = recheck.run()
    }

    override fun onServiceConnected() {
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        })
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // The notification shade and other System UI windows float over the real foreground app.
        if (pkg == SYSTEM_UI) return
        foregroundPackage = pkg
        enforce(pkg)
    }

    private fun enforce(pkg: String) {
        handler.removeCallbacks(recheck)
        val rule = store.policy.getOrNull()?.frictionRuleFor(pkg) ?: return
        val now = System.currentTimeMillis()
        when (val status = store.status(rule, now)) {
            is Status.Active -> handler.postDelayed(recheck, status.until - now)
            else -> {
                performGlobalAction(GLOBAL_ACTION_HOME)
                startActivity(MainActivity.blockedIntent(this, rule.id))
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    private companion object {
        const val SYSTEM_UI = "com.android.systemui"
    }
}
