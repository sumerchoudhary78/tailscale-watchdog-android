package com.sumer.tailscalewatchdog

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager

/**
 * Wakes the screen, asks the system to dismiss a non-secure keyguard, then
 * hands off to TailscaleReconnector. On a secure (PIN/pattern/password)
 * keyguard, Android prompts the user — we can't bypass that.
 */
class UnlockAndLaunchActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var handedOff = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isKeyguardLocked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    Log.i(TAG, "keyguard dismissed")
                    handOff()
                }

                override fun onDismissError() {
                    Log.w(TAG, "keyguard dismiss error — proceeding anyway")
                    handOff()
                }

                override fun onDismissCancelled() {
                    Log.w(TAG, "keyguard dismiss cancelled (secure lock?) — proceeding anyway")
                    handOff()
                }
            })
            handler.postDelayed({ if (!handedOff) handOff() }, FALLBACK_TIMEOUT_MS)
        } else {
            handOff()
        }
    }

    private fun handOff() {
        if (handedOff) return
        handedOff = true
        TailscaleReconnector(applicationContext).reconnect()
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val TAG = "UnlockAndLaunch"
        const val FALLBACK_TIMEOUT_MS = 8_000L
    }
}
