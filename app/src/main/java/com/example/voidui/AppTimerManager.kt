package com.example.voidui

object AppTimerManager {
    private val appTimers = mutableMapOf<String, Long>()
    private val expiredFlags = mutableSetOf<String>()

    fun setTimer(packageName: String, endTimeMillisFromNow: Long) {
        appTimers[packageName] = System.currentTimeMillis() + endTimeMillisFromNow
        expiredFlags.remove(packageName)
    }

    fun isExpired(packageName: String): Boolean {
        val endTime = appTimers[packageName] ?: return false
        return System.currentTimeMillis() > endTime
    }

    fun isOneMin(packageName: String): Boolean {
        val endTime = appTimers[packageName] ?: return false
        return 60_000 > endTime - System.currentTimeMillis()
    }

    fun markExpired(packageName: String) {
        expiredFlags.add(packageName)
    }

    fun wasMarkedExpired(packageName: String): Boolean {
        return expiredFlags.contains(packageName)
    }

    fun clearExpiredMark(packageName: String) {
        expiredFlags.remove(packageName)
    }

    fun clearAllTimers() {
        appTimers.clear()
        expiredFlags.clear()
    }

//    fun isTracking(packageName: String): Boolean {
//        val endTime = appTimers[packageName] ?: return false
//        return endTime > System.currentTimeMillis()
//    }
//
//    fun getRemainingTime(packageName: String): Long {
//        val endTime = appTimers[packageName] ?: return 0
//        val now = System.currentTimeMillis()
//        return (endTime - now).coerceAtLeast(0)
//    }

}