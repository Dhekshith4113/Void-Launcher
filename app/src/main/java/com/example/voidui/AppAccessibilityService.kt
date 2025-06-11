package com.example.voidui

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi

class AppAccessibilityService : AccessibilityService() {

    companion object {
//        var oneMinuteToastShown = false
        var instance: AppAccessibilityService? = null
            private set

        var lastForegroundApp: String? = null
            private set

        fun goHomeScreen() {
            instance?.goHomeScreen()
        }

        @RequiresApi(Build.VERSION_CODES.P)
        fun lockNowWithAccessibility() {
            instance?.lockScreenWithAccessibility()
        }

        fun isAccessibilityServiceEnabled(): Boolean {
            return instance?.isAccessibilityServiceEnabled() ?: false
        }

    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageNames = null // Monitor all apps
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }

        serviceInfo = info
        Log.d("AppAccessibilityService", "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            if (!packageName.isNullOrEmpty()) {
                lastForegroundApp = packageName
                Log.d("AppAccessibilityService", "Foreground app: $packageName")

                if (AppTimerManager.isExpired(packageName)) {
//                    val prefs = getSharedPreferences("focus_prefs", MODE_PRIVATE)
//                    val shouldSkip = prefs.getBoolean("skip_dialog_$packageName", false)

//                    if (shouldSkip) {
                        // First re-entry after force exit, skip showing dialog this time
//                        prefs.edit().remove("skip_dialog_$packageName").apply()
//                        Log.d("AppAccessibilityService", "Dialog skipped for $packageName")
//                    } else {
                        Log.d("AppAccessibilityService", "Showing dialog for $packageName")
                        val intent = Intent(this, TimerPromptActivity::class.java).apply {
                            putExtra("packageName", packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
//                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        // Required override, no action needed for now
    }

    fun goHomeScreen() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun lockScreenWithAccessibility() {
        Log.d("AppAccessibilityService", "Attempting to lock screen using Accessibility")
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = "${packageName}/${AppAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(":").any { it.equals(expectedComponent, ignoreCase = true) }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        Log.d("AppAccessibilityService", "Service destroyed")
    }
}

//package com.example.voidui
//
//import android.accessibilityservice.AccessibilityService
//import android.content.Intent
//import android.os.Handler
//import android.os.Looper
//import android.view.accessibility.AccessibilityEvent
//import android.widget.Toast
//
//class AppAccessibilityService : AccessibilityService() {
//
//    companion object {
//        var targetPackage: String? = null
//        var exitTimeMillis: Long = 0
//        var oneMinuteToastShown = false
//    }
//
//    private var currentPackage: String? = null
//
//    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
//        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
//            currentPackage = event.packageName?.toString()
//        }
//    }
//
//    override fun onServiceConnected() {
//        super.onServiceConnected()
//        monitorAppTimer()
//    }
//
//    private fun monitorAppTimer() {
//        Thread {
//            while (true) {
//                if (targetPackage != null &&
//                    currentPackage == targetPackage
//                ) {
//                    val timeRemaining = exitTimeMillis - System.currentTimeMillis()
//
//                    if (timeRemaining <= 60_000 && !oneMinuteToastShown) {
//                        oneMinuteToastShown = true
//                        Handler(Looper.getMainLooper()).post {
//                            Toast.makeText(
//                                applicationContext,
//                                "1 minute left",
//                                Toast.LENGTH_LONG
//                            ).show()
//                        }
//                    }
//
//                    if (System.currentTimeMillis() >= exitTimeMillis) {
//                        val homeIntent = packageManager.getLaunchIntentForPackage(packageName)
//                        homeIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//                        startActivity(homeIntent)
//
//                        targetPackage = null
//                        exitTimeMillis = 0
//                        oneMinuteToastShown = false
//                    }
//                }
//                Thread.sleep(1000)
//            }
//        }.start()
//    }
//
//    override fun onInterrupt() {}
//}