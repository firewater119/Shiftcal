package com.hi.shiftcal.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/** 근무 유형: 당직 / 비번 / 야간 / 주간 */
enum class ShiftType(val label: String, val colorHex: Long, val textColorHex: Long) {
    DANG("당", 0xFFF5C842, 0xFF000000),   // 당직 - 노란 원
    BI("비", 0x00000000, 0xFFE57373),      // 비번 - 빨간 글자 (배경 없음)
    YA("야", 0xFF9E9E9E, 0xFF000000),      // 야간 - 회색 원
    JU("주", 0xFFFF9800, 0xFF000000);      // 주간 - 주황 원

    companion object {
        fun fromChar(c: Char): ShiftType? = entries.firstOrNull { it.label == c.toString() }
    }
}

data class CustomAlarm(
    val id: Long,
    val date: LocalDate,
    val time: LocalTime,
    val label: String,
    val enabled: Boolean
)

/**
 * SharedPreferences + JSON 기반 저장소.
 * 데이터 양이 작아(월 단위 근무표/알람) DB 없이도 충분하며 빌드 의존성을 최소화한다.
 */
object Store {

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("shiftcal", Context.MODE_PRIVATE)

    // ---------- 근무 배정 (날짜 -> 근무유형) ----------

    fun getAssignments(ctx: Context): MutableMap<LocalDate, ShiftType> {
        val raw = prefs(ctx).getString("assignments", "{}") ?: "{}"
        val obj = JSONObject(raw)
        val map = mutableMapOf<LocalDate, ShiftType>()
        for (key in obj.keys()) {
            runCatching {
                val t = ShiftType.valueOf(obj.getString(key))
                map[LocalDate.parse(key)] = t
            }
        }
        return map
    }

    fun setAssignment(ctx: Context, date: LocalDate, type: ShiftType?) {
        val map = getAssignments(ctx)
        if (type == null) map.remove(date) else map[date] = type
        saveAssignments(ctx, map)
    }

    fun applyPattern(ctx: Context, start: LocalDate, pattern: List<ShiftType>, days: Int) {
        if (pattern.isEmpty() || days <= 0) return
        val map = getAssignments(ctx)
        for (i in 0 until days) {
            map[start.plusDays(i.toLong())] = pattern[i % pattern.size]
        }
        saveAssignments(ctx, map)
    }

    private fun saveAssignments(ctx: Context, map: Map<LocalDate, ShiftType>) {
        val obj = JSONObject()
        map.forEach { (d, t) -> obj.put(d.toString(), t.name) }
        prefs(ctx).edit().putString("assignments", obj.toString()).apply()
    }

    // ---------- 근무유형별 기본 알람 시간 ----------

    /** 반환값 null = 이 근무유형은 알람 없음. 비번(BI)은 항상 null (알람 자동 제외). */
    fun getTypeAlarm(ctx: Context, type: ShiftType): LocalTime? {
        if (type == ShiftType.BI) return null
        val raw = prefs(ctx).getString("typeAlarm_${type.name}", defaultTypeAlarm(type)) ?: return null
        if (raw.isBlank()) return null
        return runCatching { LocalTime.parse(raw) }.getOrNull()
    }

    fun setTypeAlarm(ctx: Context, type: ShiftType, time: LocalTime?) {
        prefs(ctx).edit().putString("typeAlarm_${type.name}", time?.toString() ?: "").apply()
    }

    private fun defaultTypeAlarm(type: ShiftType): String = when (type) {
        ShiftType.DANG -> "06:00"
        ShiftType.JU -> "06:00"
        ShiftType.YA -> "16:00"
        ShiftType.BI -> ""
    }

    // ---------- 날짜별 개별 알람 ----------

    fun getCustomAlarms(ctx: Context): MutableList<CustomAlarm> {
        val raw = prefs(ctx).getString("customAlarms", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<CustomAlarm>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            runCatching {
                list.add(
                    CustomAlarm(
                        id = o.getLong("id"),
                        date = LocalDate.parse(o.getString("date")),
                        time = LocalTime.parse(o.getString("time")),
                        label = o.optString("label", "개인 알람"),
                        enabled = o.optBoolean("enabled", true)
                    )
                )
            }
        }
        return list
    }

    fun addCustomAlarm(ctx: Context, date: LocalDate, time: LocalTime, label: String) {
        val list = getCustomAlarms(ctx)
        list.add(CustomAlarm(System.currentTimeMillis(), date, time, label, true))
        saveCustomAlarms(ctx, list)
    }

    fun toggleCustomAlarm(ctx: Context, id: Long, enabled: Boolean) {
        val list = getCustomAlarms(ctx).map { if (it.id == id) it.copy(enabled = enabled) else it }
        saveCustomAlarms(ctx, list)
    }

    fun deleteCustomAlarm(ctx: Context, id: Long) {
        val list = getCustomAlarms(ctx).filter { it.id != id }
        saveCustomAlarms(ctx, list)
    }

    private fun saveCustomAlarms(ctx: Context, list: List<CustomAlarm>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("date", it.date.toString())
                    .put("time", it.time.toString())
                    .put("label", it.label)
                    .put("enabled", it.enabled)
            )
        }
        prefs(ctx).edit().putString("customAlarms", arr.toString()).apply()
    }
}
