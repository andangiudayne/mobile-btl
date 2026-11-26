package com.nhom.challengealarm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Elderly
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization

// =================================================================================================
// Tab Gia đình (VIEW). Ba trạng thái:
//   Chưa dùng   : giới thiệu, chọn "Tôi chăm sóc gia đình" / "Tôi được gia đình chăm sóc" / thử với 2 tài khoản.
//   Người quản lý: cảnh báo theo độ tuổi, thẻ từng người (đêm qua, sáng nay), kế hoạch ngủ, mã mời/kế hoạch.
//   Được quản lý : đang chia sẻ với ai, kế hoạch đang áp dụng, gửi báo cáo, nhắn SMS khi chưa tắt chuông.
// =================================================================================================

private val UrgentColor = Color(0xFFE5484D)
private val WatchColor = Color(0xFFF5A524)
private val OkColor = Color(0xFF30A46C)

private fun levelColor(level: FamilyLevel): Color = when (level) {
    FamilyLevel.URGENT -> UrgentColor
    FamilyLevel.WATCH -> WatchColor
    FamilyLevel.OK -> OkColor
}

private fun dayText(t: Long): String = "${weekdayShort(t)} " + SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(t))

@Composable
fun FamilyTab(vm: FamilyViewModel, contentPadding: PaddingValues) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val invite by vm.pendingInvite.collectAsStateWithLifecycle()
    val share by vm.share.collectAsStateWithLifecycle()
    var importTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var openMemberId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    val family = ui.family

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            ScreenTitle(
                "Gia đình",
                (if (ui.accounts.size > 1) "Tài khoản ${ui.accounts.firstOrNull { it.id == ui.currentAccount }?.name.orEmpty()} · " else "") +
                    when (family.role) {
                        FamilyRole.NONE -> "Chăm giấc ngủ cho bố mẹ và con"
                        FamilyRole.MANAGER -> "Đang chăm sóc ${family.members.size} người"
                        FamilyRole.MEMBER -> "Đang chia sẻ với ${family.me?.managerName.orEmpty()}"
                    },
            ) {
                if (family.role != FamilyRole.NONE) {
                    TextButton(onClick = {
                        if (family.role == FamilyRole.MEMBER) confirmLeave = true else vm.resetRole()
                    }) { Text("Đổi vai trò", color = AppTheme.palette.accent) }
                }
            }
        }
        if (ui.accounts.size > 1) {
            item { AccountBar(ui.accounts, ui.currentAccount, onSwitch = vm::switchAccount, onRemove = { vm.removeTestAccounts() }) }
        }
        when (family.role) {
            FamilyRole.NONE -> roleChooser(
                onManage = { vm.becomeManager() },
                onJoin = { importTitle = "Nhập mã mời" },
                onTestPair = if (ui.accounts.size <= 1) ({ vm.createTestPair() }) else null,
            )
            FamilyRole.MANAGER -> managerContent(
                ui = ui,
                onOpen = { openMemberId = it.id },
                onAdd = { showAdd = true },
                onImport = { importTitle = "Nhập mã báo cáo" },
                onSimulate = vm::simulate,
                onTestPair = if (ui.accounts.size <= 1) ({ vm.createTestPair() }) else null,
            )
            FamilyRole.MEMBER -> memberContent(
                state = family,
                now = ui.now,
                onReport = { vm.showMyReport() },
                onImport = { importTitle = "Nhập kế hoạch mới" },
                onSms = vm::setSms,
                onLeave = { confirmLeave = true },
            )
        }
    }

    importTitle?.let { title ->
        ImportSheet(
            title = title,
            onImport = { text ->
                importTitle = null
                vm.importCode(text)
            },
            onDismiss = { importTitle = null },
        )
    }
    if (showAdd) {
        AddMemberSheet(
            onAdd = { name, relation, kind, year ->
                showAdd = false
                vm.addMember(name, relation, kind, year)
            },
            onDismiss = { showAdd = false },
        )
    }
    openMemberId?.let { id ->
        val m = family.members.firstOrNull { it.id == id }
        if (m != null) {
            MemberSheet(
                member = m,
                now = ui.now,
                onDismiss = { openMemberId = null },
                onSavePlan = { plan, send -> vm.updatePlan(m, plan, send) },
                onInvite = { vm.showInvite(m) },
                onRemove = {
                    openMemberId = null
                    vm.removeMember(m)
                },
            )
        }
    }
    share?.let {
        CodeSheet(
            share = it,
            targets = ui.accounts.filter { a -> a.id != ui.currentAccount },
            onDeliver = vm::deliverTo,
            onDismiss = { vm.dismissShare() },
        )
    }
    invite?.let { ConsentDialog(it, onAccept = { vm.acceptInvite() }, onDecline = { vm.declineInvite() }) }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Ngừng chia sẻ giấc ngủ?") },
            text = { Text("Người thân sẽ không nhận được báo cáo mới. Chuông \"${FamilyRepository.LABEL}\" vẫn giữ, bạn có thể tự xóa.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    vm.leave()
                }) { Text("Ngừng chia sẻ") }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Hủy") } },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Chưa dùng: giới thiệu và chọn vai trò
