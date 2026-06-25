package bikefitstudio.fit

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import bikefitstudio.biomechanics.AngleStats
import bikefitstudio.biomechanics.BodySide
import bikefitstudio.biomechanics.CycleMetrics
import bikefitstudio.biomechanics.CycleSummary
import bikefitstudio.biomechanics.HipRockingResult
import bikefitstudio.biomechanics.TorsoAngleResult

/**
 * Tests for FitEngine.
 */
class FitEngineTest {

    private lateinit var engine: FitEngine

    @Before
    fun setup() {
        engine = FitEngine()
    }

    // ========================================================================
    // Configuration Tests
    // ========================================================================

    @Test
    fun `default configuration enables all rules`() {
        val config = FitEngineConfig()
        assertTrue(config.enableSaddleHeight)
        assertTrue(config.enableSaddleForeAft)
        assertTrue(config.enableReach)
        assertTrue(config.enableHipRocking)
    }

    @Test
    fun `default requires 3 cycles for analysis`() {
        assertEquals(3, FitEngineConfig.MIN_CYCLES_FOR_ANALYSIS)
        assertEquals(3, FitEngineConfig().minCyclesForAnalysis)
    }

    @Test
    fun `custom configuration disables selected rules`() {
        val config = FitEngineConfig(
            enableSaddleHeight = true,
            enableSaddleForeAft = false,
            enableReach = false,
            enableHipRocking = true
        )
        assertTrue(config.enableSaddleHeight)
        assertFalse(config.enableSaddleForeAft)
        assertFalse(config.enableReach)
        assertTrue(config.enableHipRocking)
    }

    @Test
    fun `essential factory creates correct config`() {
        val essential = FitEngine.essential()
        assertNotNull(essential)
    }

    @Test
    fun `quickAnalysis factory requires fewer cycles`() {
        val quick = FitEngine.quickAnalysis()
        assertNotNull(quick)
    }

    // ========================================================================
    // FitAnalysisInput Tests
    // ========================================================================

    @Test
    fun `input has minimum data with 3 cycles`() {
        val input = FitAnalysisInput(
            cycleSummary = createValidCycleSummary(cycleCount = 3)
        )
        assertTrue(input.hasMinimumData())
    }

    @Test
    fun `input lacks minimum data with 2 cycles`() {
        val input = FitAnalysisInput(
            cycleSummary = createValidCycleSummary(cycleCount = 2)
        )
        assertFalse(input.hasMinimumData())
    }

    @Test
    fun `input with custom min cycles threshold`() {
        val input = FitAnalysisInput(
            cycleSummary = createValidCycleSummary(cycleCount = 2)
        )
        assertTrue(input.hasMinimumData(minCycles = 2))
        assertFalse(input.hasMinimumData(minCycles = 3))
    }

    // ========================================================================
    // Empty/Insufficient Data Tests
    // ========================================================================

    @Test
    fun `analyze returns empty result for insufficient cycles`() {
        val input = FitAnalysisInput(
            cycleSummary = createValidCycleSummary(cycleCount = 1)
        )
        val result = engine.analyze(input)
        assertFalse(result.hasIssues())
        assertEquals(1, result.cycleCount)
    }

    @Test
    fun `analyze returns empty result for zero cycles`() {
        val input = FitAnalysisInput(
            cycleSummary = CycleSummary.invalid()
        )
        val result = engine.analyze(input)
        assertFalse(result.hasIssues())
    }

    // ========================================================================
    // Saddle Height Analysis Tests
    // ========================================================================

    @Test
    fun `detects saddle too high from knee angle`() {
        val input = FitAnalysisInput(
            cycleSummary = createCycleSummaryWithKneeAngle(
                kneeAngleAtBdc = 165f, // Way above optimal (145-155)
                cycleCount = 5
            )
        )
        val result = engine.analyze(input)
        assertTrue(result.hasIssues())
        assertTrue(result.issues.any { it.type == FitIssueType.SADDLE_HEIGHT })
    }

