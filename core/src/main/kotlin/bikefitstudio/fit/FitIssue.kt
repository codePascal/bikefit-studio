package bikefitstudio.fit

import bikefitstudio.biomechanics.CycleSummary

/**
 * Severity levels for bike fit issues.
 * 
 * Used to prioritize which fit problems should be addressed first
 * and to communicate urgency to the user.
 */
enum class Severity {
    /**
     * Minor issue that may slightly affect comfort or efficiency.
     * The rider may choose to address it or not.
     */
    LOW,

    /**
     * Moderate issue that likely affects comfort, efficiency, or
     * could lead to minor discomfort over long rides.
     */
    MEDIUM,

    /**
     * Significant issue that should be addressed promptly.
     * Could cause injury, significant discomfort, or major
     * power loss if left uncorrected.
     */
    HIGH;

    /**
     * Returns true if this severity is at least the specified level.
     */
    fun isAtLeast(other: Severity): Boolean {
        return this.ordinal >= other.ordinal
    }

    companion object {
        /**
         * Returns the highest severity from a collection.
         */
        fun highest(severities: Collection<Severity>): Severity? {
            return severities.maxByOrNull { it.ordinal }
        }
    }
}

/**
 * Types of bike fit issues that can be detected.
 * 
 * Each type represents a specific adjustment that may need to be made
 * to the bike setup based on biomechanical analysis.
 */
enum class FitIssueType {
    /**
     * Saddle height issue.
     * 
     * Detected by analyzing knee angle at bottom dead center (BDC).
     * - Knee angle < 25° at BDC → saddle too high
     * - Knee angle > 35° at BDC → saddle too low
     * 
     * Optimal range: 25-35° knee flexion at BDC.
     */
    SADDLE_HEIGHT,

    /**
     * Saddle fore/aft position issue.
     * 
     * Detected by analyzing knee position relative to pedal spindle
     * when the crank is at 3 o'clock position (horizontal forward).
     * 
     * KOPS (Knee Over Pedal Spindle) is a common starting point,
     * though optimal position varies by riding style.
     */
    SADDLE_FORE_AFT,

    /**
     * Reach/cockpit length issue.
     * 
     * Detected by analyzing shoulder, elbow, and wrist positions
     * relative to the handlebars. Affects comfort, aerodynamics,
     * and handling.
     * 
     * Signs of incorrect reach:
     * - Locked elbows → reach too long
     * - Very bent elbows → reach too short
     * - Excessive forward lean → stem too long or bars too low
     */
    REACH,

    /**
     * Handlebar height issue.
     * 
     * Detected by analyzing torso angle and shoulder position.
     * Affects comfort, aerodynamics, and weight distribution.
     */
    HANDLEBAR_HEIGHT,

    /**
     * Cleat position issue.
     * 
     * Detected by analyzing foot/ankle angle and knee tracking.
     * Affects pedaling efficiency and can cause knee pain.
     */
    CLEAT_POSITION,

    /**
     * Crank length issue.
     * 
     * Detected by analyzing hip angle at top dead center.
     * Cranks too long can cause hip impingement.
     */
    CRANK_LENGTH,

    /**
     * Hip rocking/instability issue.
     * 
     * Detected by tracking hip vertical motion during pedaling.
     * Excessive rocking often indicates saddle too high.
     */
    HIP_ROCKING,

    /**
     * Hip angle issue.
     * 
     * Detected by analyzing minimum hip angle at top dead center (TDC).
     * A closed hip angle restricts breathing and reduces power.
     * A very open hip indicates an upright position.
     */
    HIP_ANGLE;

    /**
     * Returns a brief description of the issue type.
     */
    fun getDescription(): String {
        return when (this) {
            SADDLE_HEIGHT -> "Saddle height adjustment needed"
            SADDLE_FORE_AFT -> "Saddle fore/aft position adjustment needed"
            REACH -> "Handlebar reach adjustment needed"
            HANDLEBAR_HEIGHT -> "Handlebar height adjustment needed"
            CLEAT_POSITION -> "Cleat position adjustment needed"
            CRANK_LENGTH -> "Crank length may not be optimal"
            HIP_ROCKING -> "Excessive hip movement detected"
            HIP_ANGLE -> "Hip angle adjustment needed"
        }
    }
}

/**
 * Represents a specific bike fit issue detected during analysis.
 * 
 * A FitIssue contains all the information needed to understand
 * what's wrong with the current bike setup and what should be done.
 * 
 * @param type The type of fit issue
 * @param severity How urgent/important this issue is
 * @param description Detailed description of the problem
 * @param measuredValue The actual measured value (e.g., knee angle)
 * @param optimalRange The optimal range for this measurement
 * @param recommendation Suggested action to fix the issue
 */
