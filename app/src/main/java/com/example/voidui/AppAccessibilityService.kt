package com.example.voidui

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi

class AppAccessibilityService : AccessibilityService() {

    companion object {
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
//        Log.d("AppAccessibilityService", "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            if (!packageName.isNullOrEmpty()) {
                val appName = try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    "Unknown"
                }
                lastForegroundApp = MainActivity().normalizeAppName(appName)
                Log.d("AppAccessibilityService", "Foreground app: $appName")
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
//        Log.d("AppAccessibilityService", "Attempting to lock screen using Accessibility")
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
//        Log.d("AppAccessibilityService", "Service destroyed")
    }
}