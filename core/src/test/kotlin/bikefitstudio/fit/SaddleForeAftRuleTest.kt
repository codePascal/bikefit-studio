package bikefitstudio.fit

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import bikefitstudio.biomechanics.BodySide
import bikefitstudio.calibration.BikeCalibration
import bikefitstudio.calibration.BikeReferencePoint
import bikefitstudio.calibration.BikeReferencePointType
import bikefitstudio.pose.Landmark
import bikefitstudio.pose.PoseFrame
import bikefitstudio.pose.PoseLandmarkIndex

class SaddleForeAftRuleTest {

    private lateinit var rule: SaddleForeAftRule
    private lateinit var calibration: BikeCalibration

    @Before
    fun setup() {
        rule = SaddleForeAftRule()
        // Standard calibration with BB at center (0.5)
        calibration = BikeCalibration(
            bottomBracket = BikeReferencePoint(
                type = BikeReferencePointType.BOTTOM_BRACKET,
                x = 0.5f,
                y = 0.7f
            ),
            saddleTop = BikeReferencePoint(
                type = BikeReferencePointType.SADDLE_TOP,
                x = 0.4f,
                y = 0.4f
            )
        )
    }

    // ==================== Configuration Constants Tests ====================

    @Test
    fun `config has documented threshold constants`() {
        assertEquals(0.03f, SaddleForeAftConfig.MAX_FORWARD_OFFSET, 0.001f)
        assertEquals(0.03f, SaddleForeAftConfig.MAX_BACKWARD_OFFSET, 0.001f)
        assertEquals(2.0f, SaddleForeAftConfig.HIGH_SEVERITY_MULTIPLIER, 0.001f)
        assertEquals(0.5f, SaddleForeAftConfig.MIN_VISIBILITY, 0.001f)
    }

    @Test
    fun `default config uses constant values`() {
        val config = SaddleForeAftConfig()
        assertEquals(SaddleForeAftConfig.MAX_FORWARD_OFFSET, config.maxForwardOffset, 0.001f)
        assertEquals(SaddleForeAftConfig.MAX_BACKWARD_OFFSET, config.maxBackwardOffset, 0.001f)
    }

    @Test
    fun `config can be customized`() {
        val config = SaddleForeAftConfig(
            maxForwardOffset = 0.05f,
            maxBackwardOffset = 0.02f
        )
        assertEquals(0.05f, config.maxForwardOffset, 0.001f)
        assertEquals(0.02f, config.maxBackwardOffset, 0.001f)
    }

    // ==================== KopsResult Tests ====================

    @Test
    fun `KopsResult INVALID is not valid`() {
        assertFalse(KopsResult.INVALID.isValid)
    }

    @Test
    fun `KopsResult isKneeForward true when offset positive`() {
        val result = KopsResult(kneeX = 0.55f, pedalSpindleX = 0.5f, offset = 0.05f, isValid = true)
        assertTrue(result.isKneeForward)
        assertFalse(result.isKneeBehind)
    }

    @Test
    fun `KopsResult isKneeBehind true when offset negative`() {
        val result = KopsResult(kneeX = 0.45f, pedalSpindleX = 0.5f, offset = -0.05f, isValid = true)
        assertTrue(result.isKneeBehind)
        assertFalse(result.isKneeForward)
    }

    // ==================== measureKops Tests ====================

    @Test
    fun `measureKops returns INVALID without bottom bracket calibration`() {
        val emptyCalibration = BikeCalibration.EMPTY
        val result = rule.measureKops(0.5f, emptyCalibration)
        assertFalse(result.isValid)
    }

    @Test
    fun `measureKops calculates correct offset when knee forward`() {
        val result = rule.measureKops(0.55f, calibration)

        assertTrue(result.isValid)
        assertEquals(0.55f, result.kneeX, 0.001f)
        assertEquals(0.5f, result.pedalSpindleX, 0.001f)
        assertEquals(0.05f, result.offset, 0.001f)
    }

    @Test
    fun `measureKops calculates correct offset when knee behind`() {
        val result = rule.measureKops(0.45f, calibration)

        assertTrue(result.isValid)
        assertEquals(-0.05f, result.offset, 0.001f)
    }

    @Test
    fun `measureKops calculates zero offset when knee aligned`() {
        val result = rule.measureKops(0.5f, calibration)

        assertTrue(result.isValid)
        assertEquals(0f, result.offset, 0.001f)
    }

    @Test
    fun `measureKops with PoseFrame extracts correct knee position`() {
        val poseFrame = createPoseFrame(leftKneeX = 0.55f, rightKneeX = 0.45f)

        val leftResult = rule.measureKops(poseFrame, calibration, BodySide.LEFT)
        val rightResult = rule.measureKops(poseFrame, calibration, BodySide.RIGHT)

        assertEquals(0.05f, leftResult.offset, 0.001f)
        assertEquals(-0.05f, rightResult.offset, 0.001f)
    }

