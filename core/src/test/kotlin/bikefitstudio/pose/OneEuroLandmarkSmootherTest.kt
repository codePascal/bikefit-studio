package bikefitstudio.pose

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for OneEuroLandmarkSmoother.
 * 
 * Tests cover:
 * - Landmark smoothing with One Euro filter
 * - Target landmark filtering
 * - Integration with PoseFrame and PoseResult
 * - Jitter reduction validation
 * - Performance validation
 */
class OneEuroLandmarkSmootherTest {

    private lateinit var smoother: OneEuroLandmarkSmoother

    @Before
    fun setup() {
        smoother = OneEuroLandmarkSmoother(
            minCutoff = 1.0,
            beta = 0.02,
            dCutoff = 1.0
        )
    }

    // ==================== Initialization Tests ====================

    @Test
    fun `smoother is not initialized before first frame`() {
        assertFalse(smoother.isInitialized())
    }

    @Test
    fun `smoother is initialized after first frame`() {
        val frame = createTestFrame(x = 0.5f, y = 0.5f, timestampMs = 0)
        smoother.smooth(frame)
        assertTrue(smoother.isInitialized())
    }

    @Test
    fun `reset clears smoother state`() {
        val frame = createTestFrame(x = 0.5f, y = 0.5f, timestampMs = 0)
        smoother.smooth(frame)
        assertTrue(smoother.isInitialized())
        
        smoother.reset()
        assertFalse(smoother.isInitialized())
    }

    // ==================== Frame Smoothing Tests ====================

    @Test
    fun `first frame is returned unchanged`() {
        val frame = createTestFrame(x = 0.5f, y = 0.6f, timestampMs = 0)
        val result = smoother.smooth(frame)
        
        // First frame should be unchanged for target landmarks
        assertEquals(0.5f, result.landmarks[PoseLandmarkIndex.LEFT_HIP].x, 0.001f)
        assertEquals(0.6f, result.landmarks[PoseLandmarkIndex.LEFT_HIP].y, 0.001f)
    }

    @Test
    fun `second frame is smoothed with first`() {
        // First frame: x=0.0, y=0.0
        val frame1 = createTestFrame(x = 0.0f, y = 0.0f, timestampMs = 0)
        smoother.smooth(frame1)
        
        // Second frame: x=1.0, y=1.0 at 100ms
        val frame2 = createTestFrame(x = 1.0f, y = 1.0f, timestampMs = 100)
        val result = smoother.smooth(frame2)
        
        // Should be smoothed (between 0.0 and 1.0)
        val hip = result.landmarks[PoseLandmarkIndex.LEFT_HIP]
        assertTrue("X should be smoothed: ${hip.x}", hip.x > 0.0f && hip.x < 1.0f)
        assertTrue("Y should be smoothed: ${hip.y}", hip.y > 0.0f && hip.y < 1.0f)
    }

    @Test
    fun `all target landmarks are filtered`() {
        val frame1 = createFullBodyFrame(timestampMs = 0)
        smoother.smooth(frame1)
        
        val frame2 = createFullBodyFrame(timestampMs = 100, offset = 0.2f)
        val result = smoother.smooth(frame2)
        
        // Check that all default target landmarks are smoothed
        val targetLandmarks = listOf(
            PoseLandmarkIndex.LEFT_HIP,
            PoseLandmarkIndex.RIGHT_HIP,
            PoseLandmarkIndex.LEFT_KNEE,
            PoseLandmarkIndex.RIGHT_KNEE,
            PoseLandmarkIndex.LEFT_ANKLE,
            PoseLandmarkIndex.RIGHT_ANKLE,
            PoseLandmarkIndex.LEFT_FOOT_INDEX,
            PoseLandmarkIndex.RIGHT_FOOT_INDEX
        )
        
        for (index in targetLandmarks) {
            val landmark = result.landmarks[index]
            // Smoothed value should be different from the raw input (0.2)
            assertNotEquals("Landmark $index should be smoothed", 0.2f, landmark.x, 0.001f)
        }
    }

