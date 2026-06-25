package bikefitstudio.fit

import bikefitstudio.biomechanics.CycleSummary

/**
 * Represents a category of bike fit issues for grouping in UI.
 */
enum class FitCategory {
    /**
     * Issues related to saddle position (height, fore/aft).
     */
    SADDLE,

    /**
     * Issues related to handlebar/cockpit setup (reach, height).
     */
    COCKPIT,

    /**
     * Issues related to pedaling mechanics (cleats, cranks).
     */
    PEDALING,

    /**
     * Issues related to riding dynamics/stability.
     */
    STABILITY;

    /**
     * Returns a user-friendly display name.
     */
    fun displayName(): String = when (this) {
        SADDLE -> "Saddle Position"
        COCKPIT -> "Handlebar & Reach"
        PEDALING -> "Pedaling Setup"
        STABILITY -> "Riding Stability"
    }

    companion object {
        /**
         * Maps a FitIssueType to its category.
         */
        fun fromIssueType(type: FitIssueType): FitCategory = when (type) {
            FitIssueType.SADDLE_HEIGHT -> SADDLE
            FitIssueType.SADDLE_FORE_AFT -> SADDLE
            FitIssueType.REACH -> COCKPIT
            FitIssueType.HANDLEBAR_HEIGHT -> COCKPIT
            FitIssueType.HIP_ANGLE -> COCKPIT
            FitIssueType.CLEAT_POSITION -> PEDALING
            FitIssueType.CRANK_LENGTH -> PEDALING
            FitIssueType.HIP_ROCKING -> STABILITY
        }
    }
}

/**
 * Overall fit assessment grade.
 */
enum class FitGrade {
    /**
     * Excellent fit - no issues or only minor suggestions.
     */
    EXCELLENT,

    /**
     * Good fit - minor issues that could be improved.
     */
    GOOD,

    /**
     * Fair fit - some issues that should be addressed.
     */
    FAIR,

    /**
     * Poor fit - significant issues requiring attention.
     */
    POOR;

    /**
     * Returns a user-friendly display string.
     */
    fun displayText(): String = when (this) {
        EXCELLENT -> "Excellent Fit"
        GOOD -> "Good Fit"
        FAIR -> "Needs Adjustment"
        POOR -> "Significant Issues"
    }

    /**
     * Returns an emoji indicator for the grade.
     */
    fun emoji(): String = when (this) {
        EXCELLENT -> "✅"
        GOOD -> "👍"
        FAIR -> "⚠️"
        POOR -> "🔴"
    }
}

/**
 * A prioritized recommendation for the user.
 * 
 * Combines potentially multiple related issues into a single
 * actionable recommendation.
 */
data class FitRecommendation(
    val priority: Int,
    val category: FitCategory,
    val severity: Severity,
    val title: String,
    val description: String,
    val action: String,
    val relatedIssues: List<FitIssue>
) {
    /**
     * Returns true if this is a high-priority recommendation.
     */
    fun isHighPriority(): Boolean = priority <= 2 && severity == Severity.HIGH
}

/**
 * A complete summary of the bike fit analysis.
 * 
 * FitSummary transforms the raw FitAnalysisResult into a user-friendly
 * format suitable for UI display. It:
 * - Groups issues by category
 * - Deduplicates and consolidates conflicting recommendations
 * - Provides an overall grade
 * - Orders recommendations by priority
 * 
 * Usage:
 * ```
 * val result = engine.analyze(input)
 * val summary = FitSummary.fromAnalysisResult(result)
 * 
 * // Display overall grade
 * println("${summary.grade.emoji()} ${summary.grade.displayText()}")
 * 
 * // Show recommendations
 * for (rec in summary.recommendations) {
 *     println("${rec.priority}. ${rec.title}")
 *     println("   Action: ${rec.action}")
 * }
 * ```
 */
