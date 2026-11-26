package com.nhom.challengealarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Tab Thống kê: chuỗi ngày dậy đúng giờ, hai thẻ Đúng giờ / Để tỉnh có thanh tiến độ, biểu đồ sóng 7 ngày,
 * những lần dậy gần đây. Chỉ đọc state từ StatsViewModel.
 */
@Composable
fun StatsScreen(
    state: StatsUiState,
    contentPadding: PaddingValues,
) {
    val p = AppTheme.palette
    val stats = state.stats
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item { ScreenTitle("Thống kê", "Dậy đúng giờ = qua màn đầu trong ${ON_TIME_LIMIT_MS / 60_000L} phút") }

        if (stats == null) return@LazyColumn // đang tải: chưa vẽ gì, tránh nháy "Chưa có dữ liệu"
        if (stats.total == 0) {
            item {
                EmptyState(
                    icon = Icons.Default.InsertChart,
                    title = "Chưa có dữ liệu",
                    body = "Sau lần dậy đầu tiên, chuỗi ngày, tỉ lệ đúng giờ và biểu đồ sẽ hiện ở đây.",
                )
            }
            return@LazyColumn
        }

        item {
            FeatureCard(
                title = "${stats.streakDays} ngày liên tiếp",
                subtitle = "dậy đúng giờ. Ngày không có chuông (tối đa $MAX_GAP_DAYS ngày liền, như cuối tuần) không làm đứt chuỗi.",
                modifier = Modifier.padding(horizontal = GUTTER, vertical = 6.dp),
                extra = {
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        repeat(stats.streakDays.coerceIn(0, 7)) {
                            Icon(
                                Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = p.accent2,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                },
            )
        }

        item { SectionHeader("Tổng quan 30 ngày") }
        item {
            Row(Modifier.padding(horizontal = GUTTER), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard(
                    title = "Đúng giờ",
                    value = stats.onTimeRate?.let { "${(it * 100).roundToInt()} %" } ?: "--",
                    progress = stats.onTimeRate,
                    colors = p.statA,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    title = "Để tỉnh",
                    value = stats.avgSeconds?.let { formatShortDuration(it) } ?: "--",
                    // Càng tỉnh nhanh thanh càng đầy (5 phút trở lên là rỗng).
                    progress = stats.avgSeconds?.let { 1f - (it * 1000f / ON_TIME_LIMIT_MS).coerceIn(0f, 1f) },
                    colors = p.statB,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item { SectionHeader("Thời gian để tỉnh, 7 ngày") }
        item {
            val chart = @Composable {
                WaveChart(
                    values = stats.last7.map { d -> d.avgSeconds?.let { it / 60f } },
                    labels = stats.last7.map { weekdayShort(it.dayStart) },
                    minHours = 1f,
                    maxHours = (ON_TIME_LIMIT_MS / 60_000L).toFloat(),
                    unit = "p",
                )
            }
            if (p.isNight) {
                Column(Modifier.padding(horizontal = GUTTER)) { chart() }
            } else {
                AppCard(Modifier.padding(horizontal = GUTTER, vertical = 4.dp)) { chart() }
            }
        }

        state.insight?.let { insight ->
            item { SectionHeader("Giấc ngủ và giờ dậy") }
            item { InsightCard(insight, state.targetMin) }
        }
        if (stats.relapses > 0) {
            item { RelapseCard(stats.relapses) }
        }

        item { SectionHeader("Những lần dậy gần đây") }
        items(state.recent, key = { it.id }) { log -> WakeLogCard(log) }
    }
}

@Composable
private fun WakeLogCard(log: WakeLog) {
    val p = AppTheme.palette
    val fmt = remember { SimpleDateFormat("EEE dd/MM", Locale.getDefault()) }
    AppCard(Modifier.padding(horizontal = GUTTER, vertical = 7.dp), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    listOf(fmt.format(Date(log.firedAt)), log.label.trim()).filter { it.isNotEmpty() }.joinToString(" · ").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = p.muted,
                )
                Text(
                    "Tỉnh sau ${formatDuration((log.awakeAt - log.firedAt).coerceAtLeast(0) / 1000L)}" +
                        (if (log.rounds > 1) ", qua ${log.rounds} màn" else "") +
                        ", tắt bằng ${methodName(log.method)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.text.copy(alpha = 0.8f),
                )
                if (log.relapsed) {
                    Spacer(Modifier.height(6.dp))
                    Pill("Ngủ lại sau khi tắt chuông", p.accent2.copy(alpha = 0.22f), p.text)
                }
            }
            Text(
                hhmm(log.firedAt),
                style = if (p.isNight) MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light) else DigitalStyle.copy(fontSize = 20.sp),
                color = p.text,
            )
        }
    }
}

/**
 * Ghép nhật ký giấc ngủ với nhật ký thức dậy: đêm ngủ đủ thì bạn tỉnh nhanh hơn bao nhiêu.
 * Con số lấy từ chính dữ liệu của người dùng nên thuyết phục hơn lời khuyên chung chung.
 */
@Composable
private fun InsightCard(insight: SleepWakeInsight, targetMin: Int) {
    val p = AppTheme.palette
    AppCard(Modifier.padding(horizontal = GUTTER, vertical = 6.dp), padding = 18.dp) {
        Text(
            if (insight.fasterPct > 0) "Ngủ đủ, bạn dậy nhanh hơn ${insight.fasterPct}%"
            else "Ngủ đủ hay thiếu, bạn dậy nhanh như nhau",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = p.text,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(formatShortDuration(insight.enoughSec), fontSize = 22.sp, fontWeight = FontWeight.Light, color = p.text)
                Text(
                    "đêm ngủ đủ (${insight.enoughNights} đêm)",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.muted,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(formatShortDuration(insight.shortSec), fontSize = 22.sp, fontWeight = FontWeight.Light, color = p.text)
                Text(
                    "đêm ngủ thiếu (${insight.shortNights} đêm)",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.muted,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Thời gian từ lúc chuông reo đến lúc qua màn đầu. \"Đủ\" là ngủ từ " +
                "${hoursMinutes(targetMin - Insights.ENOUGH_TOLERANCE_MIN)} trở lên.",
            style = MaterialTheme.typography.bodySmall,
            color = p.muted,
        )
    }
}

@Composable
private fun RelapseCard(count: Int) {
    val p = AppTheme.palette
    AppCard(Modifier.padding(horizontal = GUTTER, vertical = 6.dp), padding = 16.dp) {
        Text("Ngủ lại sau khi tắt chuông: $count lần", style = MaterialTheme.typography.titleSmall, color = p.text)
        Text(
            "Những lần không trả lời \"Bạn còn thức không?\" nên chuông phải reo lại. Các lần này không tính là dậy đúng giờ. " +
                "Thử chọn thử thách vận động (đi bộ, quét mã ở xa giường) để ra khỏi giường ngay.",
            style = MaterialTheme.typography.bodySmall,
            color = p.muted,
        )
    }
}
