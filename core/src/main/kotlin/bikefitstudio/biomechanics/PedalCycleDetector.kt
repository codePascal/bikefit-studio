package bikefitstudio.biomechanics

import bikefitstudio.pose.Landmark
import bikefitstudio.pose.PoseFrame
import bikefitstudio.pose.PoseLandmarkIndex
import bikefitstudio.calibration.BikeCalibration
import kotlin.math.atan2

/**
 * Represents a detected extremum in the pedal cycle.
 */
enum class PedalExtremum {
    /** Bottom Dead Center - ankle at lowest point */
    BDC,
    /** Top Dead Center - ankle at highest point */
    TDC
}

/**
 * Represents a detected pedal cycle extremum with timing information.
 * 
 * @param type The type of extremum (BDC or TDC)
 * @param frameNumber The frame number where the extremum was detected
 * @param timestampMs The timestamp in milliseconds
 * @param ankleY The Y coordinate of the ankle at the extremum
 * @param side Which leg was analyzed
 * @param confidence Detection confidence based on the clarity of the extremum
 */
data class PedalExtremumEvent(
    val type: PedalExtremum,
    val frameNumber: Long,
    val timestampMs: Long,
    val ankleY: Float,
    val side: BodySide,
    val confidence: Float
)

/**
 * Configuration for the pedal cycle detector.
 * 
 * @param windowSize Number of frames to use for sliding window extrema detection
 * @param minCycleFrames Minimum frames between extrema of same type (prevents noise)
 * @param visibilityThreshold Minimum visibility for ankle landmark
 * @param useCrankAngleDetection Use crank angle ranges for TDC/BDC detection (requires calibration)
 * @param tdcCrankAngleMin Minimum crank angle for TDC (top dead center at 12 o'clock)
 * @param tdcCrankAngleMax Maximum crank angle for TDC (range includes 355-5 degrees)
 * @param bdcCrankAngleMin Minimum crank angle for BDC (bottom dead center at 6 o'clock)
 * @param bdcCrankAngleMax Maximum crank angle for BDC (range: 175-185 degrees)
 */
data class PedalCycleDetectorConfig(
    val windowSize: Int = 5,
    val minCycleFrames: Int = 5,
    val visibilityThreshold: Float = 0.5f,
    val useCrankAngleDetection: Boolean = true,
    val tdcCrankAngleMin: Float = 355f,
    val tdcCrankAngleMax: Float = 5f,
    val bdcCrankAngleMin: Float = 175f,
    val bdcCrankAngleMax: Float = 185f
)

/**
 * Detects Bottom Dead Center (BDC) and Top Dead Center (TDC) in the pedal stroke.
 * 
 * BDC is when the ankle is at its lowest point (maximum Y in image coordinates).
 * TDC is when the ankle is at its highest point (minimum Y in image coordinates).
 * 
 * The detector uses a sliding window approach to find local extrema while
 * filtering out noise from the pose detection.
 * 
 * Usage:
 * ```
 * val detector = PedalCycleDetector()
 * 
 * // Process frames as they come in
 * for (frame in poseFrames) {
 *     val events = detector.processFrame(frame, BodySide.LEFT)
 *     for (event in events) {
 *         when (event.type) {
 *             PedalExtremum.BDC -> println("Bottom dead center at frame ${event.frameNumber}")
 *             PedalExtremum.TDC -> println("Top dead center at frame ${event.frameNumber}")
 *         }
 *     }
 * }
 * ```
 */
