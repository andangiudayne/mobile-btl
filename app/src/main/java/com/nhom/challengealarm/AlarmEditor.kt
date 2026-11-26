package com.nhom.challengealarm

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import kotlin.math.roundToInt

private const val MAX_LABEL_LENGTH = 30

/**
 * Trình sửa chuông kiểu đồng hồ iOS: thanh Hủy / Lưu cố định ở trên, hai bánh xe chọn giờ và phút,
 * danh sách cài đặt nhóm (lặp lại, tên, nhạc chuông, hoãn), rồi đến phần thử thách.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditor(
    initial: AlarmEntity,
    library: List<ChallengeItem>,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (AlarmEntity) -> Unit,
    onToggleSkip: (() -> Unit)? = null,
    onOpenDaysOff: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val hardwareOk = remember { BuiltIn.all.associate { it.type to RingViewModel.hasHardware(context, it.type) } }
    val notificationsOk = remember { WakeCheck.notificationsAllowed(context) }
    var confirmDelete by remember { mutableStateOf(false) }

    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    var mask by remember { mutableIntStateOf(initial.daysMask) }
    var label by remember { mutableStateOf(initial.label) }
    var difficulty by remember { mutableIntStateOf(initial.mathDifficulty) }
    var count by remember { mutableStateOf(initial.mathCount.toFloat()) }
    // Bỏ id của thử thách đã bị xóa khỏi thư viện (chỉ khi thư viện đã tải xong, tránh xóa nhầm).
    var selected by remember {
        val builtIns = BuiltIn.all.map { it.id }.toSet()
        val libraryIds = library.map { it.id }.toSet()
        mutableStateOf(
            initial.challengeIdList().filter { id ->
                if (id <= 0) id in builtIns else library.isEmpty() || id in libraryIds
            }
        )
    }
    var snooze by remember { mutableIntStateOf(initial.snoozeLimit) }
    var ringtone by remember { mutableStateOf(initial.ringtoneUri) }
    var rounds by remember { mutableIntStateOf(initial.rounds.coerceIn(1, MAX_ROUNDS)) }
    var breakMinutes by remember { mutableIntStateOf(initial.breakMinutes.coerceIn(0, MAX_BREAK_MINUTES)) }
    var wakeCheck by remember {
        mutableIntStateOf(initial.wakeCheckMin.takeIf { it in WakeCheckRules.OPTIONS } ?: WakeCheckRules.OPTIONS[2])
    }
    var showRepeat by remember { mutableStateOf(false) }
    var skipHolidays by remember { mutableStateOf(initial.skipHolidays) }

    val ringtoneTitle = remember(ringtone) {
        if (ringtone.isBlank()) "Mặc định"
        else runCatching {
            RingtoneManager.getRingtone(context, Uri.parse(ringtone)).getTitle(context)
        }.getOrDefault("Đã chọn")
    }
    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked = result.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            ringtone = picked?.toString().orEmpty()
        }
    }

    fun toggle(id: Int) {
        selected = when {
            id in selected -> selected - id
            selected.size < MAX_CHALLENGES_PER_ALARM -> selected + id
            else -> selected
        }
    }

    fun save() {
        onSave(
            initial.copy(
                hour = hour,
                minute = minute,
                daysMask = mask,
                label = label.trim(),
                mathDifficulty = difficulty,
                mathCount = count.toInt(),
                challengeIds = selected.joinToString(","),
                snoozeLimit = snooze,
                ringtoneUri = ringtone,
                rounds = rounds,
                breakMinutes = breakMinutes,
                wakeCheckMin = wakeCheck,
                skipHolidays = skipHolidays,
                // Đổi giờ hoặc ngày lặp thì bỏ "bỏ qua lần reo tới" (lần reo cũ không còn đúng nữa).
                skipUntil = if (hour == initial.hour && minute == initial.minute && mask == initial.daysMask) {
                    initial.skipUntil
                } else {
                    0L
                },
                enabled = true,
            )
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.background,
        windowInsets = SheetWindowInsets,
    ) {
        ResizeWindowForKeyboard()
        val focusManager = LocalFocusManager.current
        // Gõ tên chuông: phần cuộn co lại phía trên bàn phím, vẫn thấy ô đang gõ và cuộn được tới mọi mục.
        Column(Modifier.keyboardPadding()) {
            // Thanh trên cố định, không cuộn theo nội dung
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Hủy") }
                Text(if (initial.id == 0) "Chuông mới" else "Sửa chuông", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = ::save) { Text("Lưu", fontWeight = FontWeight.Bold) }
            }

            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TimeWheel(
                    hour = hour,
                    minute = minute,
                    onHourChange = { hour = it },
                    onMinuteChange = { minute = it },
                )

                SettingsGroup {
                    SettingsRow("Lặp lại", repeatSummary(mask)) { showRepeat = true }
                    RowDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Tên chuông", style = MaterialTheme.typography.bodyLarge)
                        BasicTextField(
                            value = label,
                            onValueChange = { label = it.take(MAX_LABEL_LENGTH) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = cs.onSurface,
                                textAlign = TextAlign.End,
                            ),
                            cursorBrush = SolidColor(cs.primary),
                            modifier = Modifier.weight(1f).padding(start = 16.dp),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterEnd) {
                                    if (label.isEmpty()) {
                                        Text(
                                            "Báo thức",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = cs.onSurfaceVariant,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                    RowDivider()
                    SettingsRow("Nhạc chuông", ringtoneTitle) {
                        val pick = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        if (ringtone.isNotBlank()) {
                            pick.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(ringtone))
                        }
                        ringtonePicker.launch(pick)
                    }
                    RowDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Hoãn chuông", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (snooze > 0) snooze-- }) {
                            Icon(Icons.Default.Remove, contentDescription = "Giảm số lần hoãn")
                        }
                        Text(
                            if (snooze == 0) "Không" else "$snooze lần",
                            style = MaterialTheme.typography.bodyLarge,
                            color = cs.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        IconButton(onClick = { if (snooze < MAX_SNOOZE_LIMIT) snooze++ }) {
                            Icon(Icons.Default.Add, contentDescription = "Tăng số lần hoãn")
                        }
                    }
                }
                Text(
                    "Mỗi lần hoãn, chuông reo lại sau ${AlarmScheduler.SNOOZE_MINUTES} phút.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )

                // Không reo ngày lễ (cả Tết âm lịch) và kỳ nghỉ riêng: chỉ cho chuông lặp.
                if (mask != 0) {
                    val nextOff = remember { DaysOff.next(Ymd.of(System.currentTimeMillis()), days = 400) }
                    SettingsGroup {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { skipHolidays = !skipHolidays }
                                .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Không reo ngày nghỉ", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (skipHolidays && nextOff != null) {
                                        "Sắp tới: ${nextOff.second}, ${nextOff.first.weekdayShort} ${nextOff.first.shortText}. " +
                                            "Hết kỳ nghỉ chuông tự reo lại, khỏi tắt rồi quên bật."
                                    } else {
                                        "Ngày lễ Việt Nam (cả Tết âm lịch) và kỳ nghỉ bạn thêm vào lịch nghỉ."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                            Switch(checked = skipHolidays, onCheckedChange = { skipHolidays = it })
                        }
                        RowDivider()
                        SettingsRow("Lịch nghỉ", "Xem, thêm kỳ nghỉ", onOpenDaysOff)
                    }
                }

                // "Bỏ qua lần reo tới": chỉ cho chuông lặp đã lưu và đang bật.
                if (onToggleSkip != null && initial.id != 0 && initial.daysMask != 0 && initial.enabled) {
                    val now = remember { System.currentTimeMillis() }
                    val skipping = initial.skipUntil > now
                    val target = if (skipping) initial.skipUntil else AlarmScheduler.upcomingIgnoringSkip(initial, now)
                    if (target != null) {
                        SettingsGroup {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onToggleSkip)
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            ) {
                                Text(
                                    if (skipping) "Hủy bỏ qua ${weekdayShort(target)} ${hhmm(target)}"
                                    else "Bỏ qua lần reo ${weekdayShort(target)} ${hhmm(target)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = cs.primary,
                                )
                                Text(
                                    if (skipping) "Chuông đang bỏ qua lần này, sau đó reo lại như cũ."
                                    else "Nghỉ lễ, được nghỉ học hay dậy sớm hơn: chỉ bỏ một lần, sau đó chuông tự reo lại, " +
                                        "khỏi tắt rồi quên bật.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Thử thách (${selected.size}/$MAX_CHALLENGES_PER_ALARM)",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "Mỗi màn, app chọn ngẫu nhiên 1 thử thách trong số đã chọn (không lặp lại màn ngay trước). " +
                            "Không chọn gì thì giải toán.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                    listOf(
                        "Trí não (được nghỉ giữa màn)" to BuiltIn.all.filter { it.type.isMental },
                        "Vận động (bắt rời giường)" to BuiltIn.all.filter { !it.type.isMental },
                    ).forEach { (groupTitle, group) ->
                        Text(
                            groupTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        group.forEach { builtIn ->
                            ChallengeOption(
                                kind = builtIn.type,
                                title = builtIn.name,
                                subtitle = if (hardwareOk[builtIn.type] != false) builtIn.description
                                else "Máy không có cảm biến này, khi reo sẽ đổi sang giải toán",
                                selected = builtIn.id in selected,
                                onClick = { toggle(builtIn.id) },
                            )
                        }
                    }
                    if (library.isNotEmpty()) {
                        Text(
                            "Của bạn",
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    library.forEach { item ->
                        ChallengeOption(
                            kind = item.kind,
                            title = item.name,
                            subtitle = if (item.kind == ChallengeType.QR) "Quét mã" else "Chụp vật: ${item.param}",
                            selected = item.id in selected,
                            onClick = { toggle(item.id) },
                        )
                    }
                    if (library.isEmpty()) {
                        Text(
                            "Vào tab Thử thách để thêm mã QR hoặc vật để chụp.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Số màn", style = MaterialTheme.typography.titleSmall)
                    SettingsGroup {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Phải qua", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            IconButton(onClick = { if (rounds > 1) rounds-- }) {
                                Icon(Icons.Default.Remove, contentDescription = "Bớt một màn")
                            }
                            Text(
                                "$rounds màn",
                                style = MaterialTheme.typography.bodyLarge,
                                color = cs.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            IconButton(onClick = { if (rounds < MAX_ROUNDS) rounds++ }) {
                                Icon(Icons.Default.Add, contentDescription = "Thêm một màn")
                            }
                        }
                    }
                    if (rounds > 1) {
                        Text("Nghỉ sau màn trí não", style = MaterialTheme.typography.bodyMedium)
                        Segmented(listOf("Không nghỉ", "1 phút", "2 phút"), breakMinutes) { breakMinutes = it }
                        Text(
                            (
                                if (breakMinutes == 0) "Qua màn nào chuông reo tiếp màn sau ngay. "
                                else "Qua một màn trí não (toán, gõ chữ, nhớ màu, xếp chữ...) thì chuông im $breakMinutes phút " +
                                    "cho bạn nằm thêm rồi reo lại màn tiếp. Màn vận động (lắc, đi bộ, quét mã, chụp vật) " +
                                    "không nghỉ để bạn khỏi nằm xuống lại. "
                                ) + "Từ màn 2 có nút giữ \"Bạn đã tỉnh\" để tắt luôn.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Kiểm tra đã tỉnh", style = MaterialTheme.typography.titleSmall)
                    Segmented(
                        WakeCheckRules.OPTIONS.map { if (it == 0) "Tắt" else "$it phút" },
                        WakeCheckRules.OPTIONS.indexOf(wakeCheck).coerceAtLeast(0),
                    ) { wakeCheck = WakeCheckRules.OPTIONS[it] }
                    val needNotif = wakeCheck != 0 && !notificationsOk
                    val wakeCheckHint = when {
                        wakeCheck == 0 -> "Không hỏi lại sau khi tắt chuông."
                        else -> "Tắt chuông xong $wakeCheck phút, app hỏi \"Bạn còn thức không?\". Không bấm \"Tôi đã dậy\" " +
                            "trong ${WakeCheckRules.ANSWER_WINDOW_MS / 1000} giây thì chuông reo lại 1 màn, " +
                            "chống tắt chuông rồi ngủ tiếp."
                    }
                    Text(
                        if (needNotif) "$wakeCheckHint Cần bật thông báo cho app thì mới hỏi được." else wakeCheckHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (needNotif) cs.error else cs.onSurfaceVariant,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Độ khó", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Áp dụng cho mọi thử thách có sẵn: số lần lắc, số bước, độ dài câu, dãy màu, số ô, độ sáng cần đạt...",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                    Segmented(listOf("Dễ", "Vừa", "Khó"), difficulty - 1) { difficulty = it + 1 }
                    Text("Số bài toán phải giải: ${count.toInt()}", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = count,
                        onValueChange = { count = it.roundToInt().toFloat() },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = cs.primary,
                            activeTrackColor = cs.primary,
                            inactiveTrackColor = cs.surfaceVariant,
                        ),
                    )
                }

                if (onDelete != null) {
                    SettingsGroup {
                        Text(
                            "Xóa chuông",
                            style = MaterialTheme.typography.bodyLarge,
                            color = cs.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { confirmDelete = true }
                                .padding(vertical = 14.dp),
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Xóa chuông %02d:%02d?".format(hour, minute)) },
            text = { Text("Chuông và lịch reo của nó sẽ bị xóa. Thống kê cũ vẫn giữ.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Xóa", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Hủy") } },
        )
    }

    if (showRepeat) {
        RepeatSheet(mask = mask, onMaskChange = { mask = it }, onDismiss = { showRepeat = false })
    }
}
