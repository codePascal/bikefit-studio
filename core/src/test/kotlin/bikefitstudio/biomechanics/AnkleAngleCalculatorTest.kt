package bikefitstudio.biomechanics

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import bikefitstudio.pose.Landmark
import bikefitstudio.pose.PoseFrame
import bikefitstudio.pose.PoseLandmarkIndex
import bikefitstudio.pose.PoseResult

/**
 * Unit tests for AnkleAngleCalculator.
 * 
 * Tests verify:
 * - Correct angle calculation with known triangles
 * - Left and right leg detection
 * - Visibility threshold handling
 * - Edge cases (missing landmarks, invalid poses)
 */
class AnkleAngleCalculatorTest {

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
     * the specified knee, ankle, heel, and foot index for the given side.
     */
    private fun createLandmarksWithLeg(
        kneeX: Float, kneeY: Float,
        ankleX: Float, ankleY: Float,
        footIndexX: Float, footIndexY: Float,
        side: BodySide,
        visibility: Float = 1.0f,
        heelX: Float? = null,
        heelY: Float? = null
    ): List<Landmark> {
        val landmarks = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) {
            createLandmark(0f, 0f, 1.0f)
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

        val footIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_FOOT_INDEX
        } else {
            PoseLandmarkIndex.RIGHT_FOOT_INDEX
        }

