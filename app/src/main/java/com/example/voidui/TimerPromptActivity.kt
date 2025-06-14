package com.example.voidui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
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

        val appName = intent.getStringExtra("appName") ?: return

        Log.d("TimerPromptActivity", "App name is $appName")

        val dialogTitle = findViewById<TextView>(R.id.dialogTitle)
        val customTimeInput = findViewById<EditText>(R.id.customTimeInput)
        val btn1Min = findViewById<TextView>(R.id.btn1Min)
        val btn2Min = findViewById<TextView>(R.id.btn2Min)
        val btn5Min = findViewById<TextView>(R.id.btn5Min)
        val btn10Min = findViewById<TextView>(R.id.btn10Min)
        val btnClose = findViewById<Button>(R.id.btnClose)

        fun startTimerAndLaunchApp(minutes: Int) {
            if (minutes > 0) {
                Log.d("TimerPromptActivity", "App name is $appName")
                AppTimerManager.setTimer(packageName, minutes * 60 * 1000L)
                SharedPreferencesManager.setOneMinToastShown(this, appName, false)
                Toast.makeText(this, "Timer reset to $minutes min", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

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

        customTimeInput.setOnEditorActionListener { _, _, _ ->
            val minutes = customTimeInput.text.toString().toIntOrNull() ?: 20
            startTimerAndLaunchApp(minutes)
            true
        }

        btnClose.setOnClickListener {
            AppAccessibilityService.instance?.goHomeScreen()
        }

    }
}