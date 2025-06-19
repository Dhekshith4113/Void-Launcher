package com.example.voidui

import android.Manifest
import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.TextView
import androidx.core.content.ContextCompat

object PermissionUtils {

    // ---- NOTIFICATION PERMISSION ----
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Automatically granted below Android 13
        }
    }

    fun promptNotificationSettings(context: Context) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_notification_prompt, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogView.findViewById<TextView>(R.id.btnOpen).setOnClickListener {
            val intent = Intent().apply {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            context.startActivity(intent)
            dialog.dismiss()
        }
        dialogView.findViewById<TextView>(R.id.btnLater).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    // ---- USAGE STATS PERMISSION ----
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun promptUsageAccessSettings(context: Context) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_usage_prompt, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogView.findViewById<TextView>(R.id.btnOpen).setOnClickListener {
            val intent = Intent().apply {
                action = Settings.ACTION_USAGE_ACCESS_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            context.startActivity(intent)
            dialog.dismiss()
        }
        dialogView.findViewById<TextView>(R.id.btnLater).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    // ---- ACCESSIBILITY PERMISSION ----

    fun promptAccessibilitySettings(context: Context) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_accessibility_prompt, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogView.findViewById<TextView>(R.id.btnOpen).setOnClickListener {
            val intent = Intent().apply {
                action = Settings.ACTION_ACCESSIBILITY_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            context.startActivity(intent)
            dialog.dismiss()
        }
        dialogView.findViewById<TextView>(R.id.btnLater).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }
}
