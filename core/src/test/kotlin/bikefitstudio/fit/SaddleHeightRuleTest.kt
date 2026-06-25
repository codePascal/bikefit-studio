package bikefitstudio.fit

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import bikefitstudio.biomechanics.AngleStats
import bikefitstudio.biomechanics.BodySide
import bikefitstudio.biomechanics.CycleMetrics
import bikefitstudio.biomechanics.CycleSummary
import bikefitstudio.biomechanics.HipRockingResult

class SaddleHeightRuleTest {

    private lateinit var rule: SaddleHeightRule

    @Before
    fun setup() {
        rule = SaddleHeightRule()
    }

    // ==================== Configuration Constants Tests ====================

    @Test
    fun `config has documented threshold constants`() {
        assertEquals(145f, SaddleHeightConfig.MIN_OPTIMAL_KNEE_ANGLE, 0.001f)
        assertEquals(155f, SaddleHeightConfig.MAX_OPTIMAL_KNEE_ANGLE, 0.001f)
        assertEquals(160f, SaddleHeightConfig.TOO_HIGH_KNEE_ANGLE, 0.001f)
        assertEquals(140f, SaddleHeightConfig.TOO_LOW_KNEE_ANGLE, 0.001f)
        assertEquals(0.03f, SaddleHeightConfig.HIP_ROCKING_THRESHOLD, 0.001f)
    }

    @Test
    fun `default config uses constant values`() {
        val config = SaddleHeightConfig()
        assertEquals(SaddleHeightConfig.MIN_OPTIMAL_KNEE_ANGLE, config.minOptimalKneeAngle, 0.001f)
        assertEquals(SaddleHeightConfig.MAX_OPTIMAL_KNEE_ANGLE, config.maxOptimalKneeAngle, 0.001f)
    }

    @Test
    fun `config can be customized`() {
        val config = SaddleHeightConfig(
            minOptimalKneeAngle = 140f,
            maxOptimalKneeAngle = 160f
        )
        assertEquals(140f, config.minOptimalKneeAngle, 0.001f)
        assertEquals(160f, config.maxOptimalKneeAngle, 0.001f)
    }

    // ==================== Optimal Range Tests ====================

    @Test
    fun `knee angle in optimal range returns no issues`() {
        val issue = rule.analyzeKneeAngle(150f)
        assertNull(issue)
    }

    @Test
    fun `knee angle at min optimal boundary returns no issues`() {
        val issue = rule.analyzeKneeAngle(145f)
        assertNull(issue)
    }

    @Test
    fun `knee angle at max optimal boundary returns no issues`() {
        val issue = rule.analyzeKneeAngle(155f)
        assertNull(issue)
    }

    // ==================== Saddle Too High Tests ====================

    @Test
    fun `knee angle above max optimal returns saddle too high issue`() {
        val issue = rule.analyzeKneeAngle(157f)!!

        assertEquals(FitIssueType.SADDLE_HEIGHT, issue.type)
        assertTrue(issue.description.contains("extended"))
        assertEquals(157f, issue.measuredValue)
    }

    @Test
    fun `knee angle slightly above optimal is LOW severity`() {
        val issue = rule.analyzeKneeAngle(157f)!!
        assertEquals(Severity.LOW, issue.severity)
    }

    @Test
    fun `knee angle at too high threshold is HIGH severity`() {
        val issue = rule.analyzeKneeAngle(160f)!!
        assertEquals(Severity.HIGH, issue.severity)
    }

    @Test
    fun `knee angle well above threshold is HIGH severity`() {
        val issue = rule.analyzeKneeAngle(165f)!!
        assertEquals(Severity.HIGH, issue.severity)
    }

    @Test
    fun `saddle too high recommends lowering saddle`() {
        val issue = rule.analyzeKneeAngle(162f)!!
        assertTrue(issue.recommendation?.contains("Lower") == true)
    }

    @Test
    fun `saddle too high has correct optimal range`() {
        val issue = rule.analyzeKneeAngle(162f)!!
        assertEquals(145f..155f, issue.optimalRange)
    }

