package com.nhom.challengealarm

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Normalizer
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------------------------
// Bốn thử thách thêm: Bắt nhịp, Vẽ lại hình, Xếp chữ, Bắt nắng.
// Không cần quyền hay cảm biến nên luôn dùng được.
// ---------------------------------------------------------------------------------------------

/** Bắt nhịp: chấm sáng chạy qua lại, chạm đúng lúc nó nằm trong vùng vàng. Trượt thì mất một điểm. */
@Composable
fun RhythmChallengeSection(
    target: Int,
    zoneWidth: Float,
    periodMs: Int,
    onProgress: (Float) -> Unit,
    onDone: () -> Unit,
) {
    var hits by remember { mutableIntStateOf(0) }
    var center by remember { mutableFloatStateOf(Random.nextFloat() * 0.6f + 0.2f) }
    var message by remember { mutableStateOf("") }
    var finished by remember { mutableStateOf(false) }
    val pos = remember { Animatable(0f) }
    val currentDone by rememberUpdatedState(onDone)
    val currentProgress by rememberUpdatedState(onProgress)

    LaunchedEffect(Unit) {
        while (true) {
            pos.animateTo(1f, tween(periodMs, easing = LinearEasing))
            pos.animateTo(0f, tween(periodMs, easing = LinearEasing))
        }
    }
    LaunchedEffect(hits) {
        currentProgress((hits / target.toFloat()).coerceIn(0f, 1f))
        if (hits >= target && !finished) {
            finished = true
            delay(900)
            currentDone()
        }
    }

    fun tap() {
        if (finished) return
        if (abs(pos.value - center) <= zoneWidth / 2f) {
            hits++
            message = "Trúng"
            center = Random.nextFloat() * 0.6f + 0.2f
        } else {
            hits = (hits - 1).coerceAtLeast(0)
            message = "Trượt, thử lại"
        }
    }

    ChallengeHeader("Bắt nhịp", "Chạm khi chấm sáng nằm trong vùng vàng.")
    Text(
        "${hits.coerceAtMost(target)}/$target",
        fontSize = 40.sp,
        color = Color.White,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(top = 16.dp),
    )
    Spacer(Modifier.height(12.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .drawBehind {
                val r = size.height * 0.3f
                val usable = size.width - 2 * r
                val left = r + usable * (center - zoneWidth / 2f)
                val right = r + usable * (center + zoneWidth / 2f)
                drawRoundRect(
                    Dawn.Gold.copy(alpha = 0.4f),
                    topLeft = Offset(left, size.height * 0.12f),
                    size = Size(right - left, size.height * 0.76f),
                    cornerRadius = CornerRadius(size.height * 0.3f),
                )
                drawCircle(Color.White, r, Offset(r + usable * pos.value, size.height / 2f))
            }
    )
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = if (message == "Trúng") Dawn.GoldSoft else Wrong,
        modifier = Modifier.padding(top = 8.dp).height(20.dp),
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = ::tap,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Dawn.Gold, contentColor = Dawn.Midnight),
        modifier = Modifier.fillMaxWidth().height(64.dp),
    ) { Text("Chạm", fontSize = 20.sp, style = MaterialTheme.typography.titleLarge) }
}

/**
 * Vẽ lại hình: một đường đi qua các chấm trong lưới 3x3 hiện lần lượt, rồi người dùng vuốt ngón tay
 * qua đúng các chấm đó theo đúng thứ tự. Sai thì xem lại từ đầu.
 */
