package com.example.voidui

import android.content.Context
import android.content.SharedPreferences

object SharedPreferencesManager {
    private const val PREFS_NAME = "MinimaPrefs"
    private const val KEY_APP_TOGGLE_PREFIX = "app_toggle_"
    private const val KEY_GLOBAL_TIMER_ENABLED = "global_timer_enabled"
    private const val KEY_SWITCH_TRACK_ENABLED = "switch_track_enabled"
    private const val KEY_SWIPE_TO_LOCK_ENABLED = "swipe_to_lock_enabled"
    private const val KEY_SWIPE_TO_SETTINGS_ENABLED = "swipe_to_settings_enabled"
    private const val KEY_DOUBLE_TAP_TO_LOCK_ENABLED = "double_tap_to_lock_enabled"
    private const val KEY_IS_ONE_MIN_TOAST_SHOWN = "one_min_toast_shown"
    private const val KEY_APP_TIMER_PREFIX = "app_timer_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isAppToggleEnabled(context: Context, appName: String): Boolean {
        return getPrefs(context).getBoolean(KEY_APP_TOGGLE_PREFIX + appName, true)
    }

    // Global timer enabled getter
    fun isGlobalTimerEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_GLOBAL_TIMER_ENABLED, false)
    }

    // Global timer enabled setter
    fun setGlobalTimerEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_GLOBAL_TIMER_ENABLED, enabled).apply()
    }

    fun isSwitchTrackEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SWITCH_TRACK_ENABLED, false)
    }

    fun setSwitchTrackEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SWITCH_TRACK_ENABLED, enabled).apply()
    }

    // Per app timer enabled getter
    fun isAppTimerEnabled(context: Context, appName: String): Boolean {
        return getPrefs(context).getBoolean(KEY_APP_TIMER_PREFIX + appName, false)
    }

    // Per app timer enabled setter
    fun setAppTimerEnabled(context: Context, appName: String, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_APP_TIMER_PREFIX + appName, enabled).apply()
    }

    fun isSwipeToLockEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SWIPE_TO_LOCK_ENABLED, false)
    }

    fun isSwipeToSettingsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SWIPE_TO_SETTINGS_ENABLED, false)
    }

    fun isDoubleTapToLockEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DOUBLE_TAP_TO_LOCK_ENABLED, false)
    }

    fun setSwipeToLockEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SWIPE_TO_LOCK_ENABLED, enabled).apply()
    }

    fun setSwipeToSettingsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SWIPE_TO_SETTINGS_ENABLED, enabled).apply()
    }

    fun setDoubleTapToLockEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DOUBLE_TAP_TO_LOCK_ENABLED, enabled).apply()
    }

    fun isOneMinToastShown(context: Context, appName: String): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_ONE_MIN_TOAST_SHOWN + appName, false)
    }

    fun setOneMinToastShown(context: Context, appName: String, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IS_ONE_MIN_TOAST_SHOWN + appName, enabled).apply()
    }
}