    @Test
    fun `detects saddle too low from knee angle`() {
        val input = FitAnalysisInput(
            cycleSummary = createCycleSummaryWithKneeAngle(
                kneeAngleAtBdc = 130f, // Below optimal
                cycleCount = 5
            )
        )
        val result = engine.analyze(input)
        assertTrue(result.hasIssues())
        assertTrue(result.issues.any { it.type == FitIssueType.SADDLE_HEIGHT })
    }

    @Test
    fun `optimal knee angle produces no saddle height issue`() {
        val input = FitAnalysisInput(
            cycleSummary = createCycleSummaryWithKneeAngle(
                kneeAngleAtBdc = 150f, // Within optimal
                cycleCount = 5
            )
        )
        val result = engine.analyze(input)
        assertFalse(result.issues.any { it.type == FitIssueType.SADDLE_HEIGHT })
    }

    @Test
    fun `saddle height with hip rocking escalates severity`() {
        val input = FitAnalysisInput(
            cycleSummary = createCycleSummaryWithKneeAngle(
                kneeAngleAtBdc = 162f,
                cycleCount = 5
            ),
            hipRockingResult = HipRockingResult(
                amplitude = 0.05f, // Excessive
                variance = 0.001f,
                isExcessive = true,
                side = BodySide.LEFT,
                sampleCount = 30,
                minY = 0.4f,
                maxY = 0.45f
            )
        )
        val result = engine.analyze(input)
        assertTrue(result.hasIssues())
        // Should have both saddle height and hip rocking issues
        assertTrue(result.issues.any { it.type == FitIssueType.SADDLE_HEIGHT })
        assertTrue(result.issues.any { it.type == FitIssueType.HIP_ROCKING })
    }

    @Test
    fun `disabled saddle height rule produces no issue`() {
        val customEngine = FitEngine(FitEngineConfig(enableSaddleHeight = false))
        val input = FitAnalysisInput(
            cycleSummary = createCycleSummaryWithKneeAngle(
                kneeAngleAtBdc = 165f,
                cycleCount = 5
            )
        )
        val result = customEngine.analyze(input)
        assertFalse(result.issues.any { it.type == FitIssueType.SADDLE_HEIGHT })
    }

    // ========================================================================
    // Hip Rocking Analysis Tests
    // ========================================================================

    @Test
    fun `detects excessive hip rocking`() {
        val input = FitAnalysisInput(
            cycleSummary = createValidCycleSummary(cycleCount = 5),
            hipRockingResult = HipRockingResult(
                amplitude = 0.05f, // Above threshold
                variance = 0.001f,
                isExcessive = true,
                side = BodySide.LEFT,
                sampleCount = 30,
                minY = 0.4f,
                maxY = 0.45f
            )
        )
        val result = engine.analyze(input)
        assertTrue(result.issues.any { it.type == FitIssueType.HIP_ROCKING })
    }

    @Test
    fun `normal hip motion produces no issue`() {
        val input = FitAnalysisInput(
            cycleSummary = createValidCycleSummary(cycleCount = 5),
            hipRockingResult = HipRockingResult(
                amplitude = 0.02f, // Below threshold
                variance = 0.0001f,
                isExcessive = false,
                side = BodySide.LEFT,
                sampleCount = 30,
                minY = 0.4f,
                maxY = 0.42f
            )
        )
        val result = engine.analyze(input)
        assertFalse(result.issues.any { it.type == FitIssueType.HIP_ROCKING })
    }

