package bikefitstudio.biomechanics

import bikefitstudio.util.Logger
import bikefitstudio.calibration.BikeCalibration
import bikefitstudio.pose.Landmark
import bikefitstudio.pose.PoseFrame
import bikefitstudio.pose.PoseLandmarkIndex
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Represents the direction of knee position relative to pedal.
 */
enum class KneeAlignment {
    /** Knee is positioned forward of the pedal */
    FORWARD,
    /** Knee is positioned rearward of the pedal */
    REARWARD,
    /** Knee is directly aligned with the pedal */
    NEUTRAL
}

/**
 * Result of knee-over-pedal offset calculation.
 * 
 * KOPS is computed as the horizontal distance between the knee landmark and the pedal
 * spindle when the crank is horizontal (3 o'clock position). The spindle position is
 * derived from the bottom bracket location and crank length scaled to image space using
 * the foot–BB radius. The distance is normalized by femur length to produce a
 * scale-independent metric.
 * 
 * @param normalizedOffset Horizontal offset normalized by femur length (dimensionless)
 *                         Interpretation: <-0.25 (rearward), -0.10…+0.10 (neutral), >+0.30 (very forward)
 * @param alignment Directional bias of knee position
 * @param rawOffset Raw horizontal distance from knee to spindle in normalized coordinates
 * @param femurLength Length of femur (hip to knee) used for normalization
 * @param side Which leg was analyzed
 * @param frameNumber Frame number where measurement was taken
 * @param timestampMs Timestamp at measurement
 * @param confidence Measurement confidence based on landmark visibility
 * @param isValid Whether the result is valid
 * @param spindleX X coordinate of pedal spindle (for reference/debugging)
 * @param crankScale Spindle X offset from the bottom bracket, in normalized image
 *                   coordinates (mean foot–BB distance / crank length in mm); added to BB.x
 * @param computationMethod "crank_geometry" for BB+crank-based, or other method identifier
 */
