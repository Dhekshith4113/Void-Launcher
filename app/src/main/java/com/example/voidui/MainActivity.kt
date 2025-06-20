package com.example.voidui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Resources
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
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.exp

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

    private var lastAnimationUpdateTime = 0L
    private val animationThrottleMs = 16L // ~60 FPS

    val drawerSize = 4

    private val bubbleBackground by lazy {
        ContextCompat.getDrawable(this, R.drawable.bubble_background)
    }
    private val Int.dp: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()

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

        if (!isDefaultLauncher(this)) {
            finish()
            startActivity(Intent(this, DefaultLauncherActivity::class.java))
        }

        gestureDetector = GestureDetector(this, SwipeGestureListener())

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

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

        val alphabetScroller = findViewById<LinearLayout>(R.id.alphabetScroller)
        val usedAlphabets: List<Char> = listAdapter.getApps()
            .map { it.loadLabel(packageManager).first().uppercaseChar() }
            .distinct()
            .sorted()
        alphabetScroller.removeAllViews()
        usedAlphabets.forEach { letter ->
            val textView = TextView(this).apply {
                text = letter.toString()
                textSize = 14f
                setTextColor(getColor(R.color.textColorPrimary))
                gravity = Gravity.CENTER  // center the text
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,  // wrap content so background isn't stretched
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(4.dp, 2.dp, 4.dp, 2.dp)
                    gravity = Gravity.END  // optional, if you want to center in parent
                }
                setPadding(4.dp, 2.dp, 4.dp, 2.dp)  // optional for spacing inside the bubble
            }
            alphabetScroller.addView(textView)
        }

        val apps = listAdapter.getApps()
        val indexMap = getAlphabetIndexMap(apps)
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        var lastIndex = -1 // Keep this outside the listener (class-level or view-level)

//////////////////////////////// STATIC SCROLL BAR WITH AN INDICATOR ////////////////////////////////
//
//        alphabetScroller.setOnTouchListener { _, event ->
//            when (event.action) {
//                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
//                    val itemHeight = alphabetScroller.height / usedAlphabets.size
//                    val index = (event.y / itemHeight).toInt().coerceIn(0, usedAlphabets.size - 1)
//                    if (index != lastIndex) {
//                        val selectedChar = usedAlphabets.elementAt(index)
//
//                        for (i in 0 until alphabetScroller.childCount) {
//                            val child = alphabetScroller.getChildAt(i)
//                            child.translationX = 0f
//                            child.background = null
//                        }
//
//                        indexMap[selectedChar]?.let {
//                            layoutManager.scrollToPositionWithOffset(
//                                it,
//                                0
//                            )
//                        }
//
//                        alphabetScroller.getChildAt(index).translationX = -150f
//                        alphabetScroller.getChildAt(index).background = bubbleBackground
//                    }
//                    lastIndex = index
//                }
//
//                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
//                    for (i in 0 until alphabetScroller.childCount) {
//                        val child = alphabetScroller.getChildAt(i)
//                        child.translationX = 0f
//                        child.background = null
//                    }
//                    lastIndex = -1
//                }
//            }
//            true
//        }
//
/////////////////////////////////////////////////////////////////////////////////////////////////////