    @Test
    fun `hip rocking severity scales with amplitude`() {
        // Low severity
        var input = FitAnalysisInput(
            cycleSummary = createValidCycleSummary(cycleCount = 5),
            hipRockingResult = HipRockingResult(
                amplitude = 0.035f,
                variance = 0.0001f,
                isExcessive = true,
                side = BodySide.LEFT,
                sampleCount = 30,
                minY = 0.4f,
                maxY = 0.435f
            )
        )
        var result = engine.analyze(input)
        assertEquals(Severity.LOW, result.issues.first { it.type == FitIssueType.HIP_ROCKING }.severity)

        // High severity (2x threshold)
        input = FitAnalysisInput(
            cycleSummary = createValidCycleSummary(cycleCount = 5),
            hipRockingResult = HipRockingResult(
                amplitude = 0.07f, // > 2x threshold
                variance = 0.001f,
                isExcessive = true,
                side = BodySide.LEFT,
                sampleCount = 30,
                minY = 0.4f,
                maxY = 0.47f
            )
        )
        result = engine.analyze(input)
        assertEquals(Severity.HIGH, result.issues.first { it.type == FitIssueType.HIP_ROCKING }.severity)
    }

    @Test
    fun `disabled hip rocking rule produces no issue`() {
        val customEngine = FitEngine(FitEngineConfig(enableHipRocking = false))
        val input = FitAnalysisInput(
            cycleSummary = createValidCycleSummary(cycleCount = 5),
            hipRockingResult = HipRockingResult(
                amplitude = 0.05f,
                variance = 0.001f,
                isExcessive = true,
                side = BodySide.LEFT,
                sampleCount = 30,
                minY = 0.4f,
                maxY = 0.45f
            )
        )
        val result = customEngine.analyze(input)
        assertFalse(result.issues.any { it.type == FitIssueType.HIP_ROCKING })
    }

    // ========================================================================
    // Reach Analysis Tests
    // ========================================================================

    @Test
    fun `detects reach too long from aggressive torso angle`() {
        val input = FitAnalysisInput(
            cycleSummary = createCycleSummaryWithTorsoAngle(
                torsoAngle = 20f, // Too aggressive
                cycleCount = 5
            )
        )
        val result = engine.analyze(input)
        assertTrue(result.issues.any { it.type == FitIssueType.REACH })
    }

    @Test
    fun `detects reach too short from upright torso angle`() {
        val input = FitAnalysisInput(
            cycleSummary = createCycleSummaryWithTorsoAngle(
                torsoAngle = 75f, // Too upright
                cycleCount = 5
            )
        )
        val result = engine.analyze(input)
        assertTrue(result.issues.any { it.type == FitIssueType.REACH })
    }

    @Test
    fun `optimal torso angle produces no reach issue`() {
        val input = FitAnalysisInput(
            cycleSummary = createCycleSummaryWithTorsoAngle(
                torsoAngle = 45f, // Optimal
                cycleCount = 5
            )
        )
        val result = engine.analyze(input)
        assertFalse(result.issues.any { it.type == FitIssueType.REACH })
    }

    @Test
    fun `disabled reach rule produces no issue`() {
        val customEngine = FitEngine(FitEngineConfig(enableReach = false))
        val input = FitAnalysisInput(
            cycleSummary = createCycleSummaryWithTorsoAngle(
                torsoAngle = 20f,
                cycleCount = 5
            )
        )
        val result = customEngine.analyze(input)
        assertFalse(result.issues.any { it.type == FitIssueType.REACH })
    }

    @Test
    fun `reach analysis falls back to torso angle results when summary is optimal`() {
        // Create summary with optimal torso angle (won't trigger issue)
        val summary = CycleSummary(
            cycleCount = 5,
            averageKneeAngleAtBdc = 150f,
            averageKneeAngleAtTdc = 80f,
            averageKneeAngleRange = 70f,
            averageHipAngleAtTdc = 100f,
            averageTorsoAngle = 45f, // Optimal, won't trigger from summary
            averageCadenceRpm = 90f,
            kneeAngleAtBdcStats = AngleStats(145f, 155f, 150f, 10),
            kneeAngleAtTdcStats = AngleStats(75f, 85f, 80f, 10),
            hipAngleAtTdcStats = AngleStats(95f, 105f, 100f, 10),
            torsoAngleStats = AngleStats(40f, 50f, 45f, 10),
            averageAnkleAngleAtBdc = null,
            ankleAngleAtBdcStats = AngleStats.INVALID,
            averageKopsNormalized = null,
            kopsNormalizedStats = AngleStats.INVALID,
            side = BodySide.LEFT
        )

        val input = FitAnalysisInput(
            cycleSummary = summary,
            torsoAngleResults = listOf(
                TorsoAngleResult(
                    angle = 20f, // Too aggressive - should trigger from individual result
                    side = BodySide.LEFT,
                    isValid = true,
                    confidence = 1.0f
                )
            )
        )

        // Since summary torso angle is optimal, the engine falls back to individual results
        // Individual result has aggressive torso angle, so reach issue should be detected
        val result = engine.analyze(input)
        assertTrue(result.issues.any { it.type == FitIssueType.REACH })
    }

