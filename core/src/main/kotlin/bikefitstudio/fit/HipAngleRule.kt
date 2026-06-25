package bikefitstudio.fit

import bikefitstudio.biomechanics.CycleMetrics
import bikefitstudio.biomechanics.CycleSummary

/**
 * Configuration for hip angle analysis thresholds.
 * 
 * Hip angle is the minimum internal angle at the hip joint during TDC
 * (top dead center of pedal stroke). This represents the most closed
 * position of the hip during the pedal revolution.
 * 
 * A closed hip angle (<45°) can:
 * - Restrict diaphragm movement and breathing
 * - Reduce glute activation and power
 * - Cause discomfort on long rides (soft tissue pressure)
 * 
 * A very open hip (>60°) may indicate:
 * - Position is too upright for intended use
 * - Reduced aerodynamic efficiency
 * - Potential frame size mismatch (too small)
 */
data class HipAngleConfig(
    /**
     * Minimum optimal hip angle at TDC.
     * Below this, the hip is closing too much.
     */
    val minOptimalAngle: Float = MIN_OPTIMAL_ANGLE,

    /**
     * Maximum optimal hip angle at TDC.
     * Above this, position may be too upright for performance.
     */
    val maxOptimalAngle: Float = MAX_OPTIMAL_ANGLE,

    /**
     * Hip angle below which there's HIGH severity concern.
     * At this point, breathing and power are significantly compromised.
     */
    val tooClosedAngle: Float = TOO_CLOSED_ANGLE,

    /**
     * Hip angle above which position is considered very upright.
     * This is a softer threshold - may be intentional for comfort.
     */
    val tooOpenAngle: Float = TOO_OPEN_ANGLE
) {
    companion object {
        /**
         * Minimum optimal hip angle at TDC.
         * 
         * Below 45°, most riders experience some breathing restriction
         * and reduced glute engagement. This is the lower bound for
         * general road cycling positions.
         */
        const val MIN_OPTIMAL_ANGLE = 45f

        /**
         * Maximum optimal hip angle at TDC.
         * 
         * Above 55°, the position becomes quite upright. This is fine
         * for comfort-focused riding but may indicate issues if the
         * rider is targeting performance.
         */
        const val MAX_OPTIMAL_ANGLE = 55f

        /**
         * Too closed threshold (HIGH severity).
         * 
         * At or below 40°, the hip closure is severe enough to:
         * - Significantly restrict breathing capacity
         * - Inhibit glute activation patterns
         * - Cause anterior hip impingement in some riders
         * 
         * This is the global safety floor from bike fit best practices.
         */
        const val TOO_CLOSED_ANGLE = 40f

        /**
         * Too open threshold.
         * 
         * Above 65°, the position is very upright. This triggers a
         * LOW-MEDIUM severity note but isn't inherently problematic -
         * it may be intentional for city/commute riding.
         */
        const val TOO_OPEN_ANGLE = 65f
    }
}

/**
 * Analyzes hip angle at TDC for bike fit issues.
 * 
 * The hip angle (minimum angle at top dead center) is a critical metric
 * that affects:
 * - Breathing capacity (closed hip restricts diaphragm)
 * - Power production (too closed = glute inhibition)
 * - Comfort on long rides (closed hip = pressure on soft tissue)
 * - Injury risk (hip impingement with very closed angles)
 * 
 * This rule cross-references with saddle height and reach issues:
 * - Closed hip + high saddle → may need reach reduction instead
 * - Closed hip + aggressive torso → compound issue, prioritize hip
 * 
 * Usage:
 * ```
 * val rule = HipAngleRule()
 * val issues = rule.analyze(cycleSummary)
 * ```
 */
