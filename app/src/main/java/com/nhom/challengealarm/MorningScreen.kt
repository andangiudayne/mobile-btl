package com.nhom.challengealarm

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Bản tin tự đóng sau chừng này nếu người dùng không bấm gì. */
private const val MORNING_AUTO_CLOSE_MS = 3 * 60_000L

/**
 * Bản tin buổi sáng, hiện ngay sau khi tắt chuông (cùng bầu trời bình minh với màn hình reo, mặt trời đã lên hẳn):
 * tỉnh sau bao lâu, chuỗi ngày, đêm qua ngủ bao lâu so với mục tiêu, nợ ngủ, giờ app sẽ hỏi "Bạn còn thức không?",
 * và vài gợi ý ngắn. summary = null là đang tổng hợp.
 */
@Composable
fun MorningScreen(summary: MorningSummary?, onClose: () -> Unit) {
    val close by rememberUpdatedState(onClose)
    BackHandler { close() }
    LaunchedEffect(Unit) {
        delay(MORNING_AUTO_CLOSE_MS)
        close()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(skyTop(1f), skyBottom(1f))))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SunCanvas(Modifier.fillMaxWidth().height(110.dp), rise = 1f)
            Spacer(Modifier.height(14.dp))
            Text(
                if (summary?.nap == true) "Dậy thôi!" else "Chào buổi sáng",
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(hhmm(System.currentTimeMillis()), style = MaterialTheme.typography.titleMedium, color = Dawn.Lilac)
            Spacer(Modifier.height(20.dp))

            if (summary == null) {
                CircularProgressIndicator(color = Dawn.Gold, modifier = Modifier.padding(24.dp))
                Text("Đang tổng hợp đêm qua...", color = Dawn.Lilac, style = MaterialTheme.typography.bodyMedium)
            } else {
                if (!summary.nap) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassTile(
                            value = summary.wakeSeconds?.let { formatShortDuration(it) } ?: "--",
                            label = if (summary.onTime) "để tỉnh · đúng giờ" else "để tỉnh",
                            modifier = Modifier.weight(1f),
                        )
                        GlassTile(
                            value = "${summary.streakDays} ngày",
                            label = "chuỗi dậy đúng giờ",
                            modifier = Modifier.weight(1f),
                            fire = summary.streakDays > 0,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassTile(
                            value = summary.sleptMin?.let { hoursMinutes(it) } ?: "--",
                            label = summary.targetMin?.let { "đêm qua · mục tiêu ${hoursMinutes(it)}" } ?: "đêm qua",
                            modifier = Modifier.weight(1f),
                        )
                        GlassTile(
                            value = summary.debtMin?.let { if (it == 0) "Không" else hoursMinutes(it) } ?: "--",
                            label = "nợ ngủ 7 ngày",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    summary.bedAt?.let {
                        Text(
                            "Ngủ từ ${hhmm(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Dawn.Lilac,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                summary.wakeCheckAt?.let { at ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Dawn.Gold.copy(alpha = 0.18f))
                            .border(1.dp, Dawn.Gold.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Dawn.GoldSoft)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Lúc ${hhmm(at)} app sẽ hỏi \"Bạn còn thức không?\". Bấm \"Tôi đã dậy\" trong " +
                                "${WakeCheckRules.ANSWER_WINDOW_MS / 1000} giây, nếu không chuông sẽ reo lại.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }

                summary.tips.forEach { tip ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier
                                .padding(top = 7.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Dawn.GoldSoft)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(tip, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { close() },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Dawn.Gold, contentColor = Dawn.Midnight),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text("Bắt đầu ngày mới", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** Ô số liệu kính mờ trên nền bình minh. */
@Composable
private fun GlassTile(value: String, label: String, modifier: Modifier = Modifier, fire: Boolean = false) {
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (fire) {
                Icon(
                    Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = Dawn.Gold,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Light, color = Color.White, maxLines = 1)
        }
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Dawn.Lilac)
    }
}
