package com.sumer.tailscalewatchdog

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Last-resort recovery: when the watchdog arms this service and launches
 * Tailscale's activity, this service receives window events for
 * com.tailscale.ipn, finds the Connect button, and clicks it.
 *
 * The arm flag auto-clears after ARM_TIMEOUT_MS so a long-armed service
 * never clicks unexpectedly on a future Tailscale window the user opens
 * themselves.
 */
class TailscaleAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var disarmRunnable: Runnable? = null

    @Volatile
    private var armed = false

    override fun onServiceConnected() {
        Log.i(TAG, "a11y service connected")
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!armed) return
        if (event?.packageName != TailscaleReconnector.TAILSCALE_PKG) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val root = rootInActiveWindow ?: return
        val connectNode = findConnectButton(root) ?: return
        Log.i(TAG, "Tapping Connect node")
        connectNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        disarm()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun armReconnect() {
        armed = true
        disarmRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable { armed = false; disarmRunnable = null }
        disarmRunnable = r
        handler.postDelayed(r, ARM_TIMEOUT_MS)
    }

    private fun disarm() {
        armed = false
        disarmRunnable?.let { handler.removeCallbacks(it) }
        disarmRunnable = null
    }

    private fun findConnectButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (label in CONNECT_LABELS) {
            val matches = node.findAccessibilityNodeInfosByText(label) ?: continue
            for (m in matches) {
                val clickable = ascendToClickable(m)
                if (clickable != null) return clickable
            }
        }
        return null
    }

    private fun ascendToClickable(start: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (start.isClickable) return start
        var p: AccessibilityNodeInfo? = start.parent
        var hops = 0
        while (p != null && hops < MAX_PARENT_HOPS) {
            if (p.isClickable) return p
            p = p.parent
            hops++
        }
        return null
    }

    companion object {
        const val TAG = "TailscaleA11y"
        const val ARM_TIMEOUT_MS = 20_000L
        const val MAX_PARENT_HOPS = 6
        val CONNECT_LABELS = listOf(
            "Connect", "Reconnect", "Sign in", "Start", "Turn on"
        )

        @Volatile
        var instance: TailscaleAccessibilityService? = null
            private set
    }
}
