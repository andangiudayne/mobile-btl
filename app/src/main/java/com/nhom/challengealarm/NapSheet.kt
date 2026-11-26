package com.nhom.challengealarm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Bảng "Chợp mắt": chọn 10, 20 hoặc 30 phút, xem trước giờ chuông reo và lời khuyên ngắn.
 * Bấm "Bắt đầu" là tạo chuông chợp mắt (reo xong tự xóa).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NapSheet(onStart: (Int) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val p = AppTheme.palette
    var minutes by remember { mutableIntStateOf(NapPlanner.RECOMMENDED) }
    val now = remember { System.currentTimeMillis() }
    val late = remember { NapPlanner.isLate(now) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.background,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = GUTTER).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Chợp mắt", style = MaterialTheme.typography.titleLarge)
            Text(
                "Ngủ ngắn 10 đến 20 phút giúp tỉnh táo cả buổi chiều. Ngủ quá 30 phút dễ vào giấc sâu, dậy uể oải " +
                    "và tối khó ngủ hơn. App cộng thêm ${NapPlanner.FALL_ASLEEP_MIN} phút để bạn chìm vào giấc.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NapPlanner.OPTIONS.forEach { option ->
                    val on = option == minutes
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (on) cs.primaryContainer else cs.surface)
                            .border(
                                BorderStroke(if (on) 1.5.dp else 0.dp, if (on) p.accent else Color.Transparent),
                                RoundedCornerShape(20.dp),
                            )
                            .clickable { minutes = option }
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("$option", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Light)
                        Text(
                            if (option == NapPlanner.RECOMMENDED) "phút · nên chọn" else "phút",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                "Chuông reo lúc ${hhmm(NapPlanner.ringAt(minutes, now))}. ${NapPlanner.advice(minutes)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (late) {
                Text(
                    "Đã sau ${NapPlanner.LATE_HOUR} giờ: ngủ trưa muộn dễ làm tối nay khó ngủ. Nếu được, chọn 10 phút thôi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.error,
                )
            }
            Text(
                "Khi reo: giải 2 bài toán dễ. ${NapPlanner.WAKE_CHECK_MIN} phút sau app hỏi \"Bạn còn thức không?\" " +
                    "để bạn không ngủ quên tiếp.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )

            Button(
                onClick = { onStart(minutes) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Bắt đầu chợp mắt $minutes phút", style = MaterialTheme.typography.labelLarge) }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Để sau") }
        }
    }
}
