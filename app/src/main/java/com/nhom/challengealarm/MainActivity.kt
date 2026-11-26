package com.nhom.challengealarm

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// =================================================================================================
// Kiến trúc MVVM của app
//
//   View        MainActivity (khung + thanh điều hướng), AlarmsScreen, ChallengesScreen, SleepScreen (SleepUi.kt),
//               StatsScreen, FamilyTab (FamilyScreen.kt), AlarmEditor, ProfileSheet, SetupSheet, RingActivity + thử thách.
//               Chỉ vẽ theo state và gọi hàm của ViewModel.
//   ViewModel   AlarmViewModel, StatsViewModel, SleepViewModel, SetupViewModel, NightViewModel, FamilyViewModel,
//               RingViewModel, ThemeViewModel.
//               Giữ state dạng StateFlow, xử lý thao tác, không biết gì về giao diện.
//   Model       Repository.kt (AlarmRepository, SleepRepository) -> Room (Data.kt), AlarmScheduler,
//               ProfileStore, RingSession, WakeCheck, DeviceStatus; logic thuần: Stats, SleepAnalyzer, SleepAdvisor,
//               RoundPlanner, MathChallenge, NapPlanner, WakeCheckRules, NightCheck, NoiseGenerator, MorningReport.
//               InitialData (lần đầu cài: hồ sơ, chuông, lịch sử 30 ngày từ DemoData, gia đình từ FamilyDemo).
//   Hệ thống   AlarmService, NoiseService, Receivers, BedtimeReceiver, WakeCheckReceiver, SleepTracker
//               (chạy nền, không có giao diện).
// =================================================================================================

/**
 * Lối tắt khi giữ biểu tượng app (res/xml/shortcuts.xml) và nút trên widget: mở thẳng một việc hay dùng.
 * Tên action phải trùng với shortcuts.xml.
 */
object AppShortcuts {
    const val NAP = "com.nhom.challengealarm.action.NAP"
    const val ADD_ALARM = "com.nhom.challengealarm.action.ADD_ALARM"
    const val GOOD_NIGHT = "com.nhom.challengealarm.action.GOOD_NIGHT"
    const val DAYS_OFF = "com.nhom.challengealarm.action.DAYS_OFF"
    val ALL = setOf(NAP, ADD_ALARM, GOOD_NIGHT, DAYS_OFF)

    /** Số phút chợp mắt của lối tắt và nút trên widget. */
    const val NAP_MINUTES = 20
}

