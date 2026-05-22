package com.sumer.tailscalewatchdog

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1
            )
        }

        val status = findViewById<TextView>(R.id.status)
        val startBtn = findViewById<Button>(R.id.startBtn)
        val stopBtn = findViewById<Button>(R.id.stopBtn)

        val tailscaleInstalled = try {
            packageManager.getPackageInfo(TailscaleReconnector.TAILSCALE_PKG, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

        status.text = if (tailscaleInstalled) {
            getString(R.string.status_ready)
        } else {
            getString(R.string.status_no_tailscale)
        }

        startBtn.isEnabled = tailscaleInstalled
        startBtn.setOnClickListener {
            ContextCompat.startForegroundService(
                this, Intent(this, WatchdogService::class.java)
            )
            status.text = getString(R.string.status_running)
        }
        stopBtn.setOnClickListener {
            stopService(Intent(this, WatchdogService::class.java))
            status.text = getString(R.string.status_stopped)
        }
    }
}
