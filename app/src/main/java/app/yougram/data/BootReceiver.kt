package app.yougram.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.yougram.YougramApp

/** После перезагрузки поднимает фоновое соединение, если оно включено в настройках. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val container = (context.applicationContext as YougramApp).container
        if (container.settings.notifications.value.backgroundConnection) ConnectionService.start(context)
    }
}