    @Test
    fun `measureKops with PoseFrame returns INVALID when knee not visible`() {
        val poseFrame = createPoseFrame(leftKneeX = 0.55f, leftKneeVisibility = 0.3f)

        val result = rule.measureKops(poseFrame, calibration, BodySide.LEFT)

        assertFalse(result.isValid)
    }

    @Test
    fun `measureKops with PoseFrame returns INVALID when not enough landmarks`() {
        val poseFrame = PoseFrame(
            frameNumber = 0,
            timestampMs = 0,
            landmarks = emptyList(),
            confidence = 1.0f
        )

        val result = rule.measureKops(poseFrame, calibration, BodySide.LEFT)

        assertFalse(result.isValid)
    }

    // ==================== Optimal Range Tests ====================

    @Test
    fun `analyze returns no issues when knee aligned with pedal`() {
        val kopsResult = KopsResult(kneeX = 0.5f, pedalSpindleX = 0.5f, offset = 0f, isValid = true)
        val issues = rule.analyze(kopsResult)

        assertTrue(issues.isEmpty())
    }

    @Test
    fun `analyze returns no issues within tolerance forward`() {
        val kopsResult = KopsResult(kneeX = 0.52f, pedalSpindleX = 0.5f, offset = 0.02f, isValid = true)
        val issues = rule.analyze(kopsResult)

        assertTrue(issues.isEmpty())
    }

    @Test
    fun `analyze returns no issues within tolerance backward`() {
        val kopsResult = KopsResult(kneeX = 0.48f, pedalSpindleX = 0.5f, offset = -0.02f, isValid = true)
        val issues = rule.analyze(kopsResult)

        assertTrue(issues.isEmpty())
    }

    // ==================== Saddle Too Far Back Tests ====================

    @Test
    fun `analyze detects saddle too far back when knee forward`() {
        val kopsResult = KopsResult(kneeX = 0.55f, pedalSpindleX = 0.5f, offset = 0.05f, isValid = true)
        val issues = rule.analyze(kopsResult)

        assertEquals(1, issues.size)
        assertEquals(FitIssueType.SADDLE_FORE_AFT, issues[0].type)
        assertTrue(issues[0].description.contains("forward"))
    }

    @Test
    fun `saddle too far back recommends moving saddle forward`() {
        val kopsResult = KopsResult(kneeX = 0.55f, pedalSpindleX = 0.5f, offset = 0.05f, isValid = true)
        val issues = rule.analyze(kopsResult)

        assertTrue(issues[0].recommendation?.contains("forward") == true)
    }

    @Test
    fun `saddle too far back severity scales with offset`() {
        // Just above threshold -> LOW
        val lowResult = KopsResult(kneeX = 0.535f, pedalSpindleX = 0.5f, offset = 0.035f, isValid = true)
        assertEquals(Severity.LOW, rule.analyze(lowResult)[0].severity)

        // 1.5x threshold -> MEDIUM
        val medResult = KopsResult(kneeX = 0.55f, pedalSpindleX = 0.5f, offset = 0.05f, isValid = true)
        assertEquals(Severity.MEDIUM, rule.analyze(medResult)[0].severity)

        // 2x+ threshold -> HIGH
        val highResult = KopsResult(kneeX = 0.57f, pedalSpindleX = 0.5f, offset = 0.07f, isValid = true)
        assertEquals(Severity.HIGH, rule.analyze(highResult)[0].severity)
    }

    // ==================== Saddle Too Far Forward Tests ====================

    @Test
    fun `analyze detects saddle too far forward when knee behind`() {
        val kopsResult = KopsResult(kneeX = 0.45f, pedalSpindleX = 0.5f, offset = -0.05f, isValid = true)
        val issues = rule.analyze(kopsResult)

        assertEquals(1, issues.size)
        assertEquals(FitIssueType.SADDLE_FORE_AFT, issues[0].type)
        assertTrue(issues[0].description.contains("behind"))
    }

    @Test
    fun `saddle too far forward recommends moving saddle back`() {
        val kopsResult = KopsResult(kneeX = 0.45f, pedalSpindleX = 0.5f, offset = -0.05f, isValid = true)
        val issues = rule.analyze(kopsResult)

        assertTrue(issues[0].recommendation?.contains("back") == true)
    }

    @Test
    fun `saddle too far forward severity scales with offset`() {
        // Just above threshold -> LOW
        val lowResult = KopsResult(kneeX = 0.465f, pedalSpindleX = 0.5f, offset = -0.035f, isValid = true)
        assertEquals(Severity.LOW, rule.analyze(lowResult)[0].severity)

        // 1.5x threshold -> MEDIUM
        val medResult = KopsResult(kneeX = 0.45f, pedalSpindleX = 0.5f, offset = -0.05f, isValid = true)
        assertEquals(Severity.MEDIUM, rule.analyze(medResult)[0].severity)

        // 2x+ threshold -> HIGH
        val highResult = KopsResult(kneeX = 0.43f, pedalSpindleX = 0.5f, offset = -0.07f, isValid = true)
        assertEquals(Severity.HIGH, rule.analyze(highResult)[0].severity)
    }

