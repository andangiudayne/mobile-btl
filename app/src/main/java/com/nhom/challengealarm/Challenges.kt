package com.nhom.challengealarm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization

private val CameraShape = RoundedCornerShape(28.dp)

/** Thử thách quét mã: phải quét đúng mã đã đăng ký (ví dụ mã dán ở bồn rửa mặt) mới tắt được chuông. */
@Composable
fun QrChallengeSection(name: String, expected: String, onDone: () -> Unit) {
    var wrong by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    Text("Quét mã \"$name\"", style = MaterialTheme.typography.titleLarge, color = Color.White)
    Text("Đi tới chỗ dán mã và quét để tắt chuông.", style = MaterialTheme.typography.bodyMedium, color = Dawn.Lilac)
    CameraScanner(
        mode = ScanMode.BARCODE,
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .padding(vertical = 12.dp)
            .clip(CameraShape),
        onBarcode = { code ->
            if (code == expected) {
                if (!finished) {
                    finished = true
                    onDone()
                }
            } else {
                wrong = true
            }
        },
    )
    if (wrong) {
        Text(
            "Đây không phải mã đã đăng ký. Hãy tìm đúng mã.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFFFB4B4),
        )
    }
}

/**
 * Thử thách chụp vật: hướng camera vào đúng loại vật đã đăng ký (ví dụ "Sink").
 * Cần nhận diện đúng liên tiếp 2 lần với độ tin cậy >= 70% để tránh trúng ngẫu nhiên.
 */
@Composable
fun PhotoChallengeSection(name: String, expectedLabel: String, onDone: () -> Unit) {
    var hits by remember { mutableIntStateOf(0) }
    var seen by remember { mutableStateOf("") }
    var finished by remember { mutableStateOf(false) }

    Text("Chụp \"$name\"", style = MaterialTheme.typography.titleLarge, color = Color.White)
    Text("Hướng camera vào vật để tắt chuông.", style = MaterialTheme.typography.bodyMedium, color = Dawn.Lilac)
    CameraScanner(
        mode = ScanMode.OBJECT,
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .padding(vertical = 12.dp)
            .clip(CameraShape),
        onLabels = { labels ->
            seen = labels.firstOrNull()?.text.orEmpty()
            val match = labels.any { it.text.equals(expectedLabel, ignoreCase = true) && it.confidence >= 0.7f }
            hits = if (match) hits + 1 else 0
            if (hits >= 2 && !finished) {
                finished = true
                onDone()
            }
        },
    )
    Text(
        if (seen.isEmpty()) "Đang tìm vật..." else "Camera thấy: $seen",
        style = MaterialTheme.typography.bodySmall,
        color = Dawn.Lilac,
    )
}

/** Tên thử thách dài quá sẽ tràn thẻ trong thư viện và trình sửa chuông. */
private const val MAX_CHALLENGE_NAME = 40

/** Màn hình đăng ký thử thách mới (quét mã hoặc chọn nhãn vật thể) để thêm vào thư viện. */
@Composable
fun RegisterChallengeDialog(
    type: ChallengeType,
    onDismiss: () -> Unit,
    onSave: (name: String, param: String) -> Unit,
) {
    var param by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var labels by remember { mutableStateOf<List<LabelResult>>(emptyList()) }
    val isQr = type == ChallengeType.QR
    val cs = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Gõ tên thử thách: cửa sổ hộp thoại co lại phía trên bàn phím, nút "Lưu thử thách" không bị che.
        ResizeWindowForKeyboard()
        val focusManager = LocalFocusManager.current
        Surface(Modifier.fillMaxSize(), color = cs.background) {
            Column(
                Modifier.systemBarsPadding().keyboardPadding().padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (isQr) "Thêm mã QR" else "Thêm vật để chụp",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    if (isQr) "Quét mã QR hoặc mã vạch muốn dùng, ví dụ mã dán ở bồn rửa mặt."
                    else "Hướng camera vào vật cần chụp, rồi chọn nhãn nhận diện được.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                CameraScanner(
                    mode = if (isQr) ScanMode.BARCODE else ScanMode.OBJECT,
                    modifier = Modifier.fillMaxWidth().height(300.dp).clip(CameraShape),
                    onBarcode = { code ->
                        param = code
                        if (name.isBlank()) name = "Mã QR"
                    },
                    onLabels = { labels = it },
                )
                if (isQr) {
                    Text(
                        if (param.isEmpty()) "Chưa quét được mã" else "Đã quét: $param",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text("Nhãn nhận diện được:", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    labels.take(5).forEach { l ->
                        FilterChip(
                            selected = param == l.text,
                            onClick = {
                                param = l.text
                                if (name.isBlank()) name = l.text
                            },
                            label = { Text("${l.text} (${(l.confidence * 100).toInt()}%)") },
                            shape = CircleShape,
                        )
                    }
                    if (param.isNotEmpty()) Text("Đã chọn: $param", style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_CHALLENGE_NAME) },
                    label = { Text("Tên thử thách") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text("Hủy") }
                    Button(
                        enabled = param.isNotEmpty() && name.isNotBlank(),
                        onClick = { onSave(name.trim(), param) },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Dawn.Gold, contentColor = Dawn.Midnight),
                        border = BorderStroke(0.dp, Color.Transparent),
                    ) { Text("Lưu thử thách") }
                }
            }
        }
    }
}
