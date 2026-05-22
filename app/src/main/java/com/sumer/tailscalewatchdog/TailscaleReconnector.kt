package com.sumer.tailscalewatchdog

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Reconnect strategies, run together:
 *   1. Broadcast to Tailscale's IPN receiver (no-op if it died).
 *   2. Arm the accessibility service, then launch Tailscale's activity.
 *      When the window appears, the a11y service taps Connect.
 *
 * If the user has not enabled the a11y service, step 2 still launches
 * Tailscale; combined with Always-on VPN this often re-establishes the
 * tunnel without a tap.
 */
class TailscaleReconnector(private val context: Context) {

    fun reconnect() {
        val broadcasted = tryBroadcastIntent()
        val armed = TailscaleAccessibilityService.instance?.also { it.armReconnect() } != null
        val launched = tryLaunchActivity()
        Log.i(TAG, "reconnect: broadcast=$broadcasted a11y_armed=$armed launched=$launched")
    }

    private fun tryBroadcastIntent(): Boolean = try {
        val intent = Intent("com.tailscale.ipn.CONNECT_VPN").apply {
            setPackage(TAILSCALE_PKG)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        context.sendBroadcast(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "broadcast failed", e)
        false
    }

    private fun tryLaunchActivity(): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(TAILSCALE_PKG)
            ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return try {
            context.startActivity(launch)
            true
        } catch (e: Exception) {
            Log.w(TAG, "launch failed", e)
            false
        }
    }

    companion object {
        const val TAG = "TailscaleReconnector"
        const val TAILSCALE_PKG = "com.tailscale.ipn"
    }
}
