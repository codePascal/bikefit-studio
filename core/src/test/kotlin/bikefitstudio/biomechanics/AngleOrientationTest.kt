package bikefitstudio.biomechanics

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import bikefitstudio.pose.Landmark
import bikefitstudio.pose.PoseLandmarkIndex

/**
 * Unit tests to verify that angle calculations work correctly for both
 * left-facing and right-facing cyclists.
 * 
 * A left-facing cyclist has their left side visible to the camera (handlebars on left).
 * A right-facing cyclist has their right side visible to the camera (handlebars on right).
 * 
 * The key insight is that for geometric angle calculations (knee, hip, ankle),
 * the angle at a vertex should be the same regardless of orientation.
 * 
 * For torso angle (angle to horizontal), the angle from horizontal should be
 * the same whether the torso leans forward-left or forward-right.
 */
class AngleOrientationTest {

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
     * Creates a full set of 33 landmarks with default positions.
     */
    private fun createEmptyLandmarks(): MutableList<Landmark> {
        return MutableList(PoseLandmarkIndex.LANDMARK_COUNT) {
            createLandmark(0f, 0f, 1.0f)
        }
    }

    // ================================================================================
    // KNEE ANGLE ORIENTATION TESTS
    // ================================================================================

    /**
     * When knee is at 90° for a LEFT-facing cyclist (landmarks on left side of body):
     * For a 90° angle at vertex B, vectors BA and BC must be perpendicular.
     * 
     * Using simple perpendicular vectors:
     * Hip at (0.3, 0.4), Knee at (0.4, 0.5), Ankle at (0.5, 0.4)
     * Vector knee→hip = (-0.1, -0.1), Vector knee→ankle = (0.1, -0.1)
     * These are perpendicular (dot product = -0.01 + 0.01 = 0)
     */
    @Test
    fun `knee angle 90 degrees - left facing cyclist`() {
        val landmarks = createEmptyLandmarks()
        
        // Left-facing cyclist: 90° at knee
        landmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0.3f, 0.4f)
        landmarks[PoseLandmarkIndex.LEFT_KNEE] = createLandmark(0.4f, 0.5f)
        landmarks[PoseLandmarkIndex.LEFT_ANKLE] = createLandmark(0.5f, 0.4f)
        
