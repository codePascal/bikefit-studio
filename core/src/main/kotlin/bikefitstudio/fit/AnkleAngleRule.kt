package bikefitstudio.fit

import bikefitstudio.biomechanics.CycleMetrics
import bikefitstudio.biomechanics.CycleSummary

/**
 * Configuration for ankle angle analysis thresholds.
 * 
 * Ankle angle is measured as plantarflexion at bottom dead center (BDC).
 * 0° = neutral (foot perpendicular to shin)
 * Positive = plantarflexion (toes pointing down)
 * Negative = dorsiflexion (toes pointing up, heels dropping)
 * 
 * Modern bike fitting research suggests optimal plantarflexion of 15-25°
 * at BDC for efficient power transfer while avoiding Achilles strain.
 */
data class AnkleAngleConfig(
    /**
     * Minimum optimal plantarflexion at BDC.
     * Below this, the rider may be "heel dropping" which is inefficient.
     */
    val minOptimalAngle: Float = MIN_OPTIMAL_ANGLE,

    /**
     * Maximum optimal plantarflexion at BDC.
     * Above this, excessive ankling may cause Achilles tendon stress.
     */
    val maxOptimalAngle: Float = MAX_OPTIMAL_ANGLE,

    /**
     * Plantarflexion above which there's high risk of Achilles strain.
     * Triggers HIGH severity issue.
     */
    val excessiveAngle: Float = EXCESSIVE_ANGLE,

    /**
     * Dorsiflexion (negative angle) threshold for heel dropping.
     * Below this (more negative), the rider is dropping heels excessively.
     */
    val heelDropThreshold: Float = HEEL_DROP_THRESHOLD
) {
    companion object {
        /**
         * Minimum optimal plantarflexion at BDC.
         * 
         * Below 15°, the rider may not be using natural ankle motion
         * effectively. Some riders naturally have less ankling, which
         * is acceptable if consistent.
         */
        const val MIN_OPTIMAL_ANGLE = 15f

        /**
         * Maximum optimal plantarflexion at BDC.
         * 
         * Above 25°, the ankle is pointing down significantly,
         * which may indicate:
         * - Cleats positioned too far back
         * - Saddle too high (reaching for pedals)
         * - Excessive ankling technique
         */
        const val MAX_OPTIMAL_ANGLE = 25f

        /**
         * Excessive plantarflexion threshold.
         * 
         * Above 30°, there is significant risk of:
         * - Achilles tendinitis
         * - Calf fatigue
         * - Inefficient power transfer
         * 
         * This is a HIGH severity issue requiring immediate attention.
         */
        const val EXCESSIVE_ANGLE = 30f

        /**
         * Heel drop threshold (negative plantarflexion = dorsiflexion).
         * 
         * When the heel drops below horizontal (negative angle),
         * power transfer is reduced and there may be knee strain.
         * Values below -5° indicate significant heel dropping.
         */
        const val HEEL_DROP_THRESHOLD = -5f
    }
}

/**
 * Analyzes ankle plantarflexion at BDC for bike fit issues.
 * 
 * The ankle angle (plantarflexion) affects:
 * - Power transfer efficiency
 * - Achilles tendon health
 * - Calf muscle engagement
 * - Overall pedaling smoothness
 * 
 * Modern bike fitting suggests a natural plantarflexion of 15-25° at BDC.
 * This allows efficient power transfer while protecting the Achilles tendon.
 * 
 * Usage:
 * ```
 * val rule = AnkleAngleRule()
 * val issues = rule.analyze(cycleSummary)
 * ```
 */