class HipAngleRule(
    private val config: HipAngleConfig = HipAngleConfig()
) {
    /**
     * Analyzes a single cycle for hip angle issues.
     * 
     * @param cycle The cycle metrics to analyze
     * @return List of detected FitIssues (may be empty)
     */
    fun analyze(cycle: CycleMetrics): List<FitIssue> {
        val issues = mutableListOf<FitIssue>()

        cycle.hipAngleAtTdc?.let { hipAngle ->
            analyzeHipAngle(hipAngle)?.let { issues.add(it) }
        }

        return issues
    }

    /**
     * Analyzes cycle summary for hip angle issues.
     * 
     * @param summary Aggregated cycle data
     * @return List of detected FitIssues
     */
    fun analyze(summary: CycleSummary): List<FitIssue> {
        val issues = mutableListOf<FitIssue>()

        summary.averageHipAngleAtTdc?.let { hipAngle ->
            analyzeHipAngle(hipAngle)?.let { issues.add(it) }
        }

        return issues
    }

    /**
     * Analyzes just a hip angle value.
     * 
     * @param hipAngleAtTdc Hip angle at top dead center
     * @return FitIssue if problem detected, null otherwise
     */
    fun analyzeHipAngle(hipAngleAtTdc: Float): FitIssue? {
        return when {
            // Hip too closed - HIGH severity (breathing/power affected)
            hipAngleAtTdc <= config.tooClosedAngle -> {
                createHipTooClosedIssue(hipAngleAtTdc, Severity.HIGH)
            }

            // Below optimal range - MEDIUM severity
            hipAngleAtTdc < config.minOptimalAngle -> {
                createHipTooClosedIssue(hipAngleAtTdc, Severity.MEDIUM)
            }

            // Hip too open - position very upright
            hipAngleAtTdc >= config.tooOpenAngle -> {
                createHipTooOpenIssue(hipAngleAtTdc, Severity.MEDIUM)
            }

            // Above optimal but not excessive - informational
            hipAngleAtTdc > config.maxOptimalAngle -> {
                createHipTooOpenIssue(hipAngleAtTdc, Severity.LOW)
            }

            // Optimal range
            else -> null
        }
    }

    private fun createHipTooClosedIssue(
        hipAngle: Float,
        severity: Severity
    ): FitIssue {
        val angleError = config.minOptimalAngle - hipAngle

        val recommendation = buildString {
            append("Hip angle is too closed at the top of the pedal stroke. ")
            if (severity == Severity.HIGH) {
                append("This can significantly restrict breathing and reduce power output. ")
            }
            append("To open the hip angle: ")
            append("1) Raise handlebars by 10-20mm, ")
            append("2) Use a shorter stem (-10-20mm length), or ")
            append("3) Move saddle back 5-10mm on the rails. ")
            append("Target hip angle: ${config.minOptimalAngle.toInt()}-${config.maxOptimalAngle.toInt()}° at TDC.")
        }

        return FitIssue(
            type = FitIssueType.HIP_ANGLE,
            severity = severity,
            description = "Hip angle too closed at top of pedal stroke (${hipAngle.toInt()}°)",
            measuredValue = hipAngle,
            optimalRange = config.minOptimalAngle..config.maxOptimalAngle,
            recommendation = recommendation
        )
    }

    private fun createHipTooOpenIssue(
        hipAngle: Float,
        severity: Severity
    ): FitIssue {
        val recommendation = buildString {
            append("Hip angle is quite open at the top of the pedal stroke (${hipAngle.toInt()}°). ")
            append("This indicates a more upright position. ")
            if (severity == Severity.MEDIUM) {
                append("If seeking more power or aerodynamics, consider: ")
                append("1) Lowering handlebars 10-20mm, ")
                append("2) Using a longer stem (+10-20mm), or ")
                append("3) Moving saddle forward 5-10mm. ")
            } else {
                append("This position is fine for comfort-focused riding. ")
                append("Only adjust if you want a more aggressive position. ")
            }
            append("Current hip angle is above the typical ${config.minOptimalAngle.toInt()}-${config.maxOptimalAngle.toInt()}° range.")
        }

        return FitIssue(
            type = FitIssueType.HIP_ANGLE,
            severity = severity,
            description = "Hip angle very open at top of pedal stroke (${hipAngle.toInt()}°)",
            measuredValue = hipAngle,
            optimalRange = config.minOptimalAngle..config.maxOptimalAngle,
            recommendation = recommendation
        )
    }

    companion object {
        /**
         * Quick check if hip angle is within optimal range.
         */
        fun isOptimalHipAngle(
            hipAngle: Float,
            config: HipAngleConfig = HipAngleConfig()
        ): Boolean {
            return hipAngle in config.minOptimalAngle..config.maxOptimalAngle
        }

        /**
         * Quick check if hip is too closed.
         */
        fun isHipTooClosed(
            hipAngle: Float,
            config: HipAngleConfig = HipAngleConfig()
        ): Boolean {
            return hipAngle < config.minOptimalAngle
        }

        /**
         * Quick check if hip is too open (very upright).
         */
        fun isHipTooOpen(
            hipAngle: Float,
            config: HipAngleConfig = HipAngleConfig()
        ): Boolean {
            return hipAngle > config.maxOptimalAngle
        }
    }
}
