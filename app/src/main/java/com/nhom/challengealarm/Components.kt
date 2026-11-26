package com.nhom.challengealarm

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Build
import java.util.Calendar

// =================================================================================================
// Thành phần giao diện dùng chung (tầng View). Không chứa logic, chỉ nhận dữ liệu và callback.
// Mỗi thành phần tự đổi kiểu theo chế độ:
//   Ngày  = neumorphism: khối cùng màu nền, nổi lên nhờ bóng sáng (trên trái) và bóng tối (dưới phải).
//   Đêm   = thẻ tím đặc / chuyển màu, có mảng tròn mờ trang trí, nút trắng chữ tím.
// =================================================================================================

internal val GUTTER = 20.dp
internal val CardShape = RoundedCornerShape(24.dp)

/**
 * Bóng neumorphism: vẽ khối bo góc màu fill với một bóng sáng lệch lên trên trái và một bóng tối lệch xuống dưới phải.
 * Dùng setShadowLayer của Android (Android 9 trở lên vẽ được bóng cho hình khối).
 */
fun Modifier.softShadow(
    fill: Color,
    light: Color,
    dark: Color,
    corner: Dp,
    elevation: Dp = 10.dp,
): Modifier = this.drawBehind {
    val r = corner.toPx()
    val blur = elevation.toPx()
    val off = blur * 0.55f
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        // Android 8: setShadowLayer không vẽ bóng cho hình khối, nên vẽ hai khối lệch thay cho bóng.
        val cr = CornerRadius(r, r)
        drawRoundRect(dark.copy(alpha = 0.55f), topLeft = Offset(off * 0.5f, off * 0.5f), size = size, cornerRadius = cr)
        drawRoundRect(light, topLeft = Offset(-off * 0.4f, -off * 0.4f), size = size, cornerRadius = cr)
        drawRoundRect(fill, size = size, cornerRadius = cr)
        return@drawBehind
    }
    drawIntoCanvas { canvas ->
        val paint = Paint()
        val fp = paint.asFrameworkPaint()
        fp.isAntiAlias = true
        fp.color = fill.toArgb()
        fp.setShadowLayer(blur, -off, -off, light.toArgb())
        canvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
        fp.setShadowLayer(blur, off, off, dark.toArgb())
        canvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
    }
}

/** Nền khối theo chế độ: ngày = nổi neumorphism, đêm = màu thẻ tím. */
@Composable
internal fun Modifier.surfaceBlock(corner: Dp, elevation: Dp = 10.dp, nightColor: Color? = null): Modifier {
    val p = AppTheme.palette
    return if (p.isNight) {
        this.clip(RoundedCornerShape(corner)).background(nightColor ?: p.card)
    } else {
        this.softShadow(p.card, p.shadowLight, p.shadowDark, corner, elevation).clip(RoundedCornerShape(corner))
    }
}

/** Mảng tròn mờ trang trí ở góc thẻ (giống các mảng hình hữu cơ trong ảnh mẫu ban đêm). */
internal fun Modifier.nightBlobs(alpha: Float = 0.07f): Modifier = this.drawBehind {
    drawCircle(Color.White.copy(alpha = alpha), radius = size.minDimension * 0.55f, center = Offset(size.width * 0.95f, size.height * 0.05f))
    drawCircle(Color.White.copy(alpha = alpha * 0.7f), radius = size.minDimension * 0.35f, center = Offset(size.width * 0.15f, size.height * 1.05f))
}

