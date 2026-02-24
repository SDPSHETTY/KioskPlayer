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

            fun str(key: String, default: String): String {
                if (useDebugOverrides) {
                    val fromPrefs = prefs.getString(key, null)
                    if (fromPrefs != null) return fromPrefs.trim()
                }
                return (b.getString(key) ?: default).trim()
            }

            fun bool(key: String, default: Boolean): Boolean {
                if (useDebugOverrides && prefs.contains(key)) return prefs.getBoolean(key, default)
                return if (b.containsKey(key)) b.getBoolean(key, default) else default
            }

            fun int(key: String, default: Int): Int {
                if (useDebugOverrides && prefs.contains(key)) return prefs.getInt(key, default)
                return if (b.containsKey(key)) b.getInt(key, default) else default
            }

            val playlistRaw = str("playlist_files", "")
            val playlist = playlistRaw
                .split(',', '\n', ';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            return KioskConfig(
                videoDir = str("video_dir", "Movies"),
                playMode = str("play_mode", "playlist").lowercase(),
                singleFile = str("single_file", ""),
                playlistFiles = playlist,
                loopMode = str("loop_mode", "loop_all").lowercase(),
                fullscreen = bool("fullscreen", true),
                hideControls = bool("hide_controls", true),
                orientation = str("orientation", "landscape").lowercase(),
                mute = bool("mute", false),
                volumePercent = int("volume_percent", 100).coerceIn(0, 100),
                autostartOnBoot = bool("autostart_on_boot", true),
                skipMissingFiles = bool("skip_missing_files", true),
                showDebugOverlay = bool("show_debug_overlay", false),
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