    // ==================== Optimal Range in Issue Tests ====================

    @Test
    fun `issue has correct optimal range`() {
        val kopsResult = KopsResult(kneeX = 0.55f, pedalSpindleX = 0.5f, offset = 0.05f, isValid = true)
        val issues = rule.analyze(kopsResult)

        assertEquals(-0.03f..0.03f, issues[0].optimalRange)
    }

    @Test
    fun `issue has measured value`() {
        val kopsResult = KopsResult(kneeX = 0.55f, pedalSpindleX = 0.5f, offset = 0.05f, isValid = true)
        val issues = rule.analyze(kopsResult)

        assertEquals(0.05f, issues[0].measuredValue)
    }

    // ==================== Invalid Result Tests ====================

    @Test
    fun `analyze returns empty for invalid KopsResult`() {
        val issues = rule.analyze(KopsResult.INVALID)
        assertTrue(issues.isEmpty())
    }

    // ==================== analyzeAverageOffset Tests ====================

    @Test
    fun `analyzeAverageOffset works with average measurement`() {
        val issues = rule.analyzeAverageOffset(0.05f)

        assertEquals(1, issues.size)
        assertEquals(FitIssueType.SADDLE_FORE_AFT, issues[0].type)
    }

    @Test
    fun `analyzeAverageOffset returns no issues for optimal`() {
        val issues = rule.analyzeAverageOffset(0.01f)
        assertTrue(issues.isEmpty())
    }

    // ==================== Static Helper Tests ====================

    @Test
    fun `isOptimalPosition returns true within range`() {
        assertTrue(SaddleForeAftRule.isOptimalPosition(0f))
        assertTrue(SaddleForeAftRule.isOptimalPosition(0.02f))
        assertTrue(SaddleForeAftRule.isOptimalPosition(-0.02f))
    }

    @Test
    fun `isOptimalPosition returns false outside range`() {
        assertFalse(SaddleForeAftRule.isOptimalPosition(0.05f))
        assertFalse(SaddleForeAftRule.isOptimalPosition(-0.05f))
    }

    @Test
    fun `isSaddleTooFarBack returns true when offset too positive`() {
        assertTrue(SaddleForeAftRule.isSaddleTooFarBack(0.05f))
        assertFalse(SaddleForeAftRule.isSaddleTooFarBack(0.02f))
        assertFalse(SaddleForeAftRule.isSaddleTooFarBack(-0.05f))
    }

    @Test
    fun `isSaddleTooFarForward returns true when offset too negative`() {
        assertTrue(SaddleForeAftRule.isSaddleTooFarForward(-0.05f))
        assertFalse(SaddleForeAftRule.isSaddleTooFarForward(-0.02f))
        assertFalse(SaddleForeAftRule.isSaddleTooFarForward(0.05f))
    }

    @Test
    fun `static helpers accept custom config`() {
        val config = SaddleForeAftConfig(maxForwardOffset = 0.05f, maxBackwardOffset = 0.05f)

        assertTrue(SaddleForeAftRule.isOptimalPosition(0.04f, config))
        assertFalse(SaddleForeAftRule.isSaddleTooFarBack(0.04f, config))
    }

    // ==================== Direct PoseFrame Analysis Tests ====================

    @Test
    fun `analyze with PoseFrame returns issues when knee forward`() {
        val poseFrame = createPoseFrame(leftKneeX = 0.55f)
        val issues = rule.analyze(poseFrame, calibration, BodySide.LEFT)

        assertEquals(1, issues.size)
        assertEquals(FitIssueType.SADDLE_FORE_AFT, issues[0].type)
    }

    @Test
    fun `analyze with PoseFrame returns empty when optimal`() {
        val poseFrame = createPoseFrame(leftKneeX = 0.5f)
        val issues = rule.analyze(poseFrame, calibration, BodySide.LEFT)

        assertTrue(issues.isEmpty())
    }

    // ==================== Helper Functions ====================

    private fun createPoseFrame(
        leftKneeX: Float = 0.5f,
        rightKneeX: Float = 0.5f,
        leftKneeVisibility: Float = 1.0f,
        rightKneeVisibility: Float = 1.0f
    ): PoseFrame {
        val landmarks = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) { index ->
            Landmark(
                x = when (index) {
                    PoseLandmarkIndex.LEFT_KNEE -> leftKneeX
                    PoseLandmarkIndex.RIGHT_KNEE -> rightKneeX
                    else -> 0.5f
                },
                y = 0.5f,
                z = 0f,
                visibility = when (index) {
                    PoseLandmarkIndex.LEFT_KNEE -> leftKneeVisibility
                    PoseLandmarkIndex.RIGHT_KNEE -> rightKneeVisibility
                    else -> 1.0f
                },
                presence = 1.0f
            )
        }

        return PoseFrame(
            frameNumber = 0,
            timestampMs = 0,
            landmarks = landmarks,
            confidence = 1.0f
        )
    }
}
