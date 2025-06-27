package com.example.voidui

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast

class TimerMonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var notificationHelper: NotificationHelper? = null
    private var isChecking = false

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (!isChecking) return

            try {
                monitorCurrentApp()
                updateNotification()
                handler.postDelayed(this, 1000)
            } catch (e: Exception) {
                Log.e("TimerMonitor", "Error in monitoring loop", e)
                // Restart monitoring after error
                handler.postDelayed(this, 2000)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(applicationContext)
        Log.d("TimerMonitor", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NotificationHelper.NOTIFICATION_ID_APP_TIMER,
            notificationHelper?.buildAppTimerNotification(AppTimerManager.getTimers())
        )

        if (!isChecking) {
            handler.removeCallbacks(checkRunnable) // Ensure no duplicate callbacks
            handler.post(checkRunnable)
            isChecking = true
            Log.d("TimerMonitor", "Monitoring started")
        }

        return START_STICKY
    }

    private fun monitorCurrentApp() {
        val currentApp = AppAccessibilityService.lastForegroundApp
        if (currentApp.isNullOrEmpty()) {
            Log.d("TimerMonitor", "No foreground app detected")
            return
        }

        if (!AppTimerManager.hasTimer(currentApp)) return

        Log.d("TimerMonitor", "Checking $currentApp")
        if (AppTimerManager.isOneMin(currentApp)) {
            if (!(SharedPreferencesManager.isOneMinToastShown(this@TimerMonitorService, currentApp))) {
                Toast.makeText(this@TimerMonitorService, "One minute left", Toast.LENGTH_SHORT).show()
                SharedPreferencesManager.setOneMinToastShown(this@TimerMonitorService, currentApp, true)
                Log.d("TimerMonitor", "One minute toast shown for $currentApp")
            }
        }
        if (AppTimerManager.isExpired(currentApp)) {
            Log.d("TimerMonitor", "$currentApp timer expired")
            if (!AppTimerManager.wasMarkedExpired(currentApp)) {
                Log.d("TimerMonitor", "$currentApp: First expiry, going home")
                AppTimerManager.markExpired(currentApp)
                handler.post {
                    AppAccessibilityService.goHomeScreen()
                }
            } else {
                try {
                    Log.d("TimeMonitor", "Showing dialog for $currentApp")
                    val intent = Intent(
                        this@TimerMonitorService,
                        TimerPromptActivity::class.java
                    ).apply {
                        putExtra("appName", currentApp)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("TimerMonitor", "Error showing dialog for $currentApp", e)
                }
            }
        } else {
            AppTimerManager.clearExpiredMark(currentApp)
        }
    }

    private fun updateNotification() {
        notificationHelper?.let { helper ->
            val allAppTimers = AppTimerManager.getTimers()
            val updatedNotification = helper.buildAppTimerNotification(allAppTimers)
            helper.updateAppTimerNotification(updatedNotification)
        }
    }

    override fun onDestroy() {
        Log.d("TimerMonitor", "Monitoring stopped and service destroying")
        isChecking = false
        handler.removeCallbacks(checkRunnable)
        notificationHelper = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}