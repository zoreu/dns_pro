package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DnsVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var startInProgress: Boolean = false

    @Volatile
    private var stopInProgress: Boolean = false

    companion object {
        private const val TAG = "DnsVpnService"
        const val ACTION_START = "com.example.dns_pro.START"
        const val ACTION_STOP = "com.example.dns_pro.STOP"
        const val ACTION_STATUS_UPDATE = "com.example.dns_pro.STATUS_UPDATE"

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

        @Volatile
        var isRunning: Boolean = false

        private val IPV4_LITERAL = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

        fun normalizeDnsIp(value: String?): String? {
            val candidate = value?.trim().orEmpty()
            if (candidate.isEmpty()) return null

            val looksLikeIpLiteral = candidate.contains(':') || IPV4_LITERAL.matches(candidate)
            if (!looksLikeIpLiteral) return null

            return try {
                val address = InetAddress.getByName(candidate)
                when (address) {
                    is Inet4Address -> if (isValidIpv4(candidate)) address.hostAddress else null
                    is Inet6Address -> address.hostAddress
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }

        fun buildDnsServerList(
            primaryDns: String?,
            secondaryDns: String?,
            primaryDnsIpv6: String?,
            secondaryDnsIpv6: String?
        ): List<String> {
            return linkedSetOf(
                normalizeDnsIp(primaryDns),
                normalizeDnsIp(secondaryDns),
                normalizeDnsIp(primaryDnsIpv6),
                normalizeDnsIp(secondaryDnsIpv6)
            ).filterNotNull()
        }

        private fun isValidIpv4(ip: String): Boolean {
            val parts = ip.split('.')
            if (parts.size != 4) return false
            return parts.all { part ->
                if (part.isEmpty() || part.length > 3 || !part.all(Char::isDigit)) return@all false
                val number = part.toIntOrNull() ?: return@all false
                number in 0..255
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.i(TAG, "onStartCommand received action: $action")

        if (action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        startVpn()
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startVpn() {
        if (startInProgress) {
            Log.i(TAG, "Start ignored: VPN start already in progress.")
            return
        }

        if (vpnInterface != null) {
            isRunning = true
            sendStatusBroadcast(true)
            updateNotification("DNS PRO já está ativo")
            return
        }

        if (VpnService.prepare(this) != null) {
            Log.e(TAG, "VPN permission is not prepared. Service start aborted.")
            stopVpn()
            return
        }

        startInProgress = true
        stopInProgress = false

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Iniciando o DNS PRO..."))

        serviceScope.launch {
            try {
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val primaryDns = prefs.getString(KEY_PRIMARY_DNS, DnsProfile.Cloudflare.primaryDns)
                val secondaryDns = prefs.getString(KEY_SECONDARY_DNS, DnsProfile.Cloudflare.secondaryDns)
                val primaryDnsIpv6 = prefs.getString(KEY_PRIMARY_DNS_IPV6, DnsProfile.Cloudflare.primaryDnsIpv6)
                val secondaryDnsIpv6 = prefs.getString(KEY_SECONDARY_DNS_IPV6, DnsProfile.Cloudflare.secondaryDnsIpv6)
                val dohHost = prefs.getString(KEY_DOH_HOST, "").orEmpty().trim()
                val profileName = prefs.getString(KEY_PROFILE_NAME, DnsProfile.Cloudflare.name) ?: DnsProfile.Cloudflare.name

                val resolvedDnsList = buildDnsServerList(
                    primaryDns = primaryDns,
                    secondaryDns = secondaryDns,
                    primaryDnsIpv6 = primaryDnsIpv6,
                    secondaryDnsIpv6 = secondaryDnsIpv6
                ).ifEmpty {
                    listOf(DnsProfile.Cloudflare.primaryDns, DnsProfile.Cloudflare.secondaryDns)
                }

                if (dohHost.isNotEmpty()) {
                    Log.i(TAG, "DoH/Private DNS host saved for profile information only: $dohHost")
                }

                val builder = Builder()
                    .setSession("DNS PRO")
                    // Rota interna mínima para manter o perfil VPN local ativo sem capturar o tráfego de streaming.
                    .addAddress("10.0.99.1", 32)
                    .addRoute("10.0.99.0", 32)
                    .addAddress("fd00:99::1", 128)
                    .addRoute("fd00:99::1", 128)
                    .setMtu(1500)

                try {
                    builder.allowBypass()
                } catch (e: Exception) {
                    Log.w(TAG, "allowBypass() was not applied on this device.", e)
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
                        Log.e(TAG, "Invalid DNS server ignored: $dns", e)
                    }
                }

                if (dnsAddedCount == 0) {
                    builder.addDnsServer(DnsProfile.Cloudflare.primaryDns)
                    builder.addDnsServer(DnsProfile.Cloudflare.secondaryDns)
                }

                vpnInterface?.close()
                vpnInterface = builder.establish()

                if (vpnInterface != null) {
                    isRunning = true
                    prefs.edit().putBoolean(KEY_IS_ACTIVE, true).apply()
                    val activeDns = resolvedDnsList.firstOrNull() ?: DnsProfile.Cloudflare.primaryDns
                    updateNotification("DNS Ativo: $profileName - $activeDns")
                    sendStatusBroadcast(true)
                    Log.i(TAG, "VPN established. DNS mapped successfully: $resolvedDnsList")
                } else {
                    Log.e(TAG, "Builder.establish() returned null interface.")
                    stopVpn()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while starting DNS VPN service.", e)
                stopVpn()
            } finally {
                startInProgress = false
            }
        }
    }

    private fun stopVpn() {
        if (stopInProgress) return
        stopInProgress = true
        startInProgress = false

        Log.i(TAG, "Stopping DNS PRO Service...")
        isRunning = false

        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Exception while closing VPN interface.", e)
        }
        vpnInterface = null

        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_ACTIVE, false)
            .apply()

        sendStatusBroadcast(false)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {
            // Service may not have reached foreground yet.
        }

        stopSelf()
    }

    private fun sendStatusBroadcast(active: Boolean) {
        sendBroadcast(Intent(ACTION_STATUS_UPDATE).apply {
            setPackage(packageName)
            putExtra("active", active)
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "DNS PRO - Otimização de Rede",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantém o DNS PRO rodando no plano de fundo da Android TV"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(text: String): Notification {
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        val stopIntent = Intent(this, DnsVpnService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DNS PRO")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
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
