package com.example.voidui

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
//        if (event?.packageName == "com.instagram.android" || event?.packageName == "com.google.android.youtube") {
//            val rootNode = rootInActiveWindow
//
//            rootNode?.let {
//                // Look for specific UI elements that indicate Reels or Shorts
//                val reelsButton = findNodeByDescription(it, "Reels")
//                val shortsButton = findNodeByDescription(it, "Shorts")
//
//                if (reelsButton != null || shortsButton != null) {
//                    // Perform "back" action
//                    performGlobalAction(GLOBAL_ACTION_BACK)
//                }
//            }
//        }
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

//    private fun findNodeByDescription(node: AccessibilityNodeInfo, description: String): AccessibilityNodeInfo? {
//        if (node.contentDescription == description) {
//            return node
//        }
//        for (i in 0 until node.childCount) {
//            val child = node.getChild(i)
//            val result = findNodeByDescription(child, description)
//            if (result != null) {
//                return result
//            }
//        }
//        return null
//    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
//        Log.d("AppAccessibilityService", "Service destroyed")
    }
}