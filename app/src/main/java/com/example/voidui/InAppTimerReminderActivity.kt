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
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.Switch
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

        globalToggle = findViewById(R.id.globalToggle)
        appListLabel = findViewById(R.id.applyToText)
        viewSettings = findViewById(R.id.viewSettings)
        appRecyclerView = findViewById(R.id.appRecyclerView)

        val backButton = findViewById<ImageButton>(R.id.backButton)
        backButton.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }

        globalToggle.isChecked = SharedPreferencesManager.isGlobalTimerEnabled(this)
        updateAppListVisibility(globalToggle.isChecked)

        accessibilityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (isAccessibilityServiceEnabled()) {
                SharedPreferencesManager.setGlobalTimerEnabled(this, true)
                globalToggle.isChecked = true
                updateAppListVisibility(true)
            } else {
                SharedPreferencesManager.setGlobalTimerEnabled(this, false)
                globalToggle.isChecked = false
                Toast.makeText(this, "Please grant accessibility permission to activate in-app timer", Toast.LENGTH_SHORT).show()
            }
        }

        globalToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!isAccessibilityServiceEnabled()) {
                    accessibilityLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } else {
                    SharedPreferencesManager.setGlobalTimerEnabled(this, true)
                    updateAppListVisibility(true)
                }
            } else {
                SharedPreferencesManager.setGlobalTimerEnabled(this, false)
                updateAppListVisibility(false)
            }
        }

//        val appList = getAllLaunchableApps()
////        val allApps: List<ApplicationInfo> = getInstalledApps()
//        val context = this // your activity or application context
//
//        val onApps = appList.filter {
//            SharedPreferencesManager.isAppTimerEnabled(context, it.packageName)
//        }.sortedBy { it.loadLabel(packageManager).toString().lowercase() }
//
//        val offApps = appList.filter {
//            !SharedPreferencesManager.isAppTimerEnabled(context, it.packageName)
//        }.sortedBy { it.loadLabel(packageManager).toString().lowercase() }
//
//        val sortedApps = onApps + offApps
//        adapter = AppToggleAdapter(sortedApps)

        adapter = AppToggleAdapter(getSortedApps()) {
            refreshList()
        }

//        adapter = AppToggleAdapter(appList)
        appRecyclerView.layoutManager = LinearLayoutManager(this)
        appRecyclerView.adapter = adapter
    }

    private fun refreshList() {
        adapter.updateData(getSortedApps())
    }

    private fun getSortedApps(): List<ApplicationInfo> {
        val allApps = getAllLaunchableApps()
        val onApps = allApps.filter {
            SharedPreferencesManager.isAppTimerEnabled(this, it.packageName)
        }.sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        val offApps = allApps.filter {
            !SharedPreferencesManager.isAppTimerEnabled(this, it.packageName)
        }.sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        return onApps + offApps
    }

    private fun updateAppListVisibility(show: Boolean) {
        appListLabel.visibility = if (show) View.VISIBLE else View.GONE
        viewSettings.visibility = if (show) View.VISIBLE else View.GONE
        appRecyclerView.visibility = if (show) View.VISIBLE else View.GONE

        if (!show) AppTimerManager.clearAllTimers()
    }

    private fun getAllLaunchableApps(): List<ApplicationInfo> {
        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = getSystemService(Context.USER_SERVICE) as UserManager
        val users = userManager.userProfiles
        val appList = mutableSetOf<ApplicationInfo>()
        val currentPackage = applicationContext.packageName

        for (user in users) {
            val activities = launcherApps.getActivityList(null, user as UserHandle)
            for (activityInfo in activities) {
                try {
                    val appInfo = packageManager.getApplicationInfo(activityInfo.applicationInfo.packageName, 0)
                    appList.add(appInfo)
                } catch (_: PackageManager.NameNotFoundException) {}
            }
        }

        return appList
            .filter {
                packageManager.getLaunchIntentForPackage(it.packageName) != null &&
                        it.packageName != currentPackage // exclude "Void" itself
            }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = "${packageName}/${AppAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(":").any { it.equals(expectedComponent, ignoreCase = true) }
    }

}
