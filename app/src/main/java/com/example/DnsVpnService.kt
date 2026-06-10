package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.IOException
import java.net.InetAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DnsVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "DnsVpnService"
        const val ACTION_START = "com.example.dns_pro.START"
        const val ACTION_STOP = "com.example.dns_pro.STOP"
        
        const val PREFS_NAME = "dns_pro_prefs"
        const val KEY_IS_ACTIVE = "dns_is_active"
        const val KEY_PRIMARY_DNS = "dns_primary"
        const val KEY_SECONDARY_DNS = "dns_secondary"
        const val KEY_PRIMARY_DNS_IPV6 = "dns_primary_ipv6"
        const val KEY_SECONDARY_DNS_IPV6 = "dns_secondary_ipv6"
        const val KEY_DOH_HOST = "dns_doh_host"
        const val KEY_PROFILE_NAME = "dns_profile_name"
        
        const val CHANNEL_ID = "dns_pro_channel"
        const val NOTIFICATION_ID = 4589

        // Fast static check for active service
        @Volatile
        var isRunning: Boolean = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.i(TAG, "onStartCommand received action: $action")
        if (action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        
        startVpn()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopVpn()
    }

    private fun startVpn() {
        createNotificationChannel()
        val notification = createNotification("Iniciando o DNS PRO...")
        startForeground(NOTIFICATION_ID, notification)
        isRunning = true

        serviceScope.launch {
            try {
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val primaryDns = prefs.getString(KEY_PRIMARY_DNS, "1.1.1.1") ?: "1.1.1.1"
                val secondaryDns = prefs.getString(KEY_SECONDARY_DNS, "1.0.0.1") ?: "1.0.0.1"
                val primaryDnsIpv6 = prefs.getString(KEY_PRIMARY_DNS_IPV6, "") ?: ""
                val secondaryDnsIpv6 = prefs.getString(KEY_SECONDARY_DNS_IPV6, "") ?: ""
                val dohHost = prefs.getString(KEY_DOH_HOST, "") ?: ""
                val profileName = prefs.getString(KEY_PROFILE_NAME, "Cloudflare DNS") ?: "Cloudflare DNS"

                val resolvedDnsList = mutableListOf<String>()

                //if (dohHost.isNotEmpty()) {
                //    // It is a DoH / Private DNS hostname, like dns.adguard.com
                //    try {
                //        Log.i(TAG, "Resolving Private DNS hostname: $dohHost...")
                //        val addresses = InetAddress.getAllByName(dohHost)
                //        for (addr in addresses) {
                //            resolvedDnsList.add(addr.hostAddress)
                //        }
                //        Log.i(TAG, "Private DNS hostname resolved to: $resolvedDnsList")
                //    } catch (e: Exception) {
                //        Log.e(TAG, "Failed to resolve hostname: $dohHost. Falling back to default IPs.", e)
                //        resolvedDnsList.add(primaryDns)
                //        resolvedDnsList.add(secondaryDns)
                //        if (primaryDnsIpv6.isNotEmpty()) resolvedDnsList.add(primaryDnsIpv6)
                //        if (secondaryDnsIpv6.isNotEmpty()) resolvedDnsList.add(secondaryDnsIpv6)
                //    }
                //} else {
                //    resolvedDnsList.add(primaryDns)
                //    resolvedDnsList.add(secondaryDns)
                //    if (primaryDnsIpv6.isNotEmpty()) resolvedDnsList.add(primaryDnsIpv6)
                //    if (secondaryDnsIpv6.isNotEmpty()) resolvedDnsList.add(secondaryDnsIpv6)
                //}
                resolvedDnsList.add(primaryDns)
                resolvedDnsList.add(secondaryDns)
                if (primaryDnsIpv6.isNotEmpty()) resolvedDnsList.add(primaryDnsIpv6)
                if (secondaryDnsIpv6.isNotEmpty()) resolvedDnsList.add(secondaryDnsIpv6)

                // Setup lightweight mock VPN Tunnel supporting both IPv4 and IPv6
                val builder = Builder()
                    .setSession("DNS PRO")
                    // Non-routable internal bridge range, avoids taking up real traffic
                    .addAddress("10.0.99.1", 32)
                    // Only route this internal IP to the interface. Avoids routing any real traffic.
                    .addRoute("10.0.99.0", 32)
                    // Non-routable internal IPv6 address to notify Android of IPv6 configuration routing
                    .addAddress("fd00:99::1", 128)
                    .addRoute("fd00:99::1", 128)
                    .setMtu(1500)

                try {
                    val allowBypassingMethod = builder.javaClass.getMethod("allowBypassing")
                    allowBypassingMethod.invoke(builder)
                    Log.i(TAG, "allowBypassing() called successfully to allow system Private DNS to bypass the VPN tunnel.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to invoke allowBypassing via reflection", e)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setMetered(false)
                }

                var dnsAddedCount = 0
                for (dns in resolvedDnsList) {
                    try {
                        builder.addDnsServer(dns)
                        dnsAddedCount++
                    } catch (e: Exception) {
                        Log.e(TAG, "Invalid IPv4 format: $dns", e)
                    }
                }

                // Ensure we have at least 1 dns server mapped
                if (dnsAddedCount == 0) {
                    builder.addDnsServer("1.1.1.1")
                    builder.addDnsServer("1.0.0.1")
                }

                vpnInterface?.close()
                vpnInterface = builder.establish()

                if (vpnInterface != null) {
                    Log.i(TAG, "VPN established. Target DNS mapped successfully: $resolvedDnsList")
                    prefs.edit().putBoolean(KEY_IS_ACTIVE, true).apply()
                    isRunning = true

                    val statusMsg = "DNS Ativo: $profileName - ${if (dohHost.isNotEmpty()) dohHost else primaryDns}"
                    updateNotification(statusMsg)

                    // Event broadcast
                    sendBroadcast(Intent("com.example.dns_pro.STATUS_UPDATE").apply {
                        putExtra("active", true)
                    })
                } else {
                    Log.e(TAG, "Builder.establish returned null interface")
                    stopVpn()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Exception in starting VPN service loop", e)
                stopVpn()
            }
        }
    }

    private fun stopVpn() {
        Log.i(TAG, "Stopping DNS PRO Service...")
        isRunning = false
        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Exception in closing VPN Interface", e)
        }
        vpnInterface = null

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_IS_ACTIVE, false).apply()

        // Event broadcast
        sendBroadcast(Intent("com.example.dns_pro.STATUS_UPDATE").apply {
            putExtra("active", false)
        })

        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "DNS PRO - Otimização de Rede",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantém o DNS PRO rodando no plano de fundo de sua Android TV"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(text: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, pendingIntentFlags
        )

        val stopIntent = Intent(this, DnsVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, pendingIntentFlags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DNS PRO")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass) // Navigation/Compass fits DNS tracking perfectly
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "PARAR DNS", stopPendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(text))
    }
}