        val heelIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_HEEL
        } else {
            PoseLandmarkIndex.RIGHT_HEEL
        }

        landmarks[kneeIndex] = createLandmark(kneeX, kneeY, visibility)
        landmarks[ankleIndex] = createLandmark(ankleX, ankleY, visibility)
        landmarks[footIndex] = createLandmark(footIndexX, footIndexY, visibility)
        
        // Set heel slightly behind ankle by default if not provided
        // Calculate vector from Foot to Ankle, and extend it past Ankle to place Heel
        // This ensures Foot-Ankle-Heel are collinear by default
        val dirX = ankleX - footIndexX
        val dirY = ankleY - footIndexY
        val finalHeelX = heelX ?: (ankleX + dirX * 0.3f)
        val finalHeelY = heelY ?: (ankleY + dirY * 0.3f)
        
        landmarks[heelIndex] = createLandmark(finalHeelX, finalHeelY, visibility)

        return landmarks
    }

    // ==================== Right Angle Tests (90°) ====================

    @Test
    fun `calculate 90 degree angle - left leg`() {
        // Right angle triangle: knee at (0,0), ankle at (0,1), foot index at (1,1)
        // Vertex angle = 90°, plantarflexion = 90 - 90 = 0° (neutral)
        val landmarks = createLandmarksWithLeg(
            kneeX = 0f, kneeY = 0f,
            ankleX = 0f, ankleY = 1f,
            footIndexX = 1f, footIndexY = 1f,
            side = BodySide.LEFT
        )

        val result = AnkleAngleCalculator.calculateAnkleAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        assertEquals(BodySide.LEFT, result.side)
        assertEquals(0f, result.angle, delta) // 0° = neutral (no plantarflexion)
    }

    @Test
    fun `calculate 90 degree angle - right leg`() {
        // Vertex angle = 90°, plantarflexion = 0° (neutral)
        val landmarks = createLandmarksWithLeg(
            kneeX = 0f, kneeY = 0f,
            ankleX = 0f, ankleY = 1f,
            footIndexX = 1f, footIndexY = 1f,
            side = BodySide.RIGHT
        )

        val result = AnkleAngleCalculator.calculateAnkleAngleFromLandmarks(
            landmarks,
            BodySide.RIGHT
        )

        assertTrue(result.isValid)
        assertEquals(BodySide.RIGHT, result.side)
        assertEquals(0f, result.angle, delta) // 0° = neutral
    }

    // ==================== Straight Alignment Tests (180°) ====================

    @Test
    fun `calculate 180 degree angle - straight vertical alignment`() {
        // Straight vertical alignment: knee at (0.5, 0), ankle at (0.5, 0.5), foot index at (0.5, 1)
        // Vertex angle = 180°, plantarflexion = 180 - 90 = 90° (extreme plantarflexion)
        val landmarks = createLandmarksWithLeg(
            kneeX = 0.5f, kneeY = 0f,
            ankleX = 0.5f, ankleY = 0.5f,
            footIndexX = 0.5f, footIndexY = 1f,
            side = BodySide.LEFT
        )

        val result = AnkleAngleCalculator.calculateAnkleAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        assertEquals(90f, result.angle, delta)
    }

    @Test
    fun `calculate 180 degree angle - straight horizontal alignment`() {
        // Straight horizontal alignment
        // Vertex angle = 180°, plantarflexion = 90°
        val landmarks = createLandmarksWithLeg(
            kneeX = 0f, kneeY = 0.5f,
            ankleX = 0.5f, ankleY = 0.5f,
            footIndexX = 1f, footIndexY = 0.5f,
            side = BodySide.LEFT
        )

        val result = AnkleAngleCalculator.calculateAnkleAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        assertEquals(90f, result.angle, delta)
    }

    @Test
    fun `calculate 180 degree angle - straight diagonal alignment`() {
        // Straight diagonal alignment
        // Vertex angle = 180°, plantarflexion = 90°
        val landmarks = createLandmarksWithLeg(
            kneeX = 0f, kneeY = 0f,
            ankleX = 0.5f, ankleY = 0.5f,
            footIndexX = 1f, footIndexY = 1f,
            side = BodySide.LEFT
        )

        val result = AnkleAngleCalculator.calculateAnkleAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        assertEquals(90f, result.angle, delta)
    }

    // ==================== Other Angle Tests ====================

    @Test
    fun `calculate 60 degree angle`() {
        // Using coordinates that form 60 degrees at ankle
        // Vertex angle = 60°, plantarflexion = 60 - 90 = -30° (dorsiflexion)
        val footIndexY = kotlin.math.sqrt(3.0f) / 2f // Exact value for equilateral triangle
        val landmarks = createLandmarksWithLeg(
            kneeX = 0f, kneeY = 0f,
            ankleX = 1f, ankleY = 0f,
            footIndexX = 0.5f, footIndexY = footIndexY,
            side = BodySide.LEFT
        )

        val result = AnkleAngleCalculator.calculateAnkleAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        assertEquals(-30f, result.angle, 1f) // Slightly larger tolerance
    }

    @Test
    fun `calculate 120 degree angle`() {
        // 120 degrees at the ankle vertex
        // Plantarflexion = 120 - 90 = 30° (typical cycling plantarflexion at BDC)
        val angle = AnkleAngleCalculator.calculateAnkleAngleFromCoordinates(
            kneeX = 0f, kneeY = 0f,
            ankleX = 1f, ankleY = 0f,
            footIndexX = 1.5f, footIndexY = 0.866f
        )

        assertEquals(30f, angle, 1f)
    }

    @Test
    fun `calculate obtuse angle - plantarflexion`() {
        // Using direct coordinate calculation for obtuse angle
        // Knee at origin, ankle below, foot extends forward and down
        // Vertex angle ~159°, plantarflexion = 159 - 90 = 69°
        val angle = AnkleAngleCalculator.calculateAnkleAngleFromCoordinates(
            kneeX = 0f, kneeY = 0f,
            ankleX = 0f, ankleY = 1f,
            footIndexX = 0.364f, footIndexY = 1.940f // Creates obtuse angle ~159 degrees
        )

        assertEquals(69f, angle, 2f) // Allow some tolerance
    }

    // ==================== Visibility Tests ====================

    @Test
    fun `returns invalid when knee not visible`() {
        val landmarks = createLandmarksWithLeg(
            kneeX = 0f, kneeY = 0f,
            ankleX = 0f, ankleY = 1f,
            footIndexX = 1f, footIndexY = 1f,
            side = BodySide.LEFT,
            visibility = 1.0f
        ).toMutableList()

        // Set knee visibility to low
        landmarks[PoseLandmarkIndex.LEFT_KNEE] = createLandmark(0f, 0f, 0.3f)

        val result = AnkleAngleCalculator.calculateAnkleAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertFalse(result.isValid)
        assertEquals(0f, result.angle, 0.001f)
    }

    @Test
    fun `returns invalid when ankle not visible`() {
        val landmarks = createLandmarksWithLeg(
            kneeX = 0f, kneeY = 0f,
            ankleX = 0f, ankleY = 1f,
            footIndexX = 1f, footIndexY = 1f,
            side = BodySide.LEFT,
            visibility = 1.0f
        ).toMutableList()

        // Set ankle visibility to low
        landmarks[PoseLandmarkIndex.LEFT_ANKLE] = createLandmark(0f, 1f, 0.3f)

        val result = AnkleAngleCalculator.calculateAnkleAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertFalse(result.isValid)
    }

    @Test
    fun `returns invalid when foot index not visible`() {
        val landmarks = createLandmarksWithLeg(
            kneeX = 0f, kneeY = 0f,
            ankleX = 0f, ankleY = 1f,
            footIndexX = 1f, footIndexY = 1f,
            side = BodySide.LEFT,
            visibility = 1.0f
        ).toMutableList()

        // Set foot index visibility to low
        landmarks[PoseLandmarkIndex.LEFT_FOOT_INDEX] = createLandmark(1f, 1f, 0.2f)

        val result = AnkleAngleCalculator.calculateAnkleAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertFalse(result.isValid)
    }

    @Test
    fun `custom visibility threshold`() {
        val landmarks = createLandmarksWithLeg(
            kneeX = 0f, kneeY = 0f,
            ankleX = 0f, ankleY = 1f,
            footIndexX = 1f, footIndexY = 1f,
            side = BodySide.LEFT,
            visibility = 0.4f // Below default threshold of 0.5
        )

        // With default threshold, should be invalid
        val resultDefault = AnkleAngleCalculator.calculateAnkleAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )
        assertFalse(resultDefault.isValid)

        // With lower threshold, should be valid
        val resultLower = AnkleAngleCalculator.calculateAnkleAngleFromLandmarks(
            landmarks,
            BodySide.LEFT,
            visibilityThreshold = 0.3f
        )
        assertTrue(resultLower.isValid)
        assertEquals(0f, resultLower.angle, delta) // Neutral = 0° plantarflexion
    }

    // ==================== Confidence Tests ====================

    @Test
    fun `confidence is average of landmark visibilities`() {
        val landmarks = createLandmarksWithLeg(
            kneeX = 0f, kneeY = 0f,
            ankleX = 0f, ankleY = 1f,
            footIndexX = 1f, footIndexY = 1f,
            side = BodySide.LEFT
        ).toMutableList()

        // Set different visibilities
        landmarks[PoseLandmarkIndex.LEFT_KNEE] = createLandmark(0f, 0f, 0.9f)
        landmarks[PoseLandmarkIndex.LEFT_ANKLE] = createLandmark(0f, 1f, 0.8f)
        landmarks[PoseLandmarkIndex.LEFT_FOOT_INDEX] = createLandmark(1f, 1f, 0.7f)
        // Set heel visibility to ensure average is predictable: (0.9 + 0.8 + 0.7 + 0.8) / 4 = 0.8
        landmarks[PoseLandmarkIndex.LEFT_HEEL] = createLandmark(0.1f, 1.1f, 0.8f)

        val result = AnkleAngleCalculator.calculateAnkleAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        assertEquals(0.8f, result.confidence, 0.01f)
    }

    // ==================== PoseResult Tests ====================

    @Test
    fun `calculate from PoseResult`() {
        val landmarks = createLandmarksWithLeg(
            kneeX = 0f, kneeY = 0f,
            ankleX = 0f, ankleY = 1f,
            footIndexX = 1f, footIndexY = 1f,
            side = BodySide.LEFT
        )

        val poseResult = PoseResult(
            landmarks = landmarks,
            timestampMs = 1000L,
            isValid = true,
            confidence = 0.9f
        )

        val result = AnkleAngleCalculator.calculateAnkleAngle(poseResult, BodySide.LEFT)

        assertTrue(result.isValid)
        assertEquals(0f, result.angle, delta) // Neutral = 0° plantarflexion
    }

    @Test
    fun `returns invalid for invalid PoseResult`() {
        val poseResult = PoseResult(
            landmarks = emptyList(),
            timestampMs = 1000L,
            isValid = false,
            confidence = 0f
        )

        val result = AnkleAngleCalculator.calculateAnkleAngle(poseResult, BodySide.LEFT)

        assertFalse(result.isValid)
    }

    // ==================== PoseFrame Tests ====================

    @Test
    fun `calculate from PoseFrame`() {
        val landmarks = createLandmarksWithLeg(
            kneeX = 0f, kneeY = 0f,
            ankleX = 0f, ankleY = 1f,
            footIndexX = 1f, footIndexY = 1f,
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

        val result = AnkleAngleCalculator.calculateAnkleAngle(poseFrame, BodySide.RIGHT)

        assertTrue(result.isValid)
        assertEquals(0f, result.angle, delta) // Neutral = 0° plantarflexion
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

        val result = AnkleAngleCalculator.calculateAnkleAngle(poseFrame, BodySide.LEFT)

        assertFalse(result.isValid)
    }

    // ==================== Both Legs Tests ====================

    @Test
    fun `calculate both ankle angles from PoseResult`() {
        val landmarks = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) {
            createLandmark(0f, 0f, 1.0f)
        }

        // Left leg - 90 degree vertex angle = 0° plantarflexion
        landmarks[PoseLandmarkIndex.LEFT_KNEE] = createLandmark(0f, 0f, 1.0f)
        landmarks[PoseLandmarkIndex.LEFT_ANKLE] = createLandmark(0f, 1f, 1.0f)
        landmarks[PoseLandmarkIndex.LEFT_FOOT_INDEX] = createLandmark(1f, 1f, 1.0f)
        landmarks[PoseLandmarkIndex.LEFT_HEEL] = createLandmark(-0.2f, 1f, 1.0f)

        // Right leg - 180 degree vertex angle = 90° plantarflexion (straight)
        landmarks[PoseLandmarkIndex.RIGHT_KNEE] = createLandmark(0.5f, 0f, 1.0f)
        landmarks[PoseLandmarkIndex.RIGHT_ANKLE] = createLandmark(0.5f, 0.5f, 1.0f)
        landmarks[PoseLandmarkIndex.RIGHT_FOOT_INDEX] = createLandmark(0.5f, 1f, 1.0f)
        landmarks[PoseLandmarkIndex.RIGHT_HEEL] = createLandmark(0.5f, 0.4f, 1.0f)

        val poseResult = PoseResult(
            landmarks = landmarks,
            timestampMs = 1000L,
            isValid = true,
            confidence = 0.9f
        )

        val (leftResult, rightResult) = AnkleAngleCalculator.calculateBothAnkleAngles(poseResult)

        assertTrue(leftResult.isValid)
        assertEquals(BodySide.LEFT, leftResult.side)
        assertEquals(0f, leftResult.angle, delta) // Neutral

        assertTrue(rightResult.isValid)
        assertEquals(BodySide.RIGHT, rightResult.side)
        assertEquals(90f, rightResult.angle, delta) // Extreme plantarflexion
    }

    @Test
    fun `calculate both ankle angles from PoseFrame`() {
        val landmarks = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) {
            createLandmark(0f, 0f, 1.0f)
        }

        // Left leg - 90 degree vertex = 0° plantarflexion
        landmarks[PoseLandmarkIndex.LEFT_KNEE] = createLandmark(0f, 0f, 1.0f)
        landmarks[PoseLandmarkIndex.LEFT_ANKLE] = createLandmark(0f, 1f, 1.0f)
        landmarks[PoseLandmarkIndex.LEFT_FOOT_INDEX] = createLandmark(1f, 1f, 1.0f)
        landmarks[PoseLandmarkIndex.LEFT_HEEL] = createLandmark(-0.2f, 1f, 1.0f)

        // Right leg - 180 degree vertex = 90° plantarflexion (straight)
        landmarks[PoseLandmarkIndex.RIGHT_KNEE] = createLandmark(0.5f, 0f, 1.0f)
        landmarks[PoseLandmarkIndex.RIGHT_ANKLE] = createLandmark(0.5f, 0.5f, 1.0f)
        landmarks[PoseLandmarkIndex.RIGHT_FOOT_INDEX] = createLandmark(0.5f, 1f, 1.0f)
        landmarks[PoseLandmarkIndex.RIGHT_HEEL] = createLandmark(0.5f, 0.4f, 1.0f)

        val poseFrame = PoseFrame(
            landmarks = landmarks,
            timestampMs = 1000L,
            frameNumber = 1,
            imageWidth = 1920,
            imageHeight = 1080,
            confidence = 0.9f
        )

        val (leftResult, rightResult) = AnkleAngleCalculator.calculateBothAnkleAngles(poseFrame)

        assertTrue(leftResult.isValid)
        assertEquals(0f, leftResult.angle, delta)

        assertTrue(rightResult.isValid)
        assertEquals(90f, rightResult.angle, delta)
    }

    // ==================== Direct Coordinate Calculation Tests ====================

    @Test
    fun `calculateAnkleAngleFromCoordinates - 90 degrees`() {
        // Vertex angle 90° = 0° plantarflexion (neutral)
        val angle = AnkleAngleCalculator.calculateAnkleAngleFromCoordinates(
            kneeX = 0f, kneeY = 0f,
            ankleX = 0f, ankleY = 1f,
            footIndexX = 1f, footIndexY = 1f
        )

        assertEquals(0f, angle, delta)
    }

    @Test
    fun `calculateAnkleAngleFromCoordinates - 180 degrees`() {
        // Vertex angle 180° = 90° plantarflexion
        val angle = AnkleAngleCalculator.calculateAnkleAngleFromCoordinates(
            kneeX = 0f, kneeY = 0f,
            ankleX = 0.5f, ankleY = 0.5f,
            footIndexX = 1f, footIndexY = 1f
        )

        assertEquals(90f, angle, delta)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `returns invalid for insufficient landmarks`() {
        val landmarks = listOf(createLandmark(0f, 0f, 1.0f)) // Only 1 landmark

        val result = AnkleAngleCalculator.calculateAnkleAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertFalse(result.isValid)
    }

    @Test
    fun `invalid result factory method`() {
        val result = AnkleAngleResult.invalid(BodySide.RIGHT)

        assertFalse(result.isValid)
        assertEquals(BodySide.RIGHT, result.side)
        assertEquals(0f, result.angle, 0.001f)
        assertEquals(0f, result.confidence, 0.001f)
    }

    // ==================== Realistic Cycling Position Tests ====================

    @Test
    fun `realistic cycling position - neutral ankle at BDC`() {
        // Ankle angle at bottom position - neutral position
        // Plantarflexion values near 0° indicate neutral foot position
        val landmarks = createLandmarksWithLeg(
            kneeX = 0.35f, kneeY = 0.6f,
            ankleX = 0.4f, ankleY = 0.9f,
            footIndexX = 0.5f, footIndexY = 0.85f, // Foot slightly forward
            side = BodySide.LEFT
        )

        val result = AnkleAngleCalculator.calculateAnkleAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        // Plantarflexion should be within reasonable cycling range (-30° to +60°)
        assertTrue(result.angle > -30f && result.angle < 60f, 
            "Expected reasonable plantarflexion angle, got ${result.angle}")
    }

    @Test
    fun `realistic cycling position - plantarflexion at BDC`() {
        // Ankle angle at bottom position - plantarflexion (toe pointing down)
        // Typical plantarflexion at BDC: 20-30° (positive values)
        val landmarks = createLandmarksWithLeg(
            kneeX = 0.35f, kneeY = 0.6f,
            ankleX = 0.4f, ankleY = 0.9f,
            footIndexX = 0.45f, footIndexY = 1.0f, // Foot pointing down
            side = BodySide.LEFT
        )

        val result = AnkleAngleCalculator.calculateAnkleAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        // Plantarflexion should be positive (> 0°)
        assertTrue(result.angle > 0f, "Expected positive plantarflexion, got ${result.angle}")
    }

    @Test
    fun `realistic cycling position - dorsiflexion at BDC`() {
        // Ankle angle at bottom position - dorsiflexion (toe pointing up)
        // Dorsiflexion = negative plantarflexion values
        val landmarks = createLandmarksWithLeg(
            kneeX = 0.35f, kneeY = 0.6f,
            ankleX = 0.4f, ankleY = 0.9f,
            footIndexX = 0.55f, footIndexY = 0.8f, // Foot pointing up
            side = BodySide.LEFT
        )

        val result = AnkleAngleCalculator.calculateAnkleAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        // Dorsiflexion should be less than typical plantarflexion (around 20-30°)
        assertTrue(result.angle < 20f, "Expected low or negative angle for dorsiflexion, got ${result.angle}")
    }
}
