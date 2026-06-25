package bikefitstudio.pose

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PoseValidator.
 */
class PoseValidatorTest {

    private lateinit var validator: PoseValidator

    @Before
    fun setup() {
        validator = PoseValidator()
    }

    // ==================== Basic Validation Tests ====================

    @Test
    fun `empty pose result is invalid`() {
        val result = validator.validate(PoseResult.EMPTY)
        
        assertFalse(result.isValid)
        assertEquals("No pose detected", result.reason)
        assertTrue(result.issues.contains(ValidationIssue.NO_POSE_DETECTED))
    }

    @Test
    fun `empty pose frame is invalid`() {
        val result = validator.validate(PoseFrame.EMPTY)
        
        assertFalse(result.isValid)
        assertTrue(result.issues.contains(ValidationIssue.NO_POSE_DETECTED))
    }

    @Test
    fun `valid pose with all landmarks passes validation`() {
        val poseResult = createValidPoseResult()
        val result = validator.validate(poseResult)
        
        assertTrue(result.isValid)
        assertTrue(result.issues.isEmpty())
        assertTrue(result.missingLandmarks.isEmpty())
    }

    @Test
    fun `valid pose frame passes validation`() {
        val poseFrame = createValidPoseFrame()
        val result = validator.validate(poseFrame)
        
        assertTrue(result.isValid)
    }

    // ==================== Confidence Threshold Tests ====================

    @Test
    fun `low confidence pose is invalid`() {
        val poseResult = createValidPoseResult(confidence = 0.3f)
        val result = validator.validate(poseResult)
        
        assertFalse(result.isValid)
        assertTrue(result.issues.contains(ValidationIssue.LOW_CONFIDENCE))
    }

    @Test
    fun `pose at confidence threshold is valid`() {
        val poseResult = createValidPoseResult(confidence = 0.5f)
        val result = validator.validate(poseResult)
        
        assertTrue(result.isValid)
    }

    @Test
    fun `pose just below confidence threshold is invalid`() {
        val poseResult = createValidPoseResult(confidence = 0.49f)
        val result = validator.validate(poseResult)
        
        assertFalse(result.isValid)
        assertTrue(result.issues.contains(ValidationIssue.LOW_CONFIDENCE))
    }

    @Test
    fun `custom confidence threshold is respected`() {
        val strictValidator = PoseValidator(minOverallConfidence = 0.8f)
        val poseResult = createValidPoseResult(confidence = 0.7f)
        
        val result = strictValidator.validate(poseResult)
        
        assertFalse(result.isValid)
        assertTrue(result.issues.contains(ValidationIssue.LOW_CONFIDENCE))
    }

    // ==================== Missing Landmarks Tests ====================

    @Test
    fun `missing essential landmark causes invalid result`() {
        // Create pose without left hip
        val landmarks = create33Landmarks(visibility = 0.9f).toMutableList()
        landmarks[PoseLandmarkIndex.LEFT_HIP] = createLandmark(visibility = 0.1f)
        
        val poseResult = PoseResult(
            landmarks = landmarks,
            timestampMs = 1000L,
            isValid = true,
            confidence = 0.8f
        )
        
        val result = validator.validate(poseResult, Side.LEFT)
        
        assertFalse(result.isValid)
        assertTrue(result.issues.contains(ValidationIssue.MISSING_ESSENTIAL_LANDMARKS))
        assertTrue(result.missingLandmarks.contains(PoseLandmarkIndex.LEFT_HIP))
    }

    @Test
    fun `low visibility landmark is flagged as missing`() {
        val landmarks = create33Landmarks(visibility = 0.9f).toMutableList()
        landmarks[PoseLandmarkIndex.LEFT_KNEE] = createLandmark(visibility = 0.3f)
        
        val poseResult = PoseResult(
            landmarks = landmarks,
            timestampMs = 1000L,
            isValid = true,
            confidence = 0.8f
        )
        
        val result = validator.validate(poseResult, Side.LEFT)
        
        assertTrue(result.missingLandmarks.contains(PoseLandmarkIndex.LEFT_KNEE))
    }

    @Test
    fun `right side validation checks right landmarks`() {
        val landmarks = create33Landmarks(visibility = 0.9f).toMutableList()
        landmarks[PoseLandmarkIndex.RIGHT_ANKLE] = createLandmark(visibility = 0.1f)
        
        val poseResult = PoseResult(
            landmarks = landmarks,
            timestampMs = 1000L,
            isValid = true,
            confidence = 0.8f
        )
        
        val result = validator.validate(poseResult, Side.RIGHT)
        
        assertTrue(result.missingLandmarks.contains(PoseLandmarkIndex.RIGHT_ANKLE))
    }

