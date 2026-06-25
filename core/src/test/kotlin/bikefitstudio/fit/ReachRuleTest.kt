package bikefitstudio.fit

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import bikefitstudio.biomechanics.AngleStats
import bikefitstudio.biomechanics.BodySide
import bikefitstudio.biomechanics.CycleMetrics
import bikefitstudio.biomechanics.CycleSummary
import bikefitstudio.biomechanics.TorsoAngleResult
import bikefitstudio.calibration.BikeCalibration
import bikefitstudio.calibration.BikeReferencePoint
import bikefitstudio.calibration.BikeReferencePointType

class ReachRuleTest {

    private lateinit var rule: ReachRule

    @Before
    fun setup() {
        rule = ReachRule()
    }

    // ==================== Configuration Constants Tests ====================

    @Test
    fun `config has documented threshold constants`() {
        assertEquals(30f, ReachConfig.MIN_OPTIMAL_TORSO_ANGLE, 0.001f)
        assertEquals(60f, ReachConfig.MAX_OPTIMAL_TORSO_ANGLE, 0.001f)
        assertEquals(25f, ReachConfig.TOO_AGGRESSIVE_ANGLE, 0.001f)
        assertEquals(70f, ReachConfig.TOO_UPRIGHT_ANGLE, 0.001f)
    }

    @Test
    fun `default config uses constant values`() {
        val config = ReachConfig()
        assertEquals(ReachConfig.MIN_OPTIMAL_TORSO_ANGLE, config.minOptimalTorsoAngle, 0.001f)
        assertEquals(ReachConfig.MAX_OPTIMAL_TORSO_ANGLE, config.maxOptimalTorsoAngle, 0.001f)
    }

    @Test
    fun `config can be customized`() {
        val config = ReachConfig(
            minOptimalTorsoAngle = 35f,
            maxOptimalTorsoAngle = 55f
        )
        assertEquals(35f, config.minOptimalTorsoAngle, 0.001f)
        assertEquals(55f, config.maxOptimalTorsoAngle, 0.001f)
    }

    // ==================== Optimal Range Tests ====================

