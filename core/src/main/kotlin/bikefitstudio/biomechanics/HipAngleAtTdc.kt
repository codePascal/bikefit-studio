package bikefitstudio.biomechanics

import bikefitstudio.pose.PoseFrame

/**
 * Result of hip angle computation at TDC.
 * 
 * @param hipAngle The hip angle at TDC in degrees
 * @param frameNumber The frame number where TDC was detected
 * @param timestampMs The timestamp at TDC
 * @param confidence Confidence of the measurement
 * @param isValid Whether the result is valid
 */
data class HipAngleAtTdcResult(
    val hipAngle: Float,
    val frameNumber: Long,
    val timestampMs: Long,
    val confidence: Float,
    val isValid: Boolean
) {
    companion object {
        /**
         * Creates an invalid result when computation fails.
         */
        fun invalid(): HipAngleAtTdcResult {
            return HipAngleAtTdcResult(
                hipAngle = 0f,
                frameNumber = 0L,
                timestampMs = 0L,
                confidence = 0f,
                isValid = false
            )
        }
    }
}

/**
 * Summary of hip angles at TDC across multiple cycles.
 * 
 * @param cycleCount Number of cycles analyzed
 * @param minHipAngle Minimum hip angle observed at TDC (most closed position)
 * @param averageHipAngle Average hip angle at TDC
 * @param maxHipAngle Maximum hip angle observed at TDC
 * @param standardDeviation Standard deviation of hip angles
 * @param isValid Whether the summary contains valid data
 */
data class HipAngleAtTdcSummary(
    val cycleCount: Int,
    val minHipAngle: Float,
    val averageHipAngle: Float,
    val maxHipAngle: Float,
    val standardDeviation: Float,
    val isValid: Boolean
) {
    companion object {
        /**
         * Creates an invalid summary when no valid data is available.
         */
        fun invalid(): HipAngleAtTdcSummary {
            return HipAngleAtTdcSummary(
                cycleCount = 0,
                minHipAngle = 0f,
                averageHipAngle = 0f,
                maxHipAngle = 0f,
                standardDeviation = 0f,
                isValid = false
            )
        }
    }
}

/**
 * Configuration for hip angle at TDC computation.
 * 
 * @param visibilityThreshold Minimum visibility for landmarks
 * @param detectorConfig Configuration for pedal cycle detector
 * @param useMidpoints Whether to use shoulder/hip midpoints (true) or single-side landmarks (false)
 * @param side Which side to use for single-side calculations (ignored if useMidpoints is true)
 */
data class HipAngleAtTdcConfig(
    val visibilityThreshold: Float = HipAngleCalculator.DEFAULT_VISIBILITY_THRESHOLD,
    val detectorConfig: PedalCycleDetectorConfig = PedalCycleDetectorConfig(),
    val useMidpoints: Boolean = true,
    val side: BodySide = BodySide.LEFT
)

/**
 * Computes hip angle at top dead center (TDC).
 * 
 * This class integrates:
 * - PedalCycleDetector: To detect TDC events
 * - HipAngleCalculator: To compute hip angles from pose landmarks
 * 
 * It provides a high-level interface for computing hip angles at TDC
 * from a sequence of pose frames, with automatic TDC detection and
 * calculation of minimum hip angle across multiple crank cycles.
 * 
 * The hip angle represents the opening at the hip joint:
 * - Torso vector: shoulder midpoint → hip midpoint
 * - Femur vector: hip midpoint → knee midpoint
 * - Hip angle: angle between these vectors at the hip
 * 
 * At TDC (top dead center), the pedal is at its highest point, and
 * the hip is typically at its most flexed/closed position. The minimum
 * hip angle across cycles indicates the maximum hip flexion the cyclist
 * achieves.
 * 
 * Usage:
 * ```
 * // Analyze a sequence of frames
 * val result = HipAngleAtTdc.computeFromFrames(
 *     frames = poseFrames
 * )
 * 
 * if (result.isValid) {
 *     println("Minimum hip angle at TDC: ${result.minHipAngle}°")
 *     println("Average hip angle at TDC: ${result.averageHipAngle}°")
 *     println("Analyzed ${result.cycleCount} cycles")
 * }
 * ```
 * 
 * All functions are pure and stateless - they take input and return output
 * without side effects.
 */
object HipAngleAtTdc {

