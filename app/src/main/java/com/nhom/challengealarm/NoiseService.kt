package com.nhom.challengealarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Đang phát âm thanh gì và tự tắt lúc nào. */
data class NoisePlayback(val type: NoiseType, val endsAt: Long)

/**
 * Phát âm thanh ru ngủ (sinh bằng NoiseGenerator, không cần file nhạc) trong một foreground service để vẫn phát
 * khi tắt màn hình. Có hẹn giờ: to dần lúc bắt đầu, nhỏ dần trong phút cuối rồi tự tắt.
 * Chuông báo thức reo thì AlarmService gọi stop() để tiếng chuông không bị lẫn.
 */
class NoiseService : Service() {

    companion object {
        const val ACTION_PLAY = "com.nhom.challengealarm.NOISE_PLAY"
        const val ACTION_STOP = "com.nhom.challengealarm.NOISE_STOP"
        const val EXTRA_TYPE = "noise_type"
        const val EXTRA_MINUTES = "noise_minutes"
        /** Các mức hẹn giờ (phút). */
        val TIMER_OPTIONS = listOf(15, 30, 60)
        const val DEFAULT_MINUTES = 30
        private const val CHANNEL_ID = "sleep_sound"
        private const val NOTIF_ID = 4001
        private const val SAMPLE_RATE = 22_050
        /** Âm lượng tối đa của tiếng ồn (so với âm lượng nhạc của máy), để êm chứ không át. */
        private const val VOLUME = 0.35f

        private val _playing = MutableStateFlow<NoisePlayback?>(null)
        /** Giao diện theo dõi để hiện đúng trạng thái (null = không phát). */
        val playing: StateFlow<NoisePlayback?> = _playing.asStateFlow()

        fun play(context: Context, type: NoiseType, minutes: Int) {
            val intent = Intent(context, NoiseService::class.java)
                .setAction(ACTION_PLAY)
                .putExtra(EXTRA_TYPE, type.name)
                .putExtra(EXTRA_MINUTES, minutes)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            if (_playing.value == null) return
            runCatching { context.startService(Intent(context, NoiseService::class.java).setAction(ACTION_STOP)) }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null

    /** Mỗi lần phát mới tăng số này; luồng phát cũ thấy số đổi thì tự dừng. */
    @Volatile private var generation = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopPlayback()
            stopSelf()
            return START_NOT_STICKY
        }
        val type = runCatching { NoiseType.valueOf(intent?.getStringExtra(EXTRA_TYPE).orEmpty()) }
            .getOrDefault(NoiseType.RAIN)
        val minutes = (intent?.getIntExtra(EXTRA_MINUTES, DEFAULT_MINUTES) ?: DEFAULT_MINUTES).coerceIn(1, 180)
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        // Phải gọi startForeground ngay (trong vài giây) sau startForegroundService.
        startForegroundNow(type, endsAt)
        startPlayback(type, endsAt)
        return START_NOT_STICKY
    }

    private fun startForegroundNow(type: NoiseType, endsAt: Long) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Âm thanh ru ngủ", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, NoiseService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Âm thanh ru ngủ: ${type.label}")
            .setContentText("Nhỏ dần và tự tắt lúc ${hhmm(endsAt)}")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, "Tắt", stopIntent)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun buildTrack(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, SAMPLE_RATE)) // khoảng nửa giây âm thanh
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun startPlayback(type: NoiseType, endsAt: Long) {
        val myGeneration = ++generation
        val startedAt = System.currentTimeMillis()
        _playing.value = NoisePlayback(type, endsAt)
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChallengeAlarm:noise")
            .apply { acquire(endsAt - startedAt + 60_000L) }

        Thread {
            val track = runCatching { buildTrack() }.getOrNull()
            if (track != null) {
                val generator = NoiseGenerator(type)
                val buffer = ShortArray(2048)
                try {
                    track.play()
                    while (generation == myGeneration) {
                        val now = System.currentTimeMillis()
                        if (now >= endsAt) break
                        val gain = NoiseEnvelope.gain(now - startedAt, endsAt - now) * VOLUME
                        for (i in buffer.indices) {
                            buffer[i] = (generator.next() * gain * Short.MAX_VALUE).toInt().toShort()
                        }
                        if (track.write(buffer, 0, buffer.size) < 0) break
                    }
                } catch (_: Exception) {
                    // Lỗi phát âm thanh: dừng êm, không làm sập app.
                } finally {
                    runCatching { track.stop() }
                    track.release()
                }
            }
            // Hết giờ (hoặc không mở được loa) mà không có lần phát mới: tắt service.
            if (generation == myGeneration) {
                handler.post {
                    if (generation == myGeneration) {
                        stopPlayback()
                        stopSelf()
                    }
                }
            }
        }.apply {
            isDaemon = true
            name = "noise-$myGeneration"
            start()
        }
    }

    private fun stopPlayback() {
        generation++
        _playing.value = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        if (_playing.value != null) stopPlayback()
        super.onDestroy()
    }
}
