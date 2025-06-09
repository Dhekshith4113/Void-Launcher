package com.example.voidui

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.example.voidui.databinding.OverlayTimerDialogBinding

class FloatingTimerService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val packageName = intent?.getStringExtra("packageName")
        if (packageName == null || !AppTimerManager.isExpired(packageName)) {
            stopSelf()
            return START_NOT_STICKY
        }

        showFloatingDialog()
        return START_NOT_STICKY
    }

    private fun showFloatingDialog() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val binding = OverlayTimerDialogBinding.inflate(inflater)

//        binding.timerText.text = "Time’s up for $packageName"

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.CENTER

        floatingView = binding.root
        windowManager.addView(floatingView, layoutParams)

        stopSelf()

//        binding.closeButton.setOnClickListener {
//            stopSelf()
//        }
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let { windowManager.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