    // ==================== Saddle Too Low Tests ====================

    @Test
    fun `knee angle below min optimal returns saddle too low issue`() {
        val issue = rule.analyzeKneeAngle(142f)!!

        assertEquals(FitIssueType.SADDLE_HEIGHT, issue.type)
        assertTrue(issue.description.contains("flexed"))
        assertEquals(142f, issue.measuredValue)
    }

    @Test
    fun `knee angle slightly below optimal is MEDIUM severity`() {
        val issue = rule.analyzeKneeAngle(142f)!!
        assertEquals(Severity.MEDIUM, issue.severity)
    }

    @Test
    fun `knee angle at too low threshold is HIGH severity`() {
        val issue = rule.analyzeKneeAngle(140f)!!
        assertEquals(Severity.HIGH, issue.severity)
    }

    @Test
    fun `knee angle well below threshold is HIGH severity`() {
        val issue = rule.analyzeKneeAngle(130f)!!
        assertEquals(Severity.HIGH, issue.severity)
    }

    @Test
    fun `saddle too low recommends raising saddle`() {
        val issue = rule.analyzeKneeAngle(135f)!!
        assertTrue(issue.recommendation?.contains("Raise") == true)
    }

    // ==================== Hip Rocking Correlation Tests ====================

    @Test
    fun `hip rocking escalates saddle too high severity`() {
        val hipRocking = createHipRockingResult(amplitude = 0.05f, isExcessive = true)

        // Without hip rocking: LOW severity
        val issueWithout = rule.analyzeKneeAngle(157f, null)!!
        assertEquals(Severity.LOW, issueWithout.severity)

        // With hip rocking: MEDIUM severity (escalated)
        val issueWith = rule.analyzeKneeAngle(157f, hipRocking)!!
        assertEquals(Severity.MEDIUM, issueWith.severity)
    }

    @Test
    fun `hip rocking mentioned in description when present`() {
        val hipRocking = createHipRockingResult(amplitude = 0.05f, isExcessive = true)
        val issue = rule.analyzeKneeAngle(157f, hipRocking)!!

        assertTrue(issue.description.contains("Hip rocking"))
    }

    @Test
    fun `non-excessive hip rocking does not affect severity`() {
        val hipRocking = createHipRockingResult(amplitude = 0.02f, isExcessive = false)

        val issueWithout = rule.analyzeKneeAngle(157f, null)!!
        val issueWith = rule.analyzeKneeAngle(157f, hipRocking)!!

        assertEquals(issueWithout.severity, issueWith.severity)
    }

    @Test
    fun `hip rocking escalation can be disabled`() {
        val config = SaddleHeightConfig(escalateSeverityWithHipRocking = false)
        val ruleNoEscalation = SaddleHeightRule(config)
        val hipRocking = createHipRockingResult(amplitude = 0.05f, isExcessive = true)

        val issue = ruleNoEscalation.analyzeKneeAngle(157f, hipRocking)!!

        assertEquals(Severity.LOW, issue.severity)
    }

    // ==================== Standalone Hip Rocking Issue Tests ====================

    @Test
    fun `analyze creates hip rocking issue when excessive`() {
        val hipRocking = createHipRockingResult(amplitude = 0.05f, isExcessive = true)
        val cycle = createCycleMetrics(kneeAngleAtBdc = 150f) // Optimal knee angle

        val issues = rule.analyze(cycle, hipRocking)

        val hipIssue = issues.find { it.type == FitIssueType.HIP_ROCKING }
        assertNotNull(hipIssue)
    }

    @Test
    fun `hip rocking issue has correct type and recommendation`() {
        val hipRocking = createHipRockingResult(amplitude = 0.05f, isExcessive = true)
        val cycle = createCycleMetrics(kneeAngleAtBdc = 150f)

        val issues = rule.analyze(cycle, hipRocking)
        val hipIssue = issues.find { it.type == FitIssueType.HIP_ROCKING }!!

        assertTrue(hipIssue.recommendation?.contains("saddle") == true)
        // Fix: Case sensitive check failed because actual text uses "Core"
        assertTrue(hipIssue.recommendation?.contains("Core") == true)
    }

