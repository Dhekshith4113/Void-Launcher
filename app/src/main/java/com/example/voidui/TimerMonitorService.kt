package com.example.voidui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat

class TimerMonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
//    private val channelId = "timer_channel"

    private val checkRunnable = object : Runnable {
        override fun run() {
            val currentApp = AppAccessibilityService.lastForegroundApp
            if (currentApp != null) {
                Log.d("TimerMonitor", "Checking $currentApp")

//                if (AppTimerManager.isTracking(currentApp)) {
//                    val timeLeft = AppTimerManager.getRemainingTime(currentApp)
//                    startForeground(1, updateTimerNotification(currentApp, timeLeft))
//                }

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
//                        val manager = getSystemService(NotificationManager::class.java)
//                        manager.cancel(1) // Cancel the timer notification
                        AppTimerManager.markExpired(currentApp)
                        AppAccessibilityService.goHomeScreen()
                        stopForeground(true)
                    } else {
                        Log.d("TimerMonitor", "$currentApp: already marked expired, showing overlay")
//                        val intent = Intent(this@TimerMonitorService, FloatingTimerService::class.java).apply {
//                            putExtra("packageName", currentApp)
//                        }
//                        startService(intent)
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
//        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        startForeground(1, updateTimerNotification("", 0))
        handler.post(checkRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
        stopForeground(true)
    }

//    private fun createNotification(): Notification {
//        val channelId = "timer_channel"
//        val manager = getSystemService(NotificationManager::class.java)
//        val channel =
//            NotificationChannel(channelId, "Timer Monitor", NotificationManager.IMPORTANCE_LOW)
//        manager.createNotificationChannel(channel)
//
//        return NotificationCompat.Builder(this, channelId)
//            .setContentTitle("App Timer Running")
//            .setSmallIcon(R.drawable.ic_launcher_foreground)
//            .build()
//    }

//    private fun createNotificationChannel() {
//        val channel = NotificationChannel(
//            channelId,
//            "Timer Monitor",
//            NotificationManager.IMPORTANCE_LOW
//        )
//        val manager = getSystemService(NotificationManager::class.java)
//        manager.createNotificationChannel(channel)
//    }
//
//    private fun updateTimerNotification(appPackage: String, timeLeft: Long = 0): Notification {
//        val notificationManager = getSystemService(NotificationManager::class.java)
//        val channel = NotificationChannel(channelId, "Timer Monitor", NotificationManager.IMPORTANCE_LOW)
//        notificationManager.createNotificationChannel(channel)
//
//        val packageManager = applicationContext.packageManager
//        val appName = try {
//            val appInfo = packageManager.getApplicationInfo(appPackage, 0)
//            packageManager.getApplicationLabel(appInfo).toString()
//        } catch (e: PackageManager.NameNotFoundException) {
//            "Active"
//        }
//
////        val timeText = if (timeLeft > 0)
////            "${formatMillis(timeLeft)}"
////        else
////            "App Timer Running"
//
//        val builder =  NotificationCompat.Builder(this, channelId)
//            .setContentTitle("Timer: $appName")
////            .setContentText("Time left: $timeText")
//            .setSmallIcon(R.drawable.ic_launcher_foreground)
//            .setOnlyAlertOnce(true)
//            .setOngoing(true)
//
//        if (timeLeft > 0) {
//            val finishTime = System.currentTimeMillis() + timeLeft
//            builder
//                .setUsesChronometer(true)
//                .setWhen(finishTime)
//                .setChronometerCountDown(true)
//        }
//
//        return builder.build()
//    }

//    private fun formatMillis(millis: Long): String {
//        val minutes = (millis / 1000) / 60
//        val seconds = (millis / 1000) % 60
//        return String.format("%02d:%02d", minutes, seconds)
//    }

    override fun onBind(intent: Intent?): IBinder? = null
}