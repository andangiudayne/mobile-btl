package com.nhom.challengealarm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Normalizer
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/** Mục tiêu theo độ khó 1..3 (chọn ở trình sửa chuông, dùng chung cho các thử thách). */
object ChallengeLevels {
    private fun pick(values: List<Int>, difficulty: Int) = values[(difficulty - 1).coerceIn(0, values.lastIndex)]

    fun shakeCount(difficulty: Int) = pick(listOf(15, 25, 40), difficulty)
    fun stepCount(difficulty: Int) = pick(listOf(15, 30, 50), difficulty)
    fun memoryLength(difficulty: Int) = pick(listOf(4, 6, 8), difficulty)
    fun orderCount(difficulty: Int) = pick(listOf(9, 16, 25), difficulty)
    fun orderColumns(difficulty: Int) = pick(listOf(3, 4, 5), difficulty)
    fun rhythmHits(difficulty: Int) = pick(listOf(3, 5, 7), difficulty)
    fun rhythmPeriodMs(difficulty: Int) = pick(listOf(1100, 850, 650), difficulty)
    fun rhythmZone(difficulty: Int) = listOf(0.26f, 0.2f, 0.14f)[(difficulty - 1).coerceIn(0, 2)]
    fun patternLength(difficulty: Int) = pick(listOf(4, 5, 7), difficulty)
    fun wordCount(difficulty: Int) = pick(listOf(1, 2, 3), difficulty)
    fun catchTarget(difficulty: Int) = pick(listOf(8, 12, 16), difficulty)
    fun catchVisibleMs(difficulty: Int) = pick(listOf(1100, 850, 650), difficulty)
    fun stroopTarget(difficulty: Int) = pick(listOf(5, 8, 12), difficulty)
    fun pairCount(difficulty: Int) = pick(listOf(4, 6, 8), difficulty)
    /** Độ sáng tối thiểu (lux): phòng tối khoảng 0 đến 10, đèn ngủ khoảng 20 đến 50, đèn phòng 100 trở lên. */
    fun lightLux(difficulty: Int) = listOf(30f, 60f, 120f)[(difficulty - 1).coerceIn(0, 2)]
    fun lightHoldSeconds(difficulty: Int) = pick(listOf(3, 5, 8), difficulty)
    fun arrowTarget(difficulty: Int) = pick(listOf(8, 12, 16), difficulty)
    /** Tỉ lệ mũi tên đỏ (phải vuốt ngược). Mức dễ không có. */
    fun arrowReverseChance(difficulty: Int) = listOf(0f, 0.25f, 0.4f)[(difficulty - 1).coerceIn(0, 2)]
}

internal val Wrong = Color(0xFFFF8A93)

@Composable
internal fun ChallengeHeader(title: String, hint: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center)
    Text(hint, style = MaterialTheme.typography.bodyMedium, color = Dawn.Lilac, textAlign = TextAlign.Center)
}

@Composable
internal fun ProgressRing(progress: Float, centerText: String) {
    Box(Modifier.padding(vertical = 16.dp).size(170.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxSize(),
            color = Dawn.Gold,
            trackColor = Color.White.copy(alpha = 0.15f),
            strokeWidth = 10.dp,
        )
        Text(centerText, fontSize = 36.sp, color = Color.White, style = MaterialTheme.typography.headlineMedium)
    }
}

/** Lắc mạnh điện thoại đủ số lần (gia tốc kế). Mỗi cú lắc cách nhau tối thiểu 0,3 giây. */
@Composable
fun ShakeChallengeSection(target: Int, onProgress: (Float) -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    var count by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    val currentDone by rememberUpdatedState(onDone)
    val currentProgress by rememberUpdatedState(onProgress)

    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var last = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
                val now = SystemClock.elapsedRealtime()
                if (g > 2.3f && now - last > 300L) {
                    last = now
                    count++
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }

    LaunchedEffect(count) {
        currentProgress((count / target.toFloat()).coerceAtMost(1f))
        if (count >= target) finished = true
    }
    // Tách riêng: lắc thêm sau khi đủ không được hủy việc tắt chuông.
    LaunchedEffect(finished) {
        if (finished) {
            delay(900)
            currentDone()
        }
    }

    ChallengeHeader("Lắc điện thoại", "Lắc mạnh để tắt chuông.")
    ProgressRing(count / target.toFloat(), "${count.coerceAtMost(target)}/$target")
}

