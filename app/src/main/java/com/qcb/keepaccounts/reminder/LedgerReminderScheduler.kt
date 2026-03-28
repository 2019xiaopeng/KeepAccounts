package com.qcb.keepaccounts.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar
import java.util.Locale

object LedgerReminderScheduler {

    const val ACTION_DAILY_LEDGER_REMINDER = "com.qcb.keepaccounts.action.DAILY_LEDGER_REMINDER"
    const val CHANNEL_ID = "ledger_daily_reminder"

    private const val CHANNEL_NAME = "记账提醒"
    private const val CHANNEL_DESC = "每日提醒记一笔"
    private const val REQUEST_CODE_REMINDER = 2001

    fun scheduleDailyReminder(context: Context, reminderTime: String) {
        val parsed = parseReminderTime(reminderTime) ?: return
        val appContext = context.applicationContext
        ensureNotificationChannel(appContext)

        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = reminderPendingIntent(appContext)

        alarmManager.cancel(pendingIntent)

        val triggerAt = nextTriggerAt(parsed.first, parsed.second)
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            AlarmManager.INTERVAL_DAY,
            pendingIntent,
        )
    }

    fun cancelDailyReminder(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = reminderPendingIntent(appContext)
        alarmManager.cancel(pendingIntent)
    }

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = CHANNEL_DESC
        }
        manager.createNotificationChannel(channel)
    }

    internal fun reminderPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, LedgerReminderReceiver::class.java).apply {
            action = ACTION_DAILY_LEDGER_REMINDER
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_REMINDER,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun parseReminderTime(reminderTime: String): Pair<Int, Int>? {
        val regex = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$")
        val match = regex.matchEntire(reminderTime.trim()) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        return hour to minute
    }

    private fun nextTriggerAt(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance(Locale.CHINA)
        val trigger = Calendar.getInstance(Locale.CHINA).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (trigger.timeInMillis <= now.timeInMillis) {
            trigger.add(Calendar.DAY_OF_MONTH, 1)
        }
        return trigger.timeInMillis
    }
}
