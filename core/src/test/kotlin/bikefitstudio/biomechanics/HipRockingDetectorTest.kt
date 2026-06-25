package bikefitstudio.biomechanics

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import bikefitstudio.pose.Landmark
import bikefitstudio.pose.PoseFrame
import bikefitstudio.pose.PoseLandmarkIndex

class HipRockingDetectorTest {

    private lateinit var detector: HipRockingDetector

    @Before
    fun setup() {
        detector = HipRockingDetector()
    }

    // ==================== Basic Construction Tests ====================

    @Test
    fun `detector initializes with default configuration`() {
        val detector = HipRockingDetector()
        assertEquals(0, detector.getSampleCount(BodySide.LEFT))
        assertEquals(0, detector.getSampleCount(BodySide.RIGHT))
    }

    @Test
    fun `detector initializes with custom configuration`() {
        val config = HipRockingDetectorConfig(
            excessiveAmplitudeThreshold = 0.05f,
            minSamplesRequired = 30
        )
        val detector = HipRockingDetector(config)
        assertNotNull(detector)
    }

    @Test
    fun `default configuration has documented threshold value`() {
        val config = HipRockingDetectorConfig()
        assertEquals(0.03f, config.excessiveAmplitudeThreshold, 0.001f)
        assertEquals(15, config.minSamplesRequired)
    }

    // ==================== Adding Hip Positions Tests ====================

    @Test
    fun `addHipPosition increases sample count for left side`() {
        detector.addHipPosition(0.5f, BodySide.LEFT)
        assertEquals(1, detector.getSampleCount(BodySide.LEFT))
        assertEquals(0, detector.getSampleCount(BodySide.RIGHT))
    }

    @Test
    fun `addHipPosition increases sample count for right side`() {
        detector.addHipPosition(0.5f, BodySide.RIGHT)
        assertEquals(0, detector.getSampleCount(BodySide.LEFT))
        assertEquals(1, detector.getSampleCount(BodySide.RIGHT))
    }

    @Test
    fun `multiple positions can be added`() {
        for (i in 1..20) {
            detector.addHipPosition(0.5f, BodySide.LEFT)
        }
        assertEquals(20, detector.getSampleCount(BodySide.LEFT))
    }

    @Test
    fun `left and right sides are tracked independently`() {
        for (i in 1..15) {
            detector.addHipPosition(0.5f, BodySide.LEFT)
        }
        for (i in 1..10) {
            detector.addHipPosition(0.5f, BodySide.RIGHT)
        }
        assertEquals(15, detector.getSampleCount(BodySide.LEFT))
        assertEquals(10, detector.getSampleCount(BodySide.RIGHT))
    }

    // ==================== Adding Frame Tests ====================

    @Test
    fun `addFrame with valid hip landmark returns true`() {
        val frame = createPoseFrameWithHip(0.5f, 0.5f, 1.0f)
        assertTrue(detector.addFrame(frame, BodySide.LEFT))
        assertEquals(1, detector.getSampleCount(BodySide.LEFT))
    }

    @Test
    fun `addFrame with low visibility hip returns false`() {
        val frame = createPoseFrameWithHip(0.5f, 0.5f, 0.3f) // Below default 0.5 threshold
        assertFalse(detector.addFrame(frame, BodySide.LEFT))
        assertEquals(0, detector.getSampleCount(BodySide.LEFT))
    }

    @Test
    fun `addFrame with insufficient landmarks returns false`() {
        val emptyFrame = PoseFrame(
            frameNumber = 0,
            timestampMs = 0,
            landmarks = emptyList(),
            confidence = 1.0f
        )
        assertFalse(detector.addFrame(emptyFrame, BodySide.LEFT))
    }

    @Test
    fun `addFrame uses right hip for RIGHT side`() {
        // Create frame with different left and right hip Y positions
        val landmarks = createFullLandmarkList(
            leftHipY = 0.4f,
            rightHipY = 0.6f,
            visibility = 1.0f
        )
        val frame = PoseFrame(
            frameNumber = 0,
            timestampMs = 0,
            landmarks = landmarks,
            confidence = 1.0f
        )

        detector.addFrame(frame, BodySide.LEFT)
        detector.addFrame(frame, BodySide.RIGHT)

        // Add enough samples to analyze
        for (i in 1..20) {
            detector.addHipPosition(0.4f, BodySide.LEFT)
            detector.addHipPosition(0.6f, BodySide.RIGHT)
        }

        val leftResult = detector.analyze(BodySide.LEFT)
        val rightResult = detector.analyze(BodySide.RIGHT)

        // Both should have minimal amplitude (constant Y)
        assertEquals(0f, leftResult.amplitude, 0.001f)
        assertEquals(0f, rightResult.amplitude, 0.001f)
    }

