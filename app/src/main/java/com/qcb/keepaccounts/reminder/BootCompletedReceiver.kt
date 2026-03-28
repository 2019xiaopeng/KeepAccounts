package com.qcb.keepaccounts.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qcb.keepaccounts.data.local.preferences.UserSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val settings = UserSettingsRepository(context.applicationContext).settingsFlow.first()
                if (settings.initialized) {
                    LedgerReminderScheduler.scheduleDailyReminder(context, settings.reminderTime)
                }
            }
            pendingResult.finish()
        }
    }
}
