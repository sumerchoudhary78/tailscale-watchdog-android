package com.sumer.tailscalewatchdog

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Strategies in order:
 *   1. Broadcast intent to Tailscale's IPN receiver (works if Tailscale is alive).
 *   2. Launch Tailscale's main activity; combined with Always-on VPN this usually
 *      restarts the VPN service.
 *
 * Last-resort accessibility tap is added in a follow-up commit.
 */
class TailscaleReconnector(private val context: Context) {

    fun reconnect() {
        if (tryBroadcastIntent()) {
            Log.i(TAG, "Reconnect broadcast sent")
            return
        }
        if (tryLaunchActivity()) {
            Log.i(TAG, "Launched Tailscale activity")
            return
        }
        Log.e(TAG, "All reconnect strategies failed")
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