class PedalCycleDetector(
    private val config: PedalCycleDetectorConfig = PedalCycleDetectorConfig(),
    private val calibration: BikeCalibration? = null
) {
    // Sliding window buffer for ankle Y positions and crank angles
    private data class FrameData(
        val frameNumber: Long,
        val timestampMs: Long,
        val ankleY: Float,
        val visibility: Float,
        val crankAngle: Float? = null  // Crank angle in degrees [0, 360), null if unable to compute
    )

    private val leftBuffer = ArrayDeque<FrameData>(config.windowSize * 2)
    private val rightBuffer = ArrayDeque<FrameData>(config.windowSize * 2)

    // Track last detected extrema to prevent duplicates
    private var lastLeftBdcFrame: Long = Long.MIN_VALUE
    private var lastLeftTdcFrame: Long = Long.MIN_VALUE
    private var lastRightBdcFrame: Long = Long.MIN_VALUE
    private var lastRightTdcFrame: Long = Long.MIN_VALUE

    /**
     * Processes a pose frame and returns any detected extrema.
     * 
     * @param poseFrame The pose frame to process
     * @param side Which leg to analyze
     * @return List of detected extrema events (may be empty)
     */
    fun processFrame(poseFrame: PoseFrame, side: BodySide): List<PedalExtremumEvent> {
        if (poseFrame.landmarks.size < PoseLandmarkIndex.LANDMARK_COUNT) {
            return emptyList()
        }

        val ankleIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_ANKLE
        } else {
            PoseLandmarkIndex.RIGHT_ANKLE
        }

        val ankle = poseFrame.landmarks[ankleIndex]

        if (!ankle.isVisible(config.visibilityThreshold)) {
            return emptyList()
        }

        // Calculate crank angle if we have calibration and crank angle detection is enabled
        var crankAngle: Float? = null
        if (config.useCrankAngleDetection && calibration?.bottomBracket != null) {
            val footIndex = if (side == BodySide.LEFT) {
                PoseLandmarkIndex.LEFT_FOOT_INDEX
            } else {
                PoseLandmarkIndex.RIGHT_FOOT_INDEX
            }
            
            val foot = poseFrame.landmarks[footIndex]
            if (foot != null && foot.visibility >= config.visibilityThreshold) {
                val bb = calibration.bottomBracket!!
                crankAngle = computeCrankAngle(foot.x, foot.y, bb.x, bb.y)
            }
        }

        val frameData = FrameData(
            frameNumber = poseFrame.frameNumber,
            timestampMs = poseFrame.timestampMs,
            ankleY = ankle.y,
            visibility = ankle.visibility,
            crankAngle = crankAngle
        )

        val buffer = if (side == BodySide.LEFT) leftBuffer else rightBuffer
        buffer.addLast(frameData)

        // Keep buffer size manageable
        while (buffer.size > config.windowSize * 2) {
            buffer.removeFirst()
        }

        // Need at least windowSize frames to detect extrema
        if (buffer.size < config.windowSize) {
            return emptyList()
        }

        return detectExtrema(buffer, side)
    }

    /**
     * Processes a single ankle position for extrema detection.
     * 
     * This is useful for custom processing or when working with
     * pre-extracted ankle positions.
     * 
     * @param frameNumber The frame number
     * @param timestampMs The timestamp in milliseconds
     * @param ankleY The Y coordinate of the ankle
     * @param visibility The visibility of the ankle landmark
     * @param side Which leg is being analyzed
     * @return List of detected extrema events (may be empty)
     */
    fun processAnklePosition(
        frameNumber: Long,
        timestampMs: Long,
        ankleY: Float,
        visibility: Float,
        side: BodySide
    ): List<PedalExtremumEvent> {
        if (visibility < config.visibilityThreshold) {
            return emptyList()
        }

        val frameData = FrameData(
            frameNumber = frameNumber,
            timestampMs = timestampMs,
            ankleY = ankleY,
            visibility = visibility,
            crankAngle = null  // Crank angle not available for this method
        )

        val buffer = if (side == BodySide.LEFT) leftBuffer else rightBuffer
        buffer.addLast(frameData)

        while (buffer.size > config.windowSize * 2) {
            buffer.removeFirst()
        }

        if (buffer.size < config.windowSize) {
            return emptyList()
        }

        return detectExtrema(buffer, side)
    }

    /**
     * Computes raw crank angle from foot position relative to bottom bracket.
     * 
     * @param footX X coordinate of foot index landmark
     * @param footY Y coordinate of foot index landmark
     * @param bbX X coordinate of bottom bracket (crank pivot)
     * @param bbY Y coordinate of bottom bracket
     * @return Crank angle in degrees [0, 360)
     */
    private fun computeCrankAngle(footX: Float, footY: Float, bbX: Float, bbY: Float): Float {
        val dx = footX - bbX
        val dy = footY - bbY
        
        // Negate Y because MediaPipe Y increases downward (image space)
        // but crank angles need standard Cartesian orientation
        val crankAngleRadians = kotlin.math.atan2(-dy.toDouble(), dx.toDouble())
        var crankAngleDegrees = Math.toDegrees(crankAngleRadians).toFloat()
        
        // Normalize to [0, 360)
        if (crankAngleDegrees < 0) crankAngleDegrees += 360f
        
        return crankAngleDegrees
    }

    /**
     * Checks if a crank angle is within the TDC range (12 o'clock, 355-5 degrees).
     * 
     * Uses OR logic because TDC wraps around 0°/360° boundary:
     * - angle >= 355° is in range (near 360°)
     * - angle <= 5° is in range (near 0°)
     * These ranges together represent the 12 o'clock position.
     */
    @Suppress("SENSELESS_COMPARISON")
    private fun isInTdcRange(angle: Float): Boolean {
        return angle >= config.tdcCrankAngleMin || angle <= config.tdcCrankAngleMax
    }

    /**
     * Checks if a crank angle is within the BDC range (6 o'clock, 175-185 degrees).
     */
    private fun isInBdcRange(angle: Float): Boolean {
        return angle >= config.bdcCrankAngleMin && angle <= config.bdcCrankAngleMax
    }

    /**
     * Detects extrema in the buffer using either crank angle ranges or sliding window.
     * 
     * When crank angle detection is enabled and available, detects TDC (12 o'clock, 355-5°)
     * and BDC (6 o'clock, 175-185°). Otherwise uses the sliding window approach on ankle Y.
     * 
     * The buffer contains recent frames. We examine whether the frame
     * at the center of the window is a local extremum by comparing
     * it to its neighbors on both sides.
     */
    private fun detectExtrema(buffer: ArrayDeque<FrameData>, side: BodySide): List<PedalExtremumEvent> {
        val events = mutableListOf<PedalExtremumEvent>()

        val windowSize = config.windowSize
        if (buffer.size < windowSize) return events

        // The candidate for extremum is at the center of the window
        // We need at least halfWindow frames on each side
        val halfWindow = windowSize / 2
        
        // Look at the center element of the last windowSize elements
        val candidateIndex = buffer.size - 1 - halfWindow
        if (candidateIndex < halfWindow) return events

        val candidate = buffer.elementAt(candidateIndex)

        // Try crank angle-based detection first if available
        if (config.useCrankAngleDetection && candidate.crankAngle != null) {
            return detectExtremaByAngle(buffer, side, candidateIndex, candidate)
        }

        // Fall back to ankle Y-based detection
        return detectExtremaByAnkleY(buffer, side, candidateIndex, candidate, halfWindow)
    }

    /**
     * Detects TDC/BDC by checking if crank angle is in the appropriate range.
     */
    private fun detectExtremaByAngle(
        buffer: ArrayDeque<FrameData>,
        side: BodySide,
        candidateIndex: Int,
        candidate: FrameData
    ): List<PedalExtremumEvent> {
        val events = mutableListOf<PedalExtremumEvent>()
        val crankAngle = candidate.crankAngle ?: return events

        val lastBdcFrame = if (side == BodySide.LEFT) lastLeftBdcFrame else lastRightBdcFrame
        val lastTdcFrame = if (side == BodySide.LEFT) lastLeftTdcFrame else lastRightTdcFrame

        val bdcFrameOk = lastBdcFrame == Long.MIN_VALUE || 
            candidate.frameNumber - lastBdcFrame >= config.minCycleFrames
        val tdcFrameOk = lastTdcFrame == Long.MIN_VALUE || 
            candidate.frameNumber - lastTdcFrame >= config.minCycleFrames

        if (isInBdcRange(crankAngle) && bdcFrameOk) {
            val confidence = calculateCrankAngleConfidence(crankAngle, isBdc = true)
            
            events.add(PedalExtremumEvent(
                type = PedalExtremum.BDC,
                frameNumber = candidate.frameNumber,
                timestampMs = candidate.timestampMs,
                ankleY = candidate.ankleY,
                side = side,
                confidence = confidence
            ))

            if (side == BodySide.LEFT) {
                lastLeftBdcFrame = candidate.frameNumber
            } else {
                lastRightBdcFrame = candidate.frameNumber
            }
        }

        if (isInTdcRange(crankAngle) && tdcFrameOk) {
            val confidence = calculateCrankAngleConfidence(crankAngle, isBdc = false)
            
            events.add(PedalExtremumEvent(
                type = PedalExtremum.TDC,
                frameNumber = candidate.frameNumber,
                timestampMs = candidate.timestampMs,
                ankleY = candidate.ankleY,
                side = side,
                confidence = confidence
            ))

            if (side == BodySide.LEFT) {
                lastLeftTdcFrame = candidate.frameNumber
            } else {
                lastRightTdcFrame = candidate.frameNumber
            }
        }

        return events
    }

    /**
     * Detects TDC/BDC using sliding window on ankle Y position (legacy method).
     */
    private fun detectExtremaByAnkleY(
        buffer: ArrayDeque<FrameData>,
        side: BodySide,
        candidateIndex: Int,
        candidate: FrameData,
        halfWindow: Int
    ): List<PedalExtremumEvent> {
        val events = mutableListOf<PedalExtremumEvent>()

        // Check if candidate is a local maximum Y (BDC) or minimum Y (TDC)
        var isBdc = true
        var isTdc = true

        // Compare with neighbors on both sides
        for (offset in -halfWindow..halfWindow) {
            if (offset == 0) continue
            
            val neighborIndex = candidateIndex + offset
            if (neighborIndex < 0 || neighborIndex >= buffer.size) continue

            val neighbor = buffer.elementAt(neighborIndex)

            // BDC: candidate Y should be > all neighbors (lowest point = max Y in image coords)
            // Use > instead of >= to require a strict maximum
            if (neighbor.ankleY >= candidate.ankleY) {
                isBdc = false
            }

            // TDC: candidate Y should be < all neighbors (highest point = min Y in image coords)
            if (neighbor.ankleY <= candidate.ankleY) {
                isTdc = false
            }
        }

        // Check minimum cycle distance for BDC
        val lastBdcFrame = if (side == BodySide.LEFT) lastLeftBdcFrame else lastRightBdcFrame
        val lastTdcFrame = if (side == BodySide.LEFT) lastLeftTdcFrame else lastRightTdcFrame

        // Use safe comparison to avoid overflow with Long.MIN_VALUE
        val bdcFrameOk = lastBdcFrame == Long.MIN_VALUE || 
            candidate.frameNumber - lastBdcFrame >= config.minCycleFrames
        val tdcFrameOk = lastTdcFrame == Long.MIN_VALUE || 
            candidate.frameNumber - lastTdcFrame >= config.minCycleFrames

        if (isBdc && bdcFrameOk) {
            // Calculate confidence based on how clear the extremum is
            val confidence = calculateExtremumConfidence(buffer, candidateIndex, isBdc = true)

            events.add(PedalExtremumEvent(
                type = PedalExtremum.BDC,
                frameNumber = candidate.frameNumber,
                timestampMs = candidate.timestampMs,
                ankleY = candidate.ankleY,
                side = side,
                confidence = confidence
            ))

            if (side == BodySide.LEFT) {
                lastLeftBdcFrame = candidate.frameNumber
            } else {
                lastRightBdcFrame = candidate.frameNumber
            }
        }

        if (isTdc && tdcFrameOk) {
            val confidence = calculateExtremumConfidence(buffer, candidateIndex, isBdc = false)

            events.add(PedalExtremumEvent(
                type = PedalExtremum.TDC,
                frameNumber = candidate.frameNumber,
                timestampMs = candidate.timestampMs,
                ankleY = candidate.ankleY,
                side = side,
                confidence = confidence
            ))

            if (side == BodySide.LEFT) {
                lastLeftTdcFrame = candidate.frameNumber
            } else {
                lastRightTdcFrame = candidate.frameNumber
            }
        }

        return events
    }

    /**
     * Calculates confidence for a crank angle-based detection.
     * 
     * Confidence is higher when the angle is closer to the center of the range.
     * For TDC (355-5°), center is 0° or 360°.
     * For BDC (175-185°), center is 180°.
     */
    private fun calculateCrankAngleConfidence(angle: Float, isBdc: Boolean): Float {
        val centerAngle = if (isBdc) 180f else 0f
        val minAngle = if (isBdc) config.bdcCrankAngleMin else config.tdcCrankAngleMin
        val maxAngle = if (isBdc) config.bdcCrankAngleMax else config.tdcCrankAngleMax
        
        // For TDC with wraparound (355-5°), adjust angle calculation
        val distanceToCenter = if (!isBdc && (angle > 180f)) {
            // Angle is in the 355-360 range, distance to 0/360 is smaller
            minOf(angle - 355f, 360f - angle + 5f)
        } else if (!isBdc && angle <= 5f) {
            // Angle is in the 0-5 range, distance to 0 is direct
            angle
        } else {
            // BDC or regular distance calculation
            kotlin.math.abs(angle - centerAngle)
        }
        
        val maxDistance = if (isBdc) (maxAngle - minAngle) / 2f else 5f
        
        // Normalize: closer to center = higher confidence
        val normalizedDistance = (distanceToCenter / maxDistance).coerceIn(0f, 1f)
        return 1f - (normalizedDistance * 0.5f)  // Range: 0.5 to 1.0
    }

    /**
     * Calculates confidence for an extremum based on the difference from neighbors.
     */
    private fun calculateExtremumConfidence(
        buffer: ArrayDeque<FrameData>,
        candidateIndex: Int,
        isBdc: Boolean
    ): Float {
        val candidate = buffer.elementAt(candidateIndex)
        val halfWindow = config.windowSize / 2

        var totalDiff = 0f
        var count = 0

        for (i in (candidateIndex - halfWindow)..(candidateIndex + halfWindow)) {
            if (i == candidateIndex) continue
            if (i < 0 || i >= buffer.size) continue

            val other = buffer.elementAt(i)
            val diff = if (isBdc) {
                candidate.ankleY - other.ankleY // Should be positive for BDC
            } else {
                other.ankleY - candidate.ankleY // Should be positive for TDC
            }
            totalDiff += diff
            count++
        }

        if (count == 0) return 0.5f

        val avgDiff = totalDiff / count

        // Normalize confidence: small differences = lower confidence
        // Typical ankle movement might be 0.1-0.3 in normalized coords
        val normalizedDiff = (avgDiff / 0.1f).coerceIn(0f, 1f)

        return 0.5f + (normalizedDiff * 0.5f) // Range: 0.5 to 1.0
    }

    /**
     * Resets the detector state, clearing all buffers.
     */
    fun reset() {
        leftBuffer.clear()
        rightBuffer.clear()
        lastLeftBdcFrame = Long.MIN_VALUE
        lastLeftTdcFrame = Long.MIN_VALUE
        lastRightBdcFrame = Long.MIN_VALUE
        lastRightTdcFrame = Long.MIN_VALUE
    }

    /**
     * Resets the detector state for a specific side only.
     */
    fun reset(side: BodySide) {
        when (side) {
            BodySide.LEFT -> {
                leftBuffer.clear()
                lastLeftBdcFrame = Long.MIN_VALUE
                lastLeftTdcFrame = Long.MIN_VALUE
            }
            BodySide.RIGHT -> {
                rightBuffer.clear()
                lastRightBdcFrame = Long.MIN_VALUE
                lastRightTdcFrame = Long.MIN_VALUE
            }
        }
    }

    /**
     * Gets the number of frames currently in the buffer.
     */
    fun getBufferSize(side: BodySide): Int {
        return if (side == BodySide.LEFT) leftBuffer.size else rightBuffer.size
    }

    companion object {
        /**
         * Analyzes a complete sequence of frames and returns all detected extrema.
         * 
         * This is useful for offline analysis of recorded video.
         * 
         * @param frames List of pose frames to analyze
         * @param side Which leg to analyze
         * @param config Detector configuration
         * @param calibration Bike calibration (required if using crank angle detection)
         * @return List of all detected extrema events in chronological order
         */
        fun analyzeFrameSequence(
            frames: List<PoseFrame>,
            side: BodySide,
            config: PedalCycleDetectorConfig = PedalCycleDetectorConfig(),
            calibration: BikeCalibration? = null
        ): List<PedalExtremumEvent> {
            val detector = PedalCycleDetector(config, calibration)
            val allEvents = mutableListOf<PedalExtremumEvent>()

            for (frame in frames) {
                allEvents.addAll(detector.processFrame(frame, side))
            }

            return allEvents
        }

        /**
         * Analyzes ankle positions directly without full pose frames.
         * 
         * @param anklePositions List of (frameNumber, timestampMs, ankleY, visibility) tuples
         * @param side Which leg is being analyzed
         * @param config Detector configuration
         * @return List of all detected extrema events
         */
        fun analyzeAnklePositions(
            anklePositions: List<AnklePosition>,
            side: BodySide,
            config: PedalCycleDetectorConfig = PedalCycleDetectorConfig()
        ): List<PedalExtremumEvent> {
            val detector = PedalCycleDetector(config)
            val allEvents = mutableListOf<PedalExtremumEvent>()

            for (pos in anklePositions) {
                allEvents.addAll(
                    detector.processAnklePosition(
                        frameNumber = pos.frameNumber,
                        timestampMs = pos.timestampMs,
                        ankleY = pos.y,
                        visibility = pos.visibility,
                        side = side
                    )
                )
            }

            return allEvents
        }
    }
}

/**
 * Simple data class for ankle position in sequence analysis.
 */
data class AnklePosition(
    val frameNumber: Long,
    val timestampMs: Long,
    val y: Float,
    val visibility: Float = 1.0f
)
