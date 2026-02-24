package com.esper.kioskplayer

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.RestrictionsManager
import android.os.Bundle

data class KioskConfig(
    val videoDir: String,
    val playMode: String,
    val singleFile: String,
    val playlistFiles: List<String>,
    val loopMode: String,
    val fullscreen: Boolean,
    val hideControls: Boolean,
    val orientation: String,
    val mute: Boolean,
    val volumePercent: Int,
    val autostartOnBoot: Boolean,
    val skipMissingFiles: Boolean,
    val showDebugOverlay: Boolean,
) {
    companion object {
        private const val PREFS_NAME = "kioskplayer_debug_config"
        private const val KEY_DEBUG_OVERRIDE_ENABLED = "debug_override_enabled"

        fun fromRestrictions(context: Context): KioskConfig {
            val rm = context.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager
            val b = rm.applicationRestrictions ?: Bundle.EMPTY
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val useDebugOverrides = isDebuggable && prefs.getBoolean(KEY_DEBUG_OVERRIDE_ENABLED, false)

            fun strAny(default: String, vararg keys: String): String {
                if (useDebugOverrides) {
                    for (key in keys) {
                        val fromPrefs = prefs.getString(key, null)
                        if (fromPrefs != null) return fromPrefs.trim()
                    }
                }
                for (key in keys) {
                    if (b.containsKey(key)) {
                        return (b.getString(key) ?: default).trim()
                    }
                }
                return default.trim()
            }

            fun boolAny(default: Boolean, vararg keys: String): Boolean {
                if (useDebugOverrides) {
                    for (key in keys) {
                        if (prefs.contains(key)) return prefs.getBoolean(key, default)
                    }
                }
                for (key in keys) {
                    if (b.containsKey(key)) return b.getBoolean(key, default)
                }
                return default
            }

            fun intAny(default: Int, vararg keys: String): Int {
                if (useDebugOverrides) {
                    for (key in keys) {
                        if (prefs.contains(key)) return prefs.getInt(key, default)
                    }
                }
                for (key in keys) {
                    if (b.containsKey(key)) return b.getInt(key, default)
                }
                return default
            }

            val modeRaw = strAny("playlist", "mode", "play_mode").lowercase()
            val loopRaw = strAny("all", "loop", "loop_mode").lowercase()
            val controlsRaw = strAny("", "controls").lowercase()
            val filesRaw = strAny("", "files", "playlist_files")
            val singleFileLegacy = strAny("", "single_file")

            val normalizedLoop = when (loopRaw) {
                "one", "loop_one" -> "loop_one"
                "all", "loop_all" -> "loop_all"
                "off", "once" -> "once"
                else -> "loop_all"
            }

            val normalizedMode = when (modeRaw) {
                "single" -> "single"
                else -> "playlist"
            }

            val playlist = filesRaw
                .split(',', '\n', ';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val singleFile = when {
                normalizedMode == "single" && playlist.isNotEmpty() -> playlist.first()
                normalizedMode == "single" && singleFileLegacy.isNotEmpty() -> singleFileLegacy
                else -> ""
            }

            val hideControls = when (controlsRaw) {
                "show" -> false
                "hide" -> true
                else -> boolAny(true, "hide_controls")
            }

            return KioskConfig(
                videoDir = strAny("Movies", "path", "video_dir"),
                playMode = normalizedMode,
                singleFile = singleFile,
                playlistFiles = playlist,
                loopMode = normalizedLoop,
                fullscreen = boolAny(true, "fullscreen"),
                hideControls = hideControls,
                orientation = strAny("landscape", "orientation").lowercase(),
                mute = boolAny(false, "mute"),
                volumePercent = intAny(100, "volume", "volume_percent").coerceIn(0, 100),
                autostartOnBoot = boolAny(true, "autostart", "autostart_on_boot"),
                skipMissingFiles = boolAny(true, "skip_missing_files"),
                showDebugOverlay = boolAny(false, "show_debug_overlay"),
            )
        }

        fun updateDebugOverrides(context: Context, values: Map<String, Any?>, clearAll: Boolean = false) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            if (clearAll) {
                editor.clear()
                editor.putBoolean(KEY_DEBUG_OVERRIDE_ENABLED, false)
            } else {
                editor.putBoolean(KEY_DEBUG_OVERRIDE_ENABLED, true)
            }
            for ((k, v) in values) {
                when (v) {
                    null -> editor.remove(k)
                    is String -> editor.putString(k, v)
                    is Boolean -> editor.putBoolean(k, v)
                    is Int -> editor.putInt(k, v)
                    is Long -> editor.putLong(k, v)
                    is Float -> editor.putFloat(k, v)
                }
            }
            editor.apply()
        }
    }
}
