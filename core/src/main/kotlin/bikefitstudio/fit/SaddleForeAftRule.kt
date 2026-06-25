package bikefitstudio.fit

import bikefitstudio.biomechanics.BikeRelativeMeasurements
import bikefitstudio.calibration.BikeCalibration
import bikefitstudio.pose.Landmark
import bikefitstudio.pose.PoseFrame
import bikefitstudio.pose.PoseLandmarkIndex

/**
 * Configuration for saddle fore/aft analysis thresholds.
 * 
 * The KOPS (Knee Over Pedal Spindle) method measures the horizontal position
 * of the knee relative to the pedal spindle when the crank is at 3 o'clock.
 * 
 * All measurements are in normalized coordinates (0-1 range relative to image).
 */
data class SaddleForeAftConfig(
    /**
     * Maximum allowed forward offset of knee from pedal spindle.
     * Positive values mean knee is forward of pedal.
     * 
     * If knee is more than this distance forward, saddle is too far back.
     * Default: 3% of frame width forward tolerance.
     */
    val maxForwardOffset: Float = MAX_FORWARD_OFFSET,

    /**
     * Maximum allowed backward offset of knee from pedal spindle.
     * 
     * If knee is more than this distance behind pedal, saddle is too far forward.
     * Default: 3% of frame width backward tolerance.
     */
    val maxBackwardOffset: Float = MAX_BACKWARD_OFFSET,

    /**
     * Threshold for high severity issues.
     * When offset exceeds this multiple of the base threshold, severity is HIGH.
     */
    val highSeverityMultiplier: Float = HIGH_SEVERITY_MULTIPLIER,

    /**
     * Minimum landmark visibility to trust the measurement.
     */
    val minVisibility: Float = MIN_VISIBILITY
) {
    companion object {
        /**
         * Maximum forward offset of knee from pedal spindle.
         * 
         * 3% of frame width is approximately 2-3cm on most setups.
         * Knee forward of pedal suggests saddle is too far back,
         * which can cause:
         * - Reduced power to the pedals
         * - Increased quad strain
         * - Hip angle too closed
         */
        const val MAX_FORWARD_OFFSET = 0.03f

        /**
         * Maximum backward offset of knee from pedal spindle.
         * 
         * Knee behind pedal suggests saddle is too far forward,
         * which can cause:
         * - Excessive pressure on hands
         * - Knee tracking issues
         * - Hamstring strain
         */
        const val MAX_BACKWARD_OFFSET = 0.03f

        /**
         * Multiplier for high severity threshold.
         * Offsets greater than 2x the base threshold are high severity.
         */
        const val HIGH_SEVERITY_MULTIPLIER = 2.0f

        /**
         * Minimum visibility for knee landmark.
         */
        const val MIN_VISIBILITY = 0.5f
    }
}

/**
 * Result of knee-over-pedal-spindle analysis.
 * 
 * @param kneeX Normalized X position of knee
 * @param pedalSpindleX Normalized X position of pedal spindle (from calibration)
 * @param offset Horizontal offset: positive = knee forward, negative = knee behind
 * @param isValid Whether the measurement is valid
 */
data class KopsResult(
    val kneeX: Float,
    val pedalSpindleX: Float,
    val offset: Float,
    val isValid: Boolean
) {
    /**
     * True if knee is forward of pedal spindle.
     */
    val isKneeForward: Boolean
        get() = offset > 0

    /**
     * True if knee is behind pedal spindle.
     */
    val isKneeBehind: Boolean
        get() = offset < 0

    companion object {
        val INVALID = KopsResult(
            kneeX = 0f,
            pedalSpindleX = 0f,
            offset = 0f,
            isValid = false
        )
    }
}

/**
 * Analyzes saddle fore/aft position using KOPS (Knee Over Pedal Spindle) method.
 * 
 * This rule compares the horizontal position of the knee to the pedal spindle
 * when the crank is at the 3 o'clock position (horizontal forward).
 * 
 * KOPS analysis:
 * - Knee directly over pedal spindle: Optimal (neutral starting point)
 * - Knee forward of pedal: Saddle may be too far back
 * - Knee behind pedal: Saddle may be too far forward
 * 
 * Note: KOPS is a traditional starting point. Some riders benefit from
 * slightly different positions based on riding style:
 * - Time trial/triathlon: Often more forward
 * - Climbing: Often more forward
 * - Sprinting: Often neutral to slightly back
 * 
 * Usage:
 * ```
 * val rule = SaddleForeAftRule()
 * val kopsResult = rule.measureKops(poseFrame, bikeCalibration, BodySide.LEFT)
 * val issues = rule.analyze(kopsResult)
 * ```
 */