    /**
     * Computes hip angles at TDC from a sequence of pose frames.
     * 
     * This function:
     * 1. Detects all TDC events in the frame sequence
     * 2. Computes hip angle at each TDC frame
     * 3. Returns a summary with minimum, average, and statistics
     * 
     * @param frames List of pose frames to analyze
     * @param config Configuration options
     * @return Summary of hip angles at TDC across all detected cycles
     */
    fun computeFromFrames(
        frames: List<PoseFrame>,
        config: HipAngleAtTdcConfig = HipAngleAtTdcConfig()
    ): HipAngleAtTdcSummary {
        if (frames.isEmpty()) {
            return HipAngleAtTdcSummary.invalid()
        }

        // Detect all TDC events
        val tdcEvents = PedalCycleDetector.analyzeFrameSequence(
            frames = frames,
            side = config.side,
            config = config.detectorConfig
        ).filter { it.type == PedalExtremum.TDC }

        if (tdcEvents.isEmpty()) {
            return HipAngleAtTdcSummary.invalid()
        }

        // Create a map of frame number to pose frame for quick lookup
        val frameMap = frames.associateBy { it.frameNumber }

        // Compute hip angle at each TDC frame
        val hipAngles = mutableListOf<Float>()
        
        for (event in tdcEvents) {
            val frame = frameMap[event.frameNumber] ?: continue
            
            val hipAngleResult = if (config.useMidpoints) {
                HipAngleCalculator.calculateHipAngleFromMidpoints(
                    poseFrame = frame,
                    visibilityThreshold = config.visibilityThreshold
                )
            } else {
                HipAngleCalculator.calculateHipAngle(
                    poseFrame = frame,
                    side = config.side,
                    visibilityThreshold = config.visibilityThreshold
                )
            }
            
            if (hipAngleResult.isValid) {
                hipAngles.add(hipAngleResult.angle)
            }
        }

        if (hipAngles.isEmpty()) {
            return HipAngleAtTdcSummary.invalid()
        }

        // Calculate statistics
        val average = hipAngles.average().toFloat()
        val min = hipAngles.minOrNull() ?: 0f
        val max = hipAngles.maxOrNull() ?: 0f
        val stdDev = calculateStandardDeviation(hipAngles, average)

        return HipAngleAtTdcSummary(
            cycleCount = hipAngles.size,
            minHipAngle = min,
            averageHipAngle = average,
            maxHipAngle = max,
            standardDeviation = stdDev,
            isValid = true
        )
    }

    /**
     * Computes hip angle at a single TDC frame.
     * 
     * This is useful when you already know which frame represents TDC
     * and just need to compute the hip angle at that specific frame.
     * 
     * @param frame The pose frame at TDC
     * @param config Configuration options
     * @return Result containing the hip angle at TDC
     */
    fun computeAtFrame(
        frame: PoseFrame,
        config: HipAngleAtTdcConfig = HipAngleAtTdcConfig()
    ): HipAngleAtTdcResult {
        val hipAngleResult = if (config.useMidpoints) {
            HipAngleCalculator.calculateHipAngleFromMidpoints(
                poseFrame = frame,
                visibilityThreshold = config.visibilityThreshold
            )
        } else {
            HipAngleCalculator.calculateHipAngle(
                poseFrame = frame,
                side = config.side,
                visibilityThreshold = config.visibilityThreshold
            )
        }

        if (!hipAngleResult.isValid) {
            return HipAngleAtTdcResult.invalid()
        }

        return HipAngleAtTdcResult(
            hipAngle = hipAngleResult.angle,
            frameNumber = frame.frameNumber,
            timestampMs = frame.timestampMs,
            confidence = hipAngleResult.confidence,
            isValid = true
        )
    }

    /**
     * Computes hip angles at all detected TDC frames.
     * 
     * This returns a list of individual measurements at each TDC,
     * rather than an aggregated summary.
     * 
     * @param frames List of pose frames to analyze
     * @param config Configuration options
     * @return List of hip angle results at each detected TDC
     */
    fun computeAtAllTdcFrames(
        frames: List<PoseFrame>,
        config: HipAngleAtTdcConfig = HipAngleAtTdcConfig()
    ): List<HipAngleAtTdcResult> {
        if (frames.isEmpty()) {
            return emptyList()
        }

        // Detect all TDC events
        val tdcEvents = PedalCycleDetector.analyzeFrameSequence(
            frames = frames,
            side = config.side,
            config = config.detectorConfig
        ).filter { it.type == PedalExtremum.TDC }

        if (tdcEvents.isEmpty()) {
            return emptyList()
        }

        // Create a map of frame number to pose frame for quick lookup
        val frameMap = frames.associateBy { it.frameNumber }

        // Compute hip angle at each TDC frame
        val results = mutableListOf<HipAngleAtTdcResult>()
        
        for (event in tdcEvents) {
            val frame = frameMap[event.frameNumber] ?: continue
            
            val hipAngleResult = if (config.useMidpoints) {
                HipAngleCalculator.calculateHipAngleFromMidpoints(
                    poseFrame = frame,
                    visibilityThreshold = config.visibilityThreshold
                )
            } else {
                HipAngleCalculator.calculateHipAngle(
                    poseFrame = frame,
                    side = config.side,
                    visibilityThreshold = config.visibilityThreshold
                )
            }
            
            if (hipAngleResult.isValid) {
                results.add(
                    HipAngleAtTdcResult(
                        hipAngle = hipAngleResult.angle,
                        frameNumber = frame.frameNumber,
                        timestampMs = frame.timestampMs,
                        confidence = hipAngleResult.confidence,
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
