package com.oro.app.device

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

/**
 * Holds the user's Notification access for the device agent. Android lets an
 * enabled notification listener reach every app's media session
 * ([MediaControl]): exact seeking, play and pause for the app that is really
 * playing, and what is playing. That is all it is for -- it never reads, keeps
 * or acts on notifications.
 */
class OroNotificationListener : NotificationListenerService() {

    companion object {
        fun component(context: Context): ComponentName = ComponentName(context, OroNotificationListener::class.java)

        /** Whether the user has granted Oro Notification access. */
        fun isEnabled(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= 27) {
                val nm = context.getSystemService(NotificationManager::class.java)
                runCatching { nm?.isNotificationListenerAccessGranted(component(context)) }.getOrNull() == true
            } else {
                context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
            }

        /** The system screen that grants it: Oro's own page where the phone has one, else the list. */
        fun openSettings(context: Context) {
            val detail = if (Build.VERSION.SDK_INT >= 30) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(context).flattenToString())
            } else null
            val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            for (intent in listOfNotNull(detail, list)) {
                val opened = runCatching {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true
                }.getOrDefault(false)
                if (opened) return
            }
        }
    }
}
