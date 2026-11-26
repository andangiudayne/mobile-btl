package com.nhom.challengealarm

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoiseTest {

    private fun samples(type: NoiseType, n: Int = 20_000): List<Float> {
        val g = NoiseGenerator(type, seed = 42)
        return List(n) { g.next() }
    }

    /** Trung bình độ chênh giữa hai mẫu liền nhau: càng nhỏ thì âm càng trầm, càng "êm". */
    private fun roughness(xs: List<Float>): Double = xs.zipWithNext { a, b -> abs(b - a).toDouble() }.average()

    @Test
    fun samplesStayInRange() {
        NoiseType.values().forEach { t -> assertTrue(samples(t).all { it in -1f..1f }) }
    }

    @Test
    fun noiseIsNotSilent() {
        NoiseType.values().forEach { t -> assertTrue(samples(t).map { abs(it) }.average() > 0.01) }
    }

    @Test
    fun rainIsSmootherThanFanSmootherThanWhite() {
        val white = roughness(samples(NoiseType.WHITE))
        val fan = roughness(samples(NoiseType.FAN))
        val rain = roughness(samples(NoiseType.RAIN))
        assertTrue("ồn nâu phải trầm hơn ồn hồng", rain < fan)
        assertTrue("ồn hồng phải êm hơn ồn trắng", fan < white)
    }

    @Test
    fun envelopeFadesInAndOut() {
        assertEquals(0f, NoiseEnvelope.gain(0, 30 * 60_000L), 0.001f)
        assertEquals(1f, NoiseEnvelope.gain(60_000L, 30 * 60_000L), 0.001f)
        assertEquals(0.5f, NoiseEnvelope.gain(10 * 60_000L, 30_000L), 0.001f)
        assertEquals(0f, NoiseEnvelope.gain(10 * 60_000L, 0L), 0.001f)
    }
}