/** Đi bộ đủ số bước (cảm biến đếm bước). Cần quyền nhận dạng hoạt động trên Android 10 trở lên. */
@Composable
fun StepsChallengeSection(target: Int, onProgress: (Float) -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    var steps by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    val currentDone by rememberUpdatedState(onDone)
    val currentProgress by rememberUpdatedState(onProgress)

    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        var baseline = -1f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Cảm biến trả về tổng số bước từ lúc khởi động máy, nên lấy mốc ở lần đo đầu tiên.
                val total = event.values[0]
                if (baseline < 0f) baseline = total
                steps = (total - baseline).toInt()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }

    LaunchedEffect(steps) {
        currentProgress((steps / target.toFloat()).coerceAtMost(1f))
        if (steps >= target) finished = true
    }
    // Tách riêng: đi thêm bước sau khi đủ không được hủy việc tắt chuông.
    LaunchedEffect(finished) {
        if (finished) {
            delay(900)
            currentDone()
        }
    }

    ChallengeHeader("Đi bộ", "Đứng dậy và đi đủ số bước để tắt chuông.")
    ProgressRing(steps / target.toFloat(), "${steps.coerceIn(0, target)}/$target")
}

/** Câu để gõ lại, theo 3 mức khó (ngắn, vừa, dài). Mỗi mức nhiều câu để sáng nào cũng khác. */
private val PHRASES = listOf(
    listOf(
        "Dậy thôi nào",
        "Hôm nay mình làm được",
        "Một ngày mới đã đến",
        "Mình đã tỉnh rồi",
        "Chào buổi sáng",
        "Mở mắt ra nào",
        "Uống một cốc nước",
        "Đứng dậy vươn vai",
        "Trời sáng rồi",
        "Bắt đầu ngày mới",
    ),
    listOf(
        "Mình sẽ không ngủ nướng nữa",
        "Hôm nay là một ngày tuyệt vời",
        "Dậy sớm để làm điều mình thích",
        "Mỗi buổi sáng là một khởi đầu mới",
        "Rửa mặt xong sẽ tỉnh táo hơn",
        "Năm phút nữa thành ba mươi phút",
        "Mình dậy đúng giờ như đã hứa",
        "Ánh nắng buổi sáng giúp mình tỉnh",
        "Hôm nay mình sẽ học thật tốt",
        "Bữa sáng ngon đang chờ mình",
    ),
    listOf(
        "Mình dậy ngay bây giờ vì hôm nay còn nhiều việc quan trọng",
        "Rời khỏi chiếc giường ấm là việc khó nhất của cả ngày",
        "Người dậy sớm có thêm thời gian cho những điều mình yêu thích",
        "Ngủ đủ giấc và dậy đúng giờ giúp đầu óc minh mẫn cả ngày",
        "Mỗi lần ngủ nướng là một lần mình thua chính mình",
        "Một ly nước và vài phút vận động sẽ đánh thức cơ thể",
        "Hôm nay mình chọn đứng dậy thay vì nằm thêm năm phút",
        "Thành công bắt đầu từ việc rời khỏi giường đúng giờ",
    ),
)

private fun nfc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)

