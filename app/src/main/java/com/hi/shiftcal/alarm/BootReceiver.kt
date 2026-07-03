package com.hi.shiftcal.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        // 재부팅 / 시간 변경 시 알람 재예약
        AlarmScheduler.scheduleNext(ctx)
    }
}