data class FitSummary(
    val grade: FitGrade,
    val recommendations: List<FitRecommendation>,
    val issuesByCategory: Map<FitCategory, List<FitIssue>>,
    val totalIssueCount: Int,
    val highSeverityCount: Int,
    val cycleCount: Int,
    val analysisTimestampMs: Long,
    val cycleSummary: CycleSummary? = null,
    val ridingContext: RidingContext = RidingContext.DEFAULT,
    val fitBias: FitBias = FitBias.DEFAULT
) {
    /**
     * Returns true if no issues were found.
     */
    fun isOptimal(): Boolean = totalIssueCount == 0

    /**
     * Returns true if there are issues requiring attention.
     */
    fun needsAttention(): Boolean = grade == FitGrade.FAIR || grade == FitGrade.POOR

    /**
     * Returns recommendations filtered by minimum severity.
     */
    fun recommendationsWithSeverity(minSeverity: Severity): List<FitRecommendation> {
        return recommendations.filter { it.severity.isAtLeast(minSeverity) }
    }

    /**
     * Returns issues for a specific category.
     */
    fun issuesInCategory(category: FitCategory): List<FitIssue> {
        return issuesByCategory[category] ?: emptyList()
    }

    /**
     * Returns the top N recommendations.
     */
    fun topRecommendations(n: Int): List<FitRecommendation> {
        return recommendations.take(n)
    }

    /**
     * Returns a brief text summary suitable for display.
     */
    fun briefSummary(): String {
        return when {
            isOptimal() -> "Your bike fit looks great! No adjustments needed."
            highSeverityCount > 0 -> "Found $highSeverityCount issue(s) that should be addressed."
            else -> "Found $totalIssueCount minor suggestion(s) for improvement."
        }
    }

    companion object {
        /**
         * Creates a FitSummary from a FitAnalysisResult.
         * 
         * This is the main factory method that transforms raw analysis
         * results into a user-friendly summary.
         */
        fun fromAnalysisResult(
            result: FitAnalysisResult,
            ridingContext: RidingContext = RidingContext.DEFAULT,
            fitBias: FitBias = FitBias.DEFAULT
        ): FitSummary {
            // Deduplicate and resolve conflicts
            val deduplicatedIssues = deduplicateIssues(result.issues)
            
            // Sort by severity and priority
            val sortedIssues = sortByPriority(deduplicatedIssues)
            
            // Group by category
            val issuesByCategory = sortedIssues.groupBy { FitCategory.fromIssueType(it.type) }
            
            // Create recommendations from issues
            val recommendations = createRecommendations(sortedIssues)
            
            // Calculate grade
            val grade = calculateGrade(sortedIssues)
            
            // Count high severity
            val highSeverityCount = sortedIssues.count { it.severity == Severity.HIGH }
            
            return FitSummary(
                grade = grade,
                recommendations = recommendations,
                issuesByCategory = issuesByCategory,
                totalIssueCount = sortedIssues.size,
                highSeverityCount = highSeverityCount,
                cycleCount = result.cycleCount,
                analysisTimestampMs = result.analysisTimestampMs,
                cycleSummary = result.cycleSummary,
                ridingContext = ridingContext,
                fitBias = fitBias
            )
        }

        /**
         * Creates an empty summary indicating optimal fit.
         */
        fun optimal(cycleCount: Int = 0): FitSummary {
            return FitSummary(
                grade = FitGrade.EXCELLENT,
                recommendations = emptyList(),
                issuesByCategory = emptyMap(),
                totalIssueCount = 0,
                highSeverityCount = 0,
                cycleCount = cycleCount,
                analysisTimestampMs = System.currentTimeMillis()
            )
        }

        /**
         * Deduplicates issues, keeping the most severe when conflicts exist.
         * 
         * Conflicts occur when:
         * - Multiple issues of the same type exist
         * - Issues have contradictory recommendations (e.g., raise vs lower saddle)
         */
        internal fun deduplicateIssues(issues: List<FitIssue>): List<FitIssue> {
            if (issues.isEmpty()) return emptyList()

            // Group by type
            val byType = issues.groupBy { it.type }
            
            return byType.map { (type, typeIssues) ->
                when {
                    typeIssues.size == 1 -> typeIssues.first()
                    else -> resolveConflict(type, typeIssues)
                }
            }
        }

        /**
         * Resolves conflicts between multiple issues of the same type.
         * 
         * Strategy:
         * 1. If all issues recommend the same direction, keep the most severe
         * 2. If issues conflict (e.g., raise vs lower), analyze measured values
         *    and keep the one further from optimal
         * 3. Hip rocking is always kept as-is (no direction conflict possible)
         */
        private fun resolveConflict(type: FitIssueType, issues: List<FitIssue>): FitIssue {
            // Get the most severe issue
            val mostSevere = issues.maxByOrNull { it.severity.ordinal } ?: issues.first()
            
            // For hip rocking, just return most severe
            if (type == FitIssueType.HIP_ROCKING) {
                return mostSevere
            }
            
            // Check if recommendations conflict
            val recommendations = issues.mapNotNull { it.recommendation }.distinct()
            
            if (recommendations.size <= 1) {
                // No conflict, return most severe with averaged measured value
                return consolidateIssues(issues, mostSevere)
            }
            
            // Conflict detected - analyze which direction is correct
            // Use the issue with the largest deviation from optimal
            val withDeviation = issues.mapNotNull { issue ->
                issue.deviationFromOptimal()?.let { deviation ->
                    issue to kotlin.math.abs(deviation)
                }
            }
            
            return if (withDeviation.isNotEmpty()) {
                withDeviation.maxByOrNull { it.second }?.first ?: mostSevere
            } else {
                mostSevere
            }
        }

        /**
         * Consolidates multiple issues into one, averaging measured values.
         */
        private fun consolidateIssues(issues: List<FitIssue>, base: FitIssue): FitIssue {
            val measuredValues = issues.mapNotNull { it.measuredValue }
            val avgMeasured = if (measuredValues.isNotEmpty()) {
                measuredValues.average().toFloat()
            } else {
                base.measuredValue
            }
            
            return base.copy(measuredValue = avgMeasured)
        }

        /**
         * Sorts issues by priority.
         * 
         * Priority order:
         * 1. Severity (HIGH first)
         * 2. Issue type priority (safety-critical first)
         */
        internal fun sortByPriority(issues: List<FitIssue>): List<FitIssue> {
            return issues.sortedWith(
                compareByDescending<FitIssue> { it.severity.ordinal }
                    .thenBy { getTypePriority(it.type) }
            )
        }

        /**
         * Returns priority order for issue types (lower = higher priority).
         */
        private fun getTypePriority(type: FitIssueType): Int = when (type) {
            FitIssueType.SADDLE_HEIGHT -> 1
            FitIssueType.HIP_ROCKING -> 2
            FitIssueType.SADDLE_FORE_AFT -> 3
            FitIssueType.REACH -> 4
            FitIssueType.HIP_ANGLE -> 5
            FitIssueType.HANDLEBAR_HEIGHT -> 6
            FitIssueType.CLEAT_POSITION -> 7
            FitIssueType.CRANK_LENGTH -> 8
        }

        /**
         * Creates prioritized recommendations from issues.
         */
        private fun createRecommendations(issues: List<FitIssue>): List<FitRecommendation> {
            return issues.mapIndexed { index, issue ->
                FitRecommendation(
                    priority = index + 1,
                    category = FitCategory.fromIssueType(issue.type),
                    severity = issue.severity,
                    title = issue.type.getDescription(),
                    description = issue.description,
                    action = issue.recommendation ?: "Review this aspect of your bike fit",
                    relatedIssues = listOf(issue)
                )
            }
        }

        /**
         * Calculates the overall fit grade based on issues.
         */
        internal fun calculateGrade(issues: List<FitIssue>): FitGrade {
            if (issues.isEmpty()) return FitGrade.EXCELLENT
            
            val highCount = issues.count { it.severity == Severity.HIGH }
            val mediumCount = issues.count { it.severity == Severity.MEDIUM }
            
            return when {
                highCount >= 2 -> FitGrade.POOR
                highCount == 1 -> FitGrade.FAIR
                mediumCount >= 2 -> FitGrade.FAIR
                else -> FitGrade.GOOD
            }
        }
    }
}
