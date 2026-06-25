package bikefitstudio.fit

import bikefitstudio.biomechanics.CycleMetrics
import bikefitstudio.biomechanics.CycleSummary
import bikefitstudio.biomechanics.TorsoAngleResult
import bikefitstudio.calibration.BikeCalibration

/**
 * Configuration for reach assessment thresholds.
 * 
 * Reach is assessed primarily through torso angle, which reflects
 * how stretched or compressed the rider is on the bike.
 * 
 * Torso angle is measured from horizontal (0° = horizontal, 90° = vertical).
 */
data class ReachConfig(
    /**
     * Minimum optimal torso angle.
     * Below this, the rider is too stretched (reach too long).
     * 
     * A torso angle < 30° indicates a very aggressive, aerodynamic position
     * that may be uncomfortable for most recreational riders.
     */
    val minOptimalTorsoAngle: Float = MIN_OPTIMAL_TORSO_ANGLE,

    /**
     * Maximum optimal torso angle.
     * Above this, the rider is too upright (reach too short).
     * 
     * A torso angle > 60° indicates a very upright position
     * that may indicate the bars are too close or too high.
     */
    val maxOptimalTorsoAngle: Float = MAX_OPTIMAL_TORSO_ANGLE,

    /**
     * Torso angle below which position is considered excessively aggressive.
     * This triggers a HIGH severity issue.
     */
    val tooAggressiveAngle: Float = TOO_AGGRESSIVE_ANGLE,

    /**
     * Torso angle above which position is considered excessively upright.
     * This triggers a HIGH severity issue.
     */
    val tooUprightAngle: Float = TOO_UPRIGHT_ANGLE,

    /**
     * Maximum horizontal distance from shoulder to handlebar (normalized).
     * Beyond this, reach is definitely too long.
     */
    val maxShoulderToHandlebarOffset: Float = MAX_SHOULDER_HANDLEBAR_OFFSET
) {
    companion object {
        /**
         * Minimum optimal torso angle (from horizontal).
         * 
         * A 30° torso angle is typical for competitive road cyclists.
         * Below this is considered aggressive and may cause:
         * - Neck strain
         * - Lower back pain
         * - Breathing difficulties
         * - Reduced comfort on long rides
         */
        const val MIN_OPTIMAL_TORSO_ANGLE = 30f

        /**
         * Maximum optimal torso angle.
         * 
         * A 60° torso angle is typical for relaxed/recreational riding.
         * Above this indicates:
         * - Very upright position
         * - Bars may be too close or too high
         * - Potential loss of aerodynamic efficiency
         * - May indicate stem too short or bars too high
         */
        const val MAX_OPTIMAL_TORSO_ANGLE = 60f

        /**
         * Threshold for excessively aggressive position.
         * 
         * Below 25°, the position is very aggressive and suitable
         * only for time trials or short, intense efforts.
         */
        const val TOO_AGGRESSIVE_ANGLE = 25f

        /**
         * Threshold for excessively upright position.
         * 
         * Above 70°, the position is very upright, more like
         * a city bike than a road bike.
         */
        const val TOO_UPRIGHT_ANGLE = 70f

        /**
         * Maximum shoulder-to-handlebar horizontal offset.
         * 
         * If the shoulder is this far behind the handlebar,
         * reach is definitely too long.
         */
        const val MAX_SHOULDER_HANDLEBAR_OFFSET = 0.15f
    }
}

/**
 * Analyzes reach (handlebar distance) based on torso angle and shoulder position.
 * 
 * Reach assessment uses torso angle as the primary indicator:
 * - Too flat (< 30°): Reach is too long, rider is over-stretched
 * - Optimal (30-60°): Good balance of comfort and efficiency
 * - Too upright (> 60°): Reach is too short, bars too close/high
 * 
 * Secondary indicator is shoulder position relative to handlebars,
 * if bike calibration is available.
 * 
 * Usage:
 * ```
 * val rule = ReachRule()
 * val issues = rule.analyze(torsoAngle = 25f)
 * // or
 * val issues = rule.analyze(cycleSummary)
 * ```
 */
