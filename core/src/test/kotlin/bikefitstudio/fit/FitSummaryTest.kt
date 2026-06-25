package bikefitstudio.fit

import org.junit.Assert.*
import org.junit.Test

class FitSummaryTest {

    // ==================== FitCategory Tests ====================

    @Test
    fun `FitCategory fromIssueType maps SADDLE_HEIGHT to SADDLE`() {
        assertEquals(FitCategory.SADDLE, FitCategory.fromIssueType(FitIssueType.SADDLE_HEIGHT))
    }

    @Test
    fun `FitCategory fromIssueType maps SADDLE_FORE_AFT to SADDLE`() {
        assertEquals(FitCategory.SADDLE, FitCategory.fromIssueType(FitIssueType.SADDLE_FORE_AFT))
    }

    @Test
    fun `FitCategory fromIssueType maps REACH to COCKPIT`() {
        assertEquals(FitCategory.COCKPIT, FitCategory.fromIssueType(FitIssueType.REACH))
    }

    @Test
    fun `FitCategory fromIssueType maps HANDLEBAR_HEIGHT to COCKPIT`() {
        assertEquals(FitCategory.COCKPIT, FitCategory.fromIssueType(FitIssueType.HANDLEBAR_HEIGHT))
    }

    @Test
    fun `FitCategory fromIssueType maps CLEAT_POSITION to PEDALING`() {
        assertEquals(FitCategory.PEDALING, FitCategory.fromIssueType(FitIssueType.CLEAT_POSITION))
    }

    @Test
    fun `FitCategory fromIssueType maps CRANK_LENGTH to PEDALING`() {
        assertEquals(FitCategory.PEDALING, FitCategory.fromIssueType(FitIssueType.CRANK_LENGTH))
    }

    @Test
    fun `FitCategory fromIssueType maps HIP_ROCKING to STABILITY`() {
        assertEquals(FitCategory.STABILITY, FitCategory.fromIssueType(FitIssueType.HIP_ROCKING))
    }

    @Test
    fun `FitCategory displayName returns non-empty string for all categories`() {
        for (category in FitCategory.values()) {
            assertTrue(category.displayName().isNotEmpty())
        }
    }

    // ==================== FitGrade Tests ====================

    @Test
    fun `FitGrade ordinal order is EXCELLENT GOOD FAIR POOR`() {
        assertTrue(FitGrade.EXCELLENT.ordinal < FitGrade.GOOD.ordinal)
        assertTrue(FitGrade.GOOD.ordinal < FitGrade.FAIR.ordinal)
        assertTrue(FitGrade.FAIR.ordinal < FitGrade.POOR.ordinal)
    }

    @Test
    fun `FitGrade displayText returns non-empty string for all grades`() {
        for (grade in FitGrade.values()) {
            assertTrue(grade.displayText().isNotEmpty())
        }
    }

    @Test
    fun `FitGrade emoji returns non-empty string for all grades`() {
        for (grade in FitGrade.values()) {
            assertTrue(grade.emoji().isNotEmpty())
        }
    }

    // ==================== FitRecommendation Tests ====================

    @Test
    fun `FitRecommendation isHighPriority returns true for priority 1 HIGH severity`() {
        val rec = createRecommendation(priority = 1, severity = Severity.HIGH)
        assertTrue(rec.isHighPriority())
    }

    @Test
    fun `FitRecommendation isHighPriority returns true for priority 2 HIGH severity`() {
        val rec = createRecommendation(priority = 2, severity = Severity.HIGH)
        assertTrue(rec.isHighPriority())
    }

    @Test
    fun `FitRecommendation isHighPriority returns false for priority 3 HIGH severity`() {
        val rec = createRecommendation(priority = 3, severity = Severity.HIGH)
        assertFalse(rec.isHighPriority())
    }

    @Test
    fun `FitRecommendation isHighPriority returns false for priority 1 MEDIUM severity`() {
        val rec = createRecommendation(priority = 1, severity = Severity.MEDIUM)
        assertFalse(rec.isHighPriority())
    }

