package bikefitstudio.biomechanics

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import bikefitstudio.pose.Landmark
import bikefitstudio.pose.PoseFrame
import bikefitstudio.pose.PoseLandmarkIndex
import bikefitstudio.pose.PoseResult

/**
 * Unit tests for HipAngleAtTdc.
 * 
 * Tests verify:
 * - Correct angle calculation at TDC frames
 * - Minimum angle tracking across cycles
 * - TDC detection integration
 * - Edge cases (missing landmarks, no TDC detected)
 */
class HipAngleAtTdcTest {

    private val delta = 0.5f // Tolerance for angle comparisons

    /**
     * Creates a test Landmark with specified coordinates and visibility.
     */
    private fun createLandmark(
        x: Float,
        y: Float,
        visibility: Float = 1.0f
    ): Landmark {
        return Landmark(
            x = x,
            y = y,
            z = 0f,
            visibility = visibility,
            presence = 1.0f
        )
    }

    /**
     * Creates a list of 33 landmarks with specified body positions.
     */
    private fun createLandmarksWithHipAngle(
        shoulderMidX: Float, shoulderMidY: Float,
        hipMidX: Float, hipMidY: Float,
        kneeMidX: Float, kneeMidY: Float,
        visibility: Float = 1.0f
    ): List<Landmark> {
        val landmarks = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) {
            createLandmark(0f, 0f, 1.0f)
        }

        // Set shoulders (spread around midpoint)
        landmarks[PoseLandmarkIndex.LEFT_SHOULDER] = createLandmark(
            shoulderMidX - 0.05f, shoulderMidY, visibility
        )
        landmarks[PoseLandmarkIndex.RIGHT_SHOULDER] = createLandmark(
            shoulderMidX + 0.05f, shoulderMidY, visibility
        )

        // Set hips (spread around midpoint)
        landmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(
            hipMidX - 0.05f, hipMidY, visibility
        )
        landmarks[PoseLandmarkIndex.RIGHT_HIP] = createLandmark(
            hipMidX + 0.05f, hipMidY, visibility
        )

        // Set knees (spread around midpoint)
        landmarks[PoseLandmarkIndex.LEFT_KNEE] = createLandmark(
            kneeMidX - 0.05f, kneeMidY, visibility
        )
        landmarks[PoseLandmarkIndex.RIGHT_KNEE] = createLandmark(
            kneeMidX + 0.05f, kneeMidY, visibility
        )

        // Set ankles (needed for TDC detection)
        landmarks[PoseLandmarkIndex.LEFT_ANKLE] = createLandmark(
            kneeMidX - 0.05f, kneeMidY + 0.3f, visibility
        )
        landmarks[PoseLandmarkIndex.RIGHT_ANKLE] = createLandmark(
            kneeMidX + 0.05f, kneeMidY + 0.3f, visibility
        )

