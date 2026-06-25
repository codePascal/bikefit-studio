package bikefitstudio.biomechanics

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CycleMetricsTest {

    // ==================== AngleStats Tests ====================

    @Test
    fun `AngleStats INVALID has zero values`() {
        val invalid = AngleStats.INVALID
        assertEquals(0f, invalid.min, 0.001f)
        assertEquals(0f, invalid.max, 0.001f)
        assertEquals(0f, invalid.average, 0.001f)
        assertEquals(0, invalid.sampleCount)
        assertFalse(invalid.isValid)
    }

    @Test
    fun `AngleStats fromValues calculates correct statistics`() {
        val values = listOf(10f, 20f, 30f, 40f, 50f)
        val stats = AngleStats.fromValues(values)

        assertEquals(10f, stats.min, 0.001f)
        assertEquals(50f, stats.max, 0.001f)
        assertEquals(30f, stats.average, 0.001f)
        assertEquals(5, stats.sampleCount)
        assertTrue(stats.isValid)
    }

    @Test
    fun `AngleStats fromValues calculates standard deviation`() {
        val values = listOf(10f, 20f, 30f, 40f, 50f)
        val stats = AngleStats.fromValues(values)
        
        // Expected std dev for this sequence is ~14.14
        assertTrue(stats.standardDeviation > 14f)
        assertTrue(stats.standardDeviation < 15f)
    }

    @Test
    fun `AngleStats fromValues with single value has zero std dev`() {
        val stats = AngleStats.fromValues(listOf(42f))
        assertEquals(0f, stats.standardDeviation, 0.001f)
    }

    @Test
    fun `AngleStats fromValues with identical values has zero std dev`() {
        val stats = AngleStats.fromValues(listOf(10f, 10f, 10f, 10f))
        assertEquals(0f, stats.standardDeviation, 0.001f)
    }

    @Test
    fun `AngleStats range is max minus min`() {
        val stats = AngleStats(min = 25f, max = 75f, average = 50f, sampleCount = 10)
        assertEquals(50f, stats.range, 0.001f)
    }

    @Test
    fun `AngleStats fromValues with empty list returns INVALID`() {
        val stats = AngleStats.fromValues(emptyList())
        assertEquals(AngleStats.INVALID, stats)
    }

    @Test
    fun `AngleStats fromValues with single value`() {
        val stats = AngleStats.fromValues(listOf(42f))
        assertEquals(42f, stats.min, 0.001f)
        assertEquals(42f, stats.max, 0.001f)
        assertEquals(42f, stats.average, 0.001f)
        assertEquals(1, stats.sampleCount)
        assertEquals(0f, stats.range, 0.001f)
    }

    @Test
    fun `AngleStats isValid is true when sampleCount greater than zero`() {
        val valid = AngleStats(min = 0f, max = 0f, average = 0f, sampleCount = 1)
        assertTrue(valid.isValid)

        val invalid = AngleStats(min = 0f, max = 0f, average = 0f, sampleCount = 0)
        assertFalse(invalid.isValid)
    }

    // ==================== CycleMetrics Tests ====================

    @Test
    fun `CycleMetrics durationMs calculates correctly`() {
        val metrics = createTestMetrics(
            startTimestampMs = 1000,
            endTimestampMs = 1500
        )
        assertEquals(500L, metrics.durationMs)
    }

    @Test
    fun `CycleMetrics frameCount calculates correctly`() {
        val metrics = createTestMetrics(
            startFrameNumber = 100,
            endFrameNumber = 130
        )
        assertEquals(30L, metrics.frameCount)
    }

    @Test
    fun `CycleMetrics cadenceRpm calculates correctly for 1 second cycle`() {
        // 1 second per revolution = 60 RPM
        val metrics = createTestMetrics(
            startTimestampMs = 0,
            endTimestampMs = 1000
        )
        assertEquals(60f, metrics.cadenceRpm!!, 0.1f)
    }

    @Test
    fun `CycleMetrics cadenceRpm calculates correctly for 0_5 second cycle`() {
        // 0.5 seconds per revolution = 120 RPM
        val metrics = createTestMetrics(
            startTimestampMs = 0,
            endTimestampMs = 500
        )
        assertEquals(120f, metrics.cadenceRpm!!, 0.1f)
    }

    @Test
    fun `CycleMetrics cadenceRpm returns null for zero duration`() {
        val metrics = createTestMetrics(
            startTimestampMs = 1000,
            endTimestampMs = 1000
        )
        assertNull(metrics.cadenceRpm)
    }

    @Test
    fun `CycleMetrics isValid when has knee measurements and frames`() {
        val validKnee = AngleStats(min = 30f, max = 90f, average = 60f, sampleCount = 10)
        val metrics = CycleMetrics(
            cycleNumber = 0,
            startFrameNumber = 0,
            endFrameNumber = 30,
            startTimestampMs = 0,
            endTimestampMs = 1000,
            kneeAngle = validKnee,
            hipAngle = AngleStats.INVALID,
            torsoAngle = AngleStats.INVALID,
            ankleAngle = AngleStats.INVALID,
            kneeAngleAtBdc = null,
            kneeAngleAtTdc = null,
            hipAngleAtTdc = null,
            ankleAngleAtBdc = null,
            kopsNormalized = null,
            side = BodySide.LEFT
        )
        assertTrue(metrics.isValid)
    }

    @Test
    fun `CycleMetrics isInvalid when no knee measurements`() {
        val metrics = CycleMetrics(
            cycleNumber = 0,
            startFrameNumber = 0,
            endFrameNumber = 30,
            startTimestampMs = 0,
            endTimestampMs = 1000,
            kneeAngle = AngleStats.INVALID,
            hipAngle = AngleStats.INVALID,
            torsoAngle = AngleStats.INVALID,
            ankleAngle = AngleStats.INVALID,
            kneeAngleAtBdc = null,
            kneeAngleAtTdc = null,
            hipAngleAtTdc = null,
            ankleAngleAtBdc = null,
            kopsNormalized = null,
            side = BodySide.LEFT
        )
        assertFalse(metrics.isValid)
    }

    @Test
    fun `CycleMetrics invalid creates proper invalid result`() {
        val invalid = CycleMetrics.invalid(BodySide.RIGHT)
        assertEquals(-1, invalid.cycleNumber)
        assertFalse(invalid.isValid)
        assertEquals(BodySide.RIGHT, invalid.side)
    }

    // ==================== CycleSummary Tests ====================

    @Test
    fun `CycleSummary invalid creates proper invalid result`() {
        val invalid = CycleSummary.invalid(BodySide.LEFT)
        assertEquals(0, invalid.cycleCount)
        assertFalse(invalid.isValid)
        assertNull(invalid.averageKneeAngleAtBdc)
        assertNull(invalid.averageKneeAngleAtTdc)
    }

    @Test
    fun `CycleSummary isValid when cycleCount greater than zero`() {
        val valid = CycleSummary(
            cycleCount = 5,
            averageKneeAngleAtBdc = 30f,
            averageKneeAngleAtTdc = 90f,
            averageKneeAngleRange = 60f,
            averageHipAngleAtTdc = 70f,
            averageTorsoAngle = 45f,
            averageCadenceRpm = 80f,
            kneeAngleAtBdcStats = AngleStats.INVALID,
            kneeAngleAtTdcStats = AngleStats.INVALID,
            hipAngleAtTdcStats = AngleStats.INVALID,
            torsoAngleStats = AngleStats.INVALID,
            averageAnkleAngleAtBdc = null,
            ankleAngleAtBdcStats = AngleStats.INVALID,
            averageKopsNormalized = null,
            kopsNormalizedStats = AngleStats.INVALID,
            side = BodySide.LEFT
        )
        assertTrue(valid.isValid)
    }

    @Test
    fun `CycleSummary meetsQualityThreshold with sufficient cycles and quality`() {
        val summary = CycleSummary(
            cycleCount = 5,
            averageKneeAngleAtBdc = 30f,
            averageKneeAngleAtTdc = 90f,
            averageKneeAngleRange = 60f,
            averageHipAngleAtTdc = 70f,
            averageTorsoAngle = 45f,
            averageCadenceRpm = 80f,
            kneeAngleAtBdcStats = AngleStats.INVALID,
            kneeAngleAtTdcStats = AngleStats.INVALID,
            hipAngleAtTdcStats = AngleStats.INVALID,
            torsoAngleStats = AngleStats.INVALID,
            averageAnkleAngleAtBdc = null,
            ankleAngleAtBdcStats = AngleStats.INVALID,
            averageKopsNormalized = null,
            kopsNormalizedStats = AngleStats.INVALID,
            side = BodySide.LEFT,
            dataQuality = 0.8f
        )
        assertTrue(summary.meetsQualityThreshold)
    }

    @Test
    fun `CycleSummary does not meet quality threshold with low quality`() {
        val summary = CycleSummary(
            cycleCount = 5,
            averageKneeAngleAtBdc = 30f,
            averageKneeAngleAtTdc = 90f,
            averageKneeAngleRange = 60f,
            averageHipAngleAtTdc = 70f,
            averageTorsoAngle = 45f,
            averageCadenceRpm = 80f,
            kneeAngleAtBdcStats = AngleStats.INVALID,
            kneeAngleAtTdcStats = AngleStats.INVALID,
            hipAngleAtTdcStats = AngleStats.INVALID,
            torsoAngleStats = AngleStats.INVALID,
            averageAnkleAngleAtBdc = null,
            ankleAngleAtBdcStats = AngleStats.INVALID,
            averageKopsNormalized = null,
            kopsNormalizedStats = AngleStats.INVALID,
            side = BodySide.LEFT,
            dataQuality = 0.3f
        )
        assertFalse(summary.meetsQualityThreshold)
    }

    @Test
    fun `CycleSummary does not meet quality threshold with too few cycles`() {
        val summary = CycleSummary(
            cycleCount = 2,
            averageKneeAngleAtBdc = 30f,
            averageKneeAngleAtTdc = 90f,
            averageKneeAngleRange = 60f,
            averageHipAngleAtTdc = 70f,
            averageTorsoAngle = 45f,
            averageCadenceRpm = 80f,
            kneeAngleAtBdcStats = AngleStats.INVALID,
            kneeAngleAtTdcStats = AngleStats.INVALID,
            hipAngleAtTdcStats = AngleStats.INVALID,
            torsoAngleStats = AngleStats.INVALID,
            averageAnkleAngleAtBdc = null,
            ankleAngleAtBdcStats = AngleStats.INVALID,
            averageKopsNormalized = null,
            kopsNormalizedStats = AngleStats.INVALID,
            side = BodySide.LEFT,
            dataQuality = 0.8f
        )
        assertFalse(summary.meetsQualityThreshold)
    }

    // ==================== Helper Functions ====================

    private fun createTestMetrics(
        cycleNumber: Int = 0,
        startFrameNumber: Long = 0,
        endFrameNumber: Long = 30,
        startTimestampMs: Long = 0,
        endTimestampMs: Long = 1000
    ): CycleMetrics {
        return CycleMetrics(
            cycleNumber = cycleNumber,
            startFrameNumber = startFrameNumber,
            endFrameNumber = endFrameNumber,
            startTimestampMs = startTimestampMs,
            endTimestampMs = endTimestampMs,
            kneeAngle = AngleStats(min = 30f, max = 90f, average = 60f, sampleCount = 30),
            hipAngle = AngleStats(min = 65f, max = 75f, average = 70f, sampleCount = 30),
            torsoAngle = AngleStats(min = 40f, max = 50f, average = 45f, sampleCount = 30),
            ankleAngle = AngleStats.INVALID,
            kneeAngleAtBdc = 30f,
            kneeAngleAtTdc = 90f,
            hipAngleAtTdc = null,
            ankleAngleAtBdc = null,
            kopsNormalized = null,
            side = BodySide.LEFT
        )
    }
}
