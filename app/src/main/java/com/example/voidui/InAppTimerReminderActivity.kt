package com.example.voidui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class InAppTimerReminderActivity : AppCompatActivity() {
    private lateinit var globalToggle: SwitchCompat
    private lateinit var appListLabel: TextView
    private lateinit var viewSettings: View
    private lateinit var appRecyclerView: RecyclerView
    private lateinit var adapter: AppToggleAdapter
    private lateinit var accessibilityLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_in_app_timer_reminder)

        initializeViews()
        setupAccessibilityLauncher()
        setupGlobalToggle()
        setupAppList()
    }

    private fun initializeViews() {
        globalToggle = findViewById(R.id.globalToggle)
        appListLabel = findViewById(R.id.applyToText)
        viewSettings = findViewById(R.id.viewSettings)
        appRecyclerView = findViewById(R.id.appRecyclerView)

        val backButton = findViewById<ImageButton>(R.id.backButton)
        backButton.setOnClickListener {
            finish()
        }
    }

    private fun setupAccessibilityLauncher() {
        accessibilityLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            handleAccessibilityResult()
        }
    }

    private fun handleAccessibilityResult() {
        val isAccessibilityEnabled = AppAccessibilityService.isAccessibilityServiceEnabled()

        if (isAccessibilityEnabled) {
            enableGlobalTimer()
        } else {
            disableGlobalTimer()
            Toast.makeText(
                this,
                "Please grant accessibility permission to activate in-app timer",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun setupGlobalToggle() {
        // Check accessibility service status on startup
        val isAccessibilityEnabled = AppAccessibilityService.isAccessibilityServiceEnabled()
        if (!isAccessibilityEnabled) {
            SharedPreferencesManager.setGlobalTimerEnabled(this, false)
        }

        val isGlobalTimerEnabled = SharedPreferencesManager.isGlobalTimerEnabled(this)
        globalToggle.isChecked = isGlobalTimerEnabled && isAccessibilityEnabled
        updateAppListVisibility(globalToggle.isChecked)

        globalToggle.setOnCheckedChangeListener { _, isChecked ->
            handleGlobalToggleChange(isChecked)
        }
    }

    private fun handleGlobalToggleChange(isChecked: Boolean) {
        if (isChecked) {
            if (!AppAccessibilityService.isAccessibilityServiceEnabled()) {
                // Launch accessibility settings
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                accessibilityLauncher.launch(intent)
            } else {
                enableGlobalTimer()
            }
        } else {
            disableGlobalTimer()
        }
    }

    private fun enableGlobalTimer() {
        SharedPreferencesManager.setGlobalTimerEnabled(this, true)
        globalToggle.isChecked = true
        updateAppListVisibility(true)

        // Start timer service
        startTimerService()

        Log.d("InAppTimerReminder", "Global timer enabled")
    }

    private fun disableGlobalTimer() {
        SharedPreferencesManager.setGlobalTimerEnabled(this, false)
        globalToggle.isChecked = false
        updateAppListVisibility(false)

        // Stop timer service
        stopTimerService()

        Log.d("InAppTimerReminder", "Global timer disabled")
    }

    private fun startTimerService() {
        try {
            val intent = Intent(this, TimerMonitorService::class.java)
            startForegroundService(intent)
            Log.d("InAppTimerReminder", "Timer service started")
        } catch (e: Exception) {
            Log.e("InAppTimerReminder", "Failed to start timer service", e)
        }
    }

    private fun stopTimerService() {
        try {
            val intent = Intent(this, TimerMonitorService::class.java)
            stopService(intent)
            Log.d("InAppTimerReminder", "Timer service stopped")
        } catch (e: Exception) {
            Log.e("InAppTimerReminder", "Failed to stop timer service", e)
        }
    }

    private fun setupAppList() {
        adapter = AppToggleAdapter(getSortedApps()) {
            refreshList()
        }
        appRecyclerView.layoutManager = LinearLayoutManager(this)
        appRecyclerView.adapter = adapter
    }

    private fun refreshList() {
        adapter.updateData(getSortedApps())
    }

    private fun getSortedApps(): List<ApplicationInfo> {
        val allApps = getAllLaunchableApps()

        // Separate enabled and disabled apps
        val enabledApps = allApps.filter { app ->
            SharedPreferencesManager.isAppTimerEnabled(this, app.packageName)
        }.sortedBy { app ->
            MainActivity().normalizeAppName(app.loadLabel(packageManager).toString()).lowercase()
        }

        val disabledApps = allApps.filter { app ->
            !SharedPreferencesManager.isAppTimerEnabled(this, app.packageName)
        }.sortedBy { app ->
            MainActivity().normalizeAppName(app.loadLabel(packageManager).toString()).lowercase()
        }

        return enabledApps + disabledApps
    }

    private fun updateAppListVisibility(show: Boolean) {
        val visibility = if (show) View.VISIBLE else View.GONE
        appListLabel.visibility = visibility
        viewSettings.visibility = visibility
        appRecyclerView.visibility = visibility

        if (!show) {
            AppTimerManager.clearAllTimers()
        }
    }

    private fun getAllLaunchableApps(): List<ApplicationInfo> {
        return try {
            val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val userManager = getSystemService(Context.USER_SERVICE) as UserManager
            val users = userManager.userProfiles
            val appList = mutableSetOf<ApplicationInfo>()
            val currentPackage = applicationContext.packageName

            for (user in users) {
                try {
                    val activities = launcherApps.getActivityList(null, user as UserHandle)
                    for (activityInfo in activities) {
                        try {
                            val appInfo = packageManager.getApplicationInfo(
                                activityInfo.applicationInfo.packageName,
                                0
                            )
                            appList.add(appInfo)
                        } catch (e: PackageManager.NameNotFoundException) {
                            // Skip this app
                        }
                    }
                } catch (e: Exception) {
                    Log.w("InAppTimerReminder", "Error getting apps for user", e)
                }
            }

            appList.filter { app ->
                try {
                    packageManager.getLaunchIntentForPackage(app.packageName) != null &&
                            app.packageName != currentPackage // Exclude "Void" itself
                } catch (e: Exception) {
                    false
                }
            }.sortedBy { app ->
                try {
                    MainActivity().normalizeAppName(
                        app.loadLabel(packageManager).toString()
                    ).lowercase()
                } catch (e: Exception) {
                    app.packageName.lowercase()
                }
            }

        } catch (e: Exception) {
            Log.e("InAppTimerReminder", "Error getting launchable apps", e)
            emptyList()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh the toggle state in case accessibility service was disabled
        val isAccessibilityEnabled = AppAccessibilityService.isAccessibilityServiceEnabled()
        if (!isAccessibilityEnabled && globalToggle.isChecked) {
            disableGlobalTimer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("InAppTimerReminder", "Activity destroyed")
    }
}