/** Thẻ nền chung. */
@Composable
internal fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = AppTheme.palette
    Column(
        modifier
            .fillMaxWidth()
            .surfaceBlock(24.dp)
            .then(if (p.isNight) Modifier.nightBlobs(0.05f) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/**
 * Nút bo tròn chính. Ngày: khối nổi neumorphism, chữ in hoa giãn chữ (nút "ADD" trong ảnh mẫu).
 * Đêm: nút trắng chữ tím có quầng sáng (nút "Start tracking" / "Stop tracking").
 */
@Composable
internal fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val p = AppTheme.palette
    val base = if (p.isNight) {
        modifier
            .height(52.dp)
            .shadow(14.dp, CircleShape, ambientColor = Color.White, spotColor = Color.White)
            .clip(CircleShape)
            .background(p.buttonBg)
    } else {
        modifier
            .height(52.dp)
            .softShadow(p.buttonBg, p.shadowLight, p.shadowDark, 26.dp, 12.dp)
            .clip(CircleShape)
    }
    Row(
        base.clickable(onClick = onClick).padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = p.buttonText, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            if (p.isNight) text else text.uppercase(),
            color = p.buttonText,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (p.isNight) 16.sp else 14.sp,
            letterSpacing = if (p.isNight) 0.sp else 1.5.sp,
        )
    }
}

/** Công tắc nhỏ mềm (công tắc trên thẻ chuông trong ảnh mẫu ban ngày). */
@Composable
internal fun SoftToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val knobX by animateDpAsState(if (checked) 22.dp else 3.dp, label = "toggle")
    val track by animateColorAsState(if (checked) p.accent else p.track, label = "toggleTrack")
    Box(
        modifier
            .size(width = 46.dp, height = 26.dp)
            .clip(CircleShape)
            .background(track)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Box(
            Modifier
                .offset(x = knobX, y = 3.dp)
                .size(20.dp)
                .shadow(3.dp, CircleShape)
                .background(if (p.isNight && !checked) p.muted else Color.White, CircleShape)
        )
    }
}

/** Nút đổi chế độ (tự động / sáng / đêm) và trạng thái hiện tại, cung cấp qua CompositionLocal cho mọi màn hình. */
data class ModeSwitch(val override: ModeOverride, val onCycle: () -> Unit)

val LocalModeSwitch = staticCompositionLocalOf<ModeSwitch?> { null }

@Composable
internal fun ModeButton() {
    val ms = LocalModeSwitch.current ?: return
    val p = AppTheme.palette
    val icon = when (ms.override) {
        ModeOverride.AUTO -> Icons.Default.BrightnessAuto
        ModeOverride.DAY -> Icons.Default.LightMode
        ModeOverride.NIGHT -> Icons.Default.DarkMode
    }
    val label = when (ms.override) {
        ModeOverride.AUTO -> "Giao diện: tự động theo giờ"
        ModeOverride.DAY -> "Giao diện: luôn sáng"
        ModeOverride.NIGHT -> "Giao diện: luôn đêm"
    }
    Box(
        Modifier
            .size(42.dp)
            .surfaceBlock(21.dp, elevation = 6.dp, nightColor = Color.White.copy(alpha = 0.1f))
            .clickable(onClick = ms.onCycle),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = p.text, modifier = Modifier.size(20.dp)) }
}

/** Tiêu đề lớn đầu mỗi tab, có thể kèm một hành động bên phải. Ban đêm tự thêm nút đổi chế độ. */
@Composable
internal fun ScreenTitle(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    val p = AppTheme.palette
    Row(
        Modifier.fillMaxWidth().padding(start = GUTTER, end = GUTTER, top = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = p.text)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = p.muted)
            }
        }
        action?.invoke()
        if (p.isNight) {
            Spacer(Modifier.width(8.dp))
            ModeButton()
        }
    }
}

/** Tiêu đề nhóm viết hoa giãn chữ ("STATISTICS ... See all" trong ảnh mẫu). */
@Composable
internal fun SectionHeader(text: String, trailing: String? = null, onTrailing: (() -> Unit)? = null) {
    val p = AppTheme.palette
    Row(
        Modifier.fillMaxWidth().padding(start = GUTTER, end = GUTTER, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            letterSpacing = 1.2.sp,
            color = if (p.isNight) p.text else p.muted,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.bodyMedium,
                color = p.muted,
                modifier = if (onTrailing != null) Modifier.clickable(onClick = onTrailing) else Modifier,
            )
        }
    }
}

