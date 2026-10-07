package app.yougram.feature.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import app.yougram.YougramApp

/** Действия из уведомления: «Ответить», «Прочитано» и смахивание. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val center = (context.applicationContext as YougramApp).container.notificationCenter
        val chatId = intent.getLongExtra(NotificationCenter.EXTRA_CHAT_ID, 0L)
        val groupId = intent.getIntExtra(NotificationCenter.EXTRA_GROUP_ID, 0)
        val messageId = intent.getLongExtra(NotificationCenter.EXTRA_MESSAGE_ID, 0L)
        val maxId = intent.getIntExtra(NotificationCenter.EXTRA_MAX_NOTIFICATION, 0)
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationCenter.KEY_REPLY)?.toString().orEmpty()
        val action = intent.action
        val pending = goAsync()
        center.launch {
            try {
                when (action) {
                    NotificationCenter.ACTION_REPLY -> center.reply(chatId, groupId, messageId, text)
                    NotificationCenter.ACTION_READ -> center.markRead(chatId, groupId, messageId)
                    NotificationCenter.ACTION_DISMISS -> center.dismissed(groupId, maxId)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
