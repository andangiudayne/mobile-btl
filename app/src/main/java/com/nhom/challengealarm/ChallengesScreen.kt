package com.nhom.challengealarm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Tab Thử thách: hai nút thêm mã QR / vật để chụp, thử thách có sẵn chia hai nhóm (trí não được nghỉ giữa màn,
 * vận động bắt rời giường) dạng lưới 2 cột, và thư viện của người dùng.
 */
@Composable
fun ChallengesScreen(
    library: List<ChallengeItem>,
    contentPadding: PaddingValues,
    onAddQr: () -> Unit,
    onAddPhoto: () -> Unit,
    onDelete: (ChallengeItem) -> Unit,
) {
    val mental = BuiltIn.all.filter { it.type.isMental }
    val physical = BuiltIn.all.filter { !it.type.isMental }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            ScreenTitle(
                "Thử thách",
                "Mỗi chuông chọn tối đa $MAX_CHALLENGES_PER_ALARM thử thách. Mỗi màn, app chọn ngẫu nhiên 1 cái.",
            )
        }
        item {
            Row(
                Modifier.padding(horizontal = GUTTER, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AddTile("Thêm mã QR", "Dán ở bồn rửa mặt", Icons.Default.QrCodeScanner, Modifier.weight(1f), onAddQr)
                AddTile("Thêm vật để chụp", "Ví dụ bàn chải, cốc", Icons.Default.CameraAlt, Modifier.weight(1f), onAddPhoto)
            }
        }

        item { SectionHeader("Trí não", "${mental.size} thử thách") }
        item {
            Text(
                "Làm trên màn hình. Qua màn được nghỉ 1 đến 2 phút nếu chuông có bật nghỉ.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = GUTTER).padding(bottom = 8.dp),
            )
        }
        items(mental.chunked(2)) { pair -> TileRow(pair) }

        item { SectionHeader("Vận động", "${physical.size} thử thách") }
        item {
            Text(
                "Bắt bạn rời giường. Không có giờ nghỉ để khỏi nằm xuống lại.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = GUTTER).padding(bottom = 8.dp),
            )
        }
        items(physical.chunked(2)) { pair -> TileRow(pair) }

        item { SectionHeader("Của bạn", if (library.isEmpty()) null else "${library.size} thử thách") }
        if (library.isEmpty()) {
            item {
                Text(
                    "Chưa có. Thêm một mã QR dán ở nơi xa giường là cách chắc nhất để buộc mình phải dậy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = GUTTER),
                )
            }
        }
        items(library, key = { "l${it.id}" }) { item ->
            AppCard(Modifier.padding(horizontal = GUTTER, vertical = 6.dp), padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(item.kind)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (item.kind == ChallengeType.QR) "Quét đúng mã đã đăng ký" else "Chụp vật: ${item.param}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onDelete(item) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Xóa thử thách", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Hai thẻ thử thách trên một hàng (hàng lẻ thì ô thứ hai để trống). */
@Composable
private fun TileRow(pair: List<BuiltInChallenge>) {
    Row(
        Modifier.padding(horizontal = GUTTER, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        pair.forEach { ChallengeTile(it, Modifier.weight(1f)) }
        if (pair.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ChallengeTile(c: BuiltInChallenge, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    AppCard(modifier.heightIn(min = 150.dp), padding = 14.dp) {
        IconBadge(c.type)
        Spacer(Modifier.height(10.dp))
        Text(c.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(
            c.description,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Ô thêm thử thách có viền vàng. */
@Composable
private fun AddTile(title: String, hint: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val p = AppTheme.palette
    Column(
        modifier
            .surfaceBlock(22.dp, nightColor = p.featured.first())
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = p.accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = p.text)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = p.muted)
    }
}
