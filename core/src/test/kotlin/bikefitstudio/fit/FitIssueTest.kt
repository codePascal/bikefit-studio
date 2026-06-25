package bikefitstudio.fit

import org.junit.Assert.*
import org.junit.Test

class FitIssueTest {

    // ==================== Severity Tests ====================

    @Test
    fun `Severity ordinal order is LOW MEDIUM HIGH`() {
        assertTrue(Severity.LOW.ordinal < Severity.MEDIUM.ordinal)
        assertTrue(Severity.MEDIUM.ordinal < Severity.HIGH.ordinal)
    }

    @Test
    fun `Severity isAtLeast returns true for same level`() {
        assertTrue(Severity.LOW.isAtLeast(Severity.LOW))
        assertTrue(Severity.MEDIUM.isAtLeast(Severity.MEDIUM))
        assertTrue(Severity.HIGH.isAtLeast(Severity.HIGH))
    }

    @Test
    fun `Severity isAtLeast returns true for higher level`() {
        assertTrue(Severity.MEDIUM.isAtLeast(Severity.LOW))
        assertTrue(Severity.HIGH.isAtLeast(Severity.LOW))
        assertTrue(Severity.HIGH.isAtLeast(Severity.MEDIUM))
    }

    @Test
    fun `Severity isAtLeast returns false for lower level`() {
        assertFalse(Severity.LOW.isAtLeast(Severity.MEDIUM))
        assertFalse(Severity.LOW.isAtLeast(Severity.HIGH))
        assertFalse(Severity.MEDIUM.isAtLeast(Severity.HIGH))
    }

    @Test
    fun `Severity highest returns highest from collection`() {
        val severities = listOf(Severity.LOW, Severity.HIGH, Severity.MEDIUM)
        assertEquals(Severity.HIGH, Severity.highest(severities))
    }

    @Test
    fun `Severity highest returns null for empty collection`() {
        assertNull(Severity.highest(emptyList()))
    }

    @Test
    fun `Severity highest with single element returns that element`() {
        assertEquals(Severity.MEDIUM, Severity.highest(listOf(Severity.MEDIUM)))
    }

    // ==================== FitIssueType Tests ====================

    @Test
    fun `FitIssueType has all required types`() {
        assertNotNull(FitIssueType.SADDLE_HEIGHT)
        assertNotNull(FitIssueType.SADDLE_FORE_AFT)
        assertNotNull(FitIssueType.REACH)
    }

    @Test
    fun `FitIssueType getDescription returns non-empty string`() {
        for (type in FitIssueType.values()) {
            assertTrue(type.getDescription().isNotEmpty())
        }
    }

    @Test
    fun `FitIssueType has additional useful types`() {
        assertNotNull(FitIssueType.HANDLEBAR_HEIGHT)
        assertNotNull(FitIssueType.CLEAT_POSITION)
        assertNotNull(FitIssueType.CRANK_LENGTH)
        assertNotNull(FitIssueType.HIP_ROCKING)
    }

    // ==================== FitIssue Data Class Tests ====================

    @Test
    fun `FitIssue can be created with required fields`() {
        val issue = FitIssue(
            type = FitIssueType.SADDLE_HEIGHT,
            severity = Severity.MEDIUM,
            description = "Saddle too high"
        )

        assertEquals(FitIssueType.SADDLE_HEIGHT, issue.type)
        assertEquals(Severity.MEDIUM, issue.severity)
        assertEquals("Saddle too high", issue.description)
    }

    @Test
    fun `FitIssue optional fields default to null`() {
        val issue = FitIssue(
            type = FitIssueType.REACH,
            severity = Severity.LOW,
            description = "Minor reach issue"
        )

        assertNull(issue.measuredValue)
        assertNull(issue.optimalRange)
        assertNull(issue.recommendation)
    }

    @Test
    fun `FitIssue can include measured value and optimal range`() {
        val issue = FitIssue(
            type = FitIssueType.SADDLE_HEIGHT,
            severity = Severity.HIGH,
            description = "Knee over-extended",
            measuredValue = 20f,
            optimalRange = 25f..35f,
            recommendation = "Lower saddle"
        )

        assertEquals(20f, issue.measuredValue)
        assertEquals(25f..35f, issue.optimalRange)
        assertEquals("Lower saddle", issue.recommendation)
    }

