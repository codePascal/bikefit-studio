package bikefitstudio.biomechanics

import kotlin.math.abs

/**
 * Represents the detected crank position (TDC, BDC, or 3 O'Clock).
 */
enum class CrankPosition {
    /** Top Dead Center - pedal at 12 o'clock position (crank angle 355-5°) */
    TDC,
    /** Bottom Dead Center - pedal at 6 o'clock position (crank angle 175-185°) */
    BDC,
    /** 3 O'Clock position - pedal at horizontal right position (crank angle 85-95°) */
    THREE_O_CLOCK
}

/**
 * Represents a detected crank position event with timing and confidence information.
 *
 * @param position The detected crank position (TDC, BDC, or 3 O'Clock)
 * @param frameNumber Frame number where detected
 * @param timestampMs Timestamp in milliseconds
 * @param crankAngleDegrees Calculated crank angle in degrees [0, 360)
 * @param side Which leg was analyzed
 * @param confidence Detection confidence (0-1), higher is better
 */
data class CrankPositionEvent(
    val position: CrankPosition,
    val frameNumber: Long,
    val timestampMs: Long,
    val crankAngleDegrees: Float,
    val side: BodySide,
    val confidence: Float
)

/**
 * Configuration for crank position detection.
 *
 * @param tdcCrankAngleMin Minimum crank angle for TDC (12 o'clock, default 355°)
 * @param tdcCrankAngleMax Maximum crank angle for TDC (wraps around 0°, default 5°)
 * @param bdcCrankAngleMin Minimum crank angle for BDC (6 o'clock, default 175°)
 * @param bdcCrankAngleMax Maximum crank angle for BDC (default 185°)
 * @param threeOClockAngleMin Minimum crank angle for 3 O'Clock (default 85°)
 * @param threeOClockAngleMax Maximum crank angle for 3 O'Clock (default 95°)
 */
data class CrankPositionDetectorConfig(
    val tdcCrankAngleMin: Float = 355f,
    val tdcCrankAngleMax: Float = 5f,
    val bdcCrankAngleMin: Float = 175f,
    val bdcCrankAngleMax: Float = 185f,
    val threeOClockAngleMin: Float = 85f,
    val threeOClockAngleMax: Float = 95f
)

/**
 * Generic crank position detector that identifies TDC (12 o'clock), BDC (6 o'clock),
 * and 3 O'Clock positions based on crank angle.
 *
 * Uses the crank angle provided by [CrankAngleTracker] (elliptical model) or raw
 * atan2 calculations to determine which critical position the crank is at.
 *
 * This replaces the older ThreeOClockDetector with a unified approach that can
 * detect all three key pedal positions using consistent crank angle logic.
 *
 * Usage:
 * ```
 * val event = CrankPositionDetector.detectPosition(
 *     crankAngle = 90f,
 *     frameNumber = 42,
 *     timestampMs = 1000,
 *     side = BodySide.LEFT
 * )
 *
 * when (event?.position) {
 *     CrankPosition.TDC -> println("Top dead center detected")
 *     CrankPosition.BDC -> println("Bottom dead center detected")
 *     CrankPosition.THREE_O_CLOCK -> println("3 o'clock position detected")
 *     else -> println("Position not at critical point")
 * }
 * ```
 */
object CrankPositionDetector {

    /**
     * Detects the crank position for a given crank angle.
     *
     * @param crankAngle Crank angle in degrees [0, 360)
     * @param frameNumber Frame number for the measurement
     * @param timestampMs Timestamp in milliseconds
     * @param side Which leg was analyzed
     * @param config Detection configuration with angle ranges
     * @return CrankPositionEvent if angle matches a critical position, null otherwise
     */
    fun detectPosition(
        crankAngle: Float,
        frameNumber: Long,
        timestampMs: Long,
        side: BodySide = BodySide.LEFT,
        config: CrankPositionDetectorConfig = CrankPositionDetectorConfig()
    ): CrankPositionEvent? {
        // Check TDC first (wraps around 0°/360°)
        if (isInTdcRange(crankAngle, config)) {
            val confidence = calculateTdcConfidence(crankAngle, config)
            return CrankPositionEvent(
                position = CrankPosition.TDC,
                frameNumber = frameNumber,
                timestampMs = timestampMs,
                crankAngleDegrees = crankAngle,
                side = side,
                confidence = confidence
            )
        }

        // Check BDC (bottom dead center at 180°)
        if (isInBdcRange(crankAngle, config)) {
            val confidence = calculateBdcConfidence(crankAngle, config)
            return CrankPositionEvent(
                position = CrankPosition.BDC,
                frameNumber = frameNumber,
                timestampMs = timestampMs,
                crankAngleDegrees = crankAngle,
                side = side,
                confidence = confidence
            )
        }

        // Check 3 O'Clock (pedal at 90°)
        if (isInThreeOClockRange(crankAngle, config)) {
            val confidence = calculateThreeOClockConfidence(crankAngle, config)
            return CrankPositionEvent(
                position = CrankPosition.THREE_O_CLOCK,
                frameNumber = frameNumber,
                timestampMs = timestampMs,
                crankAngleDegrees = crankAngle,
                side = side,
                confidence = confidence
            )
        }

        return null
    }

