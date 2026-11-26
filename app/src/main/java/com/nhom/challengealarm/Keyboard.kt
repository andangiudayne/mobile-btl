package com.nhom.challengealarm

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.os.Build
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView

// =================================================================================================
// Bàn phím ảo. Android 11 (API 30) trở lên báo chiều cao bàn phím qua WindowInsets.ime nên imePadding() là đủ.
// Android 10 trở xuống thì không đáng tin:
//   - Màn hình chuông reo ẩn thanh trạng thái (toàn màn hình): adjustResize không chạy, WindowInsets.ime luôn bằng 0.
//   - ModalBottomSheet của Material3 nằm trong cửa sổ riêng, không nhận chiều cao bàn phím.
// Nên ở đó đo trực tiếp phần cửa sổ bị bàn phím che (getWindowVisibleDisplayFrame, cách làm quen thuộc cho
// cửa sổ toàn màn hình), rồi chừa đúng chừng đó chỗ ở dưới.
// =================================================================================================

/** Phần cửa sổ bị bàn phím che (px), chỉ dùng cho Android 10 trở xuống. 0 khi bàn phím đang ẩn. */
@Composable
private fun measuredKeyboardPx(): Int {
    val view = LocalView.current
    var px by remember { mutableIntStateOf(0) }
    DisposableEffect(view) {
        val visible = Rect()
        val location = IntArray(2)
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val root = view.rootView
            root.getWindowVisibleDisplayFrame(visible)
            root.getLocationOnScreen(location)
            val windowBottom = location[1] + root.height
            val covered = (windowBottom - visible.bottom).coerceAtLeast(0)
            // Thanh điều hướng cũng làm vùng nhìn thấy ngắn lại một chút: chỉ tính là bàn phím khi che nhiều.
            px = if (root.height > 0 && covered > root.height * KEYBOARD_MIN_FRACTION) covered else 0
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        listener.onGlobalLayout()
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    return px
}

private const val KEYBOARD_MIN_FRACTION = 0.15f

/**
 * Khoảng bàn phím đang che, dạng WindowInsets để dùng với windowInsetsPadding(): phần đã được lớp ngoài chừa
 * (ví dụ thanh điều hướng) sẽ không bị cộng hai lần.
 */
@Composable
fun keyboardInsets(): WindowInsets =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        WindowInsets.ime
    } else {
        val px = measuredKeyboardPx()
        remember(px) { WindowInsets(bottom = px) }
    }

/** Bàn phím ảo đang hiện hay không (theo đúng những gì người dùng thấy, không theo ô nhập đang được chọn). */
@Composable
fun isKeyboardVisible(): Boolean = keyboardInsets().getBottom(LocalDensity.current) > 0

/** Chừa chỗ cho bàn phím ở dưới: nội dung co lại phía trên bàn phím, vùng cuộn vẫn cuộn tới mọi nút. */
fun Modifier.keyboardPadding(): Modifier = composed { windowInsetsPadding(keyboardInsets()) }

/** WindowInsets cho ModalBottomSheet có ô nhập: chỉ thanh hệ thống, bàn phím do keyboardPadding() lo (tránh chừa hai lần). */
val SheetWindowInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars.only(WindowInsetsSides.Vertical)

/**
 * Gọi bên trong ModalBottomSheet (hoặc cửa sổ phụ khác) có ô nhập chữ. Cửa sổ của sheet mặc định "đẩy" cả khung lên
 * khi bàn phím mở (adjustPan), nên tiêu đề bị đẩy mất còn nút phía dưới vẫn bị che. Đổi sang adjustResize để không
 * bị đẩy; phần chừa chỗ do keyboardPadding() làm. Không đụng vào cửa sổ chính của Activity.
 */
@Composable
fun ResizeWindowForKeyboard() {
    val view = LocalView.current
    DisposableEffect(view) {
        runCatching {
            val root = view.rootView
            val params = root.layoutParams as? WindowManager.LayoutParams
            val activityDecor = view.context.findActivity()?.window?.decorView
            if (params != null && root !== activityDecor) {
                val mask = WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
                if (params.softInputMode and mask != WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) {
                    params.softInputMode = (params.softInputMode and mask.inv()) or
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    val wm = view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    wm.updateViewLayout(root, params)
                }
            }
        }
        onDispose { }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
