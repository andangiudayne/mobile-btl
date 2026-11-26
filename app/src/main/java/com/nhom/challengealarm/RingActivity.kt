package com.nhom.challengealarm

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * VIEW của màn hình chuông reo (MVVM). Mọi tiến độ (màn, giờ nghỉ, hoãn, tắt chuông) nằm trong RingViewModel.
 * Activity chỉ lo những việc gắn với cửa sổ: hiện trên màn hình khóa, ghim màn hình, ẩn thanh hệ thống,
 * nuốt nút âm lượng/Back, giảm độ sáng lúc nghỉ, và đóng lại khi ViewModel báo closed.
 */
class RingActivity : ComponentActivity() {

    private val vm: RingViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        val st = vm.state.value
        if (st.done || st.closed) return // đã tắt chuông (đang xem bản tin buổi sáng): không ghim lại
        RingState.visible = true
        // Ghim màn hình: chặn Home, Recents và kéo thanh thông báo cho đến khi xong thử thách.
        runCatching { startLockTask() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    /** Nuốt các nút âm lượng và Back để không hạ nhỏ hay tắt tiếng chuông được (tắt chuông xong thì trả lại bình thường). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (vm.state.value.done) return super.dispatchKeyEvent(event)
        return when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_APP_SWITCH -> true
            else -> super.dispatchKeyEvent(event)
        }
    }

    /** Lúc nghỉ giữa màn: hạ độ sáng thật thấp và cho màn hình tự tắt; hết nghỉ thì sáng lại và giữ sáng. */
    private fun dimScreen(dim: Boolean) {
        window.attributes = window.attributes.apply {
            screenBrightness = if (dim) 0.02f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        if (dim) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onPause() {
        RingState.visible = false
        super.onPause()
    }

    /** Bản tin buổi sáng chỉ để xem nhanh: rời khỏi nó (Home, tắt màn hình) thì đóng luôn, không nằm lại trong nền. */
    override fun onStop() {
        super.onStop()
        val st = vm.state.value
        if (st.done && !isFinishing) finish()
    }

    /** Màn hình này là singleTask: chuông mới reo khi màn hình cũ còn mở thì nhận Intent ở đây. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.restartIfFinished(
            alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1),
            intentSnoozeCount = intent.getIntExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, 0),
        )
    }

    /** Chia đôi màn hình / cửa sổ nổi: màn thử thách vẫn "resumed" nhưng không còn ở trên cùng, coi như bị che. */
    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        RingState.visible = isTopResumedActivity && !vm.state.value.done
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        vm.start(
            alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1),
            intentSnoozeCount = intent.getIntExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, 0),
        )

        setContent {
            AlarmTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.inBreak) { dimScreen(state.inBreak) }
                LaunchedEffect(state.done) {
                    if (state.done) {
                        // Chuông đã tắt: bỏ ghim, trả độ sáng, cho màn hình tự tắt như bình thường.
                        runCatching { stopLockTask() }
                        RingState.visible = false
                        dimScreen(false)
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
                LaunchedEffect(state.closed) {
                    if (state.closed) {
                        runCatching { stopLockTask() }
                        finish()
                    }
                }
                if (state.done) {
                    MorningScreen(summary = state.summary, onClose = vm::closeSummary)
                } else if (state.ready) {
                    RingScreen(
                        state = state,
                        onProgress = vm::onRoundProgress,
                        onRoundDone = vm::completeRound,
                        onBreakEnd = vm::endBreak,
                        onAwake = vm::awake,
                        onSnooze = vm::snooze,
                        onEmergency = vm::emergency,
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(skyTop(0f), skyBottom(0f)))),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(color = Dawn.Gold) }
                }
            }
        }
    }
}

private const val MAX_ANSWER_LENGTH = 7