    /**
     * Checks if a crank angle is within the TDC range (12 o'clock, 355-5°).
     */
    @Suppress("SENSELESS_COMPARISON")
    private fun isInTdcRange(angle: Float, config: CrankPositionDetectorConfig): Boolean {
        return angle >= config.tdcCrankAngleMin || angle <= config.tdcCrankAngleMax
    }

    /**
     * Checks if a crank angle is within the BDC range (6 o'clock, 175-185°).
     */
    private fun isInBdcRange(angle: Float, config: CrankPositionDetectorConfig): Boolean {
        return angle >= config.bdcCrankAngleMin && angle <= config.bdcCrankAngleMax
    }

    /**
     * Checks if a crank angle is within the 3 O'Clock range (85-95°).
     */
    private fun isInThreeOClockRange(angle: Float, config: CrankPositionDetectorConfig): Boolean {
        return angle >= config.threeOClockAngleMin && angle <= config.threeOClockAngleMax
    }

    /**
     * Calculates confidence for TDC detection based on proximity to 0°/360°.
     *
     * Confidence is highest when angle is closest to exactly 0° or 360°.
     */
    private fun calculateTdcConfidence(angle: Float, config: CrankPositionDetectorConfig): Float {
        val distanceToZero = if (angle <= 5f) {
            angle  // Distance from 0°
        } else {
            360f - angle  // Distance from 360°
        }

        val maxDistance = maxOf(config.tdcCrankAngleMax, 360f - config.tdcCrankAngleMin)
        val normalizedDistance = (distanceToZero / maxDistance).coerceIn(0f, 1f)

        return 1f - (normalizedDistance * 0.5f)  // Range: 0.5 to 1.0
    }

    /**
     * Calculates confidence for BDC detection based on proximity to 180°.
     *
     * Confidence is highest when angle is closest to exactly 180°.
     */
    private fun calculateBdcConfidence(angle: Float, config: CrankPositionDetectorConfig): Float {
        val distanceTo180 = abs(angle - 180f)
        val maxDistance = (config.bdcCrankAngleMax - config.bdcCrankAngleMin) / 2f
        val normalizedDistance = (distanceTo180 / maxDistance).coerceIn(0f, 1f)

        return 1f - (normalizedDistance * 0.5f)  // Range: 0.5 to 1.0
    }

    /**
     * Calculates confidence for 3 O'Clock detection based on proximity to 90°.
     *
     * Confidence is highest when angle is closest to exactly 90°.
     */
    private fun calculateThreeOClockConfidence(angle: Float, config: CrankPositionDetectorConfig): Float {
        val distanceTo90 = abs(angle - 90f)
        val maxDistance = config.threeOClockAngleMax - 90f  // 5° tolerance
        val normalizedDistance = (distanceTo90 / maxDistance).coerceIn(0f, 1f)

        return 1f - (normalizedDistance * 0.5f)  // Range: 0.5 to 1.0
    }

    /**
     * Detects all crank positions in a sequence of crank angles.
     *
     * Useful for analyzing a complete pedal cycle and finding all critical positions.
     *
     * @param measurements List of angle measurements with frame info
     * @param config Detection configuration
     * @return List of detected CrankPositionEvents in chronological order
     */
    fun detectAll(
        measurements: List<CrankAngleMeasurement>,
        config: CrankPositionDetectorConfig = CrankPositionDetectorConfig()
    ): List<CrankPositionEvent> {
        return measurements.mapNotNull { measurement ->
            detectPosition(
                crankAngle = measurement.crankAngleDegrees,
                frameNumber = measurement.frameNumber,
                timestampMs = measurement.timestampMs,
                side = measurement.side,
                config = config
            )
        }
    }

    /**
     * Finds the best (highest confidence) event for each crank position.
     *
     * @param measurements List of angle measurements
     * @param config Detection configuration
     * @return Map of CrankPosition to best CrankPositionEvent for that position
     */
    fun findBestOfEach(
        measurements: List<CrankAngleMeasurement>,
        config: CrankPositionDetectorConfig = CrankPositionDetectorConfig()
    ): Map<CrankPosition, CrankPositionEvent> {
        val allEvents = detectAll(measurements, config)
        val resultMap = mutableMapOf<CrankPosition, CrankPositionEvent>()

        for (event in allEvents) {
            val existing = resultMap[event.position]
            if (existing == null || event.confidence > existing.confidence) {
                resultMap[event.position] = event
            }
        }

        return resultMap
    }

    /**
     * Simple data class for crank angle measurements in batch analysis.
     */
    data class CrankAngleMeasurement(
        val frameNumber: Long,
        val timestampMs: Long,
        val crankAngleDegrees: Float,
        val side: BodySide
    )
}