    @Test
    fun `hip rocking severity scales with amplitude`() {
        // Just above threshold -> LOW
        val lowRocking = createHipRockingResult(amplitude = 0.035f, isExcessive = true)
        val cycle = createCycleMetrics(kneeAngleAtBdc = 150f)
        val lowIssues = rule.analyze(cycle, lowRocking)
        assertEquals(Severity.LOW, lowIssues.find { it.type == FitIssueType.HIP_ROCKING }?.severity)

        // 1.5x threshold -> MEDIUM
        val medRocking = createHipRockingResult(amplitude = 0.05f, isExcessive = true)
        val medIssues = rule.analyze(cycle, medRocking)
        assertEquals(Severity.MEDIUM, medIssues.find { it.type == FitIssueType.HIP_ROCKING }?.severity)

        // 2x threshold -> HIGH
        val highRocking = createHipRockingResult(amplitude = 0.07f, isExcessive = true)
        val highIssues = rule.analyze(cycle, highRocking)
        assertEquals(Severity.HIGH, highIssues.find { it.type == FitIssueType.HIP_ROCKING }?.severity)
    }

    // ==================== CycleMetrics Analysis Tests ====================

    @Test
    fun `analyze CycleMetrics with optimal knee angle returns no saddle issue`() {
        val cycle = createCycleMetrics(kneeAngleAtBdc = 150f)
        val issues = rule.analyze(cycle, null)

        assertTrue(issues.none { it.type == FitIssueType.SADDLE_HEIGHT })
    }

    @Test
    fun `analyze CycleMetrics with high knee angle returns saddle issue`() {
        val cycle = createCycleMetrics(kneeAngleAtBdc = 162f)
        val issues = rule.analyze(cycle, null)

        assertTrue(issues.any { it.type == FitIssueType.SADDLE_HEIGHT })
    }

    @Test
    fun `analyze CycleMetrics without BDC angle returns no saddle issue`() {
        val cycle = createCycleMetrics(kneeAngleAtBdc = null)
        val issues = rule.analyze(cycle, null)

        assertTrue(issues.none { it.type == FitIssueType.SADDLE_HEIGHT })
    }

    // ==================== CycleSummary Analysis Tests ====================

    @Test
    fun `analyze CycleSummary uses average BDC angle`() {
        val summary = createCycleSummary(averageKneeAngleAtBdc = 135f)
        val issues = rule.analyze(summary, null)

        val saddleIssue = issues.find { it.type == FitIssueType.SADDLE_HEIGHT }!!
        assertEquals(135f, saddleIssue.measuredValue)
    }

    @Test
    fun `analyze CycleSummary with optimal average returns no issue`() {
        val summary = createCycleSummary(averageKneeAngleAtBdc = 150f)
        val issues = rule.analyze(summary, null)

        assertTrue(issues.none { it.type == FitIssueType.SADDLE_HEIGHT })
    }

    // ==================== Static Helper Tests ====================

    @Test
    fun `isOptimalKneeAngle returns true for optimal range`() {
        assertTrue(SaddleHeightRule.isOptimalKneeAngle(145f))
        assertTrue(SaddleHeightRule.isOptimalKneeAngle(150f))
        assertTrue(SaddleHeightRule.isOptimalKneeAngle(155f))
    }

    @Test
    fun `isOptimalKneeAngle returns false outside range`() {
        assertFalse(SaddleHeightRule.isOptimalKneeAngle(140f))
        assertFalse(SaddleHeightRule.isOptimalKneeAngle(160f))
    }

    @Test
    fun `isSaddleTooHigh returns true above optimal`() {
        assertTrue(SaddleHeightRule.isSaddleTooHigh(156f))
        assertTrue(SaddleHeightRule.isSaddleTooHigh(165f))
    }

    @Test
    fun `isSaddleTooHigh returns false at or below optimal`() {
        assertFalse(SaddleHeightRule.isSaddleTooHigh(155f))
        assertFalse(SaddleHeightRule.isSaddleTooHigh(150f))
    }