    @Test
    fun `torso angle in optimal range returns no issues`() {
        val issues = rule.analyze(45f)
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `torso angle at min optimal boundary returns no issues`() {
        val issues = rule.analyze(30f)
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `torso angle at max optimal boundary returns no issues`() {
        val issues = rule.analyze(60f)
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `torso angle mid optimal range returns no issues`() {
        val issues = rule.analyze(45f)
        assertTrue(issues.isEmpty())
    }

    // ==================== Reach Too Long Tests ====================

    @Test
    fun `torso angle below min optimal returns reach too long issue`() {
        val issues = rule.analyze(28f)

        assertEquals(1, issues.size)
        assertEquals(FitIssueType.REACH, issues[0].type)
        assertTrue(issues[0].description.contains("aggressive"))
    }

    @Test
    fun `torso angle slightly below optimal is MEDIUM severity`() {
        val issues = rule.analyze(28f)
        assertEquals(Severity.MEDIUM, issues[0].severity)
    }

    @Test
    fun `torso angle at too aggressive threshold is HIGH severity`() {
        val issues = rule.analyze(25f)
        assertEquals(Severity.HIGH, issues[0].severity)
    }

    @Test
    fun `torso angle well below threshold is HIGH severity`() {
        val issues = rule.analyze(20f)
        assertEquals(Severity.HIGH, issues[0].severity)
    }

    @Test
    fun `reach too long recommends reducing reach`() {
        val issues = rule.analyze(22f)
        assertTrue(issues[0].recommendation?.contains("shorter stem") == true)
    }

    @Test
    fun `reach too long has correct optimal range`() {
        val issues = rule.analyze(22f)
        assertEquals(30f..60f, issues[0].optimalRange)
    }

    @Test
    fun `reach too long has measured value`() {
        val issues = rule.analyze(22f)
        assertEquals(22f, issues[0].measuredValue)
    }

    // ==================== Reach Too Short Tests ====================

    @Test
    fun `torso angle above max optimal returns reach too short issue`() {
        val issues = rule.analyze(65f)

        assertEquals(1, issues.size)
        assertEquals(FitIssueType.REACH, issues[0].type)
        assertTrue(issues[0].description.contains("upright"))
    }

    @Test
    fun `torso angle slightly above optimal is MEDIUM severity`() {
        val issues = rule.analyze(65f)
        assertEquals(Severity.MEDIUM, issues[0].severity)
    }

    @Test
    fun `torso angle at too upright threshold is HIGH severity`() {
        val issues = rule.analyze(70f)
        assertEquals(Severity.HIGH, issues[0].severity)
    }

    @Test
    fun `torso angle well above threshold is HIGH severity`() {
        val issues = rule.analyze(75f)
        assertEquals(Severity.HIGH, issues[0].severity)
    }

    @Test
    fun `reach too short recommends increasing reach`() {
        val issues = rule.analyze(72f)
        assertTrue(issues[0].recommendation?.contains("longer stem") == true)
    }

    // ==================== TorsoAngleResult Analysis Tests ====================

    @Test
    fun `analyze TorsoAngleResult with optimal angle returns no issues`() {
        val result = TorsoAngleResult(
            angle = 45f,
            side = BodySide.LEFT,
            isValid = true,
            confidence = 1.0f
        )
        val issues = rule.analyze(result)
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `analyze TorsoAngleResult with invalid result returns no issues`() {
        val result = TorsoAngleResult(
            angle = 20f,
            side = BodySide.LEFT,
            isValid = false,
            confidence = 0f
        )
        val issues = rule.analyze(result)
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `analyze TorsoAngleResult with aggressive angle returns issues`() {
        val result = TorsoAngleResult(
            angle = 22f,
            side = BodySide.LEFT,
            isValid = true,
            confidence = 1.0f
        )
        val issues = rule.analyze(result)
        assertEquals(1, issues.size)
        assertEquals(FitIssueType.REACH, issues[0].type)
    }

    // ==================== CycleMetrics Analysis Tests ====================

    @Test
    fun `analyze CycleMetrics with optimal torso returns no issues`() {
        val cycle = createCycleMetrics(torsoAngle = 45f)
        val issues = rule.analyze(cycle)
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `analyze CycleMetrics with aggressive torso returns issues`() {
        val cycle = createCycleMetrics(torsoAngle = 22f)
        val issues = rule.analyze(cycle)
        assertEquals(1, issues.size)
    }

    @Test
    fun `analyze CycleMetrics with invalid torso returns no issues`() {
        val cycle = CycleMetrics(
            cycleNumber = 0,
            startFrameNumber = 0,
            endFrameNumber = 30,
            startTimestampMs = 0,
            endTimestampMs = 1000,
            kneeAngle = AngleStats.INVALID,
            hipAngle = AngleStats.INVALID,
            torsoAngle = AngleStats.INVALID,
            ankleAngle = AngleStats.INVALID,
            kneeAngleAtBdc = null,
            kneeAngleAtTdc = null,
            hipAngleAtTdc = null,
            ankleAngleAtBdc = null,
            kopsNormalized = null,
            side = BodySide.LEFT
        )
        val issues = rule.analyze(cycle)
        assertTrue(issues.isEmpty())
    }

    // ==================== CycleSummary Analysis Tests ====================

    @Test
    fun `analyze CycleSummary with optimal torso returns no issues`() {
        val summary = createCycleSummary(averageTorsoAngle = 45f)
        val issues = rule.analyze(summary)
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `analyze CycleSummary with aggressive torso returns issues`() {
        val summary = createCycleSummary(averageTorsoAngle = 22f)
        val issues = rule.analyze(summary)
        assertEquals(1, issues.size)
    }

    // ==================== Shoulder-to-Handlebar Tests ====================

    @Test
    fun `analyze with calibration considers shoulder position`() {
        val calibration = BikeCalibration(
            handlebar = BikeReferencePoint(
                type = BikeReferencePointType.HANDLEBAR,
                x = 0.7f,
                y = 0.3f
            )
        )

        // Shoulder at 0.5, handlebar at 0.7 = 0.2 offset (above threshold)
        val issues = rule.analyze(
            torsoAngle = 45f, // Optimal torso angle
            shoulderX = 0.5f,
            calibration = calibration
        )

        assertEquals(1, issues.size)
        assertEquals(FitIssueType.REACH, issues[0].type)
        assertTrue(issues[0].description.contains("Shoulder"))
    }

    @Test
    fun `analyze with calibration combines both indicators`() {
        val calibration = BikeCalibration(
            handlebar = BikeReferencePoint(
                type = BikeReferencePointType.HANDLEBAR,
                x = 0.7f,
                y = 0.3f
            )
        )

        // Both aggressive torso AND shoulder far behind handlebar
        val issues = rule.analyze(
            torsoAngle = 28f, // Aggressive
            shoulderX = 0.5f, // Far behind handlebar
            calibration = calibration
        )

        assertEquals(1, issues.size)
        assertTrue(issues[0].description.contains("confirms"))
    }

    @Test
    fun `analyze without handlebar calibration uses only torso angle`() {
        val calibration = BikeCalibration.EMPTY

        val issues = rule.analyze(
            torsoAngle = 28f,
            shoulderX = 0.5f,
            calibration = calibration
        )

        assertEquals(1, issues.size)
        assertFalse(issues[0].description.contains("Shoulder"))
    }

    @Test
    fun `shoulder within acceptable offset returns no shoulder issue`() {
        val calibration = BikeCalibration(
            handlebar = BikeReferencePoint(
                type = BikeReferencePointType.HANDLEBAR,
                x = 0.55f, // Close to shoulder
                y = 0.3f
            )
        )

        val issues = rule.analyze(
            torsoAngle = 45f,
            shoulderX = 0.5f, // Only 0.05 offset
            calibration = calibration
        )

        assertTrue(issues.isEmpty())
    }

    // ==================== Static Helper Tests ====================

    @Test
    fun `isOptimalTorsoAngle returns true within range`() {
        assertTrue(ReachRule.isOptimalTorsoAngle(30f))
        assertTrue(ReachRule.isOptimalTorsoAngle(45f))
        assertTrue(ReachRule.isOptimalTorsoAngle(60f))
    }

    @Test
    fun `isOptimalTorsoAngle returns false outside range`() {
        assertFalse(ReachRule.isOptimalTorsoAngle(25f))
        assertFalse(ReachRule.isOptimalTorsoAngle(65f))
    }

    @Test
    fun `isReachTooLong returns true when torso too aggressive`() {
        assertTrue(ReachRule.isReachTooLong(25f))
        assertTrue(ReachRule.isReachTooLong(20f))
    }

    @Test
    fun `isReachTooLong returns false when optimal or upright`() {
        assertFalse(ReachRule.isReachTooLong(30f))
        assertFalse(ReachRule.isReachTooLong(45f))
        assertFalse(ReachRule.isReachTooLong(65f))
    }

    @Test
    fun `isReachTooShort returns true when torso too upright`() {
        assertTrue(ReachRule.isReachTooShort(65f))
        assertTrue(ReachRule.isReachTooShort(75f))
    }

    @Test
    fun `isReachTooShort returns false when optimal or aggressive`() {
        assertFalse(ReachRule.isReachTooShort(60f))
        assertFalse(ReachRule.isReachTooShort(45f))
        assertFalse(ReachRule.isReachTooShort(25f))
    }

    @Test
    fun `static helpers accept custom config`() {
        val config = ReachConfig(minOptimalTorsoAngle = 35f, maxOptimalTorsoAngle = 55f)

        assertTrue(ReachRule.isOptimalTorsoAngle(35f, config))
        assertFalse(ReachRule.isOptimalTorsoAngle(30f, config))
        assertTrue(ReachRule.isReachTooLong(30f, config))
        assertTrue(ReachRule.isReachTooShort(60f, config))
    }

    // ==================== Edge Cases ====================

    @Test
    fun `analyze handles boundary values correctly`() {
        // Exactly at boundaries should be in optimal range
        assertTrue(rule.analyze(30f).isEmpty())
        assertTrue(rule.analyze(60f).isEmpty())

        // Just outside should trigger issues
        assertEquals(1, rule.analyze(29.9f).size)
        assertEquals(1, rule.analyze(60.1f).size)
    }

    @Test
    fun `analyze handles extreme values`() {
        // Very aggressive
        val aggressiveIssues = rule.analyze(5f)
        assertEquals(1, aggressiveIssues.size)
        assertEquals(Severity.HIGH, aggressiveIssues[0].severity)

        // Very upright
        val uprightIssues = rule.analyze(85f)
        assertEquals(1, uprightIssues.size)
        assertEquals(Severity.HIGH, uprightIssues[0].severity)
    }

    // ==================== Helper Functions ====================

    private fun createCycleMetrics(torsoAngle: Float): CycleMetrics {
        return CycleMetrics(
            cycleNumber = 0,
            startFrameNumber = 0,
            endFrameNumber = 30,
            startTimestampMs = 0,
            endTimestampMs = 1000,
            kneeAngle = AngleStats.INVALID,
            hipAngle = AngleStats.INVALID,
            torsoAngle = AngleStats(
                min = torsoAngle - 5,
                max = torsoAngle + 5,
                average = torsoAngle,
                sampleCount = 30
            ),
            ankleAngle = AngleStats.INVALID,
            kneeAngleAtBdc = null,
            kneeAngleAtTdc = null,
            hipAngleAtTdc = null,
            ankleAngleAtBdc = null,
            kopsNormalized = null,
            side = BodySide.LEFT
        )
    }

    private fun createCycleSummary(averageTorsoAngle: Float): CycleSummary {
        return CycleSummary(
            cycleCount = 10,
            averageKneeAngleAtBdc = 150f,
            averageKneeAngleAtTdc = 90f,
            averageKneeAngleRange = 60f,
            averageHipAngleAtTdc = 70f,
            averageTorsoAngle = averageTorsoAngle,
            averageCadenceRpm = 80f,
            kneeAngleAtBdcStats = AngleStats.INVALID,
            kneeAngleAtTdcStats = AngleStats.INVALID,
            hipAngleAtTdcStats = AngleStats.INVALID,
            torsoAngleStats = AngleStats(
                min = averageTorsoAngle - 5,
                max = averageTorsoAngle + 5,
                average = averageTorsoAngle,
                sampleCount = 100
            ),
            averageAnkleAngleAtBdc = null,
            ankleAngleAtBdcStats = AngleStats.INVALID,
            averageKopsNormalized = null,
            kopsNormalizedStats = AngleStats.INVALID,
            side = BodySide.LEFT
        )
    }
}