/** Nhãn nhỏ bo tròn (ví dụ "3 màn"). */
@Composable
internal fun Pill(text: String, container: Color, content: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.clip(CircleShape).background(container).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Nhãn nhỏ theo bảng màu hiện tại. */
@Composable
internal fun SoftPill(text: String) {
    val p = AppTheme.palette
    Pill(text, if (p.isNight) Color.White.copy(alpha = 0.12f) else p.cardAlt, if (p.isNight) p.text else p.muted)
}

/** Ô số liệu nhỏ: số lớn, nhãn nhỏ bên dưới. */
@Composable
internal fun MetricTile(value: String, label: String, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    AppCard(modifier, padding = 16.dp) {
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light, fontSize = 24.sp),
            color = p.text,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = p.muted)
    }
}

/**
 * Thẻ thống kê có thanh tiến độ ("Quality 78 %" / "Duration 7h 50m" trong ảnh mẫu).
 * Đêm: nền chuyển màu (colors) có mảng tròn trang trí. Ngày: khối nổi, thanh tiến độ màu chuyển.
 */
@Composable
internal fun StatCard(
    title: String,
    value: String,
    progress: Float?,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val p = AppTheme.palette
    val shape = RoundedCornerShape(28.dp)
    val container = if (p.isNight) {
        modifier.clip(shape).background(Brush.linearGradient(colors)).nightBlobs(0.09f)
    } else {
        modifier.softShadow(p.card, p.shadowLight, p.shadowDark, 28.dp, 12.dp).clip(shape)
    }
    Column(container.padding(20.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = if (p.isNight) Color.White else p.muted)
        Spacer(Modifier.height(16.dp))
        Text(
            value,
            fontSize = 28.sp,
            fontWeight = FontWeight.Light,
            color = if (p.isNight) Color.White else p.text,
            maxLines = 1,
        )
        Spacer(Modifier.height(14.dp))
        val f = (progress ?: 0f).coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(if (p.isNight) Color(0xFF1E1458).copy(alpha = 0.75f) else p.track)
        ) {
            if (f > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(if (p.isNight) Brush.horizontalGradient(listOf(Color.White, Color.White)) else Brush.horizontalGradient(colors))
                )
            }
        }
    }
}

/** Trạng thái trống: biểu tượng, tiêu đề, giải thích và (tùy chọn) một nút. */
@Composable
internal fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val p = AppTheme.palette
    Column(
        Modifier.fillMaxWidth().padding(horizontal = GUTTER, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(76.dp).surfaceBlock(38.dp, nightColor = Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = p.accent, modifier = Modifier.size(36.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = p.text, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = p.muted, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(8.dp))
            PillButton(actionLabel, onAction)
        }
    }
}

