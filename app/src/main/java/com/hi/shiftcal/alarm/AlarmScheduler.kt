package com.hi.shiftcal.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.hi.shiftcal.data.ShiftType
import com.hi.shiftcal.data.Store
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * "다음 알람 하나만" setAlarmClock 으로 예약하고,
 * 울리면 AlarmReceiver 가 그 다음 알람을 다시 예약하는 체인 방식.
 * setAlarmClock 은 Doze 모드에서도 정확히 울리며 상태바에 알람 아이콘이 표시된다.
 */
object AlarmScheduler {

    private const val REQUEST_CODE = 1001

    data class Occurrence(val at: LocalDateTime, val label: String)

    fun nextOccurrence(ctx: Context): Occurrence? {
        val now = LocalDateTime.now()
        val candidates = mutableListOf<Occurrence>()

        val assignments = Store.getAssignments(ctx)
        val today = LocalDate.now()
        // 향후 60일치 근무 알람 후보
        for (i in 0..60) {
            val date = today.plusDays(i.toLong())
            val type = assignments[date] ?: continue
            if (type == ShiftType.BI) continue // 비번날 알람 자동 제외
            val time = Store.getTypeAlarm(ctx, type) ?: continue
            val dt = LocalDateTime.of(date, time)
            if (dt.isAfter(now)) candidates.add(Occurrence(dt, "${type.label} 근무 알람"))
        }

        // 개별 알람 후보
        Store.getCustomAlarms(ctx).forEach { a ->
            if (!a.enabled) return@forEach
            val dt = LocalDateTime.of(a.date, a.time)
            if (dt.isAfter(now)) candidates.add(Occurrence(dt, a.label))
        }

        return candidates.minByOrNull { it.at }
    }

    fun scheduleNext(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(ctx)
        am.cancel(pi)

        val next = nextOccurrence(ctx) ?: return

        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) return

        val millis = next.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(ctx, AlarmReceiver::class.java).putExtra("label", next.label)
        val firePi = PendingIntent.getBroadcast(
            ctx, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showPi = PendingIntent.getActivity(
            ctx, 0,
            ctx.packageManager.getLaunchIntentForPackage(ctx.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(millis, showPi), firePi)
    }

    private fun pendingIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, REQUEST_CODE, Intent(ctx, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