// ---------------------------------------------------------------------------------------------

private fun LazyListScope.roleChooser(
    onManage: () -> Unit,
    onJoin: () -> Unit,
    onTestPair: (() -> Unit)?,
) {
    item {
        FeatureCard(
            title = "Chăm giấc ngủ cho cả nhà",
            subtitle = "Con đi làm xa lo bố mẹ già ngủ ít, sáng không dậy. Bố mẹ lo con thức khuya chơi điện thoại. " +
                "Theo dõi giấc ngủ của người thân ngay trên điện thoại của bạn, với lời khuyên đúng theo độ tuổi.",
            modifier = Modifier.padding(horizontal = GUTTER, vertical = 6.dp),
        )
    }
    item { SectionHeader("Bạn là") }
    item {
        RoleCard(
            icon = Icons.Default.FamilyRestroom,
            title = "Tôi chăm sóc gia đình",
            body = "Thêm bố mẹ hoặc con, đặt giờ ngủ, giờ dậy và kiểu thử thách hợp lứa tuổi. Nhận báo cáo và cảnh báo " +
                "(ví dụ bố chưa tắt chuông sau 20 phút, con thức khuya dùng điện thoại).",
            onClick = onManage,
        )
    }
    item {
        RoleCard(
            icon = Icons.Default.Elderly,
            title = "Tôi được gia đình chăm sóc",
            body = "Nhập mã mời từ con cháu hoặc bố mẹ. App tạo chuông theo kế hoạch và gửi báo cáo giấc ngủ cho người thân. " +
                "Bạn luôn thấy rõ những gì được chia sẻ và ngừng được bất cứ lúc nào.",
            onClick = onJoin,
        )
    }
    if (onTestPair != null) {
        item {
            RoleCard(
                icon = Icons.Default.People,
                title = "Chỉ có 1 điện thoại? Thử với 2 tài khoản",
                body = "Tạo tài khoản \"Con\" (chăm sóc) và \"Bố\" (được chăm sóc) trên máy này, đổi qua lại bằng một chạm. " +
                    "Mã mời, kế hoạch, báo cáo chuyển thẳng sang tài khoản kia, không cần máy thứ hai.",
                onClick = onTestPair,
            )
        }
    }
    item { HowItWorks() }
}

@Composable
private fun RoleCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, onClick: () -> Unit) {
    val p = AppTheme.palette
    AppCard(Modifier.padding(horizontal = GUTTER, vertical = 7.dp), onClick = onClick, padding = 18.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(p.accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = p.accent) }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = p.text)
                Text(body, style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
        }
    }
}

@Composable
private fun HowItWorks() {
    val p = AppTheme.palette
    Text(
        "Cách hoạt động: không cần tài khoản hay máy chủ. Hai điện thoại trao đổi bằng mã (quét QR khi ở gần, hoặc gửi " +
            "mã qua Zalo, Messenger khi ở xa). Người lớn tuổi có thể bật nhắn SMS tự động cho người thân khi chưa tắt chuông. " +
            "Chỉ chia sẻ giờ ngủ, thời lượng, số lần thức giấc và giờ tắt chuông; không chia sẻ gì khác.",
        style = MaterialTheme.typography.bodySmall,
        color = p.muted,
        modifier = Modifier.padding(horizontal = GUTTER, vertical = 16.dp),
    )
}

// ---------------------------------------------------------------------------------------------
// Người quản lý
// ---------------------------------------------------------------------------------------------

