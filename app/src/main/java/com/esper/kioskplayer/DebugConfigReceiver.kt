package com.esper.kioskplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DebugConfigReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != ACTION_APPLY && action != ACTION_CLEAR) return

        if (action == ACTION_CLEAR) {
            KioskConfig.updateDebugOverrides(context, emptyMap(), clearAll = true)
            context.sendBroadcast(Intent(ManagedConfigReceiver.ACTION_CONFIG_CHANGED).setPackage(context.packageName))
            return
        }

        val values = mutableMapOf<String, Any?>()
        values["video_dir"] = intent.getStringExtra("video_dir")
        values["play_mode"] = intent.getStringExtra("play_mode")
        values["single_file"] = intent.getStringExtra("single_file")
        values["playlist_files"] = intent.getStringExtra("playlist_files")
        values["loop_mode"] = intent.getStringExtra("loop_mode")
        values["orientation"] = intent.getStringExtra("orientation")

        if (intent.hasExtra("fullscreen")) values["fullscreen"] = intent.getBooleanExtra("fullscreen", true)
        if (intent.hasExtra("hide_controls")) values["hide_controls"] = intent.getBooleanExtra("hide_controls", true)
        if (intent.hasExtra("mute")) values["mute"] = intent.getBooleanExtra("mute", false)
        if (intent.hasExtra("autostart_on_boot")) values["autostart_on_boot"] = intent.getBooleanExtra("autostart_on_boot", true)
        if (intent.hasExtra("skip_missing_files")) values["skip_missing_files"] = intent.getBooleanExtra("skip_missing_files", true)
        if (intent.hasExtra("show_debug_overlay")) values["show_debug_overlay"] = intent.getBooleanExtra("show_debug_overlay", false)
        if (intent.hasExtra("volume_percent")) values["volume_percent"] = intent.getIntExtra("volume_percent", 100)

        KioskConfig.updateDebugOverrides(context, values)
        context.sendBroadcast(Intent(ManagedConfigReceiver.ACTION_CONFIG_CHANGED).setPackage(context.packageName))
    }

    companion object {
        const val ACTION_APPLY = "com.esper.kioskplayer.DEBUG_APPLY_CONFIG"
        const val ACTION_CLEAR = "com.esper.kioskplayer.DEBUG_CLEAR_CONFIG"
    }
}
