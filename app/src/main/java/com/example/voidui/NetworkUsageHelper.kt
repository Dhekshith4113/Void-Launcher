package com.example.voidui

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import java.util.*

class NetworkUsageHelper(private val context: Context) {

    enum class NetworkType {
        MOBILE, WIFI
    }

    fun getDailyDataUsage(date: Date, type: NetworkType): Long {
        val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

        val calendarStart = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val calendarEnd = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        val startTime = calendarStart.timeInMillis
        val endTime = calendarEnd.timeInMillis

        val networkType = when (type) {
            NetworkType.MOBILE -> ConnectivityManager.TYPE_MOBILE
            NetworkType.WIFI -> ConnectivityManager.TYPE_WIFI
        }

        val subscriberId = if (networkType == ConnectivityManager.TYPE_MOBILE) {
            getSubscriberId(context)
        } else null

        return try {
            val bucket = networkStatsManager.querySummaryForDevice(
                networkType,
                subscriberId,
                startTime,
                endTime
            )
            bucket.rxBytes + bucket.txBytes
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        }
    }

    private fun getSubscriberId(context: Context): String? {
        return try {
            val telephonyManager =
                context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val subscriptionManager =
                    context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                val subscriptionInfo = subscriptionManager.activeSubscriptionInfoList?.firstOrNull()
                subscriptionInfo?.iccId
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.subscriberId
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            null
        }
    }

    fun getMobileDataUsageToday(context: Context): Long {
        val networkStatsManager =
            context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val startTime = getStartOfDayMillis()
        val endTime = System.currentTimeMillis()

        return try {
            val networkStats = networkStatsManager.querySummary(
                ConnectivityManager.TYPE_MOBILE,
                null,
                startTime,
                endTime
            )

            var totalBytes = 0L
            val bucket = NetworkStats.Bucket() // ✅ properly initialized

            while (networkStats.hasNextBucket()) {
                networkStats.getNextBucket(bucket)
                totalBytes += bucket.rxBytes + bucket.txBytes
            }

            totalBytes
        } catch (e: Exception) {
            e.printStackTrace()
            -1L
        }
    }

    fun getWiFiUsageToday(context: Context): Long {
        val networkStatsManager =
            context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val startTime = getStartOfDayMillis()
        val endTime = System.currentTimeMillis()

        return try {

            val wifiStats = networkStatsManager.querySummary(
                ConnectivityManager.TYPE_WIFI,
                null,
                startTime,
                endTime
            )

            var totalBytes = 0L
            val bucket = NetworkStats.Bucket() // ✅ properly initialized

            while (wifiStats.hasNextBucket()) {
                wifiStats.getNextBucket(bucket)
                totalBytes += bucket.rxBytes + bucket.txBytes
            }

            totalBytes
        } catch (e: Exception) {
            e.printStackTrace()
            -1L
        }
    }

    private fun getStartOfDayMillis(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
}