data class KneeOverPedalOffsetResult(
    val normalizedOffset: Float,
    val alignment: KneeAlignment,
    val rawOffset: Float,
    val femurLength: Float,
    val side: BodySide,
    val frameNumber: Long,
    val timestampMs: Long,
    val confidence: Float,
    val isValid: Boolean,
    val spindleX: Float = 0f,
    val crankScale: Float = 0f,
    val computationMethod: String = "crank_geometry"
) {
    companion object {
        /**
         * Creates an invalid result when computation fails.
         */
        fun invalid(side: BodySide): KneeOverPedalOffsetResult {
            return KneeOverPedalOffsetResult(
                normalizedOffset = 0f,
                alignment = KneeAlignment.NEUTRAL,
                rawOffset = 0f,
                femurLength = 0f,
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
 * Summary of knee-over-pedal offset measurements across multiple cycles.
 * 
 * @param measurementCount Number of measurements included
 * @param averageNormalizedOffset Average normalized offset
 * @param minNormalizedOffset Minimum normalized offset observed
 * @param maxNormalizedOffset Maximum normalized offset observed
 * @param standardDeviation Standard deviation of normalized offsets
 * @param averageAlignment Most common alignment direction
 * @param side Which leg was analyzed
 * @param isValid Whether the summary contains valid data
 */
data class KneeOverPedalOffsetSummary(
    val measurementCount: Int,
    val averageNormalizedOffset: Float,
    val minNormalizedOffset: Float,
    val maxNormalizedOffset: Float,
    val standardDeviation: Float,
    val averageAlignment: KneeAlignment,
    val side: BodySide,
    val isValid: Boolean
) {
    companion object {
        /**
         * Creates an invalid summary when no valid data is available.
         */
        fun invalid(side: BodySide): KneeOverPedalOffsetSummary {
            return KneeOverPedalOffsetSummary(
                measurementCount = 0,
                averageNormalizedOffset = 0f,
                minNormalizedOffset = 0f,
                maxNormalizedOffset = 0f,
                standardDeviation = 0f,
                averageAlignment = KneeAlignment.NEUTRAL,
                side = side,
                isValid = false
            )
        }
    }
}

/**
 * Configuration for knee-over-pedal offset computation.
 * 
 * @param visibilityThreshold Minimum visibility for landmarks
 * @param neutralThreshold Threshold for considering alignment neutral (as fraction of femur)
 * @param crankAngleMinDegrees Minimum crank angle for 3 o'clock detection (hardcoded 85°)
 * @param crankAngleMaxDegrees Maximum crank angle for 3 o'clock detection (hardcoded 95°)
 * @param crankScaleFrameLimit Number of 3 o'clock frames to use for computing crank scale (first 30 frames)
 */
data class KneeOverPedalOffsetConfig(
    val visibilityThreshold: Float = 0.5f,
    val neutralThreshold: Float = 0.05f,
    val crankAngleMinDegrees: Float = 85f,
    val crankAngleMaxDegrees: Float = 95f,
    val crankScaleFrameLimit: Int = 30
)

/**
 * Computes normalized knee-over-pedal offset at 3 o'clock crank position using crank geometry.
 * 
 * KOPS is the horizontal distance between the knee joint and the pedal spindle when the
 * crank is horizontal (3 o'clock position), normalized by femur length for scale invariance.
 * 
 * Algorithm (crank-geometry based):
 * 1. Identify frames where crank angle is approximately 90° (horizontal)
 * 2. Estimate crank angle using foot position relative to bottom bracket:
 *    crank_angle = atan2(foot_y - BB_y, foot_x - BB_x)
 * 3. From the first 30 frames compute a crank scale, then the spindle position:
 *    crankScale = mean(|foot - BB|) / crank_length_mm
 *    spindle_x = BB_x + crankScale   (or the user-marked spindle, when one is calibrated)
 * 4. Calculate knee-spindle offset and normalize by femur length:
 *    dx = knee_x - spindle_x
 *    kops_norm = dx / femur_length
 * 5. Average across all valid crank-horizontal frames
 * 
 * Requires complete calibration (bottom bracket + crank length) - will fail with warning if unavailable.
 * 
 * Interpretation ranges (normalized):
 * - < -0.25: Very rearward
 * - -0.25 — -0.10: Slightly rearward
 * - -0.10 — +0.10: Neutral
 * - +0.10 — +0.30: Forward
 * - > +0.30: Very forward
 */
object KneeOverPedalOffset {
    private const val TAG = "KneeOverPedalOffset"

    // Safe logging wrapper that handles Android Log not being available in unit tests
    private fun logDebug(tag: String, message: String) {
        try {
            Logger.d(tag, message)
        } catch (e: Exception) {
            // Silently fail if Log is not available (unit tests)
        }
    }
    
    private fun logInfo(tag: String, message: String) {
        try {
            Logger.i(tag, message)
        } catch (e: Exception) {
            // Silently fail if Log is not available (unit tests)
        }
    }
    
    private fun logWarn(tag: String, message: String) {
        try {
            Logger.w(tag, message)
        } catch (e: Exception) {
            // Silently fail if Log is not available (unit tests)
        }
    }

    /**
     * Cache for crank scale factor (computed once from first 30 frames).
     * Immutable holder to avoid synchronization issues.
     */
    data class CrankScaleCache(
        val scale: Float,
        val frameCount: Int,
        val isValid: Boolean
    ) {
        companion object {
            val INVALID = CrankScaleCache(1f, 0, false)
        }
    }

    /**
     * Computes knee-over-pedal offset at a single frame using crank geometry.
     * 
     * REQUIRES: Complete calibration with bottom bracket and crank length.
     * Will log warning and return invalid result if calibration incomplete.
     * 
     * @param frame The pose frame at 3 o'clock position
     * @param side Which leg to analyze
     * @param calibration Bike calibration with BB and crank length
     * @param crankScale Pre-computed spindle offset from the BB in normalized image
     *                   coordinates (see [computeCrankScale]); added to BB.x for the spindle
     * @param config Configuration options
     * @return Result containing normalized offset and alignment using crank geometry
     */
    fun computeAtFrame(
        frame: PoseFrame,
        side: BodySide,
        calibration: BikeCalibration,
        crankScale: Float,
        config: KneeOverPedalOffsetConfig = KneeOverPedalOffsetConfig()
    ): KneeOverPedalOffsetResult {
        // Validate calibration
        if (calibration.bottomBracket == null || calibration.crankLengthMm == null) {
            logWarn(TAG, "Incomplete calibration: BB=${calibration.bottomBracket != null}, crank=${calibration.crankLengthMm != null}. Cannot compute KOPS.")
            return KneeOverPedalOffsetResult.invalid(side)
        }

        if (frame.landmarks.size < PoseLandmarkIndex.LANDMARK_COUNT) {
            return KneeOverPedalOffsetResult.invalid(side)
        }

        // Get landmark indices based on side
        val (hipIndex, kneeIndex, footIndex) = getLandmarkIndices(side)

        // Get the landmarks
        val hip = frame.landmarks[hipIndex]
        val knee = frame.landmarks[kneeIndex]
        val foot = frame.landmarks[footIndex]

        // Check visibility
        if (!hip.isVisible(config.visibilityThreshold) ||
            !knee.isVisible(config.visibilityThreshold) ||
            !foot.isVisible(config.visibilityThreshold)) {
            return KneeOverPedalOffsetResult.invalid(side)
        }

        // Calculate average confidence
        val confidence = (hip.visibility + knee.visibility + foot.visibility) / 3f

        // Compute spindle position
        // Use user-marked spindle if available, otherwise compute geometrically
        val spindleX = if (calibration.spindle != null) {
            // Use marked spindle (ground truth from user calibration)
            calibration.spindle.x
        } else {
            // Fallback to geometric estimate: BB_x + crankScale
            calibration.bottomBracket.x + crankScale
        }
        
        val spindle = Vector2D(
            spindleX,
            calibration.bottomBracket.y
        )

        // Log diagnostic information
        logDebug(TAG, "KOPS Computation Frame #${frame.frameNumber}:")
        logDebug(TAG, "  Landmarks: hip=(${hip.x}, ${hip.y}), knee=(${knee.x}, ${knee.y}), foot=(${foot.x}, ${foot.y})")
        logDebug(TAG, "  Calibration: BB=(${calibration.bottomBracket.x}, ${calibration.bottomBracket.y}), crankLen=${calibration.crankLengthMm}mm")
        logDebug(TAG, "  CrankScale: $crankScale")
        logDebug(TAG, "  Spindle: (${spindle.x}, ${spindle.y}) [${if (calibration.spindle != null) "marked" else "geometric"}]")

        // Compute the offset using crank geometry
        val components = computeOffsetWithSpindle(hip, knee, spindle, config)

        logDebug(TAG, "  FemurLength: ${components.femurLength}, RawOffset: ${components.rawOffset}, NormalizedOffset: ${components.normalizedOffset}, Alignment: ${components.alignment}")

        return KneeOverPedalOffsetResult(
            normalizedOffset = components.normalizedOffset,
            alignment = components.alignment,
            rawOffset = components.rawOffset,
            femurLength = components.femurLength,
            side = side,
            frameNumber = frame.frameNumber,
            timestampMs = frame.timestampMs,
            confidence = confidence,
            isValid = true,
            spindleX = spindle.x,
            crankScale = crankScale,
            computationMethod = "crank_geometry"
        )
    }

    /**
     * Computes knee-over-pedal offset from raw landmarks using crank geometry.
     * 
     * REQUIRES: Complete calibration with bottom bracket and crank length.
     * 
     * @param landmarks List of 33 pose landmarks
     * @param side Which leg to analyze
     * @param calibration Bike calibration with BB and crank length
     * @param crankScale Pre-computed spindle offset from the BB in normalized image
     *                   coordinates (see [computeCrankScale]); added to BB.x for the spindle
     * @param config Configuration options
     * @return Result containing normalized offset and alignment
     */
    fun computeFromLandmarks(
        landmarks: List<Landmark>,
        side: BodySide,
        calibration: BikeCalibration,
        crankScale: Float,
        config: KneeOverPedalOffsetConfig = KneeOverPedalOffsetConfig()
    ): KneeOverPedalOffsetResult {
        // Validate calibration
        if (calibration.bottomBracket == null || calibration.crankLengthMm == null) {
            logWarn(TAG, "Incomplete calibration: BB=${calibration.bottomBracket != null}, crank=${calibration.crankLengthMm != null}. Cannot compute KOPS.")
            return KneeOverPedalOffsetResult.invalid(side)
        }

        if (landmarks.size < PoseLandmarkIndex.LANDMARK_COUNT) {
            return KneeOverPedalOffsetResult.invalid(side)
        }

        // Get landmark indices based on side
        val (hipIndex, kneeIndex, footIndex) = getLandmarkIndices(side)

        // Get the landmarks
        val hip = landmarks[hipIndex]
        val knee = landmarks[kneeIndex]
        val foot = landmarks[footIndex]

        // Check visibility
        if (!hip.isVisible(config.visibilityThreshold) ||
            !knee.isVisible(config.visibilityThreshold) ||
            !foot.isVisible(config.visibilityThreshold)) {
            return KneeOverPedalOffsetResult.invalid(side)
        }

        // Calculate average confidence
        val confidence = (hip.visibility + knee.visibility + foot.visibility) / 3f

        // Compute spindle position
        // Note: crankScale is already = meanFootDistance / crankLengthMm
        // So spindle_x = BB_x + crankScale (since we're in normalized coordinates)
        val spindle = Vector2D(
            calibration.bottomBracket.x + crankScale,
            calibration.bottomBracket.y
        )

        // Compute the offset using crank geometry
        val components = computeOffsetWithSpindle(hip, knee, spindle, config)

        return KneeOverPedalOffsetResult(
            normalizedOffset = components.normalizedOffset,
            alignment = components.alignment,
            rawOffset = components.rawOffset,
            femurLength = components.femurLength,
            side = side,
            frameNumber = 0L,
            timestampMs = 0L,
            confidence = confidence,
            isValid = true,
            spindleX = spindle.x,
            crankScale = crankScale,
            computationMethod = "crank_geometry"
        )
    }

    /**
     * Detects if frame is at 3 o'clock position (crank horizontal).
     *
     * Estimates crank angle using foot position relative to bottom bracket:
     * crank_angle = atan2(foot_y - BB_y, foot_x - BB_x)
     *
     * Returns true if crank_angle is within [85°, 95°].
     * 
     * @param frame The pose frame to check
     * @param side Which leg to analyze
     * @param calibration Bike calibration with BB position
     * @param config Configuration with crank angle tolerances
     * @return True if frame is at 3 o'clock position
     */
    fun isAtThreeOClock(
        frame: PoseFrame,
        side: BodySide,
        calibration: BikeCalibration,
        config: KneeOverPedalOffsetConfig = KneeOverPedalOffsetConfig()
    ): Boolean {
        if (calibration.bottomBracket == null || frame.landmarks.size < PoseLandmarkIndex.LANDMARK_COUNT) {
            return false
        }

        val footIndex = if (side == BodySide.LEFT) PoseLandmarkIndex.LEFT_FOOT_INDEX else PoseLandmarkIndex.RIGHT_FOOT_INDEX
        val foot = frame.landmarks[footIndex]

        if (!foot.isVisible(config.visibilityThreshold)) {
            return false
        }

        // Calculate crank angle relative to BB
        val dx = foot.x - calibration.bottomBracket.x
        val dy = foot.y - calibration.bottomBracket.y
        
        val crankAngleDegrees = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        
        // Normalize angle to [0, 360)
        val normalizedAngle = if (crankAngleDegrees < 0) crankAngleDegrees + 360f else crankAngleDegrees

        // Check if within [85°, 95°] (3 o'clock position)
        return normalizedAngle >= config.crankAngleMinDegrees && normalizedAngle <= config.crankAngleMaxDegrees
    }

    /**
     * Computes crank scale factor from frames at 3 o'clock position.
     *
     * Uses the foot-BB distance as proxy for crank length:
     * scale = mean(|foot - BB|) / crank_length_mm
     *
     * The resulting scale is added directly to BB.x to locate the spindle.
     * Computes using first N frames (limit from [KneeOverPedalOffsetConfig.crankScaleFrameLimit]).
     *
     * @param frames List of pose frames at 3 o'clock positions
     * @param side Which leg to analyze
     * @param calibration Bike calibration with BB and crank length
     * @param config Configuration with visibility threshold and frame limit
     * @return CrankScaleCache with computed scale or INVALID if insufficient data
     */
    fun computeCrankScale(
        frames: List<PoseFrame>,
        side: BodySide,
        calibration: BikeCalibration,
        config: KneeOverPedalOffsetConfig = KneeOverPedalOffsetConfig()
    ): CrankScaleCache {
        // Validate calibration
        if (calibration.bottomBracket == null || calibration.crankLengthMm == null) {
            logWarn(TAG, "Cannot compute crank scale: incomplete calibration")
            return CrankScaleCache.INVALID
        }

        val footIndex = if (side == BodySide.LEFT) PoseLandmarkIndex.LEFT_FOOT_INDEX else PoseLandmarkIndex.RIGHT_FOOT_INDEX
        val bb = Vector2D(calibration.bottomBracket.x, calibration.bottomBracket.y)

        // Collect foot-BB distances from first N frames
        val footRadii = mutableListOf<Float>()
        for (frame in frames) {
            if (footRadii.size >= config.crankScaleFrameLimit) break
            
            if (frame.landmarks.size < PoseLandmarkIndex.LANDMARK_COUNT) continue
            
            val foot = frame.landmarks[footIndex]
            if (!foot.isVisible(config.visibilityThreshold)) continue
            
            val footPoint = Vector2D(foot.x, foot.y)
            val radius = bb.distanceTo(footPoint)
            if (radius > Vector2D.EPSILON) {
                footRadii.add(radius)
            }
        }

        if (footRadii.isEmpty()) {
            logWarn(TAG, "No valid foot measurements for crank scale computation")
            return CrankScaleCache.INVALID
        }

        val meanRadius = footRadii.average().toFloat()
        val scale = meanRadius / calibration.crankLengthMm
        
        logDebug(TAG, "Computed crank scale: $scale from ${footRadii.size} frames")
        logDebug(TAG, "  MeanFootDistance: $meanRadius (normalized coords)")
        logDebug(TAG, "  CrankLength: ${calibration.crankLengthMm}mm")
        logDebug(TAG, "  BB Position: (${calibration.bottomBracket.x}, ${calibration.bottomBracket.y})")
        logDebug(TAG, "  Individual radii: $footRadii")

        return CrankScaleCache(scale, footRadii.size, true)
    }

    /**
     * Gets the landmark indices for the specified body side.
     * 
     * @param side Which leg to analyze
     * @return Triple of (hip index, knee index, foot index)
     */
    private fun getLandmarkIndices(side: BodySide): Triple<Int, Int, Int> {
        return if (side == BodySide.LEFT) {
            Triple(
                PoseLandmarkIndex.LEFT_HIP,
                PoseLandmarkIndex.LEFT_KNEE,
                PoseLandmarkIndex.LEFT_FOOT_INDEX
            )
        } else {
            Triple(
                PoseLandmarkIndex.RIGHT_HIP,
                PoseLandmarkIndex.RIGHT_KNEE,
                PoseLandmarkIndex.RIGHT_FOOT_INDEX
            )
        }
    }

    /**
     * Internal function to compute normalized offset using spindle position (crank geometry).
     * 
     * @param hip Hip landmark
     * @param knee Knee landmark
     * @param spindle Spindle position (marked spindle, or BB.x + crankScale)
     * @param config Configuration options
     * @return Offset components including normalized and raw values
     */
    private fun computeOffsetWithSpindle(
        hip: Landmark,
        knee: Landmark,
        spindle: Vector2D,
        config: KneeOverPedalOffsetConfig
    ): OffsetComponents {
        // Calculate horizontal offset (X-axis difference)
        // Positive = knee forward of spindle, Negative = knee behind spindle
        val horizontalOffset = knee.x - spindle.x

        // Calculate femur length (hip to knee distance)
        val hipPoint = Vector2D(hip.x, hip.y)
        val kneePoint = Vector2D(knee.x, knee.y)
        val femurLength = hipPoint.distanceTo(kneePoint)

        // Guard against zero femur length
        if (femurLength < Vector2D.EPSILON) {
            logWarn(TAG, "Femur length too small: $femurLength")
            return OffsetComponents(0f, KneeAlignment.NEUTRAL, horizontalOffset, femurLength)
        }

        // Normalize offset by femur length
        val normalizedOffset = horizontalOffset / femurLength

        logDebug(TAG, "  Offset calc: knee.x=${knee.x}, spindle.x=${spindle.x}")
        logDebug(TAG, "  horizontalOffset=${horizontalOffset}, femurLength=${femurLength}")
        logDebug(TAG, "  normalizedOffset=${normalizedOffset}")

        // Determine alignment based on normalized offset
        val alignment = when {
            abs(normalizedOffset) < config.neutralThreshold -> KneeAlignment.NEUTRAL
            normalizedOffset > 0f -> KneeAlignment.FORWARD
            else -> KneeAlignment.REARWARD
        }

        return OffsetComponents(normalizedOffset, alignment, horizontalOffset, femurLength)
    }

    /**
     * Computes knee-over-pedal offset from multiple frames and returns a summary.
     * 
     * REQUIRES: Complete calibration with bottom bracket and crank length.
     * 
     * @param frames List of pose frames (should all be at or near 3 o'clock position)
     * @param side Which leg to analyze
     * @param calibration Bike calibration with BB and crank length
     * @param crankScale Pre-computed crank scale factor
     * @param config Configuration options
     * @return Summary of offset measurements across all frames
     */
    fun computeFromFrames(
        frames: List<PoseFrame>,
        side: BodySide,
        calibration: BikeCalibration,
        crankScale: Float,
        config: KneeOverPedalOffsetConfig = KneeOverPedalOffsetConfig()
    ): KneeOverPedalOffsetSummary {
        if (frames.isEmpty()) {
            return KneeOverPedalOffsetSummary.invalid(side)
        }

        // Compute offset for each frame
        val results = frames.mapNotNull { frame ->
            val result = computeAtFrame(frame, side, calibration, crankScale, config)
            if (result.isValid) result else null
        }

        if (results.isEmpty()) {
            return KneeOverPedalOffsetSummary.invalid(side)
        }

        // Extract normalized offsets
        val normalizedOffsets = results.map { it.normalizedOffset }

        // Calculate statistics
        val average = normalizedOffsets.average().toFloat()
        val min = normalizedOffsets.minOrNull() ?: 0f
        val max = normalizedOffsets.maxOrNull() ?: 0f
        val stdDev = calculateStandardDeviation(normalizedOffsets, average)

        // Determine most common alignment
        val alignmentCounts = results.groupingBy { it.alignment }.eachCount()
        val averageAlignment = alignmentCounts.maxByOrNull { it.value }?.key ?: KneeAlignment.NEUTRAL

        return KneeOverPedalOffsetSummary(
            measurementCount = results.size,
            averageNormalizedOffset = average,
            minNormalizedOffset = min,
            maxNormalizedOffset = max,
            standardDeviation = stdDev,
            averageAlignment = averageAlignment,
            side = side,
            isValid = true
        )
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

/**
 * Internal data class for offset computation components.
 * 
 * @param normalizedOffset Offset normalized by femur length
 * @param alignment Direction of knee position
 * @param rawOffset Raw horizontal distance
 * @param femurLength Length of femur used for normalization
 */
private data class OffsetComponents(
    val normalizedOffset: Float,
    val alignment: KneeAlignment,
    val rawOffset: Float,
    val femurLength: Float
)
