package com.esper.kioskplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_REFRESH_NOW -> {
                context.sendBroadcast(Intent(ManagedConfigReceiver.ACTION_CONFIG_CHANGED).setPackage(context.packageName))
            }

            ACTION_HEALTH_DUMP -> {
                val prefs = context.getSharedPreferences("kioskplayer_health", Context.MODE_PRIVATE)
                Log.i(
                    "KioskPlayer",
                    "health state=${prefs.getString("state", "unknown")} error=${prefs.getString("error", null)} media_count=${prefs.getInt("media_count", 0)} updated=${prefs.getLong("last_update_ms", 0)}"
                )
                context.sendBroadcast(Intent(ACTION_HEALTH_DUMP_INTERNAL).setPackage(context.packageName))
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_NOW = "com.esper.kioskplayer.REFRESH_NOW"
        const val ACTION_HEALTH_DUMP = "com.esper.kioskplayer.HEALTH_DUMP"
        const val ACTION_HEALTH_DUMP_INTERNAL = "com.esper.kioskplayer.HEALTH_DUMP_INTERNAL"
    }
}