class SaddleForeAftRule(
    private val config: SaddleForeAftConfig = SaddleForeAftConfig()
) {
    /**
     * Measures KOPS from a pose frame and bike calibration.
     * 
     * Uses the calibrated bottom bracket position and crank length (if available)
     * to calculate the actual pedal position at 3 o'clock.
     * 
     * If calibration includes crank length, uses enhanced bike-relative KOPS calculation
     * that normalizes by femur length for more accurate, scale-independent analysis.
     * 
     * @param poseFrame The pose frame to analyze (should be at ~3 o'clock crank position)
     * @param calibration Bike calibration with bottom bracket and optionally crank length
     * @param side Which side to measure (left or right knee)
     * @return KopsResult with the measurement
     */
    fun measureKops(
        poseFrame: PoseFrame,
        calibration: BikeCalibration,
        side: bikefitstudio.biomechanics.BodySide
    ): KopsResult {
        // Need bottom bracket for pedal spindle reference
        val bottomBracket = calibration.bottomBracket ?: return KopsResult.INVALID

        // Get knee and hip landmarks
        val kneeIndex = if (side == bikefitstudio.biomechanics.BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_KNEE
        } else {
            PoseLandmarkIndex.RIGHT_KNEE
        }
        
        val hipIndex = if (side == bikefitstudio.biomechanics.BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_HIP
        } else {
            PoseLandmarkIndex.RIGHT_HIP
        }

        if (poseFrame.landmarks.size <= kneeIndex || poseFrame.landmarks.size <= hipIndex) {
            return KopsResult.INVALID
        }

        val knee = poseFrame.landmarks[kneeIndex]
        val hip = poseFrame.landmarks[hipIndex]
        
        if (!knee.isVisible(config.minVisibility)) {
            return KopsResult.INVALID
        }

        // Calculate pedal position at 3 o'clock
        // If crank length is available, use it for more accurate pedal position
        val pedalSpindleX = calibration.getPedalPositionAt3OClock() ?: bottomBracket.x

        // Try to use enhanced bike-relative KOPS if hip is visible and calibration has crank length
        val offset = if (hip.isVisible(config.minVisibility) && calibration.crankLengthMm != null) {
            // Use normalized KOPS calculation (scale-independent)
            val normalizedKops = BikeRelativeMeasurements.computeKOPSWithBottomBracket(
                knee = knee,
                hip = hip,
                calibration = calibration
            )
            
            // Convert normalized KOPS to pixel-space offset for compatibility
            // Scale by a typical femur-to-frame-width ratio (~0.2)
            normalizedKops?.let { it * 0.2f } ?: (knee.x - pedalSpindleX)
        } else {
            // Fallback to basic calculation
            knee.x - pedalSpindleX
        }

        return KopsResult(
            kneeX = knee.x,
            pedalSpindleX = pedalSpindleX,
            offset = offset,
            isValid = true
        )
    }

    /**
     * Measures KOPS directly from knee position and calibration.
     * 
     * @param kneeX Normalized X position of knee
     * @param calibration Bike calibration
     * @return KopsResult
     */
    fun measureKops(
        kneeX: Float,
        calibration: BikeCalibration
    ): KopsResult {
        val bottomBracket = calibration.bottomBracket ?: return KopsResult.INVALID
        val offset = kneeX - bottomBracket.x

        return KopsResult(
            kneeX = kneeX,
            pedalSpindleX = bottomBracket.x,
            offset = offset,
            isValid = true
        )
    }

    /**
     * Analyzes a KOPS result for fit issues.
     * 
     * @param kopsResult The KOPS measurement
     * @return List of detected FitIssues (may be empty)
     */
    fun analyze(kopsResult: KopsResult): List<FitIssue> {
        if (!kopsResult.isValid) {
            return emptyList()
        }

        val issues = mutableListOf<FitIssue>()
        val absOffset = kotlin.math.abs(kopsResult.offset)

        when {
            // Knee too far forward -> saddle too far back
            kopsResult.offset > config.maxForwardOffset -> {
                val severity = if (absOffset > config.maxForwardOffset * config.highSeverityMultiplier) {
                    Severity.HIGH
                } else if (absOffset > config.maxForwardOffset * 1.5f) {
                    Severity.MEDIUM
                } else {
                    Severity.LOW
                }

                // Calculate adjustment: roughly 10mm saddle movement per 1% normalized offset
                val offsetPercent = (absOffset * 100).toInt()
                val adjustmentMm = (offsetPercent * 10).coerceIn(5, 30)

                val recommendation = buildString {
                    append("Move saddle forward by $adjustmentMm-${adjustmentMm + 5}mm on the rails. ")
                    append("Your knee is too far forward of the pedal spindle at 3 o'clock. ")
                    append("This can increase quad strain and reduce power transfer. ")
                    append("After adjusting, verify knee is directly over or slightly behind the pedal spindle.")
                }

                issues.add(
                    FitIssue(
                        type = FitIssueType.SADDLE_FORE_AFT,
                        severity = severity,
                        description = "Knee is forward of pedal spindle at 3 o'clock (${formatOffset(kopsResult.offset)} offset)",
                        measuredValue = kopsResult.offset,
                        optimalRange = -config.maxBackwardOffset..config.maxForwardOffset,
                        recommendation = recommendation
                    )
                )
            }

            // Knee too far back -> saddle too far forward
            kopsResult.offset < -config.maxBackwardOffset -> {
                val severity = if (absOffset > config.maxBackwardOffset * config.highSeverityMultiplier) {
                    Severity.HIGH
                } else if (absOffset > config.maxBackwardOffset * 1.5f) {
                    Severity.MEDIUM
                } else {
                    Severity.LOW
                }

                // Calculate adjustment: roughly 10mm saddle movement per 1% normalized offset
                val offsetPercent = (absOffset * 100).toInt()
                val adjustmentMm = (offsetPercent * 10).coerceIn(5, 30)

                val recommendation = buildString {
                    append("Move saddle back by $adjustmentMm-${adjustmentMm + 5}mm on the rails. ")
                    append("Your knee is behind the pedal spindle at 3 o'clock. ")
                    append("This can cause excessive hand pressure and hamstring strain. ")
                    append("After adjusting, verify knee is directly over or slightly behind the pedal spindle.")
                }

                issues.add(
                    FitIssue(
                        type = FitIssueType.SADDLE_FORE_AFT,
                        severity = severity,
                        description = "Knee is behind pedal spindle at 3 o'clock (${formatOffset(kopsResult.offset)} offset)",
                        measuredValue = kopsResult.offset,
                        optimalRange = -config.maxBackwardOffset..config.maxForwardOffset,
                        recommendation = recommendation
                    )
                )
            }
        }

        return issues
    }

    /**
     * Analyzes pose frame directly for fore/aft issues.
     * 
     * @param poseFrame The pose frame to analyze
     * @param calibration Bike calibration
     * @param side Which side to analyze
     * @return List of detected FitIssues
     */
    fun analyze(
        poseFrame: PoseFrame,
        calibration: BikeCalibration,
        side: bikefitstudio.biomechanics.BodySide
    ): List<FitIssue> {
        val kopsResult = measureKops(poseFrame, calibration, side)
        return analyze(kopsResult)
    }

    /**
     * Analyzes average KOPS offset from multiple measurements.
     * 
     * @param averageOffset Average offset from multiple 3 o'clock captures
     * @return List of detected FitIssues
     */
    fun analyzeAverageOffset(averageOffset: Float): List<FitIssue> {
        val kopsResult = KopsResult(
            kneeX = 0f, // Not meaningful for average
            pedalSpindleX = 0f,
            offset = averageOffset,
            isValid = true
        )
        return analyze(kopsResult)
    }

    private fun formatOffset(offset: Float): String {
        val percent = (kotlin.math.abs(offset) * 100).toInt()
        val direction = if (offset > 0) "forward" else "back"
        return "$percent% $direction"
    }

    companion object {
        /**
         * Quick check if offset is within optimal range.
         */
        fun isOptimalPosition(
            offset: Float,
            config: SaddleForeAftConfig = SaddleForeAftConfig()
        ): Boolean {
            return offset in -config.maxBackwardOffset..config.maxForwardOffset
        }

        /**
         * Quick check if saddle appears too far back.
         */
        fun isSaddleTooFarBack(
            offset: Float,
            config: SaddleForeAftConfig = SaddleForeAftConfig()
        ): Boolean {
            return offset > config.maxForwardOffset
        }

        /**
         * Quick check if saddle appears too far forward.
         */
        fun isSaddleTooFarForward(
            offset: Float,
            config: SaddleForeAftConfig = SaddleForeAftConfig()
        ): Boolean {
            return offset < -config.maxBackwardOffset
        }
    }
}