    // ==================== FitSummary Creation Tests ====================

    @Test
    fun `FitSummary fromAnalysisResult with empty issues returns EXCELLENT grade`() {
        val result = FitAnalysisResult(issues = emptyList(), cycleCount = 5)
        val summary = FitSummary.fromAnalysisResult(result)
        
        assertEquals(FitGrade.EXCELLENT, summary.grade)
        assertEquals(0, summary.totalIssueCount)
        assertEquals(0, summary.highSeverityCount)
        assertTrue(summary.recommendations.isEmpty())
        assertTrue(summary.isOptimal())
    }

    @Test
    fun `FitSummary fromAnalysisResult preserves cycle count`() {
        val result = FitAnalysisResult(issues = emptyList(), cycleCount = 7)
        val summary = FitSummary.fromAnalysisResult(result)
        
        assertEquals(7, summary.cycleCount)
    }

    @Test
    fun `FitSummary fromAnalysisResult with single LOW issue returns GOOD grade`() {
        val issue = createIssue(severity = Severity.LOW)
        val result = FitAnalysisResult(issues = listOf(issue), cycleCount = 5)
        val summary = FitSummary.fromAnalysisResult(result)
        
        assertEquals(FitGrade.GOOD, summary.grade)
        assertEquals(1, summary.totalIssueCount)
        assertEquals(0, summary.highSeverityCount)
    }

    @Test
    fun `FitSummary fromAnalysisResult with single MEDIUM issue returns GOOD grade`() {
        val issue = createIssue(severity = Severity.MEDIUM)
        val result = FitAnalysisResult(issues = listOf(issue), cycleCount = 5)
        val summary = FitSummary.fromAnalysisResult(result)
        
        assertEquals(FitGrade.GOOD, summary.grade)
    }

    @Test
    fun `FitSummary fromAnalysisResult with two MEDIUM issues returns FAIR grade`() {
        val issues = listOf(
            createIssue(type = FitIssueType.SADDLE_HEIGHT, severity = Severity.MEDIUM),
            createIssue(type = FitIssueType.REACH, severity = Severity.MEDIUM)
        )
        val result = FitAnalysisResult(issues = issues, cycleCount = 5)
        val summary = FitSummary.fromAnalysisResult(result)
        
        assertEquals(FitGrade.FAIR, summary.grade)
    }

    @Test
    fun `FitSummary fromAnalysisResult with single HIGH issue returns FAIR grade`() {
        val issue = createIssue(severity = Severity.HIGH)
        val result = FitAnalysisResult(issues = listOf(issue), cycleCount = 5)
        val summary = FitSummary.fromAnalysisResult(result)
        
        assertEquals(FitGrade.FAIR, summary.grade)
        assertEquals(1, summary.highSeverityCount)
    }

    @Test
    fun `FitSummary fromAnalysisResult with two HIGH issues returns POOR grade`() {
        val issues = listOf(
            createIssue(type = FitIssueType.SADDLE_HEIGHT, severity = Severity.HIGH),
            createIssue(type = FitIssueType.REACH, severity = Severity.HIGH)
        )
        val result = FitAnalysisResult(issues = issues, cycleCount = 5)
        val summary = FitSummary.fromAnalysisResult(result)
        
        assertEquals(FitGrade.POOR, summary.grade)
        assertEquals(2, summary.highSeverityCount)
    }

    // ==================== Issue Grouping Tests ====================

    @Test
    fun `FitSummary groups issues by category correctly`() {
        val issues = listOf(
            createIssue(type = FitIssueType.SADDLE_HEIGHT),
            createIssue(type = FitIssueType.SADDLE_FORE_AFT),
            createIssue(type = FitIssueType.REACH)
        )
        val result = FitAnalysisResult(issues = issues, cycleCount = 5)
        val summary = FitSummary.fromAnalysisResult(result)
        
        assertEquals(2, summary.issuesInCategory(FitCategory.SADDLE).size)
        assertEquals(1, summary.issuesInCategory(FitCategory.COCKPIT).size)
        assertEquals(0, summary.issuesInCategory(FitCategory.PEDALING).size)
    }

