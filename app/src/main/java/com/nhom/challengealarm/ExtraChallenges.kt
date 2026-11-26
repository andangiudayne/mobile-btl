package com.nhom.challengealarm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------------------------
// Bốn thử thách thêm ở đợt MVVM: Màu chữ (Stroop), Lật cặp, Bật đèn, Vuốt mũi tên.
// Mỗi thử thách luyện một kỹ năng khác: kiềm chế phản xạ đọc, trí nhớ ngắn hạn, ánh sáng thật, phản xạ hướng.
// ---------------------------------------------------------------------------------------------

/** Màu dùng cho thử thách Màu chữ. Chọn 4 màu dễ phân biệt trên nền tối. */
internal data class InkColor(val name: String, val color: Color)

internal val INKS = listOf(
    InkColor("ĐỎ", Color(0xFFFF6B6B)),
    InkColor("VÀNG", Color(0xFFFFD166)),
    InkColor("XANH", Color(0xFF5AA9FF)),
    InkColor("TÍM", Color(0xFFC08BFF)),
)

/** Một câu hỏi Stroop: chữ ghi màu word, được tô màu ink (luôn khác nhau). askInk = hỏi màu mực hay nghĩa của chữ. */
internal data class StroopItem(val word: Int, val ink: Int, val askInk: Boolean) {
    val answer: Int get() = if (askInk) ink else word
}

internal object StroopGenerator {
    /** Câu mới, luôn khác câu trước (để người dùng thấy màn hình đã đổi sau mỗi lần chọn). */
    fun next(mixedRules: Boolean, random: Random = Random.Default, previous: StroopItem? = null): StroopItem {
        while (true) {
            val word = random.nextInt(INKS.size)
            var ink = random.nextInt(INKS.size)
            while (ink == word) ink = random.nextInt(INKS.size)
            val item = StroopItem(word, ink, askInk = if (mixedRules) random.nextBoolean() else true)
            if (item != previous) return item
        }
    }
}

/**
 * Màu chữ (hiệu ứng Stroop): chữ "ĐỎ" được tô màu xanh, phải chọn màu mực (xanh) chứ không đọc chữ.
 * Mức khó: lúc hỏi màu mực, lúc hỏi nghĩa của chữ. Chọn sai bị trừ 1 điểm.
 */
@Composable
fun StroopChallengeSection(target: Int, mixedRules: Boolean, onProgress: (Float) -> Unit, onDone: () -> Unit) {
    var score by remember { mutableIntStateOf(0) }
    var item by remember { mutableStateOf(StroopGenerator.next(mixedRules)) }
    var feedback by remember { mutableStateOf("") }
    var finished by remember { mutableStateOf(false) }
    val currentDone by rememberUpdatedState(onDone)
    val currentProgress by rememberUpdatedState(onProgress)

    LaunchedEffect(score) {
        currentProgress((score / target.toFloat()).coerceAtMost(1f))
        if (score >= target) finished = true
    }
    LaunchedEffect(finished) {
        if (finished) {
            delay(700)
            currentDone()
        }
    }

    fun answer(i: Int) {
        if (finished) return
        if (i == item.answer) {
            score++
            feedback = "Đúng"
        } else {
            score = (score - 1).coerceAtLeast(0)
            feedback = "Sai rồi, trừ 1 điểm"
        }
        item = StroopGenerator.next(mixedRules, previous = item)
    }

    ChallengeHeader(
        "Màu chữ",
        if (mixedRules) "Đọc kỹ câu hỏi: lúc hỏi màu tô, lúc hỏi chữ ghi gì." else "Chọn màu tô của chữ, đừng đọc chữ.",
    )
    Text(
        "${score.coerceAtMost(target)}/$target",
        style = MaterialTheme.typography.titleMedium,
        color = Dawn.GoldSoft,
        modifier = Modifier.padding(top = 10.dp),
    )
    Text(
        if (item.askInk) "Chữ này được tô màu gì?" else "Chữ này ghi màu gì?",
        style = MaterialTheme.typography.bodyLarge,
        color = Dawn.Lilac,
        modifier = Modifier.padding(top = 12.dp),
    )
    Text(
        INKS[item.word].name,
        fontSize = 56.sp,
        fontWeight = FontWeight.Black,
        color = INKS[item.ink].color,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = 14.dp),
    )
    Text(
        feedback.ifEmpty { " " },
        style = MaterialTheme.typography.bodyMedium,
        color = if (feedback.startsWith("Sai")) Wrong else Dawn.GoldSoft,
    )
    Spacer(Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        INKS.indices.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { i ->
                    Row(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable { answer(i) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(16.dp).clip(CircleShape).background(INKS[i].color))
                        Spacer(Modifier.width(10.dp))
                        Text(INKS[i].name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                }
            }
        }
    }
}

