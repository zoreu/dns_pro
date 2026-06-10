package com.example

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.util.Log
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
import kotlinx.coroutines.launch

class DnsViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences(DnsVpnService.PREFS_NAME, Context.MODE_PRIVATE)

    var isVpnActive by mutableStateOf(DnsVpnService.isRunning)
        private set

    var selectedProfile by mutableStateOf(DnsProfile.Cloudflare)
        private set

    var customPrimary by mutableStateOf("")
    var customSecondary by mutableStateOf("")
    var customPrimaryIpv6 by mutableStateOf("")
    var customSecondaryIpv6 by mutableStateOf("")
    var customDohHost by mutableStateOf("")

    val latencies = mutableStateMapOf<String, Long>()
    val testingStatus = mutableStateMapOf<String, Boolean>()

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DnsVpnService.ACTION_STATUS_UPDATE) {
                isVpnActive = intent.getBooleanExtra("active", DnsVpnService.isRunning)
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
            val filter = IntentFilter(DnsVpnService.ACTION_STATUS_UPDATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(statusReceiver, filter)
            }
        } catch (e: Exception) {
            Log.w("DnsViewModel", "Unable to register DNS status receiver.", e)
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

        if (selectedProfile == DnsProfile.Custom) {
            customPrimary = prefs.getString(DnsVpnService.KEY_PRIMARY_DNS, "") ?: ""
            customSecondary = prefs.getString(DnsVpnService.KEY_SECONDARY_DNS, "") ?: ""
            customPrimaryIpv6 = prefs.getString(DnsVpnService.KEY_PRIMARY_DNS_IPV6, "") ?: ""
            customSecondaryIpv6 = prefs.getString(DnsVpnService.KEY_SECONDARY_DNS_IPV6, "") ?: ""
            customDohHost = prefs.getString(DnsVpnService.KEY_DOH_HOST, "") ?: ""
        } else {
            applyProfileValues(selectedProfile)
        }
    }

    fun selectProfile(profile: DnsProfile) {
        selectedProfile = profile
        if (profile != DnsProfile.Custom) {
            applyProfileValues(profile)
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

    private fun applyProfileValues(profile: DnsProfile) {
        customPrimary = profile.primaryDns
        customSecondary = profile.secondaryDns
        customPrimaryIpv6 = profile.primaryDnsIpv6
        customSecondaryIpv6 = profile.secondaryDnsIpv6
        customDohHost = profile.dohHost
    }

    private fun saveCurrentProfileConfig(profile: DnsProfile) {
        val primary = if (profile == DnsProfile.Custom) customPrimary else profile.primaryDns
        val secondary = if (profile == DnsProfile.Custom) customSecondary else profile.secondaryDns
        val primaryIpv6 = if (profile == DnsProfile.Custom) customPrimaryIpv6 else profile.primaryDnsIpv6
        val secondaryIpv6 = if (profile == DnsProfile.Custom) customSecondaryIpv6 else profile.secondaryDnsIpv6
        val dohHost = if (profile == DnsProfile.Custom) customDohHost else profile.dohHost

        prefs.edit().apply {
            putString(DnsVpnService.KEY_PROFILE_NAME, profile.name)
            putString(DnsVpnService.KEY_PRIMARY_DNS, primary)
            putString(DnsVpnService.KEY_SECONDARY_DNS, secondary)
            putString(DnsVpnService.KEY_PRIMARY_DNS_IPV6, primaryIpv6)
            putString(DnsVpnService.KEY_SECONDARY_DNS_IPV6, secondaryIpv6)
            putString(DnsVpnService.KEY_DOH_HOST, dohHost)
            putBoolean(DnsVpnService.KEY_IS_ACTIVE, isVpnActive)
            apply()
        }
    }

    fun toggleDns(
        onVpnPermissionRequired: () -> Unit,
        onStartRejected: (String) -> Unit = {}
    ) {
        if (isVpnActive) {
            DnsVpnService.isRunning = false
            isVpnActive = false

            val stopIntent = Intent(context, DnsVpnService::class.java).apply {
                action = DnsVpnService.ACTION_STOP
            }
            try {
                context.startService(stopIntent)
            } catch (_: Exception) {
                context.stopService(stopIntent)
            }
            prefs.edit().putBoolean(DnsVpnService.KEY_IS_ACTIVE, false).apply()
            return
        }

        val vpnIntent = VpnService.prepare(context)
        if (vpnIntent != null) {
            onVpnPermissionRequired()
            return
        }

        if (selectedProfile == DnsProfile.Custom && !hasValidCustomDns()) {
            onStartRejected("Informe pelo menos um DNS válido no modo personalizado.")
            return
        }

        saveCurrentProfileConfig(selectedProfile)
        val startIntent = Intent(context, DnsVpnService::class.java).apply {
            action = DnsVpnService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(startIntent)
        } else {
            context.startService(startIntent)
        }
    }

    private fun hasValidCustomDns(): Boolean {
        return DnsVpnService.buildDnsServerList(
            primaryDns = customPrimary,
            secondaryDns = customSecondary,
            primaryDnsIpv6 = customPrimaryIpv6,
            secondaryDnsIpv6 = customSecondaryIpv6
        ).isNotEmpty()
    }

    override fun onCleared() {
        try {
            context.unregisterReceiver(statusReceiver)
        } catch (_: Exception) {
            // Receiver already unregistered or not registered.
        }
        super.onCleared()
    }

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
            repeat(2) {
                val start = System.currentTimeMillis()
                try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(targetIp, 53), 1000)
                    }
                    val duration = System.currentTimeMillis() - start
                    if (shortestTime == -1L || duration < shortestTime) {
                        shortestTime = duration
                    }
                } catch (_: Exception) {
                    try {
                        val inetAddress = InetAddress.getByName(targetIp)
                        if (inetAddress.isReachable(1000)) {
                            val duration = System.currentTimeMillis() - start
                            if (shortestTime == -1L || duration < shortestTime) {
                                shortestTime = duration
                            }
                        }
                    } catch (_: Exception) {
                        // Keep latency as -1.
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