class MainActivity : ComponentActivity() {
    /** Lối tắt vừa mở app, chờ giao diện xử lý (một lần). */
    private val shortcut = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Lần đầu cài: nạp hồ sơ, chuông, lịch sử 30 ngày và gia đình (chỉ một lần, không đè dữ liệu đã có).
        InitialData.ensure(this)
        handleWakeCheck(intent)
        // Xoay màn hình (tạo lại Activity) thì không chạy lại lối tắt, kẻo tạo hai chuông chợp mắt.
        if (savedInstanceState == null) handleShortcut(intent)
        enableEdgeToEdge()
        setContent {
            // Chế độ giao diện: 6:00 đến 18:00 sáng (neumorphism), còn lại đêm (tím), hoặc theo lựa chọn ở nút góc trên.
            val themeVm: ThemeViewModel = viewModel()
            val mode by themeVm.mode.collectAsStateWithLifecycle()
            val override by themeVm.override.collectAsStateWithLifecycle()
            AlarmTheme(mode) {
                CompositionLocalProvider(LocalModeSwitch provides ModeSwitch(override) { themeVm.cycle() }) {
                    AlarmApp(shortcut = shortcut, onShortcutHandled = { shortcut.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWakeCheck(intent)
        handleShortcut(intent)
    }

    private fun handleShortcut(intent: Intent?) {
        val action = intent?.action ?: return
        // Mở lại từ màn hình đa nhiệm cũng mang theo Intent cũ: bỏ qua, không chạy lại.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        if (action in AppShortcuts.ALL) shortcut.value = action
    }

    /** Mở app từ thông báo "Bạn còn thức không?": chạm vào thông báo cũng là trả lời "Tôi đã dậy". */
    private fun handleWakeCheck(intent: Intent?) {
        val i = intent ?: return
        if (!i.getBooleanExtra(WakeCheck.EXTRA_CONFIRM, false)) return
        i.removeExtra(WakeCheck.EXTRA_CONFIRM) // xoay màn hình không xử lý lại
        if (WakeCheck.confirm(this)) {
            Toast.makeText(this, "Tốt lắm! Chúc bạn một ngày tốt lành.", Toast.LENGTH_SHORT).show()
        }
    }
}

/** Năm tab: ban ngày là thanh tab chữ ở trên, ban đêm là thanh biểu tượng ở dưới. */
private enum class Tab(val label: String, val icon: ImageVector) {
    ALARMS("Chuông", Icons.Default.Home),
    CHALLENGES("Thử thách", Icons.Default.EmojiEvents),
    SLEEP("Giấc ngủ", Icons.Default.Bedtime),
    STATS("Thống kê", Icons.AutoMirrored.Filled.ShowChart),
    FAMILY("Gia đình", Icons.Default.Groups),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlarmApp(
    shortcut: StateFlow<String?>,
    onShortcutHandled: () -> Unit,
    alarmVm: AlarmViewModel = viewModel(),
    statsVm: StatsViewModel = viewModel(),
    sleepVm: SleepViewModel = viewModel(),
    setupVm: SetupViewModel = viewModel(),
    nightVm: NightViewModel = viewModel(),
    familyVm: FamilyViewModel = viewModel(),
) {
    val context = LocalContext.current
    val p = AppTheme.palette
    val view = LocalView.current

    // Thanh trạng thái và thanh điều hướng hệ thống theo chế độ của app (không theo chế độ tối của máy):
    // ban ngày chữ/biểu tượng tối trên nền trong suốt, ban đêm chữ/biểu tượng sáng.
    LaunchedEffect(p.isNight) {
        val activity = view.context as? ComponentActivity ?: return@LaunchedEffect
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (p.isNight) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    val alarmsOrNull by alarmVm.alarms.collectAsStateWithLifecycle()
    val alarms = alarmsOrNull.orEmpty()
    val lockedIds by alarmVm.lockedIds.collectAsStateWithLifecycle()
    val library by alarmVm.library.collectAsStateWithLifecycle()
    val next by alarmVm.next.collectAsStateWithLifecycle()
    val statsState by statsVm.state.collectAsStateWithLifecycle()
    val sleepState by sleepVm.state.collectAsStateWithLifecycle()
    val profile by sleepVm.profile.collectAsStateWithLifecycle()
    val setupItems by setupVm.items.collectAsStateWithLifecycle()
    val setupDone = setupItems.count { it.done }
    val wakeCheck by alarmVm.wakeCheck.collectAsStateWithLifecycle()
    val nightChecks by nightVm.checks.collectAsStateWithLifecycle()
    val noise by nightVm.noise.collectAsStateWithLifecycle()
    val vacations by alarmVm.vacations.collectAsStateWithLifecycle()
    val pendingShortcut by shortcut.collectAsStateWithLifecycle()

    // Trạng thái giao diện lưu bằng rememberSaveable để xoay màn hình / đổi sáng tối không mất.
    var tab by rememberSaveable { mutableStateOf(Tab.ALARMS) }
    var editingId by rememberSaveable { mutableStateOf<Int?>(null) } // 0 = chuông mới
    var registering by rememberSaveable { mutableStateOf<ChallengeType?>(null) }
    var pendingRegister by rememberSaveable { mutableStateOf<ChallengeType?>(null) }
    var showSetup by rememberSaveable { mutableStateOf(false) }
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var setupAfterProfile by rememberSaveable { mutableStateOf(false) }
    var deletingItem by remember { mutableStateOf<ChallengeItem?>(null) }
    var showGoodNight by rememberSaveable { mutableStateOf(false) }
    var showNap by rememberSaveable { mutableStateOf(false) }
    var showDaysOff by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val editing: AlarmEntity? = when (val id = editingId) {
        null -> null
        0 -> remember(id) { AlarmEntity(hour = 7, minute = 0) }
        else -> alarms.firstOrNull { it.id == id }
    }

    fun toast(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    // Thông báo ngắn từ ViewModel (đã lưu, chuông đang bị khóa...).
    LaunchedEffect(alarmVm) { alarmVm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(familyVm) { familyVm.messages.collect { snackbar.showSnackbar(it) } }

    // Quay lại app (có thể vừa bật quyền trong Cài đặt): đọc lại trạng thái quyền.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                setupVm.refresh()
                sleepVm.refreshPermissions()
                nightVm.refresh() // có thể vừa cắm sạc hoặc sửa cài đặt từ "Kiểm tra trước khi ngủ"
                familyVm.refreshDaily() // sáng mới thì người thân có báo cáo đêm mới
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        setupVm.refresh()
        // Đã từ chối hẳn (Android không hỏi lại nữa): mở trang cài đặt thông báo của app.
        val activity = context as? Activity
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            }
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) registering = pendingRegister else toast("Cần quyền camera để thêm mã QR hoặc vật để chụp.")
        pendingRegister = null
    }
    val activityPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        // Lần đầu mở app: điền hồ sơ trước (để gợi ý giấc ngủ theo tuổi), xong thì sang bảng thiết lập.
        if (setupVm.consumeFirstRun()) {
            if (profile == null) {
                showProfile = true
                setupAfterProfile = true
            } else {
                showSetup = true
            }
        }
        // Quyền thông báo được xin ở bước "Thông báo" của bảng thiết lập, không bật hộp thoại đè lên hồ sơ.
    }

    fun startRegister(type: ChallengeType) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            registering = type
        } else {
            pendingRegister = type
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    fun newAlarm() {
        editingId = 0
    }

    // Lối tắt (giữ biểu tượng app) hoặc nút trên widget.
    LaunchedEffect(pendingShortcut) {
        when (pendingShortcut) {
            AppShortcuts.NAP -> alarmVm.startNap(AppShortcuts.NAP_MINUTES)
            AppShortcuts.ADD_ALARM -> {
                tab = Tab.ALARMS
                newAlarm()
            }
            AppShortcuts.GOOD_NIGHT -> showGoodNight = true
            AppShortcuts.DAYS_OFF -> showDaysOff = true
            else -> return@LaunchedEffect
        }
        onShortcutHandled()
    }

    Box(Modifier.fillMaxSize().appBackground()) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = p.text,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                if (!p.isNight) {
                    DayTabBar(Tab.values().map { it.label }, tab.ordinal) { tab = Tab.values()[it] }
                }
            },
            bottomBar = {
                if (p.isNight) {
                    NightNavBar(Tab.values().map { it.icon }, Tab.values().map { it.label }, tab.ordinal) {
                        tab = Tab.values()[it]
                    }
                }
            },
        ) { padding ->
            val direction = LocalLayoutDirection.current
            val contentPadding = PaddingValues(
                start = padding.calculateStartPadding(direction),
                end = padding.calculateEndPadding(direction),
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            )
            // Bàn phím mở (ví dụ ô số điện thoại ở tab Gia đình): nội dung co lại phía trên bàn phím.
            // consumeWindowInsets: phần dưới mà danh sách đã chừa (thanh tab, thanh điều hướng) không bị cộng thêm lần nữa,
            // nếu không sẽ có một khoảng trống thừa giữa ô đang gõ và bàn phím.
            Box(Modifier.fillMaxSize().consumeWindowInsets(padding).keyboardPadding()) {
                when (tab) {
                    Tab.ALARMS -> AlarmsScreen(
                        alarms = alarms,
                        loading = alarmsOrNull == null,
                        lockedIds = lockedIds,
                        library = library,
                        next = next,
                        profileName = profile?.name,
                        sleep = sleepState,
                        setupDone = setupDone,
                        setupTotal = setupItems.size,
                        contentPadding = contentPadding,
                        onProfile = { showProfile = true },
                        onSetup = { showSetup = true },
                        onAdd = { newAlarm() },
                        onOpen = { if (alarmVm.canEdit(it)) editingId = it.id },
                        onToggle = { alarm, on -> alarmVm.toggle(alarm, on) },
                        onStartSleep = { showGoodNight = true },
                        onSeeSleep = { tab = Tab.SLEEP },
                        wakeCheck = wakeCheck,
                        onConfirmAwake = { alarmVm.confirmAwake() },
                        onNap = { showNap = true },
                        vacations = vacations,
                        onDaysOff = { showDaysOff = true },
                    )
                    Tab.CHALLENGES -> ChallengesScreen(
                        library = library,
                        contentPadding = contentPadding,
                        onAddQr = { startRegister(ChallengeType.QR) },
                        onAddPhoto = { startRegister(ChallengeType.PHOTO) },
                        onDelete = { deletingItem = it },
                    )
                    Tab.SLEEP -> SleepScreen(
                        state = sleepState,
                        contentPadding = contentPadding,
                        onEditProfile = { showProfile = true },
                        onGrantUsage = { sleepVm.openUsageAccess() },
                        onStartSleep = { showGoodNight = true },
                    )
                    Tab.STATS -> StatsScreen(
                        state = statsState,
                        contentPadding = contentPadding,
                    )
                    Tab.FAMILY -> FamilyTab(familyVm, contentPadding)
                }
            }
        }

        // Màn "Chúc ngủ ngon" phủ toàn màn hình (kể cả sau thanh trạng thái), nút Back để đóng.
        // Âm thanh ru ngủ vẫn phát khi đóng bằng Back; bấm "Dừng" (thức dậy, thôi ngủ) thì tắt luôn.
        if (showGoodNight) {
            LaunchedEffect(Unit) { nightVm.refresh() }
            BackHandler { showGoodNight = false }
            GoodNightScreen(
                nextWake = sleepState.nextWake,
                checks = nightChecks,
                noise = noise,
                onFix = { id ->
                    if (id == NightCheck.ID_ALARM) {
                        showGoodNight = false
                        newAlarm()
                    } else {
                        nightVm.fix(id)
                    }
                },
                onPlayNoise = { type, minutes -> nightVm.playNoise(type, minutes) },
                onStopNoise = { nightVm.stopNoise() },
                onStop = {
                    nightVm.stopNoise()
                    showGoodNight = false
                },
            )
        }
    }


    if (showSetup) {
        SetupSheet(
            items = setupItems,
            onOpen = { id ->
                if (id == "profile") {
                    showSetup = false
                    showProfile = true
                    setupAfterProfile = true // điền xong quay lại bảng thiết lập
                } else {
                    setupVm.open(id) { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                }
            },
            onToggleManual = { setupVm.toggleManual(it) },
            onTest = {
                showSetup = false
                if (!setupVm.startTestRing()) toast("Đang có chuông reo hoặc đang hoãn, thử lại sau nhé.")
            },
            onDismiss = { showSetup = false },
        )
    }

    if (showProfile) {
        fun closeProfile() {
            showProfile = false
            if (setupAfterProfile) {
                setupAfterProfile = false
                showSetup = true
            }
        }
        ProfileSheet(
            initial = profile,
            onSave = { saved ->
                sleepVm.saveProfile(saved)
                closeProfile()
            },
            onDismiss = { closeProfile() },
        )
    }

    if (showNap) {
        NapSheet(
            onStart = { minutes ->
                showNap = false
                alarmVm.startNap(minutes)
            },
            onDismiss = { showNap = false },
        )
    }

    editing?.let { current ->
        AlarmEditor(
            initial = current,
            library = library,
            onDismiss = { editingId = null },
            onToggleSkip = {
                editingId = null
                alarmVm.toggleSkip(current)
            },
            onOpenDaysOff = { showDaysOff = true },
            onDelete = if (current.id != 0) {
                {
                    editingId = null
                    alarmVm.delete(current)
                }
            } else null,
            onSave = { saved ->
                editingId = null
                // Thử thách đi bộ cần quyền nhận dạng hoạt động (Android 10+). Xin ngay khi lưu chuông.
                if (BuiltIn.STEPS_ID in saved.challengeIdList() &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                }
                alarmVm.save(saved)
            },
        )
    }

    deletingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            title = { Text("Xóa \"${item.name}\"?") },
            text = { Text("Thử thách này sẽ bị bỏ khỏi mọi chuông đang dùng nó.") },
            confirmButton = {
                TextButton(onClick = {
                    deletingItem = null
                    alarmVm.deleteChallenge(item)
                }) { Text("Xóa") }
            },
            dismissButton = { TextButton(onClick = { deletingItem = null }) { Text("Hủy") } },
        )
    }

    // Sau trình sửa chuông để nổi lên trên khi mở từ trong trình sửa.
    if (showDaysOff) {
        DaysOffSheet(
            vacations = vacations,
            onAdd = { start, end, name -> alarmVm.addVacation(start, end, name) },
            onRemove = { alarmVm.removeVacation(it) },
            onDismiss = { showDaysOff = false },
        )
    }

    registering?.let { type ->
        RegisterChallengeDialog(
            type = type,
            onDismiss = { registering = null },
            onSave = { name, param ->
                registering = null
                alarmVm.addChallenge(type, name, param)
            },
        )
    }
}