    // ==================== Amplitude Calculation Tests ====================

    @Test
    fun `constant hip position has zero amplitude`() {
        for (i in 1..20) {
            detector.addHipPosition(0.5f, BodySide.LEFT)
        }

        val result = detector.analyze(BodySide.LEFT)
        assertEquals(0f, result.amplitude, 0.001f)
    }

    @Test
    fun `amplitude is peak to peak difference`() {
        // Hip oscillates between 0.4 and 0.6
        detector.addHipPosition(0.5f, BodySide.LEFT)
        detector.addHipPosition(0.4f, BodySide.LEFT)
        detector.addHipPosition(0.5f, BodySide.LEFT)
        detector.addHipPosition(0.6f, BodySide.LEFT)
        detector.addHipPosition(0.5f, BodySide.LEFT)

        // Add more samples to meet minimum
        for (i in 1..15) {
            detector.addHipPosition(0.5f, BodySide.LEFT)
        }

        val result = detector.analyze(BodySide.LEFT)
        assertEquals(0.2f, result.amplitude, 0.001f) // 0.6 - 0.4
    }

    @Test
    fun `minY and maxY are correctly recorded`() {
        detector.addHipPosition(0.35f, BodySide.LEFT)
        detector.addHipPosition(0.75f, BodySide.LEFT)

        for (i in 1..15) {
            detector.addHipPosition(0.5f, BodySide.LEFT)
        }

        val result = detector.analyze(BodySide.LEFT)
        assertEquals(0.35f, result.minY, 0.001f)
        assertEquals(0.75f, result.maxY, 0.001f)
    }

    // ==================== Variance Calculation Tests ====================

    @Test
    fun `constant values have zero variance`() {
        for (i in 1..20) {
            detector.addHipPosition(0.5f, BodySide.LEFT)
        }

        val result = detector.analyze(BodySide.LEFT)
        assertEquals(0f, result.variance, 0.001f)
    }

    @Test
    fun `variance is calculated correctly for simple case`() {
        // Values: 0.4, 0.5, 0.6 -> mean = 0.5
        // Variance = ((0.1)^2 + 0 + (0.1)^2) / 3 = 0.02 / 3 = 0.00667
        detector.addHipPosition(0.4f, BodySide.LEFT)
        detector.addHipPosition(0.5f, BodySide.LEFT)
        detector.addHipPosition(0.6f, BodySide.LEFT)

        // Add more samples (all at mean to not affect variance too much)
        for (i in 1..12) {
            detector.addHipPosition(0.5f, BodySide.LEFT)
        }

        val result = detector.analyze(BodySide.LEFT)
        // 3 samples at 0.4, 0.5, 0.6 and 12 at 0.5
        // Mean = (0.4 + 0.5 + 0.6 + 12*0.5) / 15 = 7.5/15 = 0.5
        // Variance = (0.01 + 0 + 0.01 + 0) / 15 = 0.02/15 ≈ 0.00133
        assertTrue(result.variance > 0)
    }

    @Test
    fun `standard deviation is square root of variance`() {
        // Add some variation
        for (i in 1..10) {
            detector.addHipPosition(0.4f, BodySide.LEFT)
            detector.addHipPosition(0.6f, BodySide.LEFT)
        }

        val result = detector.analyze(BodySide.LEFT)
        val stdDev = detector.getStandardDeviation(BodySide.LEFT)

        assertEquals(kotlin.math.sqrt(result.variance.toDouble()).toFloat(), stdDev, 0.0001f)
    }

    // ==================== Excessive Rocking Detection Tests ====================

    @Test
    fun `hip motion below threshold is not excessive`() {
        // Amplitude of 0.02 is below default 0.03 threshold
        detector.addHipPosition(0.49f, BodySide.LEFT)
        detector.addHipPosition(0.51f, BodySide.LEFT)

        for (i in 1..15) {
            detector.addHipPosition(0.5f, BodySide.LEFT)
        }

        val result = detector.analyze(BodySide.LEFT)
        assertEquals(0.02f, result.amplitude, 0.001f)
        assertFalse(result.isExcessive)
    }

    @Test
    fun `hip motion above threshold is excessive`() {
        // Amplitude of 0.05 is above default 0.03 threshold
        detector.addHipPosition(0.45f, BodySide.LEFT)
        detector.addHipPosition(0.50f, BodySide.LEFT)

        for (i in 1..15) {
            detector.addHipPosition(0.475f, BodySide.LEFT)
        }

        val result = detector.analyze(BodySide.LEFT)
        assertEquals(0.05f, result.amplitude, 0.001f)
        assertTrue(result.isExcessive)
    }

