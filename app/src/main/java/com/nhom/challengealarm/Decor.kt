package com.nhom.challengealarm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlinx.coroutines.delay

// =================================================================================================
// Phần vẽ trang trí theo hai ảnh mẫu: đồng hồ kim neumorphism (ban ngày), bầu trời sao, trăng và mây,
// biểu đồ sóng, thẻ nổi bật, thanh tab trên (ban ngày) và thanh điều hướng dưới (ban đêm).
// Tất cả vẽ bằng Canvas, không dùng ảnh nên nhẹ và sắc nét ở mọi kích thước màn hình.
// =================================================================================================

internal fun hhmm(t: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))

/** Vị trí sao trên nền đêm (tỉ lệ x, y, độ sáng). Cố định để sao không nhảy khi vẽ lại. */
private val NIGHT_STARS = listOf(
    Triple(0.08f, 0.04f, 0.7f), Triple(0.22f, 0.11f, 0.3f), Triple(0.37f, 0.03f, 0.9f), Triple(0.55f, 0.08f, 0.4f),
    Triple(0.71f, 0.02f, 0.6f), Triple(0.88f, 0.10f, 0.8f), Triple(0.95f, 0.22f, 0.3f), Triple(0.13f, 0.27f, 0.5f),
    Triple(0.47f, 0.19f, 0.3f), Triple(0.64f, 0.29f, 0.6f), Triple(0.83f, 0.36f, 0.4f), Triple(0.04f, 0.41f, 0.3f),
    Triple(0.29f, 0.46f, 0.7f), Triple(0.58f, 0.52f, 0.3f), Triple(0.92f, 0.57f, 0.6f), Triple(0.18f, 0.63f, 0.4f),
    Triple(0.42f, 0.71f, 0.5f), Triple(0.75f, 0.69f, 0.3f), Triple(0.07f, 0.82f, 0.6f), Triple(0.34f, 0.88f, 0.3f),
    Triple(0.66f, 0.85f, 0.7f), Triple(0.9f, 0.93f, 0.4f), Triple(0.52f, 0.96f, 0.5f), Triple(0.26f, 0.35f, 0.2f),
)

/** Nền của app theo chế độ. Đêm: tím chuyển màu, mảng tròn mờ và sao. Ngày: xám xanh nhạt. */
@Composable
internal fun Modifier.appBackground(): Modifier {
    val p = AppTheme.palette
    val base = this.background(Brush.verticalGradient(listOf(p.bgTop, p.bgBottom)))
    return if (p.isNight) base.drawBehind { drawNightSky() } else base
}

internal fun DrawScope.drawNightSky(starAlpha: Float = 1f) {
    drawCircle(Color.White.copy(alpha = 0.035f), radius = size.width * 0.6f, center = Offset(size.width * 0.05f, size.height * 0.06f))
    drawCircle(Color.White.copy(alpha = 0.03f), radius = size.width * 0.5f, center = Offset(size.width * 1.05f, size.height * 0.62f))
    drawCircle(Color.White.copy(alpha = 0.025f), radius = size.width * 0.7f, center = Offset(size.width * 0.3f, size.height * 1.08f))
    NIGHT_STARS.forEach { (fx, fy, f) ->
        drawCircle(
            Color.White.copy(alpha = (0.25f + 0.55f * f) * starAlpha),
            radius = (0.8f + 1.3f * f).dp.toPx(),
            center = Offset(size.width * fx, size.height * fy),
        )
    }
}

private fun polar(c: Offset, r: Float, degrees: Float): Offset {
    val a = Math.toRadians(degrees.toDouble())
    return Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
}

/**
 * Đồng hồ kim neumorphism (ảnh mẫu ban ngày): mặt nổi có vạch phút, mặt trong lõm, số 12/3/6/9 kiểu điện tử,
 * kim giờ đậm, kim phút hồng, tâm tím. Chạy theo giờ thật, cập nhật mỗi giây.
 */
