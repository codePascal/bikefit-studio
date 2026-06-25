package bikefitstudio.biomechanics

import bikefitstudio.pose.Landmark
import bikefitstudio.pose.PoseFrame
import bikefitstudio.pose.PoseLandmarkIndex
import kotlin.math.sqrt

/**
 * Result of hip rocking analysis.
 * 
 * @param amplitude Peak-to-peak amplitude of hip vertical motion (in normalized coords)
 * @param variance Statistical variance of hip Y positions
 * @param isExcessive Whether the rocking exceeds the threshold
 * @param side Which side was analyzed
 * @param sampleCount Number of samples used in the analysis
 * @param minY Minimum hip Y observed
 * @param maxY Maximum hip Y observed
 */
data class HipRockingResult(
    val amplitude: Float,
    val variance: Float,
    val isExcessive: Boolean,
    val side: BodySide,
    val sampleCount: Int,
    val minY: Float,
    val maxY: Float
) {
    companion object {
        /**
         * Creates an invalid result when insufficient data is available.
         */
        fun invalid(side: BodySide): HipRockingResult {
            return HipRockingResult(
                amplitude = 0f,
                variance = 0f,
                isExcessive = false,
                side = side,
                sampleCount = 0,
                minY = 0f,
                maxY = 0f
            )
        }
    }
}

/**
 * Configuration for the hip rocking detector.
 * 
 * @param excessiveAmplitudeThreshold Amplitude above which rocking is considered excessive
 *        (in normalized coordinates, where 1.0 = full image height)
 * @param minSamplesRequired Minimum number of valid samples needed for analysis
 * @param visibilityThreshold Minimum visibility for hip landmark to be included
 */
data class HipRockingDetectorConfig(
    val excessiveAmplitudeThreshold: Float = DEFAULT_EXCESSIVE_AMPLITUDE_THRESHOLD,
    val minSamplesRequired: Int = DEFAULT_MIN_SAMPLES,
    val visibilityThreshold: Float = 0.5f
) {
    companion object {
        /**
         * Default threshold for excessive hip rocking.
         * 
         * This represents approximately 3% of frame height. For a 1080p video,
         * this would be about 32 pixels of vertical hip movement.
         * 
         * Excessive hip rocking typically indicates:
         * - Saddle too high
         * - Leg length discrepancy
         * - Core weakness
         * - Poor pedaling technique
         * 
         * Normal hip motion during pedaling should be minimal (< 2% of frame height).
         * Values > 3% are often considered problematic.
         */
        const val DEFAULT_EXCESSIVE_AMPLITUDE_THRESHOLD = 0.03f

        /**
         * Minimum samples required for reliable analysis.
         * At 30fps, 30 samples = 1 second of data.
         */
        const val DEFAULT_MIN_SAMPLES = 15
    }
}

/**
 * Detects excessive lateral hip rocking during pedaling.
 * 
 * Hip rocking is a common bike fit issue where the hips move excessively
 * up and down during the pedal stroke. This is typically caused by:
 * - Saddle height too high
 * - Leg length discrepancy
 * - Core instability
 * 
 * The detector tracks hip vertical position over time and calculates
 * both peak-to-peak amplitude and statistical variance to quantify
 * the amount of hip motion.
 * 
 * Usage:
 * ```
 * val detector = HipRockingDetector()
 * 
 * for (frame in poseFrames) {
 *     detector.addFrame(frame, BodySide.LEFT)
 * }
 * 
 * val result = detector.analyze(BodySide.LEFT)
 * if (result.isExcessive) {
 *     println("Excessive hip rocking detected: ${result.amplitude}")
 * }
 * ```
 */
