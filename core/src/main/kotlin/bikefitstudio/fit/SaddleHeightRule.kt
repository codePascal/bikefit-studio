package bikefitstudio.fit

import bikefitstudio.biomechanics.CycleMetrics
import bikefitstudio.biomechanics.CycleSummary
import bikefitstudio.biomechanics.HipRockingResult

/**
 * Configuration for saddle height analysis thresholds.
 * 
 * All thresholds are documented constants based on bike fit best practices.
 * Knee angle is measured at bottom dead center (BDC) of the pedal stroke.
 * 
 * Note: The knee angle here is the internal angle at the knee joint
 * (180° = fully extended, smaller = more flexed).
 */
data class SaddleHeightConfig(
    /**
     * Minimum knee angle at BDC for optimal range.
     * Below this indicates saddle is too low.
     * 
     * At 140°, the knee is significantly flexed at BDC, indicating
     * the rider cannot extend their leg sufficiently.
     */
    val minOptimalKneeAngle: Float = MIN_OPTIMAL_KNEE_ANGLE,

    /**
     * Maximum knee angle at BDC for optimal range.
     * Above this indicates saddle is too high.
     * 
     * At 155°, the knee is close to full extension, which is
     * the upper limit before over-extension risk.
     */
    val maxOptimalKneeAngle: Float = MAX_OPTIMAL_KNEE_ANGLE,

    /**
     * Knee angle above which saddle is definitely too high.
     * At 160°+, the knee is nearly locked out, risking injury.
     */
    val tooHighKneeAngle: Float = TOO_HIGH_KNEE_ANGLE,

    /**
     * Knee angle below which saddle is definitely too low.
     * At 140°-, significant power loss and potential knee strain.
     */
    val tooLowKneeAngle: Float = TOO_LOW_KNEE_ANGLE,

    /**
     * Hip rocking amplitude threshold (normalized coordinates).
     * Rocking above this suggests saddle may be too high.
     */
    val hipRockingThreshold: Float = HIP_ROCKING_THRESHOLD,

    /**
     * Severity escalation when hip rocking is detected.
     * If saddle height issue AND hip rocking, increase severity.
     */
    val escalateSeverityWithHipRocking: Boolean = true
) {
    companion object {
        /**
         * Minimum optimal knee angle at BDC.
         * 
         * Research and bike fit best practices suggest 25-35° of knee flexion
         * at BDC, which translates to 145-155° internal knee angle.
         * We use 145° as the lower bound of optimal.
         */
        const val MIN_OPTIMAL_KNEE_ANGLE = 145f

        /**
         * Maximum optimal knee angle at BDC.
         * 
         * The upper bound of the optimal range. Above this,
         * the knee is extending too much.
         */
        const val MAX_OPTIMAL_KNEE_ANGLE = 155f

        /**
         * Knee angle threshold for "too high" diagnosis.
         * 
         * At 160°, the knee is nearly fully extended (only 20° flexion),
         * which increases risk of hamstring strain and knee hyperextension.
         */
        const val TOO_HIGH_KNEE_ANGLE = 160f

        /**
         * Knee angle threshold for "too low" diagnosis.
         * 
         * Below 140° (40°+ flexion), the rider experiences:
         * - Reduced power output
         * - Increased knee stress
         * - Earlier fatigue
         */
        const val TOO_LOW_KNEE_ANGLE = 140f

        /**
         * Hip rocking amplitude threshold.
         * 
         * Approximately 3% of frame height. Rocking above this
         * often indicates the saddle is too high, as the rider
         * rocks their hips to reach the pedals at BDC.
         */
        const val HIP_ROCKING_THRESHOLD = 0.03f
    }
}

/**
 * Analyzes saddle height based on knee angle at BDC and hip rocking.
 * 
 * This rule uses the knee angle at bottom dead center to determine
 * if the saddle height is appropriate. It also considers hip rocking
 * as a secondary indicator that the saddle may be too high.
 * 
 * Optimal knee angle at BDC: 145-155° (25-35° flexion)
 * - Below 140°: Saddle too low
 * - Above 160°: Saddle too high
 * 
 * Usage:
 * ```
 * val rule = SaddleHeightRule()
 * val issues = rule.analyze(cycleSummary, hipRockingResult)
 * ```
 */
