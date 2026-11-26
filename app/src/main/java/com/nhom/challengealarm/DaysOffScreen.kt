package com.nhom.challengealarm

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.TimeZone

/** Tên gợi ý cho kỳ nghỉ: chọn một chạm, khỏi mở bàn phím. */
private val VACATION_NAMES = listOf("Nghỉ học", "Nghỉ phép", "Về quê", "Du lịch", "Nghỉ ốm", "Thi cử")

/**
 * Thẻ "Ngày nghỉ sắp tới" ở tab Chuông: ngày nghỉ gần nhất (lễ hoặc kỳ nghỉ riêng), còn mấy ngày,
 * bao nhiêu chuông sẽ tự không reo hôm đó, và ngày âm lịch hôm nay. Chạm để mở lịch nghỉ.
 */
@Composable
fun DaysOffCard(alarms: List<AlarmEntity>, vacations: List<Vacation>, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = AppTheme.palette
    val today = remember(vacations) { Ymd.of(System.currentTimeMillis()) }
    val next = remember(today, vacations) { DaysOff.next(today, days = 400, vacations = vacations) }
    val lunar = remember(today) { LunarCalendar.toLunar(today) }
    val skipping = alarms.count { it.enabled && it.skipHolidays && it.daysMask != 0 }
    AppCard(modifier, onClick = onClick, padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(p.accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Event, contentDescription = null, tint = p.accent) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("NGÀY NGHỈ SẮP TỚI", style = MaterialTheme.typography.labelSmall, color = p.muted)
                if (next != null) {
                    val (day, name) = next
                    val left = daysBetween(today, day)
                    Text(name, style = MaterialTheme.typography.titleMedium, color = p.text, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${day.weekdayShort} ${day.shortText} · " + when (left) {
                            0 -> "hôm nay"
                            1 -> "ngày mai"
                            else -> "còn $left ngày"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.text.copy(alpha = 0.8f),
                    )
                }
                Text(
                    (if (skipping > 0) "$skipping chuông tự không reo ngày nghỉ · " else "Bật \"Không reo ngày nghỉ\" trong chuông · ") +
                        "Hôm nay âm lịch ${lunar.day}/${lunar.month}${if (lunar.leap) " nhuận" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.muted,
                )
            }
        }
    }
}

/**
 * Lịch nghỉ: kỳ nghỉ của tôi (thêm bằng lịch chọn khoảng ngày) và ngày lễ Việt Nam 12 tháng tới.
 * Chuông bật "Không reo ngày nghỉ" tự bỏ qua những ngày này rồi reo lại như cũ, khỏi tắt chuông rồi quên bật.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaysOffSheet(
    vacations: List<Vacation>,
    onAdd: (start: Ymd, end: Ymd, name: String) -> Unit,
    onRemove: (Vacation) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val today = remember { Ymd.of(System.currentTimeMillis()) }
    val lunar = remember { LunarCalendar.toLunar(today) }
    val holidays = remember { groupHolidays(VnHolidays.upcoming(today, 365)) }
    var picking by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.background,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = GUTTER).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Ngày nghỉ", style = MaterialTheme.typography.titleLarge)
            Text(
                "Hôm nay ${today.weekdayShort} $today, âm lịch ${lunar.text} năm ${lunar.yearName}.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Chuông có bật \"Không reo ngày nghỉ\" sẽ tự bỏ qua những ngày dưới đây, hết kỳ nghỉ thì reo lại như cũ. " +
                    "Không còn cảnh tắt chuông trước Tết rồi quên bật, sáng đi học lại bị muộn.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )

            Text("Kỳ nghỉ của tôi", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
            SettingsGroup {
                if (vacations.isEmpty()) {
                    Text(
                        "Chưa có. Thêm kỳ nghỉ học, nghỉ phép, về quê... để chuông không reo những ngày đó.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                vacations.forEachIndexed { i, v ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(v.name, style = MaterialTheme.typography.bodyLarge)
                            val days = daysBetween(v.start, v.end) + 1
                            Text(
                                "${v.start.weekdayShort} ${v.start.shortText} – ${v.end.weekdayShort} ${v.end.shortText} · $days ngày",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onRemove(v) }) {
                            Icon(Icons.Default.Close, contentDescription = "Xóa kỳ nghỉ ${v.name}")
                        }
                    }
                    if (i < vacations.lastIndex) RowDivider()
                }
            }
            Button(
                onClick = { picking = true },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.palette.accent, contentColor = AppTheme.palette.onAccent),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Thêm kỳ nghỉ")
            }

            Text("Ngày lễ 12 tháng tới", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            SettingsGroup {
                holidays.forEachIndexed { i, h ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(h.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (h.days > 1) {
                                Text("${h.days} ngày", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                            }
                        }
                        Text(h.rangeText, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        h.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
                    }
                    if (i < holidays.lastIndex) RowDivider()
                }
            }
            Text(
                "Theo Bộ luật Lao động 2019: Tết Dương lịch, Tết Nguyên đán (5 ngày, tính theo âm lịch), Giỗ Tổ Hùng Vương " +
                    "(10/3 âm lịch), 30/4, 1/5, Quốc khánh (2 ngày); lễ trùng cuối tuần được nghỉ bù. Chính phủ có thể " +
                    "hoán đổi ngày làm việc mỗi năm; lịch trường hoặc công ty khác thì thêm vào \"Kỳ nghỉ của tôi\".",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
    }

    if (picking) {
        VacationPicker(
            today = today,
            onPick = { start, end, name ->
                picking = false
                onAdd(start, end, name)
            },
            onDismiss = { picking = false },
        )
    }
}

/** Chọn khoảng ngày nghỉ trên lịch và đặt tên bằng một chạm. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VacationPicker(today: Ymd, onPick: (Ymd, Ymd, String) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDateRangePickerState()
    var name by remember { mutableStateOf(VACATION_NAMES.first()) }
    val utc = remember { TimeZone.getTimeZone("UTC") }
    val start = state.selectedStartDateMillis?.let { Ymd.of(it, utc) }
    // Chỉ chọn một ngày (chưa chọn ngày kết thúc) thì kỳ nghỉ là đúng ngày đó.
    val end = state.selectedEndDateMillis?.let { Ymd.of(it, utc) } ?: start
    val valid = start != null && end != null && end >= today

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { if (start != null && end != null) onPick(maxOf(start, today), end, name) }, enabled = valid) {
                Text("Thêm")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VACATION_NAMES.forEach { n ->
                FilterChip(
                    selected = n == name,
                    onClick = { name = n },
                    label = { Text(n) },
                    shape = RoundedCornerShape(16.dp),
                )
            }
        }
        DateRangePicker(
            state = state,
            modifier = Modifier.fillMaxWidth().height(420.dp),
            showModeToggle = false,
        )
    }
}