    // ========================================================================
    // Multiple Issue Detection Tests
    // ========================================================================

    @Test
    fun `detects multiple issues simultaneously`() {
        val input = FitAnalysisInput(
            cycleSummary = CycleSummary(
                cycleCount = 5,
                averageKneeAngleAtBdc = 165f, // Too high
                averageKneeAngleAtTdc = 80f,
                averageKneeAngleRange = 85f,
                averageHipAngleAtTdc = 100f,
                averageTorsoAngle = 20f, // Too aggressive
                averageCadenceRpm = 90f,
                kneeAngleAtBdcStats = AngleStats(160f, 170f, 165f, 10),
                kneeAngleAtTdcStats = AngleStats(75f, 85f, 80f, 10),
                hipAngleAtTdcStats = AngleStats(95f, 105f, 100f, 10),
                torsoAngleStats = AngleStats(15f, 25f, 20f, 10),
                averageAnkleAngleAtBdc = null,
                ankleAngleAtBdcStats = AngleStats.INVALID,
                averageKopsNormalized = null,
                kopsNormalizedStats = AngleStats.INVALID,
                side = BodySide.LEFT
            ),
            hipRockingResult = HipRockingResult(
                amplitude = 0.05f,
                variance = 0.001f,
                isExcessive = true,
                side = BodySide.LEFT,
                sampleCount = 30,
                minY = 0.4f,
                maxY = 0.45f
            )
        )

        val result = engine.analyze(input)
        assertTrue(result.issues.size >= 3)
        assertTrue(result.issues.any { it.type == FitIssueType.SADDLE_HEIGHT })
        assertTrue(result.issues.any { it.type == FitIssueType.REACH })
        assertTrue(result.issues.any { it.type == FitIssueType.HIP_ROCKING })
    }

    @Test
    fun `issues are sorted by severity highest first`() {
        val input = FitAnalysisInput(
            cycleSummary = CycleSummary(
                cycleCount = 5,
                averageKneeAngleAtBdc = 157f, // Just outside optimal, MEDIUM
                averageKneeAngleAtTdc = 80f,
                averageKneeAngleRange = 77f,
                averageHipAngleAtTdc = 100f,
                averageTorsoAngle = 20f, // Very aggressive, HIGH
                averageCadenceRpm = 90f,
                kneeAngleAtBdcStats = AngleStats(155f, 160f, 157f, 10),
                kneeAngleAtTdcStats = AngleStats(75f, 85f, 80f, 10),
                hipAngleAtTdcStats = AngleStats(95f, 105f, 100f, 10),
                torsoAngleStats = AngleStats(15f, 25f, 20f, 10),
                averageAnkleAngleAtBdc = null,
                ankleAngleAtBdcStats = AngleStats.INVALID,
                averageKopsNormalized = null,
                kopsNormalizedStats = AngleStats.INVALID,
                side = BodySide.LEFT
            )
        )

        val result = engine.analyze(input)
        assertTrue(result.issues.size >= 2)
        // First issue should be highest severity
        val severities = result.issues.map { it.severity.ordinal }
        assertEquals(severities.sortedDescending(), severities)
    }

    // ========================================================================
    // Prioritization Tests
    // ========================================================================