    @Test
    fun `non-target landmarks are not filtered`() {
        // Create smoother that only filters left hip
        val selectiveSmoother = OneEuroLandmarkSmoother(
            targetLandmarks = setOf(PoseLandmarkIndex.LEFT_HIP)
        )
        
        val frame1 = createFullBodyFrame(timestampMs = 0, offset = 0.0f)
        selectiveSmoother.smooth(frame1)
        
        val frame2 = createFullBodyFrame(timestampMs = 100, offset = 0.3f)
        val result = selectiveSmoother.smooth(frame2)
        
        // Calculate expected values
        // LEFT_HIP is index 23: baseX = (23 % 3) * 0.3 = 0.6
        // Frame1: hip.x = 0.6 + 0.0 = 0.6
        // Frame2: hip.x = 0.6 + 0.3 = 0.9
        // LEFT_HIP should be smoothed (between frame1 and frame2)
        val hip = result.landmarks[PoseLandmarkIndex.LEFT_HIP]
        assertTrue("Hip should be smoothed (between 0.6 and 0.9): ${hip.x}", 
            hip.x > 0.6f && hip.x < 0.9f)
        
        // LEFT_KNEE is index 25: baseX = (25 % 3) * 0.3 = 0.3
        // Frame2: knee.x = 0.3 + 0.3 = 0.6
        // LEFT_KNEE should NOT be smoothed (pass through from frame2)
        val knee = result.landmarks[PoseLandmarkIndex.LEFT_KNEE]
        assertEquals("Knee should not be smoothed", 0.6f, knee.x, 0.001f)
    }

    @Test
    fun `z coordinate is filtered`() {
        val frame1 = createTestFrame(x = 0.0f, y = 0.0f, z = 0.0f, timestampMs = 0)
        smoother.smooth(frame1)
        
        val frame2 = createTestFrame(x = 0.0f, y = 0.0f, z = 1.0f, timestampMs = 100)
        val result = smoother.smooth(frame2)
        
        val hip = result.landmarks[PoseLandmarkIndex.LEFT_HIP]
        assertTrue("Z should be smoothed: ${hip.z}", hip.z > 0.0f && hip.z < 1.0f)
    }

    @Test
    fun `visibility and presence are not filtered`() {
        val frame1 = createTestFrame(
            x = 0.0f, y = 0.0f,
            visibility = 0.9f, presence = 0.8f,
            timestampMs = 0
        )
        smoother.smooth(frame1)
        
        val frame2 = createTestFrame(
            x = 1.0f, y = 1.0f,
            visibility = 0.5f, presence = 0.3f,
            timestampMs = 100
        )
        val result = smoother.smooth(frame2)
        
        val hip = result.landmarks[PoseLandmarkIndex.LEFT_HIP]
        // Visibility and presence should be from current frame, not smoothed
        assertEquals(0.5f, hip.visibility, 0.001f)
        assertEquals(0.3f, hip.presence, 0.001f)
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
        val validFrame1 = createTestFrame(x = 0.0f, y = 0.0f, timestampMs = 0)
        smoother.smooth(validFrame1)
        assertTrue(smoother.isInitialized())
        
        // Process invalid frame - should not update state
        val invalidFrame = PoseFrame.EMPTY
        smoother.smooth(invalidFrame)
        
        // Next valid frame should still smooth with first frame
        val validFrame2 = createTestFrame(x = 1.0f, y = 1.0f, timestampMs = 100)
        val result = smoother.smooth(validFrame2)
        
        val hip = result.landmarks[PoseLandmarkIndex.LEFT_HIP]
        // Should be smoothed (between 0 and 1)
        assertTrue("Should smooth with first frame: ${hip.x}", hip.x > 0.0f && hip.x < 1.0f)
    }

    // ==================== PoseResult Smoothing Tests ====================

    @Test
    fun `PoseResult smoothing works correctly`() {
        val result1 = createTestPoseResult(x = 0.0f, y = 0.0f, timestampMs = 0)
        smoother.smooth(result1)
        
        val result2 = createTestPoseResult(x = 1.0f, y = 1.0f, timestampMs = 100)
        val smoothed = smoother.smooth(result2)
        
        val hip = smoothed.landmarks[PoseLandmarkIndex.LEFT_HIP]
        assertTrue("Should be smoothed: ${hip.x}", hip.x > 0.0f && hip.x < 1.0f)
        assertTrue("Should be smoothed: ${hip.y}", hip.y > 0.0f && hip.y < 1.0f)
    }

    // ==================== Jitter Reduction Tests ====================

