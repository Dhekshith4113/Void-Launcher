package com.example.voidui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID_NET_STAT = "speed_monitor_channel"
        const val CHANNEL_NAME_NET_STAT = "Internet Speed Monitor"
        const val NOTIFICATION_ID_NET_SAT = 101
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID_NET_STAT, CHANNEL_NAME_NET_STAT,
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun buildNetStatNotification(downloadSpeed: String, uploadSpeed: String, mobileDataUsage: String, wifiDataUsage: String, wifiStrength: String): Notification {
        val contentTitle = "↓ $downloadSpeed    ↑ $uploadSpeed  $wifiStrength"
        val contentText = "Mobile: $mobileDataUsage      Wi-Fi: $wifiDataUsage"

        val download = parseSpeed(downloadSpeed) // in KB/s
        val upload = parseSpeed(uploadSpeed)     // in KB/s
        val totalSpeed = download + upload
        val speedText = totalSpeed.toFloat() // convert to KB/s

        println(speedText)

        val icon = createSpeedIcon(context, speedText)

        return NotificationCompat.Builder(context, CHANNEL_ID_NET_STAT)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSmallIcon(icon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    fun updateNetStatNotification(notification: Notification) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_NET_SAT, notification)
    }

//    fun createSpeedIcon(context: Context, speedText: Float): IconCompat {
//
//        // Convert speed to display string
//        val (value, unit) = if (speedText >= 1024) {
//            String.format("%.1f", speedText / 1024f) to "MB/s"
//        } else {
//            speedText.toInt().toString() to "KB/s"
//        }
//
////        val size = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height)
////        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
////        val canvas = Canvas(bitmap)
//
//        val scale = context.resources.displayMetrics.density
//        val sizePx = (24 * scale).toInt() // system icon size
//        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
//        val canvas = Canvas(bitmap)
//
////        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
////            color = Color.WHITE
////            textSize = size / 2.5f
////            textAlign = Paint.Align.CENTER
////            typeface = Typeface.DEFAULT_BOLD
////        }
//
//        val paintValue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
//            color = Color.WHITE
//            textSize = 10f * scale
//            textAlign = Paint.Align.CENTER
//            typeface = Typeface.DEFAULT_BOLD
//        }
//
//        val paintUnit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
//            color = Color.LTGRAY
//            textSize = 7f * scale
//            textAlign = Paint.Align.CENTER
//        }
//
//        // Draw the speed number (top)
//        canvas.drawText(value, sizePx / 2f, sizePx / 2.5f, paintValue)
//        // Draw the unit (bottom)
//        canvas.drawText(unit, sizePx / 2f, sizePx.toFloat() - 4f, paintUnit)
//
//        // Draw the speed text (e.g., "2")
////        canvas.drawText(speedText, size / 2f, size / 2f + paint.textSize / 3, paint)
//
//        return IconCompat.createWithBitmap(bitmap)
//    }

    fun createSpeedIcon(context: Context, speedInKbps: Float): IconCompat {
        // Convert speed to display string
        val (value, unit) = if (speedInKbps >= 1024) {
            String.format("%.1f", speedInKbps / 1024f) to "MB/s"
        } else {
            speedInKbps.toInt().toString() to "KB/s"
        }

        // Create a bitmap for the icon (must be 24x24dp for status bar)
        val scale = context.resources.displayMetrics.density
        val sizePx = (24 * scale).toInt() // system icon size
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paintValue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f * scale
            textScaleX = 0.3f *scale
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        val paintUnit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10f * scale
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        // Draw the speed number (top)
        canvas.drawText(value, sizePx / 2f, sizePx / 1.8f, paintValue)
        // Draw the unit (bottom)
        canvas.drawText(unit, sizePx / 2f, sizePx.toFloat() - 0.6f, paintUnit)

        return IconCompat.createWithBitmap(bitmap)
    }

    fun parseSpeed(speed: String): Float {
        return when {
            speed.contains("MB/s") -> speed.replace("MB/s", "").trim().toFloatOrNull()?.times(1024) ?: 0f
            speed.contains("KB/s") -> speed.replace("KB/s", "").trim().toFloatOrNull() ?: 0f
            else -> 0f
        }
    }

}