        val result = KneeAngleCalculator.calculateKneeAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )
        
        assertTrue(result.isValid)
        assertEquals(90f, result.angle, 2f)
    }

    /**
     * When knee is at 90° for a RIGHT-facing cyclist (landmarks on right side of body):
     * Mirror of left-facing test.
     * 
     * Hip at (0.7, 0.4), Knee at (0.6, 0.5), Ankle at (0.5, 0.4)
     */
    @Test
    fun `knee angle 90 degrees - right facing cyclist`() {
        val landmarks = createEmptyLandmarks()
        
        // Right-facing cyclist: mirror of left-facing
        landmarks[PoseLandmarkIndex.RIGHT_HIP] = createLandmark(0.7f, 0.4f)
        landmarks[PoseLandmarkIndex.RIGHT_KNEE] = createLandmark(0.6f, 0.5f)
        landmarks[PoseLandmarkIndex.RIGHT_ANKLE] = createLandmark(0.5f, 0.4f)
        
        val result = KneeAngleCalculator.calculateKneeAngleFromLandmarks(
            landmarks,
            BodySide.RIGHT
        )
        
        assertTrue(result.isValid)
        assertEquals(90f, result.angle, 2f)
    }

    /**
     * Both orientations should produce identical knee angles for equivalent poses.
     * Testing with a 120° knee angle (moderately bent).
     */
    @Test
    fun `knee angle is same for left and right facing at 120 degrees`() {
        // Using coordinates that create a 120° angle at the knee
        val leftLandmarks = createEmptyLandmarks()
        leftLandmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0.3f, 0.3f)
        leftLandmarks[PoseLandmarkIndex.LEFT_KNEE] = createLandmark(0.4f, 0.5f)
        leftLandmarks[PoseLandmarkIndex.LEFT_ANKLE] = createLandmark(0.4f, 0.7f)  // Below knee
        
        val rightLandmarks = createEmptyLandmarks()
        // Mirror coordinates: x' = 1.0 - x
        rightLandmarks[PoseLandmarkIndex.RIGHT_HIP] = createLandmark(0.7f, 0.3f)
        rightLandmarks[PoseLandmarkIndex.RIGHT_KNEE] = createLandmark(0.6f, 0.5f)
        rightLandmarks[PoseLandmarkIndex.RIGHT_ANKLE] = createLandmark(0.6f, 0.7f)
        
        val leftResult = KneeAngleCalculator.calculateKneeAngleFromLandmarks(
            leftLandmarks,
            BodySide.LEFT
        )
        
        val rightResult = KneeAngleCalculator.calculateKneeAngleFromLandmarks(
            rightLandmarks,
            BodySide.RIGHT
        )
        
        assertTrue(leftResult.isValid)
        assertTrue(rightResult.isValid)
        assertEquals(leftResult.angle, rightResult.angle, 1f)
    }

    /**
     * Maximum knee extension (approximately 150-165° for BDC) should work for both orientations.
     */
    @Test
    fun `knee angle at BDC - both orientations produce similar angles`() {
        // Simulating bottom dead center position - leg almost straight
        val leftLandmarks = createEmptyLandmarks()
        leftLandmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0.35f, 0.35f)
        leftLandmarks[PoseLandmarkIndex.LEFT_KNEE] = createLandmark(0.38f, 0.55f)
        leftLandmarks[PoseLandmarkIndex.LEFT_ANKLE] = createLandmark(0.40f, 0.80f)
        
        val rightLandmarks = createEmptyLandmarks()
        rightLandmarks[PoseLandmarkIndex.RIGHT_HIP] = createLandmark(0.65f, 0.35f)
        rightLandmarks[PoseLandmarkIndex.RIGHT_KNEE] = createLandmark(0.62f, 0.55f)
        rightLandmarks[PoseLandmarkIndex.RIGHT_ANKLE] = createLandmark(0.60f, 0.80f)
        
        val leftResult = KneeAngleCalculator.calculateKneeAngleFromLandmarks(
            leftLandmarks,
            BodySide.LEFT
        )
        
        val rightResult = KneeAngleCalculator.calculateKneeAngleFromLandmarks(
            rightLandmarks,
            BodySide.RIGHT
        )
        
        assertTrue(leftResult.isValid)
        assertTrue(rightResult.isValid)
        // Both should produce similar angles
        assertTrue(leftResult.angle > 140f, "Left knee at BDC should be > 140°, was ${leftResult.angle}")
        assertTrue(rightResult.angle > 140f, "Right knee at BDC should be > 140°, was ${rightResult.angle}")
        assertEquals(leftResult.angle, rightResult.angle, 1f)
    }

    // ================================================================================
    // HIP ANGLE ORIENTATION TESTS
    // 
    // Hip angle is calculated as (180° - vertex angle at hip).
    // A 90° vertex angle → 90° hip flexion angle.
    // A 120° vertex angle → 60° hip flexion angle.
    // A 160° vertex angle → 20° hip flexion angle.
    // ================================================================================

    @Test
    fun `hip angle - left facing cyclist - valid result`() {
        val landmarks = createEmptyLandmarks()
        
        // Left-facing: shoulder forward-left, hip below shoulder, knee below
        landmarks[PoseLandmarkIndex.LEFT_SHOULDER] = createLandmark(0.25f, 0.3f)
        landmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0.35f, 0.5f)
        landmarks[PoseLandmarkIndex.LEFT_KNEE] = createLandmark(0.40f, 0.7f)
        
        val result = HipAngleCalculator.calculateHipAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )
        
        assertTrue(result.isValid)
        // Just verify it produces a valid angle
        assertTrue(result.angle >= 0f && result.angle <= 180f,
            "Hip angle ${result.angle}° should be in valid range 0-180")
    }

    @Test
    fun `hip angle - right facing cyclist - valid result`() {
        val landmarks = createEmptyLandmarks()
        
        // Right-facing: mirror of left-facing
        landmarks[PoseLandmarkIndex.RIGHT_SHOULDER] = createLandmark(0.75f, 0.3f)
        landmarks[PoseLandmarkIndex.RIGHT_HIP] = createLandmark(0.65f, 0.5f)
        landmarks[PoseLandmarkIndex.RIGHT_KNEE] = createLandmark(0.60f, 0.7f)
        
        val result = HipAngleCalculator.calculateHipAngleFromLandmarks(
            landmarks,
            BodySide.RIGHT
        )
        
        assertTrue(result.isValid)
        // Same expected range
        assertTrue(result.angle >= 0f && result.angle <= 180f,
            "Hip angle ${result.angle}° should be in valid range 0-180")
    }

    @Test
    fun `hip angle is same for left and right facing at typical road position`() {
        // Typical road bike position: ~50° hip angle
        val leftLandmarks = createEmptyLandmarks()
        leftLandmarks[PoseLandmarkIndex.LEFT_SHOULDER] = createLandmark(0.25f, 0.25f)
        leftLandmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0.35f, 0.45f)
        leftLandmarks[PoseLandmarkIndex.LEFT_KNEE] = createLandmark(0.4f, 0.7f)
        
        val rightLandmarks = createEmptyLandmarks()
        // Mirror: x' = 1.0 - x
        rightLandmarks[PoseLandmarkIndex.RIGHT_SHOULDER] = createLandmark(0.75f, 0.25f)
        rightLandmarks[PoseLandmarkIndex.RIGHT_HIP] = createLandmark(0.65f, 0.45f)
        rightLandmarks[PoseLandmarkIndex.RIGHT_KNEE] = createLandmark(0.6f, 0.7f)
        
        val leftResult = HipAngleCalculator.calculateHipAngleFromLandmarks(
            leftLandmarks,
            BodySide.LEFT
        )
        
        val rightResult = HipAngleCalculator.calculateHipAngleFromLandmarks(
            rightLandmarks,
            BodySide.RIGHT
        )
        
        assertTrue(leftResult.isValid)
        assertTrue(rightResult.isValid)
        assertEquals(leftResult.angle, rightResult.angle, delta)
    }

    // ================================================================================
    // TORSO ANGLE ORIENTATION TESTS
    // ================================================================================

    @Test
    fun `torso angle - left facing cyclist leaning forward 45 degrees`() {
        val landmarks = createEmptyLandmarks()
        
        // Left-facing cyclist: shoulder to the left (lower X) and above hip
        // 45° lean means equal horizontal and vertical displacement
        landmarks[PoseLandmarkIndex.LEFT_SHOULDER] = createLandmark(0.3f, 0.3f)
        landmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0.4f, 0.4f)
        
        val result = TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )
        
        assertTrue(result.isValid)
        assertEquals(45f, result.angle, 2f)
    }

    @Test
    fun `torso angle - right facing cyclist leaning forward 45 degrees`() {
        val landmarks = createEmptyLandmarks()
        
        // Right-facing cyclist: shoulder to the right (higher X) and above hip
        // Same 45° lean
        landmarks[PoseLandmarkIndex.RIGHT_SHOULDER] = createLandmark(0.7f, 0.3f)
        landmarks[PoseLandmarkIndex.RIGHT_HIP] = createLandmark(0.6f, 0.4f)
        
        val result = TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(
            landmarks,
            BodySide.RIGHT
        )
        
        assertTrue(result.isValid)
        assertEquals(45f, result.angle, 2f)
    }

    @Test
    fun `torso angle is same for left and right facing at 60 degrees`() {
        // Upright position (60° from horizontal)
        val leftLandmarks = createEmptyLandmarks()
        // tan(60°) ≈ 1.732, so vertical should be ~1.732x horizontal
        leftLandmarks[PoseLandmarkIndex.LEFT_SHOULDER] = createLandmark(0.4f, 0.227f)
        leftLandmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0.5f, 0.4f)
        
        val rightLandmarks = createEmptyLandmarks()
        rightLandmarks[PoseLandmarkIndex.RIGHT_SHOULDER] = createLandmark(0.6f, 0.227f)
        rightLandmarks[PoseLandmarkIndex.RIGHT_HIP] = createLandmark(0.5f, 0.4f)
        
        val leftResult = TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(
            leftLandmarks,
            BodySide.LEFT
        )
        
        val rightResult = TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(
            rightLandmarks,
            BodySide.RIGHT
        )
        
        assertTrue(leftResult.isValid)
        assertTrue(rightResult.isValid)
        assertEquals(leftResult.angle, rightResult.angle, 2f)
    }

    @Test
    fun `torso angle near vertical - left facing`() {
        val landmarks = createEmptyLandmarks()
        
        // Nearly vertical torso (85-90°)
        landmarks[PoseLandmarkIndex.LEFT_SHOULDER] = createLandmark(0.5f, 0.2f)
        landmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0.5f, 0.5f)
        
        val result = TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )
        
        assertTrue(result.isValid)
        assertEquals(90f, result.angle, 2f)
    }

    @Test
    fun `torso angle near vertical - right facing`() {
        val landmarks = createEmptyLandmarks()
        
        // Nearly vertical torso (85-90°)
        landmarks[PoseLandmarkIndex.RIGHT_SHOULDER] = createLandmark(0.5f, 0.2f)
        landmarks[PoseLandmarkIndex.RIGHT_HIP] = createLandmark(0.5f, 0.5f)
        
        val result = TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(
            landmarks,
            BodySide.RIGHT
        )
        
        assertTrue(result.isValid)
        assertEquals(90f, result.angle, 2f)
    }

    // ================================================================================
    // ANKLE ANGLE ORIENTATION TESTS
    // ================================================================================

    @Test
    fun `ankle angle neutral - left facing cyclist`() {
        val landmarks = createEmptyLandmarks()
        
        // Left-facing cyclist with neutral ankle (90° at ankle joint)
        landmarks[PoseLandmarkIndex.LEFT_KNEE] = createLandmark(0.35f, 0.5f)
        landmarks[PoseLandmarkIndex.LEFT_ANKLE] = createLandmark(0.4f, 0.8f)
        landmarks[PoseLandmarkIndex.LEFT_HEEL] = createLandmark(0.38f, 0.82f)
        landmarks[PoseLandmarkIndex.LEFT_FOOT_INDEX] = createLandmark(0.5f, 0.8f)
        
        val result = AnkleAngleCalculator.calculateAnkleAngleFromLandmarks(
            landmarks,
            BodySide.LEFT
        )
        
        assertTrue(result.isValid)
        // Near-neutral ankle should be close to 0° plantarflexion
        assertTrue(result.angle > -20f && result.angle < 30f,
            "Ankle angle ${result.angle}° should be near neutral")
    }

    @Test
    fun `ankle angle neutral - right facing cyclist`() {
        val landmarks = createEmptyLandmarks()
        
        // Right-facing cyclist with neutral ankle
        landmarks[PoseLandmarkIndex.RIGHT_KNEE] = createLandmark(0.65f, 0.5f)
        landmarks[PoseLandmarkIndex.RIGHT_ANKLE] = createLandmark(0.6f, 0.8f)
        landmarks[PoseLandmarkIndex.RIGHT_HEEL] = createLandmark(0.62f, 0.82f)
        landmarks[PoseLandmarkIndex.RIGHT_FOOT_INDEX] = createLandmark(0.5f, 0.8f)
        
        val result = AnkleAngleCalculator.calculateAnkleAngleFromLandmarks(
            landmarks,
            BodySide.RIGHT
        )
        
        assertTrue(result.isValid)
        // Near-neutral ankle should be close to 0° plantarflexion
        assertTrue(result.angle > -20f && result.angle < 30f,
            "Ankle angle ${result.angle}° should be near neutral")
    }

    // ================================================================================
    // FULL CYCLING POSE TESTS
    // ================================================================================

    /**
     * Tests a complete cycling pose for a left-facing cyclist.
     * Verifies all angles are within expected ranges.
     */
    @Test
    fun `complete left-facing cycling pose - all angles valid`() {
        val landmarks = createEmptyLandmarks()
        
        // Realistic left-facing cyclist
        landmarks[PoseLandmarkIndex.LEFT_SHOULDER] = createLandmark(0.30f, 0.30f)
        landmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0.40f, 0.50f)
        landmarks[PoseLandmarkIndex.LEFT_KNEE] = createLandmark(0.45f, 0.65f)
        landmarks[PoseLandmarkIndex.LEFT_ANKLE] = createLandmark(0.48f, 0.85f)
        landmarks[PoseLandmarkIndex.LEFT_HEEL] = createLandmark(0.45f, 0.87f)
        landmarks[PoseLandmarkIndex.LEFT_FOOT_INDEX] = createLandmark(0.55f, 0.84f)
        
        val kneeResult = KneeAngleCalculator.calculateKneeAngleFromLandmarks(landmarks, BodySide.LEFT)
        val hipResult = HipAngleCalculator.calculateHipAngleFromLandmarks(landmarks, BodySide.LEFT)
        val torsoResult = TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(landmarks, BodySide.LEFT)
        val ankleResult = AnkleAngleCalculator.calculateAnkleAngleFromLandmarks(landmarks, BodySide.LEFT)
        
        assertTrue(kneeResult.isValid, "Knee angle should be valid")
        assertTrue(hipResult.isValid, "Hip angle should be valid")
        assertTrue(torsoResult.isValid, "Torso angle should be valid")
        assertTrue(ankleResult.isValid, "Ankle angle should be valid")
        
        // Verify all produce reasonable values
        assertTrue(kneeResult.angle > 0f && kneeResult.angle < 180f,
            "Knee angle ${kneeResult.angle}° should be in valid range")
        assertTrue(hipResult.angle > 0f && hipResult.angle < 180f,
            "Hip angle ${hipResult.angle}° should be in valid range")
        assertTrue(torsoResult.angle > 0f && torsoResult.angle < 90f,
            "Torso angle ${torsoResult.angle}° should be in valid range")
    }

    /**
     * Tests a complete cycling pose for a right-facing cyclist.
     * Uses mirrored coordinates from the left-facing test.
     */
    @Test
    fun `complete right-facing cycling pose - all angles valid`() {
        val landmarks = createEmptyLandmarks()
        
        // Mirrored right-facing cyclist
        landmarks[PoseLandmarkIndex.RIGHT_SHOULDER] = createLandmark(0.70f, 0.30f)
        landmarks[PoseLandmarkIndex.RIGHT_HIP] = createLandmark(0.60f, 0.50f)
        landmarks[PoseLandmarkIndex.RIGHT_KNEE] = createLandmark(0.55f, 0.65f)
        landmarks[PoseLandmarkIndex.RIGHT_ANKLE] = createLandmark(0.52f, 0.85f)
        landmarks[PoseLandmarkIndex.RIGHT_HEEL] = createLandmark(0.55f, 0.87f)
        landmarks[PoseLandmarkIndex.RIGHT_FOOT_INDEX] = createLandmark(0.45f, 0.84f)
        
        val kneeResult = KneeAngleCalculator.calculateKneeAngleFromLandmarks(landmarks, BodySide.RIGHT)
        val hipResult = HipAngleCalculator.calculateHipAngleFromLandmarks(landmarks, BodySide.RIGHT)
        val torsoResult = TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(landmarks, BodySide.RIGHT)
        val ankleResult = AnkleAngleCalculator.calculateAnkleAngleFromLandmarks(landmarks, BodySide.RIGHT)
        
        assertTrue(kneeResult.isValid, "Knee angle should be valid")
        assertTrue(hipResult.isValid, "Hip angle should be valid")
        assertTrue(torsoResult.isValid, "Torso angle should be valid")
        assertTrue(ankleResult.isValid, "Ankle angle should be valid")
        
        // Verify all produce reasonable values
        assertTrue(kneeResult.angle > 0f && kneeResult.angle < 180f,
            "Knee angle ${kneeResult.angle}° should be in valid range")
        assertTrue(hipResult.angle > 0f && hipResult.angle < 180f,
            "Hip angle ${hipResult.angle}° should be in valid range")
        assertTrue(torsoResult.angle > 0f && torsoResult.angle < 90f,
            "Torso angle ${torsoResult.angle}° should be in valid range")
    }

    /**
     * Verifies that mirrored poses produce identical angle measurements.
     */
    @Test
    fun `mirrored poses produce identical angles`() {
        val leftLandmarks = createEmptyLandmarks()
        leftLandmarks[PoseLandmarkIndex.LEFT_SHOULDER] = createLandmark(0.3f, 0.25f)
        leftLandmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(0.4f, 0.45f)
        leftLandmarks[PoseLandmarkIndex.LEFT_KNEE] = createLandmark(0.45f, 0.6f)
        leftLandmarks[PoseLandmarkIndex.LEFT_ANKLE] = createLandmark(0.48f, 0.8f)
        leftLandmarks[PoseLandmarkIndex.LEFT_HEEL] = createLandmark(0.46f, 0.82f)
        leftLandmarks[PoseLandmarkIndex.LEFT_FOOT_INDEX] = createLandmark(0.55f, 0.79f)
        
        val rightLandmarks = createEmptyLandmarks()
        // Mirror all X coordinates: x' = 1.0 - x
        rightLandmarks[PoseLandmarkIndex.RIGHT_SHOULDER] = createLandmark(0.7f, 0.25f)
        rightLandmarks[PoseLandmarkIndex.RIGHT_HIP] = createLandmark(0.6f, 0.45f)
        rightLandmarks[PoseLandmarkIndex.RIGHT_KNEE] = createLandmark(0.55f, 0.6f)
        rightLandmarks[PoseLandmarkIndex.RIGHT_ANKLE] = createLandmark(0.52f, 0.8f)
        rightLandmarks[PoseLandmarkIndex.RIGHT_HEEL] = createLandmark(0.54f, 0.82f)
        rightLandmarks[PoseLandmarkIndex.RIGHT_FOOT_INDEX] = createLandmark(0.45f, 0.79f)
        
        val leftKnee = KneeAngleCalculator.calculateKneeAngleFromLandmarks(leftLandmarks, BodySide.LEFT)
        val rightKnee = KneeAngleCalculator.calculateKneeAngleFromLandmarks(rightLandmarks, BodySide.RIGHT)
        
        val leftHip = HipAngleCalculator.calculateHipAngleFromLandmarks(leftLandmarks, BodySide.LEFT)
        val rightHip = HipAngleCalculator.calculateHipAngleFromLandmarks(rightLandmarks, BodySide.RIGHT)
        
        val leftTorso = TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(leftLandmarks, BodySide.LEFT)
        val rightTorso = TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(rightLandmarks, BodySide.RIGHT)
        
        val leftAnkle = AnkleAngleCalculator.calculateAnkleAngleFromLandmarks(leftLandmarks, BodySide.LEFT)
        val rightAnkle = AnkleAngleCalculator.calculateAnkleAngleFromLandmarks(rightLandmarks, BodySide.RIGHT)
        
        // All angle pairs should be equal (within tolerance)
        assertEquals(leftKnee.angle, rightKnee.angle, 2f,
            "Knee angles should match: left=${leftKnee.angle}, right=${rightKnee.angle}")
        assertEquals(leftHip.angle, rightHip.angle, 2f,
            "Hip angles should match: left=${leftHip.angle}, right=${rightHip.angle}")
        assertEquals(leftTorso.angle, rightTorso.angle, 2f,
            "Torso angles should match: left=${leftTorso.angle}, right=${rightTorso.angle}")
        assertEquals(leftAnkle.angle, rightAnkle.angle, 5f,  // Ankle uses intersection, may vary slightly
            "Ankle angles should match: left=${leftAnkle.angle}, right=${rightAnkle.angle}")
    }
}