    @Test
    fun `smoothing reduces jitter in noisy landmark data`() {
        // Create noisy sequence oscillating around (0.5, 0.5)
        val noisySequence = (0..50).map { i ->
            val noise = Math.sin(i * 0.5).toFloat() * 0.05f
            createTestFrame(
                x = 0.5f + noise,
                y = 0.5f + noise,
                timestampMs = i * 40L  // 25 FPS
            )
        }
        
        // Apply smoothing
        val smoothedSequence = noisySequence.map { frame ->
            smoother.smooth(frame)
        }
        
        // Extract x coordinates of left hip
        val noisyXValues = noisySequence.map { 
            it.landmarks[PoseLandmarkIndex.LEFT_HIP].x 
        }
        val smoothedXValues = smoothedSequence.map { 
            it.landmarks[PoseLandmarkIndex.LEFT_HIP].x 
        }
        
        // Skip first few frames for filter initialization
        val skipFrames = 5
        val noisySubset = noisyXValues.drop(skipFrames)
        val smoothedSubset = smoothedXValues.drop(skipFrames)
        
        // Calculate variance
        val noisyVariance = variance(noisySubset)
        val smoothedVariance = variance(smoothedSubset)
        
        // Smoothed variance should be lower
        assertTrue(
            "Smoothed variance ($smoothedVariance) should be less than noisy ($noisyVariance)",
            smoothedVariance < noisyVariance
        )
    }

    @Test
    fun `validates jitter reduction with recorded sequence`() {
        // Simulate a landmark that should be stable at (0.5, 0.5)
        // but has measurement jitter
        val groundTruthX = 0.5f
        val groundTruthY = 0.5f
        val samples = 100
        
        val noisySequence = (0 until samples).map { i ->
            val noiseX = (Math.sin(i * 0.5) * 0.03 + Math.cos(i * 0.3) * 0.02).toFloat()
            val noiseY = (Math.sin(i * 0.7) * 0.03 + Math.cos(i * 0.4) * 0.02).toFloat()
            createTestFrame(
                x = groundTruthX + noiseX,
                y = groundTruthY + noiseY,
                timestampMs = i * 40L  // 25 FPS
            )
        }
        
        // Apply smoothing
        val smoothedSequence = noisySequence.map { frame ->
            smoother.smooth(frame)
        }
        
        // Extract coordinates
        val noisyXValues = noisySequence.map { it.landmarks[PoseLandmarkIndex.LEFT_HIP].x }
        val smoothedXValues = smoothedSequence.map { it.landmarks[PoseLandmarkIndex.LEFT_HIP].x }
        
        // Skip initialization period
        val skipFrames = 10
        val noisySubset = noisyXValues.drop(skipFrames)
        val smoothedSubset = smoothedXValues.drop(skipFrames)
        
        // Calculate jitter (mean absolute consecutive difference)
        val noisyJitter = jitter(noisySubset)
        val smoothedJitter = jitter(smoothedSubset)
        
        assertTrue(
            "Smoothed jitter ($smoothedJitter) should be less than noisy jitter ($noisyJitter)",
            smoothedJitter < noisyJitter
        )
    }

    // ==================== Performance Tests ====================

    @Test
    fun `no measurable increase in frame latency`() {
        // Process 1000 frames and measure total time
        val iterations = 1000
        val startTime = System.nanoTime()
        
        for (i in 0 until iterations) {
            val frame = createFullBodyFrame(timestampMs = i * 40L)
            smoother.smooth(frame)
        }
        
        val endTime = System.nanoTime()
        val totalTimeMs = (endTime - startTime) / 1_000_000.0
        val avgTimePerFrameMs = totalTimeMs / iterations
        
        // Should be well under 2ms per frame (requirement: <2ms)
        assertTrue(
            "Average time per frame should be < 2ms, got ${avgTimePerFrameMs}ms",
            avgTimePerFrameMs < 2.0
        )
        
        // Log for reference
        println("OneEuroLandmarkSmoother performance: ${avgTimePerFrameMs}ms per frame")
    }

    @Test
    fun `filter all landmarks performance test`() {
        // Test with filtering all landmarks (not just target ones)
        val allLandmarksSmoother = OneEuroLandmarkSmoother(
            targetLandmarks = null  // Filter all landmarks
        )
        
        val iterations = 1000
        val startTime = System.nanoTime()
        
        for (i in 0 until iterations) {
            val frame = createFullBodyFrame(timestampMs = i * 40L)
            allLandmarksSmoother.smooth(frame)
        }
        
        val endTime = System.nanoTime()
        val totalTimeMs = (endTime - startTime) / 1_000_000.0
        val avgTimePerFrameMs = totalTimeMs / iterations
        
        // Even with all 33 landmarks, should be under 2ms
        assertTrue(
            "Average time per frame (all landmarks) should be < 2ms, got ${avgTimePerFrameMs}ms",
            avgTimePerFrameMs < 2.0
        )
    }

