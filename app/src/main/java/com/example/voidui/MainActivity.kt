package com.example.voidui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.widget.Toast
import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.DragEvent
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var listAdapter: AppListAdapter
    private lateinit var gestureDetector: GestureDetector
    private lateinit var drawerGestureDetector: GestureDetector
    private lateinit var drawerAdapter: AppDrawerAdapter
    private lateinit var drawerRecyclerView: RecyclerView

    private var needRefresh = false
    private var toastShownThisDrag = false
    private var shouldMoveIndicator = true
    val drawerSize = 4

    @SuppressLint("ClickableViewAccessibility")
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applySavedTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefsInstalledApps = getSharedPreferences("installed_apps", MODE_PRIVATE)
        val firstTime = prefsInstalledApps.getBoolean("first_time", false)

        if (!firstTime) {
            saveListApps(loadListApps())
            prefsInstalledApps.edit().putBoolean("first_time", true).apply()
        }

        if (!AppAccessibilityService.isAccessibilityServiceEnabled()) {
            promptAccessibilitySettings()
        }

        if (!UsageStatsManagerUtils.hasUsageStatsPermission(this)) {
            promptOverlaySettings()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED) {
                if (SharedPreferencesManager.isSwitchTrackEnabled(this)) {
                    val intent = Intent(this, SpeedMonitorService::class.java)
                    startForegroundService(intent)
                }
            } else {
                promptNotificationSettings()
                SharedPreferencesManager.setSwitchTrackEnabled(this, false)
                val intent = Intent(this, SpeedMonitorService::class.java)
                stopService(intent)
            }
        }

        gestureDetector = GestureDetector(this, SwipeGestureListener())

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        listAdapter = AppListAdapter(this, loadListApps().toMutableList(), packageManager, refreshList = {
            needRefresh =true
        }) { appInfo ->
            val packageName = appInfo.packageName
            if (shouldShowTimer(this, packageName)) {
                showTimeLimitDialog(appInfo)
            } else {
                launchAppDirectly(appInfo)
            }
        }

        recyclerView.adapter = listAdapter

        listAdapter.onAppDragStarted = { app ->
            val currentApps = listAdapter.getApps()
            val index = currentApps.indexOfFirst { it.packageName == app.packageName }
            if (index != -1) {
                // Clone list to preserve order
                val updatedList = currentApps.toMutableList()
                updatedList.removeAt(index)
                listAdapter.updateData(updatedList)
            }
        }

        drawerRecyclerView = findViewById(R.id.appDrawerRecyclerView)
        drawerAdapter = AppDrawerAdapter(this, packageManager, loadDrawerApps().toMutableList(), ::saveDrawerApps, refreshList = {
            needRefresh = true
        }) { appInfo ->
            val packageName = appInfo.packageName
            if (shouldShowTimer(this, packageName)) {
                showTimeLimitDialog(appInfo)
            } else {
                launchAppDirectly(appInfo)
            }
        }

        drawerRecyclerView.apply {
            drawerRecyclerView.layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            drawerRecyclerView.addItemDecoration(CenterSpacingDecoration())
            adapter = drawerAdapter
        }

        drawerRecyclerView.itemAnimator = null

        drawerRecyclerView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                drawerRecyclerView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                drawerRecyclerView.invalidateItemDecorations()
            }
        })

        drawerAdapter.onAppDragStarted = { app ->
            val currentApps = drawerAdapter.getApps()
            val index = currentApps.indexOfFirst { it.packageName == app.packageName }
            if (index != -1) {
                // Clone list to preserve order
                val updatedList = currentApps.toMutableList()
                updatedList.removeAt(index)
                updatedList.add(index, AppDrawerAdapter.getDropIndicatorItem())
                drawerAdapter.updateData(updatedList)
            }
        }

        drawerRecyclerView.setOnDragListener { view, event ->
            when (event.action) {

                DragEvent.ACTION_DRAG_ENTERED -> {
                    // Only insert if not already inserted
                    if (!drawerAdapter.getApps().any { it.packageName == AppDrawerAdapter.DROP_INDICATOR_PACKAGE }) {
                        drawerAdapter.insertDropIndicator(0)
                    }
                    true
                }

                DragEvent.ACTION_DRAG_LOCATION -> {
                    val x = event.x.toInt()
                    val recyclerView = view as RecyclerView
                    val draggedApp = event.localState as ApplicationInfo
                    val isAppFromDrawer = !listAdapter.getApps().contains(draggedApp)
                    if (!isAppFromDrawer && (drawerAdapter.getApps().size >= drawerSize+1)) {
                        if (!toastShownThisDrag) {
                            drawerAdapter.removeDropIndicator()
                            Toast.makeText(this, "Cannot add more than $drawerSize apps", Toast.LENGTH_SHORT).show()
                            shouldMoveIndicator = false
                            toastShownThisDrag = true
                        }
                        return@setOnDragListener true
                    }

                    var targetPos = -1
                    // Find the hovered child
                    for (i in 0 until recyclerView.childCount) {
                        val child = recyclerView.getChildAt(i)
                        val left = child.left
                        val right = child.right

                        if (x in left..right) {
                            targetPos = recyclerView.getChildAdapterPosition(child)
                            break
                        }
                    }

                    if (targetPos != -1) {
                        // Only move if we're not already at that position
                        val currentDropIndex = drawerAdapter.getApps().indexOfFirst {
                            it.packageName == AppDrawerAdapter.DROP_INDICATOR_PACKAGE
                        }
                        if (currentDropIndex != targetPos && shouldMoveIndicator) {
                            drawerAdapter.moveDropIndicator(targetPos)
                        }
                    }
                    true
                }

                DragEvent.ACTION_DROP -> {
                    val draggedApp = event.localState as ApplicationInfo
                    val dropIndex = drawerAdapter.getApps().indexOfFirst {
                        it.packageName == AppDrawerAdapter.DROP_INDICATOR_PACKAGE
                    }.takeIf { it != -1 } ?: drawerAdapter.itemCount

                    drawerAdapter.removeDropIndicator()

                    // Check if app was already in the drawer
                    val isAppFromDrawer = !listAdapter.getApps().contains(draggedApp)
                    if (view == drawerRecyclerView) {
                        // Reorder or return to drawer
                        if (!isAppFromDrawer && drawerAdapter.getApps().size >= drawerSize) {
                            return@setOnDragListener true
                        } else {
                            if (!isAppFromDrawer) {
                                // Moved from list to drawer
                                listAdapter.removeApp(draggedApp)
                            }
                            drawerAdapter.addAppAtPosition(draggedApp, dropIndex)
                        }
                    } else {
                        // Dropped into main app list, remove from drawer
                        if (isAppFromDrawer) {
                            drawerAdapter.removeApp(draggedApp)
                            listAdapter.addApp(draggedApp)
                        }
                    }

                    saveDrawerApps(drawerAdapter.getApps())
                    saveListApps(listAdapter.getApps())
                    drawerRecyclerView.invalidateItemDecorations()
                    true
                }

                DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                    drawerAdapter.removeDropIndicator()
                    toastShownThisDrag = false
                    shouldMoveIndicator = true
                    true
                }

                else -> true
            }
        }

        recyclerView.setOnDragListener { _, event ->
            when (event.action) {

                DragEvent.ACTION_DROP -> {
                    val app = event.localState as ApplicationInfo
                    drawerAdapter.removeApp(app)
                    listAdapter.addApp(app)
                    saveDrawerApps(drawerAdapter.getApps())
                    saveListApps(listAdapter.getApps())
                    true
                }

                DragEvent.ACTION_DRAG_EXITED -> {
                    val app = event.localState as ApplicationInfo
                    listAdapter.addApp(app)
                    true
                }

                else -> true
            }
        }

        drawerGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val child = drawerRecyclerView.findChildViewUnder(e.x, e.y)
                return if (child == null) {
                    if (SharedPreferencesManager.isDoubleTapToLockEnabled(this@MainActivity)) {
                        vibratePhone(100)
                        AppAccessibilityService.lockNowWithAccessibility()
                    } else {
                        Toast.makeText(this@MainActivity, "Please enable 'Double tap on the mini app drawer to Lock' in Gestures", Toast.LENGTH_SHORT).show()
                    }
                    true
                } else {
                    false
                }
            }
        })

        drawerRecyclerView.setOnTouchListener { _, event ->
            drawerGestureDetector.onTouchEvent(event)
            false
        }

        val settingsButton = findViewById<ImageButton>(R.id.settingsButton)
        settingsButton.setOnClickListener {
            openSettings()
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

    }

    override fun onResume() {
        super.onResume()
        clearExpiredNewAppTags()
        checkNewlyInstalledApps()
        if (needRefresh) {
            listAdapter.setApps(getNewlyInstalledApps())
            listAdapter.updateData(loadListApps().toMutableList())
            drawerAdapter.updateData(loadDrawerApps().toMutableList())
            needRefresh =false
        }
    }

    private fun checkNewlyInstalledApps() {
        val prefsNewApps = getSharedPreferences("new_apps", MODE_PRIVATE)
        val prefsList = getSharedPreferences("list_prefs", MODE_PRIVATE)
        val prefsDrawer = getSharedPreferences("drawer_prefs", MODE_PRIVATE)
        val currentAppList = prefsList.getStringSet("list_packages", emptySet()) ?: emptySet()
        val currentDrawerList = prefsDrawer.getString("drawer_ordered_packages", "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = getSystemService(Context.USER_SERVICE) as UserManager
        val users = userManager.userProfiles
        val currentPackage = applicationContext.packageName
        val newAppInfoList = mutableListOf<ApplicationInfo>()
        val newAppList = mutableListOf<String>()

        for (user in users) {
            val activities = launcherApps.getActivityList(null, user as UserHandle)
            for (activity in activities) {
                if (activity.applicationInfo.packageName != currentPackage) {   // exclude "Void" itself
                    newAppInfoList.add(activity.applicationInfo)
                    newAppList.add(activity.applicationInfo.packageName)
                }
            }
        }

        newAppInfoList.filter { packageManager.getLaunchIntentForPackage(it.packageName) != null &&
                it.packageName in currentDrawerList
        }.sortedBy {
            it.loadLabel(packageManager).toString().lowercase()
        }

        val newApps = newAppList.filterNot { it in currentAppList || it in currentDrawerList.toSet() }
        Log.d("App List", "Current App List = $currentAppList")
        Log.d("App List", "New App List = $newAppList")
        Log.d("App List", "New Apps = $newApps")
        if (newApps.isNotEmpty()) {
            val editor = prefsNewApps.edit()
            val timestamp = System.currentTimeMillis()

            newApps.forEach { pkg ->
                editor.putLong("new_app_time_$pkg", timestamp)
            }

            editor.apply()
            prefsNewApps.edit().putStringSet("new_app_name", newApps.toSet()).apply()
            saveListApps(newAppInfoList)
        }
        needRefresh = true
    }

    private fun getNewlyInstalledApps(): Set<String> {
        val prefs = getSharedPreferences("new_apps", MODE_PRIVATE)
        val allEntries = prefs.all
        println(allEntries)
        val now = System.currentTimeMillis()
        val oneDayMillis = 12 * 60 * 60 * 1000L

        return allEntries
            .filterKeys { it.startsWith("new_app_time_") }
            .mapNotNull { entry ->
                val pkg = entry.key.removePrefix("new_app_time_")
                val time = entry.value as? Long ?: return@mapNotNull null
                if (now - time < oneDayMillis) pkg else null
            }
            .toSet()
    }

    private fun clearExpiredNewAppTags() {
        val prefs = getSharedPreferences("new_apps", MODE_PRIVATE)
        val editor = prefs.edit()
        val now = System.currentTimeMillis()
        val halfDayMillis = 12 * 60 * 60 * 1000L

        prefs.all.forEach { (key, value) ->
            if (key.startsWith("new_app_time_")) {
                val time = value as? Long ?: return@forEach
                if (now - time >= halfDayMillis) {
                    editor.remove(key)
                }
            }
        }

        editor.apply()
    }

    private fun promptNotificationSettings() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_notification_prompt, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogView.findViewById<TextView>(R.id.btnOpen).setOnClickListener {
            val intent = Intent().apply {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            startActivity(intent)
            dialog.dismiss()
        }
        dialogView.findViewById<TextView>(R.id.btnLater).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun promptOverlaySettings() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_usage_prompt, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogView.findViewById<TextView>(R.id.btnOpen).setOnClickListener {
            val intent = Intent().apply {
                action = Settings.ACTION_USAGE_ACCESS_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            startActivity(intent)
            dialog.dismiss()
        }
        dialogView.findViewById<TextView>(R.id.btnLater).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun promptAccessibilitySettings() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_accessibility_prompt, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogView.findViewById<TextView>(R.id.btnOpen).setOnClickListener {
            val intent = Intent().apply {
                action = Settings.ACTION_ACCESSIBILITY_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            startActivity(intent)
            dialog.dismiss()
        }
        dialogView.findViewById<TextView>(R.id.btnLater).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun shouldShowTimer(context: Context, packageName: String): Boolean {
        return SharedPreferencesManager.isGlobalTimerEnabled(context) &&
                SharedPreferencesManager.isAppTimerEnabled(context, packageName)
    }

    private fun launchAppDirectly(appInfo: ApplicationInfo) {
        val intent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "Cannot launch app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showTimeLimitDialog(appInfo: ApplicationInfo) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_time_limit, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        fun startTimerAndLaunchApp(minutes: Int) {
            if (minutes > 0) {
                Log.d("MainActivity", "packageName is ${appInfo.packageName}")
                AppTimerManager.setTimer(appInfo.packageName, minutes * 60 * 1000L)

                val intent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
                if (intent != null) {
                    startActivity(intent)
                    Toast.makeText(this, "Timer set to $minutes min", Toast.LENGTH_SHORT).show()
                    SharedPreferencesManager.setOneMinToastShown(this, appInfo.packageName, false)
                } else {
                    Toast.makeText(this, "Cannot launch app", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
        }

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogView.findViewById<TextView>(R.id.dialogTitle).text = "How much time would you like\nto spend on ${appInfo.loadLabel(applicationContext.packageManager)}?"
        dialogView.findViewById<TextView>(R.id.btn1Min).setOnClickListener {
            startTimerAndLaunchApp(1)
        }
        dialogView.findViewById<TextView>(R.id.btn2Min).setOnClickListener {
            startTimerAndLaunchApp(2)
        }
        dialogView.findViewById<TextView>(R.id.btn5Min).setOnClickListener {
            startTimerAndLaunchApp(5)
        }
        dialogView.findViewById<TextView>(R.id.btn10Min).setOnClickListener {
            startTimerAndLaunchApp(10)
        }

        val customInput = dialogView.findViewById<EditText>(R.id.customTimeInput)
        customInput.setOnEditorActionListener { _, _, _ ->
            val minutes = customInput.text.toString().toIntOrNull() ?: 20
            startTimerAndLaunchApp(minutes)
            true
        }

        dialogView.findViewById<Button>(R.id.btnClose).visibility = View.GONE

        dialog.show()
    }

    private fun saveDrawerApps(apps: List<ApplicationInfo>) {
        val orderedPackages = apps.joinToString(",") { it.packageName }
        getSharedPreferences("drawer_prefs", MODE_PRIVATE)
            .edit()
            .putString("drawer_ordered_packages", orderedPackages)
            .apply()
    }

    private fun saveListApps(apps: List<ApplicationInfo>) {
        val packageNames = apps.map { it.packageName }
        getSharedPreferences("list_prefs", MODE_PRIVATE)
            .edit()
            .putStringSet("list_packages", packageNames.toSet())
            .apply()
    }

    private fun loadDrawerApps(): List<ApplicationInfo> {
        val prefs = getSharedPreferences("drawer_prefs", MODE_PRIVATE)
        val packageList = prefs.getString("drawer_ordered_packages", "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = getSystemService(Context.USER_SERVICE) as UserManager
        val users = userManager.userProfiles

        val allAppsMap = mutableMapOf<String, ApplicationInfo>()
        for (user in users) {
            val activities = launcherApps.getActivityList(null, user as UserHandle)
            for (activity in activities) {
                allAppsMap[activity.applicationInfo.packageName] = activity.applicationInfo
            }
        }
        val drawerApps = mutableListOf<ApplicationInfo>()
        for (pkg in packageList) {
            allAppsMap[pkg]?.let { drawerApps.add(it) }
        }
        return drawerApps
    }

    private fun loadListApps(): List<ApplicationInfo> {
        val prefs = getSharedPreferences("list_prefs", MODE_PRIVATE)
        val packageNames = prefs.getStringSet("list_packages", emptySet()) ?: emptySet()

        Log.d("loadListApps", "$packageNames")

        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = getSystemService(Context.USER_SERVICE) as UserManager
        val users = userManager.userProfiles
        val currentPackage = applicationContext.packageName
        val listApps = mutableListOf<ApplicationInfo>()

        if (packageNames.isEmpty()) {
            for (user in users) {
                val activities = launcherApps.getActivityList(null, user as UserHandle)
                for (activity in activities) {
                    try {
                        val appInfo = activity.applicationInfo
                        listApps.add(appInfo)
                    } catch (_: PackageManager.NameNotFoundException) {
                    }
                }
            }
            return listApps.filter {
                packageManager.getLaunchIntentForPackage(it.packageName) != null &&
                        it.packageName != currentPackage // exclude "Void" itself
            }
                .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        } else {
            for (user in users) {
                val activities = launcherApps.getActivityList(null, user as UserHandle)
                for (activity in activities) {
                    val pkgName = activity.applicationInfo.packageName
                    if (pkgName in packageNames) {
                        listApps.add(activity.applicationInfo)
                    }
                }
            }
            return listApps.sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        }
    }

    private fun vibratePhone(millis: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return event?.let { gestureDetector.onTouchEvent(it) } == true || super.onTouchEvent(event)
    }

    inner class SwipeGestureListener : GestureDetector.SimpleOnGestureListener() {
        private val swipeThreshold = 100
        private val swipeVelocityThreshold = 100
        private val edgeSwipeThreshold = 50  // px from edge to detect swipe

        @RequiresApi(Build.VERSION_CODES.P)
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false

            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y

            // Swipe must start within left edgeSwipeThreshold
            val screenWidth = resources.displayMetrics.widthPixels

            if (abs(diffX) > abs(diffY) &&
                abs(diffX) > swipeThreshold &&
                abs(velocityX) > swipeVelocityThreshold
            ) {
                if (diffX < 0 && e1.x > screenWidth - edgeSwipeThreshold) {
                    // Swiped left (right → left)
                    if (SharedPreferencesManager.isSwipeToSettingsEnabled(this@MainActivity)) {
                        openSettings()
                    } else {
                        Toast.makeText(this@MainActivity, "Enable 'Swipe left to open settings' in Gestures", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }

                if (diffX > 0 && e1.x < edgeSwipeThreshold) {
                    // Swipe right → Lock screen
                    if (SharedPreferencesManager.isSwipeToLockEnabled(this@MainActivity)) {
                        vibratePhone(100)
                        AppAccessibilityService.lockNowWithAccessibility()
                    } else {
                        Toast.makeText(this@MainActivity, "Enable 'Swipe right to Lock' in Gestures", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
            }
            return false
        }

    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

}