//////////////////////////// STATIC ANIMATED SCROLL BAR WITH AN INDICATOR ///////////////////////////

        alphabetScroller.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val itemHeight = alphabetScroller.height / usedAlphabets.size
                    val index = (event.y / itemHeight).toInt().coerceIn(0, usedAlphabets.size - 1)
                    if (index != lastIndex) {
                        val selectedChar = usedAlphabets.elementAt(index)
                        indexMap[selectedChar]?.let {
                            layoutManager.scrollToPositionWithOffset(
                                it,
                                0
                            )
                        }

                        val sigma = 1.5f

                        for (i in 0 until alphabetScroller.childCount) {
                            val child = alphabetScroller.getChildAt(i)
                            val offset = i - index

                            val distance = offset.toFloat()
                            val curveFactor = exp(-(distance * distance) / (2 * sigma * sigma))

                            val scale = 0.85f + (0.15f * curveFactor)
                            val alpha = 0.4f + (0.6f * curveFactor)

                            ViewCompat.animate(child).cancel()
                            ViewCompat.animate(child)
                                .scaleX(scale)
                                .scaleY(scale)
                                .alpha(alpha)
                                .setDuration(75)
                                .setInterpolator(LinearInterpolator())
                                .withLayer()
                                .start()

                            if (offset == 0) {
                                child.translationX = -150f
                                child.background = bubbleBackground
                            } else {
                                child.translationX = 0f
                                child.background = null
                            }
                        }
                    }
                    lastIndex = index
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    for (i in 0 until alphabetScroller.childCount) {
                        val child = alphabetScroller.getChildAt(i)
                        ViewCompat.animate(child).cancel()
                        ViewCompat.animate(child)
                            .translationX(0f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(75)
                            .setInterpolator(OvershootInterpolator())
                            .withLayer()
                            .start()
                        child.background = null
                    }
                    lastIndex = -1
                }
            }
            true
        }

/////////////////////////////////////////////////////////////////////////////////////////////////////

/////////////////////////// DYNAMIC BENDING SCROLL BAR WITH AN INDICATOR ////////////////////////////
//
//        alphabetScroller.setOnTouchListener { _, event ->
//            when (event.action) {
//                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
//                    val itemHeight = alphabetScroller.height / usedAlphabets.size
//                    val index = (event.y / itemHeight).toInt().coerceIn(0, usedAlphabets.size - 1)
//
//                    if (index != lastIndex) {
//                        val selectedChar = usedAlphabets.elementAt(index)
//                        indexMap[selectedChar]?.let { layoutManager.scrollToPositionWithOffset(it, 0) }
//
//                        // Reset all views
//                        for (i in 0 until alphabetScroller.childCount) {
//                            val child = alphabetScroller.getChildAt(i)
//                            child.translationX = 0f
//                            child.background = null
//                        }
//
//                        // Apply bend effect ±3 items
//                        for (offset in -4..4) {
//                            val childIndex = index + offset
//                            if (childIndex in 0 until alphabetScroller.childCount) {
//                                val child = alphabetScroller.getChildAt(childIndex)
//                                val translation = when (offset) {
//                                    0 -> -150f
//                                    -1, 1 -> -140f
//                                    -2, 2 -> -75f
//                                    -3, 3 -> -20f
//                                    else -> 0f
//                                }
//                                child.translationX = translation
//
//                                if (offset == 0) {
//                                    child.background = ContextCompat.getDrawable(this, R.drawable.bubble_background)
//                                }
//                            }
//                        }
//
//                        lastIndex = index
//                    }
//                }
//
//                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
//                    // Reset all on touch release
//                    for (i in 0 until alphabetScroller.childCount) {
//                        val child = alphabetScroller.getChildAt(i)
//                        child.translationX = 0f
//                        child.background = null
//                    }
//                    lastIndex = -1
//                }
//            }
//            true
//        }
//
/////////////////////////////////////////////////////////////////////////////////////////////////////

