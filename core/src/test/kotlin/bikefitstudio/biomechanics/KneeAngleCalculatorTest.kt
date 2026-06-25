package bikefitstudio.biomechanics

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import bikefitstudio.pose.Landmark
import bikefitstudio.pose.PoseFrame
import bikefitstudio.pose.PoseLandmarkIndex
import bikefitstudio.pose.PoseResult
import kotlin.math.abs

/**
 * Unit tests for KneeAngleCalculator.
 * 
 * Tests verify:
 * - Correct angle calculation with known triangles
 * - Left and right leg detection
 * - Visibility threshold handling
 * - Edge cases (missing landmarks, invalid poses)
 */
class KneeAngleCalculatorTest {

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
     * Creates a list of 33 landmarks with specified leg landmarks.
     * All landmarks default to (0,0) with high visibility except
     * the specified hip, knee, and ankle for the given side.
     */
    private fun createLandmarksWithLeg(
        hipX: Float, hipY: Float,
        kneeX: Float, kneeY: Float,
        ankleX: Float, ankleY: Float,
        side: BodySide,
        visibility: Float = 1.0f
    ): List<Landmark> {
        val landmarks = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) {
            createLandmark(0f, 0f, 1.0f)
        }

        val hipIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_HIP
        } else {
            PoseLandmarkIndex.RIGHT_HIP
        }

        val kneeIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_KNEE
        } else {
            PoseLandmarkIndex.RIGHT_KNEE
        }

        val ankleIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_ANKLE
        } else {
            PoseLandmarkIndex.RIGHT_ANKLE
        }

        landmarks[hipIndex] = createLandmark(hipX, hipY, visibility)
        landmarks[kneeIndex] = createLandmark(kneeX, kneeY, visibility)
        landmarks[ankleIndex] = createLandmark(ankleX, ankleY, visibility)

        return landmarks
    }

    // ==================== Right Angle Tests (90°) ====================

    @Test
    fun `calculate 90 degree angle - left leg`() {
        // Right angle triangle: hip at (0,0), knee at (0,1), ankle at (1,1)
        val landmarks = createLandmarksWithLeg(
            hipX = 0f, hipY = 0f,
            kneeX = 0f, kneeY = 1f,
            ankleX = 1f, ankleY = 1f,
            side = BodySide.LEFT
        )

        val result = KneeAngleCalculator.calculateKneeAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        assertEquals(BodySide.LEFT, result.side)
        assertEquals(90f, result.angle, delta)
    }

    @Test
    fun `calculate 90 degree angle - right leg`() {
        val landmarks = createLandmarksWithLeg(
            hipX = 0f, hipY = 0f,
            kneeX = 0f, kneeY = 1f,
            ankleX = 1f, ankleY = 1f,
            side = BodySide.RIGHT
        )

        val result = KneeAngleCalculator.calculateKneeAngleFromLandmarks(
            landmarks,
            BodySide.RIGHT
        )

        assertTrue(result.isValid)
        assertEquals(BodySide.RIGHT, result.side)
        assertEquals(90f, result.angle, delta)
    }

    // ==================== Straight Leg Tests (180°) ====================

    @Test
    fun `calculate 180 degree angle - straight leg vertical`() {
        // Straight vertical leg: hip at (0.5, 0), knee at (0.5, 0.5), ankle at (0.5, 1)
        val landmarks = createLandmarksWithLeg(
            hipX = 0.5f, hipY = 0f,
            kneeX = 0.5f, kneeY = 0.5f,
            ankleX = 0.5f, ankleY = 1f,
            side = BodySide.LEFT
        )

        val result = KneeAngleCalculator.calculateKneeAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        assertEquals(180f, result.angle, delta)
    }

    @Test
    fun `calculate 180 degree angle - straight leg horizontal`() {
        // Straight horizontal leg
        val landmarks = createLandmarksWithLeg(
            hipX = 0f, hipY = 0.5f,
            kneeX = 0.5f, kneeY = 0.5f,
            ankleX = 1f, ankleY = 0.5f,
            side = BodySide.LEFT
        )

        val result = KneeAngleCalculator.calculateKneeAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        assertEquals(180f, result.angle, delta)
    }

    @Test
    fun `calculate 180 degree angle - straight leg diagonal`() {
        // Straight diagonal leg
        val landmarks = createLandmarksWithLeg(
            hipX = 0f, hipY = 0f,
            kneeX = 0.5f, kneeY = 0.5f,
            ankleX = 1f, ankleY = 1f,
            side = BodySide.LEFT
        )

        val result = KneeAngleCalculator.calculateKneeAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        assertEquals(180f, result.angle, delta)
    }

    // ==================== Other Angle Tests ====================

    @Test
    fun `calculate 60 degree angle`() {
        // Equilateral triangle has 60 degree angles
        // Using coordinates that form 60 degrees at knee
        val landmarks = createLandmarksWithLeg(
            hipX = 0f, hipY = 0f,
            kneeX = 1f, kneeY = 0f,
            ankleX = 0.5f, ankleY = 0.866f, // sqrt(3)/2 ≈ 0.866
            side = BodySide.LEFT
        )

        val result = KneeAngleCalculator.calculateKneeAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        assertEquals(60f, result.angle, 1f) // Slightly larger tolerance for this triangle
    }

    @Test
    fun `calculate 120 degree angle`() {
        // 120 degrees at the knee - using coordinates that form correct angle
        // For a 120 degree angle at (0.5, 0.5), we position hip and ankle accordingly
        // Using direct coordinate test to verify
        val angle = KneeAngleCalculator.calculateKneeAngleFromCoordinates(
            hipX = 0f, hipY = 0f,
            kneeX = 1f, kneeY = 0f,
            ankleX = 1.5f, ankleY = 0.866f // This creates 120 degree angle
        )

        assertEquals(120f, angle, 1f)
    }

    @Test
    fun `calculate 145 degree angle - optimal cycling`() {
        // Using direct coordinate calculation for known angle
        val angle = KneeAngleCalculator.calculateKneeAngleFromCoordinates(
            hipX = 0f, hipY = 0f,
            kneeX = 0f, kneeY = 1f,
            ankleX = -0.5736f, ankleY = 1.8192f // Creates ~145 degree angle
        )

        assertEquals(145f, angle, 2f) // Allow some tolerance
    }

    // ==================== Visibility Tests ====================

    @Test
    fun `returns invalid when hip not visible`() {
        val landmarks = createLandmarksWithLeg(
            hipX = 0f, hipY = 0f,
            kneeX = 0f, kneeY = 1f,
            ankleX = 1f, ankleY = 1f,
            side = BodySide.LEFT,
            visibility = 1.0f
        ).toMutableList()

        // Set hip visibility to low
        landmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0f, 0f, 0.3f)

        val result = KneeAngleCalculator.calculateKneeAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertFalse(result.isValid)
        assertEquals(0f, result.angle, 0.001f)
    }

    @Test
    fun `returns invalid when knee not visible`() {
        val landmarks = createLandmarksWithLeg(
            hipX = 0f, hipY = 0f,
            kneeX = 0f, kneeY = 1f,
            ankleX = 1f, ankleY = 1f,
            side = BodySide.LEFT,
            visibility = 1.0f
        ).toMutableList()

        // Set knee visibility to low
        landmarks[PoseLandmarkIndex.LEFT_KNEE] = createLandmark(0f, 1f, 0.3f)

        val result = KneeAngleCalculator.calculateKneeAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertFalse(result.isValid)
    }

    @Test
    fun `returns invalid when ankle not visible`() {
        val landmarks = createLandmarksWithLeg(
            hipX = 0f, hipY = 0f,
            kneeX = 0f, kneeY = 1f,
            ankleX = 1f, ankleY = 1f,
            side = BodySide.LEFT,
            visibility = 1.0f
        ).toMutableList()

        // Set ankle visibility to low
        landmarks[PoseLandmarkIndex.LEFT_ANKLE] = createLandmark(1f, 1f, 0.2f)

        val result = KneeAngleCalculator.calculateKneeAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertFalse(result.isValid)
    }

    @Test
    fun `custom visibility threshold`() {
        val landmarks = createLandmarksWithLeg(
            hipX = 0f, hipY = 0f,
            kneeX = 0f, kneeY = 1f,
            ankleX = 1f, ankleY = 1f,
            side = BodySide.LEFT,
            visibility = 0.4f // Below default threshold of 0.5
        )

        // With default threshold, should be invalid
        val resultDefault = KneeAngleCalculator.calculateKneeAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )
        assertFalse(resultDefault.isValid)

        // With lower threshold, should be valid
        val resultLower = KneeAngleCalculator.calculateKneeAngleFromLandmarks(
            landmarks,
            BodySide.LEFT,
            visibilityThreshold = 0.3f
        )
        assertTrue(resultLower.isValid)
        assertEquals(90f, resultLower.angle, delta)
    }

    // ==================== Confidence Tests ====================

    @Test
    fun `confidence is average of landmark visibilities`() {
        val landmarks = createLandmarksWithLeg(
            hipX = 0f, hipY = 0f,
            kneeX = 0f, kneeY = 1f,
            ankleX = 1f, ankleY = 1f,
            side = BodySide.LEFT
        ).toMutableList()

        // Set different visibilities
        landmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0f, 0f, 0.9f)
        landmarks[PoseLandmarkIndex.LEFT_KNEE] = createLandmark(0f, 1f, 0.8f)
        landmarks[PoseLandmarkIndex.LEFT_ANKLE] = createLandmark(1f, 1f, 0.7f)

        val result = KneeAngleCalculator.calculateKneeAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        assertEquals(0.8f, result.confidence, 0.01f) // (0.9 + 0.8 + 0.7) / 3 = 0.8
    }

    // ==================== PoseResult Tests ====================

    @Test
    fun `calculate from PoseResult`() {
        val landmarks = createLandmarksWithLeg(
            hipX = 0f, hipY = 0f,
            kneeX = 0f, kneeY = 1f,
            ankleX = 1f, ankleY = 1f,
            side = BodySide.LEFT
        )

        val poseResult = PoseResult(
            landmarks = landmarks,
            timestampMs = 1000L,
            isValid = true,
            confidence = 0.9f
        )

        val result = KneeAngleCalculator.calculateKneeAngle(poseResult, BodySide.LEFT)

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

        val result = KneeAngleCalculator.calculateKneeAngle(poseResult, BodySide.LEFT)

        assertFalse(result.isValid)
    }

    // ==================== PoseFrame Tests ====================

    @Test
    fun `calculate from PoseFrame`() {
        val landmarks = createLandmarksWithLeg(
            hipX = 0f, hipY = 0f,
            kneeX = 0f, kneeY = 1f,
            ankleX = 1f, ankleY = 1f,
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

        val result = KneeAngleCalculator.calculateKneeAngle(poseFrame, BodySide.RIGHT)

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

        val result = KneeAngleCalculator.calculateKneeAngle(poseFrame, BodySide.LEFT)

        assertFalse(result.isValid)
    }

    // ==================== Both Legs Tests ====================

    @Test
    fun `calculate both knee angles from PoseResult`() {
        val landmarks = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) {
            createLandmark(0f, 0f, 1.0f)
        }

        // Left leg - 90 degree angle
        landmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0f, 0f, 1.0f)
        landmarks[PoseLandmarkIndex.LEFT_KNEE] = createLandmark(0f, 1f, 1.0f)
        landmarks[PoseLandmarkIndex.LEFT_ANKLE] = createLandmark(1f, 1f, 1.0f)

        // Right leg - 180 degree angle (straight)
        landmarks[PoseLandmarkIndex.RIGHT_HIP] = createLandmark(0.5f, 0f, 1.0f)
        landmarks[PoseLandmarkIndex.RIGHT_KNEE] = createLandmark(0.5f, 0.5f, 1.0f)
        landmarks[PoseLandmarkIndex.RIGHT_ANKLE] = createLandmark(0.5f, 1f, 1.0f)

        val poseResult = PoseResult(
            landmarks = landmarks,
            timestampMs = 1000L,
            isValid = true,
            confidence = 0.9f
        )

        val (leftResult, rightResult) = KneeAngleCalculator.calculateBothKneeAngles(poseResult)

        assertTrue(leftResult.isValid)
        assertEquals(BodySide.LEFT, leftResult.side)
        assertEquals(90f, leftResult.angle, delta)

        assertTrue(rightResult.isValid)
        assertEquals(BodySide.RIGHT, rightResult.side)
        assertEquals(180f, rightResult.angle, delta)
    }

    @Test
    fun `calculate both knee angles from PoseFrame`() {
        val landmarks = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) {
            createLandmark(0f, 0f, 1.0f)
        }

        // Left leg - 90 degree angle
        landmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0f, 0f, 1.0f)
        landmarks[PoseLandmarkIndex.LEFT_KNEE] = createLandmark(0f, 1f, 1.0f)
        landmarks[PoseLandmarkIndex.LEFT_ANKLE] = createLandmark(1f, 1f, 1.0f)

        // Right leg - 180 degree angle (straight)
        landmarks[PoseLandmarkIndex.RIGHT_HIP] = createLandmark(0.5f, 0f, 1.0f)
        landmarks[PoseLandmarkIndex.RIGHT_KNEE] = createLandmark(0.5f, 0.5f, 1.0f)
        landmarks[PoseLandmarkIndex.RIGHT_ANKLE] = createLandmark(0.5f, 1f, 1.0f)

        val poseFrame = PoseFrame(
            landmarks = landmarks,
            timestampMs = 1000L,
            frameNumber = 1,
            imageWidth = 1920,
            imageHeight = 1080,
            confidence = 0.9f
        )

        val (leftResult, rightResult) = KneeAngleCalculator.calculateBothKneeAngles(poseFrame)

        assertTrue(leftResult.isValid)
        assertEquals(90f, leftResult.angle, delta)

        assertTrue(rightResult.isValid)
        assertEquals(180f, rightResult.angle, delta)
    }

    // ==================== Direct Coordinate Calculation Tests ====================

    @Test
    fun `calculateKneeAngleFromCoordinates - 90 degrees`() {
        val angle = KneeAngleCalculator.calculateKneeAngleFromCoordinates(
            hipX = 0f, hipY = 0f,
            kneeX = 0f, kneeY = 1f,
            ankleX = 1f, ankleY = 1f
        )

        assertEquals(90f, angle, delta)
    }

    @Test
    fun `calculateKneeAngleFromCoordinates - 180 degrees`() {
        val angle = KneeAngleCalculator.calculateKneeAngleFromCoordinates(
            hipX = 0f, hipY = 0f,
            kneeX = 0.5f, kneeY = 0.5f,
            ankleX = 1f, ankleY = 1f
        )

        assertEquals(180f, angle, delta)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `returns invalid for insufficient landmarks`() {
        val landmarks = listOf(createLandmark(0f, 0f, 1.0f)) // Only 1 landmark

        val result = KneeAngleCalculator.calculateKneeAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertFalse(result.isValid)
    }

    @Test
    fun `invalid result factory method`() {
        val result = KneeAngleResult.invalid(BodySide.RIGHT)

        assertFalse(result.isValid)
        assertEquals(BodySide.RIGHT, result.side)
        assertEquals(0f, result.angle, 0.001f)
        assertEquals(0f, result.confidence, 0.001f)
    }

    // ==================== Realistic Cycling Position Tests ====================

    @Test
    fun `realistic cycling position - knee at top of stroke`() {
        // Knee angle when pedal is at top position (most bent)
        // Typically around 70-90 degrees
        val landmarks = createLandmarksWithLeg(
            hipX = 0.3f, hipY = 0.3f,
            kneeX = 0.4f, kneeY = 0.6f,
            ankleX = 0.5f, ankleY = 0.4f, // Foot up
            side = BodySide.LEFT
        )

        val result = KneeAngleCalculator.calculateKneeAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        // The angle should be acute (less than 120 degrees)
        assertTrue(result.angle < 120f)
    }

    @Test
    fun `realistic cycling position - knee at bottom of stroke`() {
        // Knee angle when pedal is at bottom position (most extended)
        // Typically around 140-150 degrees
        val landmarks = createLandmarksWithLeg(
            hipX = 0.3f, hipY = 0.3f,
            kneeX = 0.35f, kneeY = 0.6f,
            ankleX = 0.4f, ankleY = 0.9f, // Foot down
            side = BodySide.LEFT
        )

        val result = KneeAngleCalculator.calculateKneeAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        // The angle should be obtuse (more than 120 degrees)
        assertTrue(result.angle > 120f)
    }
}
