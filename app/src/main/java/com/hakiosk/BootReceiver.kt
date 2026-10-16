package com.hakiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Starts the kiosk after boot. On Android 10+ background activity starts are blocked unless the
 * app is the default Home app, so set it as Home (Settings → Kiosk → "Set as Home app").
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
