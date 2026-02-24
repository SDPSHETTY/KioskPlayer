package com.esper.kioskplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ManagedConfigReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val config = KioskConfig.fromRestrictions(context)
            if (config.autostartOnBoot) {
                MainActivity.start(context)
            }
            return
        }

        if (action == Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED) {
            context.sendBroadcast(Intent(ACTION_CONFIG_CHANGED).setPackage(context.packageName))
        }
    }

    companion object {
        const val ACTION_CONFIG_CHANGED = "com.esper.kioskplayer.CONFIG_CHANGED"
    }
}