/////////////////////// DYNAMIC ANIMATED BENDING SCROLL BAR WITH AN INDICATOR ///////////////////////
//
//        alphabetScroller.setOnTouchListener { _, event ->
//            when (event.action) {
//                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
//                    val itemHeight = alphabetScroller.height / usedAlphabets.size
//                    val index = (event.y / itemHeight).toInt().coerceIn(0, usedAlphabets.size - 1)
//
//                    if (index != lastIndex) {
//                        val selectedChar = usedAlphabets.elementAt(index)
//                        indexMap[selectedChar]?.let {
//                            layoutManager.scrollToPositionWithOffset(it, 0)
//                        }
//
//                        // Reset all views with animation
//                        for (i in 0 until alphabetScroller.childCount) {
//                            val child = alphabetScroller.getChildAt(i)
//                            child.animate()
//                                .translationX(0f)
//                                .setDuration(75)
//                                .start()
//                            child.background = null
//                        }
//
//                        // Apply bend effect ±4 items with smooth animation
//                        for (offset in -4..4) {
//                            val childIndex = index + offset
//                            if (childIndex in 0 until alphabetScroller.childCount) {
//                                val child = alphabetScroller.getChildAt(childIndex)
//                                val translation = when (offset) {
//                                    0 -> -150f
//                                    -1, 1 -> -140f
//                                    -2, 2 -> -75f
//                                    -3, 3 -> -20f
//                                    else -> 0f
//                                }
//                                child.animate()
//                                    .translationX(translation)
//                                    .setDuration(75)
//                                    .start()
//
//                                if (offset == 0) {
//                                    child.background = ContextCompat.getDrawable(this, R.drawable.bubble_background)
//                                }
//                            }
//                        }
//
//                        lastIndex = index
//                    }
//                }
//
//                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
//                    // Reset all on touch release with animation
//                    for (i in 0 until alphabetScroller.childCount) {
//                        val child = alphabetScroller.getChildAt(i)
//                        child.animate()
//                            .translationX(0f)
//                            .setDuration(75)
//                            .start()
//                        child.background = null
//                    }
//                    lastIndex = -1
//                }
//            }
//            true
//        }
//
/////////////////////////////////////////////////////////////////////////////////////////////////////

/////////////////////// DYNAMIC ANIMATED BENDING SCROLL BAR WITH AN INDICATOR ///////////////////////
////////////////////////////////// (DIFFERENT METHOD FOR BENDING) ///////////////////////////////////
//
//        alphabetScroller.setOnTouchListener { _, event ->
//            when (event.action) {
//                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
//                    val itemHeight = alphabetScroller.height / usedAlphabets.size
//                    val index = (event.y / itemHeight).toInt().coerceIn(0, usedAlphabets.size - 1)
//
//                    if (index != lastIndex) {
//                        val selectedChar = usedAlphabets.elementAt(index)
//                        indexMap[selectedChar]?.let {
//                            layoutManager.scrollToPositionWithOffset(it, 0)
//                        }
//
//                        // Reset all views with animation
//                        for (i in 0 until alphabetScroller.childCount) {
//                            val child = alphabetScroller.getChildAt(i)
//                            child.animate()
//                                .translationX(0f)
//                                .setDuration(75)
//                                .start()
//                            child.background = null
//                        }
//
//                        val amplitude = 150f
//                        val sigma = 1.5f
//
//                        for (i in 0 until alphabetScroller.childCount) {
//                            val offset = i - index
//                            if (abs(offset) <= 4) {
//                                val child = alphabetScroller.getChildAt(i)
//
//                                // Bell curve translation based on distance from touch
//                                val distance = offset.toFloat()
//                                val translation = -amplitude * exp(-((distance * distance) / (2 * sigma * sigma)))
//
//                                child.animate()
//                                    .translationX(translation)
//                                    .setDuration(75)
//                                    .start()
//
//                                if (offset == 0) {
//                                    child.background = ContextCompat.getDrawable(this, R.drawable.bubble_background)
//                                }
//                            }
//                        }
//
//                        lastIndex = index
//                    }
//                }
//
//                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
//                    // Reset all on touch release with animation
//                    for (i in 0 until alphabetScroller.childCount) {
//                        val child = alphabetScroller.getChildAt(i)
//                        child.animate()
//                            .translationX(0f)
//                            .setDuration(75)
//                            .start()
//                        child.background = null
//                    }
//                    lastIndex = -1
//                }
//            }
//            true
//        }
//
/////////////////////////////////////////////////////////////////////////////////////////////////////