    @Test
    fun `FitIssue isOutOfRange true when below range`() {
        val issue = FitIssue(
            type = FitIssueType.SADDLE_HEIGHT,
            severity = Severity.HIGH,
            description = "Test",
            measuredValue = 20f,
            optimalRange = 25f..35f
        )

        assertTrue(issue.isOutOfRange())
    }

    @Test
    fun `FitIssue isOutOfRange true when above range`() {
        val issue = FitIssue(
            type = FitIssueType.SADDLE_HEIGHT,
            severity = Severity.MEDIUM,
            description = "Test",
            measuredValue = 40f,
            optimalRange = 25f..35f
        )

        assertTrue(issue.isOutOfRange())
    }

    @Test
    fun `FitIssue isOutOfRange false when within range`() {
        val issue = FitIssue(
            type = FitIssueType.SADDLE_HEIGHT,
            severity = Severity.LOW,
            description = "Test",
            measuredValue = 30f,
            optimalRange = 25f..35f
        )

        assertFalse(issue.isOutOfRange())
    }

    @Test
    fun `FitIssue isOutOfRange false when values null`() {
        val issue = FitIssue(
            type = FitIssueType.REACH,
            severity = Severity.LOW,
            description = "Test"
        )

        assertFalse(issue.isOutOfRange())
    }

    @Test
    fun `FitIssue deviationFromOptimal negative when below`() {
        val issue = FitIssue(
            type = FitIssueType.SADDLE_HEIGHT,
            severity = Severity.HIGH,
            description = "Test",
            measuredValue = 20f,
            optimalRange = 25f..35f
        )

        assertEquals(-5f, issue.deviationFromOptimal()!!, 0.001f)
    }

    @Test
    fun `FitIssue deviationFromOptimal positive when above`() {
        val issue = FitIssue(
            type = FitIssueType.SADDLE_HEIGHT,
            severity = Severity.MEDIUM,
            description = "Test",
            measuredValue = 40f,
            optimalRange = 25f..35f
        )

        assertEquals(5f, issue.deviationFromOptimal()!!, 0.001f)
    }

    @Test
    fun `FitIssue deviationFromOptimal zero when within`() {
        val issue = FitIssue(
            type = FitIssueType.SADDLE_HEIGHT,
            severity = Severity.LOW,
            description = "Test",
            measuredValue = 30f,
            optimalRange = 25f..35f
        )

        assertEquals(0f, issue.deviationFromOptimal()!!, 0.001f)
    }

    // ==================== FitIssue Factory Methods Tests ====================

    @Test
    fun `saddleHeightFromKneeAngle returns null when within range`() {
        val issue = FitIssue.saddleHeightFromKneeAngle(30f)
        assertNull(issue)
    }

    @Test
    fun `saddleHeightFromKneeAngle returns issue when too low`() {
        val issue = FitIssue.saddleHeightFromKneeAngle(20f)!!

        assertEquals(FitIssueType.SADDLE_HEIGHT, issue.type)
        assertEquals("Lower saddle height", issue.recommendation)
        assertEquals(20f, issue.measuredValue)
    }

    @Test
    fun `saddleHeightFromKneeAngle returns HIGH severity when very low`() {
        val issue = FitIssue.saddleHeightFromKneeAngle(15f)!!
        assertEquals(Severity.HIGH, issue.severity)
    }

    @Test
    fun `saddleHeightFromKneeAngle returns MEDIUM severity when slightly low`() {
        val issue = FitIssue.saddleHeightFromKneeAngle(22f)!!
        assertEquals(Severity.MEDIUM, issue.severity)
    }

    @Test
    fun `saddleHeightFromKneeAngle returns issue when too high`() {
        val issue = FitIssue.saddleHeightFromKneeAngle(40f)!!

        assertEquals(FitIssueType.SADDLE_HEIGHT, issue.type)
        assertEquals("Raise saddle height", issue.recommendation)
    }

    @Test
    fun `saddleHeightFromKneeAngle accepts custom range`() {
        // Custom range 20-30
        val issueWithin = FitIssue.saddleHeightFromKneeAngle(25f, 20f, 30f)
        assertNull(issueWithin)

        val issueBelow = FitIssue.saddleHeightFromKneeAngle(18f, 20f, 30f)
        assertNotNull(issueBelow)
    }

