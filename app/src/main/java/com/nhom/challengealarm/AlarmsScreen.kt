package com.nhom.challengealarm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

private fun greeting(now: Long): String =
    when (Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)) {
        in 4..10 -> "Chào buổi sáng"
        in 11..12 -> "Chào buổi trưa"
        in 13..17 -> "Chào buổi chiều"
        else -> "Chào buổi tối"
    }

/** "14 tháng 10" cho tiêu đề ban đêm (giống "20 February" trong ảnh mẫu); thứ hiện ở dòng nhỏ phía trên. */
private fun dateTitle(now: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = now }
    return "${c.get(Calendar.DAY_OF_MONTH)} tháng ${c.get(Calendar.MONTH) + 1}"
}


/**
 * Tab Báo thức. Hai bố cục theo chế độ:
 * Ngày  (ảnh mẫu 1): lời chào, đồng hồ kim neumorphism, chuông kế tiếp, các thẻ chuông nổi, nút "THÊM".
 * Đêm   (ảnh mẫu 2): ngày tháng, thẻ "Tối nay" với nút "Bắt đầu ngủ", thẻ Chất lượng / Thời lượng, các thẻ chuông tím.
 */
@Composable
fun AlarmsScreen(
    alarms: List<AlarmEntity>,
    loading: Boolean,
    lockedIds: Set<Int>,
    library: List<ChallengeItem>,
    next: NextAlarm?,
    profileName: String?,
    sleep: SleepUiState,
    setupDone: Int,
    setupTotal: Int,
    contentPadding: PaddingValues,
    onProfile: () -> Unit,
    onSetup: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (AlarmEntity) -> Unit,
    onToggle: (AlarmEntity, Boolean) -> Unit,
    onStartSleep: () -> Unit,
    onSeeSleep: () -> Unit,
    wakeCheck: WakeCheckState? = null,
    onConfirmAwake: () -> Unit = {},
    onNap: () -> Unit = {},
    vacations: List<Vacation> = emptyList(),
    onDaysOff: () -> Unit = {},
) {
    val p = AppTheme.palette
    val now = System.currentTimeMillis()
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        // Đang hỏi "Bạn còn thức không?": thẻ trả lời ngay trên cùng (phòng khi người dùng mở app thay vì chạm thông báo).
        if (wakeCheck != null) {
            item { WakeCheckBanner(wakeCheck, onConfirmAwake) }
        }

        if (p.isNight) {
            nightHeader(now, profileName, next, sleep, onProfile, onStartSleep, onSeeSleep)
        } else {
            dayHeader(now, profileName, next, onProfile, onNap)
        }

        if (setupDone < setupTotal) {
            item { SetupBanner(setupDone, setupTotal, onSetup) }
        }

        item {
            SectionHeader(
                "Báo thức",
                trailing = if (p.isNight) "+ Thêm" else alarms.takeIf { it.isNotEmpty() }?.let { "${it.count { a -> a.enabled }}/${it.size} đang bật" },
                onTrailing = if (p.isNight) onAdd else null,
            )
        }

        if (alarms.isEmpty() && !loading) {
            item {
                EmptyState(
                    icon = Icons.Default.Alarm,
                    title = "Đặt giờ dậy đầu tiên",
                    body = "Thêm chuông rồi chọn thử thách. Phải làm xong thử thách mới tắt được chuông.",
                    actionLabel = "Thêm chuông",
                    onAction = onAdd,
                )
            }
        }
        itemsIndexed(alarms, key = { _, a -> a.id }) { index, alarm ->
            AlarmCard(
                alarm = alarm,
                index = index,
                library = library,
                locked = alarm.id in lockedIds,
                vacations = vacations,
                modifier = Modifier.padding(horizontal = GUTTER, vertical = 8.dp),
                onClick = { onOpen(alarm) },
                onToggle = { onToggle(alarm, it) },
            )
        }

        if (!p.isNight && alarms.isNotEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                    PillButton("Thêm", onAdd, Modifier.width(150.dp), icon = Icons.Default.Add)
                }
            }
        }

        // Ngày nghỉ sắp tới (lễ Việt Nam, Tết âm lịch, kỳ nghỉ riêng): chạm để xem và thêm kỳ nghỉ.
        item {
            DaysOffCard(
                alarms = alarms,
                vacations = vacations,
                modifier = Modifier.padding(horizontal = GUTTER, vertical = 10.dp),
                onClick = onDaysOff,
            )
        }
    }
}