@Composable
internal fun AnalogClock(modifier: Modifier = Modifier, diameter: Dp = 250.dp) {
    val p = AppTheme.palette
    val time by produceState(Calendar.getInstance()) {
        while (true) {
            value = Calendar.getInstance()
            delay(1_000)
        }
    }
    val hour = time.get(Calendar.HOUR)
    val minute = time.get(Calendar.MINUTE)
    val second = time.get(Calendar.SECOND)

    Box(
        modifier.size(diameter).softShadow(p.card, p.shadowLight, p.shadowDark, diameter / 2, 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f
            for (i in 0 until 60) {
                val long = i % 5 == 0
                drawLine(
                    p.muted.copy(alpha = if (long) 0.8f else 0.4f),
                    polar(c, r * (if (long) 0.79f else 0.82f), i * 6f - 90f),
                    polar(c, r * 0.87f, i * 6f - 90f),
                    strokeWidth = (if (long) 2.dp else 1.dp).toPx(),
                    cap = StrokeCap.Round,
                )
            }
            // Mặt trong lõm: tối ở trên trái, sáng ở dưới phải.
            drawCircle(
                Brush.linearGradient(
                    listOf(p.shadowDark.copy(alpha = 0.55f), p.cardAlt, p.shadowLight),
                    start = Offset(c.x - r * 0.7f, c.y - r * 0.7f),
                    end = Offset(c.x + r * 0.7f, c.y + r * 0.7f),
                ),
                radius = r * 0.7f,
                center = c,
            )
            drawCircle(p.cardAlt, radius = r * 0.665f, center = c)

            val hourAngle = (hour + minute / 60f) * 30f - 90f
            val minuteAngle = (minute + second / 60f) * 6f - 90f
            drawLine(p.text, c, polar(c, r * 0.34f, hourAngle), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
            drawLine(p.accent2, c, polar(c, r * 0.5f, minuteAngle), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            drawLine(
                p.accent2.copy(alpha = 0.45f),
                c,
                polar(c, r * 0.56f, second * 6f - 90f),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(p.accent, radius = 9.dp.toPx(), center = c)
            drawCircle(Color.White.copy(alpha = 0.7f), radius = 3.dp.toPx(), center = c)
        }
        val numStyle = DigitalStyle.copy(fontSize = 18.sp, color = p.text)
        Text("12", style = numStyle, modifier = Modifier.align(Alignment.TopCenter).padding(top = diameter * 0.2f))
        Text("6", style = numStyle, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = diameter * 0.2f))
        Text("9", style = numStyle, modifier = Modifier.align(Alignment.CenterStart).padding(start = diameter * 0.22f))
        Text("3", style = numStyle, modifier = Modifier.align(Alignment.CenterEnd).padding(end = diameter * 0.22f))
    }
}

private fun starPath(c: Offset, outer: Float, inner: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val pt = polar(c, r, -90f + i * 36f)
        if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
    }
    close()
}

/**
 * Biểu đồ sóng theo tuần ("Weekly sleep" trong ảnh mẫu): đường cong mượt qua các điểm, phủ màu nhạt bên dưới,
 * vạch ngang theo giờ (6h, 7h, 8h...), ngôi sao đánh dấu điểm nổi bật. values tính bằng giờ, null = không có dữ liệu.
 */
@Composable
internal fun WaveChart(
    values: List<Float?>,
    labels: List<String>,
    minHours: Float,
    maxHours: Float,
    modifier: Modifier = Modifier,
    unit: String = "h",
) {
    val p = AppTheme.palette
    val known = values.filterNotNull()
    val lo = floor(minOf(minHours - 1f, known.minOrNull() ?: minHours)).coerceAtLeast(0f)
    val hi = ceil(maxOf(maxHours, known.maxOrNull() ?: maxHours)).coerceAtLeast(lo + 1f)
    // Tối đa khoảng 4 vạch ngang dù khoảng giá trị rộng hay hẹp.
    val step = maxOf(1f, ceil((hi - lo) / 4f))
    val grid = generateSequence(lo) { it + step }.takeWhile { it <= hi + 0.001f }.toList()
    // Ngày thiếu dữ liệu: lấy trung bình hai bên (hoặc mức thấp nhất) để đường cong liền mạch.
    val filled = values.mapIndexed { i, v ->
        v ?: run {
            val before = values.take(i).lastOrNull { it != null }
            val after = values.drop(i + 1).firstOrNull { it != null }
            listOfNotNull(before, after).takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: lo
        }
    }
    val highlight = values.indexOfLast { it != null }
    val chartHeight = 160.dp
    val pad = 14.dp
    val labelWidth = 32.dp
    fun frac(h: Float) = 1f - (h - lo) / (hi - lo)

    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(chartHeight)) {
            grid.forEach { g ->
                Text(
                    "${g.toInt()}$unit",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.muted.copy(alpha = 0.8f),
                    modifier = Modifier.offset(y = pad + (chartHeight - pad * 2) * frac(g) - 8.dp),
                )
            }
            Canvas(Modifier.fillMaxSize().padding(start = labelWidth)) {
                val top = pad.toPx()
                val h = size.height - top * 2
                fun y(v: Float) = top + h * frac(v)
                grid.forEach { g ->
                    drawLine(p.muted.copy(alpha = 0.25f), Offset(0f, y(g)), Offset(size.width, y(g)), strokeWidth = 1.dp.toPx())
                }
                if (p.isNight) {
                    NIGHT_STARS.take(10).forEach { (fx, fy, f) ->
                        drawCircle(Color.White.copy(alpha = 0.2f + 0.4f * f), (0.8f + f).dp.toPx(), Offset(size.width * fx, size.height * fy))
                    }
                }
                if (filled.isEmpty()) return@Canvas
                val n = filled.size
                val pts = filled.mapIndexed { i, v -> Offset(size.width * (i + 0.5f) / n, y(v)) }
                val line = Path().apply {
                    moveTo(0f, pts.first().y)
                    lineTo(pts.first().x, pts.first().y)
                    for (i in 1 until pts.size) {
                        val a = pts[i - 1]
                        val b = pts[i]
                        val cx = (a.x + b.x) / 2f
                        cubicTo(cx, a.y, cx, b.y, b.x, b.y)
                    }
                    lineTo(size.width, pts.last().y)
                }
                val area = Path().apply {
                    addPath(line)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(
                    area,
                    Brush.verticalGradient(listOf(p.chartFill.copy(alpha = 0.42f), p.chartFill.copy(alpha = 0f))),
                )
                drawPath(line, p.chartLine, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                if (highlight >= 0) {
                    val pt = pts[highlight]
                    drawCircle(p.chartLine.copy(alpha = 0.25f), radius = 12.dp.toPx(), center = pt)
                    drawPath(starPath(pt, 8.dp.toPx(), 3.5.dp.toPx()), if (p.isNight) Color.White else p.accent2)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = labelWidth, top = 6.dp)) {
            labels.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = p.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Thẻ nổi bật ("Monday / Sleep time ... / Start tracking" trong ảnh mẫu ban đêm).
 * Đêm: nền tím chuyển màu, mảng tròn mờ và hai nét lá trang trí ở góc. Ngày: khối nổi neumorphism.
 */
@Composable
internal fun FeatureCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    buttonText: String? = null,
    onButton: (() -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    val p = AppTheme.palette
    val shape = RoundedCornerShape(30.dp)
    val container = if (p.isNight) {
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(p.featured))
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.07f), radius = size.height * 0.75f, center = Offset(size.width * 0.92f, size.height * 0.95f))
                drawCircle(Color.White.copy(alpha = 0.05f), radius = size.height * 0.45f, center = Offset(size.width * 0.7f, 0f))
                val leaf = Color.White.copy(alpha = 0.16f)
                val w = 14.dp.toPx()
                drawLine(leaf, Offset(size.width * 0.05f, size.height * 1.02f), Offset(size.width * 0.12f, size.height * 0.62f), w, StrokeCap.Round)
                drawLine(leaf, Offset(size.width * 0.11f, size.height * 1.05f), Offset(size.width * 0.2f, size.height * 0.7f), w, StrokeCap.Round)
                NIGHT_STARS.take(6).forEach { (fx, fy, f) ->
                    drawCircle(Color.White.copy(alpha = 0.25f + 0.4f * f), (0.8f + f).dp.toPx(), Offset(size.width * fx, size.height * fy))
                }
            }
    } else {
        modifier.fillMaxWidth().softShadow(p.card, p.shadowLight, p.shadowDark, 30.dp, 14.dp).clip(shape)
    }
    Column(container.padding(24.dp)) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = p.text)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = if (p.isNight) Color.White.copy(alpha = 0.78f) else p.muted)
        extra?.invoke()
        if (buttonText != null && onButton != null) {
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { PillButton(buttonText, onButton) }
        }
    }
}

