package com.kraftplay.openiptv.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kraftplay.openiptv.MainActivity
import com.kraftplay.openiptv.R

class EpgReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "epg_reminders_channel"
        const val EXTRA_PROGRAM_TITLE = "extra_program_title"
        const val EXTRA_CHANNEL_NAME = "extra_channel_name"
        const val EXTRA_CHANNEL_URL = "extra_channel_url"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_PROGRAM_TITLE) ?: com.kraftplay.openiptv.ui.tr("Скоро начнется передача")
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: "OpenIPTV"
        val channelUrl = intent.getStringExtra(EXTRA_CHANNEL_URL) ?: ""

        createNotificationChannel(context)

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("auto_play_url", channelUrl)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            channelUrl.hashCode(),
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(com.kraftplay.openiptv.ui.tr("Скоро начнется: {0}", title))
            .setContentText(com.kraftplay.openiptv.ui.tr("Канал: {0}", channelName))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(title.hashCode(), notification)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = com.kraftplay.openiptv.ui.tr("Напоминания ТВ-передач")
            val descriptionText = com.kraftplay.openiptv.ui.tr("Уведомления о начале выбранных ТВ-передач")
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
