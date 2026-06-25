package bikefitstudio.pose

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for LandmarkSmoother.
 */
class LandmarkSmootherTest {

    private lateinit var smoother: LandmarkSmoother

    @Before
    fun setup() {
        smoother = LandmarkSmoother(alpha = 0.5f)
    }

    // ==================== Basic EMA Tests ====================

    @Test
    fun `ema with alpha 0_5 averages current and previous`() {
        val result = LandmarkSmoother.ema(current = 1.0f, previous = 0.0f, alpha = 0.5f)
        assertEquals(0.5f, result, 0.001f)
    }

    @Test
    fun `ema with alpha 1_0 returns current value`() {
        val result = LandmarkSmoother.ema(current = 1.0f, previous = 0.0f, alpha = 1.0f)
        assertEquals(1.0f, result, 0.001f)
    }

    @Test
    fun `ema with alpha 0_0 returns previous value`() {
        val result = LandmarkSmoother.ema(current = 1.0f, previous = 0.5f, alpha = 0.0f)
        assertEquals(0.5f, result, 0.001f)
    }

    @Test
    fun `ema with alpha 0_3 applies correct weighting`() {
        // smoothed = 0.3 * 10 + 0.7 * 5 = 3 + 3.5 = 6.5
        val result = LandmarkSmoother.ema(current = 10.0f, previous = 5.0f, alpha = 0.3f)
        assertEquals(6.5f, result, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ema rejects alpha greater than 1`() {
        LandmarkSmoother.ema(current = 1.0f, previous = 0.0f, alpha = 1.5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ema rejects negative alpha`() {
        LandmarkSmoother.ema(current = 1.0f, previous = 0.0f, alpha = -0.1f)
    }

    // ==================== Smoother Initialization Tests ====================

    @Test
    fun `smoother is not initialized before first frame`() {
        assertFalse(smoother.isInitialized())
    }

    @Test
    fun `smoother is initialized after first frame`() {
        val frame = createTestFrame(x = 0.5f, y = 0.5f)
        smoother.smooth(frame)
        assertTrue(smoother.isInitialized())
    }

    @Test
    fun `reset clears smoother state`() {
        val frame = createTestFrame(x = 0.5f, y = 0.5f)
        smoother.smooth(frame)
        assertTrue(smoother.isInitialized())
        
        smoother.reset()
        assertFalse(smoother.isInitialized())
    }

    // ==================== Frame Smoothing Tests ====================

    @Test
    fun `first frame is returned unchanged`() {
        val frame = createTestFrame(x = 0.5f, y = 0.6f)
        val result = smoother.smooth(frame)
        
        assertEquals(0.5f, result.landmarks[0].x, 0.001f)
        assertEquals(0.6f, result.landmarks[0].y, 0.001f)
    }

    @Test
    fun `second frame is smoothed with first`() {
        // First frame: x=0.0, y=0.0
        val frame1 = createTestFrame(x = 0.0f, y = 0.0f)
        smoother.smooth(frame1)
        
        // Second frame: x=1.0, y=1.0
        // With alpha=0.5: smoothed = 0.5*1.0 + 0.5*0.0 = 0.5
        val frame2 = createTestFrame(x = 1.0f, y = 1.0f)
        val result = smoother.smooth(frame2)
        
        assertEquals(0.5f, result.landmarks[0].x, 0.001f)
        assertEquals(0.5f, result.landmarks[0].y, 0.001f)
    }

    @Test
    fun `smoothing accumulates over multiple frames`() {
        val smoother = LandmarkSmoother(alpha = 0.5f)
        
        // Frame 1: x=0.0 -> smoothed=0.0
        smoother.smooth(createTestFrame(x = 0.0f, y = 0.0f))
        
        // Frame 2: x=1.0 -> smoothed = 0.5*1.0 + 0.5*0.0 = 0.5
        smoother.smooth(createTestFrame(x = 1.0f, y = 0.0f))
        
        // Frame 3: x=1.0 -> smoothed = 0.5*1.0 + 0.5*0.5 = 0.75
        val result = smoother.smooth(createTestFrame(x = 1.0f, y = 0.0f))
        
        assertEquals(0.75f, result.landmarks[0].x, 0.001f)
    }

    @Test
    fun `visibility and presence are not smoothed`() {
        val frame1 = createTestFrame(x = 0.0f, y = 0.0f, visibility = 0.9f, presence = 0.8f)
        smoother.smooth(frame1)
        
        val frame2 = createTestFrame(x = 1.0f, y = 1.0f, visibility = 0.5f, presence = 0.3f)
        val result = smoother.smooth(frame2)
        
        // Visibility and presence should be from current frame, not smoothed
        assertEquals(0.5f, result.landmarks[0].visibility, 0.001f)
        assertEquals(0.3f, result.landmarks[0].presence, 0.001f)
    }

    @Test
    fun `z coordinate is smoothed`() {
        val frame1 = createTestFrame(x = 0.0f, y = 0.0f, z = 0.0f)
        smoother.smooth(frame1)
        
        val frame2 = createTestFrame(x = 0.0f, y = 0.0f, z = 1.0f)
        val result = smoother.smooth(frame2)
        
        assertEquals(0.5f, result.landmarks[0].z, 0.001f)
    }

    // ==================== Invalid Frame Tests ====================

    @Test
    fun `invalid frame is returned unchanged`() {
        val invalidFrame = PoseFrame.EMPTY
        val result = smoother.smooth(invalidFrame)
        
        assertFalse(result.isValid)
        assertEquals(0, result.landmarks.size)
    }

    @Test
    fun `invalid frame does not update smoother state`() {
        val validFrame = createTestFrame(x = 0.5f, y = 0.5f)
        smoother.smooth(validFrame)
        assertTrue(smoother.isInitialized())
        
        // Process invalid frame - should not update state
        val invalidFrame = PoseFrame.EMPTY
        smoother.smooth(invalidFrame)
        
        // Next valid frame should still smooth with first frame
        val frame2 = createTestFrame(x = 1.0f, y = 1.0f)
        val result = smoother.smooth(frame2)
        
        // With alpha=0.5: 0.5*1.0 + 0.5*0.5 = 0.75
        assertEquals(0.75f, result.landmarks[0].x, 0.001f)
    }

    // ==================== PoseResult Smoothing Tests ====================

    @Test
    fun `PoseResult smoothing works correctly`() {
        val result1 = createTestPoseResult(x = 0.0f, y = 0.0f)
        smoother.smooth(result1)
        
        val result2 = createTestPoseResult(x = 1.0f, y = 1.0f)
        val smoothed = smoother.smooth(result2)
        
        assertEquals(0.5f, smoothed.landmarks[0].x, 0.001f)
        assertEquals(0.5f, smoothed.landmarks[0].y, 0.001f)
    }

    // ==================== Jitter Reduction Tests ====================

    @Test
    fun `smoothing reduces jitter in noisy data`() {
        val smoother = LandmarkSmoother(alpha = 0.3f)
        
        // Simulate noisy data oscillating around 0.5
        val noisyValues = listOf(0.5f, 0.55f, 0.45f, 0.52f, 0.48f, 0.53f, 0.47f, 0.51f)
        val smoothedValues = mutableListOf<Float>()
        
        for (value in noisyValues) {
            val frame = createTestFrame(x = value, y = 0.5f)
            val result = smoother.smooth(frame)
            smoothedValues.add(result.landmarks[0].x)
        }
        
        // Calculate variance of original vs smoothed
        val originalVariance = variance(noisyValues)
        val smoothedVariance = variance(smoothedValues)
        
        // Smoothed variance should be lower
        assertTrue(
            "Smoothed variance ($smoothedVariance) should be less than original ($originalVariance)",
            smoothedVariance < originalVariance
        )
    }

    @Test
    fun `smoothing converges to stable value`() {
        val smoother = LandmarkSmoother(alpha = 0.4f)
        
        // Send constant value multiple times
        repeat(10) {
            smoother.smooth(createTestFrame(x = 0.5f, y = 0.5f))
        }
        
        // After initial value, switch to new constant
        var lastResult: PoseFrame? = null
        repeat(20) {
            lastResult = smoother.smooth(createTestFrame(x = 0.8f, y = 0.8f))
        }
        
        // Should converge close to 0.8
        assertEquals(0.8f, lastResult!!.landmarks[0].x, 0.01f)
    }

    // ==================== Static Smoothing Tests ====================

    @Test
    fun `static smoothLandmarks function works correctly`() {
        val current = listOf(createLandmark(x = 1.0f, y = 1.0f))
        val previous = listOf(createLandmark(x = 0.0f, y = 0.0f))
        
        val result = LandmarkSmoother.smoothLandmarks(current, previous, alpha = 0.5f)
        
        assertEquals(0.5f, result[0].x, 0.001f)
        assertEquals(0.5f, result[0].y, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `static smoothLandmarks rejects mismatched sizes`() {
        val current = listOf(createLandmark(x = 1.0f, y = 1.0f))
        val previous = listOf(
            createLandmark(x = 0.0f, y = 0.0f),
            createLandmark(x = 0.0f, y = 0.0f)
        )
        
        LandmarkSmoother.smoothLandmarks(current, previous, alpha = 0.5f)
    }

    // ==================== Alpha Configuration Tests ====================

    @Test
    fun `different alpha values produce different smoothing`() {
        val lowSmoother = LandmarkSmoother(alpha = 0.8f)   // Less smoothing
        val highSmoother = LandmarkSmoother(alpha = 0.2f)  // More smoothing
        
        // First frame
        lowSmoother.smooth(createTestFrame(x = 0.0f, y = 0.0f))
        highSmoother.smooth(createTestFrame(x = 0.0f, y = 0.0f))
        
        // Second frame with jump
        val lowResult = lowSmoother.smooth(createTestFrame(x = 1.0f, y = 0.0f))
        val highResult = highSmoother.smooth(createTestFrame(x = 1.0f, y = 0.0f))
        
        // Low smoothing should be closer to current value
        assertTrue(lowResult.landmarks[0].x > highResult.landmarks[0].x)
        assertEquals(0.8f, lowResult.landmarks[0].x, 0.001f)
        assertEquals(0.2f, highResult.landmarks[0].x, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `constructor rejects invalid alpha`() {
        LandmarkSmoother(alpha = 1.5f)
    }

    // ==================== Helper Functions ====================

    private fun createLandmark(
        x: Float = 0f,
        y: Float = 0f,
        z: Float = 0f,
        visibility: Float = 1f,
        presence: Float = 1f
    ): Landmark {
        return Landmark(x = x, y = y, z = z, visibility = visibility, presence = presence)
    }

    private fun createTestFrame(
        x: Float = 0f,
        y: Float = 0f,
        z: Float = 0f,
        visibility: Float = 1f,
        presence: Float = 1f
    ): PoseFrame {
        val landmark = createLandmark(x, y, z, visibility, presence)
        return PoseFrame(
            frameNumber = 1,
            timestampMs = System.currentTimeMillis(),
            landmarks = listOf(landmark),
            confidence = 0.9f
        )
    }

    private fun createTestPoseResult(
        x: Float = 0f,
        y: Float = 0f,
        z: Float = 0f,
        visibility: Float = 1f,
        presence: Float = 1f
    ): PoseResult {
        val landmark = createLandmark(x, y, z, visibility, presence)
        return PoseResult(
            landmarks = listOf(landmark),
            timestampMs = System.currentTimeMillis(),
            isValid = true,
            confidence = 0.9f
        )
    }

    private fun variance(values: List<Float>): Float {
        val mean = values.average().toFloat()
        return values.map { (it - mean) * (it - mean) }.average().toFloat()
    }
}
