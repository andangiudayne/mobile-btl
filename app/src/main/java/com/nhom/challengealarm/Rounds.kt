package com.nhom.challengealarm

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Tiến độ của lần reo hiện tại. Lưu trong SharedPreferences để nếu app bị vuốt khỏi đa nhiệm
 * và chuông dự phòng dựng lại, người dùng làm tiếp từ màn đang dở chứ không phải làm lại từ đầu.
 * Phiên vẫn giữ trong lúc hoãn chuông (firedAt là lần reo đầu tiên), chỉ xóa khi tắt chuông hẳn.
 * Trong lúc còn phiên, chuông đó bị khóa không cho tắt, xóa hay sửa ở màn hình chính.
 * recheck = lần reo lại vì không trả lời "Bạn còn thức không?" (chỉ 1 màn, không hoãn, không ghi nhật ký mới).
 */
data class RingSessionState(
    val alarmId: Int,
    val firedAt: Long,
    val roundsDone: Int,
    val wokeAt: Long,
    val breakUntil: Long,
    val snoozeCount: Int = 0,
    val recheck: Boolean = false,
)

object RingSession {
    private const val PREFS = "ring_session"
    /** Phiên quá cũ (chuông đã reo từ lâu, app bị giết giữa chừng) thì bắt đầu phiên mới. */
    private const val MAX_AGE_MS = 90 * 60_000L

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Mỗi chuông một phiên riêng (khóa "<id>.<trường>"), để chuông thử hay một chuông khác reo
    // không ghi đè phiên của chuông đang hoãn.
    private fun k(alarmId: Int, field: String) = "$alarmId.$field"

    fun load(context: Context, alarmId: Int): RingSessionState? {
        val p = prefs(context)
        if (!p.contains(k(alarmId, "firedAt"))) return null
        return RingSessionState(
            alarmId = alarmId,
            firedAt = p.getLong(k(alarmId, "firedAt"), 0L),
            roundsDone = p.getInt(k(alarmId, "roundsDone"), 0),
            wokeAt = p.getLong(k(alarmId, "wokeAt"), 0L),
            breakUntil = p.getLong(k(alarmId, "breakUntil"), 0L),
            snoozeCount = p.getInt(k(alarmId, "snoozeCount"), 0),
            recheck = p.getBoolean(k(alarmId, "recheck"), false),
        )
    }

    fun save(context: Context, s: RingSessionState) {
        prefs(context).edit()
            .putLong(k(s.alarmId, "firedAt"), s.firedAt)
            .putInt(k(s.alarmId, "roundsDone"), s.roundsDone)
            .putLong(k(s.alarmId, "wokeAt"), s.wokeAt)
            .putLong(k(s.alarmId, "breakUntil"), s.breakUntil)
            .putInt(k(s.alarmId, "snoozeCount"), s.snoozeCount)
            .putBoolean(k(s.alarmId, "recheck"), s.recheck)
            .commit() // ghi ngay: tiến trình có thể bị giết bất cứ lúc nào
    }

    /**
     * Còn hiệu lực: chưa quá MAX_AGE_MS kể từ lần reo đầu. Giờ máy bị chỉnh lùi (tuổi âm) vẫn tính là còn,
     * để không thể chỉnh đồng hồ lùi lại mà thoát chuông đang hoãn.
     */
    private fun fresh(s: RingSessionState, now: Long) = now - s.firedAt < MAX_AGE_MS

    /** Dùng lại phiên đang dở của chuông này, hoặc mở phiên mới. */
    fun begin(context: Context, alarmId: Int, now: Long = System.currentTimeMillis()): RingSessionState {
        val current = load(context, alarmId)
        if (current != null && fresh(current, now)) return current
        return RingSessionState(alarmId, now, 0, 0L, 0L).also { save(context, it) }
    }

    /** Các phiên của chuông thật đang reo hoặc đang hoãn (chưa tắt hẳn). Phiên hết hạn thì dọn luôn. */
    fun active(context: Context, now: Long = System.currentTimeMillis()): List<RingSessionState> {
        val p = prefs(context)
        // Dọn khóa kiểu cũ (bản trước chỉ lưu một phiên, không có tiền tố id).
        val legacy = p.all.keys.filter { '.' !in it }
        if (legacy.isNotEmpty()) p.edit().apply { legacy.forEach { remove(it) } }.commit()
        val ids = p.all.keys.mapNotNull { it.substringBefore('.').toIntOrNull() }.toSet()
        val sessions = ids.mapNotNull { load(context, it) }
        sessions.filter { !fresh(it, now) }.forEach { clear(context, it.alarmId) }
        return sessions.filter { it.alarmId >= 0 && fresh(it, now) }
    }

