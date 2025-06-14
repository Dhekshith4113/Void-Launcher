package com.example.voidui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.os.Bundle
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class SettingsActivity : AppCompatActivity() {

    private lateinit var gestureDetector: GestureDetector
    private lateinit var appUsageView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val backButton = findViewById<ImageButton>(R.id.backButtonSettings)

        backButton.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }

        gestureDetector = GestureDetector(this, SwipeBackGestureListener())

        val listView: ListView = findViewById(R.id.settingsListView)
        val options = listOf(
            "In-App timer reminder",
            "Change color theme",
            "Internet Speed Meter",
            "Gestures",
            "Change launcher",
            "Device settings",
            "Digital Wellbeing",
            "Version"
        )
        val adapter = object : ArrayAdapter<String>(
            this,
            R.layout.item_settings_option,
            R.id.settingOptionText,
            options
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                if (position == options.size - 2) {
                    // Inflate your custom layout for the last item
                    appUsageView = LayoutInflater.from(context)
                        .inflate(R.layout.app_usage_layout, parent, false)

                    appUsageView.visibility = View.GONE
                    populateAppUsageOption(appUsageView)
                    appUsageView.visibility = View.VISIBLE

                    return appUsageView
                } else {
                    val view = LayoutInflater.from(context)
                        .inflate(R.layout.item_settings_option, parent, false)
                    view.findViewById<TextView>(R.id.settingOptionText)?.text = options[position]
                    return view
                }
            }
        }
        listView.adapter = adapter

        val snackBar = Snackbar.make(listView, "Version 1.1", Snackbar.LENGTH_SHORT)
        val textView =
            snackBar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView.setTextColor(ContextCompat.getColor(this, R.color.backgroundColor))
        textView.textSize = 18f
        textView.typeface = ResourcesCompat.getFont(this, R.font.minima_font_family)
        textView.gravity = Gravity.CENTER_HORIZONTAL
        snackBar.view.setBackgroundColor(ContextCompat.getColor(this, R.color.textColorPrimary))

        listView.setOnItemClickListener { _, _, position, _ ->
            when (position) {
                0 -> startActivity(Intent(this, InAppTimerReminderActivity::class.java))
                1 -> showThemeDialog()
                2 -> showInternetStatsDialog()
                3 -> showGesturesDialog()
                4 -> startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                5 -> startActivity(Intent(Settings.ACTION_SETTINGS))
                6 -> try {
                    val intent = Intent()
                    intent.setClassName(
                        "com.samsung.android.forest",
                        "com.samsung.android.forest.home.ui.DefaultActivity" // Common on some devices
                    )
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Digital Wellbeing not available", Toast.LENGTH_SHORT)
                        .show()
                }

                7 -> snackBar.show()

                else -> {
                    Toast.makeText(this, "Something's wrong!", Toast.LENGTH_SHORT).show()
                }
            }.also {
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }
    }

    private fun populateAppUsageOption(optionView: View): View {
        val totalTimeTextView = optionView.findViewById<TextView>(R.id.textViewTotalTime)

        val appTextViews = listOf(
            optionView.findViewById<TextView>(R.id.appOne),
            optionView.findViewById(R.id.appTwo),
            optionView.findViewById(R.id.appThree),
            optionView.findViewById(R.id.appFour)
        )

        val appTimeViews = listOf(
            optionView.findViewById<TextView>(R.id.appOneTime),
            optionView.findViewById(R.id.appTwoTime),
            optionView.findViewById(R.id.appThreeTime),
            optionView.findViewById(R.id.appFourTime)
        )

        val appIndicatorView = listOf(
            optionView.findViewById<View>(R.id.appOneIndicator),
            optionView.findViewById(R.id.appTwoIndicator),
            optionView.findViewById(R.id.appThreeIndicator),
            optionView.findViewById(R.id.appFourIndicator)
        )

        val appViews = listOf(
            optionView.findViewById<View>(R.id.appOneView),
            optionView.findViewById(R.id.appTwoView),
            optionView.findViewById(R.id.appThreeView),
            optionView.findViewById(R.id.appFourView)
        )

        val usageBar = optionView.findViewById<LinearLayout>(R.id.usageBar)

        val colors = listOf(
            Color.parseColor("#2ED3B7"),
            Color.parseColor("#3A6FF8"),
            Color.parseColor("#7A5DFE"),
            Color.parseColor("#B1C0D7")
        )

        if (UsageStatsManagerUtils.hasUsageStatsPermission(this)) {
            lifecycleScope.launch {
                val (totalTime, topApps) = withContext(Dispatchers.IO) {
                    UsageStatsManagerUtils.getTodayTopUsedApps(this@SettingsActivity)
                }

                totalTimeTextView.text = formatTime(totalTime)

                for (i in appTextViews.indices) {
                    if (i < topApps.size) {
                        appTextViews[i].visibility = View.VISIBLE
                        appTimeViews[i].visibility = View.VISIBLE
                        appIndicatorView[i].visibility = View.VISIBLE
                        appViews[i].visibility = View.VISIBLE

                        val (packageName, usageTime) = topApps[i]
                        appTextViews[i].text = packageName
                        appTimeViews[i].text = formatTime(usageTime)
                    } else {
                        appTextViews[i].visibility = View.GONE
                        appTimeViews[i].visibility = View.GONE
                        appIndicatorView[i].visibility = View.GONE
                        appViews[i].visibility = View.GONE
                    }
                }

                for (i in appIndicatorView.indices) {
                    val drawable = ContextCompat.getDrawable(
                        this@SettingsActivity,
                        R.drawable.app_indicator_background
                    )?.mutate() as? GradientDrawable
                    drawable?.setColor(colors[i])
                    appIndicatorView[i].background = drawable
                }

                populateUsageBar(usageBar, topApps, totalTime)
            }
        } else {
            totalTimeTextView.text = "  --h --m"
        }

        return optionView
    }


    private fun populateUsageBar(
        container: LinearLayout,
        appUsageList: List<Pair<String, Long>>,
        totalTime: Long
    ) {
        container.removeAllViews()
        val colors = listOf(
            Color.parseColor("#2ED3B7"),
            Color.parseColor("#3A6FF8"),
            Color.parseColor("#7A5DFE"),
            Color.parseColor("#B1C0D7")
        )

        appUsageList.forEachIndexed { index, (_, usageTime) ->
            val weight = usageTime.toFloat() / totalTime
            val view = createRoundedSegment(
                container.context,
                colors.getOrElse(index) { Color.GRAY },
                isFirst = index == 0,
                isLast = index == appUsageList.lastIndex
            )

            view.layoutParams =
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply {
                    marginEnd = if (index != appUsageList.lastIndex) 2 else 0
                }
            container.addView(view)
        }
    }

    private fun createRoundedSegment(
        context: Context,
        color: Int,
        isFirst: Boolean,
        isLast: Boolean
    ): View {
        val radius = 50f
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadii = when {
                isFirst && isLast -> FloatArray(8) { radius }
                isFirst -> floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius)
                isLast -> floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f)
                else -> FloatArray(8)
            }
        }
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT, // ensures it fills height properly
                1f
            )
            background = drawable
        }
    }

    private fun formatTime(millis: Long): String {
        val seconds = millis / 1000
        val secs = seconds % 60
        val minutes = seconds / 60
        val mins = minutes % 60
        val hours = minutes / 60
        return when {
            hours >= 10 -> "${hours}h ${mins}m"
            hours >= 1 -> " ${hours}h ${mins}m"
            mins >= 10 -> "${mins}m"
            mins >= 1 -> " ${mins}m"
            secs >= 10 -> "${secs}s"
            else -> " ${secs}s"
        }
    }

    private fun showThemeDialog() {
        ThemeManager.applySavedTheme(this)
        val dialogView = layoutInflater.inflate(R.layout.activity_sub_options, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogView.findViewById<RadioButton>(R.id.radioDark).setOnClickListener {
            val mode = AppCompatDelegate.MODE_NIGHT_YES
            ThemeManager.saveThemeMode(this, mode)
            recreate()
            dialog.dismiss()
        }
        dialogView.findViewById<RadioButton>(R.id.radioLight).setOnClickListener {
            val mode = AppCompatDelegate.MODE_NIGHT_NO
            ThemeManager.saveThemeMode(this, mode)
            recreate()
            dialog.dismiss()
        }
        dialogView.findViewById<RadioButton>(R.id.radioSystem).setOnClickListener {
            val mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeManager.saveThemeMode(this, mode)
            recreate()
            dialog.dismiss()
        }

        when (ThemeManager.getSavedThemeMode(this)) {
            AppCompatDelegate.MODE_NIGHT_YES -> dialogView.findViewById<RadioButton>(R.id.radioDark)?.isChecked =
                true

            AppCompatDelegate.MODE_NIGHT_NO -> dialogView.findViewById<RadioButton>(R.id.radioLight)?.isChecked =
                true

            else -> dialogView.findViewById<RadioButton>(R.id.radioSystem)?.isChecked = true
        }

        dialog.show()
    }

    private fun showInternetStatsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.internet_stats_activity, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val switchTrack = dialogView.findViewById<SwitchCompat>(R.id.switchTrack)
        switchTrack.isChecked = SharedPreferencesManager.isSwitchTrackEnabled(this)

        populateUsageTable(dialogView)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        switchTrack.setOnCheckedChangeListener { _, isChecked ->
            SharedPreferencesManager.setSwitchTrackEnabled(this, isChecked)
            if (isChecked) {
                val intent = Intent(this, SpeedMonitorService::class.java)
                startForegroundService(intent)
            } else {
                val intent = Intent(this, SpeedMonitorService::class.java)
                stopService(intent)
            }
        }

        dialog.show()
    }

    private fun populateUsageTable(dialogView: View) {
        CoroutineScope(Dispatchers.IO).launch {
            val cal = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
            val rows = mutableListOf<TableRow>()

            for (i in 0..6) {
                cal.time = Date()
                cal.add(Calendar.DATE, -i)
                val date = cal.time

                val mobileData = NetworkUsageHelper(this@SettingsActivity).getDailyDataUsage(
                    date,
                    NetworkUsageHelper.NetworkType.MOBILE
                )
                val wifiData = NetworkUsageHelper(this@SettingsActivity).getDailyDataUsage(
                    date,
                    NetworkUsageHelper.NetworkType.WIFI
                )
                val totalData = mobileData + wifiData

                val row = TableRow(this@SettingsActivity).apply {
                    addCell(dateFormat.format(date))
                    addCell(formatBytes(mobileData))
                    addCell(formatBytes(wifiData))
                    addCell(formatBytes(totalData))
                }
                rows.add(row)
            }

            val headerRow = TableRow(this@SettingsActivity).apply {
                addCell("Date", isHeader = true)
                addCell("Mobile", isHeader = true)
                addCell("Wi-Fi", isHeader = true)
                addCell("Total", isHeader = true)
            }

            withContext(Dispatchers.Main) {
                dialogView.findViewById<TableLayout>(R.id.tableUsage).apply {
                    removeAllViews()
                    addView(headerRow)
                    rows.forEach { addView(it) }
                }
            }
        }
    }

    private fun TableRow.addCell(text: String, isHeader: Boolean = false) {
        val textView = TextView(this@SettingsActivity).apply {
            this.text = text
            textSize = 14f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(if (isHeader) getColor(R.color.textColorPrimary) else getColor(R.color.backgroundColor))
            setTextColor(if (isHeader) getColor(R.color.backgroundColor) else getColor(R.color.textColorPrimary))
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.cell_border)
            if (isHeader) {
                setBackgroundColor(getColor(R.color.textColorPrimary))
            }

            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
        }
        addView(textView)
    }

    private fun formatBytes(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> String.format("%.2f GB", bytes / gb)
            bytes >= mb -> String.format("%.2f MB", bytes / mb)
            bytes >= kb -> String.format("%.2f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    private fun showGesturesDialog() {
        val dialogView = layoutInflater.inflate(R.layout.gesture_dialog, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val lockSwitch = dialogView.findViewById<SwitchCompat>(R.id.lockSwitch)
        val settingsSwitch = dialogView.findViewById<SwitchCompat>(R.id.settingsSwitch)
        val doubleTapSwitch = dialogView.findViewById<SwitchCompat>(R.id.doubleTapSwitch)

        if (!AppAccessibilityService.isAccessibilityServiceEnabled()) {
            SharedPreferencesManager.setSwipeToLockEnabled(this, false)
            SharedPreferencesManager.setDoubleTapToLockEnabled(this, false)
        }

        lockSwitch.isChecked = SharedPreferencesManager.isSwipeToLockEnabled(this)
        settingsSwitch.isChecked = SharedPreferencesManager.isSwipeToSettingsEnabled(this)
        doubleTapSwitch.isChecked = SharedPreferencesManager.isDoubleTapToLockEnabled(this)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        lockSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!AppAccessibilityService.isAccessibilityServiceEnabled()) {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } else {
                    SharedPreferencesManager.setSwipeToLockEnabled(this, true)
                    Toast.makeText(
                        this,
                        "'Swipe right to lock phone' is enabled",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                SharedPreferencesManager.setSwipeToLockEnabled(this, false)
                Toast.makeText(this, "'Swipe right to lock phone' is disabled", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        settingsSwitch.setOnCheckedChangeListener { _, isChecked ->
            SharedPreferencesManager.setSwipeToSettingsEnabled(this, isChecked)
            if (isChecked) {
                Toast.makeText(this, "'Swipe left to open settings' is enabled", Toast.LENGTH_SHORT)
                    .show()
            } else {
                Toast.makeText(
                    this,
                    "'Swipe left to open settings' is disabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        doubleTapSwitch.setOnCheckedChangeListener { _, isChecked ->
            SharedPreferencesManager.setDoubleTapToLockEnabled(this, isChecked)
            if (isChecked) {
                if (!AppAccessibilityService.isAccessibilityServiceEnabled()) {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } else {
                    Toast.makeText(
                        this,
                        "'Double tap to lock phone' is enabled",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(this, "'Double tap to lock phone' is disabled", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        dialog.show()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    inner class SwipeBackGestureListener : GestureDetector.SimpleOnGestureListener() {
        private val swipeThreshold = 100
        private val swipeVelocityThreshold = 100
        private val edgeSwipeThreshold = 50

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false
            val startX = e1.x
            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y

            if (abs(diffX) > abs(diffY) &&
                abs(diffX) > swipeThreshold &&
                abs(velocityX) > swipeVelocityThreshold
            ) {
                if (diffX > 0 && startX < edgeSwipeThreshold) {
                    finish()
                    overridePendingTransition(
                        android.R.anim.slide_in_left,
                        android.R.anim.slide_out_right
                    )
                    return true
                }
            }
            return false
        }
    }
}