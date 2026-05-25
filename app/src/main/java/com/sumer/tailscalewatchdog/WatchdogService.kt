package com.sumer.tailscalewatchdog

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log

class WatchdogService : Service() {

    private lateinit var cm: ConnectivityManager
    private var callback: ConnectivityManager.NetworkCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pendingReconnect: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        ensureChannel()
        startForeground(NOTIF_ID, buildNotification("Watching VPN"))
        registerCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isVpnActive()) scheduleReconnect()
        return START_STICKY
    }

    override fun onDestroy() {
        callback?.let { cm.unregisterNetworkCallback(it) }
        callback = null
        cancelPendingReconnect()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerCallback() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "VPN up")
                cancelPendingReconnect()
                updateNotification("Tailscale connected")
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "VPN down")
                updateNotification("VPN down, scheduling reconnect")
                scheduleReconnect()
            }
        }
        callback = cb
        cm.registerNetworkCallback(request, cb)
    }

    private fun isVpnActive(): Boolean = cm.allNetworks.any { net ->
        val caps = cm.getNetworkCapabilities(net) ?: return@any false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun scheduleReconnect() {
        cancelPendingReconnect()
        val r = Runnable {
            if (isVpnActive()) return@Runnable
            Log.i(TAG, "Triggering Tailscale reconnect")
            triggerReconnect()
        }
        pendingReconnect = r
        handler.postDelayed(r, RECONNECT_DELAY_MS)
    }

    private fun triggerReconnect() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val needsWake = !pm.isInteractive || km.isKeyguardLocked

        if (needsWake) {
            wakeScreen(pm)
            val intent = Intent(this, UnlockAndLaunchActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION
                )
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "UnlockAndLaunchActivity start failed; falling back", e)
                TailscaleReconnector(applicationContext).reconnect()
            }
        } else {
            TailscaleReconnector(applicationContext).reconnect()
        }
    }

    private fun wakeScreen(pm: PowerManager) {
        @Suppress("DEPRECATION")
        val wl = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "$TAG:wake"
        )
        try {
            wl.acquire(WAKE_LOCK_MS)
        } catch (e: Exception) {
            Log.w(TAG, "wake lock failed", e)
        }
    }

    private fun cancelPendingReconnect() {
        pendingReconnect?.let { handler.removeCallbacks(it) }
        pendingReconnect = null
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Watchdog", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Tailscale Watchdog")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    companion object {
        const val TAG = "WatchdogService"
        const val CHANNEL_ID = "watchdog"
        const val NOTIF_ID = 1001
        const val RECONNECT_DELAY_MS = 15_000L
        const val WAKE_LOCK_MS = 10_000L
    }
}