private fun LazyListScope.managerContent(
    ui: FamilyUiState,
    onOpen: (FamilyMember) -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onSimulate: (String, FamilyScenario) -> Unit,
    onTestPair: (() -> Unit)?,
) {
    val members = ui.family.members
    if (ui.alerts.isNotEmpty()) {
        item { SectionHeader("Cần chú ý", "${ui.alerts.size}") }
        items(ui.alerts.take(5)) { alert ->
            AlertCard(alert) { members.firstOrNull { it.id == alert.memberId }?.let(onOpen) }
        }
    }

    item { SectionHeader("Thành viên", if (members.isEmpty()) null else "${members.size} người") }
    if (members.isEmpty()) {
        item {
            EmptyState(
                icon = Icons.Default.FamilyRestroom,
                title = "Chưa có ai",
                body = "Thêm bố mẹ hoặc con rồi gửi mã mời để bắt đầu nhận báo cáo giấc ngủ.",
                actionLabel = "Thêm người thân",
                onAction = onAdd,
            )
        }
    }
    items(members, key = { it.id }) { m -> MemberCard(m, ui.now) { onOpen(m) } }

    item {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = GUTTER, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PillButton("Thêm người thân", onAdd, Modifier.fillMaxWidth(), icon = Icons.Default.Add)
            PillButton("Nhập mã báo cáo", onImport, Modifier.fillMaxWidth(), icon = Icons.Default.QrCodeScanner)
        }
    }

    if (members.any { it.sample }) {
        item { AlertPreview(members, onSimulate) }
    }
    if (onTestPair != null) {
        item {
            TextButton(onClick = onTestPair, modifier = Modifier.padding(horizontal = GUTTER)) {
                Text("Không có máy thứ hai? Tạo 2 tài khoản thử (Con và Bố)", color = AppTheme.palette.accent)
            }
        }
    }
    item { HowItWorks() }
}

/** Thanh chọn tài khoản khi đang thử nhiều tài khoản trên một máy. */
@Composable
private fun AccountBar(accounts: List<FamilyAccount>, current: String, onSwitch: (String) -> Unit, onRemove: () -> Unit) {
    val p = AppTheme.palette
    AppCard(Modifier.padding(horizontal = GUTTER, vertical = 6.dp), padding = 14.dp) {
        Text("Tài khoản trên máy này (chạm để đổi)", style = MaterialTheme.typography.labelLarge, color = p.muted)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            accounts.forEach { a ->
                val on = a.id == current
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (on) p.accent else p.accent.copy(alpha = 0.1f))
                        .clickable { onSwitch(a.id) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        a.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (on) p.onAccent else p.text,
                    )
                    Text(
                        when (a.role) {
                            FamilyRole.MANAGER -> "Chăm sóc"
                            FamilyRole.MEMBER -> "Được chăm sóc"
                            FamilyRole.NONE -> "Chưa chọn vai trò"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (on) p.onAccent.copy(alpha = 0.85f) else p.muted,
                    )
                }
            }
        }
        TextButton(onClick = onRemove) { Text("Bỏ tài khoản thử", color = p.muted) }
    }
}

@Composable
private fun AlertCard(alert: FamilyAlert, onClick: () -> Unit) {
    val p = AppTheme.palette
    val color = levelColor(alert.level)
    AppCard(Modifier.padding(horizontal = GUTTER, vertical = 6.dp), onClick = onClick, padding = 0.dp) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(color))
            Column(Modifier.padding(14.dp)) {
                Text(
                    (if (alert.level == FamilyLevel.URGENT) "KHẨN · " else "") + alert.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (alert.level == FamilyLevel.URGENT) color else p.text,
                )
                Text(alert.body, style = MaterialTheme.typography.bodySmall, color = p.muted, maxLines = 3)
            }
        }
    }
}

@Composable
private fun MemberCard(m: FamilyMember, now: Long, onClick: () -> Unit) {
    val p = AppTheme.palette
    val level = FamilyAdvisor.level(m, now)
    val last = m.latest
    val group = AgeGroup.of(m.age(now).coerceAtLeast(AgeGroup.MIN_AGE))
    AppCard(Modifier.padding(horizontal = GUTTER, vertical = 7.dp), onClick = onClick, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(p.accent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(m.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = p.text)
                }
                Box(Modifier.size(14.dp).clip(CircleShape).background(levelColor(level)))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = p.text)
                    Spacer(Modifier.width(8.dp))
                    if (!m.paired) SoftPill("Chưa ghép nối")
                }
                Text(
                    "${m.relation} · ${m.age(now)} tuổi · nên ngủ ${group.rangeText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.muted,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                last?.asleepMin == null -> "Đêm qua: chưa có số liệu"
                last.sleepStart != null -> "Đêm qua: ngủ ${hoursMinutes(last.asleepMin)} · ${hhmm(last.sleepStart)} → " +
                    (last.alarmAt?.let { hhmm(it) } ?: "?")
                else -> "Đêm qua: ngủ ${hoursMinutes(last.asleepMin)}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = p.text,
        )
        Text(
            when {
                last?.alarmAt == null -> "Sáng nay: chưa có chuông"
                last.dismissedAfterSec == null -> "Chuông ${hhmm(last.alarmAt)} CHƯA TẮT"
                else -> "Dậy lúc ${hhmm(last.alarmAt)}, tắt chuông sau ${formatShortDuration(last.dismissedAfterSec.toLong())}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (last?.alarmAt != null && last.dismissedAfterSec == null) UrgentColor else p.muted,
        )
        Spacer(Modifier.height(10.dp))
        NightBars(m.nights.sortedBy { it.nightOf }.takeLast(7), group, height = 54.dp, labels = false)
    }
}

