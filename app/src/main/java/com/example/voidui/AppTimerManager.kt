package com.example.voidui

object AppTimerManager {
    private val appTimers = mutableMapOf<String, Long>()
    private val expiredFlags = mutableSetOf<String>()

    fun setTimer(appName: String, endTimeMillisFromNow: Long) {
        appTimers[appName] = System.currentTimeMillis() + endTimeMillisFromNow
        expiredFlags.remove(appName)
    }

    fun getTimers(): Map<String, Long> {
        return appTimers
    }

    fun clearTimer(appName: String) {
        appTimers.remove(appName)
    }

    fun isExpired(appName: String): Boolean {
        val endTime = appTimers[appName] ?: return false
        return System.currentTimeMillis() > endTime
    }

    fun isOneMin(appName: String): Boolean {
        val endTime = appTimers[appName] ?: return false
        return 60_000 > endTime - System.currentTimeMillis()
    }

    fun markExpired(appName: String) {
        expiredFlags.add(appName)
    }

    fun wasMarkedExpired(appName: String): Boolean {
        return expiredFlags.contains(appName)
    }

    fun clearExpiredMark(appName: String) {
        expiredFlags.remove(appName)
    }

    fun clearAllTimers() {
        appTimers.clear()
        expiredFlags.clear()
    }

}