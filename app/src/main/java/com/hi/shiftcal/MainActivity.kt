package com.hi.shiftcal

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hi.shiftcal.alarm.AlarmScheduler
import com.hi.shiftcal.data.CustomAlarm
import com.hi.shiftcal.data.ShiftType
import com.hi.shiftcal.data.Store
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestExactAlarmIfNeeded()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = Color.Black) {
                    App()
                }
            }
        }
    }

    private fun requestExactAlarmIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                runCatching {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData(android.net.Uri.parse("package:$packageName"))
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AlarmScheduler.scheduleNext(this)
    }
}

@Composable
fun App() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var month by remember { mutableStateOf(YearMonth.now()) }
    var refresh by remember { mutableStateOf(0) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showPattern by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val assignments = remember(refresh) { Store.getAssignments(ctx) }
    val customAlarms = remember(refresh) { Store.getCustomAlarms(ctx) }
    val nextAlarm = remember(refresh) { AlarmScheduler.nextOccurrence(ctx) }

    fun mutated() {
        AlarmScheduler.scheduleNext(ctx)
        refresh++
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        // ── 상단: 월 이동 + 메뉴 ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { month = month.minusMonths(1) }) { Text("◀", fontSize = 18.sp) }
            Text(
                month.format(DateTimeFormatter.ofPattern("yyyy. MM")),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            TextButton(onClick = { month = month.plusMonths(1) }) { Text("▶", fontSize = 18.sp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showPattern = true }, modifier = Modifier.weight(1f)) {
                Text("패턴 자동입력", fontSize = 13.sp)
            }
            OutlinedButton(onClick = { showSettings = true }, modifier = Modifier.weight(1f)) {
                Text("근무별 알람설정", fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(8.dp))

        // ── 다음 알람 배너 ──
        if (nextAlarm != null) {
            Surface(
                color = Color(0xFF1E2A45),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "⏰ 다음 알람: ${nextAlarm.at.format(DateTimeFormatter.ofPattern("MM.dd(E) HH:mm"))} · ${nextAlarm.label}",
                    modifier = Modifier.padding(10.dp),
                    color = Color(0xFF9DB8F0),
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // ── 요일 헤더 ──
        Row(Modifier.fillMaxWidth()) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, d ->
                Text(
                    d,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    color = when (i) {
                        0 -> Color(0xFFE57373)
                        6 -> Color(0xFF64B5F6)
                        else -> Color.LightGray
                    }
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        // ── 달력 그리드 ──
        val first = month.atDay(1)
        val lead = first.dayOfWeek.value % 7 // 일요일 시작
        val totalCells = lead + month.lengthOfMonth()
        val rows = (totalCells + 6) / 7

        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c - lead
                    if (idx in 0 until month.lengthOfMonth()) {
                        val date = month.atDay(idx + 1)
                        DayCell(
                            date = date,
                            type = assignments[date],
                            alarmCount = customAlarms.count { it.date == date && it.enabled },
                            isToday = date == LocalDate.now(),
                            modifier = Modifier.weight(1f)
                        ) { selectedDate = date }
                    } else {
                        Box(Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "날짜를 누르면 근무 입력 / 개별 알람 등록 · 비번(비)날은 알람이 자동 제외됩니다.",
            fontSize = 11.sp, color = Color.Gray
        )
    }

    // ── 날짜 상세 다이얼로그 ──
    selectedDate?.let { date ->
        DayDialog(
            date = date,
            type = assignments[date],
            alarms = customAlarms.filter { it.date == date },
            onSetType = { t -> Store.setAssignment(ctx, date, t); mutated() },
            onAddAlarm = { time, label -> Store.addCustomAlarm(ctx, date, time, label); mutated() },
            onToggleAlarm = { id, en -> Store.toggleCustomAlarm(ctx, id, en); mutated() },
            onDeleteAlarm = { id -> Store.deleteCustomAlarm(ctx, id); mutated() },
            onDismiss = { selectedDate = null }
        )
    }

    if (showPattern) {
        PatternDialog(
            defaultStart = selectedDate ?: LocalDate.now(),
            onApply = { start, pattern, days ->
                Store.applyPattern(ctx, start, pattern, days)
                mutated()
                showPattern = false
            },
            onDismiss = { showPattern = false }
        )
    }

    if (showSettings) {
        TypeAlarmDialog(onChanged = { mutated() }, onDismiss = { showSettings = false })
    }
}

@Composable
fun DayCell(
    date: LocalDate,
    type: ShiftType?,
    alarmCount: Int,
    isToday: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .padding(1.dp)
            .background(
                if (isToday) Color(0xFF1E2A45) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "${date.dayOfMonth}",
            fontSize = 14.sp,
            color = when (date.dayOfWeek) {
                DayOfWeek.SUNDAY -> Color(0xFFE57373)
                DayOfWeek.SATURDAY -> Color(0xFF64B5F6)
                else -> Color.White
            }
        )
        Spacer(Modifier.height(3.dp))
        if (type != null) {
            if (type == ShiftType.BI) {
                Text("비", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(type.textColorHex))
            } else {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(Color(type.colorHex), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(type.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(type.textColorHex))
                }
            }
        } else {
            Spacer(Modifier.height(28.dp))
        }
        if (alarmCount > 0) {
            Text("⏰$alarmCount", fontSize = 9.sp, color = Color(0xFF9DB8F0))
        } else {
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun DayDialog(
    date: LocalDate,
    type: ShiftType?,
    alarms: List<CustomAlarm>,
    onSetType: (ShiftType?) -> Unit,
    onAddAlarm: (LocalTime, String) -> Unit,
    onToggleAlarm: (Long, Boolean) -> Unit,
    onDeleteAlarm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var label by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        title = { Text(date.format(DateTimeFormatter.ofPattern("M월 d일 (E)"))) },
        text = {
            Column {
                Text("근무 유형", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShiftType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { onSetType(if (type == t) null else t) },
                            label = { Text(t.label) }
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("이 날짜의 개별 알람", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(6.dp))
                alarms.forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.time.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 16.sp)
                            Text(a.label, fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(checked = a.enabled, onCheckedChange = { onToggleAlarm(a.id, it) })
                        TextButton(onClick = { onDeleteAlarm(a.id) }) { Text("삭제", color = Color(0xFFE57373)) }
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    placeholder = { Text("알람 이름 (예: 병원 예약)", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = {
                        val now = LocalTime.now()
                        TimePickerDialog(ctx, { _, h, m ->
                            onAddAlarm(LocalTime.of(h, m), label.ifBlank { "개인 알람" })
                            label = ""
                        }, now.hour, now.minute, false).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("＋ 알람 추가 (시간 선택)") }
            }
        }
    )
}

@Composable
fun PatternDialog(
    defaultStart: LocalDate,
    onApply: (LocalDate, List<ShiftType>, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var patternText by remember { mutableStateOf("당비비") }
    var startText by remember { mutableStateOf(defaultStart.toString()) }
    var daysText by remember { mutableStateOf("90") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("근무 패턴 자동입력") },
        text = {
            Column {
                Text("패턴 (예: 당비비 / 주야비휴 → 휴는 '비'로)", fontSize = 12.sp, color = Color.Gray)
                OutlinedTextField(value = patternText, onValueChange = { patternText = it }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Text("시작일 (YYYY-MM-DD)", fontSize = 12.sp, color = Color.Gray)
                OutlinedTextField(value = startText, onValueChange = { startText = it }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Text("적용 일수", fontSize = 12.sp, color = Color.Gray)
                OutlinedTextField(value = daysText, onValueChange = { daysText = it }, singleLine = true)
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = Color(0xFFE57373), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val pattern = patternText.mapNotNull { ShiftType.fromChar(it) }
                val start = runCatching { LocalDate.parse(startText.trim()) }.getOrNull()
                val days = daysText.trim().toIntOrNull()
                if (pattern.isEmpty()) { error = "패턴에 당/비/야/주 문자를 입력하세요"; return@TextButton }
                if (start == null) { error = "시작일 형식이 올바르지 않습니다"; return@TextButton }
                if (days == null || days <= 0 || days > 730) { error = "적용 일수는 1~730 사이"; return@TextButton }
                onApply(start, pattern, days)
            }) { Text("적용") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
fun TypeAlarmDialog(onChanged: () -> Unit, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var refresh by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        title = { Text("근무별 기본 알람") },
        text = {
            Column {
                Text("근무를 달력에 등록하면 아래 시간에 자동으로 알람이 예약됩니다. 비번(비)은 알람 없음.", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(10.dp))
                key(refresh) {
                    listOf(ShiftType.DANG, ShiftType.YA, ShiftType.JU).forEach { t ->
                        val time = Store.getTypeAlarm(ctx, t)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .background(Color(t.colorHex), CircleShape),
                                contentAlignment = Alignment.Center
                            ) { Text(t.label, color = Color(t.textColorHex), fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "알람 없음",
                                modifier = Modifier.weight(1f), fontSize = 16.sp
                            )
                            TextButton(onClick = {
                                val base = time ?: LocalTime.of(6, 0)
                                TimePickerDialog(ctx, { _, h, m ->
                                    Store.setTypeAlarm(ctx, t, LocalTime.of(h, m))
                                    onChanged(); refresh++
                                }, base.hour, base.minute, false).show()
                            }) { Text("변경") }
                            TextButton(onClick = {
                                Store.setTypeAlarm(ctx, t, null)
                                onChanged(); refresh++
                            }) { Text("끄기", color = Color.Gray) }
                        }
                    }
                }
            }
        }
    )
}