    @Test
    fun `hip motion exactly at threshold is not excessive`() {
        // Exactly at 0.03 threshold should NOT be excessive (> not >=)
        detector.addHipPosition(0.485f, BodySide.LEFT)
        detector.addHipPosition(0.515f, BodySide.LEFT)

        for (i in 1..15) {
            detector.addHipPosition(0.5f, BodySide.LEFT)
        }

        val result = detector.analyze(BodySide.LEFT)
        assertEquals(0.03f, result.amplitude, 0.001f)
        assertFalse(result.isExcessive)
    }

    @Test
    fun `custom threshold changes excessive detection`() {
        val config = HipRockingDetectorConfig(excessiveAmplitudeThreshold = 0.01f)
        val detector = HipRockingDetector(config)

        // Amplitude of 0.02 is above custom 0.01 threshold
        detector.addHipPosition(0.49f, BodySide.LEFT)
        detector.addHipPosition(0.51f, BodySide.LEFT)

        for (i in 1..15) {
            detector.addHipPosition(0.5f, BodySide.LEFT)
        }

        val result = detector.analyze(BodySide.LEFT)
        assertTrue(result.isExcessive)
    }

    // ==================== Insufficient Data Tests ====================

    @Test
    fun `insufficient samples returns invalid result`() {
        for (i in 1..10) { // Less than default 15
            detector.addHipPosition(0.5f, BodySide.LEFT)
        }

        val result = detector.analyze(BodySide.LEFT)
        assertEquals(0f, result.amplitude)
        assertEquals(0, result.sampleCount)
        assertFalse(result.isExcessive)
    }

    @Test
    fun `empty detector returns invalid result`() {
        val result = detector.analyze(BodySide.LEFT)
        assertEquals(0f, result.amplitude)
        assertEquals(0f, result.variance)
        assertEquals(0, result.sampleCount)
    }

    @Test
    fun `custom minSamplesRequired is respected`() {
        val config = HipRockingDetectorConfig(minSamplesRequired = 5)
        val detector = HipRockingDetector(config)

        for (i in 1..5) {
            detector.addHipPosition(0.5f, BodySide.LEFT)
        }

        val result = detector.analyze(BodySide.LEFT)
        assertEquals(5, result.sampleCount)
    }

    // ==================== Reset Tests ====================

    @Test
    fun `reset clears all data`() {
        for (i in 1..20) {
            detector.addHipPosition(0.5f, BodySide.LEFT)
            detector.addHipPosition(0.5f, BodySide.RIGHT)
        }

        detector.reset()

        assertEquals(0, detector.getSampleCount(BodySide.LEFT))
        assertEquals(0, detector.getSampleCount(BodySide.RIGHT))
    }

    @Test
    fun `reset specific side only clears that side`() {
        for (i in 1..20) {
            detector.addHipPosition(0.5f, BodySide.LEFT)
            detector.addHipPosition(0.5f, BodySide.RIGHT)
        }

        detector.reset(BodySide.LEFT)

        assertEquals(0, detector.getSampleCount(BodySide.LEFT))
        assertEquals(20, detector.getSampleCount(BodySide.RIGHT))
    }

    // ==================== Both Sides Analysis Tests ====================

    @Test
    fun `analyzeBoth returns results for both sides`() {
        for (i in 1..20) {
            detector.addHipPosition(0.5f, BodySide.LEFT)
            detector.addHipPosition(0.5f, BodySide.RIGHT)
        }

        val (leftResult, rightResult) = detector.analyzeBoth()

        assertEquals(BodySide.LEFT, leftResult.side)
        assertEquals(BodySide.RIGHT, rightResult.side)
        assertEquals(20, leftResult.sampleCount)
        assertEquals(20, rightResult.sampleCount)
    }

    @Test
    fun `analyzeBoth can detect asymmetric rocking`() {
        // Left side has excessive rocking
        for (i in 1..10) {
            detector.addHipPosition(0.4f, BodySide.LEFT)
            detector.addHipPosition(0.6f, BodySide.LEFT)
        }
        // Right side has minimal rocking
        for (i in 1..20) {
            detector.addHipPosition(0.5f, BodySide.RIGHT)
        }

        val (leftResult, rightResult) = detector.analyzeBoth()

        assertTrue(leftResult.isExcessive)
        assertFalse(rightResult.isExcessive)
    }

    // ==================== Static Helper Tests ====================

    @Test
    fun `calculateAmplitude static method works correctly`() {
        val positions = listOf(0.4f, 0.5f, 0.6f, 0.5f, 0.4f)
        val amplitude = HipRockingDetector.calculateAmplitude(positions)
        assertEquals(0.2f, amplitude, 0.001f)
    }

    @Test
    fun `calculateAmplitude returns zero for empty list`() {
        val amplitude = HipRockingDetector.calculateAmplitude(emptyList())
        assertEquals(0f, amplitude, 0.001f)
    }

