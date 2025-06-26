package com.example.voidui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.DragEvent
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class MainActivity: AppCompatActivity(), GradientUpdateListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var listAdapter: AppListAdapter
    private lateinit var gestureDetector: GestureDetector
    private lateinit var drawerGestureDetector: GestureDetector
    private lateinit var drawerAdapter: AppDrawerAdapter
    private lateinit var drawerRecyclerView: RecyclerView

    private var needRefresh = false
    private var toastShownThisDrag = false
    private var shouldMoveIndicator = true
    private var gradientOverlay: GradientOverlayView? = null

    private val bubbleBackground by lazy {
        ContextCompat.getDrawable(this, R.drawable.bubble_background)
    }

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

//        if (!isDefaultLauncher(this)) {
//            finish()
//            startActivity(Intent(this, DefaultLauncherActivity::class.java))
//        }

        gestureDetector = GestureDetector(this, SwipeGestureListener())

        recyclerView = findViewById(R.id.recyclerView)

        listAdapter =
            AppListAdapter(this, loadListApps().toMutableList(), packageManager, refreshList = {
                needRefresh = true
            }) { appInfo ->
                val packageName = appInfo.packageName
                Log.d("MainActivity", "App name is $packageName")
                if (shouldShowTimer(this, packageName)) {
                    showTimeLimitDialog(appInfo)
                } else {
                    launchAppDirectly(appInfo)
                }
            }

        if (SharedPreferencesManager.isAppDrawerEnabled(this)) {
            val appDrawerRowSize = SharedPreferencesManager.getAppDrawerRowSize(this)
            recyclerView.layoutManager = GridLayoutManager(this, appDrawerRowSize)
        } else {
            recyclerView.layoutManager = LinearLayoutManager(this)
        }

        recyclerView.adapter = listAdapter
        setupGradientOverlay()

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

