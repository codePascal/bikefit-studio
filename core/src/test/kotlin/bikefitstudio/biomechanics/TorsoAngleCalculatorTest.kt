package bikefitstudio.biomechanics

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import bikefitstudio.pose.Landmark
import bikefitstudio.pose.PoseFrame
import bikefitstudio.pose.PoseLandmarkIndex
import bikefitstudio.pose.PoseResult

/**
 * Unit tests for TorsoAngleCalculator.
 * 
 * Tests verify:
 * - Correct angle calculation for known positions
 * - Left and right side detection
 * - Visibility threshold handling
 * - Edge cases (missing landmarks, invalid poses)
 * 
 * Convention: 0° = horizontal, 90° = vertical
 */
class TorsoAngleCalculatorTest {

    private val delta = 1f // Tolerance for angle comparisons

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
     * Creates a list of 33 landmarks with specified shoulder and hip landmarks.
     */
    private fun createLandmarksWithTorso(
        shoulderX: Float, shoulderY: Float,
        hipX: Float, hipY: Float,
        side: BodySide,
        visibility: Float = 1.0f
    ): List<Landmark> {
        val landmarks = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) {
            createLandmark(0f, 0f, 1.0f)
        }

        val shoulderIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_SHOULDER
        } else {
            PoseLandmarkIndex.RIGHT_SHOULDER
        }

        val hipIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_HIP
        } else {
            PoseLandmarkIndex.RIGHT_HIP
        }

        landmarks[shoulderIndex] = createLandmark(shoulderX, shoulderY, visibility)
        landmarks[hipIndex] = createLandmark(hipX, hipY, visibility)

        return landmarks
    }

    // ==================== Vertical Torso Tests (90°) ====================

    @Test
    fun `calculate 90 degree angle - fully upright`() {
        // Vertical torso: shoulder directly above hip
        // In image coords: shoulder at (0.5, 0.3), hip at (0.5, 0.6)
        val landmarks = createLandmarksWithTorso(
            shoulderX = 0.5f, shoulderY = 0.3f,
            hipX = 0.5f, hipY = 0.6f,
            side = BodySide.LEFT
        )

        val result = TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        assertEquals(90f, result.angle, delta)
    }

    @Test
    fun `calculate 90 degree angle - right side`() {
        val landmarks = createLandmarksWithTorso(
            shoulderX = 0.5f, shoulderY = 0.3f,
            hipX = 0.5f, hipY = 0.6f,
            side = BodySide.RIGHT
        )

        val result = TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(
            landmarks,
            BodySide.RIGHT
        )

        assertTrue(result.isValid)
        assertEquals(BodySide.RIGHT, result.side)
        assertEquals(90f, result.angle, delta)
    }

    // ==================== Horizontal Torso Tests (0°) ====================

    @Test
    fun `calculate 0 degree angle - fully horizontal`() {
        // Horizontal torso: shoulder at same Y as hip
        val landmarks = createLandmarksWithTorso(
            shoulderX = 0.3f, shoulderY = 0.5f,
            hipX = 0.6f, hipY = 0.5f,
            side = BodySide.LEFT
        )

        val result = TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        assertEquals(0f, result.angle, delta)
    }

    // ==================== 45 Degree Tests ====================

    @Test
    fun `calculate 45 degree angle`() {
        // 45 degree torso: equal horizontal and vertical displacement
        val landmarks = createLandmarksWithTorso(
            shoulderX = 0.4f, shoulderY = 0.4f,
            hipX = 0.5f, hipY = 0.5f,
            side = BodySide.LEFT
        )

        val result = TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        assertEquals(45f, result.angle, delta)
    }

    @Test
    fun `calculate 45 degree angle - other direction`() {
        // 45 degree but shoulder is forward (x greater than hip)
        val landmarks = createLandmarksWithTorso(
            shoulderX = 0.6f, shoulderY = 0.4f,
            hipX = 0.5f, hipY = 0.5f,
            side = BodySide.LEFT
        )

        val result = TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        assertEquals(45f, result.angle, delta)
    }

    // ==================== Coordinate-based Tests ====================

    @Test
    fun `calculateTorsoAngleFromCoordinates - vertical`() {
        val angle = TorsoAngleCalculator.calculateTorsoAngleFromCoordinates(
            shoulderX = 0.5f, shoulderY = 0f,
            hipX = 0.5f, hipY = 1f
        )

        assertEquals(90f, angle, delta)
    }

    @Test
    fun `calculateTorsoAngleFromCoordinates - horizontal`() {
        val angle = TorsoAngleCalculator.calculateTorsoAngleFromCoordinates(
            shoulderX = 0f, shoulderY = 0.5f,
            hipX = 1f, hipY = 0.5f
        )

        assertEquals(0f, angle, delta)
    }

    @Test
    fun `calculateTorsoAngleFromCoordinates - 30 degrees`() {
        // tan(30°) ≈ 0.577, so for horizontal distance of 1, vertical is ~0.577
        val angle = TorsoAngleCalculator.calculateTorsoAngleFromCoordinates(
            shoulderX = 0f, shoulderY = 0.5f - 0.5f,  // shoulder
            hipX = 0.866f, hipY = 0.5f                 // hip (cos(30°) = 0.866)
        )

        assertEquals(30f, angle, 2f)
    }

    @Test
    fun `calculateTorsoAngleFromCoordinates - 60 degrees`() {
        // 60 degree lean
        val angle = TorsoAngleCalculator.calculateTorsoAngleFromCoordinates(
            shoulderX = 0.5f, shoulderY = 0f,
            hipX = 1f, hipY = 0.866f  // Creates 60 degree angle
        )

        assertEquals(60f, angle, 2f)
    }

    // ==================== Visibility Tests ====================

    @Test
    fun `returns invalid when shoulder not visible`() {
        val landmarks = createLandmarksWithTorso(
            shoulderX = 0.5f, shoulderY = 0.3f,
            hipX = 0.5f, hipY = 0.6f,
            side = BodySide.LEFT,
            visibility = 1.0f
        ).toMutableList()

        landmarks[PoseLandmarkIndex.LEFT_SHOULDER] = createLandmark(0.5f, 0.3f, 0.3f)

        val result = TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertFalse(result.isValid)
    }

    @Test
    fun `returns invalid when hip not visible`() {
        val landmarks = createLandmarksWithTorso(
            shoulderX = 0.5f, shoulderY = 0.3f,
            hipX = 0.5f, hipY = 0.6f,
            side = BodySide.LEFT,
            visibility = 1.0f
        ).toMutableList()

        landmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0.5f, 0.6f, 0.3f)

        val result = TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertFalse(result.isValid)
    }

    @Test
    fun `custom visibility threshold`() {
        val landmarks = createLandmarksWithTorso(
            shoulderX = 0.5f, shoulderY = 0.3f,
            hipX = 0.5f, hipY = 0.6f,
            side = BodySide.LEFT,
            visibility = 0.4f
        )

        // With default threshold, should be invalid
        val resultDefault = TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )
        assertFalse(resultDefault.isValid)

        // With lower threshold, should be valid
        val resultLower = TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(
            landmarks,
            BodySide.LEFT,
            visibilityThreshold = 0.3f
        )
        assertTrue(resultLower.isValid)
    }

    // ==================== Confidence Tests ====================

    @Test
    fun `confidence is average of landmark visibilities`() {
        val landmarks = createLandmarksWithTorso(
            shoulderX = 0.5f, shoulderY = 0.3f,
            hipX = 0.5f, hipY = 0.6f,
            side = BodySide.LEFT
        ).toMutableList()

        landmarks[PoseLandmarkIndex.LEFT_SHOULDER] = createLandmark(0.5f, 0.3f, 0.9f)
        landmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0.5f, 0.6f, 0.7f)

        val result = TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        assertEquals(0.8f, result.confidence, 0.01f) // (0.9 + 0.7) / 2 = 0.8
    }

    // ==================== PoseResult Tests ====================

    @Test
    fun `calculate from PoseResult`() {
        val landmarks = createLandmarksWithTorso(
            shoulderX = 0.5f, shoulderY = 0.3f,
            hipX = 0.5f, hipY = 0.6f,
            side = BodySide.LEFT
        )

        val poseResult = PoseResult(
            landmarks = landmarks,
            timestampMs = 1000L,
            isValid = true,
            confidence = 0.9f
        )

        val result = TorsoAngleCalculator.calculateTorsoAngle(poseResult, BodySide.LEFT)

        assertTrue(result.isValid)
        assertEquals(90f, result.angle, delta)
    }

    @Test
    fun `returns invalid for invalid PoseResult`() {
        val poseResult = PoseResult(
            landmarks = emptyList(),
            timestampMs = 1000L,
            isValid = false,
            confidence = 0f
        )

        val result = TorsoAngleCalculator.calculateTorsoAngle(poseResult, BodySide.LEFT)

        assertFalse(result.isValid)
    }

    // ==================== PoseFrame Tests ====================

    @Test
    fun `calculate from PoseFrame`() {
        val landmarks = createLandmarksWithTorso(
            shoulderX = 0.5f, shoulderY = 0.3f,
            hipX = 0.5f, hipY = 0.6f,
            side = BodySide.RIGHT
        )

        val poseFrame = PoseFrame(
            landmarks = landmarks,
            timestampMs = 1000L,
            frameNumber = 1,
            imageWidth = 1920,
            imageHeight = 1080,
            confidence = 0.9f
        )

        val result = TorsoAngleCalculator.calculateTorsoAngle(poseFrame, BodySide.RIGHT)

        assertTrue(result.isValid)
        assertEquals(90f, result.angle, delta)
    }

    @Test
    fun `returns invalid for empty PoseFrame`() {
        val poseFrame = PoseFrame(
            landmarks = emptyList(),
            timestampMs = 1000L,
            frameNumber = 1,
            imageWidth = 1920,
            imageHeight = 1080,
            confidence = 0f
        )

        val result = TorsoAngleCalculator.calculateTorsoAngle(poseFrame, BodySide.LEFT)

        assertFalse(result.isValid)
    }

    // ==================== Both Sides Tests ====================

    @Test
    fun `calculate both torso angles`() {
        val landmarks = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) {
            createLandmark(0f, 0f, 1.0f)
        }

        // Left side - 90 degrees (vertical)
        landmarks[PoseLandmarkIndex.LEFT_SHOULDER] = createLandmark(0.3f, 0.3f, 1.0f)
        landmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0.3f, 0.6f, 1.0f)

        // Right side - 45 degrees
        landmarks[PoseLandmarkIndex.RIGHT_SHOULDER] = createLandmark(0.6f, 0.4f, 1.0f)
        landmarks[PoseLandmarkIndex.RIGHT_HIP] = createLandmark(0.7f, 0.5f, 1.0f)

        val poseResult = PoseResult(
            landmarks = landmarks,
            timestampMs = 1000L,
            isValid = true,
            confidence = 0.9f
        )

        val (leftResult, rightResult) = TorsoAngleCalculator.calculateBothTorsoAngles(poseResult)

        assertTrue(leftResult.isValid)
        assertEquals(90f, leftResult.angle, delta)

        assertTrue(rightResult.isValid)
        assertEquals(45f, rightResult.angle, delta)
    }

    // ==================== Average Torso Angle Tests ====================

    @Test
    fun `calculate average torso angle - both sides valid`() {
        val landmarks = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) {
            createLandmark(0f, 0f, 1.0f)
        }

        // Left side - 60 degrees
        landmarks[PoseLandmarkIndex.LEFT_SHOULDER] = createLandmark(0.3f, 0.3f, 0.9f)
        landmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0.4732f, 0.6f, 0.9f)

        // Right side - 60 degrees (same)
        landmarks[PoseLandmarkIndex.RIGHT_SHOULDER] = createLandmark(0.5f, 0.3f, 0.8f)
        landmarks[PoseLandmarkIndex.RIGHT_HIP] = createLandmark(0.6732f, 0.6f, 0.8f)

        val poseResult = PoseResult(
            landmarks = landmarks,
            timestampMs = 1000L,
            isValid = true,
            confidence = 0.9f
        )

        val result = TorsoAngleCalculator.calculateAverageTorsoAngle(poseResult)

        assertTrue(result.isValid)
        // Average of two ~60 degree angles
        assertTrue(result.angle > 50f && result.angle < 70f)
    }

    @Test
    fun `calculate average torso angle - only left valid`() {
        val landmarks = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) {
            createLandmark(0f, 0f, 1.0f)
        }

        // Left side - valid
        landmarks[PoseLandmarkIndex.LEFT_SHOULDER] = createLandmark(0.5f, 0.3f, 0.9f)
        landmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0.5f, 0.6f, 0.9f)

        // Right side - not visible
        landmarks[PoseLandmarkIndex.RIGHT_SHOULDER] = createLandmark(0.5f, 0.3f, 0.2f)
        landmarks[PoseLandmarkIndex.RIGHT_HIP] = createLandmark(0.5f, 0.6f, 0.2f)

        val poseResult = PoseResult(
            landmarks = landmarks,
            timestampMs = 1000L,
            isValid = true,
            confidence = 0.9f
        )

        val result = TorsoAngleCalculator.calculateAverageTorsoAngle(poseResult)

        assertTrue(result.isValid)
        assertEquals(BodySide.LEFT, result.side)
        assertEquals(90f, result.angle, delta)
    }

    @Test
    fun `calculate average torso angle - only right valid`() {
        val landmarks = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) {
            createLandmark(0f, 0f, 1.0f)
        }

        // Left side - not visible
        landmarks[PoseLandmarkIndex.LEFT_SHOULDER] = createLandmark(0.5f, 0.3f, 0.2f)
        landmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0.5f, 0.6f, 0.2f)

        // Right side - valid
        landmarks[PoseLandmarkIndex.RIGHT_SHOULDER] = createLandmark(0.5f, 0.3f, 0.9f)
        landmarks[PoseLandmarkIndex.RIGHT_HIP] = createLandmark(0.5f, 0.6f, 0.9f)

        val poseResult = PoseResult(
            landmarks = landmarks,
            timestampMs = 1000L,
            isValid = true,
            confidence = 0.9f
        )

        val result = TorsoAngleCalculator.calculateAverageTorsoAngle(poseResult)

        assertTrue(result.isValid)
        assertEquals(BodySide.RIGHT, result.side)
        assertEquals(90f, result.angle, delta)
    }

    @Test
    fun `calculate average torso angle - neither valid`() {
        val landmarks = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) {
            createLandmark(0f, 0f, 0.2f) // All low visibility
        }

        val poseResult = PoseResult(
            landmarks = landmarks,
            timestampMs = 1000L,
            isValid = true,
            confidence = 0.2f
        )

        val result = TorsoAngleCalculator.calculateAverageTorsoAngle(poseResult)

        assertFalse(result.isValid)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `returns invalid for insufficient landmarks`() {
        val landmarks = listOf(createLandmark(0f, 0f, 1.0f))

        val result = TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertFalse(result.isValid)
    }

    @Test
    fun `invalid result factory method`() {
        val result = TorsoAngleResult.invalid(BodySide.RIGHT)

        assertFalse(result.isValid)
        assertEquals(BodySide.RIGHT, result.side)
        assertEquals(0f, result.angle, 0.001f)
        assertEquals(0f, result.confidence, 0.001f)
    }

    // ==================== Realistic Cycling Position Tests ====================

    @Test
    fun `realistic cycling position - aero tuck`() {
        // Very aggressive position - torso nearly horizontal
        val landmarks = createLandmarksWithTorso(
            shoulderX = 0.6f, shoulderY = 0.45f,  // Shoulder forward
            hipX = 0.5f, hipY = 0.5f,              // Hip
            side = BodySide.LEFT
        )

        val result = TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        // Aggressive aero should be less than 45 degrees
        assertTrue(result.angle < 45f)
    }

    @Test
    fun `realistic cycling position - upright comfort`() {
        // Upright position - torso nearly vertical
        val landmarks = createLandmarksWithTorso(
            shoulderX = 0.48f, shoulderY = 0.3f,
            hipX = 0.5f, hipY = 0.6f,
            side = BodySide.LEFT
        )

        val result = TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        // Upright should be more than 70 degrees
        assertTrue(result.angle > 70f)
    }
}
