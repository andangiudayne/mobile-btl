package com.nhom.challengealarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

/** Cho service biết màn hình thử thách có đang hiện trước mặt người dùng hay không. */
object RingState {
    @Volatile var visible: Boolean = false
    /** Đã tắt chuông, đang đóng màn hình: watchdog không được mở lại màn hình thử thách nữa. */
    @Volatile var stopping: Boolean = false
}

/**
 * Foreground service giữ chuông reo cho đến khi RingActivity báo đã hoàn thành thử thách.
 * Âm lượng tăng dần để không giật mình, có wake lock để CPU không ngủ, và cứ 1 giây kiểm tra
 * một lần: nếu người dùng thoát ra khỏi màn hình thử thách thì cố gắng mở lại.
 */
class AlarmService : Service() {

    companion object {
        const val ACTION_START = "com.nhom.challengealarm.START"
        const val ACTION_STOP = "com.nhom.challengealarm.STOP"
        const val ACTION_PAUSE = "com.nhom.challengealarm.PAUSE"
        const val ACTION_RESUME = "com.nhom.challengealarm.RESUME"
        const val EXTRA_UNTIL = "pause_until"
        private const val CHANNEL_ID = "alarm_ring"
        private const val NOTIF_ID = 1001
        private const val WATCHDOG_FIRST_DELAY_MS = 2_500L
        private const val WATCHDOG_INTERVAL_MS = 1_000L

        fun stop(context: Context) {
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_STOP))
        }

        /** Nghỉ giữa hai màn: chuông im đến thời điểm until rồi tự reo lại. */
        fun pause(context: Context, until: Long) {
            runCatching {
                context.startService(
                    Intent(context, AlarmService::class.java).setAction(ACTION_PAUSE).putExtra(EXTRA_UNTIL, until)
                )
            }
        }

        /** Người dùng bấm "Làm tiếp ngay": reo lại luôn. */
        fun resume(context: Context) {
            runCatching { context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_RESUME)) }
        }
    }

    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var volume = 0.1f
    private var ringIntent: Intent? = null
    private var notification: Notification? = null

    private val volumeRamp = object : Runnable {
        override fun run() {
            volume = (volume + 0.1f).coerceAtMost(1f)
            player?.setVolume(volume, volume)
            if (volume < 1f) handler.postDelayed(this, 3000)
        }
    }

    /**
     * Android 10+ hạn chế mở Activity từ nền, nên đây chỉ là nỗ lực tốt nhất:
     * thử mở lại màn hình thử thách và đăng lại thông báo toàn màn hình.
     * Tiếng chuông vẫn tiếp tục reo dù cách này bị chặn.
     */
    private var ticks = 0
    private var paused = false
    private var ringing = false
    private var currentAlarmId = -1
    private var currentSnoozeCount = 0
    private var currentRingtone = ""

    private val watchdog = object : Runnable {
        override fun run() {
            ticks++
            ensureAudible()
            if (ticks % 3 == 0) reArm()
            // Đang nghỉ giữa màn mà màn hình đã tắt: để yên cho người dùng nằm thêm, không bật màn hình lên.
            val screenOn = (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
            if (!RingState.visible && !RingState.stopping && (!paused || screenOn)) {
                // Có quyền "hiện trên ứng dụng khác" thì Android cho phép mở Activity từ nền.
                ringIntent?.let { runCatching { startActivity(it) } }
                if (ticks % 5 == 0) {
                    notification?.let { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, it) }
                }
            }
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    /** Đẩy chuông dự phòng lùi tiếp vài giây. Chỉ nổ thật khi tiến trình app bị giết. */
    private fun reArm() {
        AlarmScheduler.scheduleReRing(this, currentAlarmId, currentSnoozeCount, currentRingtone)
    }

    /** Người dùng vuốt app khỏi màn hình đa nhiệm: dựng lại chuông và màn hình thử thách gần như ngay. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        AlarmScheduler.scheduleReRing(this, currentAlarmId, currentSnoozeCount, currentRingtone, 1_000L)
        ringIntent?.let { runCatching { startActivity(it) } }
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private val resumeRunnable = Runnable { resumeRinging() }

    /** Tạm im chuông và rung đến thời điểm until. Service vẫn chạy nền trước, chuông dự phòng vẫn được gia hạn. */
    private fun pauseUntil(until: Long) {
        if (!ringing) return // service không đang reo (ví dụ vừa bị khởi động lại): không có gì để tạm dừng
        val wait = until - System.currentTimeMillis()
        if (wait <= 0) {
            resumeRinging()
            return
        }
        paused = true
        handler.removeCallbacks(volumeRamp)
        handler.removeCallbacks(resumeRunnable)
        runCatching { player?.pause() }
        vibrator().cancel()
        handler.postDelayed(resumeRunnable, wait)
    }

    private fun resumeRinging() {
        handler.removeCallbacks(resumeRunnable)
        if (!paused || !ringing) return
        paused = false
        volume = 0.4f
        player?.let { p ->
            p.setVolume(volume, volume)
            runCatching { p.start() }
        }
        handler.removeCallbacks(volumeRamp)
        handler.postDelayed(volumeRamp, 3000)
        vibrator().vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
        // Hết giờ nghỉ: bật lại màn hình thử thách (màn hình có thể đã tắt trong lúc nằm thêm).
        ringIntent?.let { runCatching { startActivity(it) } }
        notification?.let { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRinging()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE, ACTION_RESUME -> {
                // Service không đang reo thì không có gì để làm: dừng ngay, không để service chạy thừa.
                if (!ringing) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                if (intent.action == ACTION_PAUSE) pauseUntil(intent.getLongExtra(EXTRA_UNTIL, 0L)) else resumeRinging()
                return START_NOT_STICKY
            }
        }
        val alarmId = intent?.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1) ?: -1
        val snoozeCount = intent?.getIntExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, 0) ?: 0
        val ringtone = intent?.getStringExtra(AlarmScheduler.EXTRA_RINGTONE).orEmpty()
        // Đang reo chuông khác: chuông mới chờ, 1 phút sau thử lại (tắt xong chuông này thì chuông kia reo).
        // Không để chuông mới ghi đè phiên của chuông đang reo.
        if (ringing && alarmId != currentAlarmId) {
            AlarmScheduler.scheduleReRing(this, alarmId, snoozeCount, ringtone, 60_000L)
            return START_NOT_STICKY
        }
        // Đang phát âm thanh ru ngủ thì tắt đi, để tiếng chuông không bị lẫn.
        NoiseService.stop(this)
        RingState.stopping = false
        currentAlarmId = alarmId
        currentSnoozeCount = snoozeCount
        currentRingtone = ringtone
        // Mở phiên reo mới, hoặc làm tiếp phiên đang dở nếu app vừa bị giết và chuông dự phòng dựng lại.
        val session = RingSession.begin(this, alarmId)
        // Chế độ Gia đình: hẹn nhắn SMS cho người thân nếu N phút sau chuông vẫn chưa tắt.
        runCatching { CheckIn.onAlarmStarted(this, session) }
        reArm()
        startForegroundWithNotification(alarmId, snoozeCount)
        // App bị giết giữa giờ nghỉ rồi dựng lại: giữ im lặng đến hết giờ nghỉ, không kêu lên một tiếng.
        startRinging(ringtone, silentUntil = session.breakUntil)
        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, WATCHDOG_FIRST_DELAY_MS)
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification(alarmId: Int, snoozeCount: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Báo thức đang reo", NotificationManager.IMPORTANCE_HIGH)
            channel.setSound(null, null)
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(channel)
        }

        val intent = Intent(this, RingActivity::class.java)
            .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            .putExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, snoozeCount)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        ringIntent = intent
        val pi = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val built = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Báo thức!")
            .setContentText("Chạm để làm thử thách và tắt chuông")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()
        notification = built

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, built, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, built)
        }

        // Trên một số máy full-screen intent bị chặn, nên mở trực tiếp màn hình thử thách luôn.
        try { startActivity(intent) } catch (_: Exception) { }
    }

    private fun createPlayer(uri: Uri): MediaPlayer {
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setDataSource(this, uri)
            mp.isLooping = true
            mp.setVolume(volume, volume)
            mp.prepare()
            return mp
        } catch (e: Exception) {
            mp.release() // không đọc được nhạc này: giải phóng rồi thử nhạc khác
            throw e
        }
    }

    private fun startRinging(ringtone: String, silentUntil: Long = 0L) {
        if (ringing) return

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChallengeAlarm:ring").apply {
            acquire(60 * 60 * 1000L) // tối đa 60 phút (chuông nhiều màn có nghỉ giữa màn)
        }

        ensureAudible()
        val defaultUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val chosen = ringtone.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        // Thử lần lượt: nhạc đã chọn, nhạc báo thức mặc định, nhạc chuông, âm thông báo.
        // Không phát được gì thì vẫn rung và vẫn hiện thử thách, không để service bị lỗi.
        val candidates = listOfNotNull(
            chosen,
            defaultUri,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        )
        val ready = candidates.firstNotNullOfOrNull { uri -> runCatching { createPlayer(uri) }.getOrNull() }
        player = ready
        ringing = true
        val wait = silentUntil - System.currentTimeMillis()
        if (wait > 0) {
            paused = true
            handler.postDelayed(resumeRunnable, wait)
            return
        }
        ready?.start()
        handler.postDelayed(volumeRamp, 3000)

        vibrator().vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
    }

    /** Giữ âm lượng báo thức từ 80% trở lên để không thể hạ nhỏ bằng nút âm lượng. */
    private fun ensureAudible() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val want = (am.getStreamMaxVolume(AudioManager.STREAM_ALARM) * 0.8f).toInt().coerceAtLeast(1)
        if (am.getStreamVolume(AudioManager.STREAM_ALARM) < want) {
            runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, want, 0) }
        }
    }

    private fun vibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    private fun stopRinging() {
        AlarmScheduler.cancelReRing(this, currentAlarmId)
        handler.removeCallbacks(volumeRamp)
        handler.removeCallbacks(watchdog)
        handler.removeCallbacks(resumeRunnable)
        paused = false
        ringing = false
        player?.run { runCatching { stop() }; release() }
        player = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        vibrator().cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }
}