//        if (!SharedPreferencesManager.isAppDrawerEnabled(this)) {
//            val scroller = findViewById<AlphabetScrollerView>(R.id.alphabetScroller)
//            val apps = listAdapter.getApps()
//            val usedAlphabets =
//                apps.map { normalizeAppName(it.loadLabel(packageManager).toString()).first().uppercaseChar() }.distinct()
//                    .sorted()
//            val indexMap = getAlphabetIndexMap(apps)
//            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
//
////            scroller.setup(usedAlphabets, indexMap, layoutManager)
////            scroller.enableFloatingBubble(findViewById(R.id.layoutMainActivity))
//            scroller.setup(usedAlphabets, indexMap, layoutManager, bubbleBackground)
//        }

        setupAlphabetScroller()

        drawerRecyclerView = findViewById(R.id.appDrawerRecyclerView)
        val centerSpacing = CenterSpacingDecoration()

        drawerRecyclerView.visibility = if (SharedPreferencesManager.isMiniDrawerEnabled(this)) {
            View.VISIBLE
        } else {
            View.GONE
        }

        drawerAdapter = AppDrawerAdapter(
            this,
            packageManager,
            loadDrawerApps().toMutableList(),
            ::saveDrawerApps,
            refreshList = {
                needRefresh = true
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
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            addItemDecoration(centerSpacing)
            adapter = drawerAdapter
//            itemAnimator = DrawerItemAnimator() // Custom animator for smooth transitions
            itemAnimator = null // Remove animations

            // Ensure proper initial layout
            viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                    post {
                        centerSpacing.invalidateSpacing()
                        invalidateItemDecorations()
                    }
                }
            })
        }

        drawerAdapter.onAppDragStarted = { app ->
            val currentApps = drawerAdapter.getApps()
            val index = currentApps.indexOfFirst { it.packageName == app.packageName }
            if (index != -1) {
                val updatedList = currentApps.toMutableList()
                updatedList.removeAt(index)
                drawerAdapter.updateData(updatedList)
            }
        }

        drawerRecyclerView.setOnDragListener { view, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    toastShownThisDrag = false
                    shouldMoveIndicator = true
                    true
                }

                DragEvent.ACTION_DRAG_ENTERED -> {
                    if (!drawerAdapter.hasDropIndicator()) {
                        drawerAdapter.insertDropIndicator(0)
                    }
                    true
                }

                DragEvent.ACTION_DRAG_LOCATION -> {
                    val x = event.x.toInt()
                    val y = event.y.toInt()
                    val recyclerView = view as RecyclerView
                    val draggedApp = event.localState as ApplicationInfo
                    val isAppFromDrawer = drawerAdapter.getApps().any { it.packageName == draggedApp.packageName }
                    val drawerSize = SharedPreferencesManager.getMiniAppDrawerCount(this)
                    val currentRealApps = drawerAdapter.getApps().count { it.packageName != AppDrawerAdapter.DROP_INDICATOR_PACKAGE }

                    // Check if we can add more apps
                    if (!isAppFromDrawer && currentRealApps >= drawerSize) {
                        if (!toastShownThisDrag) {
                            Toast.makeText(this, "Cannot add more than $drawerSize apps", Toast.LENGTH_SHORT).show()
                            shouldMoveIndicator = false
                            toastShownThisDrag = true
                        }
                        return@setOnDragListener true
                    }

                    if (!shouldMoveIndicator) return@setOnDragListener true

                    // Find which child view is being directly hovered over
                    var hoveredChild: View? = null
                    var hoveredPosition = -1

                    for (i in 0 until recyclerView.childCount) {
                        val child = recyclerView.getChildAt(i)

                        // Check if drag point is within the bounds of this child
                        if (x >= child.left && x <= child.right &&
                            y >= child.top && y <= child.bottom) {
                            hoveredChild = child
                            hoveredPosition = recyclerView.getChildAdapterPosition(child)
                            break
                        }
                    }

                    val currentDropIndex = drawerAdapter.getApps().indexOfFirst {
                        it.packageName == AppDrawerAdapter.DROP_INDICATOR_PACKAGE
                    }

                    if (hoveredChild != null && hoveredPosition != -1 && hoveredPosition < drawerAdapter.getApps().size) {
                        // We're directly hovering over a specific child
                        val hoveredApp = drawerAdapter.getApps()[hoveredPosition]

                        if (hoveredApp.packageName == AppDrawerAdapter.DROP_INDICATOR_PACKAGE) {
                            // Hovering over drop indicator - don't move it
                            return@setOnDragListener true
                        }

                        // We're hovering over a real app - determine where to place drop indicator
                        val targetPos = if (currentDropIndex == -1) {
                            // No drop indicator yet, place it based on drag direction
                            if (x < hoveredChild.left + hoveredChild.width / 2) {
                                hoveredPosition // Place before the hovered app
                            } else {
                                hoveredPosition + 1 // Place after the hovered app
                            }
                        } else {
                            // Drop indicator exists, move it to the opposite side of hovered app
                            if (currentDropIndex < hoveredPosition) {
                                // Drop indicator is to the left, move it to the right
                                hoveredPosition + 1
                            } else if (currentDropIndex > hoveredPosition) {
                                // Drop indicator is to the right, move it to the left
                                hoveredPosition
                            } else {
                                // Don't move if already adjacent
                                return@setOnDragListener true
                            }
                        }

                        val safeTargetPos = targetPos.coerceIn(0, drawerAdapter.itemCount)
                        if (currentDropIndex != safeTargetPos) {
                            if (currentDropIndex == -1) {
                                drawerAdapter.insertDropIndicator(safeTargetPos)
                            } else {
                                drawerAdapter.moveDropIndicator(safeTargetPos)
                            }
                        }
                    } else {
                        // Not hovering over any specific child
                        // Only insert drop indicator if it doesn't exist, at the edges
                        if (currentDropIndex == -1) {
                            val targetPos = if (x < recyclerView.width / 3) {
                                0 // Left edge
                            } else if (x > recyclerView.width * 2 / 3) {
                                drawerAdapter.itemCount // Right edge
                            } else {
                                // Middle area - don't insert drop indicator
                                return@setOnDragListener true
                            }
                            drawerAdapter.insertDropIndicator(targetPos)
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

                    val isAppFromDrawer = drawerAdapter.getApps().any { it.packageName == draggedApp.packageName }
                    val drawerSize = SharedPreferencesManager.getMiniAppDrawerCount(this)
                    val currentRealApps = drawerAdapter.getApps().count { it.packageName != AppDrawerAdapter.DROP_INDICATOR_PACKAGE }

                    if (view == drawerRecyclerView) {
                        if (!isAppFromDrawer && currentRealApps >= drawerSize) {
                            // Can't add more apps
                            return@setOnDragListener true
                        } else {
                            if (!isAppFromDrawer) {
                                // Moving from list to drawer
                                listAdapter.removeApp(draggedApp)
                            }
                            drawerAdapter.addAppAtPosition(draggedApp, dropIndex)
                        }
                    } else {
                        // Dropped into main app list
                        if (isAppFromDrawer) {
                            drawerAdapter.removeApp(draggedApp)
                            listAdapter.addApp(draggedApp)
                        }
                    }

                    saveDrawerApps(drawerAdapter.getApps().filter { it.packageName != AppDrawerAdapter.DROP_INDICATOR_PACKAGE })
                    saveListApps(listAdapter.getApps())

                    // Force layout refresh
                    drawerRecyclerView.post {
                        centerSpacing.invalidateSpacing()
                        drawerRecyclerView.invalidateItemDecorations()
                    }
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
                    saveDrawerApps(drawerAdapter.getApps().filter { it.packageName != AppDrawerAdapter.DROP_INDICATOR_PACKAGE })
                    saveListApps(listAdapter.getApps())
                    true
                }
                else -> true
            }
        }

        drawerGestureDetector =
            GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val child = drawerRecyclerView.findChildViewUnder(e.x, e.y)
                    return if (child == null) {
                        if (SharedPreferencesManager.isDoubleTapToLockEnabled(this@MainActivity)) {
                            vibratePhone(100)
                            AppAccessibilityService.lockNowWithAccessibility()
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Please enable 'Double tap on the mini app drawer to Lock' in Gestures",
                                Toast.LENGTH_SHORT
                            ).show()
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
        }

    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onResume() {
        super.onResume()
        clearExpiredNewAppTags()
        checkNewlyInstalledApps()
        if (needRefresh) {
            listAdapter.setApps(getNewlyInstalledApps())
            listAdapter.updateData(loadListApps().toMutableList())
            drawerAdapter.updateData(loadDrawerApps().toMutableList())
            needRefresh = false
        }
//        if (!isDefaultLauncher(this)) {
//            finish()
//            startActivity(Intent(this, DefaultLauncherActivity::class.java))
//        }
        if (SharedPreferencesManager.isRefreshViewEnabled(this)) {
            if (!SharedPreferencesManager.isMiniDrawerEnabled(this)) {
                for (app in loadDrawerApps()) {
                    listAdapter.addApp(app)
                }
                saveListApps(listAdapter.getApps())
            } else{
                for (app in loadDrawerApps()) {
                    listAdapter.removeApp(app)
                }
                saveListApps(listAdapter.getApps())
            }
            val miniAppDrawerCount = SharedPreferencesManager.getMiniAppDrawerCount(this)
            if (miniAppDrawerCount < drawerAdapter.getApps().size) {
                for (i in 0 until drawerAdapter.getApps().size - miniAppDrawerCount) {
                    val extraApp = loadDrawerApps().last()
                    drawerAdapter.removeApp(extraApp)
                    listAdapter.addApp(extraApp)
                    saveDrawerApps(drawerAdapter.getApps())
                    saveListApps(listAdapter.getApps())
                }
            }
            finish()
            startActivity(Intent(this, MainActivity::class.java))
            SharedPreferencesManager.setRefreshViewEnabled(this, false)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun updateGradients() {
        updateGradientAlphas()
    }

    private fun setupAlphabetScroller() {
        if (!SharedPreferencesManager.isAppDrawerEnabled(this)) {
            val scroller = findViewById<AlphabetScrollerView>(R.id.alphabetScroller)
            val apps = listAdapter.getApps()
            val usedAlphabets = apps.map {
                normalizeAppName(it.loadLabel(packageManager).toString()).first().uppercaseChar()
            }.distinct().sorted()
            val indexMap = getAlphabetIndexMap(apps)
            val layoutManager = recyclerView.layoutManager as LinearLayoutManager

//            scroller.setup(usedAlphabets, indexMap, layoutManager, bubbleBackground)

            scroller.setup(usedAlphabets, indexMap, layoutManager)
            scroller.enableFloatingBubble(findViewById(R.id.layoutMainActivity))
            scroller.setGradientUpdateListener(this)   // IMPORTANT: Set the gradient update listener
        }
    }

//    private fun setupGradientOverlay() {
//        // Create gradient overlay
//        gradientOverlay = GradientOverlayView(this)
//
//        // Get RecyclerView's parent to add overlay
//        val recyclerViewParent = recyclerView.parent as ViewGroup
//
//        // Create overlay params matching RecyclerView's layout params
//        val recyclerViewParams = recyclerView.layoutParams
//        val overlayParams = ViewGroup.LayoutParams(
//            recyclerViewParams.width,
//            recyclerViewParams.height
//        )
//
//        // Position overlay to match RecyclerView exactly
//        if (overlayParams.width == ViewGroup.LayoutParams.MATCH_PARENT) {
//            overlayParams.width = ViewGroup.LayoutParams.MATCH_PARENT
//        }
//        if (overlayParams.height == ViewGroup.LayoutParams.MATCH_PARENT) {
//            overlayParams.height = ViewGroup.LayoutParams.MATCH_PARENT
//        }
//
//        // Add gradient overlay as the last child so it appears on top of RecyclerView
//        recyclerViewParent.addView(gradientOverlay, overlayParams)
//
//        // Position the overlay exactly over the RecyclerView
//        gradientOverlay?.post {
//            gradientOverlay?.let { overlay ->
//                overlay.x = recyclerView.x
//                overlay.y = recyclerView.y
//                overlay.layoutParams.width = recyclerView.width
//                overlay.layoutParams.height = recyclerView.height
//            }
//        }
//
//        // Set up scroll listener for smooth gradient updates
//        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
//            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
//                super.onScrolled(recyclerView, dx, dy)
//                updateGradientAlphas()
//            }
//        })
//
//        // Initial gradient state
//        updateGradientAlphas()
//    }

    private fun setupGradientOverlay() {
        // Create gradient overlay
        gradientOverlay = GradientOverlayView(this)

        // Get RecyclerView's current parent and position
        val currentParent = recyclerView.parent as ViewGroup
        val recyclerViewIndex = currentParent.indexOfChild(recyclerView)
        val recyclerViewParams = recyclerView.layoutParams

        // Remove RecyclerView from its current parent
        currentParent.removeView(recyclerView)

        // Create a FrameLayout container
        val frameContainer = FrameLayout(this)
        frameContainer.layoutParams = recyclerViewParams

        // Add RecyclerView to FrameLayout with MATCH_PARENT params
        val recyclerParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        frameContainer.addView(recyclerView, recyclerParams)

        // Add gradient overlay to FrameLayout (on top of RecyclerView)
        val overlayParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        frameContainer.addView(gradientOverlay, overlayParams)

        // Add the FrameLayout container back to the original parent
        currentParent.addView(frameContainer, recyclerViewIndex)

        // Set up scroll listener for smooth gradient updates
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                updateGradientAlphas()
            }
        })

        // Initial gradient state
        updateGradientAlphas()
    }

    private fun updateGradientAlphas() {
        val (topAlpha, bottomAlpha) = recyclerView.calculateGradientAlphas()
        gradientOverlay?.updateGradients(topAlpha, bottomAlpha)
    }

    fun RecyclerView.calculateGradientAlphas(): Pair<Float, Float> {
        val layoutManager = this.layoutManager as? LinearLayoutManager ?: return 0f to 1f

        // Check if we can scroll up (not at top)
        val canScrollUp = canScrollVertically(-1)

        // Check if we can scroll down (not at bottom)
        val canScrollDown = canScrollVertically(1)

        // Calculate precise scroll position
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        val firstVisibleView = layoutManager.findViewByPosition(firstVisiblePosition)

        val topAlpha = when {
            !canScrollUp -> 0f // At the very top
            firstVisiblePosition == 0 && firstVisibleView != null -> {
                // Near top, calculate based on first item offset
                val offset = firstVisibleView.top
                val viewHeight = firstVisibleView.height
                if (offset >= 0) 0f else (-offset.toFloat() / (viewHeight * 0.5f)).coerceIn(0f, 1f)
            }
            else -> 1f // Scrolled down significantly
        }

        val bottomAlpha = when {
            !canScrollDown -> 0f // At the very bottom
            else -> {
                // Calculate based on how close we are to bottom
                val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
                val itemCount = layoutManager.itemCount
                val lastVisibleView = layoutManager.findViewByPosition(lastVisiblePosition)

                if (lastVisiblePosition == itemCount - 1 && lastVisibleView != null) {
                    // Near bottom, calculate based on last item offset
                    val offset = lastVisibleView.bottom - height
                    val viewHeight = lastVisibleView.height
                    if (offset <= 0) 0f else (offset.toFloat() / (viewHeight * 0.5f)).coerceIn(0f, 1f)
                } else 1f
            }
        }

        return topAlpha to bottomAlpha
    }

    private fun getAlphabetIndexMap(apps: List<ApplicationInfo>): Map<Char, Int> {
        val map = mutableMapOf<Char, Int>()
        for ((index, app) in apps.withIndex()) {
            val label = normalizeAppName(app.loadLabel(packageManager).toString())
            val firstChar = label.firstOrNull()?.uppercaseChar() ?: continue
            if (!map.containsKey(firstChar)) {
                map[firstChar] = index
            }
        }
        return map
    }

    private fun checkNewlyInstalledApps() {
        val prefsNewApps = getSharedPreferences("new_apps", MODE_PRIVATE)
        val prefsList = getSharedPreferences("list_prefs", MODE_PRIVATE)
        val prefsDrawer = getSharedPreferences("drawer_prefs", MODE_PRIVATE)
        val currentAppList = prefsList.getStringSet("list_packages", emptySet()) ?: emptySet()
        val currentDrawerList = prefsDrawer.getString("drawer_ordered_packages", "")?.split(",")
            ?.filter { it.isNotBlank() } ?: emptyList()

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

        newAppInfoList.filter {
            packageManager.getLaunchIntentForPackage(it.packageName) != null &&
                    it.packageName in currentDrawerList
        }.sortedBy {
            normalizeAppName(it.loadLabel(packageManager).toString()).lowercase()
        }

        val newApps =
            newAppList.filterNot { it in currentAppList || it in currentDrawerList.toSet() }
//        Log.d("App List", "Current App List = $currentAppList")
//        Log.d("App List", "New App List = $newAppList")
//        Log.d("App List", "New Apps = $newApps")
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
        val appName = normalizeAppName(appInfo.loadLabel(applicationContext.packageManager).toString())

        fun startTimerAndLaunchApp(minutes: Int) {
            if (minutes > 0) {

                Log.d("MainActivity", "Set timer for $appName")
                AppTimerManager.setTimer(appName, minutes * 60 * 1000L)

                val intent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
                if (intent != null) {
                    startActivity(intent)
                    Toast.makeText(this, "Timer set to $minutes min", Toast.LENGTH_SHORT).show()
                    SharedPreferencesManager.setOneMinToastShown(this, appName, false)
                } else {
                    Toast.makeText(this, "Cannot launch app", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
        }

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogView.findViewById<TextView>(R.id.dialogTitle).text =
            "How much time would you like\nto spend on $appName?"
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
        val packageList =
            prefs.getString("drawer_ordered_packages", "")?.split(",")?.filter { it.isNotBlank() }
                ?: emptyList()
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

//        Log.d("loadListApps", "$packageNames")

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
                .sortedBy { normalizeAppName(it.loadLabel(packageManager).toString()).lowercase() }
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
            return listApps.sortedBy { normalizeAppName(it.loadLabel(packageManager).toString()).lowercase() }
        }
    }

    fun normalizeAppName(label: String): String {
        val prefixesToRemove = listOf("Samsung ", "Google ", "Galaxy ")
        var normalized = label

        for (prefix in prefixesToRemove) {
            if (label.startsWith(prefix, ignoreCase = true)) {
                normalized = normalized.removePrefix(prefix).trim()
                break
            }
        }

        return normalized
    }

    private fun vibratePhone(millis: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun isDefaultLauncher(context: Context): Boolean {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == context.packageName
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
                        Toast.makeText(
                            this@MainActivity,
                            "Enable 'Swipe left to open settings' in Gestures",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return true
                }

                if (diffX > 0 && e1.x < edgeSwipeThreshold) {
                    // Swipe right → Lock screen
                    if (SharedPreferencesManager.isSwipeToLockEnabled(this@MainActivity)) {
                        vibratePhone(100)
                        AppAccessibilityService.lockNowWithAccessibility()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Enable 'Swipe right to Lock' in Gestures",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return true
                }
            }
            return false
        }

    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

}

interface GradientUpdateListener {
    fun updateGradients()
}