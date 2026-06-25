package bikefitstudio.biomechanics

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import bikefitstudio.pose.Landmark
import bikefitstudio.pose.PoseFrame
import bikefitstudio.pose.PoseLandmarkIndex
import bikefitstudio.pose.PoseResult

/**
 * Unit tests for HipAngleCalculator.
 * 
 * Tests verify:
 * - Correct angle calculation with known triangles
 * - Left and right side detection
 * - Visibility threshold handling
 * - Edge cases (missing landmarks, invalid poses)
 */
class HipAngleCalculatorTest {

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
     * Creates a list of 33 landmarks with specified shoulder, hip, and knee landmarks.
     */
    private fun createLandmarksWithHip(
        shoulderX: Float, shoulderY: Float,
        hipX: Float, hipY: Float,
        kneeX: Float, kneeY: Float,
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

        val kneeIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_KNEE
        } else {
            PoseLandmarkIndex.RIGHT_KNEE
        }

        landmarks[shoulderIndex] = createLandmark(shoulderX, shoulderY, visibility)
        landmarks[hipIndex] = createLandmark(hipX, hipY, visibility)
        landmarks[kneeIndex] = createLandmark(kneeX, kneeY, visibility)

        return landmarks
    }

    // ==================== Right Angle Tests (90°) ====================

    @Test
    fun `calculate 90 degree angle - left side`() {
        // Right angle: shoulder at (0,0), hip at (0,1), knee at (1,1)
        val landmarks = createLandmarksWithHip(
            shoulderX = 0f, shoulderY = 0f,
            hipX = 0f, hipY = 1f,
            kneeX = 1f, kneeY = 1f,
            side = BodySide.LEFT
        )

        val result = HipAngleCalculator.calculateHipAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        assertEquals(BodySide.LEFT, result.side)
        assertEquals(90f, result.angle, delta)
    }

    @Test
    fun `calculate 90 degree angle - right side`() {
        val landmarks = createLandmarksWithHip(
            shoulderX = 0f, shoulderY = 0f,
            hipX = 0f, hipY = 1f,
            kneeX = 1f, kneeY = 1f,
            side = BodySide.RIGHT
        )

        val result = HipAngleCalculator.calculateHipAngleFromLandmarks(
            landmarks,
            BodySide.RIGHT
        )

        assertTrue(result.isValid)
        assertEquals(BodySide.RIGHT, result.side)
        assertEquals(90f, result.angle, delta)
    }

    // ==================== Straight Body Tests (0° flexion -> 180° angle) ====================

    @Test
    fun `calculate straight body - straight vertical`() {
        // Straight vertical: shoulder at (0.5, 0), hip at (0.5, 0.5), knee at (0.5, 1)
        // When body is straight, hip angle is 180°
        val landmarks = createLandmarksWithHip(
            shoulderX = 0.5f, shoulderY = 0f,
            hipX = 0.5f, hipY = 0.5f,
            kneeX = 0.5f, kneeY = 1f,
            side = BodySide.LEFT
        )

        val result = HipAngleCalculator.calculateHipAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        assertEquals(180f, result.angle, delta)
    }

    @Test
    fun `calculate straight body - straight diagonal`() {
        // Straight diagonal: all points in a line
        // When body is straight, hip angle is 180°
        val landmarks = createLandmarksWithHip(
            shoulderX = 0f, shoulderY = 0f,
            hipX = 0.5f, hipY = 0.5f,
            kneeX = 1f, kneeY = 1f,
            side = BodySide.LEFT
        )

        val result = HipAngleCalculator.calculateHipAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        assertEquals(180f, result.angle, delta)
    }

    // ==================== Other Angle Tests ====================

    @Test
    fun `calculate 60 degree vertex angle`() {
        // Vertex angle is 60°
        // Hip Angle should be 60° (Closed / Flexed)
        val angle = HipAngleCalculator.calculateHipAngleFromCoordinates(
            shoulderX = 0f, shoulderY = 0f,
            hipX = 1f, hipY = 0f,
            kneeX = 0.5f, kneeY = 0.866f
        )

        assertEquals(60f, angle, 1f)
    }

    @Test
    fun `calculate 120 degree vertex angle`() {
        // Vertex angle is 120°
        // Hip Angle should be 120° (Open)
        val angle = HipAngleCalculator.calculateHipAngleFromCoordinates(
            shoulderX = 0f, shoulderY = 0f,
            hipX = 1f, hipY = 0f,
            kneeX = 1.5f, kneeY = 0.866f
        )

        assertEquals(120f, angle, 1f)
    }

    @Test
    fun `calculate closed hip angle`() {
        // Vertex angle is 60°
        val angle = HipAngleCalculator.calculateHipAngleFromCoordinates(
            shoulderX = 0f, shoulderY = 0f,
            hipX = 1f, hipY = 0f,
            kneeX = 0.5f, kneeY = 0.866f
        )

        // Hip angle should be 60° (Acute)
        assertTrue(angle < 90f)
    }

    // ==================== Visibility Tests ====================

    @Test
    fun `returns invalid when shoulder not visible`() {
        val landmarks = createLandmarksWithHip(
            shoulderX = 0f, shoulderY = 0f,
            hipX = 0f, hipY = 1f,
            kneeX = 1f, kneeY = 1f,
            side = BodySide.LEFT,
            visibility = 1.0f
        ).toMutableList()

        // Set shoulder visibility to low
        landmarks[PoseLandmarkIndex.LEFT_SHOULDER] = createLandmark(0f, 0f, 0.3f)

        val result = HipAngleCalculator.calculateHipAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertFalse(result.isValid)
        assertEquals(0f, result.angle, 0.001f)
    }

    @Test
    fun `returns invalid when hip not visible`() {
        val landmarks = createLandmarksWithHip(
            shoulderX = 0f, shoulderY = 0f,
            hipX = 0f, hipY = 1f,
            kneeX = 1f, kneeY = 1f,
            side = BodySide.LEFT,
            visibility = 1.0f
        ).toMutableList()

        landmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0f, 1f, 0.3f)

        val result = HipAngleCalculator.calculateHipAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertFalse(result.isValid)
    }

    @Test
    fun `returns invalid when knee not visible`() {
        val landmarks = createLandmarksWithHip(
            shoulderX = 0f, shoulderY = 0f,
            hipX = 0f, hipY = 1f,
            kneeX = 1f, kneeY = 1f,
            side = BodySide.LEFT,
            visibility = 1.0f
        ).toMutableList()

        landmarks[PoseLandmarkIndex.LEFT_KNEE] = createLandmark(1f, 1f, 0.2f)

        val result = HipAngleCalculator.calculateHipAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertFalse(result.isValid)
    }

    @Test
    fun `custom visibility threshold`() {
        val landmarks = createLandmarksWithHip(
            shoulderX = 0f, shoulderY = 0f,
            hipX = 0f, hipY = 1f,
            kneeX = 1f, kneeY = 1f,
            side = BodySide.LEFT,
            visibility = 0.4f // Below default threshold of 0.5
        )

        // With default threshold, should be invalid
        val resultDefault = HipAngleCalculator.calculateHipAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )
        assertFalse(resultDefault.isValid)

        // With lower threshold, should be valid
        val resultLower = HipAngleCalculator.calculateHipAngleFromLandmarks(
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
        val landmarks = createLandmarksWithHip(
            shoulderX = 0f, shoulderY = 0f,
            hipX = 0f, hipY = 1f,
            kneeX = 1f, kneeY = 1f,
            side = BodySide.LEFT
        ).toMutableList()

        // Set different visibilities
        landmarks[PoseLandmarkIndex.LEFT_SHOULDER] = createLandmark(0f, 0f, 0.9f)
        landmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0f, 1f, 0.8f)
        landmarks[PoseLandmarkIndex.LEFT_KNEE] = createLandmark(1f, 1f, 0.7f)

        val result = HipAngleCalculator.calculateHipAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        assertEquals(0.8f, result.confidence, 0.01f) // (0.9 + 0.8 + 0.7) / 3 = 0.8
    }

    // ==================== PoseResult Tests ====================

    @Test
    fun `calculate from PoseResult`() {
        val landmarks = createLandmarksWithHip(
            shoulderX = 0f, shoulderY = 0f,
            hipX = 0f, hipY = 1f,
            kneeX = 1f, kneeY = 1f,
            side = BodySide.LEFT
        )

        val poseResult = PoseResult(
            landmarks = landmarks,
            timestampMs = 1000L,
            isValid = true,
            confidence = 0.9f
        )

        val result = HipAngleCalculator.calculateHipAngle(poseResult, BodySide.LEFT)

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

        val result = HipAngleCalculator.calculateHipAngle(poseResult, BodySide.LEFT)

        assertFalse(result.isValid)
    }

    // ==================== PoseFrame Tests ====================

    @Test
    fun `calculate from PoseFrame`() {
        val landmarks = createLandmarksWithHip(
            shoulderX = 0f, shoulderY = 0f,
            hipX = 0f, hipY = 1f,
            kneeX = 1f, kneeY = 1f,
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

        val result = HipAngleCalculator.calculateHipAngle(poseFrame, BodySide.RIGHT)

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

        val result = HipAngleCalculator.calculateHipAngle(poseFrame, BodySide.LEFT)

        assertFalse(result.isValid)
    }

    // ==================== Both Sides Tests ====================

    @Test
    fun `calculate both hip angles from PoseResult`() {
        val landmarks = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) {
            createLandmark(0f, 0f, 1.0f)
        }

        // Left side - 90 degree vertex angle → 90° hip flexion
        landmarks[PoseLandmarkIndex.LEFT_SHOULDER] = createLandmark(0f, 0f, 1.0f)
        landmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0f, 1f, 1.0f)
        landmarks[PoseLandmarkIndex.LEFT_KNEE] = createLandmark(1f, 1f, 1.0f)

        // Right side - 180 degree vertex angle (straight) → 0° hip flexion
        landmarks[PoseLandmarkIndex.RIGHT_SHOULDER] = createLandmark(0.5f, 0f, 1.0f)
        landmarks[PoseLandmarkIndex.RIGHT_HIP] = createLandmark(0.5f, 0.5f, 1.0f)
        landmarks[PoseLandmarkIndex.RIGHT_KNEE] = createLandmark(0.5f, 1f, 1.0f)

        val poseResult = PoseResult(
            landmarks = landmarks,
            timestampMs = 1000L,
            isValid = true,
            confidence = 0.9f
        )

        val (leftResult, rightResult) = HipAngleCalculator.calculateBothHipAngles(poseResult)

        assertTrue(leftResult.isValid)
        assertEquals(BodySide.LEFT, leftResult.side)
        assertEquals(90f, leftResult.angle, delta)

        assertTrue(rightResult.isValid)
        assertEquals(BodySide.RIGHT, rightResult.side)
        assertEquals(180f, rightResult.angle, delta)  // Straight body = 180°
    }

    @Test
    fun `calculate both hip angles from PoseFrame`() {
        val landmarks = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) {
            createLandmark(0f, 0f, 1.0f)
        }

        // Left side - 90 degree vertex angle → 90° hip angle
        landmarks[PoseLandmarkIndex.LEFT_SHOULDER] = createLandmark(0f, 0f, 1.0f)
        landmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0f, 1f, 1.0f)
        landmarks[PoseLandmarkIndex.LEFT_KNEE] = createLandmark(1f, 1f, 1.0f)

        // Right side - 180 degree vertex angle (straight) → 180° hip angle
        landmarks[PoseLandmarkIndex.RIGHT_SHOULDER] = createLandmark(0.5f, 0f, 1.0f)
        landmarks[PoseLandmarkIndex.RIGHT_HIP] = createLandmark(0.5f, 0.5f, 1.0f)
        landmarks[PoseLandmarkIndex.RIGHT_KNEE] = createLandmark(0.5f, 1f, 1.0f)

        val poseFrame = PoseFrame(
            landmarks = landmarks,
            timestampMs = 1000L,
            frameNumber = 1,
            imageWidth = 1920,
            imageHeight = 1080,
            confidence = 0.9f
        )

        val (leftResult, rightResult) = HipAngleCalculator.calculateBothHipAngles(poseFrame)

        assertTrue(leftResult.isValid)
        assertEquals(90f, leftResult.angle, delta)

        assertTrue(rightResult.isValid)
        assertEquals(180f, rightResult.angle, delta)  // Straight body = 180°
    }

    // ==================== Direct Coordinate Calculation Tests ====================

    @Test
    fun `calculateHipAngleFromCoordinates - 90 degrees`() {
        val angle = HipAngleCalculator.calculateHipAngleFromCoordinates(
            shoulderX = 0f, shoulderY = 0f,
            hipX = 0f, hipY = 1f,
            kneeX = 1f, kneeY = 1f
        )

        assertEquals(90f, angle, delta)
    }

    @Test
    fun `calculateHipAngleFromCoordinates - 180 degrees (straight body)`() {
        // Straight body = 180° hip angle
        val angle = HipAngleCalculator.calculateHipAngleFromCoordinates(
            shoulderX = 0f, shoulderY = 0f,
            hipX = 0.5f, hipY = 0.5f,
            kneeX = 1f, kneeY = 1f
        )

        assertEquals(180f, angle, delta)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `returns invalid for insufficient landmarks`() {
        val landmarks = listOf(createLandmark(0f, 0f, 1.0f)) // Only 1 landmark

        val result = HipAngleCalculator.calculateHipAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertFalse(result.isValid)
    }

    @Test
    fun `invalid result factory method`() {
        val result = HipAngleResult.invalid(BodySide.RIGHT)

        assertFalse(result.isValid)
        assertEquals(BodySide.RIGHT, result.side)
        assertEquals(0f, result.angle, 0.001f)
        assertEquals(0f, result.confidence, 0.001f)
    }

    // ==================== Realistic Cycling Position Tests ====================

    @Test
    fun `realistic cycling position - relaxed upright`() {
        // Upright position with smaller hip flexion angle (more open hip)
        val landmarks = createLandmarksWithHip(
            shoulderX = 0.4f, shoulderY = 0.2f,  // Shoulder above and slightly back
            hipX = 0.5f, hipY = 0.5f,            // Hip
            kneeX = 0.5f, kneeY = 0.8f,          // Knee below
            side = BodySide.LEFT
        )

        val result = HipAngleCalculator.calculateHipAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        // Upright position should have wider hip angle (closer to 180)
        assertTrue(result.angle > 90f)
    }

    @Test
    fun `realistic cycling position - aero tuck`() {
        // Aggressive aero position with smaller hip angle
        val landmarks = createLandmarksWithHip(
            shoulderX = 0.6f, shoulderY = 0.35f, // Shoulder forward (aero)
            hipX = 0.5f, hipY = 0.5f,            // Hip
            kneeX = 0.45f, kneeY = 0.8f,         // Knee below
            side = BodySide.LEFT
        )

        val result = HipAngleCalculator.calculateHipAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )

        assertTrue(result.isValid)
        // Just verify it calculates a valid angle
        assertTrue(result.angle > 0f && result.angle <= 180f)
    }

    // ==================== Midpoint-Based Calculation Tests ====================

    /**
     * Creates landmarks with both left and right sides at specified positions.
     */
    private fun createLandmarksWithBothSides(
        leftShoulderX: Float, leftShoulderY: Float,
        rightShoulderX: Float, rightShoulderY: Float,
        leftHipX: Float, leftHipY: Float,
        rightHipX: Float, rightHipY: Float,
        leftKneeX: Float, leftKneeY: Float,
        rightKneeX: Float, rightKneeY: Float,
        visibility: Float = 1.0f
    ): List<Landmark> {
        val landmarks = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) {
            createLandmark(0f, 0f, 1.0f)
        }

        landmarks[PoseLandmarkIndex.LEFT_SHOULDER] = createLandmark(leftShoulderX, leftShoulderY, visibility)
        landmarks[PoseLandmarkIndex.RIGHT_SHOULDER] = createLandmark(rightShoulderX, rightShoulderY, visibility)
        landmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(leftHipX, leftHipY, visibility)
        landmarks[PoseLandmarkIndex.RIGHT_HIP] = createLandmark(rightHipX, rightHipY, visibility)
        landmarks[PoseLandmarkIndex.LEFT_KNEE] = createLandmark(leftKneeX, leftKneeY, visibility)
        landmarks[PoseLandmarkIndex.RIGHT_KNEE] = createLandmark(rightKneeX, rightKneeY, visibility)

        return landmarks
    }

    @Test
    fun `calculate hip angle from midpoints - 90 degrees`() {
        // Create symmetrical pose with 90 degree hip angle
        val landmarks = createLandmarksWithBothSides(
            leftShoulderX = 0.45f, leftShoulderY = 0f,
            rightShoulderX = 0.55f, rightShoulderY = 0f,
            leftHipX = 0.45f, leftHipY = 1f,
            rightHipX = 0.55f, rightHipY = 1f,
            leftKneeX = 1.45f, leftKneeY = 1f,
            rightKneeX = 1.55f, rightKneeY = 1f
        )

        val result = HipAngleCalculator.calculateHipAngleFromMidpoints(landmarks)

        assertTrue(result.isValid)
        assertEquals(90f, result.angle, delta)
        assertEquals(BodySide.LEFT, result.side) // Convention for midpoint calculations
    }

    @Test
    fun `calculate hip angle from midpoints - 0 degrees flexion straight`() {
        // Create straight vertical pose - 0° hip flexion
        val landmarks = createLandmarksWithBothSides(
            leftShoulderX = 0.45f, leftShoulderY = 0f,
            rightShoulderX = 0.55f, rightShoulderY = 0f,
            leftHipX = 0.45f, leftHipY = 0.5f,
            rightHipX = 0.55f, rightHipY = 0.5f,
            leftKneeX = 0.45f, leftKneeY = 1f,
            rightKneeX = 0.55f, rightKneeY = 1f
        )

        val result = HipAngleCalculator.calculateHipAngleFromMidpoints(landmarks)

        assertTrue(result.isValid)
        assertEquals(0f, result.angle, delta)
    }

    @Test
    fun `calculate hip angle from midpoints with low visibility returns invalid`() {
        val landmarks = createLandmarksWithBothSides(
            leftShoulderX = 0.45f, leftShoulderY = 0f,
            rightShoulderX = 0.55f, rightShoulderY = 0f,
            leftHipX = 0.45f, leftHipY = 1f,
            rightHipX = 0.55f, rightHipY = 1f,
            leftKneeX = 1.45f, leftKneeY = 1f,
            rightKneeX = 1.55f, rightKneeY = 1f,
            visibility = 0.3f // Below default threshold
        )

        val result = HipAngleCalculator.calculateHipAngleFromMidpoints(landmarks)

        assertFalse(result.isValid)
    }

    @Test
    fun `calculate hip angle from midpoints with custom visibility threshold`() {
        val landmarks = createLandmarksWithBothSides(
            leftShoulderX = 0.45f, leftShoulderY = 0f,
            rightShoulderX = 0.55f, rightShoulderY = 0f,
            leftHipX = 0.45f, leftHipY = 1f,
            rightHipX = 0.55f, rightHipY = 1f,
            leftKneeX = 1.45f, leftKneeY = 1f,
            rightKneeX = 1.55f, rightKneeY = 1f,
            visibility = 0.4f
        )

        // With default threshold (0.5), should be invalid
        val resultDefault = HipAngleCalculator.calculateHipAngleFromMidpoints(landmarks)
        assertFalse(resultDefault.isValid)

        // With lower threshold (0.3), should be valid
        val resultLower = HipAngleCalculator.calculateHipAngleFromMidpoints(
            landmarks,
            visibilityThreshold = 0.3f
        )
        assertTrue(resultLower.isValid)
        assertEquals(90f, resultLower.angle, delta)
    }

    @Test
    fun `calculate hip angle from midpoints averages confidence from all landmarks`() {
        val landmarks = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) {
            createLandmark(0f, 0f, 1.0f)
        }

        // Set different visibilities for all 6 landmarks
        landmarks[PoseLandmarkIndex.LEFT_SHOULDER] = createLandmark(0.45f, 0f, 0.9f)
        landmarks[PoseLandmarkIndex.RIGHT_SHOULDER] = createLandmark(0.55f, 0f, 0.8f)
        landmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0.45f, 1f, 0.85f)
        landmarks[PoseLandmarkIndex.RIGHT_HIP] = createLandmark(0.55f, 1f, 0.75f)
        landmarks[PoseLandmarkIndex.LEFT_KNEE] = createLandmark(1.45f, 1f, 0.95f)
        landmarks[PoseLandmarkIndex.RIGHT_KNEE] = createLandmark(1.55f, 1f, 0.7f)

        val result = HipAngleCalculator.calculateHipAngleFromMidpoints(landmarks)

        assertTrue(result.isValid)
        // Average should be (0.9 + 0.8 + 0.85 + 0.75 + 0.95 + 0.7) / 6 = 0.825
        assertEquals(0.825f, result.confidence, 0.01f)
    }

    @Test
    fun `calculate hip angle from midpoints - PoseResult`() {
        val landmarks = createLandmarksWithBothSides(
            leftShoulderX = 0.45f, leftShoulderY = 0f,
            rightShoulderX = 0.55f, rightShoulderY = 0f,
            leftHipX = 0.45f, leftHipY = 1f,
            rightHipX = 0.55f, rightHipY = 1f,
            leftKneeX = 1.45f, leftKneeY = 1f,
            rightKneeX = 1.55f, rightKneeY = 1f
        )

        val poseResult = PoseResult(
            landmarks = landmarks,
            timestampMs = 1000L,
            isValid = true,
            confidence = 0.9f
        )

        val result = HipAngleCalculator.calculateHipAngleFromMidpoints(poseResult)

        assertTrue(result.isValid)
        assertEquals(90f, result.angle, delta)
    }

    @Test
    fun `calculate hip angle from midpoints - PoseFrame`() {
        val landmarks = createLandmarksWithBothSides(
            leftShoulderX = 0.45f, leftShoulderY = 0f,
            rightShoulderX = 0.55f, rightShoulderY = 0f,
            leftHipX = 0.45f, leftHipY = 1f,
            rightHipX = 0.55f, rightHipY = 1f,
            leftKneeX = 1.45f, leftKneeY = 1f,
            rightKneeX = 1.55f, rightKneeY = 1f
        )

        val poseFrame = PoseFrame(
            landmarks = landmarks,
            timestampMs = 1000L,
            frameNumber = 1,
            imageWidth = 1920,
            imageHeight = 1080,
            confidence = 0.9f
        )

        val result = HipAngleCalculator.calculateHipAngleFromMidpoints(poseFrame)

        assertTrue(result.isValid)
        assertEquals(90f, result.angle, delta)
    }

    @Test
    fun `calculate hip angle from midpoints returns invalid for invalid PoseResult`() {
        val poseResult = PoseResult(
            landmarks = emptyList(),
            timestampMs = 1000L,
            isValid = false,
            confidence = 0f
        )

        val result = HipAngleCalculator.calculateHipAngleFromMidpoints(poseResult)

        assertFalse(result.isValid)
    }

    @Test
    fun `calculate hip angle from midpoints returns invalid for empty PoseFrame`() {
        val poseFrame = PoseFrame(
            landmarks = emptyList(),
            timestampMs = 1000L,
            frameNumber = 1,
            imageWidth = 1920,
            imageHeight = 1080,
            confidence = 0f
        )

        val result = HipAngleCalculator.calculateHipAngleFromMidpoints(poseFrame)

        assertFalse(result.isValid)
    }

    @Test
    fun `calculate hip angle from midpoints returns invalid for insufficient landmarks`() {
        val landmarks = listOf(createLandmark(0f, 0f, 1.0f)) // Only 1 landmark

        val result = HipAngleCalculator.calculateHipAngleFromMidpoints(landmarks)

        assertFalse(result.isValid)
    }

    @Test
    fun `calculate hip angle from midpoints with asymmetric pose`() {
        // Asymmetric pose (e.g., side view where one side is closer to camera)
        // Left side slightly forward, right side slightly back
        val landmarks = createLandmarksWithBothSides(
            leftShoulderX = 0.4f, leftShoulderY = 0f,
            rightShoulderX = 0.6f, rightShoulderY = 0f,
            leftHipX = 0.4f, leftHipY = 1f,
            rightHipX = 0.6f, rightHipY = 1f,
            leftKneeX = 1.4f, leftKneeY = 1f,
            rightKneeX = 1.6f, rightKneeY = 1f
        )

        val result = HipAngleCalculator.calculateHipAngleFromMidpoints(landmarks)

        assertTrue(result.isValid)
        // Midpoint should still give ~90 degrees
        assertEquals(90f, result.angle, delta)
    }
}