class ReachRule(
    private val config: ReachConfig = ReachConfig()
) {
    /**
     * Analyzes torso angle for reach issues.
     * 
     * @param torsoAngle Torso angle in degrees (0° = horizontal, 90° = vertical)
     * @return List of detected FitIssues (may be empty)
     */
    fun analyze(torsoAngle: Float): List<FitIssue> {
        val issues = mutableListOf<FitIssue>()

        when {
            // Reach too long (torso too aggressive/flat)
            torsoAngle <= config.tooAggressiveAngle -> {
                issues.add(createReachTooLongIssue(torsoAngle, Severity.HIGH))
            }
            torsoAngle < config.minOptimalTorsoAngle -> {
                issues.add(createReachTooLongIssue(torsoAngle, Severity.MEDIUM))
            }

            // Reach too short (torso too upright)
            torsoAngle >= config.tooUprightAngle -> {
                issues.add(createReachTooShortIssue(torsoAngle, Severity.HIGH))
            }
            torsoAngle > config.maxOptimalTorsoAngle -> {
                issues.add(createReachTooShortIssue(torsoAngle, Severity.MEDIUM))
            }
        }

        return issues
    }

    /**
     * Analyzes a TorsoAngleResult for reach issues.
     * 
     * @param torsoResult The torso angle calculation result
     * @return List of detected FitIssues
     */
    fun analyze(torsoResult: TorsoAngleResult): List<FitIssue> {
        if (!torsoResult.isValid) {
            return emptyList()
        }
        return analyze(torsoResult.angle)
    }

    /**
     * Analyzes a CycleMetrics for reach issues.
     * 
     * @param cycle The cycle metrics
     * @return List of detected FitIssues
     */
    fun analyze(cycle: CycleMetrics): List<FitIssue> {
        if (!cycle.torsoAngle.isValid) {
            return emptyList()
        }
        return analyze(cycle.torsoAngle.average)
    }

    /**
     * Analyzes a CycleSummary for reach issues.
     * 
     * @param summary The cycle summary with average torso angle
     * @return List of detected FitIssues
     */
    fun analyze(summary: CycleSummary): List<FitIssue> {
        if (summary.averageTorsoAngle == 0f && !summary.torsoAngleStats.isValid) {
            return emptyList()
        }
        return analyze(summary.averageTorsoAngle)
    }

    /**
     * Analyzes reach using both torso angle and shoulder-to-handlebar offset.
     * 
     * @param torsoAngle Torso angle in degrees
     * @param shoulderX Normalized X position of shoulder
     * @param calibration Bike calibration with handlebar position
     * @return List of detected FitIssues
     */
    fun analyze(
        torsoAngle: Float,
        shoulderX: Float,
        calibration: BikeCalibration
    ): List<FitIssue> {
        val issues = mutableListOf<FitIssue>()

        // Add torso angle-based issues
        issues.addAll(analyze(torsoAngle))

        // Check shoulder-to-handlebar offset if calibration available
        calibration.handlebar?.let { handlebar ->
            val offset = handlebar.x - shoulderX
            if (offset > config.maxShoulderToHandlebarOffset) {
                // Shoulder is far behind handlebar = reach too long
                val existingReachIssue = issues.find { it.type == FitIssueType.REACH }
                if (existingReachIssue != null) {
                    // Escalate severity if both indicators present
                    issues.remove(existingReachIssue)
                    issues.add(existingReachIssue.copy(
                        severity = maxOf(existingReachIssue.severity, Severity.MEDIUM),
                        description = existingReachIssue.description + 
                            " Shoulder position confirms reach is too long."
                    ))
                } else {
                    issues.add(
                        FitIssue(
                            type = FitIssueType.REACH,
                            severity = Severity.LOW,
                            description = "Shoulder is far behind handlebar (${formatPercent(offset)} offset)",
                            measuredValue = offset,
                            optimalRange = 0f..config.maxShoulderToHandlebarOffset,
                            recommendation = "Consider a shorter stem or moving handlebars closer"
                        )
                    )
                }
            }
        }

        return issues
    }

    private fun createReachTooLongIssue(torsoAngle: Float, severity: Severity): FitIssue {
        // Calculate adjustment based on how aggressive the position is
        val angleError = config.minOptimalTorsoAngle - torsoAngle
        val stemAdjustment = when {
            angleError > 10 -> "20-30mm"
            angleError > 5 -> "10-20mm"
            else -> "5-10mm"
        }

        val recommendation = buildString {
            append("Reduce reach with a shorter stem (-$stemAdjustment length). ")
            append("Alternatively: raise handlebars 10-20mm, or move saddle forward 5-10mm. ")
            if (severity == Severity.HIGH) {
                append("Your position is very aggressive, which can strain the neck, back, and shoulders. ")
            }
            append("Target torso angle: ${formatAngle(config.minOptimalTorsoAngle)}-${formatAngle(config.maxOptimalTorsoAngle)}.")
        }

        return FitIssue(
            type = FitIssueType.REACH,
            severity = severity,
            description = "Torso angle too aggressive (${formatAngle(torsoAngle)}). " +
                    "Rider is overstretched.",
            measuredValue = torsoAngle,
            optimalRange = config.minOptimalTorsoAngle..config.maxOptimalTorsoAngle,
            recommendation = recommendation
        )
    }

    private fun createReachTooShortIssue(torsoAngle: Float, severity: Severity): FitIssue {
        // Calculate adjustment based on how upright the position is
        val angleError = torsoAngle - config.maxOptimalTorsoAngle
        val stemAdjustment = when {
            angleError > 15 -> "20-30mm"
            angleError > 8 -> "10-20mm"
            else -> "5-10mm"
        }

        val recommendation = buildString {
            append("Increase reach with a longer stem (+$stemAdjustment length). ")
            append("Alternatively: lower handlebars 10-20mm, or move saddle back 5-10mm. ")
            if (severity == Severity.HIGH) {
                append("An overly upright position reduces aerodynamic efficiency and may indicate the frame is too small. ")
            }
            append("Target torso angle: ${formatAngle(config.minOptimalTorsoAngle)}-${formatAngle(config.maxOptimalTorsoAngle)}.")
        }

        return FitIssue(
            type = FitIssueType.REACH,
            severity = severity,
            description = "Torso angle too upright (${formatAngle(torsoAngle)}). " +
                    "Handlebars may be too close or too high.",
            measuredValue = torsoAngle,
            optimalRange = config.minOptimalTorsoAngle..config.maxOptimalTorsoAngle,
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
         * Quick check if torso angle is within optimal range.
         */
        fun isOptimalTorsoAngle(
            torsoAngle: Float,
            config: ReachConfig = ReachConfig()
        ): Boolean {
            return torsoAngle in config.minOptimalTorsoAngle..config.maxOptimalTorsoAngle
        }

        /**
         * Quick check if reach appears too long.
         */
        fun isReachTooLong(
            torsoAngle: Float,
            config: ReachConfig = ReachConfig()
        ): Boolean {
            return torsoAngle < config.minOptimalTorsoAngle
        }

        /**
         * Quick check if reach appears too short.
         */
        fun isReachTooShort(
            torsoAngle: Float,
            config: ReachConfig = ReachConfig()
        ): Boolean {
            return torsoAngle > config.maxOptimalTorsoAngle
        }
    }
}