/** Thanh tab ở trên (ảnh mẫu ban ngày): tab đang chọn là khối nổi, các tab khác chữ mờ; bên phải là nút đổi chế độ. */
@Composable
internal fun DayTabBar(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = AppTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.bgTop)
            .statusBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 5 tab thì chữ nhỏ lại một chút để không bị cắt trên màn hình hẹp.
        val many = labels.size > 4
        Row(Modifier.weight(1f).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = if (many) 1.dp else 3.dp)
                        .then(if (on) Modifier.softShadow(p.card, p.shadowLight, p.shadowDark, 22.dp, 8.dp) else Modifier)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = if (many) 11.sp else 13.sp,
                        color = if (on) p.text else p.muted,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        ModeButton()
    }
}

/** Thanh điều hướng dưới (ảnh mẫu ban đêm): nền tím đậm bo góc trên, chỉ có biểu tượng, mục đang chọn sáng trắng. */
@Composable
internal fun NightNavBar(icons: List<ImageVector>, labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = AppTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(p.navBar)
            .navigationBarsPadding()
            .height(72.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icons.forEachIndexed { i, icon ->
            val on = i == selected
            Column(
                Modifier.size(60.dp).clip(CircleShape).clickable { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = labels.getOrNull(i),
                    tint = if (on) Color.White else p.muted.copy(alpha = 0.55f),
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.height(5.dp))
                Box(Modifier.size(5.dp).clip(CircleShape).background(if (on) Color.White else Color.Transparent))
            }
        }
    }
}

