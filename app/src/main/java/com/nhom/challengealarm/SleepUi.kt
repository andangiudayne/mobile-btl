package com.nhom.challengealarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


/**
 * Tab Giấc ngủ (theo ảnh mẫu ban đêm): thẻ "Tối nay" có nút "Bắt đầu ngủ", hai thẻ Chất lượng / Thời lượng,
 * biểu đồ sóng 7 đêm, các chỉ số phụ, lời khuyên theo tuổi và các đêm gần đây.
 * Mọi con số đã được SleepViewModel tính sẵn; ban ngày các khối chuyển sang kiểu nổi neumorphism.
 */
@Composable
fun SleepScreen(
    state: SleepUiState,
    contentPadding: PaddingValues,
    onEditProfile: () -> Unit,
    onGrantUsage: () -> Unit,
    onStartSleep: () -> Unit,
) {
    val p = AppTheme.palette
    val profile = state.profile
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            ScreenTitle(
                "Giấc ngủ",
                if (profile == null) "Gợi ý theo độ tuổi" else "${profile.name.ifBlank { "Bạn" }}, ${state.age} tuổi · nên ngủ ${state.group.rangeText}",
            ) {
                TextButton(onClick = onEditProfile) { Text(if (profile == null) "Điền hồ sơ" else "Sửa", color = p.accent) }
            }
        }

        if (profile == null) {
            item {
                EmptyState(
                    icon = Icons.Default.Bedtime,
                    title = "Gợi ý giấc ngủ theo tuổi",
                    body = "Người già, người trẻ và trẻ em cần ngủ khác nhau. Cho app biết năm sinh và vài thói quen " +
                        "để tính giờ ngủ hợp với bạn và nhắc bạn đi ngủ đúng giờ.",
                    actionLabel = "Điền hồ sơ",
                    onAction = onEditProfile,
                )
            }
            return@LazyColumn
        }

        if (!state.usageOk) item { UsageBanner(state.supported, onGrantUsage) }

        item { TonightCard(state, onStartSleep) }

        val report = state.report
        item { SectionHeader("Thống kê") }
        item {
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
                    progress = report?.avgAsleepMin?.let { it / state.targetMin.toFloat() },
                    colors = p.statB,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item { SectionHeader("Ngủ trong tuần", "mục tiêu ${SleepAdvisor.durationText(state.targetMin)}") }
        item {
            val days = lastSevenDays(state.now)
            val byNight = remember(state.logs) { state.logs.associateBy { it.nightOf } }
            val chart = @Composable {
                WaveChart(
                    values = days.map { d -> byNight[d]?.asleepMin?.let { it / 60f } },
                    labels = days.map { weekdayShort(it) },
                    minHours = state.group.minHours,
                    maxHours = state.group.maxHours,
                )
            }
            if (p.isNight) {
                Column(Modifier.padding(horizontal = GUTTER)) { chart() }
            } else {
                AppCard(Modifier.padding(horizontal = GUTTER, vertical = 4.dp)) { chart() }
            }
        }

        item {
            Row(
                Modifier.padding(start = GUTTER, end = GUTTER, top = 18.dp).height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val tile = Modifier.weight(1f).fillMaxHeight()
                MetricTile(report?.avgBedClockMin?.let { SleepAdvisor.clockText(it) } ?: "--", "giờ ngủ TB", tile)
                MetricTile(report?.bedSpreadMin?.let { "±$it′" } ?: "--", "độ lệch", tile)
                MetricTile(
                    report?.avgInterruptions?.let { "%.1f".format(it).replace('.', ',') } ?: "--",
                    "lần thức/đêm",
                    tile,
                )
            }
        }

        report?.debtMin?.let { debt ->
            item { DebtCard(debt, state.targetMin) }
        }

        if (report != null) {
            item { SectionHeader("Gợi ý cho bạn") }
            items(report.advice) { AdviceCard(it) }
        }

        if (state.logs.isNotEmpty()) {
            item { SectionHeader("Các đêm gần đây") }
            items(state.logs.take(7), key = { it.id }) { NightCard(it) }
        }

        item {
            Text(
                "Cách tính: từ ${SleepAdvisor.clockText(profile.nightStartMin)}, màn hình tắt liền 15 phút trở lên thì tính là " +
                    "bắt đầu ngủ, đến lúc chuông reo; các lần bật máy giữa đêm bị trừ ra. Chất lượng = ngủ đủ mục tiêu " +
                    "(tối đa 100), trừ 6 điểm mỗi lần thức dậy dùng máy. Khuyến nghị theo National Sleep Foundation và " +
                    "Viện Hàn lâm Y học Giấc ngủ Hoa Kỳ (AASM). Đây là gợi ý tham khảo, không thay cho tư vấn y tế.",
                style = MaterialTheme.typography.bodySmall,
                color = p.muted,
                modifier = Modifier.padding(horizontal = GUTTER, vertical = 18.dp),
            )
        }
    }
}

/** 0h của 7 ngày gần nhất (ngày cuối là hôm nay). */
private fun lastSevenDays(now: Long): List<Long> {
    val today = SleepAnalyzer.dayStart(now)
    return (6 downTo 0).map { back ->
        Calendar.getInstance().apply {
            timeInMillis = today
            add(Calendar.DAY_OF_YEAR, -back)
        }.timeInMillis
    }
}

