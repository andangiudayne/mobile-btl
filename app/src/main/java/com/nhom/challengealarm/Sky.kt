package com.nhom.challengealarm

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

private val DawnTop = Color(0xFF4B3F9E)
private val DawnBottom = Color(0xFFF59B8A)

/** progress 0 = đêm, 1 = bình minh. Dùng cho thẻ giờ báo (gần giờ hơn thì sáng hơn) và màn hình reo (giải xong bài thì sáng hơn). */
fun skyTop(progress: Float): Color = lerp(Dawn.Night, DawnTop, progress.coerceIn(0f, 1f))
fun skyBottom(progress: Float): Color = lerp(Dawn.Dusk, DawnBottom, progress.coerceIn(0f, 1f))

/**
 * Mặt trời mọc trên đường chân trời. rise = 0: ẩn dưới đường chân trời, rise = 1: nhô cao.
 * Vòng sáng quanh mặt trời thở chậm để màn hình không bị tĩnh khi chuông đang reo.
 */
@Composable
fun SunCanvas(modifier: Modifier = Modifier, rise: Float) {
    val glow by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse),
        label = "glowValue",
    )
    Canvas(modifier.clipToBounds()) {
        val r = minOf(size.width * 0.3f, size.height * 0.85f)
        val center = Offset(size.width / 2f, size.height + r * (1f - 1.8f * rise.coerceIn(0f, 1f)))
        drawCircle(Dawn.Gold.copy(alpha = 0.22f), radius = r * (1.7f + 0.15f * glow), center = center)
        drawCircle(Dawn.Gold, radius = r, center = center)
        drawCircle(Dawn.GoldSoft, radius = r * 0.72f, center = center)
        drawLine(
            color = Dawn.Horizon,
            start = Offset(0f, size.height - 1.5f),
            end = Offset(size.width, size.height - 1.5f),
            strokeWidth = 3f,
        )
    }
}

private val SkyStars = listOf(
    Triple(0.06f, 0.05f, 0.9f), Triple(0.17f, 0.17f, 0.4f), Triple(0.28f, 0.03f, 0.7f),
    Triple(0.41f, 0.11f, 0.3f), Triple(0.53f, 0.04f, 1.0f), Triple(0.62f, 0.15f, 0.5f),
    Triple(0.71f, 0.07f, 0.6f), Triple(0.84f, 0.02f, 0.8f), Triple(0.93f, 0.13f, 0.4f),
    Triple(0.12f, 0.29f, 0.3f), Triple(0.36f, 0.25f, 0.6f), Triple(0.58f, 0.31f, 0.4f),
    Triple(0.90f, 0.26f, 0.7f), Triple(0.22f, 0.40f, 0.5f), Triple(0.76f, 0.36f, 0.3f),
    Triple(0.46f, 0.43f, 0.8f), Triple(0.05f, 0.52f, 0.5f), Triple(0.96f, 0.47f, 0.6f),
)

/** Sao trên nửa trên của vùng vẽ. alpha = 1 là đêm, 0 là sáng hẳn. */
fun DrawScope.drawStars(alpha: Float) {
    if (alpha <= 0f) return
    SkyStars.forEach { (fx, fy, f) ->
        drawCircle(
            Color.White.copy(alpha = alpha * (0.3f + 0.6f * f)),
            radius = (0.7f + 1.1f * f).dp.toPx(),
            center = Offset(size.width * fx, size.height * fy),
        )
    }
}