    @Test
    fun `isSaddleTooLow returns true below optimal`() {
        assertTrue(SaddleHeightRule.isSaddleTooLow(144f))
        assertTrue(SaddleHeightRule.isSaddleTooLow(130f))
    }

    @Test
    fun `isSaddleTooLow returns false at or above optimal`() {
        assertFalse(SaddleHeightRule.isSaddleTooLow(145f))
        assertFalse(SaddleHeightRule.isSaddleTooLow(150f))
    }

    @Test
    fun `static helpers accept custom config`() {
        val config = SaddleHeightConfig(minOptimalKneeAngle = 140f, maxOptimalKneeAngle = 160f)

        assertTrue(SaddleHeightRule.isOptimalKneeAngle(140f, config))
        assertTrue(SaddleHeightRule.isOptimalKneeAngle(160f, config))
        assertFalse(SaddleHeightRule.isSaddleTooLow(142f, config))
    }

    // ==================== Combined Issue Tests ====================

    @Test
    fun `both saddle height and hip rocking issues can be returned`() {
        val hipRocking = createHipRockingResult(amplitude = 0.05f, isExcessive = true)
        val cycle = createCycleMetrics(kneeAngleAtBdc = 162f)

        val issues = rule.analyze(cycle, hipRocking)

        assertEquals(2, issues.size)
        assertTrue(issues.any { it.type == FitIssueType.SADDLE_HEIGHT })
        assertTrue(issues.any { it.type == FitIssueType.HIP_ROCKING })
    }

    // ==================== Helper Functions ====================

    private fun createHipRockingResult(
        amplitude: Float,
        isExcessive: Boolean
    ): HipRockingResult {
        return HipRockingResult(
            amplitude = amplitude,
            variance = amplitude * amplitude,
            isExcessive = isExcessive,
            side = BodySide.LEFT,
            sampleCount = 100,
            minY = 0.5f - amplitude / 2,
            maxY = 0.5f + amplitude / 2
        )
    }

    private fun createCycleMetrics(
        kneeAngleAtBdc: Float?
    ): CycleMetrics {
        return CycleMetrics(
            cycleNumber = 0,
            startFrameNumber = 0,
            endFrameNumber = 30,
            startTimestampMs = 0,
            endTimestampMs = 1000,
            kneeAngle = AngleStats(
                min = kneeAngleAtBdc ?: 0f,
                max = 90f,
                average = 60f,
                sampleCount = 30
            ),
            hipAngle = AngleStats.INVALID,
            torsoAngle = AngleStats.INVALID,
            ankleAngle = AngleStats.INVALID,
            kneeAngleAtBdc = kneeAngleAtBdc,
            kneeAngleAtTdc = 90f,
            hipAngleAtTdc = null,
            ankleAngleAtBdc = null,
            kopsNormalized = null,
            side = BodySide.LEFT
        )
    }

    private fun createCycleSummary(
        averageKneeAngleAtBdc: Float?
    ): CycleSummary {
        return CycleSummary(
            cycleCount = 10,
            averageKneeAngleAtBdc = averageKneeAngleAtBdc,
            averageKneeAngleAtTdc = 90f,
            averageKneeAngleRange = 60f,
            averageHipAngleAtTdc = 70f,
            averageTorsoAngle = 45f,
            averageCadenceRpm = 80f,
            kneeAngleAtBdcStats = AngleStats.fromValues(
                if (averageKneeAngleAtBdc != null) listOf(averageKneeAngleAtBdc) else emptyList()
            ),
            kneeAngleAtTdcStats = AngleStats.INVALID,
            hipAngleAtTdcStats = AngleStats.INVALID,
            torsoAngleStats = AngleStats.INVALID,
            averageAnkleAngleAtBdc = null,
            ankleAngleAtBdcStats = AngleStats.INVALID,
            averageKopsNormalized = null,
            kopsNormalizedStats = AngleStats.INVALID,
            side = BodySide.LEFT
        )
    }
}
