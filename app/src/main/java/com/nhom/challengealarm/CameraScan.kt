package com.nhom.challengealarm

import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.concurrent.Executors

enum class ScanMode { BARCODE, OBJECT }

data class LabelResult(val text: String, val confidence: Float)

/**
 * Khung camera + phân tích ảnh trực tiếp (CameraX + ML Kit, chạy offline).
 * BARCODE: trả về nội dung mã QR/mã vạch quét được.
 * OBJECT: trả về danh sách nhãn vật thể nhận diện được trong khung hình.
 */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun CameraScanner(
    mode: ScanMode,
    modifier: Modifier = Modifier,
    onBarcode: (String) -> Unit = {},
    onLabels: (List<LabelResult>) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val barcodeCallback by rememberUpdatedState(onBarcode)
    val labelsCallback by rememberUpdatedState(onLabels)
    val previewView = remember { PreviewView(context) }

    DisposableEffect(mode, lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val barcodeScanner = if (mode == ScanMode.BARCODE) BarcodeScanning.getClient() else null
        val labeler = if (mode == ScanMode.OBJECT) {
            ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.6f).build())
        } else null

        var lastRun = 0L
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(executor) { proxy ->
            val media = proxy.image
            val now = SystemClock.elapsedRealtime()
            if (media == null || now - lastRun < 400) {
                proxy.close()
                return@setAnalyzer
            }
            lastRun = now
            val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
            if (barcodeScanner != null) {
                barcodeScanner.process(image)
                    .addOnSuccessListener { codes ->
                        codes.firstNotNullOfOrNull { it.rawValue }?.let { barcodeCallback(it) }
                    }
                    .addOnCompleteListener { proxy.close() }
            } else if (labeler != null) {
                labeler.process(image)
                    .addOnSuccessListener { labels ->
                        labelsCallback(labels.map { LabelResult(it.text, it.confidence) })
                    }
                    .addOnCompleteListener { proxy.close() }
            } else {
                proxy.close()
            }
        }

        var provider: ProcessCameraProvider? = null
        var disposed = false
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            // Màn này có thể đã đóng (ví dụ vừa bấm "Đổi sang giải toán") trước khi camera sẵn sàng:
            // không mở camera nữa, tránh camera chạy ngầm. Máy không có camera sau hoặc camera đang bận thì bỏ qua,
            // không làm sập app lúc chuông đang reo (vẫn còn nút "Đổi sang giải toán").
            if (disposed) return@addListener
            runCatching {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            provider?.unbindAll()
            analysis.clearAnalyzer()
            executor.shutdown()
            barcodeScanner?.close()
            labeler?.close()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
