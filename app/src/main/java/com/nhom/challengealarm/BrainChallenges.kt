package com.nhom.challengealarm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

// =================================================================================================
// Sáu thử thách mới (giao diện). Logic nằm ở BrainLogic.kt.
//   Tìm ô khác màu  - tập trung thị giác       Nhớ dãy số   - trí nhớ ngắn hạn (bàn phím số riêng)
//   Xếp hình trượt  - lập kế hoạch nhiều bước   Giữ thăng bằng - phải cầm máy chắc tay, ngồi dậy
//   So sánh nhanh   - tính nhẩm có áp lực       Đọc đồng hồ  - hợp cả người lớn tuổi
// Không thử thách nào mở bàn phím hệ thống, nên không che nút "Đổi sang giải toán" và "Thoát khẩn cấp".
// =================================================================================================

/** Dòng điểm "3/8" dùng chung. */
@Composable
private fun ScoreText(score: Int, target: Int, suffix: String = "") {
    Text(
        "${score.coerceAtMost(target)}/$target$suffix",
        style = MaterialTheme.typography.titleMedium,
        color = Dawn.GoldSoft,
        modifier = Modifier.padding(vertical = 10.dp),
    )
}

@Composable
private fun FeedbackText(text: String) {
    Text(
        text.ifEmpty { " " },
        style = MaterialTheme.typography.bodyMedium,
        color = if (text.startsWith("Sai")) Wrong else Dawn.GoldSoft,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Gọi onDone sau một nhịp khi finished chuyển sang true (để kịp thấy mặt trời nhô lên). */
@Composable
private fun FinishAfterDelay(finished: Boolean, onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(finished) {
        if (finished) {
            delay(800)
            done()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Tìm ô khác màu
// ---------------------------------------------------------------------------------------------

@Composable
fun OddColorChallengeSection(difficulty: Int, onProgress: (Float) -> Unit, onDone: () -> Unit) {
    val target = OddColor.target(difficulty)
    var score by remember { mutableIntStateOf(0) }
    var round by remember { mutableStateOf(OddColor.next(difficulty)) }
    var feedback by remember { mutableStateOf("") }
    var finished by remember { mutableStateOf(false) }
    val progress by rememberUpdatedState(onProgress)

    LaunchedEffect(score) {
        progress((score / target.toFloat()).coerceAtMost(1f))
        if (score >= target) finished = true
    }
    FinishAfterDelay(finished, onDone)

    fun tap(i: Int) {
        if (finished) return
        if (i == round.odd) {
            score++
            feedback = "Đúng"
        } else {
            score = (score - 1).coerceAtLeast(0)
            feedback = "Sai rồi, trừ 1 điểm"
        }
        round = OddColor.next(difficulty, previous = round)
    }

    ChallengeHeader("Tìm ô khác màu", "Chạm vào ô có màu hơi khác các ô còn lại.")
    ScoreText(score, target)
    val base = Color.hsv(round.hue, round.saturation, round.value)
    val odd = Color.hsv(round.hue, round.saturation, round.oddValue)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (r in 0 until round.size) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in 0 until round.size) {
                    val i = r * round.size + c
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (i == round.odd) odd else base)
                            .clickable { tap(i) }
                    )
                }
            }
        }
    }
    FeedbackText(feedback)
}

// ---------------------------------------------------------------------------------------------
// Nhớ dãy số
// ---------------------------------------------------------------------------------------------