    @Test
    fun `prioritizeIssues sorts by severity then type`() {
        val issues = listOf(
            FitIssue(FitIssueType.REACH, Severity.LOW, "reach"),
            FitIssue(FitIssueType.SADDLE_HEIGHT, Severity.HIGH, "saddle"),
            FitIssue(FitIssueType.HIP_ROCKING, Severity.MEDIUM, "hip")
        )

        val prioritized = engine.prioritizeIssues(issues)
        assertEquals(Severity.HIGH, prioritized[0].severity)
        assertEquals(Severity.MEDIUM, prioritized[1].severity)
        assertEquals(Severity.LOW, prioritized[2].severity)
    }

    @Test
    fun `prioritizeIssues sorts same severity by type priority`() {
        val issues = listOf(
            FitIssue(FitIssueType.REACH, Severity.HIGH, "reach"),
            FitIssue(FitIssueType.SADDLE_HEIGHT, Severity.HIGH, "saddle"),
            FitIssue(FitIssueType.HIP_ROCKING, Severity.HIGH, "hip")
        )

        val prioritized = engine.prioritizeIssues(issues)
        // Saddle height should come first (priority 1)
        assertEquals(FitIssueType.SADDLE_HEIGHT, prioritized[0].type)
        assertEquals(FitIssueType.HIP_ROCKING, prioritized[1].type)
        assertEquals(FitIssueType.REACH, prioritized[2].type)
    }

    // ========================================================================
    // Quick Check Tests
    // ========================================================================

    @Test
    fun `hasLikelyIssues returns true for obvious problems`() {
        val summary = createCycleSummaryWithKneeAngle(
            kneeAngleAtBdc = 165f,
            cycleCount = 5
        )
        assertTrue(engine.hasLikelyIssues(summary))
    }

    @Test
    fun `hasLikelyIssues returns false for optimal setup`() {
        val summary = createValidCycleSummary(cycleCount = 5)
        assertFalse(engine.hasLikelyIssues(summary))
    }

    @Test
    fun `hasLikelyIssues returns false for invalid summary`() {
        val summary = CycleSummary.invalid()
        assertFalse(engine.hasLikelyIssues(summary))
    }

    @Test
    fun `hasLikelyIssues detects reach issues`() {
        val summary = createCycleSummaryWithTorsoAngle(
            torsoAngle = 20f,
            cycleCount = 5
        )
        assertTrue(engine.hasLikelyIssues(summary))
    }

    // ========================================================================
    // Single Cycle Analysis Tests
    // ========================================================================

    @Test
    fun `analyzeCycle detects saddle height issue`() {
        val metrics = CycleMetrics(
            cycleNumber = 1,
            startFrameNumber = 0,
            endFrameNumber = 30,
            startTimestampMs = 0,
            endTimestampMs = 1000,
            kneeAngle = AngleStats(140f, 165f, 155f, 30),
            hipAngle = AngleStats(95f, 105f, 100f, 30),
            torsoAngle = AngleStats(40f, 50f, 45f, 30),
            ankleAngle = AngleStats.INVALID,
            kneeAngleAtBdc = 165f, // Too high
            kneeAngleAtTdc = 80f,
            hipAngleAtTdc = null,
            ankleAngleAtBdc = null,
            kopsNormalized = null,
            side = BodySide.LEFT
        )

        val issues = engine.analyzeCycle(metrics)
        assertTrue(issues.any { it.type == FitIssueType.SADDLE_HEIGHT })
    }

    @Test
    fun `analyzeCycle returns empty for optimal cycle`() {
        val metrics = CycleMetrics(
            cycleNumber = 1,
            startFrameNumber = 0,
            endFrameNumber = 30,
            startTimestampMs = 0,
            endTimestampMs = 1000,
            kneeAngle = AngleStats(145f, 155f, 150f, 30),
            hipAngle = AngleStats(95f, 105f, 100f, 30),
            torsoAngle = AngleStats(40f, 50f, 45f, 30),
            ankleAngle = AngleStats.INVALID,
            kneeAngleAtBdc = 150f, // Optimal
            kneeAngleAtTdc = 80f,
            hipAngleAtTdc = null,
            ankleAngleAtBdc = null,
            kopsNormalized = null,
            side = BodySide.LEFT
        )

        val issues = engine.analyzeCycle(metrics)
        assertTrue(issues.isEmpty())
    }