private fun nowText(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

/** Màn hình reo: đồng hồ, mặt trời mọc theo tiến độ, chấm màn, khung thử thách (hoặc đếm ngược lúc nghỉ). */
@Composable
private fun RingScreen(
    state: RingUiState,
    onProgress: (Float) -> Unit,
    onRoundDone: (method: String, type: ChallengeType) -> Unit,
    onBreakEnd: () -> Unit,
    onAwake: () -> Unit,
    onSnooze: () -> Unit,
    onEmergency: () -> Unit,
) {
    // Chặn nút Back: không cho thoát màn hình thử thách
    BackHandler(enabled = true) { }

    val progress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = tween(900),
        label = "ringSky",
    )
    val clock by produceState(nowText()) {
        while (true) {
            value = nowText()
            delay(10_000)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(skyTop(progress), skyBottom(progress))))
            .drawBehind { drawStars((1f - progress * 1.3f).coerceIn(0f, 1f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                // Chừa chỗ cho bàn phím (thử thách gõ chữ): vùng cuộn co lại phía trên bàn phím, nên vẫn cuộn xuống
                // được tới nút "Đổi sang giải toán" và "Thoát khẩn cấp" thay vì bị bàn phím che mất.
                // Không dùng imePadding(): màn này ẩn thanh trạng thái nên trên Android 10 trở xuống nó luôn bằng 0.
                .keyboardPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                clock,
                style = androidx.compose.material3.MaterialTheme.typography.displayLarge.copy(
                    fontSize = 72.sp,
                    lineHeight = 76.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-3).sp,
                ),
                color = Color.White,
            )
            if (state.label.isNotBlank()) {
                Text(
                    state.label,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = Dawn.Lilac,
                )
            }

            SunCanvas(
                modifier = Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp),
                rise = 0.15f + 0.85f * progress,
            )
            Spacer(Modifier.height(12.dp))
            RoundHeader(state.roundsDone, state.rounds)
            Spacer(Modifier.height(12.dp))

            // Khung kính mờ chứa thử thách (hoặc đồng hồ đếm ngược lúc nghỉ), nổi trên bầu trời
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.inBreak) {
                    BreakPanel(
                        until = state.breakUntil,
                        nextRound = state.roundsDone + 1,
                        total = state.rounds,
                        onEnd = onBreakEnd,
                    )
                } else {
                    // key: mỗi màn là một thử thách mới tinh, trạng thái màn trước không còn sót lại
                    key(state.roundsDone) {
                        RoundContent(
                            challenge = state.current,
                            difficulty = state.difficulty,
                            mathCount = state.mathCount,
                            isLastRound = state.isLastRound,
                            onProgress = onProgress,
                            onRoundDone = onRoundDone,
                        )
                    }
                }
            }

            if (state.canUseAwake) {
                Spacer(Modifier.height(16.dp))
                AwakeHoldButton(onConfirm = onAwake)
            }

            if (state.snoozesLeft > 0 && !state.inBreak) {
                TextButton(onClick = onSnooze) {
                    Text(
                        "Ngủ thêm ${AlarmScheduler.SNOOZE_MINUTES} phút (còn ${state.snoozesLeft} lần)",
                        color = Dawn.Lilac,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            EmergencyExit(unlockAt = state.emergencyUnlockAt, onExit = onEmergency)
        }
    }
}

/**
 * Nội dung một màn. Trạng thái tạm trong màn (đã giải mấy bài, có đổi sang toán không) là trạng thái giao diện
 * nên để ở đây; tiến độ và kết quả màn thì báo lên ViewModel.
 */