    @Test
    fun `non-essential landmarks can be low visibility`() {
        // Face landmarks are not essential - can have low visibility
        val landmarks = create33Landmarks(visibility = 0.9f).toMutableList()
        landmarks[PoseLandmarkIndex.NOSE] = createLandmark(visibility = 0.1f)
        landmarks[PoseLandmarkIndex.LEFT_EYE] = createLandmark(visibility = 0.1f)
        
        val poseResult = PoseResult(
            landmarks = landmarks,
            timestampMs = 1000L,
            isValid = true,
            confidence = 0.8f
        )
        
        val result = validator.validate(poseResult, Side.LEFT)
        
        assertTrue(result.isValid)
        assertFalse(result.missingLandmarks.contains(PoseLandmarkIndex.NOSE))
    }

    // ==================== Essential Visibility Tests ====================

    @Test
    fun `low average essential visibility flags issue`() {
        val landmarks = create33Landmarks(visibility = 0.55f).toMutableList()
        // Essential landmarks at threshold but below minEssentialVisibility
        
        val poseResult = PoseResult(
            landmarks = landmarks,
            timestampMs = 1000L,
            isValid = true,
            confidence = 0.8f
        )
        
        val result = validator.validate(poseResult, Side.LEFT)
        
        assertTrue(result.issues.contains(ValidationIssue.LOW_LANDMARK_VISIBILITY))
    }

    // ==================== Quick Check Tests ====================

    @Test
    fun `isValid returns true for valid pose`() {
        val poseResult = createValidPoseResult()
        
        assertTrue(validator.isValid(poseResult))
    }

    @Test
    fun `isValid returns false for invalid pose`() {
        assertFalse(validator.isValid(PoseResult.EMPTY))
    }

    // ==================== Filter Tests ====================

    @Test
    fun `filterValid removes invalid poses`() {
        val validPose = createValidPoseResult()
        val invalidPose = PoseResult.EMPTY
        val lowConfidence = createValidPoseResult(confidence = 0.2f)
        
        val results = listOf(validPose, invalidPose, lowConfidence)
        val filtered = validator.filterValid(results)
        
        assertEquals(1, filtered.size)
        assertEquals(validPose, filtered[0])
    }

    @Test
    fun `filterValidFrames removes invalid frames`() {
        val validFrame = createValidPoseFrame()
        val invalidFrame = PoseFrame.EMPTY
        
        val frames = listOf(validFrame, invalidFrame)
        val filtered = validator.filterValidFrames(frames)
        
        assertEquals(1, filtered.size)
    }

    // ==================== Statistics Tests ====================

    @Test
    fun `validationStats calculates correctly`() {
        val poses = listOf(
            createValidPoseResult(),
            createValidPoseResult(),
            PoseResult.EMPTY,
            createValidPoseResult(confidence = 0.2f)
        )
        
        val stats = validator.validationStats(poses)
        
        assertEquals(4, stats.totalFrames)
        assertEquals(2, stats.validFrames)
        assertEquals(2, stats.invalidFrames)
        assertEquals(50f, stats.validPercentage, 0.01f)
    }

    @Test
    fun `validationStats handles empty list`() {
        val stats = validator.validationStats(emptyList())
        
        assertEquals(0, stats.totalFrames)
        assertEquals(0, stats.validFrames)
        assertEquals(0f, stats.validPercentage, 0.01f)
    }

    @Test
    fun `hasEnoughValidFrames respects threshold`() {
        val stats = ValidationStats(
            totalFrames = 100,
            validFrames = 75,
            invalidFrames = 25,
            validPercentage = 75f
        )
        
        assertTrue(stats.hasEnoughValidFrames(70f))
        assertTrue(stats.hasEnoughValidFrames(75f))
        assertFalse(stats.hasEnoughValidFrames(80f))
    }

    // ==================== Factory Method Tests ====================

    @Test
    fun `strict validator has higher thresholds`() {
        val strict = PoseValidator.strict()
        
        assertEquals(0.7f, strict.minOverallConfidence, 0.01f)
        assertEquals(0.7f, strict.minLandmarkVisibility, 0.01f)
        assertEquals(0.8f, strict.minEssentialVisibility, 0.01f)
    }