    // ==================== Parameter Configuration Tests ====================

    @Test
    fun `custom parameters work correctly`() {
        val customSmoother = OneEuroLandmarkSmoother(
            minCutoff = 2.0,
            beta = 0.01,
            dCutoff = 0.5
        )
        
        val frame1 = createTestFrame(x = 0.0f, y = 0.0f, timestampMs = 0)
        customSmoother.smooth(frame1)
        
        val frame2 = createTestFrame(x = 1.0f, y = 1.0f, timestampMs = 100)
        val result = customSmoother.smooth(frame2)
        
        // Should produce valid smoothed result
        val hip = result.landmarks[PoseLandmarkIndex.LEFT_HIP]
        assertTrue("Should be smoothed with custom params", hip.x > 0.0f && hip.x < 1.0f)
    }

    @Test
    fun `default target landmarks include correct indices`() {
        val expectedIndices = setOf(
            PoseLandmarkIndex.LEFT_HIP,
            PoseLandmarkIndex.RIGHT_HIP,
            PoseLandmarkIndex.LEFT_KNEE,
            PoseLandmarkIndex.RIGHT_KNEE,
            PoseLandmarkIndex.LEFT_ANKLE,
            PoseLandmarkIndex.RIGHT_ANKLE,
            PoseLandmarkIndex.LEFT_HEEL,
            PoseLandmarkIndex.RIGHT_HEEL,
            PoseLandmarkIndex.LEFT_FOOT_INDEX,
            PoseLandmarkIndex.RIGHT_FOOT_INDEX
        )
        
        assertEquals(expectedIndices, OneEuroLandmarkSmoother.DEFAULT_TARGET_LANDMARKS)
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
        presence: Float = 1f,
        timestampMs: Long = 0
    ): PoseFrame {
        // Create 33 landmarks (MediaPipe standard)
        val landmarks = (0 until PoseLandmarkIndex.LANDMARK_COUNT).map { index ->
            // Only set specific values for target landmarks
            if (index in OneEuroLandmarkSmoother.DEFAULT_TARGET_LANDMARKS) {
                createLandmark(x, y, z, visibility, presence)
            } else {
                createLandmark(0f, 0f, 0f, visibility, presence)
            }
        }
        
        return PoseFrame(
            frameNumber = 1,
            timestampMs = timestampMs,
            landmarks = landmarks,
            confidence = 0.9f
        )
    }

    private fun createFullBodyFrame(
        timestampMs: Long = 0,
        offset: Float = 0f
    ): PoseFrame {
        // Create all 33 landmarks with varying positions
        val landmarks = (0 until PoseLandmarkIndex.LANDMARK_COUNT).map { index ->
            val baseX = (index % 3) * 0.3f + offset
            val baseY = (index / 3) * 0.1f + offset
            createLandmark(x = baseX, y = baseY)
        }
        
        return PoseFrame(
            frameNumber = 1,
            timestampMs = timestampMs,
            landmarks = landmarks,
            confidence = 0.9f
        )
    }

    private fun createTestPoseResult(
        x: Float = 0f,
        y: Float = 0f,
        z: Float = 0f,
        visibility: Float = 1f,
        presence: Float = 1f,
        timestampMs: Long = 0
    ): PoseResult {
        val landmarks = (0 until PoseLandmarkIndex.LANDMARK_COUNT).map { index ->
            if (index in OneEuroLandmarkSmoother.DEFAULT_TARGET_LANDMARKS) {
                createLandmark(x, y, z, visibility, presence)
            } else {
                createLandmark(0f, 0f, 0f, visibility, presence)
            }
        }
        
        return PoseResult(
            landmarks = landmarks,
            timestampMs = timestampMs,
            isValid = true,
            confidence = 0.9f
        )
    }

    private fun variance(values: List<Float>): Float {
        val mean = values.average().toFloat()
        return values.map { (it - mean) * (it - mean) }.average().toFloat()
    }

    private fun jitter(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val diffs = values.zipWithNext { a, b -> kotlin.math.abs(b - a) }
        return diffs.average().toFloat()
    }
}