private fun DrawScope.cloud(c: Offset, s: Float, alpha: Float = 1f) {
    val col = Color.White.copy(alpha = alpha)
    drawCircle(col, s * 0.85f, Offset(c.x - s * 0.95f, c.y + s * 0.2f))
    drawCircle(col, s * 1.2f, Offset(c.x, c.y - s * 0.15f))
    drawCircle(col, s * 0.9f, Offset(c.x + s * 1.0f, c.y + s * 0.15f))
    drawRoundRect(
        col,
        topLeft = Offset(c.x - s * 1.75f, c.y + s * 0.05f),
        size = Size(s * 3.6f, s * 1.0f),
        cornerRadius = CornerRadius(s * 0.5f),
    )
}

/** Trăng lưỡi liềm, các vòng mảnh và mây (ảnh mẫu "Good night"), ở giữa là giờ hiện tại. */
@Composable
internal fun MoonScene(timeText: String, modifier: Modifier = Modifier, sceneSize: Dp = 300.dp) {
    Box(modifier.size(sceneSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension * 0.3f
            for (k in 0..2) {
                drawCircle(
                    Color.White.copy(alpha = 0.22f - k * 0.06f),
                    radius = r * 1.12f + k * 5.dp.toPx(),
                    center = Offset(c.x + k * 3.dp.toPx(), c.y - k * 2.dp.toPx()),
                    style = Stroke(1.dp.toPx()),
                )
            }
            val moonCenter = Offset(c.x - r * 0.12f, c.y)
            val outer = Path().apply { addOval(Rect(moonCenter, r)) }
            val inner = Path().apply { addOval(Rect(Offset(moonCenter.x + r * 0.5f, moonCenter.y - r * 0.22f), r * 0.9f)) }
            val crescent = Path().apply { op(outer, inner, PathOperation.Difference) }
            drawPath(
                crescent,
                Brush.linearGradient(
                    listOf(Color.White, Color(0xFFE4DBFF)),
                    start = Offset(moonCenter.x - r, moonCenter.y - r),
                    end = Offset(moonCenter.x + r, moonCenter.y + r),
                ),
            )
            cloud(Offset(c.x + r * 0.85f, c.y + r * 0.78f), r * 0.36f)
            cloud(Offset(c.x - r * 1.05f, c.y + r * 0.12f), r * 0.2f, alpha = 0.9f)
            cloud(Offset(c.x + r * 1.3f, c.y - r * 0.42f), r * 0.18f)
        }
        Text(
            timeText,
            fontSize = 30.sp,
            fontWeight = FontWeight.Light,
            color = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.offset(x = 22.dp),
        )
    }
}

/**
 * Màn hình "Chúc ngủ ngon" (ảnh mẫu ban đêm, màn bên phải): trăng và mây, giờ hiện tại, giờ chuông,
 * "Kiểm tra trước khi ngủ" (pin, Không làm phiền, có chuông sáng mai chưa, quyền), âm thanh ru ngủ có hẹn giờ,
 * thời gian đã nằm và nút Dừng. Luôn dùng giao diện đêm, kể cả khi mở ban ngày.
 */
