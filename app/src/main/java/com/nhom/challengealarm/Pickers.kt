package com.nhom.challengealarm

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged

private const val WHEEL_REPEATS = 400

/**
 * Bánh xe cuộn kiểu iOS: cuộn vô hạn, tự căn mục gần nhất vào giữa, mục càng xa giữa càng mờ,
 * nhỏ và nghiêng như mặt trống. Mỗi lần qua một số sẽ rung nhẹ.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    count: Int,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 48.dp,
    visibleCount: Int = 5,
    format: (Int) -> String = { it.toString() },
) {
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val startIndex = count * (WHEEL_REPEATS / 2) + selected
    val state = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val haptic = LocalHapticFeedback.current
    val currentOnSelected by rememberUpdatedState(onSelected)
    val textColor = MaterialTheme.colorScheme.onSurface

    // Mục đang nằm giữa = vị trí cuộn làm tròn (mục k nằm giữa khi cuộn đúng k đơn vị).
    LaunchedEffect(state, count) {
        var first = true
        snapshotFlow {
            (state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / itemHeightPx).roundToInt()
        }.distinctUntilChanged().collect { index ->
            currentOnSelected(index % count)
            if (!first) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            first = false
        }
    }

    LazyColumn(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(vertical = itemHeight * (visibleCount / 2)),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.height(itemHeight * visibleCount),
    ) {
        items(count * WHEEL_REPEATS) { index ->
            Box(
                Modifier
                    .height(itemHeight)
                    .fillMaxWidth()
                    .graphicsLayer {
                        // d = khoảng cách (tính bằng số mục) từ mục này tới tâm bánh xe
                        val d = index - state.firstVisibleItemIndex - state.firstVisibleItemScrollOffset / itemHeightPx
                        val a = abs(d)
                        alpha = (1f - 0.3f * a).coerceIn(0.12f, 1f)
                        val s = (1f - 0.1f * a).coerceIn(0.7f, 1f)
                        scaleX = s
                        scaleY = s
                        rotationX = (d * -16f).coerceIn(-60f, 60f)
                        cameraDistance = 14f * this.density
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    format(index % count),
                    style = MaterialTheme.typography.headlineMedium,
                    color = textColor,
                )
            }
        }
    }
}

/** Hai bánh xe giờ (00 đến 23) và phút (00 đến 59) có dải sáng ở giữa, giống bộ chọn giờ của iOS. */
@Composable
fun TimeWheel(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(cs.surfaceVariant)
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            WheelPicker(
                count = 24,
                selected = hour,
                onSelected = onHourChange,
                modifier = Modifier.width(96.dp),
                format = { "%02d".format(it) },
            )
            Text(
                ":",
                style = MaterialTheme.typography.headlineMedium,
                color = cs.onSurface,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            WheelPicker(
                count = 60,
                selected = minute,
                onSelected = onMinuteChange,
                modifier = Modifier.width(96.dp),
                format = { "%02d".format(it) },
            )
        }
    }
}

/** Nhóm các dòng cài đặt trong một khối bo góc, giống danh sách nhóm của iOS. */
@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface),
        content = content,
    )
}

@Composable
fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
    )
}

/** Một dòng cài đặt: tên bên trái, giá trị và mũi tên bên phải. */
@Composable
fun SettingsRow(title: String, value: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tên đo trước, giá trị (ví dụ tên nhạc chuông dài) chiếm phần còn lại và cắt bớt bằng "…".
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = cs.onSurfaceVariant,
        )
    }
}

/** Chọn các ngày lặp: mỗi ngày một dòng, ngày đang chọn có dấu tích bên phải. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeatSheet(mask: Int, onMaskChange: (Int) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.background,
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Lặp lại", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onDismiss) { Text("Xong") }
            }
            SettingsGroup {
                DAY_FULL.forEachIndexed { i, name ->
                    val on = (mask shr i) and 1 == 1
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onMaskChange(mask xor (1 shl i)) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Mỗi ${name.lowercase()}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (on) Icon(Icons.Default.Check, contentDescription = "Đã chọn", tint = cs.primary)
                    }
                    if (i < DAY_FULL.lastIndex) RowDivider()
                }
            }
            Text(
                "Không chọn ngày nào thì chuông chỉ reo một lần.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}