class AnkleAngleRule(
    private val config: AnkleAngleConfig = AnkleAngleConfig()
) {
    /**
     * Analyzes a single cycle for ankle angle issues.
     * 
     * @param cycle The cycle metrics to analyze
     * @return List of detected FitIssues (may be empty)
     */
    fun analyze(cycle: CycleMetrics): List<FitIssue> {
        val issues = mutableListOf<FitIssue>()

        cycle.ankleAngleAtBdc?.let { ankleAngle ->
            analyzeAnkleAngle(ankleAngle)?.let { issues.add(it) }
        }

        return issues
    }

    /**
     * Analyzes cycle summary for ankle angle issues.
     * 
     * @param summary Aggregated cycle data
     * @return List of detected FitIssues
     */
    fun analyze(summary: CycleSummary): List<FitIssue> {
        val issues = mutableListOf<FitIssue>()

        summary.averageAnkleAngleAtBdc?.let { ankleAngle ->
            analyzeAnkleAngle(ankleAngle)?.let { issues.add(it) }
        }

        return issues
    }

    /**
     * Analyzes just an ankle angle value.
     * 
     * @param ankleAngleAtBdc Ankle plantarflexion at bottom dead center
     * @return FitIssue if problem detected, null otherwise
     */
    fun analyzeAnkleAngle(ankleAngleAtBdc: Float): FitIssue? {
        return when {
            // Excessive plantarflexion - HIGH severity (Achilles strain risk)
            ankleAngleAtBdc >= config.excessiveAngle -> {
                createExcessivePlantarflexionIssue(
                    ankleAngle = ankleAngleAtBdc,
                    severity = Severity.HIGH
                )
            }

            // Above optimal range - MEDIUM severity
            ankleAngleAtBdc > config.maxOptimalAngle -> {
                createExcessivePlantarflexionIssue(
                    ankleAngle = ankleAngleAtBdc,
                    severity = Severity.MEDIUM
                )
            }

            // Heel dropping (dorsiflexion) - significant negative angle
            ankleAngleAtBdc <= config.heelDropThreshold -> {
                createHeelDropIssue(
                    ankleAngle = ankleAngleAtBdc,
                    severity = if (ankleAngleAtBdc < config.heelDropThreshold - 5) {
                        Severity.MEDIUM
                    } else {
                        Severity.LOW
                    }
                )
            }

            // Below optimal but not heel dropping - minor issue
            ankleAngleAtBdc < config.minOptimalAngle -> {
                createInsufficientAnklingIssue(
                    ankleAngle = ankleAngleAtBdc,
                    severity = Severity.LOW
                )
            }

            // Optimal range
            else -> null
        }
    }

    private fun createExcessivePlantarflexionIssue(
        ankleAngle: Float,
        severity: Severity
    ): FitIssue {
        val adjustmentMm = if (ankleAngle > config.excessiveAngle) "5-8mm" else "3-5mm"
        
        val recommendation = buildString {
            append("Move cleats $adjustmentMm forward on your shoes. ")
            if (severity == Severity.HIGH) {
                append("Excessive toe-down position risks Achilles tendinitis. ")
            }
            append("Also check if saddle is too high (causing reaching for pedals).")
        }

        return FitIssue(
            type = FitIssueType.CLEAT_POSITION,
            severity = severity,
            description = "Excessive ankle plantarflexion at bottom of pedal stroke (${formatAngle(ankleAngle)})",
            measuredValue = ankleAngle,
            optimalRange = config.minOptimalAngle..config.maxOptimalAngle,
            recommendation = recommendation
        )
    }

    private fun createHeelDropIssue(
        ankleAngle: Float,
        severity: Severity
    ): FitIssue {
        return FitIssue(
            type = FitIssueType.CLEAT_POSITION,
            severity = severity,
            description = "Heel dropping below horizontal at bottom of pedal stroke (${formatAngle(ankleAngle)})",
            measuredValue = ankleAngle,
            optimalRange = config.minOptimalAngle..config.maxOptimalAngle,
            recommendation = "Move cleats 3-5mm back on your shoes. " +
                    "Also focus on smooth pedaling technique - " +
                    "visualize 'scraping mud' off your shoe at the bottom of the stroke."
        )
    }

    private fun createInsufficientAnklingIssue(
        ankleAngle: Float,
        severity: Severity
    ): FitIssue {
        return FitIssue(
            type = FitIssueType.CLEAT_POSITION,
            severity = severity,
            description = "Limited ankle motion at bottom of pedal stroke (${formatAngle(ankleAngle)})",
            measuredValue = ankleAngle,
            optimalRange = config.minOptimalAngle..config.maxOptimalAngle,
            recommendation = "Some natural ankle motion (15-25°) improves power transfer. " +
                    "This may be your natural pedaling style - only adjust if experiencing discomfort. " +
                    "Consider if cleats are too far forward."
        )
    }

    private fun formatAngle(angle: Float): String {
        return if (angle >= 0) {
            "${angle.toInt()}° plantarflexion"
        } else {
            "${(-angle).toInt()}° dorsiflexion"
        }
    }

    companion object {
        /**
         * Quick check if ankle angle is within optimal range.
         */
        fun isOptimalAnkleAngle(
            ankleAngleAtBdc: Float,
            config: AnkleAngleConfig = AnkleAngleConfig()
        ): Boolean {
            return ankleAngleAtBdc in config.minOptimalAngle..config.maxOptimalAngle
        }

        /**
         * Quick check if plantarflexion is excessive.
         */
        fun isExcessivePlantarflexion(
            ankleAngleAtBdc: Float,
            config: AnkleAngleConfig = AnkleAngleConfig()
        ): Boolean {
            return ankleAngleAtBdc > config.maxOptimalAngle
        }

        /**
         * Quick check if heel is dropping (dorsiflexion).
         */
        fun isHeelDropping(
            ankleAngleAtBdc: Float,
            config: AnkleAngleConfig = AnkleAngleConfig()
        ): Boolean {
            return ankleAngleAtBdc < config.heelDropThreshold
        }
    }
}