/** Điều khiển phân đoạn (ví dụ Dễ / Vừa / Khó). */
@Composable
internal fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().clip(CircleShape).background(cs.surfaceVariant).padding(4.dp)
    ) {
        options.forEachIndexed { i, text ->
            val bg by animateColorAsState(if (i == selected) cs.surface else Color.Transparent, label = "segment")
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (i == selected) cs.onSurface else cs.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Biểu tượng và màu cho từng loại thử thách
// ---------------------------------------------------------------------------------------------

internal fun iconFor(kind: ChallengeType): ImageVector = when (kind) {
    ChallengeType.MATH -> Icons.Default.Calculate
    ChallengeType.QR -> Icons.Default.QrCodeScanner
    ChallengeType.PHOTO -> Icons.Default.CameraAlt
    ChallengeType.SHAKE -> Icons.Default.Vibration
    ChallengeType.STEPS -> Icons.AutoMirrored.Filled.DirectionsWalk
    ChallengeType.TYPING -> Icons.Default.Keyboard
    ChallengeType.MEMORY -> Icons.Default.Psychology
    ChallengeType.ORDER -> Icons.Default.GridView
    ChallengeType.RHYTHM -> Icons.Default.MusicNote
    ChallengeType.PATTERN -> Icons.Default.Gesture
    ChallengeType.WORDS -> Icons.Default.SortByAlpha
    ChallengeType.CATCH -> Icons.Default.WbSunny
    ChallengeType.STROOP -> Icons.Default.Palette
    ChallengeType.PAIRS -> Icons.Default.Style
    ChallengeType.LIGHT -> Icons.Default.LightMode
    ChallengeType.ARROWS -> Icons.Default.OpenWith
    ChallengeType.ODD -> Icons.Default.Visibility
    ChallengeType.DIGITS -> Icons.Default.Dialpad
    ChallengeType.SLIDE -> Icons.Default.Extension
    ChallengeType.TILT -> Icons.Default.ScreenRotation
    ChallengeType.COMPARE -> Icons.Default.SwapHoriz
    ChallengeType.CLOCK -> Icons.Default.AccessTime
}

/** Cặp màu nền nhạt / màu biểu tượng đậm cho huy hiệu thử thách. */
internal fun badgeColors(kind: ChallengeType): Pair<Color, Color> = when (kind) {
    ChallengeType.MATH -> Color(0xFFFFE9C2) to Color(0xFF8A5A00)
    ChallengeType.QR -> Color(0xFFD5F2EA) to Color(0xFF0F6E56)
    ChallengeType.PHOTO -> Color(0xFFFBE0EA) to Color(0xFF993556)
    ChallengeType.SHAKE -> Color(0xFFE3E0FF) to Color(0xFF4B3F9E)
    ChallengeType.STEPS -> Color(0xFFDDF3D3) to Color(0xFF2F6B1A)
    ChallengeType.TYPING -> Color(0xFFD9E8FB) to Color(0xFF1F5C99)
    ChallengeType.MEMORY -> Color(0xFFFFE0D8) to Color(0xFFA23F25)
    ChallengeType.ORDER -> Color(0xFFE1E5F2) to Color(0xFF3C4271)
    ChallengeType.RHYTHM -> Color(0xFFFFE6F0) to Color(0xFF9B2C5E)
    ChallengeType.PATTERN -> Color(0xFFDDF1F7) to Color(0xFF16627D)
    ChallengeType.WORDS -> Color(0xFFEFE3FF) to Color(0xFF6A3FB0)
    ChallengeType.CATCH -> Color(0xFFFFF0C2) to Color(0xFF8F6A00)
    ChallengeType.STROOP -> Color(0xFFFFE3E3) to Color(0xFFB3261E)
    ChallengeType.PAIRS -> Color(0xFFE6F4FF) to Color(0xFF0B5CAD)
    ChallengeType.LIGHT -> Color(0xFFFFF6D6) to Color(0xFF9A6B00)
    ChallengeType.ARROWS -> Color(0xFFE4F7EF) to Color(0xFF1C6B4A)
    ChallengeType.ODD -> Color(0xFFFCE7F3) to Color(0xFF9D174D)
    ChallengeType.DIGITS -> Color(0xFFE0F2FE) to Color(0xFF075985)
    ChallengeType.SLIDE -> Color(0xFFFEF3C7) to Color(0xFF92400E)
    ChallengeType.TILT -> Color(0xFFDCFCE7) to Color(0xFF166534)
    ChallengeType.COMPARE -> Color(0xFFEDE9FE) to Color(0xFF5B21B6)
    ChallengeType.CLOCK -> Color(0xFFFFEDD5) to Color(0xFF9A3412)
}

/** Huy hiệu vuông bo góc chứa biểu tượng thử thách. */
@Composable
internal fun IconBadge(kind: ChallengeType, size: Dp = 44.dp) {
    val (bg, fg) = badgeColors(kind)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(iconFor(kind), contentDescription = null, tint = fg, modifier = Modifier.size(size * 0.55f))
    }
}

/** Một dòng chọn thử thách trong trình sửa chuông: có dấu tích và viền vàng khi được chọn. */
@Composable
internal fun ChallengeOption(kind: ChallengeType, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) cs.primaryContainer else cs.surface)
            .border(BorderStroke(if (selected) 1.5.dp else 0.dp, if (selected) AppTheme.palette.accent else Color.Transparent), shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(kind)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Default.Check, contentDescription = "Đã chọn", tint = cs.primary)
    }
}

