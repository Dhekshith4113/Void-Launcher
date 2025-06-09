package com.example.voidui

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import java.util.Calendar

object UsageStatsManagerUtils {

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getTodayTopUsedApps(context: Context): Pair<Long, List<Pair<String, Long>>> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val packageManager = context.packageManager

//        val installedApps = context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
//            .filter { context.packageManager.getLaunchIntentForPackage(it.packageName) != null }
//            .map { it.packageName }
//            .toSet()

        val packageNameSet: Set<String> = filterApps(context).map { it.packageName }.toSet()

        val excludedPackages = setOf(context.packageName) + packageNameSet
        val launchablePackages = getAllLaunchableAppPackages(context)
//        val launchablePackages = context.packageManager.getInstalledApplications(0)
//            .filter { context.packageManager.getLaunchIntentForPackage(it.packageName) != null && it.packageName !in excludedPackages }
//            .map { it.packageName }
//            .toSet()

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val usageMap = mutableMapOf<String, Long>()
        var lastUsedApp: String? = null
        var lastEventTime: Long = startTime

        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val packageName = event.packageName

            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    // Close previous session
                    lastUsedApp?.let {
                        val duration = event.timeStamp - lastEventTime
                        if (duration > 0 && duration < 1000 * 60 * 60 * 4) {
                            usageMap[it] = usageMap.getOrDefault(it, 0L) + duration
                        }
                    }
                    lastUsedApp = packageName
                    lastEventTime = event.timeStamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (packageName == lastUsedApp) {
                        val duration = event.timeStamp - lastEventTime
                        if (duration > 0 && duration < 1000 * 60 * 60 * 4) {
                            usageMap[packageName] = usageMap.getOrDefault(packageName, 0L) + duration
                        }
                        lastUsedApp = null
                        lastEventTime = event.timeStamp
                    }
                }
            }
        }

        // Include ongoing session if any
        lastUsedApp?.let {
            val duration = endTime - lastEventTime
            if (duration > 0 && duration < 1000 * 60 * 60 * 4) {
                usageMap[it] = usageMap.getOrDefault(it, 0L) + duration
            }
        }

        val filteredUsageMap = usageMap.filterKeys { it in launchablePackages }

//        val filteredUsageMap = usageMap.filterKeys { it in installedApps }
        val totalTime = filteredUsageMap.values.sum()

//        val top3 = filteredUsageMap.entries
//            .sortedByDescending { it.value }
//            .take(5)
//            .map { (packageName, time) ->
//                val appName = try {
//                    packageManager.getApplicationLabel(
//                        packageManager.getApplicationInfo(packageName, 0)
//                    ).toString()
//                } catch (e: Exception) {
//                    packageName
//                }
//                appName to time
//            }

        val sortedUsage = filteredUsageMap.entries
            .sortedByDescending { it.value }

//        println(sortedUsage)

        val top3 = sortedUsage
            .take(3)
            .map { (packageName, time) ->
                val appName = try {
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(packageName, 0)
                    ).toString()
                } catch (e: Exception) {
                    packageName
                }
                appName to time
            }
            .toMutableList()

        // Sum remaining usage time as "Other"
        if (sortedUsage.size > 3) {
            val otherTotal = sortedUsage.drop(3).sumOf { it.value }
            top3.add("Other" to otherTotal)
        }

        return totalTime to top3
    }

    private fun getAllLaunchableAppPackages(context: Context): Set<String> {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val users = userManager.userProfiles

        val packages = mutableSetOf<String>()
        for (user in users) {
            val activities = launcherApps.getActivityList(null, user as UserHandle)
            for (activityInfo in activities) {
                val pkg = activityInfo.applicationInfo.packageName
                if (pkg != context.packageName) {
                    packages.add(pkg)
                }
            }
        }
        return packages
    }

    private fun isLauncherApp(context: Context, packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

//    private fun filterApps(context: Context): List<ApplicationInfo> {
//        val launcherApps = mutableListOf<ApplicationInfo>()
//        val allApps = context.packageManager.getInstalledApplications(0)
//
//        for (app in allApps) {
//            when {
//                isLauncherApp(context, app.packageName) -> launcherApps.add(app)
//            }
//        }
//
//        return launcherApps
//    }

    private fun filterApps(context: Context): List<ApplicationInfo> {
        val allApps = context.packageManager.getInstalledApplications(0)
        return allApps.filterNot { isLauncherApp(context, it.packageName) }
    }

}