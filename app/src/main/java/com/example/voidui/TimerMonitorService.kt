package com.example.voidui

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimerMonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var notificationHelper: NotificationHelper

    private val checkRunnable = object : Runnable {
        override fun run() {
            val currentApp = AppAccessibilityService.lastForegroundApp
            if (currentApp != null) {
                Log.d("TimerMonitor", "Checking $currentApp")
                if (AppTimerManager.isOneMin(currentApp)) {
                    if (!(SharedPreferencesManager.isOneMinToastShown(this@TimerMonitorService, currentApp))) {
                        Toast.makeText(this@TimerMonitorService, "One minute left", Toast.LENGTH_SHORT).show()
                        SharedPreferencesManager.setOneMinToastShown(this@TimerMonitorService, currentApp, true)
                    }
                }
                if (AppTimerManager.isExpired(currentApp)) {
                    Log.d("TimerMonitor", "$currentApp timer expired.")
                    if (!AppTimerManager.wasMarkedExpired(currentApp)) {
                        Log.d("TimerMonitor", "$currentApp: first-time expiry, going home.")
                        AppTimerManager.markExpired(currentApp)
                        AppAccessibilityService.goHomeScreen()
                    } else {
                        Log.d("TimeMonitor", "Showing dialog for $currentApp")
                        val intent = Intent(this@TimerMonitorService, TimerPromptActivity::class.java).apply {
                            putExtra("appName", currentApp)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    }
                } else {
                    AppTimerManager.clearExpiredMark(currentApp)
                }
            } else {
                Log.d("TimerMonitor", "Foreground app: null")
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(applicationContext)
        startForeground(NotificationHelper.NOTIFICATION_ID_APP_TIMER, notificationHelper.buildAppTimerNotification(appTimers = emptyMap()))
        startTimerMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.post(checkRunnable)
        return START_STICKY
    }

    private fun startTimerMonitoring() {
        coroutineScope.launch {
            while (isActive) {
                val allAppTimers = AppTimerManager.getTimers()
                val updatedNotification = notificationHelper.buildAppTimerNotification(allAppTimers)
                notificationHelper.updateAppTimerNotification(updatedNotification)
                delay(1000) // Update every second
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
        coroutineScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}