class HipRockingDetector(
    private val config: HipRockingDetectorConfig = HipRockingDetectorConfig()
) {
    // Store hip Y positions for each side
    private val leftHipPositions = mutableListOf<Float>()
    private val rightHipPositions = mutableListOf<Float>()

    /**
     * Adds a pose frame to the analysis.
     * 
     * @param poseFrame The pose frame to process
     * @param side Which hip to track
     * @return True if the hip position was successfully added
     */
    fun addFrame(poseFrame: PoseFrame, side: BodySide): Boolean {
        if (poseFrame.landmarks.size < PoseLandmarkIndex.LANDMARK_COUNT) {
            return false
        }

        val hipIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_HIP
        } else {
            PoseLandmarkIndex.RIGHT_HIP
        }

        val hip = poseFrame.landmarks[hipIndex]

        if (!hip.isVisible(config.visibilityThreshold)) {
            return false
        }

        val positions = if (side == BodySide.LEFT) leftHipPositions else rightHipPositions
        positions.add(hip.y)

        return true
    }

    /**
     * Adds a hip Y position directly.
     * 
     * @param hipY The hip Y coordinate (normalized, 0-1)
     * @param side Which hip this belongs to
     */
    fun addHipPosition(hipY: Float, side: BodySide) {
        val positions = if (side == BodySide.LEFT) leftHipPositions else rightHipPositions
        positions.add(hipY)
    }

    /**
     * Analyzes the collected hip positions and returns the result.
     * 
     * @param side Which side to analyze
     * @return HipRockingResult with amplitude, variance, and excessive flag
     */
    fun analyze(side: BodySide): HipRockingResult {
        val positions = if (side == BodySide.LEFT) leftHipPositions else rightHipPositions

        if (positions.size < config.minSamplesRequired) {
            return HipRockingResult.invalid(side)
        }

        val minY = positions.minOrNull() ?: 0f
        val maxY = positions.maxOrNull() ?: 0f
        val amplitude = maxY - minY
        val variance = calculateVariance(positions)
        val isExcessive = amplitude > config.excessiveAmplitudeThreshold

        return HipRockingResult(
            amplitude = amplitude,
            variance = variance,
            isExcessive = isExcessive,
            side = side,
            sampleCount = positions.size,
            minY = minY,
            maxY = maxY
        )
    }

    /**
     * Analyzes both sides and returns results.
     * 
     * @return Pair of HipRockingResults (left, right)
     */
    fun analyzeBoth(): Pair<HipRockingResult, HipRockingResult> {
        return Pair(analyze(BodySide.LEFT), analyze(BodySide.RIGHT))
    }

    /**
     * Calculates the statistical variance of the positions.
     */
    private fun calculateVariance(positions: List<Float>): Float {
        if (positions.isEmpty()) return 0f

        val mean = positions.average().toFloat()
        val squaredDiffs = positions.map { (it - mean) * (it - mean) }
        return squaredDiffs.average().toFloat()
    }

    /**
     * Calculates the standard deviation of the positions.
     */
    fun getStandardDeviation(side: BodySide): Float {
        val positions = if (side == BodySide.LEFT) leftHipPositions else rightHipPositions
        if (positions.isEmpty()) return 0f
        return sqrt(calculateVariance(positions))
    }

    /**
     * Gets the number of samples collected.
     */
    fun getSampleCount(side: BodySide): Int {
        return if (side == BodySide.LEFT) leftHipPositions.size else rightHipPositions.size
    }

    /**
     * Resets the detector, clearing all collected data.
     */
    fun reset() {
        leftHipPositions.clear()
        rightHipPositions.clear()
    }

    /**
     * Resets data for a specific side only.
     */
    fun reset(side: BodySide) {
        when (side) {
            BodySide.LEFT -> leftHipPositions.clear()
            BodySide.RIGHT -> rightHipPositions.clear()
        }
    }

    companion object {
        /**
         * Analyzes a sequence of pose frames for hip rocking.
         * 
         * @param frames List of pose frames to analyze
         * @param side Which hip to track
         * @param config Detector configuration
         * @return HipRockingResult with analysis
         */
        fun analyzeFrameSequence(
            frames: List<PoseFrame>,
            side: BodySide,
            config: HipRockingDetectorConfig = HipRockingDetectorConfig()
        ): HipRockingResult {
            val detector = HipRockingDetector(config)

            for (frame in frames) {
                detector.addFrame(frame, side)
            }

            return detector.analyze(side)
        }

        /**
         * Analyzes a list of hip Y positions directly.
         * 
         * @param hipYPositions List of hip Y coordinates
         * @param side Which side this data represents
         * @param config Detector configuration
         * @return HipRockingResult with analysis
         */
        fun analyzeHipPositions(
            hipYPositions: List<Float>,
            side: BodySide,
            config: HipRockingDetectorConfig = HipRockingDetectorConfig()
        ): HipRockingResult {
            val detector = HipRockingDetector(config)

            for (y in hipYPositions) {
                detector.addHipPosition(y, side)
            }

            return detector.analyze(side)
        }

        /**
         * Calculates amplitude from a list of positions without creating a detector.
         * 
         * @param positions List of Y positions
         * @return Peak-to-peak amplitude
         */
        fun calculateAmplitude(positions: List<Float>): Float {
            if (positions.isEmpty()) return 0f
            val min = positions.minOrNull() ?: 0f
            val max = positions.maxOrNull() ?: 0f
            return max - min
        }

        /**
         * Calculates variance from a list of positions without creating a detector.
         * 
         * @param positions List of Y positions
         * @return Statistical variance
         */
        fun calculateVariance(positions: List<Float>): Float {
            if (positions.isEmpty()) return 0f
            val mean = positions.average().toFloat()
            val squaredDiffs = positions.map { (it - mean) * (it - mean) }
            return squaredDiffs.average().toFloat()
        }
    }
}