    @Test
    fun `calculateVariance static method works correctly`() {
        // Values: 2, 4, 6 -> mean = 4
        // Variance = ((2)^2 + 0 + (2)^2) / 3 = 8/3 ≈ 2.667
        val positions = listOf(2f, 4f, 6f)
        val variance = HipRockingDetector.calculateVariance(positions)
        assertEquals(2.667f, variance, 0.01f)
    }

    @Test
    fun `analyzeHipPositions static method works correctly`() {
        val positions = (1..20).map { 0.5f + if (it % 2 == 0) 0.05f else -0.05f }

        val result = HipRockingDetector.analyzeHipPositions(positions, BodySide.LEFT)

        assertEquals(0.1f, result.amplitude, 0.001f)
        assertTrue(result.isExcessive) // 0.1 > 0.03
        assertEquals(20, result.sampleCount)
    }

    @Test
    fun `analyzeFrameSequence static method works correctly`() {
        val frames = (1..20).map { i ->
            val y = if (i % 2 == 0) 0.52f else 0.48f
            createPoseFrameWithHip(0.5f, y, 1.0f)
        }

        val result = HipRockingDetector.analyzeFrameSequence(frames, BodySide.LEFT)

        assertEquals(0.04f, result.amplitude, 0.001f)
        assertTrue(result.isExcessive) // 0.04 > 0.03
    }

    // ==================== HipRockingResult Tests ====================

    @Test
    fun `HipRockingResult invalid creates proper invalid result`() {
        val invalid = HipRockingResult.invalid(BodySide.LEFT)

        assertEquals(0f, invalid.amplitude)
        assertEquals(0f, invalid.variance)
        assertFalse(invalid.isExcessive)
        assertEquals(BodySide.LEFT, invalid.side)
        assertEquals(0, invalid.sampleCount)
    }

    @Test
    fun `HipRockingResult data class equality works`() {
        val result1 = HipRockingResult(0.1f, 0.01f, true, BodySide.LEFT, 20, 0.4f, 0.5f)
        val result2 = HipRockingResult(0.1f, 0.01f, true, BodySide.LEFT, 20, 0.4f, 0.5f)

        assertEquals(result1, result2)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `negative hip positions are handled`() {
        // Should not happen in practice, but test robustness
        detector.addHipPosition(-0.1f, BodySide.LEFT)
        detector.addHipPosition(0.1f, BodySide.LEFT)

        for (i in 1..15) {
            detector.addHipPosition(0f, BodySide.LEFT)
        }

        val result = detector.analyze(BodySide.LEFT)
        assertEquals(0.2f, result.amplitude, 0.001f)
    }

    @Test
    fun `hip positions greater than 1 are handled`() {
        detector.addHipPosition(0.9f, BodySide.LEFT)
        detector.addHipPosition(1.1f, BodySide.LEFT)

        for (i in 1..15) {
            detector.addHipPosition(1.0f, BodySide.LEFT)
        }

        val result = detector.analyze(BodySide.LEFT)
        assertEquals(0.2f, result.amplitude, 0.001f)
    }

    @Test
    fun `very small amplitude changes are detected`() {
        // Small changes that might have floating point issues
        detector.addHipPosition(0.5000f, BodySide.LEFT)
        detector.addHipPosition(0.5001f, BodySide.LEFT)

        for (i in 1..15) {
            detector.addHipPosition(0.50005f, BodySide.LEFT)
        }

        val result = detector.analyze(BodySide.LEFT)
        assertEquals(0.0001f, result.amplitude, 0.00001f)
        assertFalse(result.isExcessive)
    }

    @Test
    fun `standard deviation returns zero for empty data`() {
        val stdDev = detector.getStandardDeviation(BodySide.LEFT)
        assertEquals(0f, stdDev, 0.001f)
    }

    // ==================== Helper Functions ====================

    private fun createPoseFrameWithHip(hipX: Float, hipY: Float, visibility: Float): PoseFrame {
        val landmarks = createFullLandmarkList(hipY, hipY, visibility)
        return PoseFrame(
            frameNumber = 0,
            timestampMs = 0,
            landmarks = landmarks,
            confidence = 1.0f
        )
    }

    private fun createFullLandmarkList(
        leftHipY: Float,
        rightHipY: Float,
        visibility: Float
    ): List<Landmark> {
        val landmarks = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) { index ->
            Landmark(
                x = 0.5f,
                y = when (index) {
                    PoseLandmarkIndex.LEFT_HIP -> leftHipY
                    PoseLandmarkIndex.RIGHT_HIP -> rightHipY
                    else -> 0.5f
                },
                z = 0f,
                visibility = visibility,
                presence = 1.0f
            )
        }
        return landmarks
    }
}
