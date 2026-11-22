package com.kraftplay.openiptv.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object EpgReminderManager {

    fun scheduleReminder(
        context: Context,
        programTitle: String,
        channelName: String,
        channelUrl: String,
        startTimeMs: Long
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, EpgReminderReceiver::class.java).apply {
            putExtra(EpgReminderReceiver.EXTRA_PROGRAM_TITLE, programTitle)
            putExtra(EpgReminderReceiver.EXTRA_CHANNEL_NAME, channelName)
            putExtra(EpgReminderReceiver.EXTRA_CHANNEL_URL, channelUrl)
        }

        val requestCode = (programTitle + channelUrl).hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Уведомление за 5 минут до начала (или через 3 секунды, если до начала осталось меньше 5 минут)
        val triggerTime = startTimeMs - 300000L
        val now = System.currentTimeMillis()
        val actualTriggerTime = if (triggerTime > now) triggerTime else now + 3000L

        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(actualTriggerTime, pendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } catch (e: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, actualTriggerTime, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, actualTriggerTime, pendingIntent)
                }
            } catch (e2: Exception) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, actualTriggerTime, pendingIntent)
            }
        }
    }

    fun cancelReminder(context: Context, programTitle: String, channelUrl: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, EpgReminderReceiver::class.java)
        val requestCode = (programTitle + channelUrl).hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}