        return landmarks
    }

    /**
     * Creates a pose frame with cycling position at specified ankle height.
     * 
     * @param vertexAngleDegrees The vertex angle at the hip (angle between torso and femur vectors).
     *                           This is the geometric angle used to position the knee.
     *                           The HipAngleCalculator will return hipFlexion = 180 - vertexAngle.
     *                           Example: vertexAngle=50° → hipFlexion=130°, vertexAngle=130° → hipFlexion=50°
     */
    private fun createCyclingPoseFrame(
        frameNumber: Long,
        timestampMs: Long,
        ankleY: Float,
        vertexAngleDegrees: Float
    ): PoseFrame {
        // For simplicity, use a vertical torso and angle the leg
        val shoulderY = 0.3f
        val hipY = 0.5f
        
        // Calculate knee position based on desired vertex angle
        val shoulderX = 0.5f
        val hipX = 0.5f
        
        // Use trigonometry to place knee at correct angle
        val angleRad = Math.toRadians(vertexAngleDegrees.toDouble())
        val legLength = 0.3f
        val kneeX = (hipX + legLength * kotlin.math.cos(angleRad + Math.PI / 2)).toFloat()
        val kneeY = (hipY + legLength * kotlin.math.sin(angleRad + Math.PI / 2)).toFloat()

        val landmarks = createLandmarksWithHipAngle(
            shoulderMidX = shoulderX,
            shoulderMidY = shoulderY,
            hipMidX = hipX,
            hipMidY = hipY,
            kneeMidX = kneeX,
            kneeMidY = kneeY,
            visibility = 1.0f
        ).toMutableList()

        // Override ankle Y for TDC detection
        landmarks[PoseLandmarkIndex.LEFT_ANKLE] = createLandmark(
            kneeX - 0.05f, ankleY, 1.0f
        )
        landmarks[PoseLandmarkIndex.RIGHT_ANKLE] = createLandmark(
            kneeX + 0.05f, ankleY, 1.0f
        )

        return PoseFrame(
            landmarks = landmarks,
            timestampMs = timestampMs,
            frameNumber = frameNumber,
            imageWidth = 1920,
            imageHeight = 1080,
            confidence = 0.9f
        )
    }

    // ==================== Single Frame Tests ====================

    @Test
    fun `compute at single frame with valid landmarks`() {
        // Create frame with 90 degree hip angle
        val landmarks = createLandmarksWithHipAngle(
            shoulderMidX = 0.5f, shoulderMidY = 0.2f,
            hipMidX = 0.5f, hipMidY = 0.5f,
            kneeMidX = 0.8f, kneeMidY = 0.5f
        )

        val frame = PoseFrame(
            landmarks = landmarks,
            timestampMs = 1000L,
            frameNumber = 10,
            imageWidth = 1920,
            imageHeight = 1080,
            confidence = 0.9f
        )

        val result = HipAngleAtTdc.computeAtFrame(frame)

        assertTrue(result.isValid)
        assertEquals(90f, result.hipAngle, delta)
        assertEquals(10L, result.frameNumber)
        assertEquals(1000L, result.timestampMs)
        assertTrue(result.confidence > 0.8f)
    }

    @Test
    fun `compute at single frame with hip flexion angle`() {
        // Create a closed hip position (typical at TDC)
        // Shoulder forward, knee high - creates hip flexion
        val landmarks = createLandmarksWithHipAngle(
            shoulderMidX = 0.5f, shoulderMidY = 0.2f,
            hipMidX = 0.5f, hipMidY = 0.5f,
            kneeMidX = 0.65f, kneeMidY = 0.4f
        )

        val frame = PoseFrame(
            landmarks = landmarks,
            timestampMs = 2000L,
            frameNumber = 20,
            imageWidth = 1920,
            imageHeight = 1080,
            confidence = 0.9f
        )

        val result = HipAngleAtTdc.computeAtFrame(frame)

        assertTrue(result.isValid)
        // Hip flexion angle should be positive and reasonable for cycling
        assertTrue(result.hipAngle > 0f)
        assertTrue(result.hipAngle < 180f)
    }

    @Test
    fun `compute at frame with low visibility returns invalid`() {
        val landmarks = createLandmarksWithHipAngle(
            shoulderMidX = 0.5f, shoulderMidY = 0.2f,
            hipMidX = 0.5f, hipMidY = 0.5f,
            kneeMidX = 0.8f, kneeMidY = 0.5f,
            visibility = 0.3f // Below default threshold
        )

        val frame = PoseFrame(
            landmarks = landmarks,
            timestampMs = 1000L,
            frameNumber = 10,
            imageWidth = 1920,
            imageHeight = 1080,
            confidence = 0.9f
        )

        val result = HipAngleAtTdc.computeAtFrame(frame)

        assertFalse(result.isValid)
        assertEquals(0f, result.hipAngle)
    }

    @Test
    fun `compute at frame with custom visibility threshold`() {
        val landmarks = createLandmarksWithHipAngle(
            shoulderMidX = 0.5f, shoulderMidY = 0.2f,
            hipMidX = 0.5f, hipMidY = 0.5f,
            kneeMidX = 0.8f, kneeMidY = 0.5f,
            visibility = 0.4f
        )

        val frame = PoseFrame(
            landmarks = landmarks,
            timestampMs = 1000L,
            frameNumber = 10,
            imageWidth = 1920,
            imageHeight = 1080,
            confidence = 0.9f
        )

        // With default threshold (0.5), should be invalid
        val resultDefault = HipAngleAtTdc.computeAtFrame(frame)
        assertFalse(resultDefault.isValid)

        // With lower threshold (0.3), should be valid
        val config = HipAngleAtTdcConfig(visibilityThreshold = 0.3f)
        val resultLower = HipAngleAtTdc.computeAtFrame(frame, config)
        assertTrue(resultLower.isValid)
        assertEquals(90f, resultLower.hipAngle, delta)
    }

    // ==================== Frame Sequence Tests ====================

    @Test
    fun `compute from frames with no frames returns invalid`() {
        val result = HipAngleAtTdc.computeFromFrames(emptyList())

        assertFalse(result.isValid)
        assertEquals(0, result.cycleCount)
    }

    @Test
    fun `compute from frames detects minimum hip angle`() {
        // Create a sequence with varying hip angles at different heights
        // Simulate pedal cycle with TDC (ankle high) and BDC (ankle low)
        val frames = listOf(
            createCyclingPoseFrame(0, 0, ankleY = 0.8f, vertexAngleDegrees = 120f),    // BDC
            createCyclingPoseFrame(5, 160, ankleY = 0.6f, vertexAngleDegrees = 90f),   // Mid
            createCyclingPoseFrame(10, 330, ankleY = 0.4f, vertexAngleDegrees = 50f),  // TDC
            createCyclingPoseFrame(15, 500, ankleY = 0.6f, vertexAngleDegrees = 90f),  // Mid
            createCyclingPoseFrame(20, 660, ankleY = 0.8f, vertexAngleDegrees = 120f), // BDC
            createCyclingPoseFrame(25, 830, ankleY = 0.6f, vertexAngleDegrees = 90f),  // Mid
            createCyclingPoseFrame(30, 1000, ankleY = 0.4f, vertexAngleDegrees = 45f), // TDC (more closed)
            createCyclingPoseFrame(35, 1160, ankleY = 0.6f, vertexAngleDegrees = 90f)  // Mid
        )

        val result = HipAngleAtTdc.computeFromFrames(frames)

        assertTrue(result.isValid)
        // Should have detected 2 TDC events
        assertTrue(result.cycleCount >= 1)
        // Minimum should be around 45 degrees (most closed hip)
        assertTrue(result.minHipAngle < 60f)
        assertTrue(result.minHipAngle > 0f)
    }

    @Test
    fun `compute at all TDC frames returns list of results`() {
        // Create frames with clear TDC/BDC pattern including mid-points for proper detection
        val frames = listOf(
            createCyclingPoseFrame(0, 0, ankleY = 0.8f, vertexAngleDegrees = 120f),     // BDC
            createCyclingPoseFrame(5, 160, ankleY = 0.6f, vertexAngleDegrees = 90f),    // Mid
            createCyclingPoseFrame(10, 330, ankleY = 0.4f, vertexAngleDegrees = 50f),   // TDC
            createCyclingPoseFrame(15, 500, ankleY = 0.6f, vertexAngleDegrees = 90f),   // Mid
            createCyclingPoseFrame(20, 660, ankleY = 0.8f, vertexAngleDegrees = 120f),  // BDC
            createCyclingPoseFrame(25, 830, ankleY = 0.6f, vertexAngleDegrees = 90f),   // Mid
            createCyclingPoseFrame(30, 1000, ankleY = 0.4f, vertexAngleDegrees = 45f),  // TDC
            createCyclingPoseFrame(35, 1160, ankleY = 0.6f, vertexAngleDegrees = 90f),  // Mid
            createCyclingPoseFrame(40, 1330, ankleY = 0.8f, vertexAngleDegrees = 120f)  // BDC
        )

        val results = HipAngleAtTdc.computeAtAllTdcFrames(frames)

        // Should detect at least 1 TDC
        assertTrue(results.isNotEmpty())
        // All results should be valid
        assertTrue(results.all { it.isValid })
        // All hip angles should be reasonable (now hip flexion = 180 - vertex)
        // vertexAngleDegrees 50° → hipFlexion 130°, vertexAngleDegrees 45° → hipFlexion 135°
        assertTrue(results.all { it.hipAngle > 0f && it.hipAngle < 180f })
    }

    // ==================== Configuration Tests ====================

    @Test
    fun `compute with single-side configuration`() {
        val landmarks = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) {
            createLandmark(0f, 0f, 1.0f)
        }

        // Set LEFT side landmarks for 90 degree angle
        landmarks[PoseLandmarkIndex.LEFT_SHOULDER] = createLandmark(0f, 0f, 1.0f)
        landmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0f, 1f, 1.0f)
        landmarks[PoseLandmarkIndex.LEFT_KNEE] = createLandmark(1f, 1f, 1.0f)
        landmarks[PoseLandmarkIndex.LEFT_ANKLE] = createLandmark(1f, 1.3f, 1.0f)

        val frame = PoseFrame(
            landmarks = landmarks,
            timestampMs = 1000L,
            frameNumber = 10,
            imageWidth = 1920,
            imageHeight = 1080,
            confidence = 0.9f
        )

        val config = HipAngleAtTdcConfig(useMidpoints = false, side = BodySide.LEFT)
        val result = HipAngleAtTdc.computeAtFrame(frame, config)

        assertTrue(result.isValid)
        assertEquals(90f, result.hipAngle, delta)
    }

    @Test
    fun `compute with midpoints configuration requires all landmarks`() {
        val landmarks = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) {
            createLandmark(0f, 0f, 1.0f)
        }

        // Set only LEFT side landmarks
        landmarks[PoseLandmarkIndex.LEFT_SHOULDER] = createLandmark(0f, 0f, 1.0f)
        landmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0f, 1f, 1.0f)
        landmarks[PoseLandmarkIndex.LEFT_KNEE] = createLandmark(1f, 1f, 1.0f)
        
        // Set RIGHT side with low visibility
        landmarks[PoseLandmarkIndex.RIGHT_SHOULDER] = createLandmark(0f, 0f, 0.3f)
        landmarks[PoseLandmarkIndex.RIGHT_HIP] = createLandmark(0f, 1f, 0.3f)
        landmarks[PoseLandmarkIndex.RIGHT_KNEE] = createLandmark(1f, 1f, 0.3f)

        val frame = PoseFrame(
            landmarks = landmarks,
            timestampMs = 1000L,
            frameNumber = 10,
            imageWidth = 1920,
            imageHeight = 1080,
            confidence = 0.9f
        )

        // With midpoints (default), should be invalid due to low visibility on right
        val resultMidpoints = HipAngleAtTdc.computeAtFrame(frame)
        assertFalse(resultMidpoints.isValid)

        // With single-side, should be valid
        val config = HipAngleAtTdcConfig(useMidpoints = false, side = BodySide.LEFT)
        val resultSingleSide = HipAngleAtTdc.computeAtFrame(frame, config)
        assertTrue(resultSingleSide.isValid)
    }

    // ==================== Statistics Tests ====================

    @Test
    fun `summary calculates correct statistics`() {
        // Create frames with known hip angles at TDC, including mid-points for proper detection
        val frames = listOf(
            createCyclingPoseFrame(0, 0, ankleY = 0.8f, vertexAngleDegrees = 120f),     // BDC
            createCyclingPoseFrame(5, 160, ankleY = 0.6f, vertexAngleDegrees = 90f),    // Mid
            createCyclingPoseFrame(10, 330, ankleY = 0.4f, vertexAngleDegrees = 50f),   // TDC
            createCyclingPoseFrame(15, 500, ankleY = 0.6f, vertexAngleDegrees = 90f),   // Mid
            createCyclingPoseFrame(20, 660, ankleY = 0.8f, vertexAngleDegrees = 120f),  // BDC
            createCyclingPoseFrame(25, 830, ankleY = 0.6f, vertexAngleDegrees = 90f),   // Mid
            createCyclingPoseFrame(30, 1000, ankleY = 0.4f, vertexAngleDegrees = 60f),  // TDC
            createCyclingPoseFrame(35, 1160, ankleY = 0.6f, vertexAngleDegrees = 90f),  // Mid
            createCyclingPoseFrame(40, 1330, ankleY = 0.8f, vertexAngleDegrees = 120f), // BDC
            createCyclingPoseFrame(45, 1500, ankleY = 0.6f, vertexAngleDegrees = 90f),  // Mid
            createCyclingPoseFrame(50, 1660, ankleY = 0.4f, vertexAngleDegrees = 40f),  // TDC
            createCyclingPoseFrame(55, 1830, ankleY = 0.6f, vertexAngleDegrees = 90f)   // Mid
        )

        val result = HipAngleAtTdc.computeFromFrames(frames)

        assertTrue(result.isValid)
        // Should detect TDC events
        assertTrue(result.cycleCount > 0)
        // Min should be less than average
        assertTrue(result.minHipAngle <= result.averageHipAngle)
        // Average should be less than max
        assertTrue(result.averageHipAngle <= result.maxHipAngle)
        // All values should be in valid range
        assertTrue(result.minHipAngle > 0f && result.minHipAngle < 180f)
        assertTrue(result.maxHipAngle > 0f && result.maxHipAngle < 180f)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `invalid result factory methods`() {
        val result = HipAngleAtTdcResult.invalid()
        assertFalse(result.isValid)
        assertEquals(0f, result.hipAngle)
        assertEquals(0L, result.frameNumber)

        val summary = HipAngleAtTdcSummary.invalid()
        assertFalse(summary.isValid)
        assertEquals(0, summary.cycleCount)
        assertEquals(0f, summary.minHipAngle)
    }

    @Test
    fun `compute from frames with no TDC detected returns invalid`() {
        // Create frames with no clear TDC pattern (constant ankle height)
        val frames = listOf(
            createCyclingPoseFrame(0, 0, ankleY = 0.6f, vertexAngleDegrees = 90f),
            createCyclingPoseFrame(10, 330, ankleY = 0.6f, vertexAngleDegrees = 90f),
            createCyclingPoseFrame(20, 660, ankleY = 0.6f, vertexAngleDegrees = 90f)
        )

        val result = HipAngleAtTdc.computeFromFrames(frames)

        assertFalse(result.isValid)
        assertEquals(0, result.cycleCount)
    }

    @Test
    fun `compute at all TDC frames with no frames returns empty list`() {
        val results = HipAngleAtTdc.computeAtAllTdcFrames(emptyList())
        assertTrue(results.isEmpty())
    }
}
