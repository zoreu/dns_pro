package com.example

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DnsViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences(DnsVpnService.PREFS_NAME, Context.MODE_PRIVATE)

    var isVpnActive by mutableStateOf(DnsVpnService.isRunning)
        private set

    // Selected profile
    var selectedProfile by mutableStateOf(DnsProfile.Cloudflare)
        private set

    // Custom config inputs
    var customPrimary by mutableStateOf("")
    var customSecondary by mutableStateOf("")
    var customPrimaryIpv6 by mutableStateOf("")
    var customSecondaryIpv6 by mutableStateOf("")
    var customDohHost by mutableStateOf("")

    // High performance network latencies tracking
    val latencies = mutableStateMapOf<String, Long>()
    val testingStatus = mutableStateMapOf<String, Boolean>()

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.dns_pro.STATUS_UPDATE") {
                isVpnActive = DnsVpnService.isRunning
            }
        }
    }

    init {
        loadPreferences()
        registerReceiver()
        startPeriodicStatusCheck()
        testAllLatencies()
    }

    private fun registerReceiver() {
        try {
            val filter = IntentFilter("com.example.dns_pro.STATUS_UPDATE")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(statusReceiver, filter)
            }
        } catch (e: Exception) {
            // Fallback
        }
    }

    private fun startPeriodicStatusCheck() {
        viewModelScope.launch {
            while (true) {
                isVpnActive = DnsVpnService.isRunning
                delay(1000)
            }
        }
    }

    private fun loadPreferences() {
        val lastProfileName = prefs.getString(DnsVpnService.KEY_PROFILE_NAME, DnsProfile.Cloudflare.name)
        selectedProfile = DnsProfile.presets.firstOrNull { it.name == lastProfileName } ?: DnsProfile.Cloudflare

        customPrimary = prefs.getString(DnsVpnService.KEY_PRIMARY_DNS, "") ?: ""
        customSecondary = prefs.getString(DnsVpnService.KEY_SECONDARY_DNS, "") ?: ""
        customPrimaryIpv6 = prefs.getString(DnsVpnService.KEY_PRIMARY_DNS_IPV6, "") ?: ""
        customSecondaryIpv6 = prefs.getString(DnsVpnService.KEY_SECONDARY_DNS_IPV6, "") ?: ""
        customDohHost = prefs.getString(DnsVpnService.KEY_DOH_HOST, "") ?: ""
    }

    fun selectProfile(profile: DnsProfile) {
        selectedProfile = profile
        if (profile != DnsProfile.Custom) {
            customPrimary = profile.primaryDns
            customSecondary = profile.secondaryDns
            customPrimaryIpv6 = profile.primaryDnsIpv6
            customSecondaryIpv6 = profile.secondaryDnsIpv6
            customDohHost = profile.dohHost
        }
        saveCurrentProfileConfig(profile)
    }

    fun saveCustomConfig(primary: String, secondary: String, doh: String) {
        customPrimary = primary.trim()
        customSecondary = secondary.trim()
        customDohHost = doh.trim()
        if (selectedProfile == DnsProfile.Custom) {
            saveCurrentProfileConfig(DnsProfile.Custom)
        }
    }

    fun saveCustomConfigIpv6(primaryIpv6: String, secondaryIpv6: String) {
        customPrimaryIpv6 = primaryIpv6.trim()
        customSecondaryIpv6 = secondaryIpv6.trim()
        if (selectedProfile == DnsProfile.Custom) {
            saveCurrentProfileConfig(DnsProfile.Custom)
        }
    }

    private fun saveCurrentProfileConfig(profile: DnsProfile) {
        prefs.edit().apply {
            putString(DnsVpnService.KEY_PROFILE_NAME, profile.name)
            putString(DnsVpnService.KEY_PRIMARY_DNS, customPrimary)
            putString(DnsVpnService.KEY_SECONDARY_DNS, customSecondary)
            putString(DnsVpnService.KEY_PRIMARY_DNS_IPV6, customPrimaryIpv6)
            putString(DnsVpnService.KEY_SECONDARY_DNS_IPV6, customSecondaryIpv6)
            putString(DnsVpnService.KEY_DOH_HOST, customDohHost)
            putBoolean(DnsVpnService.KEY_IS_ACTIVE, isVpnActive)
            apply()
        }
    }

    fun toggleDns(onVpnPermissionRequired: () -> Unit) {
        if (isVpnActive) {
            // Instantly stop locally and statically to avoid periodic check race conditions
            DnsVpnService.isRunning = false
            isVpnActive = false

            // Stop service
            val stopIntent = Intent(context, DnsVpnService::class.java).apply {
                action = DnsVpnService.ACTION_STOP
            }
            context.stopService(stopIntent)
            prefs.edit().putBoolean(DnsVpnService.KEY_IS_ACTIVE, false).apply()
        } else {
            // First check if VPN authorization is required in parent activity
            val vpnIntent = VpnService.prepare(context)
            if (vpnIntent != null) {
                // Return and let UI activity trigger system dialog
                onVpnPermissionRequired()
                return
            }

            // Sync state synchronously first to avoid checks flickering before bind runs
            DnsVpnService.isRunning = true
            isVpnActive = true

            // Otherwise, we can launch immediately
            saveCurrentProfileConfig(selectedProfile)
            val startIntent = Intent(context, DnsVpnService::class.java).apply {
                action = DnsVpnService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(startIntent)
            } else {
                context.startService(startIntent)
            }
            prefs.edit().putBoolean(DnsVpnService.KEY_IS_ACTIVE, true).apply()
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            context.unregisterReceiver(statusReceiver)
        } catch (e: Exception) {
            // Ignored
        }
    }

    // Ping latencies for all profiles
    fun testAllLatencies() {
        DnsProfile.presets.forEach { profile ->
            if (profile != DnsProfile.Custom) {
                measureLatencyForProfile(profile)
            }
        }
    }

    fun measureLatencyForProfile(profile: DnsProfile) {
        val targetIp = profile.primaryDns
        val name = profile.name
        testingStatus[name] = true
        
        viewModelScope.launch(Dispatchers.IO) {
            var shortestTime = -1L
            // Perform 2 connection probe cycles to account for routing cold starts
            for (i in 1..2) {
                val start = System.currentTimeMillis()
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(targetIp, 53), 1000)
                    socket.close()
                    val duration = System.currentTimeMillis() - start
                    if (shortestTime == -1L || duration < shortestTime) {
                        shortestTime = duration
                    }
                } catch (e: Exception) {
                    try {
                        val inetAddress = InetAddress.getByName(targetIp)
                        if (inetAddress.isReachable(1000)) {
                            val duration = System.currentTimeMillis() - start
                            if (shortestTime == -1L || duration < shortestTime) {
                                shortestTime = duration
                            }
                        }
                    } catch (ex: Exception) {
                        // Keep time as -1
                    }
                }
            }
            
            viewModelScope.launch(Dispatchers.Main) {
                latencies[name] = shortestTime
                testingStatus[name] = false
            }
        }
    }
}