// ---------------------------------------------------------------------------------------------
// Định dạng chữ dùng chung
// ---------------------------------------------------------------------------------------------

internal fun formatDuration(seconds: Long): String =
    if (seconds < 60) "$seconds giây" else "${seconds / 60} phút ${seconds % 60} giây"

/** Gọn cho ô số liệu: "45 giây", "2:35 phút". */
internal fun formatShortDuration(seconds: Long): String =
    if (seconds < 60) "$seconds giây" else "%d:%02d phút".format(seconds / 60, seconds % 60)

/** "7h 50m" cho thẻ Thời lượng. */
internal fun hoursMinutes(min: Int): String = "${min / 60}h ${"%02d".format(min % 60)}m"

internal fun formatLeft(minutes: Long): String = when {
    minutes < 1 -> "reo ngay bây giờ"
    minutes < 60 -> "còn $minutes phút"
    minutes < 24 * 60 -> "còn ${minutes / 60} giờ ${minutes % 60} phút"
    else -> "còn ${minutes / (24 * 60)} ngày ${(minutes % (24 * 60)) / 60} giờ"
}

/** "T2", "T3"... "CN" cho một thời điểm. */
internal fun weekdayShort(t: Long): String {
    val dow = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.DAY_OF_WEEK)
    return DAY_SHORT[(dow + 5) % 7]
}

/** "Thứ hai"... "Chủ nhật" cho một thời điểm. */
internal fun weekdayFull(t: Long): String {
    val dow = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.DAY_OF_WEEK)
    return DAY_FULL[(dow + 5) % 7]
}

internal fun methodName(method: String): String = when (method) {
    WakeMethod.MATH -> "giải toán"
    WakeMethod.QR -> "quét mã"
    WakeMethod.PHOTO -> "chụp vật"
    WakeMethod.SHAKE -> "lắc điện thoại"
    WakeMethod.STEPS -> "đi bộ"
    WakeMethod.TYPING -> "gõ lại câu"
    WakeMethod.MEMORY -> "nhớ dãy màu"
    WakeMethod.ORDER -> "chạm số theo thứ tự"
    WakeMethod.RHYTHM -> "bắt nhịp"
    WakeMethod.PATTERN -> "vẽ lại hình"
    WakeMethod.WORDS -> "xếp chữ"
    WakeMethod.CATCH -> "bắt nắng"
    WakeMethod.STROOP -> "màu chữ"
    WakeMethod.PAIRS -> "lật cặp"
    WakeMethod.LIGHT -> "bật đèn"
    WakeMethod.ARROWS -> "vuốt mũi tên"
    WakeMethod.ODD -> "tìm ô khác màu"
    WakeMethod.DIGITS -> "nhớ dãy số"
    WakeMethod.SLIDE -> "xếp hình trượt"
    WakeMethod.TILT -> "giữ thăng bằng"
    WakeMethod.COMPARE -> "so sánh nhanh"
    WakeMethod.CLOCK -> "đọc đồng hồ"
    WakeMethod.AWAKE -> "nút \"Bạn đã tỉnh\""
    else -> "thoát khẩn cấp"
}

/** Loại thử thách của một id trong chuông (có sẵn hoặc trong thư viện), null nếu không còn. */
internal fun challengeKindOf(id: Int, library: List<ChallengeItem>): ChallengeType? =
    if (id <= 0) BuiltIn.byId(id)?.type else library.firstOrNull { it.id == id }?.kind
