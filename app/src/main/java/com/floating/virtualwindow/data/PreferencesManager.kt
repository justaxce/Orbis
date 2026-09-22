package com.floating.virtualwindow.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var engineMode: Int
        get() = prefs.getInt(KEY_ENGINE_MODE, MODE_ZERO_SETUP)
        set(value) = prefs.edit().putInt(KEY_ENGINE_MODE, value).apply()

    var dockSide: String
        get() = prefs.getString(KEY_DOCK_SIDE, "RIGHT") ?: "RIGHT"
        set(value) = prefs.edit().putString(KEY_DOCK_SIDE, value).apply()

    var handleYPosition: Int
        get() = prefs.getInt(KEY_HANDLE_Y, 300)
        set(value) = prefs.edit().putInt(KEY_HANDLE_Y, value).apply()

    var windowWidth: Int
        get() = prefs.getInt(KEY_WINDOW_WIDTH, 750)
        set(value) = prefs.edit().putInt(KEY_WINDOW_WIDTH, value).apply()

    var windowHeight: Int
        get() = prefs.getInt(KEY_WINDOW_HEIGHT, 1100)
        set(value) = prefs.edit().putInt(KEY_WINDOW_HEIGHT, value).apply()

    fun getSelectedPackages(): Set<String> {
        return prefs.getStringSet(KEY_SELECTED_PACKAGES, DEFAULT_PACKAGES) ?: DEFAULT_PACKAGES
    }

    fun setSelectedPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_SELECTED_PACKAGES, packages).apply()
    }

    fun toggleAppSelection(packageName: String, selected: Boolean) {
        val current = getSelectedPackages().toMutableSet()
        if (selected) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        setSelectedPackages(current)
    }

    var autoMinimizeOnOutsideTap: Boolean
        get() = prefs.getBoolean(KEY_AUTO_MINIMIZE_OUTSIDE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_MINIMIZE_OUTSIDE, value).apply()

    companion object {
        private const val PREFS_NAME = "floating_virtual_window_prefs"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_ENGINE_MODE = "engine_mode"
        private const val KEY_DOCK_SIDE = "dock_side"
        private const val KEY_HANDLE_Y = "handle_y"
        private const val KEY_WINDOW_WIDTH = "window_width"
        private const val KEY_WINDOW_HEIGHT = "window_height"
        private const val KEY_SELECTED_PACKAGES = "selected_packages"
        private const val KEY_AUTO_MINIMIZE_OUTSIDE = "auto_minimize_outside_tap"

        const val MODE_ZERO_SETUP = 0
        const val MODE_ADVANCED = 1

        val DEFAULT_PACKAGES = setOf(
            "com.google.android.youtube",
            "com.whatsapp",
            "com.instagram.android",
            "com.openai.chatgpt",
            "com.spotify.music",
            "com.google.android.apps.maps",
            "com.twitter.android",
            "com.google.android.googlequicksearchbox"
        )
    }
}
