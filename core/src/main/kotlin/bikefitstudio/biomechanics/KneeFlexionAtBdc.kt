package bikefitstudio.biomechanics

import bikefitstudio.pose.PoseFrame

/**
 * Result of knee flexion angle computation at BDC.
 * 
 * @param kneeAngle The knee flexion angle at BDC in degrees
 * @param side Which leg was analyzed
 * @param frameNumber The frame number where BDC was detected
 * @param timestampMs The timestamp at BDC
 * @param confidence Confidence of the measurement
 * @param isValid Whether the result is valid
 */
data class KneeFlexionAtBdcResult(
    val kneeAngle: Float,
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
        fun invalid(side: BodySide): KneeFlexionAtBdcResult {
            return KneeFlexionAtBdcResult(
                kneeAngle = 0f,
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
 * Summary of knee flexion angles at BDC across multiple cycles.
 * 
 * @param cycleCount Number of cycles analyzed
 * @param averageKneeAngle Average knee flexion angle at BDC
 * @param minKneeAngle Minimum knee angle observed
 * @param maxKneeAngle Maximum knee angle observed
 * @param standardDeviation Standard deviation of knee angles
 * @param side Which leg was analyzed
 * @param isValid Whether the summary contains valid data
 */
data class KneeFlexionAtBdcSummary(
    val cycleCount: Int,
    val averageKneeAngle: Float,
    val minKneeAngle: Float,
    val maxKneeAngle: Float,
    val standardDeviation: Float,
    val side: BodySide,
    val isValid: Boolean
) {
    companion object {
        /**
         * Creates an invalid summary when no valid data is available.
         */
        fun invalid(side: BodySide): KneeFlexionAtBdcSummary {
            return KneeFlexionAtBdcSummary(
                cycleCount = 0,
                averageKneeAngle = 0f,
                minKneeAngle = 0f,
                maxKneeAngle = 0f,
                standardDeviation = 0f,
                side = side,
                isValid = false
            )
        }
    }
}

/**
 * Configuration for knee flexion at BDC computation.
 * 
 * @param visibilityThreshold Minimum visibility for landmarks
 * @param detectorConfig Configuration for pedal cycle detector
 */
data class KneeFlexionAtBdcConfig(
    val visibilityThreshold: Float = KneeAngleCalculator.DEFAULT_VISIBILITY_THRESHOLD,
    val detectorConfig: PedalCycleDetectorConfig = PedalCycleDetectorConfig()
)

/**
 * Computes knee flexion angle at bottom dead center (BDC).
 * 
 * This class integrates:
 * - PedalCycleDetector: To detect BDC events
 * - KneeAngleCalculator: To compute knee angles from pose landmarks
 * 
 * It provides a high-level interface for computing knee flexion angles at BDC
 * from a sequence of pose frames, with automatic BDC detection and averaging
 * across multiple crank cycles.
 * 
 * Usage:
 * ```
 * // Analyze a sequence of frames
 * val result = KneeFlexionAtBdc.computeFromFrames(
 *     frames = poseFrames,
 *     side = BodySide.LEFT
 * )
 * 
 * if (result.isValid) {
 *     println("Knee angle at BDC: ${result.averageKneeAngle}°")
 *     println("Analyzed ${result.cycleCount} cycles")
 * }
 * ```
 * 
 * All functions are pure and stateless - they take input and return output
 * without side effects.
 */
object KneeFlexionAtBdc {

    /**
     * Computes knee flexion angles at BDC from a sequence of pose frames.
     * 
     * This function:
     * 1. Detects all BDC events in the frame sequence
     * 2. Computes knee angle at each BDC frame
     * 3. Returns a summary with average and statistics
     * 
     * @param frames List of pose frames to analyze
     * @param side Which leg to analyze
     * @param config Configuration options
     * @return Summary of knee flexion angles at BDC across all detected cycles
     */
    fun computeFromFrames(
        frames: List<PoseFrame>,
        side: BodySide,
        config: KneeFlexionAtBdcConfig = KneeFlexionAtBdcConfig()
    ): KneeFlexionAtBdcSummary {
        if (frames.isEmpty()) {
            return KneeFlexionAtBdcSummary.invalid(side)
        }

        // Detect all BDC events
        val bdcEvents = PedalCycleDetector.analyzeFrameSequence(
            frames = frames,
            side = side,
            config = config.detectorConfig
        ).filter { it.type == PedalExtremum.BDC }

        if (bdcEvents.isEmpty()) {
            return KneeFlexionAtBdcSummary.invalid(side)
        }

        // Create a map of frame number to pose frame for quick lookup
        val frameMap = frames.associateBy { it.frameNumber }

        // Compute knee angle at each BDC frame
        val kneeAngles = mutableListOf<Float>()
        
        // Search window to find true max extension around the detected BDC
        // BDC (lowest ankle point) doesn't always perfectly align with max knee extension
        // due to ankling technique and seat position.
        val searchWindowHalfWidth = 5 
        
        for (event in bdcEvents) {
            val centerFrameNum = event.frameNumber
            var maxKneeAngleForCycle = 0f
            var foundValid = false

            // Search a small window around BDC to find the peak extension
            for (offset in -searchWindowHalfWidth..searchWindowHalfWidth) {
                val targetFrameNum = centerFrameNum + offset
                val frame = frameMap[targetFrameNum] ?: continue
                
                val kneeAngleResult = KneeAngleCalculator.calculateKneeAngle(
                    poseFrame = frame,
                    side = side,
                    visibilityThreshold = config.visibilityThreshold
                )
                
                if (kneeAngleResult.isValid) {
                    if (kneeAngleResult.angle > maxKneeAngleForCycle) {
                        maxKneeAngleForCycle = kneeAngleResult.angle
                        foundValid = true
                    }
                }
            }

            if (foundValid) {
                kneeAngles.add(maxKneeAngleForCycle)
            }
        }

        if (kneeAngles.isEmpty()) {
            return KneeFlexionAtBdcSummary.invalid(side)
        }

        // Calculate statistics
        val average = kneeAngles.average().toFloat()
        val min = kneeAngles.minOrNull() ?: 0f
        val max = kneeAngles.maxOrNull() ?: 0f
        val stdDev = calculateStandardDeviation(kneeAngles, average)

        return KneeFlexionAtBdcSummary(
            cycleCount = kneeAngles.size,
            averageKneeAngle = average,
            minKneeAngle = min,
            maxKneeAngle = max,
            standardDeviation = stdDev,
            side = side,
            isValid = true
        )
    }

    /**
     * Computes knee flexion angle at a single BDC frame.
     * 
     * This is useful when you already know which frame represents BDC
     * and just need to compute the knee angle at that specific frame.
     * 
     * @param frame The pose frame at BDC
     * @param side Which leg to analyze
     * @param visibilityThreshold Minimum visibility for landmarks
     * @return Result containing the knee angle at BDC
     */
    fun computeAtFrame(
        frame: PoseFrame,
        side: BodySide,
        visibilityThreshold: Float = KneeAngleCalculator.DEFAULT_VISIBILITY_THRESHOLD
    ): KneeFlexionAtBdcResult {
        val kneeAngleResult = KneeAngleCalculator.calculateKneeAngle(
            poseFrame = frame,
            side = side,
            visibilityThreshold = visibilityThreshold
        )

        if (!kneeAngleResult.isValid) {
            return KneeFlexionAtBdcResult.invalid(side)
        }

        return KneeFlexionAtBdcResult(
            kneeAngle = kneeAngleResult.angle,
            side = side,
            frameNumber = frame.frameNumber,
            timestampMs = frame.timestampMs,
            confidence = kneeAngleResult.confidence,
            isValid = true
        )
    }

    /**
     * Computes knee flexion angles at all detected BDC frames.
     * 
     * This returns a list of individual measurements at each BDC,
     * rather than an aggregated summary.
     * 
     * @param frames List of pose frames to analyze
     * @param side Which leg to analyze
     * @param config Configuration options
     * @return List of knee flexion results at each detected BDC
     */
    fun computeAtAllBdcFrames(
        frames: List<PoseFrame>,
        side: BodySide,
        config: KneeFlexionAtBdcConfig = KneeFlexionAtBdcConfig()
    ): List<KneeFlexionAtBdcResult> {
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

        // Compute knee angle at each BDC frame
        val results = mutableListOf<KneeFlexionAtBdcResult>()
        
        for (event in bdcEvents) {
            val frame = frameMap[event.frameNumber] ?: continue
            
            val kneeAngleResult = KneeAngleCalculator.calculateKneeAngle(
                poseFrame = frame,
                side = side,
                visibilityThreshold = config.visibilityThreshold
            )
            
            if (kneeAngleResult.isValid) {
                results.add(
                    KneeFlexionAtBdcResult(
                        kneeAngle = kneeAngleResult.angle,
                        side = side,
                        frameNumber = frame.frameNumber,
                        timestampMs = frame.timestampMs,
                        confidence = kneeAngleResult.confidence,
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