class SaddleHeightRule(
    private val config: SaddleHeightConfig = SaddleHeightConfig()
) {
    /**
     * Analyzes a single cycle for saddle height issues.
     * 
     * @param cycle The cycle metrics to analyze
     * @param hipRocking Optional hip rocking result
     * @return List of detected FitIssues (may be empty)
     */
    fun analyze(
        cycle: CycleMetrics,
        hipRocking: HipRockingResult? = null
    ): List<FitIssue> {
        val issues = mutableListOf<FitIssue>()

        // Analyze knee angle at BDC
        cycle.kneeAngleAtBdc?.let { kneeAngle ->
            analyzeKneeAngle(kneeAngle, hipRocking)?.let { issues.add(it) }
        }

        // Analyze hip rocking as standalone issue
        hipRocking?.let { rocking ->
            if (rocking.isExcessive) {
                analyzeHipRocking(rocking)?.let { issues.add(it) }
            }
        }

        return issues
    }

    /**
     * Analyzes cycle summary for saddle height issues.
     * 
     * @param summary Aggregated cycle data
     * @param hipRocking Optional hip rocking result
     * @return List of detected FitIssues
     */
    fun analyze(
        summary: CycleSummary,
        hipRocking: HipRockingResult? = null
    ): List<FitIssue> {
        val issues = mutableListOf<FitIssue>()

        // Analyze average knee angle at BDC
        summary.averageKneeAngleAtBdc?.let { kneeAngle ->
            analyzeKneeAngle(kneeAngle, hipRocking)?.let { issues.add(it) }
        }

        // Analyze hip rocking
        hipRocking?.let { rocking ->
            if (rocking.isExcessive) {
                analyzeHipRocking(rocking)?.let { issues.add(it) }
            }
        }

        return issues
    }

    /**
     * Analyzes just a knee angle value.
     * 
     * @param kneeAngleAtBdc Knee angle at bottom dead center
     * @param hipRocking Optional hip rocking for severity escalation
     * @return FitIssue if problem detected, null otherwise
     */
    fun analyzeKneeAngle(
        kneeAngleAtBdc: Float,
        hipRocking: HipRockingResult? = null
    ): FitIssue? {
        val hasHipRocking = hipRocking?.isExcessive == true

        return when {
            // Saddle too high
            kneeAngleAtBdc >= config.tooHighKneeAngle -> {
                createSaddleTooHighIssue(
                    kneeAngle = kneeAngleAtBdc,
                    severity = if (hasHipRocking && config.escalateSeverityWithHipRocking) {
                        Severity.HIGH
                    } else {
                        Severity.HIGH // Already high severity at 160°+
                    },
                    hasHipRocking = hasHipRocking
                )
            }

            kneeAngleAtBdc > config.maxOptimalKneeAngle -> {
                createSaddleTooHighIssue(
                    kneeAngle = kneeAngleAtBdc,
                    severity = if (hasHipRocking && config.escalateSeverityWithHipRocking) {
                        Severity.MEDIUM
                    } else {
                        Severity.LOW
                    },
                    hasHipRocking = hasHipRocking
                )
            }

            // Saddle too low
            kneeAngleAtBdc <= config.tooLowKneeAngle -> {
                createSaddleTooLowIssue(
                    kneeAngle = kneeAngleAtBdc,
                    severity = Severity.HIGH
                )
            }

            kneeAngleAtBdc < config.minOptimalKneeAngle -> {
                createSaddleTooLowIssue(
                    kneeAngle = kneeAngleAtBdc,
                    severity = Severity.MEDIUM
                )
            }

            // Optimal range
            else -> null
        }
    }

    /**
     * Analyzes hip rocking for standalone issue.
     */
    private fun analyzeHipRocking(rocking: HipRockingResult): FitIssue? {
        if (!rocking.isExcessive) return null

        val severity = when {
            rocking.amplitude > config.hipRockingThreshold * 2 -> Severity.HIGH
            rocking.amplitude > config.hipRockingThreshold * 1.5 -> Severity.MEDIUM
            else -> Severity.LOW
        }

        return FitIssue(
            type = FitIssueType.HIP_ROCKING,
            severity = severity,
            description = "Excessive hip movement during pedaling (${formatPercent(rocking.amplitude)} of frame height)",
            measuredValue = rocking.amplitude,
            optimalRange = 0f..config.hipRockingThreshold,
            recommendation = "Lower the saddle by 5-10mm. Hip rocking typically means " +
                    "you're reaching too far at the bottom of the pedal stroke. " +
                    "Core stability exercises can also help reduce rocking."
        )
    }

    private fun createSaddleTooHighIssue(
        kneeAngle: Float,
        severity: Severity,
        hasHipRocking: Boolean
    ): FitIssue {
        val description = buildString {
            append("Knee too extended at bottom of pedal stroke ")
            append("(${formatAngle(kneeAngle)})")
            if (hasHipRocking) {
                append(". Hip rocking also detected, confirming saddle is too high.")
            }
        }

        // Calculate recommended adjustment: ~2-3mm saddle height per 1° knee angle
        val angleError = kneeAngle - config.maxOptimalKneeAngle
        val minAdjustment = (angleError * 2).toInt().coerceAtLeast(3)
        val maxAdjustment = (angleError * 3).toInt().coerceAtLeast(5)

        val recommendation = buildString {
            append("Lower the saddle by $minAdjustment-${maxAdjustment}mm. ")
            if (hasHipRocking) {
                append("Hip rocking confirms this adjustment is needed. ")
            }
            append("Each 1° knee angle change ≈ 2-3mm saddle height. ")
            append("Target: ${formatAngle(config.minOptimalKneeAngle)}-${formatAngle(config.maxOptimalKneeAngle)} at BDC.")
        }

        return FitIssue(
            type = FitIssueType.SADDLE_HEIGHT,
            severity = severity,
            description = description,
            measuredValue = kneeAngle,
            optimalRange = config.minOptimalKneeAngle..config.maxOptimalKneeAngle,
            recommendation = recommendation
        )
    }

    private fun createSaddleTooLowIssue(
        kneeAngle: Float,
        severity: Severity
    ): FitIssue {
        // Calculate recommended adjustment: ~2-3mm saddle height per 1° knee angle
        val angleError = config.minOptimalKneeAngle - kneeAngle
        val minAdjustment = (angleError * 2).toInt().coerceAtLeast(3)
        val maxAdjustment = (angleError * 3).toInt().coerceAtLeast(5)

        val recommendation = buildString {
            append("Raise the saddle by $minAdjustment-${maxAdjustment}mm. ")
            append("Each 1° knee angle change ≈ 2-3mm saddle height. ")
            append("A low saddle reduces power and stresses the knee joint. ")
            append("Target: ${formatAngle(config.minOptimalKneeAngle)}-${formatAngle(config.maxOptimalKneeAngle)} at BDC.")
        }

        return FitIssue(
            type = FitIssueType.SADDLE_HEIGHT,
            severity = severity,
            description = "Knee too flexed at bottom of pedal stroke (${formatAngle(kneeAngle)})",
            measuredValue = kneeAngle,
            optimalRange = config.minOptimalKneeAngle..config.maxOptimalKneeAngle,
            recommendation = recommendation
        )
    }

    private fun formatAngle(angle: Float): String {
        return "${angle.toInt()}°"
    }

    private fun formatPercent(value: Float): String {
        return "${(value * 100).toInt()}%"
    }

    companion object {
        /**
         * Quick check if a knee angle is within optimal range.
         */
        fun isOptimalKneeAngle(
            kneeAngleAtBdc: Float,
            config: SaddleHeightConfig = SaddleHeightConfig()
        ): Boolean {
            return kneeAngleAtBdc in config.minOptimalKneeAngle..config.maxOptimalKneeAngle
        }

        /**
         * Quick check if saddle appears too high.
         */
        fun isSaddleTooHigh(
            kneeAngleAtBdc: Float,
            config: SaddleHeightConfig = SaddleHeightConfig()
        ): Boolean {
            return kneeAngleAtBdc > config.maxOptimalKneeAngle
        }

        /**
         * Quick check if saddle appears too low.
         */
        fun isSaddleTooLow(
            kneeAngleAtBdc: Float,
            config: SaddleHeightConfig = SaddleHeightConfig()
        ): Boolean {
            return kneeAngleAtBdc < config.minOptimalKneeAngle
        }
    }
}