private val CARD_SYMBOLS = listOf("★", "♥", "♣", "♠", "♦", "●", "▲", "■", "♪", "✿", "☾", "✚")

/** Lật cặp: lật hai thẻ một lượt, giống nhau thì giữ mở, khác nhau thì úp lại. Tìm hết các cặp. */
@Composable
fun PairsChallengeSection(pairs: Int, onProgress: (Float) -> Unit, onDone: () -> Unit) {
    val cards = remember {
        val chosen = CARD_SYMBOLS.shuffled().take(pairs.coerceIn(2, CARD_SYMBOLS.size))
        (chosen + chosen).shuffled()
    }
    var open by remember { mutableStateOf(listOf<Int>()) }
    var matched by remember { mutableStateOf(setOf<Int>()) }
    var finished by remember { mutableStateOf(false) }
    val currentDone by rememberUpdatedState(onDone)
    val currentProgress by rememberUpdatedState(onProgress)

    LaunchedEffect(open) {
        if (open.size == 2) {
            val a = open[0]
            val b = open[1]
            if (cards[a] == cards[b]) {
                matched = matched + a + b
            } else {
                delay(750) // cho kịp nhìn rồi mới úp lại
            }
            open = emptyList()
        }
    }
    LaunchedEffect(matched) {
        currentProgress(matched.size / cards.size.toFloat())
        if (matched.size == cards.size) finished = true
    }
    LaunchedEffect(finished) {
        if (finished) {
            delay(800)
            currentDone()
        }
    }

    fun flip(i: Int) {
        if (finished || open.size >= 2 || i in open || i in matched) return
        open = open + i
    }

    ChallengeHeader("Lật cặp", "Lật hai thẻ một lượt, tìm đủ ${cards.size / 2} cặp giống nhau.")
    Text(
        "${matched.size / 2}/${cards.size / 2} cặp",
        style = MaterialTheme.typography.titleMedium,
        color = Dawn.GoldSoft,
        modifier = Modifier.padding(vertical = 12.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cards.indices.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { i ->
                    val faceUp = i in open || i in matched
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(0.8f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                when {
                                    i in matched -> Dawn.Gold.copy(alpha = 0.35f)
                                    faceUp -> Color.White.copy(alpha = 0.18f)
                                    else -> Dawn.Horizon
                                }
                            )
                            .clickable { flip(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (faceUp) cards[i] else "?",
                            fontSize = if (faceUp) 28.sp else 20.sp,
                            color = if (faceUp) Color.White else Dawn.Lilac.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bật đèn: dùng cảm biến ánh sáng. Phải bật đèn phòng hoặc mở rèm, giữ máy ở chỗ đủ sáng liên tục vài giây.
 * Ánh sáng mạnh buổi sáng cũng giúp cơ thể tỉnh hẳn. Đèn màn hình của chính điện thoại không làm cảm biến sáng lên.
 */
@Composable
fun LightChallengeSection(minLux: Float, holdSeconds: Int, onProgress: (Float) -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    var lux by remember { mutableFloatStateOf(0f) }
    var heldMs by remember { mutableLongStateOf(0L) }
    var finished by remember { mutableStateOf(false) }
    val currentDone by rememberUpdatedState(onDone)
    val currentProgress by rememberUpdatedState(onProgress)
    val holdMs = holdSeconds * 1000L

    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                lux = event.values[0]
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }

    // Đếm thời gian đủ sáng liên tục; tối lại thì đếm lại từ đầu.
    LaunchedEffect(Unit) {
        val step = 200L
        while (!finished) {
            delay(step)
            heldMs = if (lux >= minLux) heldMs + step else 0L
            currentProgress((heldMs / holdMs.toFloat()).coerceAtMost(1f))
            if (heldMs >= holdMs) finished = true
        }
    }
    LaunchedEffect(finished) {
        if (finished) {
            delay(700)
            currentDone()
        }
    }

    val bright = lux >= minLux
    ChallengeHeader("Bật đèn", "Bật đèn phòng hoặc mở rèm, rồi giữ máy ở chỗ sáng $holdSeconds giây liền.")
    ProgressRing(
        heldMs / holdMs.toFloat(),
        if (bright) "${((holdMs - heldMs).coerceAtLeast(0) + 999) / 1000}s" else "Tối",
    )
    Text(
        "Độ sáng ${lux.roundToInt()} lux, cần từ ${minLux.roundToInt()} lux",
        style = MaterialTheme.typography.bodyMedium,
        color = Dawn.Lilac,
    )
    Text(
        if (bright) "Đủ sáng rồi, giữ nguyên nhé." else "Còn tối quá. Hãy bật đèn thật hoặc ra chỗ có ánh sáng.",
        style = MaterialTheme.typography.bodyMedium,
        color = if (bright) Dawn.GoldSoft else Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** Hướng vuốt cho thử thách Vuốt mũi tên. */
internal enum class SwipeDir(val symbol: String) {
    LEFT("←"), RIGHT("→"), UP("↑"), DOWN("↓");

    val opposite: SwipeDir
        get() = when (this) {
            LEFT -> RIGHT
            RIGHT -> LEFT
            UP -> DOWN
            DOWN -> UP
        }

    companion object {
        /** Hướng chính của một cú vuốt (dx, dy theo pixel), hoặc null nếu vuốt quá ngắn. */
        fun of(dx: Float, dy: Float, minDistance: Float): SwipeDir? {
            if (dx * dx + dy * dy < minDistance * minDistance) return null
            return if (abs(dx) > abs(dy)) {
                if (dx > 0) RIGHT else LEFT
            } else {
                if (dy > 0) DOWN else UP
            }
        }
    }
}

/** Một mũi tên. reverse = mũi tên đỏ, phải vuốt ngược hướng. */
internal data class ArrowItem(val dir: SwipeDir, val reverse: Boolean) {
    val expected: SwipeDir get() = if (reverse) dir.opposite else dir
}

/** Mũi tên mới, luôn khác mũi tên trước (để thấy rõ màn hình đã đổi sau mỗi lần vuốt). */
internal fun nextArrow(reverseChance: Float, random: Random = Random.Default, previous: ArrowItem? = null): ArrowItem {
    while (true) {
        val item = ArrowItem(SwipeDir.values()[random.nextInt(4)], random.nextFloat() < reverseChance)
        if (item != previous) return item
    }
}

/** Vuốt mũi tên: vuốt theo hướng mũi tên vàng, ngược hướng mũi tên đỏ. Sai bị trừ 1 điểm. */
@Composable
fun ArrowsChallengeSection(target: Int, reverseChance: Float, onProgress: (Float) -> Unit, onDone: () -> Unit) {
    var score by remember { mutableIntStateOf(0) }
    var item by remember { mutableStateOf(nextArrow(reverseChance)) }
    var feedback by remember { mutableStateOf("") }
    var finished by remember { mutableStateOf(false) }
    val currentDone by rememberUpdatedState(onDone)
    val currentProgress by rememberUpdatedState(onProgress)

    LaunchedEffect(score) {
        currentProgress((score / target.toFloat()).coerceAtMost(1f))
        if (score >= target) finished = true
    }
    LaunchedEffect(finished) {
        if (finished) {
            delay(700)
            currentDone()
        }
    }

    fun swipe(d: SwipeDir) {
        if (finished) return
        if (d == item.expected) {
            score++
            feedback = "Đúng"
        } else {
            score = (score - 1).coerceAtLeast(0)
            feedback = "Sai hướng, trừ 1 điểm"
        }
        item = nextArrow(reverseChance, previous = item)
    }
    // pointerInput(Unit) chỉ chạy một lần, nên giữ bản mới nhất của hàm xử lý qua rememberUpdatedState.
    val swipeHandler: (SwipeDir) -> Unit = { swipe(it) }
    val onSwipe by rememberUpdatedState(swipeHandler)

    ChallengeHeader(
        "Vuốt mũi tên",
        if (reverseChance > 0f) "Mũi tên vàng: vuốt theo. Mũi tên đỏ: vuốt ngược lại." else "Vuốt theo hướng mũi tên.",
    )
    Text(
        "${score.coerceAtMost(target)}/$target",
        style = MaterialTheme.typography.titleMedium,
        color = Dawn.GoldSoft,
        modifier = Modifier.padding(vertical = 12.dp),
    )
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.1f)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .pointerInput(Unit) {
                var total = Offset.Zero
                detectDragGestures(
                    onDragStart = { total = Offset.Zero },
                    onDrag = { change, drag ->
                        change.consume()
                        total += drag
                    },
                    onDragEnd = {
                        SwipeDir.of(total.x, total.y, 60.dp.toPx())?.let { onSwipe(it) }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            item.dir.symbol,
            fontSize = 120.sp,
            color = if (item.reverse) Wrong else Dawn.Gold,
        )
    }
    Text(
        feedback.ifEmpty { " " },
        style = MaterialTheme.typography.bodyMedium,
        color = if (feedback.startsWith("Sai")) Wrong else Dawn.GoldSoft,
        modifier = Modifier.padding(top = 10.dp),
    )
}