    fun isActive(context: Context, alarmId: Int): Boolean = active(context).any { it.alarmId == alarmId }

    fun clear(context: Context, alarmId: Int) {
        val e = prefs(context).edit()
        listOf("firedAt", "roundsDone", "wokeAt", "breakUntil", "snoozeCount", "recheck").forEach { e.remove(k(alarmId, it)) }
        e.commit()
    }
}

private fun mmss(ms: Long): String {
    val total = ((ms + 999) / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/** "Màn 2/5" và một hàng chấm: chấm vàng là màn đã qua, chấm sáng viền là màn đang làm. */
@Composable
fun RoundHeader(roundsDone: Int, total: Int) {
    if (total <= 1) return
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Màn ${(roundsDone + 1).coerceAtMost(total)}/$total",
            style = MaterialTheme.typography.labelLarge,
            color = Dawn.Lilac,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(total) { i ->
                val done = i < roundsDone
                val current = i == roundsDone
                Box(
                    Modifier
                        .size(if (current) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                done -> Dawn.Gold
                                current -> Color.White.copy(alpha = 0.85f)
                                else -> Color.White.copy(alpha = 0.18f)
                            }
                        )
                )
            }
        }
    }
}

/**
 * Màn nghỉ giữa hai màn: chuông im, đếm ngược đến lúc reo lại.
 * Hết giờ thì tự gọi onEnd; bấm "Làm tiếp ngay" cũng gọi onEnd.
 */
@Composable
fun BreakPanel(until: Long, nextRound: Int, total: Int, onEnd: () -> Unit) {
    val currentEnd by rememberUpdatedState(onEnd)
    var left by remember(until) { mutableLongStateOf((until - System.currentTimeMillis()).coerceAtLeast(0)) }
    LaunchedEffect(until) {
        while (true) {
            left = (until - System.currentTimeMillis()).coerceAtLeast(0)
            if (left <= 0) break
            delay(250)
        }
        currentEnd()
    }

    Column(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Nằm thêm chút nhé", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(
            "Chuông im, sẽ reo lại cho màn $nextRound/$total sau",
            style = MaterialTheme.typography.bodyMedium,
            color = Dawn.Lilac,
            textAlign = TextAlign.Center,
        )
        Text(
            mmss(left),
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 64.sp,
                lineHeight = 68.sp,
                fontWeight = FontWeight.Light,
            ),
            color = Dawn.GoldSoft,
        )
        TextButton(onClick = onEnd) { Text("Làm tiếp ngay", color = Color.White) }
    }
}

/**
 * Nút "Bạn đã tỉnh": phải giữ liền AWAKE_HOLD_MS mới tắt chuông, viền vàng đầy dần khi giữ.
 * Chỉ hiện từ màn 2, nghĩa là người dùng đã tự làm xong ít nhất một thử thách.
 */
@Composable
fun AwakeHoldButton(onConfirm: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val confirm by rememberUpdatedState(onConfirm)
    var heldMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(pressed) {
        if (!pressed) {
            heldMs = 0L
            return@LaunchedEffect
        }
        val step = 50L
        while (heldMs < AWAKE_HOLD_MS) {
            delay(step)
            heldMs += step
        }
        confirm()
    }

    val fraction = (heldMs.toFloat() / AWAKE_HOLD_MS).coerceIn(0f, 1f)
    val secondsLeft = ((AWAKE_HOLD_MS - heldMs + 999) / 1000).coerceAtLeast(1)

    Box(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .drawBehind {
                drawRect(Dawn.Gold.copy(alpha = 0.55f), size = Size(size.width * fraction, size.height))
            }
            .border(1.5.dp, Dawn.Gold, CircleShape)
            .clickable(interactionSource = source, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Bạn đã tỉnh",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                if (pressed) "Giữ tiếp, còn $secondsLeft giây" else "Giữ ${AWAKE_HOLD_MS / 1000} giây để tắt chuông",
                style = MaterialTheme.typography.bodySmall,
                color = Dawn.GoldSoft,
            )
        }
    }
}
