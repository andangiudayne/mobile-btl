package com.nhom.challengealarm

import kotlin.random.Random

/**
 * Âm thanh ru ngủ, sinh bằng code (không cần file nhạc). Vấn đề: phòng ồn (ký túc xá, nhà gần đường) làm khó vào giấc;
 * một tiếng ồn đều và êm che bớt những tiếng động bất chợt.
 * WHITE = ồn trắng (mọi tần số như nhau, che tiếng ồn tốt nhất nhưng hơi rít),
 * FAN = ồn hồng (giảm dần ở tần số cao, êm như quạt máy), RAIN = ồn nâu (trầm như mưa rào, thác nước).
 */
enum class NoiseType(val label: String, val hint: String) {
    RAIN("Mưa", "Trầm, đều như mưa rào"),
    FAN("Quạt", "Êm như tiếng quạt máy"),
    WHITE("Ồn trắng", "Che tiếng ồn tốt nhất"),
}

/** Sinh từng mẫu âm thanh trong khoảng -1..1. Hàm thuần nên test được trên JVM. */
class NoiseGenerator(private val type: NoiseType, seed: Long = System.nanoTime()) {
    private val random = Random(seed)

    // Bộ lọc ồn hồng của Paul Kellet (bản "refined").
    private var b0 = 0.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var b3 = 0.0
    private var b4 = 0.0
    private var b5 = 0.0
    private var b6 = 0.0

    // Ồn nâu: cộng dồn ồn trắng có rò rỉ để không trôi ra ngoài biên.
    private var brown = 0.0

    fun next(): Float {
        val white = random.nextDouble() * 2.0 - 1.0
        val v = when (type) {
            NoiseType.WHITE -> white * 0.5
            NoiseType.FAN -> {
                b0 = 0.99886 * b0 + white * 0.0555179
                b1 = 0.99332 * b1 + white * 0.0750759
                b2 = 0.96900 * b2 + white * 0.1538520
                b3 = 0.86650 * b3 + white * 0.3104856
                b4 = 0.55000 * b4 + white * 0.5329522
                b5 = -0.7616 * b5 - white * 0.0168980
                val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362
                b6 = white * 0.115926
                pink * 0.11
            }
            NoiseType.RAIN -> {
                brown = (brown + 0.02 * white) / 1.02
                brown * 3.5
            }
        }
        return v.toFloat().coerceIn(-1f, 1f)
    }
}

/** Âm lượng theo thời gian: to dần khi bắt đầu, nhỏ dần trước khi tắt để không làm giật mình tỉnh giấc. */
object NoiseEnvelope {
    const val FADE_IN_MS = 8_000L
    const val FADE_OUT_MS = 60_000L

    fun gain(elapsedMs: Long, remainingMs: Long): Float {
        val fadeIn = (elapsedMs.toFloat() / FADE_IN_MS).coerceIn(0f, 1f)
        val fadeOut = (remainingMs.toFloat() / FADE_OUT_MS).coerceIn(0f, 1f)
        return minOf(fadeIn, fadeOut)
    }
}
