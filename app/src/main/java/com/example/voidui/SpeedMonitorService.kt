package com.example.voidui

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.*
import java.text.DecimalFormat

class SpeedMonitorService : Service() {

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var speedCalculator: NetworkSpeedCalculator
    private lateinit var notificationHelper: NotificationHelper
    private var isMonitoring = false

    override fun onCreate() {
        super.onCreate()
        speedCalculator = NetworkSpeedCalculator()
        notificationHelper = NotificationHelper(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isMonitoring) {
            isMonitoring = true
            startForeground(
                NotificationHelper.NOTIFICATION_ID_NET_SAT,
                notificationHelper.buildNetStatNotification("0", "0", "0", "0", "")
            )
            startSpeedMonitoring()

            requestIgnoreBatteryOptimizations()
        }
        return START_STICKY
    }

    private fun startSpeedMonitoring() {
        coroutineScope.launch {
            while (isActive) {
                val (downloadSpeed, uploadSpeed) = speedCalculator.getNetworkSpeed()
                val downStr = formatSpeed(downloadSpeed)
                val upStr = formatSpeed(uploadSpeed)
                val mobileDataBytes = NetworkUsageHelper(applicationContext).getMobileDataUsageToday(applicationContext)
                val wifiDataBytes = NetworkUsageHelper(applicationContext).getWiFiUsageToday(applicationContext)
                val mobileDataUsageFormatted = formatData(mobileDataBytes)
                val wifiDataUsageFormatted = formatData(wifiDataBytes)

                val wifiRssi = getWifiSignalStrength(applicationContext)
                val wifiText = wifiRssi?.let { "Signal $it dBm" } ?: ""
//                Log.d("SpeedMonitorService", "Signal Strength: $wifiText")

                val updatedNotification = notificationHelper.buildNetStatNotification(downStr, upStr, mobileDataUsageFormatted, wifiDataUsageFormatted, wifiText)
                notificationHelper.updateNetStatNotification(updatedNotification)

                delay(1000) // Update every second
            }
        }
    }

    private fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun getWifiSignalStrength(context: Context): Int? {

        if (!isWifiConnected(context)) {
            Log.d("SignalDebug", "Not connected to Wi-Fi")
//            return null
        }

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo

//        Log.d("SignalDebug", "SSID: ${wifiInfo.ssid}, RSSI: ${wifiInfo.rssi}, Network ID: ${wifiInfo.networkId}")

        return if (wifiInfo.rssi != -127) {
            wifiInfo.rssi
        } else {
            null
        }
    }

    private fun rssiToPercentage(rssi: Int): Int {
        val minRssi = -100
        val maxRssi = -30

        return when {
            rssi <= minRssi -> 0
            rssi >= maxRssi -> 100
            else -> ((rssi - minRssi) * 100 / (maxRssi - minRssi))
        }
    }

    private fun formatData(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1 -> DecimalFormat("##.##").format(gb) + " GB"
            mb >= 1 -> DecimalFormat("###.#").format(mb) + " MB"
            else -> DecimalFormat("###.#").format(kb) + " KB"
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        val kbps = bytesPerSec / 1024.0
        val mbps = kbps / 1024.0
        return when {
            mbps >= 1 -> DecimalFormat("#.##").format(mbps) + " MB/s"
            kbps >= 1 -> DecimalFormat("###").format(kbps) + " KB/s"
            else -> DecimalFormat("###").format(bytesPerSec) + " B/s"
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = applicationContext.packageName

        // Respect user choice: Don't show again if they've dismissed or allowed
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val ignorePrompt = prefs.getBoolean("battery_opt_ignore_prompt", false)
        if (ignorePrompt) return

        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            // Show a notification or launch Activity to explain, if needed
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)

            // Optional: Save that we already prompted them once
            prefs.edit().putBoolean("battery_opt_ignore_prompt", true).apply()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}