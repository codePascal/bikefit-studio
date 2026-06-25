package bikefitstudio.calibration

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import bikefitstudio.pose.Landmark
import bikefitstudio.pose.PoseFrame
import bikefitstudio.pose.PoseResult

/**
 * Unit tests for CoordinateTransformer.
 */
class CoordinateTransformerTest {

    private lateinit var calibration: BikeCalibration
    private lateinit var transformer: CoordinateTransformer

    @Before
    fun setup() {
        // Create calibration with known positions:
        // - Bottom bracket at (0.5, 0.7) - center bottom
        // - Saddle at (0.3, 0.3) - above and left of BB
        // - Handlebar at (0.8, 0.4) - right and up from BB
        calibration = BikeCalibration(
            bottomBracket = BikeReferencePoint(
                type = BikeReferencePointType.BOTTOM_BRACKET,
                x = 0.5f,
                y = 0.7f
            ),
            saddleTop = BikeReferencePoint(
                type = BikeReferencePointType.SADDLE_TOP,
                x = 0.3f,
                y = 0.3f
            ),
            handlebar = BikeReferencePoint(
                type = BikeReferencePointType.HANDLEBAR,
                x = 0.8f,
                y = 0.4f
            )
        )
        transformer = CoordinateTransformer(calibration)
    }

    // ==================== Distance Calculation Tests ====================

    @Test
    fun `distance calculates correctly for horizontal line`() {
        val d = CoordinateTransformer.distance(0f, 0f, 3f, 0f)
        assertEquals(3f, d, 0.001f)
    }

    @Test
    fun `distance calculates correctly for vertical line`() {
        val d = CoordinateTransformer.distance(0f, 0f, 0f, 4f)
        assertEquals(4f, d, 0.001f)
    }

    @Test
    fun `distance calculates correctly for diagonal (3-4-5 triangle)`() {
        val d = CoordinateTransformer.distance(0f, 0f, 3f, 4f)
        assertEquals(5f, d, 0.001f)
    }

    // ==================== Reference Scale Tests ====================

    @Test
    fun `referenceScale is BB to saddle distance`() {
        // BB at (0.5, 0.7), Saddle at (0.3, 0.3)
        // Distance = sqrt((0.3-0.5)^2 + (0.3-0.7)^2) = sqrt(0.04 + 0.16) = sqrt(0.20)
        val expected = kotlin.math.sqrt(0.20f)
        assertEquals(expected, transformer.getReferenceScale(), 0.001f)
    }

    // ==================== Origin Translation Tests ====================

    @Test
    fun `bottom bracket transforms to origin`() {
        val (x, y) = transformer.getTransformedBikePoint(BikeReferencePointType.BOTTOM_BRACKET)
        assertEquals(0f, x, 0.001f)
        assertEquals(0f, y, 0.001f)
    }

    @Test
    fun `saddle transforms to positive Y`() {
        val (x, y) = transformer.getTransformedBikePoint(BikeReferencePointType.SADDLE_TOP)
        // Saddle is above BB, so Y should be positive
        assertTrue("Saddle Y should be positive (above BB)", y > 0)
        // Saddle is to the left of BB, so X should be negative
        assertTrue("Saddle X should be negative (left of BB)", x < 0)
    }

    @Test
    fun `handlebar transforms correctly`() {
        val (x, y) = transformer.getTransformedBikePoint(BikeReferencePointType.HANDLEBAR)
        // Handlebar is to the right of BB, so X should be positive
        assertTrue("Handlebar X should be positive (right of BB)", x > 0)
        // Handlebar is above BB, so Y should be positive
        assertTrue("Handlebar Y should be positive (above BB)", y > 0)
    }

    // ==================== Landmark Translation Tests ====================

    @Test
    fun `translateLandmark shifts relative to origin`() {
        val landmark = createLandmark(x = 0.6f, y = 0.5f)
        val translated = CoordinateTransformer.translateLandmark(
            landmark,
            originX = 0.5f,
            originY = 0.7f
        )
        
        // X: 0.6 - 0.5 = 0.1
        assertEquals(0.1f, translated.x, 0.001f)
        // Y: 0.7 - 0.5 = 0.2 (inverted Y axis)
        assertEquals(0.2f, translated.y, 0.001f)
    }

    @Test
    fun `translateLandmark inverts Y axis`() {
        // Point below origin should have negative Y
        val landmark = createLandmark(x = 0.5f, y = 0.9f)
        val translated = CoordinateTransformer.translateLandmark(
            landmark,
            originX = 0.5f,
            originY = 0.7f
        )
        
        // Y: 0.7 - 0.9 = -0.2 (point is below origin)
        assertEquals(-0.2f, translated.y, 0.001f)
    }

    // ==================== Scale Normalization Tests ====================

    @Test
    fun `scaleLandmark divides by scale`() {
        val landmark = createLandmark(x = 0.4f, y = 0.8f, z = 0.2f)
        val scaled = CoordinateTransformer.scaleLandmark(landmark, 0.2f)
        
        assertEquals(2.0f, scaled.x, 0.001f)  // 0.4 / 0.2
        assertEquals(4.0f, scaled.y, 0.001f)  // 0.8 / 0.2
        assertEquals(1.0f, scaled.z, 0.001f)  // 0.2 / 0.2
    }

