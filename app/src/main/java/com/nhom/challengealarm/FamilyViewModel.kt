package com.nhom.challengealarm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Một mã đang hiện để gửi đi (QR + chữ). message = nội dung gửi kèm qua Zalo/Messenger. */
data class ShareCode(val title: String, val steps: String, val code: String, val message: String)

data class FamilyUiState(
    val family: FamilyState = FamilyState(),
    /** Cảnh báo của mọi thành viên, nặng trước. */
    val alerts: List<FamilyAlert> = emptyList(),
    val managerName: String = "",
    val now: Long = System.currentTimeMillis(),
    /** Các tài khoản trên máy này (thử chế độ Gia đình bằng một điện thoại) và tài khoản đang dùng. */
    val accounts: List<FamilyAccount> = emptyList(),
    val currentAccount: String = FamilyRepository.DEFAULT_ACCOUNT,
)

/** ViewModel của tab Gia đình. Giao diện chỉ đọc state và gọi các hàm dưới đây. */
class FamilyViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = AppGraph.family(app)
    private val sleepRepo = AppGraph.sleep(app)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Lời mời vừa nhập, đang chờ người dùng đồng ý chia sẻ. */
    private val _pendingInvite = MutableStateFlow<FamilyCode.Invite?>(null)
    val pendingInvite: StateFlow<FamilyCode.Invite?> = _pendingInvite.asStateFlow()

    private val _share = MutableStateFlow<ShareCode?>(null)
    val share: StateFlow<ShareCode?> = _share.asStateFlow()

    val state: StateFlow<FamilyUiState> =
        combine(repo.state, sleepRepo.profile, clockFlow(30_000), repo.accounts, repo.currentAccount) { family, _, now, accounts, current ->
            FamilyUiState(
                family = family,
                alerts = family.members.flatMap { FamilyAdvisor.alerts(it, now) }.sortedByDescending { it.level.ordinal },
                managerName = managerName(),
                now = now,
                accounts = accounts,
                currentAccount = current,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            FamilyUiState(family = repo.state.value, accounts = repo.accounts.value, currentAccount = repo.currentAccount.value),
        )

    /** Tên người gửi trong mã: tên tài khoản khi đang thử nhiều tài khoản, không thì tên trong hồ sơ. */
    private fun managerName(): String =
        if (repo.accounts.value.size > 1) repo.currentAccountName()
        else sleepRepo.profile.value?.name?.takeIf { it.isNotBlank() } ?: "Người thân"

    fun switchAccount(id: String) = repo.switchAccount(id)

    /** Tạo 2 tài khoản thử "Con" và "Bố" trên máy này. */
    fun createTestPair() {
        repo.createTestPair()
        _messages.tryEmit("Đã tạo 2 tài khoản: Con (chăm sóc, đang dùng) và Bố (được chăm sóc). Thêm Bố để bắt đầu.")
    }

    fun removeTestAccounts() {
        repo.removeTestAccounts()
        _messages.tryEmit("Đã bỏ các tài khoản thử.")
    }

    /**
     * Thử trên một máy: chuyển sang tài khoản khác và nhập luôn mã đang hiện, thay cho việc quét QR hoặc gửi qua Zalo
     * sang một điện thoại thứ hai.
     */
    fun deliverTo(accountId: String) {
        val code = _share.value?.code ?: return
        _share.value = null
        repo.switchAccount(accountId)
        _messages.tryEmit("Đã chuyển sang tài khoản ${repo.currentAccountName()}.")
        importCode(code)
    }

    fun becomeManager() = repo.setRole(FamilyRole.MANAGER)

    fun resetRole() {
        if (repo.state.value.role == FamilyRole.MEMBER) repo.leave() else repo.setRole(FamilyRole.NONE)
    }

    /** Mở app hoặc quay lại app: người có sẵn trong gia đình có đêm mới nếu đã sang sáng mới. */
    fun refreshDaily() = repo.refreshDaily()

    fun addMember(name: String, relation: String, kind: MemberKind, birthYear: Int) {
        val m = repo.addMember(name, relation, kind, birthYear)
        showInvite(m)
    }

    fun updatePlan(m: FamilyMember, plan: FamilyPlan, send: Boolean) {
        val updated = m.copy(plan = plan)
        repo.updateMember(updated)
        if (send) showPlan(updated) else _messages.tryEmit("Đã lưu kế hoạch của ${m.name}.")
    }

    fun removeMember(m: FamilyMember) {
        repo.removeMember(m.id)
        _messages.tryEmit("Đã xóa ${m.name} khỏi danh sách.")
    }

    fun simulate(id: String, scenario: FamilyScenario) {
        val m = repo.simulate(id, scenario) ?: return
        // Thông báo như khi nhận báo cáo thật, để thấy cảnh báo khẩn cấp trên thanh thông báo trông thế nào.
        FamilyNotifier.notifyUrgent(app, m, System.currentTimeMillis())
    }

    fun showInvite(m: FamilyMember) {
        val code = repo.inviteCode(m, managerName())
        _share.value = ShareCode(
            title = "Mời ${m.name} ghép nối",
            steps = "Trên điện thoại của ${m.name}: mở Challenge Alarm > Gia đình > \"Tôi được gia đình chăm sóc\", " +
                "rồi quét mã này hoặc dán mã gửi qua Zalo.",
            code = code,
            message = "${managerName()} mời ${m.name} chia sẻ giấc ngủ trên Challenge Alarm.\n" +
                "Mở app > Gia đình > Nhập mã, dán mã sau:\n$code",
        )
    }

    fun showPlan(m: FamilyMember) {
        val code = repo.planCode(m, managerName())
        _share.value = ShareCode(
            title = "Kế hoạch ngủ cho ${m.name}",
            steps = "Trên điện thoại của ${m.name}: Gia đình > \"Nhập kế hoạch mới\", quét hoặc dán mã. Chuông sẽ tự đổi theo.",
            code = code,
            message = "Kế hoạch ngủ mới cho ${m.name}: ngủ ${SleepAdvisor.clockText(m.plan.bedtimeMin)}, " +
                "dậy ${SleepAdvisor.clockText(m.plan.wakeMin)}.\nMở Challenge Alarm > Gia đình > Nhập kế hoạch mới, dán mã:\n$code",
        )
    }

    fun showMyReport() {
        viewModelScope.launch {
            val me = repo.state.value.me ?: return@launch
            val code = runCatching { repo.myReportCode() }.getOrNull()
            if (code == null) {
                _messages.tryEmit("Chưa tạo được báo cáo.")
                return@launch
            }
            _share.value = ShareCode(
                title = "Báo cáo giấc ngủ gửi ${me.managerName}",
                steps = "${me.managerName} mở Challenge Alarm > Gia đình > \"Nhập mã\" rồi quét mã này, " +
                    "hoặc bạn gửi mã qua Zalo/Messenger.",
                code = code,
                message = "Báo cáo giấc ngủ của ${me.name} (Challenge Alarm). Mở app > Gia đình > Nhập mã, dán mã sau:\n$code",
            )
        }
    }

    fun dismissShare() {
        _share.value = null
    }

    /** Nhập mã từ ô dán hoặc từ QR. */
    fun importCode(text: String) {
        viewModelScope.launch {
            when (val r = runCatching { repo.importCode(text) }.getOrElse { ImportResult.Error("Mã bị lỗi, thử lại nhé.") }) {
                is ImportResult.ReportAdded -> _messages.tryEmit(
                    if (r.isNew) "Đã thêm ${r.member.name} và báo cáo ${r.member.nights.size} đêm."
                    else "Đã cập nhật báo cáo của ${r.member.name}."
                )
                is ImportResult.InviteReceived -> _pendingInvite.value = r.invite
                is ImportResult.PlanApplied -> _messages.tryEmit(
                    "Đã cập nhật kế hoạch: ngủ ${SleepAdvisor.clockText(r.plan.bedtimeMin)}, " +
                        "chuông ${SleepAdvisor.clockText(r.plan.wakeMin)}."
                )
                is ImportResult.Error -> _messages.tryEmit(r.message)
            }
        }
    }

    fun acceptInvite() {
        val invite = _pendingInvite.value ?: return
        _pendingInvite.value = null
        viewModelScope.launch {
            runCatching { repo.acceptInvite(invite) }
                .onSuccess {
                    _messages.tryEmit(
                        "Đã ghép nối với ${invite.managerName}. Chuông \"${FamilyRepository.LABEL}\" " +
                            "${SleepAdvisor.clockText(invite.plan.wakeMin)} đã được tạo."
                    )
                }
                .onFailure { _messages.tryEmit("Chưa ghép nối được, thử lại nhé.") }
        }
    }

    fun declineInvite() {
        _pendingInvite.value = null
    }

    fun leave() {
        repo.leave()
        _messages.tryEmit("Đã ngừng chia sẻ giấc ngủ. Chuông đã tạo vẫn giữ, bạn có thể tự xóa.")
    }

    fun setSms(sms: SmsCheckIn) = repo.setSms(sms)
}