    // ========================================================================
    // Summary Generation Tests
    // ========================================================================

    @Test
    fun `summarize with no issues returns positive message`() {
        val result = FitAnalysisResult.EMPTY
        val summary = engine.summarize(result)
        assertTrue(summary.contains("No significant fit issues"))
    }

    @Test
    fun `summarize includes issue count`() {
        val result = FitAnalysisResult(
            issues = listOf(
                FitIssue(FitIssueType.SADDLE_HEIGHT, Severity.HIGH, "test"),
                FitIssue(FitIssueType.REACH, Severity.LOW, "test2")
            )
        )
        val summary = engine.summarize(result)
        assertTrue(summary.contains("Issues found: 2"))
    }

    @Test
    fun `summarize includes severity and recommendations`() {
        val result = FitAnalysisResult(
            issues = listOf(
                FitIssue(
                    FitIssueType.SADDLE_HEIGHT,
                    Severity.HIGH,
                    "Knee too extended",
                    recommendation = "Lower saddle"
                )
            )
        )
        val summary = engine.summarize(result)
        assertTrue(summary.contains("[HIGH]"))
        assertTrue(summary.contains("Lower saddle"))
    }

    // ========================================================================
    // Factory Method Tests
    // ========================================================================

    @Test
    fun `default factory creates full engine`() {
        val defaultEngine = FitEngine.default()
        assertNotNull(defaultEngine)
    }

    @Test
    fun `essential factory disables non-essential rules`() {
        val essential = FitEngine.essential()
        val input = FitAnalysisInput(
            cycleSummary = createCycleSummaryWithTorsoAngle(
                torsoAngle = 20f, // Should trigger reach issue in full engine
                cycleCount = 5
            )
        )
        val result = essential.analyze(input)
        // Essential engine should NOT detect reach issues
        assertFalse(result.issues.any { it.type == FitIssueType.REACH })
    }

    @Test
    fun `quickAnalysis accepts 2 cycles`() {
        val quick = FitEngine.quickAnalysis()
        val input = FitAnalysisInput(
            cycleSummary = createCycleSummaryWithKneeAngle(
                kneeAngleAtBdc = 165f,
                cycleCount = 2 // Only 2 cycles
            )
        )
        val result = quick.analyze(input)
        // Should still detect issues with only 2 cycles
        assertTrue(result.hasIssues())
    }

    // ========================================================================
    // FitAnalysisResult Tests
    // ========================================================================

    @Test
    fun `result reports highest severity`() {
        val result = FitAnalysisResult(
            issues = listOf(
                FitIssue(FitIssueType.SADDLE_HEIGHT, Severity.LOW, "test"),
                FitIssue(FitIssueType.REACH, Severity.HIGH, "test2"),
                FitIssue(FitIssueType.HIP_ROCKING, Severity.MEDIUM, "test3")
            )
        )
        assertEquals(Severity.HIGH, result.highestSeverity())
    }

    @Test
    fun `result filters issues by type`() {
        val result = FitAnalysisResult(
            issues = listOf(
                FitIssue(FitIssueType.SADDLE_HEIGHT, Severity.LOW, "test1"),
                FitIssue(FitIssueType.SADDLE_HEIGHT, Severity.HIGH, "test2"),
                FitIssue(FitIssueType.REACH, Severity.MEDIUM, "test3")
            )
        )
        assertEquals(2, result.issuesByType(FitIssueType.SADDLE_HEIGHT).size)
        assertEquals(1, result.issuesByType(FitIssueType.REACH).size)
    }