/**
 * Gõ lại đúng nguyên câu. Câu hiện từng ký tự: đúng thì sáng, sai thì đỏ. Không phân biệt hoa thường, gõ có dấu hay
 * không dấu đều được (TypingMatch). Nút Back bị khóa lúc chuông reo, nên luôn có nút ẩn/hiện bàn phím ngay trong ô nhập
 * và bên dưới, để lộ ra "Đổi sang giải toán" và "Thoát khẩn cấp".
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TypingChallengeSection(difficulty: Int, onProgress: (Float) -> Unit, onDone: () -> Unit) {
    val phrase = remember { nfc(PHRASES[(difficulty - 1).coerceIn(0, PHRASES.lastIndex)].random()) }
    var input by remember { mutableStateOf("") }
    var finished by remember { mutableStateOf(false) }
    val currentDone by rememberUpdatedState(onDone)
    val currentProgress by rememberUpdatedState(onProgress)
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // Theo bàn phím thật sự đang hiện (ô nhập vẫn có thể đang được chọn khi bàn phím đã ẩn).
    val keyboardShown = isKeyboardVisible()

    fun showKeyboard() {
        focus.requestFocus()
        keyboard?.show()
    }

    fun hideKeyboard() {
        keyboard?.hide()
        focusManager.clearFocus()
    }

    LaunchedEffect(Unit) {
        // Đợi cửa sổ sẵn sàng rồi mới mở bàn phím, mở ngay lúc vừa hiện có máy không bật bàn phím lên.
        delay(300)
        runCatching { showKeyboard() }
    }
    LaunchedEffect(input) {
        currentProgress(TypingMatch.correctPrefix(phrase, input) / phrase.length.toFloat())
        if (TypingMatch.isDone(phrase, input)) finished = true
    }
    // Tách riêng: gõ thêm sau khi đúng không được hủy việc tắt chuông.
    LaunchedEffect(finished) {
        if (finished) {
            hideKeyboard() // gõ xong thì cất bàn phím
            delay(900)
            currentDone()
        }
    }

    ChallengeHeader("Gõ lại câu", "Gõ đúng câu bên dưới để tắt chuông. Có dấu hay không dấu đều được.")
    Spacer(Modifier.height(12.dp))
    val states = TypingMatch.charStates(phrase, input)
    Text(
        buildAnnotatedString {
            phrase.forEachIndexed { i, c ->
                val color = when (states.getOrNull(i)) {
                    null -> Dawn.Lilac.copy(alpha = 0.7f)
                    true -> Color.White
                    false -> Wrong
                }
                withStyle(SpanStyle(color = color)) { append(c) }
            }
        },
        fontSize = 24.sp,
        lineHeight = 34.sp,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.titleLarge,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = input,
        // Giữ nguyên chữ bộ gõ đưa vào (đổi chữ ở đây làm Telex/VNI ghép dấu sai); chỉ bỏ dấu xuống dòng.
        // Ô nhiều dòng nên nhiều bàn phím hiện phím Enter thay cho "Xong": bấm Enter cũng là cất bàn phím.
        onValueChange = { v ->
            if ('\n' in v) {
                input = v.replace("\n", "")
                hideKeyboard()
            } else {
                input = v
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus),
        placeholder = { Text("Gõ vào đây", color = Dawn.Lilac) },
        shape = RoundedCornerShape(16.dp),
        maxLines = 3,
        // Tắt tự sửa chữ: bàn phím tự đổi từ hoặc tự thêm dấu cách sẽ làm câu không khớp.
        keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { hideKeyboard() }),
        trailingIcon = {
            IconButton(onClick = { if (keyboardShown) hideKeyboard() else showKeyboard() }) {
                Icon(
                    if (keyboardShown) Icons.Default.KeyboardHide else Icons.Default.Keyboard,
                    contentDescription = if (keyboardShown) "Ẩn bàn phím" else "Hiện bàn phím",
                    tint = Dawn.GoldSoft,
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Dawn.Gold,
            focusedBorderColor = Dawn.Gold,
            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
        ),
    )
    TextButton(onClick = { if (keyboardShown) hideKeyboard() else showKeyboard() }) {
        Text(if (keyboardShown) "Ẩn bàn phím" else "Hiện bàn phím để gõ tiếp", color = Dawn.GoldSoft)
    }
}

private val PadColors = listOf(Color(0xFFF5736A), Color(0xFFFFB84D), Color(0xFF3FB8A5), Color(0xFF8C7BFF))

/** Nhìn dãy ô sáng lần lượt rồi chạm lại đúng thứ tự. Sai thì đổi dãy mới. */
@Composable
fun MemoryChallengeSection(length: Int, onProgress: (Float) -> Unit, onDone: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    val sequence = remember(round) { List(length) { Random.nextInt(4) } }
    var lit by remember { mutableIntStateOf(-1) }
    var showing by remember { mutableStateOf(true) }
    var position by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val currentDone by rememberUpdatedState(onDone)
    val currentProgress by rememberUpdatedState(onProgress)
    val scope = rememberCoroutineScope()

    LaunchedEffect(round) {
        showing = true
        position = 0
        lit = -1
        delay(700)
        for (pad in sequence) {
            lit = pad
            delay(550)
            lit = -1
            delay(250)
        }
        showing = false
    }
    LaunchedEffect(position) { currentProgress(position / length.toFloat()) }

    ChallengeHeader(
        "Nhớ dãy màu",
        if (showing) "Nhìn kỹ dãy ô sáng." else "Chạm lại theo thứ tự ($position/$length).",
    )
    if (wrong) {
        Text("Sai rồi, xem dãy mới nhé.", style = MaterialTheme.typography.bodyMedium, color = Wrong)
    }
    Spacer(Modifier.height(16.dp))

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in 0..1) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (col in 0..1) {
                    val pad = row * 2 + col
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1.3f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(PadColors[pad].copy(alpha = if (lit == pad) 1f else 0.3f))
                            .clickable(enabled = !showing && !finished) {
                                wrong = false
                                scope.launch {
                                    lit = pad
                                    delay(150)
                                    lit = -1
                                }
                                if (sequence[position] == pad) {
                                    position++
                                    if (position == length) {
                                        finished = true
                                        scope.launch {
                                            delay(900)
                                            currentDone()
                                        }
                                    }
                                } else {
                                    wrong = true
                                    round++
                                }
                            }
                    )
                }
            }
        }
    }
}