private fun LazyListScope.dayHeader(
    now: Long,
    profileName: String?,
    next: NextAlarm?,
    onProfile: () -> Unit,
    onNap: () -> Unit,
) {
    item {
        Row(
            Modifier.fillMaxWidth().padding(start = GUTTER, end = GUTTER, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(greeting(now), style = MaterialTheme.typography.bodyMedium, color = AppTheme.palette.muted)
                Text(
                    profileName?.takeIf { it.isNotBlank() } ?: "Challenge Alarm",
                    style = MaterialTheme.typography.titleLarge,
                    color = AppTheme.palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ProfileButton(profileName, onProfile)
        }
    }
    item {
        Box(Modifier.fillMaxWidth().padding(vertical = 26.dp), contentAlignment = Alignment.Center) {
            AnalogClock(diameter = 250.dp)
        }
    }
    item {
        val p = AppTheme.palette
        Column(Modifier.fillMaxWidth().padding(horizontal = GUTTER), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("CHUÔNG KẾ TIẾP", style = MaterialTheme.typography.labelSmall, color = p.muted, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(4.dp))
            Text(next?.let { hhmm(it.at) } ?: "--:--", style = DigitalStyle.copy(fontSize = 34.sp), color = p.text)
            Text(
                next?.let { "${weekdayFull(it.at)}, ${formatLeft(it.minutesLeft)}" } ?: "Chưa có chuông nào đang bật",
                style = MaterialTheme.typography.bodyMedium,
                color = p.muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            // Ngủ trưa: một chạm là có chuông, khỏi lo ngủ quá giấc.
            PillButton("Chợp mắt", onNap, icon = Icons.Default.Hotel)
        }
    }
}

private fun LazyListScope.nightHeader(
    now: Long,
    profileName: String?,
    next: NextAlarm?,
    sleep: SleepUiState,
    onProfile: () -> Unit,
    onStartSleep: () -> Unit,
    onSeeSleep: () -> Unit,
) {
    item {
        Row(
            Modifier.fillMaxWidth().padding(start = GUTTER, end = GUTTER, top = 16.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(weekdayFull(now), style = MaterialTheme.typography.bodyLarge, color = AppTheme.palette.muted)
                Text(
                    dateTitle(now),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ProfileButton(profileName, onProfile)
            Spacer(Modifier.width(8.dp))
            ModeButton()
        }
    }
    item {
        val plan = sleep.plan
        val wake = next?.at
        FeatureCard(
            title = if (wake != null && wake - now > 24 * 3_600_000L) "Đêm ${weekdayFull(wake).lowercase()}" else "Tối nay",
            subtitle = when {
                plan != null && wake != null -> "Giờ ngủ: ${hhmm(plan.bedtime)} - ${hhmm(wake)}"
                wake != null -> "Chuông kế tiếp: ${hhmm(wake)}"
                else -> "Chưa có chuông nào đang bật"
            },
            modifier = Modifier.padding(horizontal = GUTTER),
            buttonText = "Bắt đầu ngủ",
            onButton = onStartSleep,
        )
    }
    item { SectionHeader("Thống kê giấc ngủ", "Xem tất cả", onSeeSleep) }
    item {
        val p = AppTheme.palette
        val report = sleep.report
        Row(Modifier.padding(horizontal = GUTTER), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(
                title = "Chất lượng",
                value = report?.qualityPct?.let { "$it %" } ?: "--",
                progress = report?.qualityPct?.let { it / 100f },
                colors = p.statA,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = "Thời lượng",
                value = report?.avgAsleepMin?.let { hoursMinutes(it) } ?: "--",
                progress = report?.avgAsleepMin?.let { it / sleep.targetMin.toFloat() },
                colors = p.statB,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Nút tròn mở hồ sơ: chữ cái đầu của tên, chưa có tên thì biểu tượng người. */
@Composable
private fun ProfileButton(name: String?, onClick: () -> Unit) {
    val p = AppTheme.palette
    val initial = name?.trim()?.firstOrNull()?.uppercaseChar()
    Box(
        Modifier
            .size(44.dp)
            .surfaceBlock(22.dp, elevation = 6.dp, nightColor = Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (initial != null) {
            Text(initial.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = p.text)
        } else {
            Icon(Icons.Default.Person, contentDescription = "Hồ sơ", tint = p.text)
        }
    }
}

/**
 * Thẻ một chuông (ảnh mẫu ban ngày: "ALARM 1 / 20:50 AM / Every day" và công tắc nhỏ).
 * Thêm hàng biểu tượng thử thách, số màn, nhãn khóa khi chuông đang reo/hoãn. Chuông tắt thì mờ đi.
 */
@Composable
private fun AlarmCard(
    alarm: AlarmEntity,
    index: Int,
    library: List<ChallengeItem>,
    locked: Boolean,
    vacations: List<Vacation>,
    modifier: Modifier,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val p = AppTheme.palette
    // Lần reo tới rơi vào ngày nghỉ: báo nghỉ gì và khi nào reo lại.
    val offNote = remember(alarm, vacations) { dayOffNote(alarm) }
    val kinds = alarm.challengeIdList().mapNotNull { challengeKindOf(it, library) }.ifEmpty { listOf(ChallengeType.MATH) }
    AppCard(modifier, onClick = onClick, padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).alpha(if (alarm.enabled) 1f else 0.5f)) {
                Text(
                    (alarm.label.trim().ifEmpty { "Chuông ${index + 1}" }).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = p.muted,
                    letterSpacing = 1.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "%02d:%02d".format(alarm.hour, alarm.minute),
                    style = if (p.isNight) {
                        MaterialTheme.typography.displayLarge.copy(fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Light)
                    } else {
                        DigitalStyle.copy(fontSize = 30.sp)
                    },
                    color = p.text,
                )
                Text(
                    repeatSummary(alarm.daysMask) + if (alarm.skipHolidays && alarm.daysMask != 0) " · trừ ngày nghỉ" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.text.copy(alpha = 0.75f),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Tối đa 3 biểu tượng + "+N" để hàng không tràn trên màn hình hẹp.
                    kinds.take(3).forEach { IconBadge(it, size = 26.dp) }
                    if (kinds.size > 3) SoftPill("+${kinds.size - 3}")
                    if (alarm.rounds > 1) SoftPill("${alarm.rounds} màn")
                }
                val skipping = alarm.enabled && alarm.skipUntil > System.currentTimeMillis()
                if (skipping || alarm.nap) {
                    Spacer(Modifier.height(8.dp))
                    when {
                        alarm.nap -> Pill("Chợp mắt · reo xong tự xóa", p.accent.copy(alpha = 0.22f), p.text)
                        else -> Pill("Bỏ qua ${weekdayShort(alarm.skipUntil)} ${hhmm(alarm.skipUntil)}", p.accent2.copy(alpha = 0.22f), p.text)
                    }
                }
                if (offNote != null) {
                    Spacer(Modifier.height(8.dp))
                    Pill(offNote, p.accent2.copy(alpha = 0.22f), p.text)
                }
                if (locked) {
                    Spacer(Modifier.height(8.dp))
                    Pill("Đang reo/hoãn · đã khóa", p.accent.copy(alpha = 0.25f), p.text)
                }
            }
            SoftToggle(checked = alarm.enabled, onCheckedChange = onToggle)
        }
    }
}

/** "Nghỉ Quốc khánh · reo lại T5 3/9" khi lần reo tới của chuông rơi vào ngày nghỉ, ngược lại null. */
private fun dayOffNote(alarm: AlarmEntity, now: Long = System.currentTimeMillis()): String? {
    if (!alarm.enabled || !alarm.skipHolidays || alarm.daysMask == 0) return null
    val raw = AlarmScheduler.nextIgnoringDaysOff(alarm, now) ?: return null
    val real = AlarmScheduler.nextTriggerMillis(alarm, now)
    if (raw == real) return null
    val why = DaysOff.reason(Ymd.of(raw)) ?: "ngày nghỉ"
    val title = if (why.startsWith("Nghỉ")) why else "Nghỉ $why"
    return title + (real?.let { " · reo lại ${weekdayShort(it)} ${Ymd.of(it).shortText}" } ?: "")
}

/**
 * Thẻ "Bạn còn thức không?" (kiểm tra đã tỉnh). Đang hỏi thì có nút trả lời; chưa đến giờ hỏi thì chỉ báo giờ sẽ hỏi
 * (không cho trả lời trước, để không thể bấm sẵn rồi ngủ tiếp).
 */
@Composable
private fun WakeCheckBanner(check: WakeCheckState, onConfirm: () -> Unit) {
    val p = AppTheme.palette
    AppCard(Modifier.padding(horizontal = GUTTER, vertical = 10.dp), padding = 18.dp) {
        if (check.asking) {
            Text("Bạn còn thức không?", style = MaterialTheme.typography.titleLarge, color = p.text)
            Text(
                "Bấm \"Tôi đã dậy\" trước ${hhmm(check.deadline)}, nếu không chuông sẽ reo lại.",
                style = MaterialTheme.typography.bodyMedium,
                color = p.muted,
            )
            Spacer(Modifier.height(14.dp))
            PillButton("Tôi đã dậy", onConfirm, Modifier.fillMaxWidth(), icon = Icons.Default.Check)
        } else {
            Text("Kiểm tra đã tỉnh lúc ${hhmm(check.askAt)}", style = MaterialTheme.typography.titleSmall, color = p.text)
            Text(
                "Lúc đó app sẽ hỏi \"Bạn còn thức không?\". Đừng nằm xuống ngủ tiếp nhé.",
                style = MaterialTheme.typography.bodySmall,
                color = p.muted,
            )
        }
    }
}
