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
        const val CHANNEL_ID_APP_TIMER = "timer_channel"
        const val CHANNEL_NAME_APP_TIMER = "Timer Monitor"
        const val NOTIFICATION_ID_APP_TIMER = 1
    }

    init {
        createNetSpeedNotificationChannel()
        createAppTimerNotificationChannel()
    }

    private fun createNetSpeedNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID_NET_STAT, CHANNEL_NAME_NET_STAT,
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createAppTimerNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID_APP_TIMER, CHANNEL_NAME_APP_TIMER,
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
        val speedText = totalSpeed               // convert to KB/s

        val icon = createSpeedIcon(context, speedText)

        return NotificationCompat.Builder(context, CHANNEL_ID_NET_STAT)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSmallIcon(icon)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    fun buildAppTimerNotification(appTimers: Map<String, Long>): Notification {
        val contentTitle = appTimers.toList()
            .filter { (_, endTime) -> (endTime - System.currentTimeMillis()).coerceAtLeast(0) > 0 }
            .sortedBy { (_, value) -> value }
            .joinToString("\n") { (appName, endTime) ->
            val timeText = (endTime - System.currentTimeMillis()).coerceAtLeast(0)
            "$appName ${formatMillis(timeText)}"
        }

        if (contentTitle.isBlank()) {
            return NotificationCompat.Builder(context, CHANNEL_ID_NET_STAT)
                .setContentTitle("In-app time reminder")
                .setContentText("Active")
                .setSmallIcon(R.drawable.timer_24px)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }

        val topApp = contentTitle.lines().first()

        val maxSeconds = contentTitle
            .lines()
            .mapNotNull { line ->
                val time = line.substringAfterLast(" ")
                val parts = time.split(":")
                if (parts.size == 2) {
                    val minutes = parts[0].toIntOrNull() ?: return@mapNotNull null
                    val seconds = parts[1].toIntOrNull() ?: return@mapNotNull null
                    minutes * 60 + seconds
                } else null
            }
            .minOrNull() ?: 0

        val icon = createTimerIcon(context, maxSeconds)

        return NotificationCompat.Builder(context, CHANNEL_ID_NET_STAT)
            .setContentTitle("In-app time reminder")
            .setContentText(topApp)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentTitle))
            .setSmallIcon(icon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    fun updateNetStatNotification(notification: Notification) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_NET_SAT, notification)
    }

    fun updateAppTimerNotification(notification: Notification) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_APP_TIMER, notification)
    }

    private fun createSpeedIcon(context: Context, speedInKbps: Float): IconCompat {
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

    private fun createTimerIcon(context: Context, seconds: Int): IconCompat {
        // Convert speed to display string
        val unit: String
        val timeLeft: String

        if (seconds >= 60){
            timeLeft = ((seconds / 60) + 1).toString()
            unit = "MIN"
        } else {
            timeLeft = seconds.toString()
            unit = "SEC"
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
        canvas.drawText(timeLeft, sizePx / 2f, sizePx / 1.8f, paintValue)
        // Draw the unit (bottom)
        canvas.drawText(unit, sizePx / 2f, sizePx.toFloat() - 0.6f, paintUnit)

        return IconCompat.createWithBitmap(bitmap)
    }

    private fun parseSpeed(speed: String): Float {
        return when {
            speed.contains("MB/s") -> speed.replace("MB/s", "").trim().toFloatOrNull()?.times(1024) ?: 0f
            speed.contains("KB/s") -> speed.replace("KB/s", "").trim().toFloatOrNull() ?: 0f
            else -> 0f
        }
    }

    private fun formatMillis(millis: Long): String {
        val minutes = (millis / 1000) / 60
        val seconds = (millis / 1000) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

}