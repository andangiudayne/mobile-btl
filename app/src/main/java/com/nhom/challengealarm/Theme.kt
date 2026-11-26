package com.nhom.challengealarm

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

/** Bảng màu "bình minh" của màn hình chuông reo (bầu trời, mặt trời). */
object Dawn {
    val Midnight = Color(0xFF14183A)
    val Night = Color(0xFF1B2058)
    val Dusk = Color(0xFF2B2F73)
    val Gold = Color(0xFFFFB84D)
    val GoldSoft = Color(0xFFFFD27A)
    val Paper = Color(0xFFF3F4FB)
    val Mist = Color(0xFFE4E6F4)
    val Slate = Color(0xFF6B6F95)
    val Lilac = Color(0xFFC9CCF0)
    val Horizon = Color(0xFF4B519E)
}

/**
 * Hai chế độ giao diện:
 * DAY (6:00 đến 18:00): nền sáng xám xanh, các khối nổi bằng bóng mềm (neumorphism).
 * NIGHT (18:00 đến 6:00): nền tím đêm có sao, thẻ tím chuyển màu, nút trắng.
 */
enum class AppMode {
    DAY, NIGHT;

    companion object {
        const val DAY_START_HOUR = 6
        const val NIGHT_START_HOUR = 18

        fun byClock(now: Long = System.currentTimeMillis()): AppMode {
            val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
            return if (hour in DAY_START_HOUR until NIGHT_START_HOUR) DAY else NIGHT
        }
    }
}

/** Người dùng chọn: tự động theo giờ, luôn sáng hoặc luôn đêm (để thử cả hai giao diện). */
enum class ModeOverride { AUTO, DAY, NIGHT }

/** Màu riêng của từng chế độ, dùng cho các thành phần tự vẽ (thẻ, nút, biểu đồ, thanh điều hướng). */
@Immutable
data class AppPalette(
    val mode: AppMode,
    val bgTop: Color,
    val bgBottom: Color,
    val card: Color,
    val cardAlt: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val accent2: Color,
    val shadowLight: Color,
    val shadowDark: Color,
    val featured: List<Color>,
    val statA: List<Color>,
    val statB: List<Color>,
    val navBar: Color,
    val chartLine: Color,
    val chartFill: Color,
    val buttonBg: Color,
    val buttonText: Color,
    val track: Color,
) {
    val isNight: Boolean get() = mode == AppMode.NIGHT
}

val DayPalette = AppPalette(
    mode = AppMode.DAY,
    bgTop = Color(0xFFE9EDF5),
    bgBottom = Color(0xFFE2E7F0),
    card = Color(0xFFECF0F6),
    cardAlt = Color(0xFFDDE3ED),
    text = Color(0xFF3B4163),
    muted = Color(0xFF99A1B7),
    accent = Color(0xFF7B61FF),
    onAccent = Color.White,
    accent2 = Color(0xFFF2557B),
    shadowLight = Color(0xFFFFFFFF),
    shadowDark = Color(0xFFBAC3D3),
    featured = listOf(Color(0xFFECF0F6), Color(0xFFE4E9F2)),
    statA = listOf(Color(0xFF7B61FF), Color(0xFFB59CFF)),
    statB = listOf(Color(0xFFF2557B), Color(0xFFFF9BB3)),
    navBar = Color(0xFFECF0F6),
    chartLine = Color(0xFF7B61FF),
    chartFill = Color(0xFF7B61FF),
    buttonBg = Color(0xFFECF0F6),
    buttonText = Color(0xFF3B4163),
    track = Color(0xFFD6DCE8),
)

val NightPalette = AppPalette(
    mode = AppMode.NIGHT,
    bgTop = Color(0xFF2A1D70),
    bgBottom = Color(0xFF221763),
    card = Color(0xFF3A2B8E),
    cardAlt = Color(0xFF2F237A),
    text = Color.White,
    muted = Color(0xFFB8AEE6),
    accent = Color(0xFFB79CFF),
    onAccent = Color(0xFF221763),
    accent2 = Color(0xFFE48CFF),
    shadowLight = Color(0x00000000),
    shadowDark = Color(0x00000000),
    featured = listOf(Color(0xFF5E48C9), Color(0xFF4B39AE)),
    statA = listOf(Color(0xFFA244D2), Color(0xFF8B3BC6)),
    statB = listOf(Color(0xFF6A55C9), Color(0xFF5643B3)),
    navBar = Color(0xFF1F1559),
    chartLine = Color.White,
    chartFill = Color(0xFF9C86FF),
    buttonBg = Color.White,
    buttonText = Color(0xFF2A1D70),
    track = Color(0xFF241A63),
)

val LocalPalette = staticCompositionLocalOf { DayPalette }

/** Lối tắt đọc bảng màu hiện tại trong composable: AppTheme.palette.accent ... */
object AppTheme {
    val palette: AppPalette
        @Composable @ReadOnlyComposable get() = LocalPalette.current
}

private val DayScheme = lightColorScheme(
    primary = Color(0xFF7B61FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4DEFF),
    onPrimaryContainer = Color(0xFF2E2470),
    secondary = Color(0xFFF2557B),
    onSecondary = Color.White,
    background = DayPalette.bgTop,
    onBackground = DayPalette.text,
    surface = DayPalette.card,
    onSurface = DayPalette.text,
    surfaceVariant = DayPalette.cardAlt,
    onSurfaceVariant = Color(0xFF7D86A0),
    outline = Color(0xFFCBD2E0),
    error = Color(0xFFE5484D),
    errorContainer = Color(0xFFFFE1E3),
    onErrorContainer = Color(0xFF5A1117),
)

private val NightScheme = darkColorScheme(
    primary = Color(0xFFB79CFF),
    onPrimary = Color(0xFF221763),
    primaryContainer = Color(0xFF4B39AE),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFE48CFF),
    onSecondary = Color(0xFF221763),
    background = NightPalette.bgTop,
    onBackground = Color.White,
    surface = NightPalette.card,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF34277F),
    onSurfaceVariant = NightPalette.muted,
    outline = Color(0xFF4D3FA8),
    error = Color(0xFFFF8A93),
    errorContainer = Color(0xFF4A1F28),
    onErrorContainer = Color(0xFFFFD9DC),
)

private val Sans = FontFamily.SansSerif

/** Chữ số kiểu đồng hồ điện tử (ảnh mẫu ban ngày): chữ đơn cách, giãn chữ. */
val DigitalStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    letterSpacing = 2.sp,
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 64.sp, lineHeight = 68.sp, letterSpacing = (-2).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.8.sp,
    ),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Theme của app. Mặc định chọn chế độ theo giờ hiện tại; MainActivity truyền chế độ từ ThemeViewModel
 * (tự đổi lúc 6:00 và 18:00, hoặc theo lựa chọn của người dùng).
 */
@Composable
fun AlarmTheme(mode: AppMode = AppMode.byClock(), content: @Composable () -> Unit) {
    val palette = if (mode == AppMode.DAY) DayPalette else NightPalette
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = if (mode == AppMode.DAY) DayScheme else NightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