@Composable
private fun UsageBanner(supported: Boolean, onGrant: () -> Unit) {
    val p = AppTheme.palette
    AppCard(Modifier.padding(horizontal = GUTTER, vertical = 8.dp), padding = 16.dp) {
        if (!supported) {
            Text("Máy cần Android 9 trở lên", style = MaterialTheme.typography.titleSmall, color = p.text)
            Text(
                "Phiên bản Android này chưa cho app đọc giờ bật tắt màn hình, nên chưa đo được giấc ngủ. Gợi ý theo tuổi vẫn dùng được.",
                style = MaterialTheme.typography.bodySmall,
                color = p.muted,
            )
            return@AppCard
        }
        Text("Cho phép đo giấc ngủ", style = MaterialTheme.typography.titleSmall, color = p.text)
        Text(
            "Bật quyền \"Truy cập dữ liệu sử dụng\" cho Challenge Alarm để app biết lúc màn hình tắt, từ đó tính giờ bạn đi ngủ. " +
                "Dữ liệu chỉ nằm trên máy.",
            style = MaterialTheme.typography.bodySmall,
            color = p.muted,
        )
        TextButton(onClick = onGrant) { Text("Cấp quyền", color = p.accent) }
    }
}

/** Thẻ "Tối nay": giờ lên giường đề xuất đến giờ chuông, các mốc phụ và nút "Bắt đầu ngủ". */
@Composable
private fun TonightCard(state: SleepUiState, onStartSleep: () -> Unit) {
    val plan = state.plan
    val wake = state.nextWake
    val now = state.now
    val profile = state.profile ?: return
    val title = when {
        wake == null || wake - now <= 24 * 3_600_000L -> "Tối nay"
        else -> "Đêm trước ${weekdayFull(wake).lowercase()}"
    }
    val subtitle = when {
        plan == null || wake == null -> "Bật một chuông để có giờ ngủ đề xuất"
        now > plan.bedtime -> "Đã quá giờ lên giường. Ngủ ngay thì còn khoảng " +
            "${SleepAdvisor.durationText(((wake - now) / 60_000L).toInt().coerceAtLeast(0))} trước chuông ${hhmm(wake)}."
        else -> "Giờ ngủ: ${hhmm(plan.bedtime)} - ${hhmm(wake)}"
    }
    FeatureCard(
        title = title,
        subtitle = subtitle,
        modifier = Modifier.padding(horizontal = GUTTER, vertical = 6.dp),
        buttonText = "Bắt đầu ngủ",
        onButton = onStartSleep,
        extra = {
            if (plan != null && wake != null) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftPill("Muộn nhất ${hhmm(plan.latestBedtime)}")
                    SoftPill("Tắt màn hình ${hhmm(plan.screensOff)}")
                }
                if (profile.lateCaffeine) {
                    Spacer(Modifier.height(8.dp))
                    SoftPill("Ngừng cà phê trước ${hhmm(plan.caffeineCutoff)}")
                }
                if (profile.remind && now <= plan.bedtime) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Sẽ nhắc bạn lúc ${hhmm(BedtimeReminder.remindAt(plan))}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.palette.muted,
                    )
                }
            }
        },
    )
}

@Composable
private fun AdviceCard(a: Advice) {
    val p = AppTheme.palette
    AppCard(Modifier.padding(horizontal = GUTTER, vertical = 7.dp), padding = 16.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                if (a.warn) Icons.Default.WarningAmber else Icons.Default.Info,
                contentDescription = null,
                tint = if (a.warn) p.accent2 else p.muted,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(a.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = p.text)
                Text(a.body, style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
        }
    }
}

@Composable
private fun NightCard(log: SleepLog) {
    val p = AppTheme.palette
    val dateFmt = remember { SimpleDateFormat("dd/MM", Locale.getDefault()) }
    AppCard(Modifier.padding(horizontal = GUTTER, vertical = 7.dp), padding = 16.dp) {
        Text(
            "${weekdayShort(log.wakeAt)} ${dateFmt.format(Date(log.wakeAt))}: ${hhmm(log.sleepStart)} → ${hhmm(log.wakeAt)}",
            style = MaterialTheme.typography.titleSmall,
            color = p.text,
        )
        Text(
            "Ngủ ${SleepAdvisor.durationText(log.asleepMin)}" +
                if (log.interruptions > 0) ", thức dậy dùng máy ${log.interruptions} lần" else "",
            style = MaterialTheme.typography.bodySmall,
            color = p.muted,
        )
    }
}

/**
 * Nợ ngủ 7 ngày: cộng phần ngủ thiếu so với mục tiêu của từng đêm. Thanh đầy khi nợ bằng một đêm ngủ trọn
 * (đến mức đó là thiếu tương đương mất trắng một đêm).
 */
@Composable
private fun DebtCard(debtMin: Int, targetMin: Int) {
    val p = AppTheme.palette
    val fraction = (debtMin / targetMin.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    AppCard(Modifier.padding(start = GUTTER, end = GUTTER, top = 16.dp, bottom = 4.dp), padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Nợ ngủ 7 ngày", style = MaterialTheme.typography.titleSmall, color = p.text)
                Text(
                    when {
                        debtMin == 0 -> "Không nợ. Bạn ngủ đủ mục tiêu mọi đêm đã đo."
                        debtMin < SleepAdvisor.DEBT_WARN_MIN -> "Nợ ít, ngủ sớm hơn một chút là bù được."
                        else -> "Trả dần: lên giường sớm 30 đến 60 phút trong vài tối."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = p.muted,
                )
            }
            Text(
                if (debtMin == 0) "0" else hoursMinutes(debtMin),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
                color = if (debtMin >= SleepAdvisor.DEBT_WARN_MIN) p.accent2 else p.text,
            )
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = if (debtMin >= SleepAdvisor.DEBT_WARN_MIN) p.accent2 else p.accent,
            trackColor = p.track,
        )
    }
}