/////////////////////// DYNAMIC ANIMATED BENDING SCROLL BAR WITH AN INDICATOR ///////////////////////
/////////////////////// (DIFFERENT METHOD FOR BENDING AND BETTER PERFORMANCE) ///////////////////////
//
//        alphabetScroller.setOnTouchListener { _, event ->
//            when (event.action) {
//                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
//                    val now = System.currentTimeMillis()
//                    if (now - lastAnimationUpdateTime < animationThrottleMs) return@setOnTouchListener true
//                    lastAnimationUpdateTime = now
//
//                    val itemHeight = alphabetScroller.height / usedAlphabets.size
//                    val index = (event.y / itemHeight).toInt().coerceIn(0, usedAlphabets.size - 1)
//
//                    if (index != lastIndex) {
//                        val selectedChar = usedAlphabets.elementAt(index)
//                        indexMap[selectedChar]?.let {
//                            layoutManager.scrollToPositionWithOffset(it, 0)
//                        }
//
//                        val amplitude = 150f
//                        val sigma = 1.5f
//
//                        for (i in 0 until alphabetScroller.childCount) {
//                            val child = alphabetScroller.getChildAt(i)
//                            val offset = i - index
//
//                            val distance = offset.toFloat()
//                            val curveFactor = exp(-(distance * distance) / (2 * sigma * sigma))
//
//                            val translationX = -amplitude * curveFactor
//                            val scale = 0.85f + (0.15f * curveFactor)
//                            val alpha = 0.4f + (0.6f * curveFactor)
//
//                            ViewCompat.animate(child).cancel()
//                            ViewCompat.animate(child)
//                                .translationX(translationX)
//                                .scaleX(scale)
//                                .scaleY(scale)
//                                .alpha(alpha)
//                                .setDuration(75)
//                                .setInterpolator(LinearInterpolator())
//                                .withLayer()
//                                .start()
//
//                            child.background = if (offset == 0) {
//                                bubbleBackground
//                            } else {
//                                null
//                            }
//                        }
//
//                        lastIndex = index
//                    }
//                }
//
//                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
//                    for (i in 0 until alphabetScroller.childCount) {
//                        val child = alphabetScroller.getChildAt(i)
//                        ViewCompat.animate(child).cancel()
//                        ViewCompat.animate(child)
//                            .translationX(0f)
//                            .scaleX(1f)
//                            .scaleY(1f)
//                            .alpha(1f)
//                            .setDuration(75)
//                            .setInterpolator(LinearInterpolator())
//                            .withLayer()
//                            .start()
//                        child.background = null
//                    }
//
//                    lastIndex = -1
//                }
//            }
//            true
//        }
//
/////////////////////////////////////////////////////////////////////////////////////////////////////

        drawerRecyclerView = findViewById(R.id.appDrawerRecyclerView)
        drawerAdapter = AppDrawerAdapter(
            this,
            packageManager,
            loadDrawerApps().toMutableList(),
            ::saveDrawerApps,
            refreshList = {
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
            drawerRecyclerView.layoutManager =
                LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            drawerRecyclerView.addItemDecoration(CenterSpacingDecoration())
            adapter = drawerAdapter
        }

        drawerRecyclerView.itemAnimator = null

        drawerRecyclerView.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
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
                    if (!drawerAdapter.getApps()
                            .any { it.packageName == AppDrawerAdapter.DROP_INDICATOR_PACKAGE }
                    ) {
                        drawerAdapter.insertDropIndicator(0)
                    }
                    true
                }

                DragEvent.ACTION_DRAG_LOCATION -> {
                    val x = event.x.toInt()
                    val recyclerView = view as RecyclerView
                    val draggedApp = event.localState as ApplicationInfo
                    val isAppFromDrawer = !listAdapter.getApps().contains(draggedApp)
                    if (!isAppFromDrawer && (drawerAdapter.getApps().size >= drawerSize + 1)) {
                        if (!toastShownThisDrag) {
                            drawerAdapter.removeDropIndicator()
                            Toast.makeText(
                                this,
                                "Cannot add more than $drawerSize apps",
                                Toast.LENGTH_SHORT
                            ).show()
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
//            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
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
            needRefresh = false
        }
        if (!isDefaultLauncher(this)) {
            finish()
            startActivity(Intent(this, DefaultLauncherActivity::class.java))
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

    private fun getAlphabetIndexMap(apps: List<ApplicationInfo>): Map<Char, Int> {
        val map = mutableMapOf<Char, Int>()
        for ((index, app) in apps.withIndex()) {
            val label = app.loadLabel(packageManager).toString()
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
            it.loadLabel(packageManager).toString().lowercase()
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
        val appName = appInfo.loadLabel(applicationContext.packageManager).toString()

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
//        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

}