    @Test
    fun `lenient validator has lower thresholds`() {
        val lenient = PoseValidator.lenient()
        
        assertEquals(0.3f, lenient.minOverallConfidence, 0.01f)
        assertEquals(0.3f, lenient.minLandmarkVisibility, 0.01f)
        assertEquals(0.4f, lenient.minEssentialVisibility, 0.01f)
    }

    @Test
    fun `strict validator rejects medium confidence pose`() {
        val strict = PoseValidator.strict()
        val pose = createValidPoseResult(confidence = 0.6f)
        
        assertFalse(strict.isValid(pose))
    }

    @Test
    fun `lenient validator accepts low confidence pose`() {
        val lenient = PoseValidator.lenient()
        val landmarks = create33Landmarks(visibility = 0.5f)
        val pose = PoseResult(
            landmarks = landmarks,
            timestampMs = 1000L,
            isValid = true,
            confidence = 0.4f
        )
        
        assertTrue(lenient.isValid(pose))
    }

    // ==================== Constructor Validation Tests ====================

    @Test(expected = IllegalArgumentException::class)
    fun `constructor rejects confidence above 1`() {
        PoseValidator(minOverallConfidence = 1.5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `constructor rejects negative confidence`() {
        PoseValidator(minOverallConfidence = -0.1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `constructor rejects invalid landmark visibility`() {
        PoseValidator(minLandmarkVisibility = 2.0f)
    }

    // ==================== ValidationResult Tests ====================

    @Test
    fun `ValidationResult VALID is correct`() {
        val result = ValidationResult.VALID
        
        assertTrue(result.isValid)
        assertTrue(result.issues.isEmpty())
        assertEquals("", result.reason)
    }

    @Test
    fun `ValidationResult EMPTY_POSE is correct`() {
        val result = ValidationResult.EMPTY_POSE
        
        assertFalse(result.isValid)
        assertTrue(result.issues.contains(ValidationIssue.NO_POSE_DETECTED))
    }

    // ==================== Essential Landmarks Constants Tests ====================

    @Test
    fun `essential left landmarks includes required joints`() {
        val essentialLeft = PoseValidator.ESSENTIAL_LEFT_LANDMARKS
        
        assertTrue(essentialLeft.contains(PoseLandmarkIndex.LEFT_SHOULDER))
        assertTrue(essentialLeft.contains(PoseLandmarkIndex.LEFT_HIP))
        assertTrue(essentialLeft.contains(PoseLandmarkIndex.LEFT_KNEE))
        assertTrue(essentialLeft.contains(PoseLandmarkIndex.LEFT_ANKLE))
        assertEquals(4, essentialLeft.size)
    }

    @Test
    fun `essential right landmarks includes required joints`() {
        val essentialRight = PoseValidator.ESSENTIAL_RIGHT_LANDMARKS
        
        assertTrue(essentialRight.contains(PoseLandmarkIndex.RIGHT_SHOULDER))
        assertTrue(essentialRight.contains(PoseLandmarkIndex.RIGHT_HIP))
        assertTrue(essentialRight.contains(PoseLandmarkIndex.RIGHT_KNEE))
        assertTrue(essentialRight.contains(PoseLandmarkIndex.RIGHT_ANKLE))
        assertEquals(4, essentialRight.size)
    }

    // ==================== Helper Functions ====================

    private fun createLandmark(
        x: Float = 0.5f,
        y: Float = 0.5f,
        z: Float = 0f,
        visibility: Float = 0.9f,
        presence: Float = 0.9f
    ): Landmark {
        return Landmark(x = x, y = y, z = z, visibility = visibility, presence = presence)
    }

    private fun create33Landmarks(visibility: Float = 0.9f): List<Landmark> {
        return (0 until 33).map {
            createLandmark(visibility = visibility)
        }
    }

    private fun createValidPoseResult(confidence: Float = 0.8f): PoseResult {
        return PoseResult(
            landmarks = create33Landmarks(visibility = 0.9f),
            timestampMs = System.currentTimeMillis(),
            isValid = true,
            confidence = confidence
        )
    }

    private fun createValidPoseFrame(confidence: Float = 0.8f): PoseFrame {
        return PoseFrame(
            frameNumber = 1,
            timestampMs = System.currentTimeMillis(),
            landmarks = create33Landmarks(visibility = 0.9f),
            confidence = confidence
        )
    }
}
