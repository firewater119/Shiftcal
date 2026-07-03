package com.hi.shiftcal.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val label = intent.getStringExtra("label") ?: "알람"

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "shiftcal_alarm"
        if (nm.getNotificationChannel(channelId) == null) {
            val ch = NotificationChannel(channelId, "근무 알람", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
            }
            nm.createNotificationChannel(ch)
        }

        val ringIntent = Intent(ctx, AlarmRingActivity::class.java).apply {
            putExtra("label", label)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val fullScreenPi = PendingIntent.getActivity(
            ctx, 2001, ringIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("교대달력 알람")
            .setContentText(label)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPi, true)
            .setAutoCancel(true)
            .build()
        nm.notify(3001, notif)

        // 화면을 직접 띄우는 시도 (full-screen intent가 시스템에 의해 지연될 경우 대비)
        runCatching { ctx.startActivity(ringIntent) }

        // 다음 알람 체인 예약
        AlarmScheduler.scheduleNext(ctx)
    }
}