    @Test
    fun `FitSummary issuesInCategory returns empty list for missing category`() {
        val issue = createIssue(type = FitIssueType.SADDLE_HEIGHT)
        val result = FitAnalysisResult(issues = listOf(issue), cycleCount = 5)
        val summary = FitSummary.fromAnalysisResult(result)
        
        assertTrue(summary.issuesInCategory(FitCategory.PEDALING).isEmpty())
    }

    // ==================== Deduplication Tests ====================

    @Test
    fun `deduplicateIssues returns empty list for empty input`() {
        val result = FitSummary.deduplicateIssues(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `deduplicateIssues keeps single issue unchanged`() {
        val issue = createIssue(type = FitIssueType.SADDLE_HEIGHT, severity = Severity.MEDIUM)
        val result = FitSummary.deduplicateIssues(listOf(issue))
        
        assertEquals(1, result.size)
        assertEquals(issue, result.first())
    }

    @Test
    fun `deduplicateIssues removes duplicate issues of same type keeping most severe`() {
        val lowIssue = createIssue(type = FitIssueType.SADDLE_HEIGHT, severity = Severity.LOW)
        val highIssue = createIssue(type = FitIssueType.SADDLE_HEIGHT, severity = Severity.HIGH)
        
        val result = FitSummary.deduplicateIssues(listOf(lowIssue, highIssue))
        
        assertEquals(1, result.size)
        assertEquals(Severity.HIGH, result.first().severity)
    }

    @Test
    fun `deduplicateIssues keeps different issue types`() {
        val saddleIssue = createIssue(type = FitIssueType.SADDLE_HEIGHT)
        val reachIssue = createIssue(type = FitIssueType.REACH)
        
        val result = FitSummary.deduplicateIssues(listOf(saddleIssue, reachIssue))
        
        assertEquals(2, result.size)
    }

    @Test
    fun `deduplicateIssues handles conflicting recommendations by deviation`() {
        // Issue with larger deviation from optimal should be kept
        val tooLowIssue = createIssue(
            type = FitIssueType.SADDLE_HEIGHT,
            severity = Severity.MEDIUM,
            measuredValue = 40f, // 5 degrees above optimal (35)
            optimalRange = 25f..35f,
            recommendation = "Raise saddle"
        )
        val tooHighIssue = createIssue(
            type = FitIssueType.SADDLE_HEIGHT,
            severity = Severity.MEDIUM,
            measuredValue = 20f, // 5 degrees below optimal (25)
            optimalRange = 25f..35f,
            recommendation = "Lower saddle"
        )
        
        val result = FitSummary.deduplicateIssues(listOf(tooLowIssue, tooHighIssue))
        
        assertEquals(1, result.size)
        // Both have same deviation, so first one processed is kept
    }

    // ==================== Sorting Tests ====================

    @Test
    fun `sortByPriority puts HIGH severity first`() {
        val low = createIssue(type = FitIssueType.REACH, severity = Severity.LOW)
        val high = createIssue(type = FitIssueType.SADDLE_HEIGHT, severity = Severity.HIGH)
        val medium = createIssue(type = FitIssueType.SADDLE_FORE_AFT, severity = Severity.MEDIUM)
        
        val result = FitSummary.sortByPriority(listOf(low, high, medium))
        
        assertEquals(Severity.HIGH, result[0].severity)
        assertEquals(Severity.MEDIUM, result[1].severity)
        assertEquals(Severity.LOW, result[2].severity)
    }

    @Test
    fun `sortByPriority sorts by type priority for same severity`() {
        val reach = createIssue(type = FitIssueType.REACH, severity = Severity.HIGH)
        val saddle = createIssue(type = FitIssueType.SADDLE_HEIGHT, severity = Severity.HIGH)
        val foreAft = createIssue(type = FitIssueType.SADDLE_FORE_AFT, severity = Severity.HIGH)
        
        val result = FitSummary.sortByPriority(listOf(reach, saddle, foreAft))
        
        assertEquals(FitIssueType.SADDLE_HEIGHT, result[0].type)
        assertEquals(FitIssueType.SADDLE_FORE_AFT, result[1].type)
        assertEquals(FitIssueType.REACH, result[2].type)
    }

    // ==================== Grade Calculation Tests ====================

    @Test
    fun `calculateGrade returns EXCELLENT for empty list`() {
        assertEquals(FitGrade.EXCELLENT, FitSummary.calculateGrade(emptyList()))
    }

    @Test
    fun `calculateGrade returns GOOD for single LOW issue`() {
        val issues = listOf(createIssue(severity = Severity.LOW))
        assertEquals(FitGrade.GOOD, FitSummary.calculateGrade(issues))
    }

    @Test
    fun `calculateGrade returns GOOD for single MEDIUM issue`() {
        val issues = listOf(createIssue(severity = Severity.MEDIUM))
        assertEquals(FitGrade.GOOD, FitSummary.calculateGrade(issues))
    }

    @Test
    fun `calculateGrade returns FAIR for two MEDIUM issues`() {
        val issues = listOf(
            createIssue(type = FitIssueType.SADDLE_HEIGHT, severity = Severity.MEDIUM),
            createIssue(type = FitIssueType.REACH, severity = Severity.MEDIUM)
        )
        assertEquals(FitGrade.FAIR, FitSummary.calculateGrade(issues))
    }

    @Test
    fun `calculateGrade returns FAIR for single HIGH issue`() {
        val issues = listOf(createIssue(severity = Severity.HIGH))
        assertEquals(FitGrade.FAIR, FitSummary.calculateGrade(issues))
    }

    @Test
    fun `calculateGrade returns POOR for two HIGH issues`() {
        val issues = listOf(
            createIssue(type = FitIssueType.SADDLE_HEIGHT, severity = Severity.HIGH),
            createIssue(type = FitIssueType.REACH, severity = Severity.HIGH)
        )
        assertEquals(FitGrade.POOR, FitSummary.calculateGrade(issues))
    }

    // ==================== Recommendation Tests ====================

    @Test
    fun `recommendations are created for each issue`() {
        val issues = listOf(
            createIssue(type = FitIssueType.SADDLE_HEIGHT),
            createIssue(type = FitIssueType.REACH)
        )
        val result = FitAnalysisResult(issues = issues, cycleCount = 5)
        val summary = FitSummary.fromAnalysisResult(result)
        
        assertEquals(2, summary.recommendations.size)
    }

    @Test
    fun `recommendations have sequential priorities`() {
        val issues = listOf(
            createIssue(type = FitIssueType.SADDLE_HEIGHT, severity = Severity.HIGH),
            createIssue(type = FitIssueType.REACH, severity = Severity.MEDIUM),
            createIssue(type = FitIssueType.SADDLE_FORE_AFT, severity = Severity.LOW)
        )
        val result = FitAnalysisResult(issues = issues, cycleCount = 5)
        val summary = FitSummary.fromAnalysisResult(result)
        
        assertEquals(1, summary.recommendations[0].priority)
        assertEquals(2, summary.recommendations[1].priority)
        assertEquals(3, summary.recommendations[2].priority)
    }

    @Test
    fun `topRecommendations returns requested count`() {
        val issues = listOf(
            createIssue(type = FitIssueType.SADDLE_HEIGHT),
            createIssue(type = FitIssueType.REACH),
            createIssue(type = FitIssueType.SADDLE_FORE_AFT)
        )
        val result = FitAnalysisResult(issues = issues, cycleCount = 5)
        val summary = FitSummary.fromAnalysisResult(result)
        
        assertEquals(2, summary.topRecommendations(2).size)
    }

    @Test
    fun `recommendationsWithSeverity filters correctly`() {
        val issues = listOf(
            createIssue(type = FitIssueType.SADDLE_HEIGHT, severity = Severity.HIGH),
            createIssue(type = FitIssueType.REACH, severity = Severity.LOW)
        )
        val result = FitAnalysisResult(issues = issues, cycleCount = 5)
        val summary = FitSummary.fromAnalysisResult(result)
        
        assertEquals(1, summary.recommendationsWithSeverity(Severity.HIGH).size)
        assertEquals(2, summary.recommendationsWithSeverity(Severity.LOW).size)
    }

    // ==================== Summary Text Tests ====================

    @Test
    fun `briefSummary for optimal fit mentions no adjustments`() {
        val summary = FitSummary.optimal(5)
        assertTrue(summary.briefSummary().contains("great") || summary.briefSummary().contains("No"))
    }

    @Test
    fun `briefSummary for issues mentions issue count`() {
        val issue = createIssue(severity = Severity.HIGH)
        val result = FitAnalysisResult(issues = listOf(issue), cycleCount = 5)
        val summary = FitSummary.fromAnalysisResult(result)
        
        assertTrue(summary.briefSummary().contains("1"))
    }

    @Test
    fun `needsAttention returns false for EXCELLENT grade`() {
        val summary = FitSummary.optimal()
        assertFalse(summary.needsAttention())
    }

    @Test
    fun `needsAttention returns false for GOOD grade`() {
        val issue = createIssue(severity = Severity.LOW)
        val result = FitAnalysisResult(issues = listOf(issue), cycleCount = 5)
        val summary = FitSummary.fromAnalysisResult(result)
        
        assertFalse(summary.needsAttention())
    }

    @Test
    fun `needsAttention returns true for FAIR grade`() {
        val issue = createIssue(severity = Severity.HIGH)
        val result = FitAnalysisResult(issues = listOf(issue), cycleCount = 5)
        val summary = FitSummary.fromAnalysisResult(result)
        
        assertTrue(summary.needsAttention())
    }

    @Test
    fun `needsAttention returns true for POOR grade`() {
        val issues = listOf(
            createIssue(type = FitIssueType.SADDLE_HEIGHT, severity = Severity.HIGH),
            createIssue(type = FitIssueType.REACH, severity = Severity.HIGH)
        )
        val result = FitAnalysisResult(issues = issues, cycleCount = 5)
        val summary = FitSummary.fromAnalysisResult(result)
        
        assertTrue(summary.needsAttention())
    }

    // ==================== Factory Method Tests ====================

    @Test
    fun `optimal factory method creates correct summary`() {
        val summary = FitSummary.optimal(10)
        
        assertEquals(FitGrade.EXCELLENT, summary.grade)
        assertEquals(0, summary.totalIssueCount)
        assertEquals(10, summary.cycleCount)
        assertTrue(summary.isOptimal())
    }

    // ==================== Helper Methods ====================

    private fun createIssue(
        type: FitIssueType = FitIssueType.SADDLE_HEIGHT,
        severity: Severity = Severity.MEDIUM,
        description: String = "Test issue",
        measuredValue: Float? = null,
        optimalRange: ClosedFloatingPointRange<Float>? = null,
        recommendation: String? = "Test recommendation"
    ): FitIssue {
        return FitIssue(
            type = type,
            severity = severity,
            description = description,
            measuredValue = measuredValue,
            optimalRange = optimalRange,
            recommendation = recommendation
        )
    }

    private fun createRecommendation(
        priority: Int = 1,
        severity: Severity = Severity.MEDIUM,
        category: FitCategory = FitCategory.SADDLE
    ): FitRecommendation {
        return FitRecommendation(
            priority = priority,
            category = category,
            severity = severity,
            title = "Test recommendation",
            description = "Test description",
            action = "Test action",
            relatedIssues = emptyList()
        )
    }
}