@Composable
fun PatternChallengeSection(length: Int, onProgress: (Float) -> Unit, onDone: () -> Unit) {
    // Không sinh hình phải vuốt xuyên qua một chấm chưa dùng (vuốt thẳng sẽ chạm nhầm chấm giữa, không vẽ lại được).
    val pattern = remember { PatternRules.generate(length) }
    var showing by remember { mutableStateOf(true) }
    var shown by remember { mutableIntStateOf(0) }
    var replay by remember { mutableIntStateOf(0) }
    var drawn by remember { mutableStateOf(listOf<Int>()) }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var message by remember { mutableStateOf("Nhớ đường đi") }
    var finished by remember { mutableStateOf(false) }
    val currentDone by rememberUpdatedState(onDone)
    val currentProgress by rememberUpdatedState(onProgress)

    LaunchedEffect(replay) {
        showing = true
        drawn = emptyList()
        shown = 0
        message = "Nhớ đường đi"
        delay(700)
        for (i in 1..length) {
            shown = i
            delay(550)
        }
        delay(350)
        showing = false
        shown = 0
        message = "Vuốt lại đúng đường đi"
    }
    LaunchedEffect(finished) {
        if (finished) {
            currentProgress(1f)
            delay(900)
            currentDone()
        }
    }

    fun check() {
        if (drawn == pattern) {
            finished = true
            message = "Đúng rồi"
        } else {
            message = "Sai rồi, xem lại nhé"
            replay++
        }
    }

    ChallengeHeader("Vẽ lại hình", "Nhìn đường đi, rồi vuốt lại qua đúng các chấm.")
    Text(
        message,
        style = MaterialTheme.typography.titleMedium,
        color = if (message.startsWith("Sai")) Wrong else Dawn.GoldSoft,
        modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
    )
    Box(
        Modifier
            .size(264.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .pointerInput(showing, finished) {
                if (showing || finished) return@pointerInput
                val cell = size.width / 3f
                fun hit(o: Offset): Int? {
                    for (i in 0..8) {
                        val c = Offset((i % 3 + 0.5f) * cell, (i / 3 + 0.5f) * cell)
                        if ((o - c).getDistance() <= cell * 0.34f) return i
                    }
                    return null
                }
                detectDragGestures(
                    onDragStart = { o ->
                        drawn = emptyList()
                        finger = o
                        hit(o)?.let { drawn = listOf(it) }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        finger = change.position
                        hit(change.position)?.let { if (it !in drawn) drawn = drawn + it }
                    },
                    onDragEnd = {
                        finger = null
                        check()
                    },
                    onDragCancel = {
                        finger = null
                        drawn = emptyList()
                    },
                )
            }
            .drawBehind {
                val cell = size.width / 3f
                fun c(i: Int) = Offset((i % 3 + 0.5f) * cell, (i / 3 + 0.5f) * cell)
                val active = if (showing) pattern.take(shown) else drawn
                val stroke = 10.dp.toPx()
                for (k in 1 until active.size) {
                    drawLine(Dawn.Gold, c(active[k - 1]), c(active[k]), stroke, StrokeCap.Round)
                }
                val f = finger
                if (!showing && f != null && active.isNotEmpty()) {
                    drawLine(Dawn.Gold.copy(alpha = 0.5f), c(active.last()), f, stroke, StrokeCap.Round)
                }
                for (i in 0..8) {
                    val on = i in active
                    drawCircle(
                        if (on) Dawn.GoldSoft else Color.White.copy(alpha = 0.25f),
                        cell * (if (on) 0.17f else 0.1f),
                        c(i),
                    )
                }
            }
    )
}

/** Từ tiếng Việt để xếp chữ (viết hoa, có dấu). Nhiều từ ngắn và dài để mỗi lần mỗi khác. */
private val WORD_BANK = listOf(
    "TRƯỜNG", "THƯỜNG", "HƯỚNG", "NGƯỜI", "QUYẾT", "TUYỆT",
    "NHƯNG", "KHUYÊN", "ĐƯỜNG", "CHƯƠNG", "NGHIỆP", "THUYỀN",
    "BÌNH", "MINH", "SÁNG", "NẮNG", "THỨC", "DẬY", "SỚM", "KHỎE",
    "TỈNH", "GIƯỜNG", "ĐỒNG", "HỒ", "CHUÔNG", "BỮA", "TRƯA",
    "NƯỚC", "CHẠY", "NHẢY", "HỌC", "BÀI", "LỚP", "BẠN", "VUI",
    "MẶT", "TRỜI", "BẦU", "XANH", "MÂY", "GIÓ", "MƯA",
).map { Normalizer.normalize(it, Normalizer.Form.NFC) }

private fun scramble(word: String): List<Char> {
    var letters = word.toList().shuffled()
    var tries = 0
    while (letters.joinToString("") == word && tries++ < 10) letters = letters.shuffled()
    return letters
}

/** Xếp chữ: các chữ cái bị xáo trộn, chạm lần lượt để ghép lại thành một từ tiếng Việt. */
@Composable
fun WordsChallengeSection(count: Int, onProgress: (Float) -> Unit, onDone: () -> Unit) {
    val words = remember { WORD_BANK.shuffled().take(count) }
    var index by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val word = words[index.coerceAtMost(words.lastIndex)]
    val tiles = remember(index) { scramble(word) }
    var chosen by remember(index) { mutableStateOf(listOf<Int>()) }
    val currentDone by rememberUpdatedState(onDone)
    val currentProgress by rememberUpdatedState(onProgress)

    LaunchedEffect(finished) {
        if (finished) {
            delay(900)
            currentDone()
        }
    }

    fun add(tileIndex: Int) {
        if (finished) return
        val next = chosen + tileIndex
        chosen = next
        if (next.size == word.length) {
            val attempt = next.map { tiles[it] }.joinToString("")
            if (attempt == word) {
                message = ""
                currentProgress((index + 1) / words.size.toFloat())
                if (index + 1 >= words.size) finished = true else index++
            } else {
                message = "Chưa đúng, thử lại"
                chosen = emptyList()
            }
        }
    }

    ChallengeHeader("Xếp chữ", "Chạm các chữ cái để ghép thành một từ.")
    Text(
        "Từ ${(index + 1).coerceAtMost(words.size)}/${words.size}",
        style = MaterialTheme.typography.bodyMedium,
        color = Dawn.Lilac,
        modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (slot in 0 until word.length) {
            val tile = chosen.getOrNull(slot)
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(0.8f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = if (tile != null) 0.18f else 0.06f))
                    .clickable(enabled = tile != null) {
                        chosen = chosen.filterIndexed { i, _ -> i != slot }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (tile != null) {
                    Text(tiles[tile].toString(), fontSize = 24.sp, color = Color.White, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = Wrong,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = 8.dp).height(16.dp),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (ti in tiles.indices) {
            val used = ti in chosen
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(0.8f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (used) Color.White.copy(alpha = 0.05f) else Dawn.Gold)
                    .clickable(enabled = !used) { add(ti) },
                contentAlignment = Alignment.Center,
            ) {
                if (!used) {
                    Text(tiles[ti].toString(), fontSize = 24.sp, color = Dawn.Midnight, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

/** Bắt nắng: mặt trời nhỏ hiện lên ở một ô ngẫu nhiên rất nhanh. Chạm trúng được điểm, chạm nhầm ô thì mất điểm. */
@Composable
fun CatchChallengeSection(target: Int, visibleMs: Int, onProgress: (Float) -> Unit, onDone: () -> Unit) {
    var active by remember { mutableIntStateOf(-1) }
    var hits by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    var last by remember { mutableIntStateOf(-1) }
    val currentDone by rememberUpdatedState(onDone)
    val currentProgress by rememberUpdatedState(onProgress)

    LaunchedEffect(tick, finished) {
        if (finished) return@LaunchedEffect
        active = -1
        delay(260)
        var next = (0..8).random()
        while (next == last) next = (0..8).random()
        last = next
        active = next
        delay(visibleMs.toLong())
        tick++
    }
    LaunchedEffect(hits) {
        currentProgress((hits / target.toFloat()).coerceIn(0f, 1f))
        if (hits >= target && !finished) {
            finished = true
            active = -1
            delay(900)
            currentDone()
        }
    }

    fun tap(i: Int) {
        if (finished) return
        if (i == active) {
            hits++
            active = -1
            tick++
        } else {
            hits = (hits - 1).coerceAtLeast(0)
        }
    }

    ChallengeHeader("Bắt nắng", "Chạm vào mặt trời nhỏ trước khi nó biến mất.")
    Text(
        "${hits.coerceAtMost(target)}/$target",
        fontSize = 40.sp,
        color = Color.White,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(vertical = 12.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0 until 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0 until 3) {
                    val i = row * 3 + col
                    val on = i == active
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = if (on) 0.16f else 0.07f))
                            .clickable { tap(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) {
                            Box(Modifier.size(58.dp).clip(CircleShape).background(Dawn.Gold.copy(alpha = 0.35f)))
                            Box(Modifier.size(40.dp).clip(CircleShape).background(Dawn.Gold))
                            Box(Modifier.size(26.dp).clip(CircleShape).background(Dawn.GoldSoft))
                        }
                    }
                }
            }
        }
    }
}