@Composable
private fun RoundContent(
    challenge: ActiveChallenge,
    difficulty: Int,
    mathCount: Int,
    isLastRound: Boolean,
    onProgress: (Float) -> Unit,
    onRoundDone: (method: String, type: ChallengeType) -> Unit,
) {
    var useMath by remember { mutableStateOf(challenge.type == ChallengeType.MATH) }
    var canSwitch by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var solved by remember { mutableIntStateOf(0) }
    val total = mathCount.coerceAtLeast(1)
    // Toán là phương án thay thế (tự đổi hoặc thử thách gốc không dùng được) thì khó hơn một bậc.
    val replaced = challenge.type != ChallengeType.MATH || challenge.param == FALLBACK_PARAM
    val mathDifficulty = if (replaced) (difficulty + 1).coerceAtMost(3) else difficulty
    // Giờ nghỉ tính theo thử thách gốc: đổi từ đi bộ sang toán không được hưởng giờ nghỉ.
    val mathDone = { onRoundDone(WakeMethod.MATH, challenge.type) }
    val doneText = if (isLastRound) "Dậy rồi, chào buổi sáng" else "Qua màn rồi"

    LaunchedEffect(Unit) {
        delay(MATH_SWITCH_AFTER_MS)
        canSwitch = true
    }

    if (useMath) {
        MathSection(
            difficulty = mathDifficulty,
            total = total,
            solved = solved,
            onSolvedChange = {
                solved = it
                onProgress(it / total.toFloat())
            },
            finishText = doneText,
            onDone = mathDone,
        )
        return
    }

    val type = challenge.type
    val done: (String) -> Unit = { method -> onRoundDone(method, type) }
    when (type) {
        ChallengeType.QR ->
            QrChallengeSection(challenge.name, challenge.param) { done(WakeMethod.QR) }
        ChallengeType.PHOTO ->
            PhotoChallengeSection(challenge.name, challenge.param) { done(WakeMethod.PHOTO) }
        ChallengeType.SHAKE ->
            ShakeChallengeSection(ChallengeLevels.shakeCount(difficulty), onProgress) { done(WakeMethod.SHAKE) }
        ChallengeType.STEPS ->
            StepsChallengeSection(ChallengeLevels.stepCount(difficulty), onProgress) { done(WakeMethod.STEPS) }
        ChallengeType.TYPING ->
            TypingChallengeSection(difficulty, onProgress) { done(WakeMethod.TYPING) }
        ChallengeType.MEMORY ->
            MemoryChallengeSection(ChallengeLevels.memoryLength(difficulty), onProgress) { done(WakeMethod.MEMORY) }
        ChallengeType.ORDER ->
            OrderChallengeSection(
                ChallengeLevels.orderCount(difficulty),
                ChallengeLevels.orderColumns(difficulty),
                onProgress,
            ) { done(WakeMethod.ORDER) }
        ChallengeType.RHYTHM ->
            RhythmChallengeSection(
                ChallengeLevels.rhythmHits(difficulty),
                ChallengeLevels.rhythmZone(difficulty),
                ChallengeLevels.rhythmPeriodMs(difficulty),
                onProgress,
            ) { done(WakeMethod.RHYTHM) }
        ChallengeType.PATTERN ->
            PatternChallengeSection(ChallengeLevels.patternLength(difficulty), onProgress) { done(WakeMethod.PATTERN) }
        ChallengeType.WORDS ->
            WordsChallengeSection(ChallengeLevels.wordCount(difficulty), onProgress) { done(WakeMethod.WORDS) }
        ChallengeType.CATCH ->
            CatchChallengeSection(
                ChallengeLevels.catchTarget(difficulty),
                ChallengeLevels.catchVisibleMs(difficulty),
                onProgress,
            ) { done(WakeMethod.CATCH) }
        ChallengeType.STROOP ->
            StroopChallengeSection(
                ChallengeLevels.stroopTarget(difficulty),
                mixedRules = difficulty >= 3,
                onProgress = onProgress,
            ) { done(WakeMethod.STROOP) }
        ChallengeType.PAIRS ->
            PairsChallengeSection(ChallengeLevels.pairCount(difficulty), onProgress) { done(WakeMethod.PAIRS) }
        ChallengeType.LIGHT ->
            LightChallengeSection(
                ChallengeLevels.lightLux(difficulty),
                ChallengeLevels.lightHoldSeconds(difficulty),
                onProgress,
            ) { done(WakeMethod.LIGHT) }
        ChallengeType.ARROWS ->
            ArrowsChallengeSection(
                ChallengeLevels.arrowTarget(difficulty),
                reverseChance = ChallengeLevels.arrowReverseChance(difficulty),
                onProgress = onProgress,
            ) { done(WakeMethod.ARROWS) }
        ChallengeType.ODD ->
            OddColorChallengeSection(difficulty, onProgress) { done(WakeMethod.ODD) }
        ChallengeType.DIGITS ->
            DigitSpanChallengeSection(difficulty, onProgress) { done(WakeMethod.DIGITS) }
        ChallengeType.SLIDE ->
            SlidePuzzleChallengeSection(difficulty, onProgress) { done(WakeMethod.SLIDE) }
        ChallengeType.TILT ->
            TiltChallengeSection(difficulty, onProgress) { done(WakeMethod.TILT) }
        ChallengeType.COMPARE ->
            CompareChallengeSection(difficulty, onProgress) { done(WakeMethod.COMPARE) }
        ChallengeType.CLOCK ->
            ClockChallengeSection(difficulty, onProgress) { done(WakeMethod.CLOCK) }
        ChallengeType.MATH -> Unit // đã xử lý ở trên
    }

    // Chỉ cho đổi sau một lúc, để không thể bỏ qua thử thách vận động ngay từ giây đầu.
    if (!canSwitch) return
    TextButton(onClick = {
        focusManager.clearFocus() // ẩn bàn phím của thử thách gõ chữ (toán dùng bàn phím số riêng)
        onProgress(0f)
        useMath = true
    }) {
        Text("Không làm được? Đổi sang giải toán (khó hơn)", color = Dawn.GoldSoft)
    }
}