data class FitIssue(
    val type: FitIssueType,
    val severity: Severity,
    val description: String,
    val measuredValue: Float? = null,
    val optimalRange: ClosedFloatingPointRange<Float>? = null,
    val recommendation: String? = null
) {
    /**
     * Returns true if a measured value is outside the optimal range.
     */
    fun isOutOfRange(): Boolean {
        if (measuredValue == null || optimalRange == null) return false
        return measuredValue !in optimalRange
    }

    /**
     * Calculates how far the measured value is from the optimal range.
     * Returns negative if below range, positive if above, zero if within.
     */
    fun deviationFromOptimal(): Float? {
        if (measuredValue == null || optimalRange == null) return null

        return when {
            measuredValue < optimalRange.start -> measuredValue - optimalRange.start
            measuredValue > optimalRange.endInclusive -> measuredValue - optimalRange.endInclusive
            else -> 0f
        }
    }

    companion object {
        /**
         * Creates a saddle height issue based on knee angle at BDC.
         */
        fun saddleHeightFromKneeAngle(
            kneeAngleAtBdc: Float,
            optimalMin: Float = 25f,
            optimalMax: Float = 35f
        ): FitIssue? {
            val optimalRange = optimalMin..optimalMax

            return when {
                kneeAngleAtBdc < optimalMin -> FitIssue(
                    type = FitIssueType.SADDLE_HEIGHT,
                    severity = if (kneeAngleAtBdc < optimalMin - 5) Severity.HIGH else Severity.MEDIUM,
                    description = "Knee too extended at bottom of pedal stroke",
                    measuredValue = kneeAngleAtBdc,
                    optimalRange = optimalRange,
                    recommendation = "Lower saddle height"
                )
                kneeAngleAtBdc > optimalMax -> FitIssue(
                    type = FitIssueType.SADDLE_HEIGHT,
                    severity = if (kneeAngleAtBdc > optimalMax + 10) Severity.HIGH else Severity.MEDIUM,
                    description = "Knee too flexed at bottom of pedal stroke",
                    measuredValue = kneeAngleAtBdc,
                    optimalRange = optimalRange,
                    recommendation = "Raise saddle height"
                )
                else -> null // Within optimal range
            }
        }

        /**
         * Creates a hip rocking issue.
         */
        fun hipRocking(
            amplitude: Float,
            threshold: Float = 0.03f
        ): FitIssue? {
            if (amplitude <= threshold) return null

            val severity = when {
                amplitude > threshold * 2 -> Severity.HIGH
                amplitude > threshold * 1.5 -> Severity.MEDIUM
                else -> Severity.LOW
            }

            return FitIssue(
                type = FitIssueType.HIP_ROCKING,
                severity = severity,
                description = "Excessive lateral hip movement during pedaling",
                measuredValue = amplitude,
                optimalRange = 0f..threshold,
                recommendation = "Check saddle height (may be too high) or core stability"
            )
        }
    }
}

/**
 * Collection of fit issues from a complete analysis.
 * 
 * @param issues List of detected fit issues
 * @param analysisTimestampMs When the analysis was performed
 * @param cycleCount Number of pedal cycles analyzed
 * @param cycleSummary The raw biomechanics data used for analysis
 */
data class FitAnalysisResult(
    val issues: List<FitIssue>,
    val analysisTimestampMs: Long = System.currentTimeMillis(),
    val cycleCount: Int = 0,
    val cycleSummary: CycleSummary? = null,
    val keyFrameSetLeft: bikefitstudio.biomechanics.KeyFrameSet? = null,
    val keyFrameSetRight: bikefitstudio.biomechanics.KeyFrameSet? = null
) {
    /**
     * Returns the highest severity issue, or null if no issues.
     */
    fun highestSeverity(): Severity? {
        return Severity.highest(issues.map { it.severity })
    }

    /**
     * Returns issues filtered by type.
     */
    fun issuesByType(type: FitIssueType): List<FitIssue> {
        return issues.filter { it.type == type }
    }

    /**
     * Returns issues filtered by minimum severity.
     */
    fun issuesWithSeverity(minSeverity: Severity): List<FitIssue> {
        return issues.filter { it.severity.isAtLeast(minSeverity) }
    }

    /**
     * Returns true if any issues exist.
     */
    fun hasIssues(): Boolean = issues.isNotEmpty()

    /**
     * Returns true if any high severity issues exist.
     */
    fun hasHighSeverityIssues(): Boolean {
        return issues.any { it.severity == Severity.HIGH }
    }

    companion object {
        /**
         * Empty result with no issues.
         */
        val EMPTY = FitAnalysisResult(issues = emptyList())
    }
}