    @Test
    fun `hipRocking returns null when below threshold`() {
        val issue = FitIssue.hipRocking(0.02f)
        assertNull(issue)
    }

    @Test
    fun `hipRocking returns issue when above threshold`() {
        val issue = FitIssue.hipRocking(0.05f)!!

        assertEquals(FitIssueType.HIP_ROCKING, issue.type)
        assertEquals(0.05f, issue.measuredValue)
    }

    @Test
    fun `hipRocking severity scales with amplitude`() {
        val lowIssue = FitIssue.hipRocking(0.035f)!! // Just above threshold
        assertEquals(Severity.LOW, lowIssue.severity)

        val mediumIssue = FitIssue.hipRocking(0.05f)!! // 1.5x+ threshold
        assertEquals(Severity.MEDIUM, mediumIssue.severity)

        val highIssue = FitIssue.hipRocking(0.07f)!! // 2x+ threshold
        assertEquals(Severity.HIGH, highIssue.severity)
    }

    // ==================== FitAnalysisResult Tests ====================

    @Test
    fun `FitAnalysisResult EMPTY has no issues`() {
        assertFalse(FitAnalysisResult.EMPTY.hasIssues())
        assertEquals(0, FitAnalysisResult.EMPTY.issues.size)
    }

    @Test
    fun `FitAnalysisResult hasIssues true when issues exist`() {
        val result = FitAnalysisResult(
            issues = listOf(
                FitIssue(FitIssueType.SADDLE_HEIGHT, Severity.MEDIUM, "Test")
            )
        )

        assertTrue(result.hasIssues())
    }

    @Test
    fun `FitAnalysisResult highestSeverity returns highest`() {
        val result = FitAnalysisResult(
            issues = listOf(
                FitIssue(FitIssueType.SADDLE_HEIGHT, Severity.LOW, "Test"),
                FitIssue(FitIssueType.REACH, Severity.HIGH, "Test"),
                FitIssue(FitIssueType.HIP_ROCKING, Severity.MEDIUM, "Test")
            )
        )

        assertEquals(Severity.HIGH, result.highestSeverity())
    }

    @Test
    fun `FitAnalysisResult highestSeverity null when empty`() {
        assertNull(FitAnalysisResult.EMPTY.highestSeverity())
    }

    @Test
    fun `FitAnalysisResult issuesByType filters correctly`() {
        val result = FitAnalysisResult(
            issues = listOf(
                FitIssue(FitIssueType.SADDLE_HEIGHT, Severity.LOW, "Test1"),
                FitIssue(FitIssueType.REACH, Severity.HIGH, "Test2"),
                FitIssue(FitIssueType.SADDLE_HEIGHT, Severity.MEDIUM, "Test3")
            )
        )

        val saddleIssues = result.issuesByType(FitIssueType.SADDLE_HEIGHT)
        assertEquals(2, saddleIssues.size)
    }

    @Test
    fun `FitAnalysisResult issuesWithSeverity filters correctly`() {
        val result = FitAnalysisResult(
            issues = listOf(
                FitIssue(FitIssueType.SADDLE_HEIGHT, Severity.LOW, "Test1"),
                FitIssue(FitIssueType.REACH, Severity.HIGH, "Test2"),
                FitIssue(FitIssueType.HIP_ROCKING, Severity.MEDIUM, "Test3")
            )
        )

        val mediumAndHigher = result.issuesWithSeverity(Severity.MEDIUM)
        assertEquals(2, mediumAndHigher.size)
    }

    @Test
    fun `FitAnalysisResult hasHighSeverityIssues true when HIGH exists`() {
        val result = FitAnalysisResult(
            issues = listOf(
                FitIssue(FitIssueType.REACH, Severity.HIGH, "Test")
            )
        )

        assertTrue(result.hasHighSeverityIssues())
    }

    @Test
    fun `FitAnalysisResult hasHighSeverityIssues false when no HIGH`() {
        val result = FitAnalysisResult(
            issues = listOf(
                FitIssue(FitIssueType.REACH, Severity.MEDIUM, "Test")
            )
        )

        assertFalse(result.hasHighSeverityIssues())
    }

    @Test
    fun `FitAnalysisResult stores cycle count`() {
        val result = FitAnalysisResult(
            issues = emptyList(),
            cycleCount = 25
        )

        assertEquals(25, result.cycleCount)
    }
}
