package bikefitstudio.biomechanics

import bikefitstudio.pose.PoseFrame

/**
 * Result of ankle flexion angle computation at BDC.
 * 
 * @param ankleAngle The ankle flexion angle at BDC in degrees
 * @param side Which leg was analyzed
 * @param frameNumber The frame number where BDC was detected
 * @param timestampMs The timestamp at BDC
 * @param confidence Confidence of the measurement
 * @param isValid Whether the result is valid
 */
data class AnkleFlexionAtBdcResult(
    val ankleAngle: Float,
    val side: BodySide,
    val frameNumber: Long,
    val timestampMs: Long,
    val confidence: Float,
    val isValid: Boolean
) {
    companion object {
        /**
         * Creates an invalid result when computation fails.
         */
        fun invalid(side: BodySide): AnkleFlexionAtBdcResult {
            return AnkleFlexionAtBdcResult(
                ankleAngle = 0f,
                side = side,
                frameNumber = 0L,
                timestampMs = 0L,
                confidence = 0f,
                isValid = false
            )
        }
    }
}

/**
 * Summary of ankle flexion angles at BDC across multiple cycles.
 * 
 * @param cycleCount Number of cycles analyzed
 * @param averageAnkleAngle Average ankle flexion angle at BDC
 * @param minAnkleAngle Minimum ankle angle observed
 * @param maxAnkleAngle Maximum ankle angle observed
 * @param standardDeviation Standard deviation of ankle angles
 * @param side Which leg was analyzed
 * @param isValid Whether the summary contains valid data
 */
data class AnkleFlexionAtBdcSummary(
    val cycleCount: Int,
    val averageAnkleAngle: Float,
    val minAnkleAngle: Float,
    val maxAnkleAngle: Float,
    val standardDeviation: Float,
    val side: BodySide,
    val isValid: Boolean
) {
    companion object {
        /**
         * Creates an invalid summary when no valid data is available.
         */
        fun invalid(side: BodySide): AnkleFlexionAtBdcSummary {
            return AnkleFlexionAtBdcSummary(
                cycleCount = 0,
                averageAnkleAngle = 0f,
                minAnkleAngle = 0f,
                maxAnkleAngle = 0f,
                standardDeviation = 0f,
                side = side,
                isValid = false
            )
        }
    }
}

/**
 * Configuration for ankle flexion at BDC computation.
 * 
 * @param visibilityThreshold Minimum visibility for landmarks
 * @param detectorConfig Configuration for pedal cycle detector
 */
data class AnkleFlexionAtBdcConfig(
    val visibilityThreshold: Float = AnkleAngleCalculator.DEFAULT_VISIBILITY_THRESHOLD,
    val detectorConfig: PedalCycleDetectorConfig = PedalCycleDetectorConfig()
)

/**
 * Computes ankle flexion angle at bottom dead center (BDC).
 * 
 * This class integrates:
 * - PedalCycleDetector: To detect BDC events
 * - AnkleAngleCalculator: To compute ankle angles from pose landmarks
 * 
 * It provides a high-level interface for computing ankle flexion angles at BDC
 * from a sequence of pose frames, with automatic BDC detection and averaging
 * across multiple crank cycles.
 * 
 * Usage:
 * ```
 * // Analyze a sequence of frames
 * val result = AnkleFlexionAtBdc.computeFromFrames(
 *     frames = poseFrames,
 *     side = BodySide.LEFT
 * )
 * 
 * if (result.isValid) {
 *     println("Ankle angle at BDC: ${result.averageAnkleAngle}°")
 *     println("Analyzed ${result.cycleCount} cycles")
 * }
 * ```
 * 
 * All functions are pure and stateless - they take input and return output
 * without side effects.
 */
object AnkleFlexionAtBdc {