/** Cột ngủ của 7 đêm: xanh là đủ theo tuổi, cam là thiếu; vạch ngang là mức tối thiểu. */
@Composable
private fun NightBars(nights: List<NightReport>, group: AgeGroup, height: androidx.compose.ui.unit.Dp, labels: Boolean) {
    val p = AppTheme.palette
    val minMin = group.minHours * 60
    val top = (group.maxHours + 1.5f) * 60
    Column {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (nights.isEmpty()) return@Canvas
            val slot = size.width / 7f
            val bar = slot * 0.55f
            val minY = size.height * (1f - minMin / top)
            drawLine(p.muted.copy(alpha = 0.5f), Offset(0f, minY), Offset(size.width, minY), strokeWidth = 1.dp.toPx())
            nights.forEachIndexed { i, n ->
                val v = (n.asleepMin ?: 0).toFloat()
                val h = size.height * (v / top).coerceIn(0.03f, 1f)
                val x = slot * (i + 7 - nights.size) + (slot - bar) / 2f
                drawRoundRect(
                    color = if (n.asleepMin == null) p.track else if (v >= minMin) OkColor else WatchColor,
                    topLeft = Offset(x, size.height - h),
                    size = Size(bar, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(bar / 2f),
                )
            }
        }
        if (labels) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                repeat(7 - nights.size) { Spacer(Modifier.weight(1f)) }
                nights.forEach {
                    Text(
                        weekdayShort(it.nightOf),
                        style = MaterialTheme.typography.bodySmall,
                        color = p.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * "Xem trước cảnh báo": dựng nhanh một tình huống trên người có sẵn trong gia đình (FamilyDemo) để thấy cảnh báo
 * và thông báo hiện ra thế nào. Không hiện với người đã gửi báo cáo thật, để không ghi đè số liệu thật.
 */
@Composable
private fun AlertPreview(members: List<FamilyMember>, onSimulate: (String, FamilyScenario) -> Unit) {
    val p = AppTheme.palette
    val elder = members.firstOrNull { it.sample && it.kind == MemberKind.ELDER }
    val kid = members.firstOrNull { it.sample && it.kind == MemberKind.CHILD }
    AppCard(Modifier.padding(horizontal = GUTTER, vertical = 8.dp), padding = 16.dp) {
        Text("Xem trước cảnh báo", style = MaterialTheme.typography.titleSmall, color = p.text)
        Text(
            "Khi có chuyện, app báo thế nào? Chạm để thử, cảnh báo và thông báo sẽ hiện như thật.",
            style = MaterialTheme.typography.bodySmall,
            color = p.muted,
        )
        Spacer(Modifier.height(10.dp))
        if (elder != null) {
            PreviewButton("${elder.name} không tắt chuông (khẩn)") { onSimulate(elder.id, FamilyScenario.MISSED_ALARM) }
            PreviewButton("${elder.name} dậy bình thường") { onSimulate(elder.id, FamilyScenario.GOOD_MORNING) }
        }
        if (kid != null) {
            PreviewButton("${kid.name} thức khuya dùng điện thoại") { onSimulate(kid.id, FamilyScenario.LATE_NIGHT) }
            PreviewButton("${kid.name} ngủ đủ giấc") { onSimulate(kid.id, FamilyScenario.GOOD_MORNING) }
        }
    }
}

@Composable
private fun PreviewButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), shape = CircleShape) {
        Text(text, color = AppTheme.palette.text)
    }
}

// ---------------------------------------------------------------------------------------------
// Chi tiết một thành viên và kế hoạch ngủ
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberSheet(
    member: FamilyMember,
    now: Long,
    onDismiss: () -> Unit,
    onSavePlan: (FamilyPlan, Boolean) -> Unit,
    onInvite: () -> Unit,
    onRemove: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val group = AgeGroup.of(member.age(now).coerceAtLeast(AgeGroup.MIN_AGE))
    var plan by remember(member.id) { mutableStateOf(member.plan) }
    var confirmRemove by remember { mutableStateOf(false) }
    val alerts = FamilyAdvisor.alerts(member, now)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.background,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = GUTTER).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(member.name, style = MaterialTheme.typography.titleLarge)
            Text(
                "${member.relation} · ${member.age(now)} tuổi · ${member.kind.label} · nên ngủ ${group.rangeText}" +
                    if (member.lastSync > 0) " · báo cáo lúc ${hhmm(member.lastSync)} ${dayText(member.lastSync)}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            alerts.forEach { a ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(levelColor(a.level)))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(a.title, style = MaterialTheme.typography.titleSmall)
                        Text(a.body, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }

            Text("7 đêm gần nhất", style = MaterialTheme.typography.titleSmall)
            NightBars(member.nights.sortedBy { it.nightOf }.takeLast(7), group, height = 110.dp, labels = true)
            SettingsGroup {
                val list = member.nights.sortedByDescending { it.nightOf }.take(7)
                if (list.isEmpty()) {
                    Text(
                        "Chưa có báo cáo. Gửi mã mời để ${member.name} ghép nối.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                list.forEachIndexed { i, n ->
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(
                            dayText(n.nightOf) + (n.asleepMin?.let { ": ngủ ${hoursMinutes(it)}" } ?: ": chưa đo được giấc ngủ"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            listOfNotNull(
                                n.sleepStart?.let { "ngủ từ ${hhmm(it)}" },
                                if (n.interruptions > 0) "thức ${n.interruptions} lần" else null,
                                n.alarmAt?.let { at ->
                                    n.dismissedAfterSec?.let { "chuông ${hhmm(at)}, tắt sau ${formatShortDuration(it.toLong())}" }
                                        ?: "chuông ${hhmm(at)} chưa tắt"
                                },
                                if (n.relapsed) "ngủ lại sau khi tắt" else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                    if (i < list.lastIndex) RowDivider()
                }
            }

            Text("Kế hoạch ngủ", style = MaterialTheme.typography.titleSmall)
            SettingsGroup {
                ClockStepper("Giờ đi ngủ", plan.bedtimeMin) { plan = plan.copy(bedtimeMin = it) }
                RowDivider()
                ClockStepper("Giờ dậy (chuông)", plan.wakeMin) { plan = plan.copy(wakeMin = it) }
            }
            val sleepHours = ((plan.wakeMin - plan.bedtimeMin + 24 * 60) % (24 * 60)) / 60f
            Text(
                "Từ giờ ngủ đến giờ dậy: ${"%.1f".format(sleepHours).replace('.', ',')} tiếng (tuổi này nên ${group.rangeText}).",
                style = MaterialTheme.typography.bodySmall,
                color = if (sleepHours < group.minHours) cs.error else cs.onSurfaceVariant,
            )
            Segmented(listOf("Hằng ngày", "Ngày thường"), if (plan.daysMask == WEEKDAYS_MASK) 1 else 0) {
                plan = plan.copy(daysMask = if (it == 1) WEEKDAYS_MASK else ALL_DAYS_MASK)
            }
            Text("Thử thách khi chuông reo", style = MaterialTheme.typography.bodyMedium)
            Segmented(ChallengePreset.values().map { it.label }, plan.preset.ordinal) {
                plan = plan.copy(preset = ChallengePreset.values()[it])
            }
            Text(plan.preset.hint, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Text("Báo khi chưa tắt chuông", style = MaterialTheme.typography.bodyMedium)
            Segmented(
                FamilyPlan.CHECK_IN_OPTIONS.map { if (it == 0) "Không" else "$it phút" },
                FamilyPlan.CHECK_IN_OPTIONS.indexOf(plan.checkInMin).coerceAtLeast(0),
            ) { plan = plan.copy(checkInMin = FamilyPlan.CHECK_IN_OPTIONS[it]) }
            Text(
                "Nên bật với người lớn tuổi ở một mình: chuông reo quá số phút này mà chưa tắt thì bạn nhận cảnh báo khẩn.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )

            Button(
                onClick = { onSavePlan(plan, true) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.palette.accent, contentColor = AppTheme.palette.onAccent),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Lưu và gửi kế hoạch cho ${member.name}") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onSavePlan(plan, false) }) { Text("Chỉ lưu") }
                TextButton(onClick = onInvite) { Text(if (member.paired) "Mã mời ghép nối lại" else "Mã mời ghép nối") }
                TextButton(onClick = { confirmRemove = true }) { Text("Xóa", color = cs.error) }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Xóa ${member.name}?") },
            text = { Text("Báo cáo đã nhận của ${member.name} sẽ bị xóa khỏi máy này.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemove()
                }) { Text("Xóa", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Hủy") } },
        )
    }
}

/** Chỉnh giờ theo bước 15 phút, vòng qua nửa đêm. */
@Composable
private fun ClockStepper(label: String, minutes: Int, onChange: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((minutes - 15 + 24 * 60) % (24 * 60)) }) {
            Icon(Icons.Default.Remove, contentDescription = "Sớm hơn")
        }
        Text(SleepAdvisor.clockText(minutes), style = MaterialTheme.typography.titleMedium, color = cs.primary)
        IconButton(onClick = { onChange((minutes + 15) % (24 * 60)) }) {
            Icon(Icons.Default.Add, contentDescription = "Muộn hơn")
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Thêm thành viên
// ---------------------------------------------------------------------------------------------

private val RELATIONS = listOf(
    "Bố" to MemberKind.ELDER, "Mẹ" to MemberKind.ELDER, "Ông" to MemberKind.ELDER, "Bà" to MemberKind.ELDER,
    "Con" to MemberKind.CHILD, "Em" to MemberKind.CHILD,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddMemberSheet(onAdd: (String, String, MemberKind, Int) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val thisYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    var relation by remember { mutableStateOf("Bố") }
    var kind by remember { mutableStateOf(MemberKind.ELDER) }
    var name by remember { mutableStateOf("") }
    var yearText by remember { mutableStateOf("") }
    val year = yearText.toIntOrNull()
    val validYear = year != null && year in (thisYear - 110)..(thisYear - 4)

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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Thêm người thân", style = MaterialTheme.typography.titleLarge)
            Text("Là", style = MaterialTheme.typography.titleSmall)
            // Xuống dòng khi màn hình hẹp (6 lựa chọn không vừa một hàng trên máy 360dp).
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RELATIONS.forEach { (r, k) ->
                    val on = r == relation
                    Text(
                        r,
                        color = if (on) AppTheme.palette.onAccent else cs.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (on) AppTheme.palette.accent else cs.surfaceVariant)
                            .clickable {
                                relation = r
                                kind = k
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            Segmented(MemberKind.values().map { it.label }, kind.ordinal) { kind = MemberKind.values()[it] }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30) },
                label = { Text("Tên gọi (không bắt buộc)") },
                placeholder = { Text(relation) },
                singleLine = true,
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
                        if (validYear) {
                            val g = AgeGroup.of((thisYear - year!!).coerceAtLeast(AgeGroup.MIN_AGE))
                            "${thisYear - year} tuổi, nên ngủ ${g.rangeText} mỗi đêm"
                        } else {
                            "Dùng để đưa lời khuyên đúng theo tuổi"
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Kế hoạch mặc định: ${FamilyPlan.defaultFor(kind).let { "ngủ ${SleepAdvisor.clockText(it.bedtimeMin)}, dậy ${SleepAdvisor.clockText(it.wakeMin)}, thử thách ${it.preset.label.lowercase()}" }}. " +
                    "Sửa được sau khi thêm.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            Button(
                onClick = { if (validYear) onAdd(name.ifBlank { relation }, relation, kind, year!!) },
                enabled = validYear,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.palette.accent, contentColor = AppTheme.palette.onAccent),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Thêm và tạo mã mời") }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Người được quản lý
// ---------------------------------------------------------------------------------------------

private fun LazyListScope.memberContent(
    state: FamilyState,
    now: Long,
    onReport: () -> Unit,
    onImport: () -> Unit,
    onSms: (SmsCheckIn) -> Unit,
    onLeave: () -> Unit,
) {
    val me = state.me ?: return
    item {
        FeatureCard(
            title = "Đang chia sẻ với ${me.managerName}",
            subtitle = "${me.name} · ${Calendar.getInstance().get(Calendar.YEAR) - me.birthYear} tuổi · ${me.kind.label}. " +
                "Mỗi sáng bấm \"Gửi báo cáo\" để ${me.managerName} biết bạn ngủ thế nào.",
            modifier = Modifier.padding(horizontal = GUTTER, vertical = 6.dp),
            buttonText = "Gửi báo cáo",
            onButton = onReport,
        )
    }
    item { SectionHeader("Kế hoạch đang áp dụng") }
    item {
        val p = AppTheme.palette
        AppCard(Modifier.padding(horizontal = GUTTER, vertical = 6.dp), padding = 16.dp) {
            Text(
                "Ngủ ${SleepAdvisor.clockText(me.plan.bedtimeMin)} · Dậy ${SleepAdvisor.clockText(me.plan.wakeMin)} " +
                    "(${repeatSummary(me.plan.daysMask).lowercase()})",
                style = MaterialTheme.typography.titleMedium,
                color = p.text,
            )
            Text(
                "Thử thách ${me.plan.preset.label.lowercase()}: ${me.plan.preset.hint}",
                style = MaterialTheme.typography.bodySmall,
                color = p.muted,
            )
            Text(
                "App đã tạo chuông \"${FamilyRepository.LABEL}\" theo kế hoạch này (xem ở tab Chuông).",
                style = MaterialTheme.typography.bodySmall,
                color = p.muted,
                modifier = Modifier.padding(top = 6.dp),
            )
            TextButton(onClick = onImport) { Text("Nhập kế hoạch mới", color = p.accent) }
        }
    }
    item { SmsCard(state.sms, me, onSms) }
    item {
        val p = AppTheme.palette
        AppCard(Modifier.padding(horizontal = GUTTER, vertical = 6.dp), padding = 16.dp) {
            Text("Những gì được chia sẻ", style = MaterialTheme.typography.titleSmall, color = p.text)
            listOf(
                "Giờ đi ngủ, thời lượng ngủ, số lần thức giấc của 7 đêm gần nhất",
                "Giờ chuông reo và bao lâu thì tắt chuông",
                "Không chia sẻ tin nhắn, ảnh, vị trí hay ứng dụng khác",
                "Chỉ gửi khi bạn bấm \"Gửi báo cáo\" (hoặc SMS nếu bạn bật bên trên)",
            ).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = p.muted) }
            TextButton(onClick = onLeave) { Text("Ngừng chia sẻ", color = p.accent2) }
        }
    }
}

/** Tự nhắn SMS cho người thân khi chuông reo quá N phút chưa tắt (an toàn cho người lớn tuổi ở một mình). */
@Composable
private fun SmsCard(sms: SmsCheckIn, me: MemberLink, onChange: (SmsCheckIn) -> Unit) {
    val p = AppTheme.palette
    val context = LocalContext.current
    // Khóa theo người (đổi tài khoản thì đọc lại), KHÔNG theo sms.phone: mỗi phím gõ đều lưu sms.phone, nếu khóa theo nó
    // thì ô nhập bị tạo lại giữa chừng, gõ nhanh sẽ mất số và con trỏ nhảy về cuối.
    var phone by remember(me.memberId) { mutableStateOf(sms.phone) }
    var pendingEnable by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingEnable) onChange(sms.copy(enabled = true, phone = phone))
        pendingEnable = false
    }
    AppCard(Modifier.padding(horizontal = GUTTER, vertical = 6.dp), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Nhắn tin khi chưa tắt chuông", style = MaterialTheme.typography.titleSmall, color = p.text)
                Text(
                    "Chuông reo quá ${sms.minutes} phút mà ${me.name} chưa tắt, máy tự nhắn SMS cho người thân. " +
                        "Không cần mạng, tốn phí tin nhắn thường.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.muted,
                )
            }
            Switch(
                checked = sms.enabled,
                onCheckedChange = { on ->
                    if (!on) {
                        onChange(sms.copy(enabled = false, phone = phone))
                    } else if (phone.isNotBlank()) {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            onChange(sms.copy(enabled = true, phone = phone))
                        } else {
                            pendingEnable = true
                            permission.launch(Manifest.permission.SEND_SMS)
                        }
                    }
                },
            )
        }
        val focusManager = LocalFocusManager.current
        OutlinedTextField(
            value = phone,
            onValueChange = { v ->
                phone = v.filter { it.isDigit() || it == '+' }.take(15)
                // Lưu ngay cả khi chưa bật, để rời màn hình rồi quay lại không phải gõ lại số.
                onChange(sms.copy(phone = phone))
            },
            label = { Text("Số điện thoại người thân (${me.managerName})") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
        Segmented(listOf("15 phút", "20 phút", "30 phút"), listOf(15, 20, 30).indexOf(sms.minutes).coerceAtLeast(0)) {
            onChange(sms.copy(minutes = listOf(15, 20, 30)[it], phone = phone))
        }
        if (!sms.enabled && phone.isBlank()) {
            Text(
                "Nhập số điện thoại trước rồi bật công tắc.",
                style = MaterialTheme.typography.bodySmall,
                color = p.muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Mã QR và nhập mã
// ---------------------------------------------------------------------------------------------

/** Vẽ mã QR (thư viện ZXing chỉ dùng để tạo ma trận điểm, không cần mạng). */
@Composable
fun QrImage(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        runCatching {
            QRCodeWriter().encode(
                text, BarcodeFormat.QR_CODE, 0, 0,
                mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L),
            )
        }.getOrNull()
    }
    Canvas(modifier.background(Color.White)) {
        val m = matrix ?: return@Canvas
        val cell = size.minDimension / maxOf(m.width, m.height)
        for (y in 0 until m.height) {
            for (x in 0 until m.width) {
                if (m.get(x, y)) {
                    drawRect(Color.Black, topLeft = Offset(x * cell, y * cell), size = Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodeSheet(
    share: ShareCode,
    targets: List<FamilyAccount>,
    onDeliver: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.background,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = GUTTER).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(share.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(share.steps, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
            // Thử trên một máy: chuyển thẳng sang tài khoản kia và nhập mã, thay cho máy thứ hai.
            targets.forEach { t ->
                Button(
                    onClick = { onDeliver(t.id) },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.palette.accent2, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Mở tài khoản ${t.name} và nhập mã này") }
            }
            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(10.dp)) {
                QrImage(share.code, Modifier.size(240.dp))
            }
            SelectionContainer {
                Text(
                    share.code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(share.code))
                    copied = true
                }, shape = CircleShape) { Text(if (copied) "Đã sao chép" else "Sao chép mã") }
                Button(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, share.message)
                        runCatching { context.startActivity(Intent.createChooser(send, "Gửi mã qua")) }
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.palette.accent, contentColor = AppTheme.palette.onAccent),
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Gửi qua Zalo...")
                }
            }
            TextButton(onClick = onDismiss) { Text("Xong") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSheet(title: String, onImport: (String) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var handled by remember { mutableStateOf(false) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scanning = granted
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                "Quét mã QR trên điện thoại kia, hoặc dán tin nhắn chứa mã (bắt đầu bằng \"CA1:\") nhận qua Zalo, Messenger.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
            if (scanning) {
                CameraScanner(
                    mode = ScanMode.BARCODE,
                    modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(24.dp)),
                    onBarcode = { code ->
                        if (!handled && FamilyCodec.decode(code) != null) {
                            handled = true
                            onImport(code)
                        }
                    },
                )
                TextButton(onClick = { scanning = false }) { Text("Tắt camera") }
            } else {
                OutlinedButton(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) scanning = true else cameraPermission.launch(Manifest.permission.CAMERA)
                    },
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Quét mã QR")
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Dán mã hoặc cả tin nhắn") },
                minLines = 3,
                maxLines = 6,
                // Mã dán vào đã có sẵn xuống dòng; phím "Xong" chỉ để cất bàn phím, lộ nút "Nhập mã".
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { clipboard.getText()?.text?.let { text = it } }, shape = CircleShape) { Text("Dán") }
                Button(
                    onClick = { onImport(text) },
                    enabled = text.isNotBlank(),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.palette.accent, contentColor = AppTheme.palette.onAccent),
                ) { Text("Nhập mã") }
            }
        }
    }
}

/** Hỏi đồng ý trước khi chia sẻ giấc ngủ (không tự động ghép nối khi chỉ nhập mã). */
@Composable
private fun ConsentDialog(invite: FamilyCode.Invite, onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("Chia sẻ giấc ngủ với ${invite.managerName}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${invite.managerName} mời ${invite.name} (${invite.relation}) dùng kế hoạch ngủ:")
                Text(
                    "• Ngủ ${SleepAdvisor.clockText(invite.plan.bedtimeMin)}, chuông ${SleepAdvisor.clockText(invite.plan.wakeMin)} " +
                        "(${repeatSummary(invite.plan.daysMask).lowercase()})\n" +
                        "• Thử thách ${invite.plan.preset.label.lowercase()}",
                )
                Text(
                    "Nếu đồng ý: app tạo chuông theo kế hoạch, và khi bạn bấm \"Gửi báo cáo\" thì ${invite.managerName} thấy giờ ngủ, " +
                        "thời lượng, số lần thức giấc, giờ tắt chuông. Không chia sẻ gì khác. Ngừng được bất cứ lúc nào.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Đồng ý chia sẻ") } },
        dismissButton = { TextButton(onClick = onDecline) { Text("Không") } },
    )
}
