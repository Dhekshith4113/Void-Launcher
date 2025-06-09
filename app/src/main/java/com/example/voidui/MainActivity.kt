package com.example.voidui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
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

    private val requestCodePostNotifications = 1001
    private var needsRefresh = false

    @SuppressLint("ClickableViewAccessibility")
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applySavedTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!isAccessibilityServiceEnabled()) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        if (!UsageStatsManagerUtils.hasUsageStatsPermission(this)) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        val prefs = getSharedPreferences("minima_prefs", MODE_PRIVATE)
        val askedForNotifications = prefs.getBoolean("asked_notifications", false)

        val prefsInstalledApps = getSharedPreferences("installed_apps", MODE_PRIVATE)
        prefsInstalledApps.edit().putStringSet("apps", getInstalledAppPackages().toSet()).apply()

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        gestureDetector = GestureDetector(this, SwipeGestureListener())

        if (!askedForNotifications) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        requestCodePostNotifications
                    )
                } else {
                    checkAndPromptNotificationSettings(prefs)
                }
            } else {
                checkAndPromptNotificationSettings(prefs)
            }
        }

        listAdapter = AppListAdapter(this, loadListApps().toMutableList(), packageManager,
            refreshAppList = {
                needsRefresh = true
            }
        ) { appInfo ->
            val packageName = appInfo.packageName
            if (shouldShowTimer(this, packageName)) {
                showTimeLimitDialog(appInfo)
            } else {
                launchAppDirectly(appInfo)
            }
        }

        listAdapter.setApps(getNewlyInstalledApps())

        recyclerView.adapter = listAdapter

        drawerRecyclerView = findViewById(R.id.appDrawerRecyclerView)
        drawerAdapter = AppDrawerAdapter(this, packageManager, loadDrawerApps().toMutableList(), ::saveDrawerApps,
            refreshAppList = {
                needsRefresh = true
            }
        ) { appInfo ->
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

        drawerRecyclerView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                drawerRecyclerView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                drawerRecyclerView.invalidateItemDecorations()
            }
        })

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                drawerAdapter.swapItems(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // No swipe action
            }

            override fun isLongPressDragEnabled(): Boolean {
                return true
            }
        }

        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(drawerRecyclerView)

        drawerRecyclerView.setOnDragListener { view, event ->
            when (event.action) {

                DragEvent.ACTION_DRAG_ENTERED -> {
                    view.setBackgroundColor(Color.parseColor("#FF121212"))
                    true
                }

                DragEvent.ACTION_DROP -> {
                    val app = event.localState as ApplicationInfo
                    val recyclerView = view as RecyclerView

                    // Prevent duplicate addition or overfilling
                    if (drawerAdapter.getApps().any { it.packageName == app.packageName } || drawerAdapter.getApps().size >= 4) {
                        Toast.makeText(this, "Cannot add more than 4 apps", Toast.LENGTH_SHORT).show()
                        return@setOnDragListener true
                    }

                    val x = event.x.toInt()
                    var insertPosition = drawerAdapter.itemCount // default to end

                    // Find the drop position by comparing x with midpoints of each child
                    for (i in 0 until recyclerView.childCount) {
                        val child = recyclerView.getChildAt(i)
                        val left = child.left
                        val right = child.right
                        val midpoint = (left + right) / 2

                        if (x < midpoint) {
                            insertPosition = recyclerView.getChildAdapterPosition(child)
                            break
                        }
                    }

                    drawerAdapter.addAppAtPosition(app, insertPosition)
                    listAdapter.removeApp(app)
                    saveDrawerApps(drawerAdapter.getApps())
                    saveListApps(listAdapter.getApps())

                    drawerRecyclerView.invalidateItemDecorations()

                    true
                }

                DragEvent.ACTION_DRAG_ENDED, DragEvent.ACTION_DRAG_EXITED -> {
                    view.setBackgroundColor(Color.TRANSPARENT)
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
                else -> true
            }
        }

        drawerGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val child = drawerRecyclerView.findChildViewUnder(e.x, e.y)
                return if (child == null) {
                    if (SharedPreferencesManager.isDoubleTapToLockEnabled(this@MainActivity)) {
                        vibratePhone()
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

        if (UsageStatsManagerUtils.hasUsageStatsPermission(this)) {
            startService(Intent(this, TimerMonitorService::class.java))
        }

    }

    override fun onResume() {
        super.onResume()
        clearExpiredNewAppTags()
        checkForNewlyInstalledApps()
//        if (needsRefresh) {
        refreshList()
//            needsRefresh = false
//        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestCodePostNotifications) {
            val prefs = getSharedPreferences("minima_prefs", MODE_PRIVATE)
            checkAndPromptNotificationSettings(prefs)
        }
    }

    private fun refreshList() {
        listAdapter.updateData(loadListApps().toMutableList())
        drawerAdapter.updateData(loadDrawerApps().toMutableList())
        listAdapter.setApps(getNewlyInstalledApps())
    }

    private fun checkAndPromptNotificationSettings(prefs: SharedPreferences) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("Enable Notifications")
                .setMessage("Void needs notification access to show important messages. Please enable notifications.")
                .setPositiveButton("Open Settings") { _, _ ->
                    val intent = Intent().apply {
                        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    }
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        prefs.edit().putBoolean("asked_notifications", true).apply()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = "${packageName}/${AppAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(":").any { it.equals(expectedComponent, ignoreCase = true) }
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

        Log.d("loadListApps", "packageNames = $packageNames")

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

    private fun checkForNewlyInstalledApps() {
        val prefs = getSharedPreferences("installed_apps", MODE_PRIVATE)
        val prevAppSet = prefs.getStringSet("apps", emptySet()) ?: emptySet()
        val currentAppSet = getInstalledAppPackages().toSet()

        val newlyInstalled = currentAppSet - prevAppSet

        if (newlyInstalled.isNotEmpty()) {
            val editor = prefs.edit()
            val timestamp = System.currentTimeMillis()

            newlyInstalled.forEach { pkg ->
                editor.putLong("new_app_time_$pkg", timestamp)
            }

            editor.apply()
            needsRefresh = true
        }

        // Always update the installed app set
        prefs.edit().putStringSet("apps", currentAppSet).apply()
    }

    private fun getInstalledAppPackages(): List<String> {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return apps
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { it.packageName }
    }

    private fun getNewlyInstalledApps(): Set<String> {
        val prefs = getSharedPreferences("installed_apps", MODE_PRIVATE)
        val allEntries = prefs.all
        val now = System.currentTimeMillis()
        val oneDayMillis = 24 * 60 * 60 * 1000L

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
        val prefs = getSharedPreferences("installed_apps", MODE_PRIVATE)
        val editor = prefs.edit()
        val now = System.currentTimeMillis()
        val oneDayMillis = 24 * 60 * 60 * 1000L

        prefs.all.forEach { (key, value) ->
            if (key.startsWith("new_app_time_")) {
                val time = value as? Long ?: return@forEach
                if (now - time >= oneDayMillis) {
                    editor.remove(key)
                }
            }
        }

        editor.apply()
    }

    private fun vibratePhone() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return event?.let { gestureDetector.onTouchEvent(it) } == true || super.onTouchEvent(event)
    }

    inner class SwipeGestureListener : GestureDetector.SimpleOnGestureListener() {
        private val SWIPE_THRESHOLD = 100
        private val SWIPE_VELOCITY_THRESHOLD = 100
        private val EDGE_SWIPE_THRESHOLD = 50  // px from edge to detect swipe

        @RequiresApi(Build.VERSION_CODES.P)
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false

            val startX = e1.x
            val endX = e2.x
            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y

            // Swipe must start within left EDGE_SWIPE_THRESHOLD
            val screenWidth = resources.displayMetrics.widthPixels

            if (abs(diffX) > abs(diffY) &&
                abs(diffX) > SWIPE_THRESHOLD &&
                abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
            ) {
                if (diffX < 0 && startX > screenWidth - EDGE_SWIPE_THRESHOLD) {
                    // Swiped left (right → left)
                    if (SharedPreferencesManager.isSwipeToSettingsEnabled(this@MainActivity)) {
                        openSettings()
                    } else {
                        Toast.makeText(this@MainActivity, "Please enable 'Swipe left to open settings' in Gestures", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }

                if (diffX > 0 && startX < EDGE_SWIPE_THRESHOLD) {
                    // Swipe right → Lock screen
                    if (SharedPreferencesManager.isSwipeToLockEnabled(this@MainActivity)) {
                        vibratePhone()
                        AppAccessibilityService.lockNowWithAccessibility()
                    } else {
                        Toast.makeText(this@MainActivity, "Please enable 'Swipe right to Lock' in Gestures", Toast.LENGTH_SHORT).show()
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