    @Test
    fun `result filters issues by minimum severity`() {
        val result = FitAnalysisResult(
            issues = listOf(
                FitIssue(FitIssueType.SADDLE_HEIGHT, Severity.LOW, "test1"),
                FitIssue(FitIssueType.REACH, Severity.HIGH, "test2"),
                FitIssue(FitIssueType.HIP_ROCKING, Severity.MEDIUM, "test3")
            )
        )
        assertEquals(2, result.issuesWithSeverity(Severity.MEDIUM).size)
        assertEquals(1, result.issuesWithSeverity(Severity.HIGH).size)
    }

    // ========================================================================
    // Helper Functions
    // ========================================================================

    private fun createValidCycleSummary(cycleCount: Int): CycleSummary {
        return CycleSummary(
            cycleCount = cycleCount,
            averageKneeAngleAtBdc = 150f, // Optimal
            averageKneeAngleAtTdc = 80f,
            averageKneeAngleRange = 70f,
            averageHipAngleAtTdc = 100f,
            averageTorsoAngle = 45f, // Optimal
            averageCadenceRpm = 90f,
            kneeAngleAtBdcStats = AngleStats(145f, 155f, 150f, 10),
            kneeAngleAtTdcStats = AngleStats(75f, 85f, 80f, 10),
            hipAngleAtTdcStats = AngleStats(95f, 105f, 100f, 10),
            torsoAngleStats = AngleStats(40f, 50f, 45f, 10),
            averageAnkleAngleAtBdc = null,
            ankleAngleAtBdcStats = AngleStats.INVALID,
            averageKopsNormalized = null,
            kopsNormalizedStats = AngleStats.INVALID,
            side = BodySide.LEFT
        )
    }

    private fun createCycleSummaryWithKneeAngle(
        kneeAngleAtBdc: Float,
        cycleCount: Int
    ): CycleSummary {
        return CycleSummary(
            cycleCount = cycleCount,
            averageKneeAngleAtBdc = kneeAngleAtBdc,
            averageKneeAngleAtTdc = 80f,
            averageKneeAngleRange = kneeAngleAtBdc - 80f,
            averageHipAngleAtTdc = 100f,
            averageTorsoAngle = 45f,
            averageCadenceRpm = 90f,
            kneeAngleAtBdcStats = AngleStats(kneeAngleAtBdc - 5, kneeAngleAtBdc + 5, kneeAngleAtBdc, 10),
            kneeAngleAtTdcStats = AngleStats(75f, 85f, 80f, 10),
            hipAngleAtTdcStats = AngleStats(95f, 105f, 100f, 10),
            torsoAngleStats = AngleStats(40f, 50f, 45f, 10),
            averageAnkleAngleAtBdc = null,
            ankleAngleAtBdcStats = AngleStats.INVALID,
            averageKopsNormalized = null,
            kopsNormalizedStats = AngleStats.INVALID,
            side = BodySide.LEFT
        )
    }

    private fun createCycleSummaryWithTorsoAngle(
        torsoAngle: Float,
        cycleCount: Int
    ): CycleSummary {
        return CycleSummary(
            cycleCount = cycleCount,
            averageKneeAngleAtBdc = 150f,
            averageKneeAngleAtTdc = 80f,
            averageKneeAngleRange = 70f,
            averageHipAngleAtTdc = 100f,
            averageTorsoAngle = torsoAngle,
            averageCadenceRpm = 90f,
            kneeAngleAtBdcStats = AngleStats(145f, 155f, 150f, 10),
            kneeAngleAtTdcStats = AngleStats(75f, 85f, 80f, 10),
            hipAngleAtTdcStats = AngleStats(95f, 105f, 100f, 10),
            torsoAngleStats = AngleStats(torsoAngle - 5, torsoAngle + 5, torsoAngle, 10),
            averageAnkleAngleAtBdc = null,
            ankleAngleAtBdcStats = AngleStats.INVALID,
            averageKopsNormalized = null,
            kopsNormalizedStats = AngleStats.INVALID,
            side = BodySide.LEFT
        )
    }
}
