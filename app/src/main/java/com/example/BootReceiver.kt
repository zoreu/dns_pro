package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i("BootReceiver", "Android TV restarted. Checking if DNS PRO should be restored...")
        val prefs = context.getSharedPreferences(DnsVpnService.PREFS_NAME, Context.MODE_PRIVATE)
        val wasActive = prefs.getBoolean(DnsVpnService.KEY_IS_ACTIVE, false)

        if (!wasActive) {
            Log.i("BootReceiver", "DNS PRO was not active at shutdown. Auto-start skipped.")
            return
        }

        if (VpnService.prepare(context) != null) {
            Log.w("BootReceiver", "VPN permission is not prepared after boot. Auto-start skipped safely.")
            return
        }

        Log.i("BootReceiver", "DNS PRO was active. Restoring VPN background service.")
        val serviceIntent = Intent(context, DnsVpnService::class.java).apply {
            action = DnsVpnService.ACTION_START
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("BootReceiver", "Failed to start DNS PRO service after boot.", e)
        }
    }
}
