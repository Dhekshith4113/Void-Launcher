package com.example.voidui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class TimerPromptActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.dialog_time_limit)

        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val packageName = intent.getStringExtra("packageName") ?: return
        val packageManager = applicationContext.packageManager
        val appName = try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            "Unknown App"
        }

        Log.d("TimerPromptActivity", "packageName is $packageName")

        val dialogTitle = findViewById<TextView>(R.id.dialogTitle)
        val customTimeInput = findViewById<EditText>(R.id.customTimeInput)
        val btn1Min = findViewById<TextView>(R.id.btn1Min)
        val btn2Min = findViewById<TextView>(R.id.btn2Min)
        val btn5Min = findViewById<TextView>(R.id.btn5Min)
        val btn10Min = findViewById<TextView>(R.id.btn10Min)
        val btnClose = findViewById<Button>(R.id.btnClose)

//        setButton.setOnClickListener {
//            val minutes = input.text.toString().toIntOrNull()
//            if (minutes != null && minutes > 0) {
//                AppTimerManager.setTimer(packageName, minutes * 60 * 1000L)
//                Toast.makeText(this, "Timer reset for $packageName", Toast.LENGTH_SHORT).show()
//                finish()
//            } else {
//                Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show()
//            }
//        }

//        closeButton.setOnClickListener {
//            AppAccessibilityService.instance?.goHomeScreen()
//        }

//        val dialogView = layoutInflater.inflate(R.layout.activity_timer_prompt, null)
//        val dialog = AlertDialog.Builder(this)
//            .setView(dialogView)
//            .setCancelable(true)
//            .create()

        fun startTimerAndLaunchApp(minutes: Int) {
            if (minutes > 0) {
                Log.d("TimerPromptActivity", "packageName is $packageName")
                AppTimerManager.setTimer(packageName, minutes * 60 * 1000L)
//                AppAccessibilityService.targetPackage = appInfo.packageName
//                AppAccessibilityService.exitTimeMillis = System.currentTimeMillis() + (minutes * 60 * 1000)
//                AppAccessibilityService.oneMinuteToastShown = false
                SharedPreferencesManager.setOneMinToastShown(this, packageName, false)
                Toast.makeText(this, "Timer reset to $minutes min", Toast.LENGTH_SHORT).show()
                finish()
//                dialog.dismiss()
            }
        }

//        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogTitle.text = "How much time would you like\nto spend on $appName again?"
        btn1Min.setOnClickListener {
            startTimerAndLaunchApp(1)
        }
        btn2Min.setOnClickListener {
            startTimerAndLaunchApp(2)
        }
        btn5Min.setOnClickListener {
            startTimerAndLaunchApp(5)
        }
        btn10Min.setOnClickListener {
            startTimerAndLaunchApp(10)
        }

//        val customInput = dialogView.findViewById<EditText>(R.id.customTimeInput)
        customTimeInput.setOnEditorActionListener { _, _, _ ->
            val minutes = customTimeInput.text.toString().toIntOrNull() ?: 20
            startTimerAndLaunchApp(minutes)
            true
        }

        btnClose.setOnClickListener {
            AppAccessibilityService.instance?.goHomeScreen()
//            dialog.dismiss()
        }

//        dialog.show()
    }
}