    /**
     * Computes ankle flexion angles at BDC from a sequence of pose frames.
     * 
     * This function:
     * 1. Detects all BDC events in the frame sequence
     * 2. Computes ankle angle at each BDC frame
     * 3. Returns a summary with average and statistics
     * 
     * @param frames List of pose frames to analyze
     * @param side Which leg to analyze
     * @param config Configuration options
     * @return Summary of ankle flexion angles at BDC across all detected cycles
     */
    fun computeFromFrames(
        frames: List<PoseFrame>,
        side: BodySide,
        config: AnkleFlexionAtBdcConfig = AnkleFlexionAtBdcConfig()
    ): AnkleFlexionAtBdcSummary {
        if (frames.isEmpty()) {
            return AnkleFlexionAtBdcSummary.invalid(side)
        }

        // Detect all BDC events
        val bdcEvents = PedalCycleDetector.analyzeFrameSequence(
            frames = frames,
            side = side,
            config = config.detectorConfig
        ).filter { it.type == PedalExtremum.BDC }

        if (bdcEvents.isEmpty()) {
            return AnkleFlexionAtBdcSummary.invalid(side)
        }

        // Create a map of frame number to pose frame for quick lookup
        val frameMap = frames.associateBy { it.frameNumber }

        // Compute ankle angle at each BDC frame
        val ankleAngles = mutableListOf<Float>()
        
        for (event in bdcEvents) {
            val frame = frameMap[event.frameNumber] ?: continue
            
            val ankleAngleResult = AnkleAngleCalculator.calculateAnkleAngle(
                poseFrame = frame,
                side = side,
                visibilityThreshold = config.visibilityThreshold
            )
            
            if (ankleAngleResult.isValid) {
                ankleAngles.add(ankleAngleResult.angle)
            }
        }

        if (ankleAngles.isEmpty()) {
            return AnkleFlexionAtBdcSummary.invalid(side)
        }

        // Calculate statistics
        val average = ankleAngles.average().toFloat()
        val min = ankleAngles.minOrNull() ?: 0f
        val max = ankleAngles.maxOrNull() ?: 0f
        val stdDev = calculateStandardDeviation(ankleAngles, average)

        return AnkleFlexionAtBdcSummary(
            cycleCount = ankleAngles.size,
            averageAnkleAngle = average,
            minAnkleAngle = min,
            maxAnkleAngle = max,
            standardDeviation = stdDev,
            side = side,
            isValid = true
        )
    }

    /**
     * Computes ankle flexion angle at a single BDC frame.
     * 
     * This is useful when you already know which frame represents BDC
     * and just need to compute the ankle angle at that specific frame.
     * 
     * @param frame The pose frame at BDC
     * @param side Which leg to analyze
     * @param visibilityThreshold Minimum visibility for landmarks
     * @return Result containing the ankle angle at BDC
     */
    fun computeAtFrame(
        frame: PoseFrame,
        side: BodySide,
        visibilityThreshold: Float = AnkleAngleCalculator.DEFAULT_VISIBILITY_THRESHOLD
    ): AnkleFlexionAtBdcResult {
        val ankleAngleResult = AnkleAngleCalculator.calculateAnkleAngle(
            poseFrame = frame,
            side = side,
            visibilityThreshold = visibilityThreshold
        )

        if (!ankleAngleResult.isValid) {
            return AnkleFlexionAtBdcResult.invalid(side)
        }

        return AnkleFlexionAtBdcResult(
            ankleAngle = ankleAngleResult.angle,
            side = side,
            frameNumber = frame.frameNumber,
            timestampMs = frame.timestampMs,
            confidence = ankleAngleResult.confidence,
            isValid = true
        )
    }

    /**
     * Computes ankle flexion angles at all detected BDC frames.
     * 
     * This returns a list of individual measurements at each BDC,
     * rather than an aggregated summary.
     * 
     * @param frames List of pose frames to analyze
     * @param side Which leg to analyze
     * @param config Configuration options
     * @return List of ankle flexion results at each detected BDC
     */
    fun computeAtAllBdcFrames(
        frames: List<PoseFrame>,
        side: BodySide,
        config: AnkleFlexionAtBdcConfig = AnkleFlexionAtBdcConfig()
    ): List<AnkleFlexionAtBdcResult> {
        if (frames.isEmpty()) {
            return emptyList()
        }

        // Detect all BDC events
        val bdcEvents = PedalCycleDetector.analyzeFrameSequence(
            frames = frames,
            side = side,
            config = config.detectorConfig
        ).filter { it.type == PedalExtremum.BDC }

        if (bdcEvents.isEmpty()) {
            return emptyList()
        }

        // Create a map of frame number to pose frame for quick lookup
        val frameMap = frames.associateBy { it.frameNumber }

        // Compute ankle angle at each BDC frame
        val results = mutableListOf<AnkleFlexionAtBdcResult>()
        
        for (event in bdcEvents) {
            val frame = frameMap[event.frameNumber] ?: continue
            
            val ankleAngleResult = AnkleAngleCalculator.calculateAnkleAngle(
                poseFrame = frame,
                side = side,
                visibilityThreshold = config.visibilityThreshold
            )
            
            if (ankleAngleResult.isValid) {
                results.add(
                    AnkleFlexionAtBdcResult(
                        ankleAngle = ankleAngleResult.angle,
                        side = side,
                        frameNumber = frame.frameNumber,
                        timestampMs = frame.timestampMs,
                        confidence = ankleAngleResult.confidence,
                        isValid = true
                    )
                )
            }
        }

        return results
    }

    /**
     * Calculates standard deviation of a list of values.
     * 
     * @param values List of values
     * @param mean Mean of the values
     * @return Standard deviation
     */
    private fun calculateStandardDeviation(values: List<Float>, mean: Float): Float {
        if (values.size < 2) {
            return 0f
        }

        val variance = values.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance).toFloat()
    }
}