@Composable
private fun MathSection(
    difficulty: Int,
    total: Int,
    solved: Int,
    onSolvedChange: (Int) -> Unit,
    finishText: String,
    onDone: () -> Unit,
) {
    var problem by remember { mutableStateOf(MathChallenge.generate(difficulty)) }
    var input by remember { mutableStateOf("") }
    var wrongTick by remember { mutableIntStateOf(0) }
    var showWrong by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val finished = solved >= total

    // Giải xong bài cuối: để mặt trời nhô lên một nhịp rồi mới tắt chuông.
    LaunchedEffect(finished) {
        if (finished) {
            delay(1200)
            onDone()
        }
    }
    LaunchedEffect(wrongTick) {
        if (wrongTick > 0) {
            repeat(3) {
                shake.animateTo(14f, tween(50))
                shake.animateTo(-14f, tween(50))
            }
            shake.animateTo(0f, tween(50))
        }
    }

    if (finished) {
        Text(
            finishText,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 48.dp),
        )
        return
    }

    Text("Bài ${solved + 1}/$total", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = Dawn.Lilac)
    LinearProgressIndicator(
        progress = { solved / total.toFloat() },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).clip(CircleShape),
        color = Dawn.Gold,
        trackColor = Color.White.copy(alpha = 0.15f),
    )
    Spacer(Modifier.height(8.dp))

    Text(
        problem.question,
        fontSize = 38.sp,
        color = Color.White,
        textAlign = TextAlign.Center,
        style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(12.dp))

    Box(
        Modifier
            .fillMaxWidth()
            .offset { IntOffset(shake.value.roundToInt(), 0) }
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (input.isEmpty()) "Nhập đáp án" else input,
            fontSize = 28.sp,
            color = if (input.isEmpty()) Dawn.Lilac.copy(alpha = 0.6f) else Color.White,
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
        )
    }
    if (showWrong) {
        Text(
            "Chưa đúng, thử lại.",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = Color(0xFFFFB4B4),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    Spacer(Modifier.height(12.dp))

    NumPad(
        onDigit = { d ->
            showWrong = false
            if (input.length < MAX_ANSWER_LENGTH) input += d
        },
        onMinus = {
            showWrong = false
            input = if (input.startsWith("-")) input.drop(1) else "-$input"
        },
        onBackspace = {
            showWrong = false
            input = input.dropLast(1)
        },
    )
    Spacer(Modifier.height(12.dp))

    Button(
        onClick = {
            if (input.toIntOrNull() == problem.answer) {
                input = ""
                onSolvedChange(solved + 1)
                if (solved + 1 < total) problem = MathChallenge.generate(difficulty)
            } else {
                showWrong = true
                wrongTick++
            }
        },
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Dawn.Gold, contentColor = Dawn.Midnight),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("Xác nhận", style = androidx.compose.material3.MaterialTheme.typography.labelLarge) }
}

@Composable
private fun NumPad(onDigit: (String) -> Unit, onMinus: () -> Unit, onBackspace: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { d -> Key(onClick = { onDigit(d) }) { KeyText(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key(onClick = onMinus) { KeyText("−") }
            Key(onClick = { onDigit("0") }) { KeyText("0") }
            Key(onClick = onBackspace) {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Xóa",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun RowScope.Key(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun KeyText(text: String) {
    Text(text, fontSize = 22.sp, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
}

/**
 * Thoát khẩn cấp (ví dụ có việc gấp, chuông reo nhầm): chỉ mở sau vài phút chuông reo và phải giữ liền 15 giây,
 * để không thành đường tắt khi đang ngái ngủ nhưng cũng không bị kẹt hẳn. Bị ghi là "thoát khẩn cấp" trong thống kê.
 */
@Composable
private fun EmergencyExit(unlockAt: Long, onExit: () -> Unit) {
    val lockedFor by produceState(unlockAt - System.currentTimeMillis(), unlockAt) {
        while (value > 0) {
            delay(1_000)
            value = unlockAt - System.currentTimeMillis()
        }
    }
    if (lockedFor > 0) {
        val sec = (lockedFor + 999) / 1000
        Text(
            "Thoát khẩn cấp mở sau %d:%02d".format(sec / 60, sec % 60),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = Dawn.Lilac.copy(alpha = 0.6f),
        )
        return
    }

    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var elapsed by remember { mutableIntStateOf(0) }

    LaunchedEffect(pressed) {
        if (!pressed) {
            elapsed = 0
            return@LaunchedEffect
        }
        val step = 100L
        while (elapsed * step < EMERGENCY_HOLD_MS) {
            delay(step)
            elapsed++
        }
        onExit()
    }

    val fraction = (elapsed * 100f / EMERGENCY_HOLD_MS).coerceIn(0f, 1f)
    val secondsLeft = ((EMERGENCY_HOLD_MS - elapsed * 100L) / 1000L).coerceAtLeast(0) + 1

    Box(
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .drawBehind {
                drawRect(Color.White.copy(alpha = 0.2f), size = Size(size.width * fraction, size.height))
            }
            .clickable(interactionSource = source, indication = null) { }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            if (pressed) "Giữ tiếp, còn $secondsLeft giây" else "Giữ ${EMERGENCY_HOLD_MS / 1000} giây để thoát khẩn cấp",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = Dawn.Lilac,
        )
    }
}
