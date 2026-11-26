package com.nhom.challengealarm

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Calendar
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization

/** Lưu hồ sơ trên máy (SharedPreferences). Không gửi đi đâu. */
object ProfileStore {
    private const val PREFS = "profile"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): UserProfile? {
        val p = prefs(context)
        if (!p.contains("birthYear")) return null
        return UserProfile(
            name = p.getString("name", "").orEmpty(),
            birthYear = p.getInt("birthYear", 2000),
            occupation = enumOr(p.getString("occupation", null), Occupation.OFFICE),
            activity = enumOr(p.getString("activity", null), ActivityLevel.MEDIUM),
            chronotype = enumOr(p.getString("chronotype", null), Chronotype.NEUTRAL),
            lateCaffeine = p.getBoolean("lateCaffeine", false),
            nightStartMin = p.getInt("nightStartMin", AgeGroup.ADULT.defaultNightStart),
            remind = p.getBoolean("remind", true),
        )
    }

    fun save(context: Context, profile: UserProfile) {
        prefs(context).edit()
            .putString("name", profile.name)
            .putInt("birthYear", profile.birthYear)
            .putString("occupation", profile.occupation.name)
            .putString("activity", profile.activity.name)
            .putString("chronotype", profile.chronotype.name)
            .putBoolean("lateCaffeine", profile.lateCaffeine)
            .putInt("nightStartMin", profile.nightStartMin)
            .putBoolean("remind", profile.remind)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        runCatching { enumValueOf<T>(name ?: "") }.getOrDefault(fallback)
}

private const val NIGHT_STEP_MIN = 30
private const val DAY_MIN = 24 * 60

/**
 * Bảng "Hồ sơ của bạn": tên, năm sinh, công việc, mức vận động, kiểu người, cà phê buổi chiều,
 * giờ bắt đầu tính giấc ngủ và có nhắc đi ngủ hay không. Từ năm sinh suy ra nhóm tuổi để gợi ý.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(initial: UserProfile?, onSave: (UserProfile) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val thisYear = remember { Calendar.getInstance().get(Calendar.YEAR) }

    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var yearText by remember { mutableStateOf(initial?.birthYear?.toString().orEmpty()) }
    var occupation by remember { mutableStateOf(initial?.occupation ?: Occupation.UNIVERSITY) }
    var activity by remember { mutableStateOf(initial?.activity ?: ActivityLevel.MEDIUM) }
    var chronotype by remember { mutableStateOf(initial?.chronotype ?: Chronotype.NEUTRAL) }
    var caffeine by remember { mutableStateOf(initial?.lateCaffeine ?: false) }
    var remind by remember { mutableStateOf(initial?.remind ?: true) }
    var nightStart by remember { mutableIntStateOf(initial?.nightStartMin ?: AgeGroup.ADULT.defaultNightStart) }
    var nightTouched by remember { mutableStateOf(initial != null) }

    val year = yearText.toIntOrNull()
    val validYear = year != null && year in (thisYear - 100)..(thisYear - AgeGroup.MIN_AGE)
    val age = if (validYear) thisYear - year!! else null
    val group = age?.let { AgeGroup.of(it) }

    // Chưa tự chỉnh giờ bắt đầu tính thì lấy theo nhóm tuổi (người lớn tuổi, trẻ em ngủ sớm hơn).
    LaunchedEffect(group) {
        if (group != null && !nightTouched) nightStart = group.defaultNightStart
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.background,
        windowInsets = SheetWindowInsets,
    ) {
        ResizeWindowForKeyboard()
        val focusManager = LocalFocusManager.current
        Column(
            Modifier.keyboardPadding().verticalScroll(rememberScrollState()).padding(horizontal = GUTTER).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Hồ sơ của bạn", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Người già, người trẻ và trẻ em cần ngủ khác nhau. Vài thông tin này giúp app gợi ý giờ ngủ đúng với bạn. " +
                        "Hồ sơ chỉ lưu trên máy này.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30) },
                label = { Text("Tên gọi") },
                singleLine = true,
                // "Tiếp" trên bàn phím chuyển sang ô năm sinh.
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = yearText,
                onValueChange = { v -> yearText = v.filter { it.isDigit() }.take(4) },
                label = { Text("Năm sinh") },
                singleLine = true,
                isError = yearText.length == 4 && !validYear,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                supportingText = {
                    Text(
                        when {
                            group != null -> "$age tuổi, nhóm ${group.label}: nên ngủ ${group.rangeText} mỗi đêm"
                            yearText.length == 4 -> "Năm sinh từ ${thisYear - 100} đến ${thisYear - AgeGroup.MIN_AGE}"
                            else -> "Dùng để chọn khuyến nghị theo độ tuổi"
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Công việc", style = MaterialTheme.typography.titleSmall)
            SettingsGroup {
                Occupation.values().forEachIndexed { i, o ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { occupation = o }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(o.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (o == occupation) Icon(Icons.Default.Check, contentDescription = "Đã chọn", tint = cs.primary)
                    }
                    if (i < Occupation.values().lastIndex) RowDivider()
                }
            }

            Text("Mức vận động", style = MaterialTheme.typography.titleSmall)
            Segmented(ActivityLevel.values().map { it.label }, activity.ordinal) {
                activity = ActivityLevel.values()[it]
            }

            Text("Bạn là người", style = MaterialTheme.typography.titleSmall)
            Segmented(Chronotype.values().map { it.label }, chronotype.ordinal) {
                chronotype = Chronotype.values()[it]
            }

            SettingsGroup {
                SwitchRow("Hay uống cà phê, trà sau 14h", caffeine) { caffeine = it }
                RowDivider()
                SwitchRow("Nhắc tôi đi ngủ", remind) { remind = it }
                RowDivider()
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Bắt đầu tính giấc ngủ từ", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        nightTouched = true
                        nightStart = (nightStart - NIGHT_STEP_MIN + DAY_MIN) % DAY_MIN // vòng qua nửa đêm, cho người làm ca
                    }) { Icon(Icons.Default.Remove, contentDescription = "Sớm hơn") }
                    Text(
                        SleepAdvisor.clockText(nightStart),
                        style = MaterialTheme.typography.bodyLarge,
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    IconButton(onClick = {
                        nightTouched = true
                        nightStart = (nightStart + NIGHT_STEP_MIN) % DAY_MIN
                    }) { Icon(Icons.Default.Add, contentDescription = "Muộn hơn") }
                }
            }
            Text(
                "Từ giờ này, màn hình tắt liền 15 phút trở lên thì app tính là bạn bắt đầu ngủ, cho đến lúc chuông reo. " +
                    "Lời nhắc đến trước giờ lên giường đề xuất ${BedtimeReminder.LEAD_MIN} phút.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )

            Button(
                onClick = {
                    if (validYear) {
                        onSave(
                            UserProfile(
                                name = name.trim(),
                                birthYear = year!!,
                                occupation = occupation,
                                activity = activity,
                                chronotype = chronotype,
                                lateCaffeine = caffeine,
                                nightStartMin = nightStart,
                                remind = remind,
                            )
                        )
                    }
                },
                enabled = validYear,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.palette.accent,
                    contentColor = AppTheme.palette.onAccent,
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Lưu hồ sơ", style = MaterialTheme.typography.labelLarge) }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Để sau") }
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AppTheme.palette.accent,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = cs.onSurfaceVariant,
                uncheckedTrackColor = cs.outline,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}