@Composable
fun GoodNightScreen(
    nextWake: Long?,
    checks: List<NightCheckItem>,
    noise: NoisePlayback?,
    onFix: (String) -> Unit,
    onPlayNoise: (NoiseType, Int) -> Unit,
    onStopNoise: () -> Unit,
    onStop: () -> Unit,
) {
    CompositionLocalProvider(LocalPalette provides NightPalette) {
        val p = NightPalette
        val start = remember { System.currentTimeMillis() }
        val now by produceState(System.currentTimeMillis()) {
            while (true) {
                value = System.currentTimeMillis()
                delay(1_000)
            }
        }
        val elapsedMin = ((now - start) / 60_000L).coerceAtLeast(0)
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(p.bgTop, p.bgBottom)))
                .drawBehind { drawNightSky() }
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(12.dp))
                Text("Chúc ngủ ngon", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(
                    nextWake?.let { "Chuông ${hhmm(it)}" } ?: "Chưa bật chuông nào",
                    style = MaterialTheme.typography.bodyLarge,
                    color = p.muted,
                )
                MoonScene(hhmm(now), sceneSize = 230.dp)
                ReadyCard(checks, onFix)
                Spacer(Modifier.height(12.dp))
                SoundCard(noise, onPlayNoise, onStopNoise)
                Spacer(Modifier.height(20.dp))
                Text("Đã nằm", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(
                    "%02d:%02d".format(elapsedMin / 60, elapsedMin % 60),
                    fontSize = 20.sp,
                    color = Color.White,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .padding(horizontal = 30.dp, vertical = 10.dp),
                )
                Spacer(Modifier.height(22.dp))
                PillButton("Dừng", onStop, Modifier.fillMaxWidth(0.78f))
                Spacer(Modifier.height(12.dp))
                Text(
                    "Úp điện thoại và tắt màn hình. App tự tính giờ ngủ từ lúc màn hình tắt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private val GoodColor = Color(0xFF8CF0C0)
private val WarnColor = Color(0xFFFFC56B)
private val BadColor = Color(0xFFFF8A93)

/** "Kiểm tra trước khi ngủ": ổn hết thì một dòng tóm tắt, có vấn đề thì liệt kê kèm nút sửa. */
@Composable
private fun ReadyCard(checks: List<NightCheckItem>, onFix: (String) -> Unit) {
    val p = AppTheme.palette
    val problems = NightCheck.problems(checks)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (problems.isEmpty()) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                contentDescription = null,
                tint = if (problems.isEmpty()) GoodColor else WarnColor,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    checks.isEmpty() -> "Đang kiểm tra..."
                    problems.isEmpty() -> "Sẵn sàng cho sáng mai"
                    else -> "Cần xem lại ${problems.size} mục"
                },
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
        }
        if (problems.isEmpty()) {
            if (checks.isNotEmpty()) {
                Text(
                    checks.joinToString(" · ") { it.title },
                    style = MaterialTheme.typography.bodySmall,
                    color = p.muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        } else {
            problems.forEach { item ->
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (item.level == CheckLevel.BAD) BadColor else WarnColor)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text(item.detail, style = MaterialTheme.typography.bodySmall, color = p.muted)
                    }
                    item.fixLabel?.let { label ->
                        TextButton(onClick = { onFix(item.id) }) { Text(label, color = Color.White) }
                    }
                }
            }
        }
    }
}

/** Âm thanh ru ngủ: chọn loại (chạm lần nữa để tắt) và hẹn giờ tắt. */
@Composable
private fun SoundCard(noise: NoisePlayback?, onPlay: (NoiseType, Int) -> Unit, onStop: () -> Unit) {
    val p = AppTheme.palette
    var minutes by remember { mutableIntStateOf(NoiseService.DEFAULT_MINUTES) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Âm thanh ru ngủ", style = MaterialTheme.typography.titleSmall, color = Color.White, modifier = Modifier.weight(1f))
            if (noise != null) {
                Text("tự tắt lúc ${hhmm(noise.endsAt)}", style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NoiseType.values().forEach { t ->
                val on = noise?.type == t
                NightChip(t.label, on, Modifier.weight(1f)) { if (on) onStop() else onPlay(t, minutes) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Hẹn giờ", style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.weight(1f))
            NoiseService.TIMER_OPTIONS.forEach { m ->
                NightChip("$m phút", m == minutes) {
                    minutes = m
                    noise?.let { onPlay(it.type, m) } // đang phát thì hẹn lại giờ tắt
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            noise?.let { "${it.type.hint}. Chạm lần nữa để tắt." }
                ?: "Tiếng ồn đều che bớt tiếng động bất chợt (xe, nói chuyện), giúp dễ vào giấc. Tự nhỏ dần rồi tắt.",
            style = MaterialTheme.typography.bodySmall,
            color = p.muted,
        )
    }
}

@Composable
private fun NightChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(CircleShape)
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) NightPalette.buttonText else Color.White,
            maxLines = 1,
        )
    }
}