    @Test
    fun `scaleLandmark preserves visibility and presence`() {
        val landmark = createLandmark(visibility = 0.9f, presence = 0.85f)
        val scaled = CoordinateTransformer.scaleLandmark(landmark, 0.5f)
        
        assertEquals(0.9f, scaled.visibility, 0.001f)
        assertEquals(0.85f, scaled.presence, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `scaleLandmark rejects zero scale`() {
        CoordinateTransformer.scaleLandmark(createLandmark(), 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `scaleLandmark rejects negative scale`() {
        CoordinateTransformer.scaleLandmark(createLandmark(), -0.5f)
    }

    // ==================== Full Transformation Tests ====================

    @Test
    fun `transformLandmark applies translation and scale`() {
        // Landmark at BB position should transform to (0, 0)
        val atBB = createLandmark(x = 0.5f, y = 0.7f)
        val transformed = transformer.transformLandmark(atBB)
        
        assertEquals(0f, transformed.x, 0.001f)
        assertEquals(0f, transformed.y, 0.001f)
    }

    @Test
    fun `transformLandmarks processes all landmarks`() {
        val landmarks = listOf(
            createLandmark(x = 0.5f, y = 0.7f),
            createLandmark(x = 0.6f, y = 0.6f),
            createLandmark(x = 0.4f, y = 0.8f)
        )
        
        val transformed = transformer.transformLandmarks(landmarks)
        
        assertEquals(3, transformed.size)
        // First landmark at BB should be at origin
        assertEquals(0f, transformed[0].x, 0.001f)
        assertEquals(0f, transformed[0].y, 0.001f)
    }

    @Test
    fun `visibility and presence preserved through transformation`() {
        val landmark = createLandmark(x = 0.5f, y = 0.5f, visibility = 0.95f, presence = 0.88f)
        val transformed = transformer.transformLandmark(landmark)
        
        assertEquals(0.95f, transformed.visibility, 0.001f)
        assertEquals(0.88f, transformed.presence, 0.001f)
    }

    // ==================== PoseResult Transformation Tests ====================

    @Test
    fun `transformPoseResult transforms landmarks`() {
        val landmarks = listOf(
            createLandmark(x = 0.5f, y = 0.7f),  // At BB
            createLandmark(x = 0.3f, y = 0.3f)   // At saddle
        )
        val result = PoseResult(
            landmarks = landmarks,
            timestampMs = 1000L,
            isValid = true,
            confidence = 0.8f
        )
        
        val transformed = transformer.transformPoseResult(result)
        
        assertTrue(transformed.isValid)
        assertEquals(0.8f, transformed.confidence, 0.001f)
        assertEquals(0f, transformed.landmarks[0].x, 0.001f)
        assertEquals(0f, transformed.landmarks[0].y, 0.001f)
    }

    @Test
    fun `transformPoseResult returns invalid result unchanged`() {
        val result = PoseResult.EMPTY
        val transformed = transformer.transformPoseResult(result)
        
        assertFalse(transformed.isValid)
        assertTrue(transformed.landmarks.isEmpty())
    }

    // ==================== PoseFrame Transformation Tests ====================

    @Test
    fun `transformPoseFrame transforms landmarks`() {
        val landmarks = listOf(createLandmark(x = 0.5f, y = 0.7f))
        val frame = PoseFrame(
            frameNumber = 1,
            timestampMs = 1000L,
            landmarks = landmarks,
            confidence = 0.9f
        )
        
        val transformed = transformer.transformPoseFrame(frame)
        
        assertEquals(1, transformed.frameNumber)
        assertEquals(1000L, transformed.timestampMs)
        assertEquals(0f, transformed.landmarks[0].x, 0.001f)
    }

    @Test
    fun `transformPoseFrame returns invalid frame unchanged`() {
        val frame = PoseFrame.EMPTY
        val transformed = transformer.transformPoseFrame(frame)
        
        assertFalse(transformed.isValid)
    }

    // ==================== Static Transform Function Tests ====================

    @Test
    fun `static transform function works correctly`() {
        val landmarks = listOf(
            createLandmark(x = 0.5f, y = 0.7f),  // At BB
            createLandmark(x = 0.6f, y = 0.5f)
        )
        
        val transformed = CoordinateTransformer.transform(landmarks, calibration)
        
        assertEquals(2, transformed.size)
        assertEquals(0f, transformed[0].x, 0.001f)
        assertEquals(0f, transformed[0].y, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `static transform rejects incomplete calibration`() {
        val incomplete = BikeCalibration(
            saddleTop = BikeReferencePoint(BikeReferencePointType.SADDLE_TOP, 0.5f, 0.5f)
        )
        
        CoordinateTransformer.transform(listOf(createLandmark()), incomplete)
    }

    // ==================== Constructor Validation Tests ====================

    @Test(expected = IllegalArgumentException::class)
    fun `constructor rejects incomplete calibration`() {
        val incomplete = BikeCalibration(
            saddleTop = BikeReferencePoint(BikeReferencePointType.SADDLE_TOP, 0.5f, 0.5f)
        )
        
        CoordinateTransformer(incomplete)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `constructor rejects zero scale calibration`() {
        // Saddle and BB at same position
        val zeroScale = BikeCalibration(
            bottomBracket = BikeReferencePoint(BikeReferencePointType.BOTTOM_BRACKET, 0.5f, 0.5f),
            saddleTop = BikeReferencePoint(BikeReferencePointType.SADDLE_TOP, 0.5f, 0.5f),
            handlebar = BikeReferencePoint(BikeReferencePointType.HANDLEBAR, 0.8f, 0.4f)
        )
        
        CoordinateTransformer(zeroScale)
    }

    // ==================== Repository Factory Tests ====================

    @Test
    fun `fromRepository returns null when no calibration`() {
        CalibrationRepository.reset()
        
        assertNull(CoordinateTransformer.fromRepository())
    }

    @Test
    fun `fromRepository returns transformer when calibration exists`() {
        CalibrationRepository.reset()
        CalibrationRepository.setCalibration(calibration)
        
        val transformer = CoordinateTransformer.fromRepository()
        
        assertNotNull(transformer)
        
        CalibrationRepository.reset()
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
}