/** Chạm các số 1, 2, 3, ... theo đúng thứ tự trong lưới xáo trộn. Chạm sai thì ô đó nháy đỏ. */
@Composable
fun OrderChallengeSection(count: Int, columns: Int, onProgress: (Float) -> Unit, onDone: () -> Unit) {
    val numbers = remember { (1..count).shuffled() }
    var next by remember { mutableIntStateOf(1) }
    var wrongNumber by remember { mutableIntStateOf(-1) }
    var finished by remember { mutableStateOf(false) }
    val currentDone by rememberUpdatedState(onDone)
    val currentProgress by rememberUpdatedState(onProgress)

    LaunchedEffect(wrongNumber) {
        if (wrongNumber != -1) {
            delay(300)
            wrongNumber = -1
        }
    }
    LaunchedEffect(next) {
        currentProgress((next - 1) / count.toFloat())
        if (next > count && !finished) {
            finished = true
            delay(900)
            currentDone()
        }
    }

    ChallengeHeader(
        "Chạm số theo thứ tự",
        if (next <= count) "Chạm số $next." else "Xong rồi.",
    )
    Spacer(Modifier.height(12.dp))

    val rows = count / columns
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (r in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in 0 until columns) {
                    val number = numbers[r * columns + c]
                    val done = number < next
                    val background = when {
                        done -> Dawn.Gold
                        number == wrongNumber -> Wrong.copy(alpha = 0.8f)
                        else -> Color.White.copy(alpha = 0.1f)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(background)
                            .clickable(enabled = !done && !finished) {
                                if (number == next) next++ else wrongNumber = number
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            number.toString(),
                            fontSize = 22.sp,
                            color = if (done) Dawn.Midnight else Color.White,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
        }
    }
}