@Composable
fun DigitSpanChallengeSection(difficulty: Int, onProgress: (Float) -> Unit, onDone: () -> Unit) {
    val length = DigitSpan.length(difficulty)
    val rounds = DigitSpan.rounds(difficulty)
    var round by remember { mutableIntStateOf(0) }
    var attempt by remember { mutableIntStateOf(0) }
    val sequence = remember(round, attempt) { DigitSpan.generate(length) }
    var showing by remember { mutableStateOf(true) }
    var input by remember { mutableStateOf(listOf<Int>()) }
    var feedback by remember { mutableStateOf("") }
    var finished by remember { mutableStateOf(false) }
    val progress by rememberUpdatedState(onProgress)

    LaunchedEffect(round, attempt) {
        showing = true
        input = emptyList()
        delay(DigitSpan.showMs(length))
        showing = false
    }
    LaunchedEffect(round) { progress(round / rounds.toFloat()) }
    FinishAfterDelay(finished, onDone)

    fun press(d: Int) {
        if (showing || finished || input.size >= length) return
        val next = input + d
        input = next
        if (next.size == length) {
            if (next == sequence) {
                feedback = "Đúng"
                if (round + 1 >= rounds) {
                    progress(1f)
                    finished = true
                } else {
                    round++
                }
            } else {
                feedback = "Sai rồi, xem dãy mới nhé"
                attempt++
            }
        }
    }

    ChallengeHeader(
        "Nhớ dãy số",
        if (showing) "Nhớ dãy số này." else "Bấm lại đúng dãy số vừa thấy.",
    )
    ScoreText(if (finished) rounds else round, rounds, " dãy")
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        for (i in 0 until length) {
            val digit = if (showing) sequence[i] else input.getOrNull(i)
            Box(
                Modifier
                    .size(width = 34.dp, height = 46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = if (digit != null) 0.16f else 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(digit?.toString() ?: "", fontSize = 26.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
    FeedbackText(feedback)
    Spacer(Modifier.height(8.dp))
    DigitPad(enabled = !showing && !finished, onDigit = ::press, onBackspace = {
        if (!showing && input.isNotEmpty()) input = input.dropLast(1)
    })
}

@Composable
private fun DigitPad(enabled: Boolean, onDigit: (Int) -> Unit, onBackspace: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { d -> PadKey(enabled, { onDigit(d) }) { Text("$d", fontSize = 22.sp, color = Color.White) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            PadKey(enabled, { onDigit(0) }) { Text("0", fontSize = 22.sp, color = Color.White) }
            PadKey(enabled, onBackspace) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Xóa", tint = Color.White)
            }
        }
    }
}

@Composable
private fun RowScope.PadKey(enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.1f else 0.04f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ---------------------------------------------------------------------------------------------
// Xếp hình trượt
// ---------------------------------------------------------------------------------------------

@Composable
fun SlidePuzzleChallengeSection(difficulty: Int, onProgress: (Float) -> Unit, onDone: () -> Unit) {
    var tiles by remember { mutableStateOf(SlidePuzzle.shuffled(SlidePuzzle.moves(difficulty))) }
    var moves by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    val progress by rememberUpdatedState(onProgress)

    LaunchedEffect(tiles) {
        progress(SlidePuzzle.correctCount(tiles) / 8f)
        if (SlidePuzzle.isSolved(tiles)) finished = true
    }
    FinishAfterDelay(finished, onDone)

    ChallengeHeader("Xếp hình trượt", "Chạm ô nằm cạnh ô trống để trượt. Xếp về 1 2 3 / 4 5 6 / 7 8.")
    Text(
        "Đã đi $moves nước · ${SlidePuzzle.correctCount(tiles)}/8 ô đúng chỗ",
        style = MaterialTheme.typography.bodyMedium,
        color = Dawn.GoldSoft,
        modifier = Modifier.padding(vertical = 10.dp),
    )
    Column(
        Modifier.fillMaxWidth(0.86f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (r in 0 until 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in 0 until 3) {
                    val i = r * 3 + c
                    val value = tiles[i]
                    val movable = !finished && value != 0 && SlidePuzzle.canMove(tiles, i)
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                when {
                                    value == 0 -> Color.Transparent
                                    value == i + 1 -> Dawn.Gold
                                    else -> Color.White.copy(alpha = if (movable) 0.22f else 0.12f)
                                }
                            )
                            .clickable(enabled = movable) {
                                tiles = SlidePuzzle.move(tiles, i)
                                moves++
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (value != 0) {
                            Text(
                                "$value",
                                fontSize = 30.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (value == i + 1) Dawn.Midnight else Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Giữ thăng bằng (gia tốc kế)
// ---------------------------------------------------------------------------------------------

@Composable
fun TiltChallengeSection(difficulty: Int, onProgress: (Float) -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val targets = TiltMath.targets(difficulty)
    val radius = TiltMath.targetRadius(difficulty)
    var ballX by remember { mutableFloatStateOf(0f) }
    var ballY by remember { mutableFloatStateOf(0f) }
    var reached by remember { mutableIntStateOf(0) }
    var target by remember { mutableStateOf(TiltMath.nextTarget()) }
    var heldMs by remember { mutableLongStateOf(0L) }
    var finished by remember { mutableStateOf(false) }
    val progress by rememberUpdatedState(onProgress)

    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y) = TiltMath.ball(event.values[0], event.values[1])
                ballX = TiltMath.smooth(ballX, x)
                ballY = TiltMath.smooth(ballY, y)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }

    // Bi nằm trong vòng đích liên tục HOLD_MS thì qua đích; ra khỏi vòng thì đếm lại.
    LaunchedEffect(reached) {
        heldMs = 0L
        while (!finished) {
            delay(100)
            heldMs = if (TiltMath.inside(ballX, ballY, target.first, target.second, radius)) heldMs + 100 else 0L
            progress(((reached + heldMs / TiltMath.HOLD_MS.toFloat()) / targets).coerceAtMost(1f))
            if (heldMs >= TiltMath.HOLD_MS) {
                if (reached + 1 >= targets) {
                    finished = true
                } else {
                    target = TiltMath.nextTarget(previous = target)
                    reached++
                }
                break
            }
        }
    }
    FinishAfterDelay(finished, onDone)

    ChallengeHeader("Giữ thăng bằng", "Ngồi dậy, nghiêng máy để đưa viên bi vào vòng vàng rồi giữ yên 2 giây.")
    ScoreText(if (finished) targets else reached, targets, " vòng")
    Canvas(Modifier.fillMaxWidth(0.82f).aspectRatio(1f)) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        fun p(x: Float, y: Float) = Offset(c.x + x * r * 0.86f, c.y + y * r * 0.86f)
        drawCircle(Color.White.copy(alpha = 0.07f), r, c)
        drawCircle(Color.White.copy(alpha = 0.2f), r, c, style = Stroke(1.5.dp.toPx()))
        val t = p(target.first, target.second)
        val tr = radius * r * 0.86f
        drawCircle(Dawn.Gold.copy(alpha = 0.18f + 0.5f * (heldMs / TiltMath.HOLD_MS.toFloat()).coerceIn(0f, 1f)), tr, t)
        drawCircle(Dawn.Gold, tr, t, style = Stroke(2.5.dp.toPx()))
        drawCircle(Color.White, 13.dp.toPx(), p(ballX, ballY))
    }
    Text(
        if (heldMs > 0) "Giữ yên... ${((TiltMath.HOLD_MS - heldMs).coerceAtLeast(0) + 999) / 1000}s" else " ",
        style = MaterialTheme.typography.bodyMedium,
        color = Dawn.GoldSoft,
        modifier = Modifier.padding(top = 8.dp),
    )
}

// ---------------------------------------------------------------------------------------------
// So sánh nhanh
// ---------------------------------------------------------------------------------------------

@Composable
fun CompareChallengeSection(difficulty: Int, onProgress: (Float) -> Unit, onDone: () -> Unit) {
    val target = CompareQuiz.target(difficulty)
    var score by remember { mutableIntStateOf(0) }
    var item by remember { mutableStateOf(CompareQuiz.next(difficulty)) }
    var feedback by remember { mutableStateOf("") }
    var finished by remember { mutableStateOf(false) }
    val progress by rememberUpdatedState(onProgress)

    LaunchedEffect(score) {
        progress((score / target.toFloat()).coerceAtMost(1f))
        if (score >= target) finished = true
    }
    FinishAfterDelay(finished, onDone)

    fun choose(left: Boolean) {
        if (finished) return
        if (left == item.leftBigger) {
            score++
            feedback = "Đúng: ${item.leftValue} và ${item.rightValue}"
        } else {
            score = (score - 1).coerceAtLeast(0)
            feedback = "Sai rồi: ${item.leftValue} và ${item.rightValue}"
        }
        item = CompareQuiz.next(difficulty)
    }

    ChallengeHeader("So sánh nhanh", "Bên nào có kết quả lớn hơn? Chọn sai bị trừ 1 điểm.")
    ScoreText(score, target)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(true to item.left, false to item.right).forEach { (isLeft, text) ->
            Box(
                Modifier
                    .weight(1f)
                    .height(120.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable { choose(isLeft) },
                contentAlignment = Alignment.Center,
            ) {
                Text(text, fontSize = 26.sp, color = Color.White, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            }
        }
    }
    FeedbackText(feedback)
}

// ---------------------------------------------------------------------------------------------
// Đọc đồng hồ
// ---------------------------------------------------------------------------------------------

@Composable
fun ClockChallengeSection(difficulty: Int, onProgress: (Float) -> Unit, onDone: () -> Unit) {
    val target = ClockQuiz.target(difficulty)
    var score by remember { mutableIntStateOf(0) }
    var item by remember { mutableStateOf(ClockQuiz.next(difficulty)) }
    var feedback by remember { mutableStateOf("") }
    var finished by remember { mutableStateOf(false) }
    val progress by rememberUpdatedState(onProgress)

    LaunchedEffect(score) {
        progress((score / target.toFloat()).coerceAtMost(1f))
        if (score >= target) finished = true
    }
    FinishAfterDelay(finished, onDone)

    fun choose(option: String) {
        if (finished) return
        if (option == item.answer) {
            score++
            feedback = "Đúng"
        } else {
            score = (score - 1).coerceAtLeast(0)
            feedback = "Sai rồi, đó là ${item.answer}"
        }
        item = ClockQuiz.next(difficulty)
    }

    ChallengeHeader("Đọc đồng hồ", "Kim ngắn chỉ giờ, kim dài chỉ phút. Đồng hồ chỉ mấy giờ?")
    ScoreText(score, target)
    Canvas(Modifier.size(200.dp)) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(Color.White.copy(alpha = 0.08f), r, c)
        drawCircle(Color.White.copy(alpha = 0.35f), r, c, style = Stroke(2.dp.toPx()))
        for (i in 0 until 60) {
            val long = i % 5 == 0
            val a = Math.toRadians(i * 6.0 - 90.0)
            val outer = Offset(c.x + r * 0.92f * cos(a).toFloat(), c.y + r * 0.92f * sin(a).toFloat())
            val inner = (if (long) 0.78f else 0.86f) * r
            drawLine(
                Color.White.copy(alpha = if (long) 0.9f else 0.35f),
                Offset(c.x + inner * cos(a).toFloat(), c.y + inner * sin(a).toFloat()),
                outer,
                strokeWidth = (if (long) 3.dp else 1.dp).toPx(),
                cap = StrokeCap.Round,
            )
        }
        val hourAngle = Math.toRadians(((item.hour % 12) + item.minute / 60.0) * 30.0 - 90.0)
        val minuteAngle = Math.toRadians(item.minute * 6.0 - 90.0)
        drawLine(
            Color.White,
            c,
            Offset(c.x + r * 0.48f * cos(hourAngle).toFloat(), c.y + r * 0.48f * sin(hourAngle).toFloat()),
            strokeWidth = 7.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            Dawn.Gold,
            c,
            Offset(c.x + r * 0.74f * cos(minuteAngle).toFloat(), c.y + r * 0.74f * sin(minuteAngle).toFloat()),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(Dawn.GoldSoft, 6.dp.toPx(), c)
    }
    Spacer(Modifier.height(14.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item.options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { option ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable { choose(option) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(option, fontSize = 22.sp, color = Color.White, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
    FeedbackText(feedback)
}
