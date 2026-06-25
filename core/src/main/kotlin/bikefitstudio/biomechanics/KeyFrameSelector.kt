package bikefitstudio.biomechanics

import bikefitstudio.pose.PoseFrame

/**
 * Represents a key frame in the pedal cycle with its associated data.
 * 
 * @param frameNumber The frame number in the video
 * @param timestampMs The timestamp in milliseconds
 * @param type The type of extremum (BDC or TDC)
 * @param poseFrame Optional pose frame data at this key point
 */
data class KeyFrame(
    val frameNumber: Long,
    val timestampMs: Long,
    val type: PedalExtremum,
    val poseFrame: PoseFrame? = null
)

/**
 * Represents a complete pedal cycle segment with key frames.
 * 
 * A cycle segment is defined from one extremum to the next extremum of the same type
 * (e.g., BDC to BDC or TDC to TDC). Each segment includes:
 * - Start and end key frames
 * - Optional intermediate key frames (e.g., TDC between two BDCs)
 * - Frame range for the complete cycle
 * 
 * @param cycleNumber Sequential cycle number
 * @param startFrame The starting key frame (BDC or TDC)
 * @param endFrame The ending key frame (same type as start)
 * @param intermediateFrames Key frames between start and end (e.g., TDC in a BDC-to-BDC cycle)
 * @param side Which leg this cycle represents
 */
data class CycleSegment(
    val cycleNumber: Int,
    val startFrame: KeyFrame,
    val endFrame: KeyFrame,
    val intermediateFrames: List<KeyFrame> = emptyList(),
    val side: BodySide
) {
    /**
     * Total frame count in this cycle.
     */
    val frameCount: Long
        get() = endFrame.frameNumber - startFrame.frameNumber + 1

    /**
     * Duration of this cycle in milliseconds.
     */
    val durationMs: Long
        get() = endFrame.timestampMs - startFrame.timestampMs

    /**
     * All key frames in chronological order.
     */
    val allKeyFrames: List<KeyFrame>
        get() = listOf(startFrame) + intermediateFrames + endFrame

    /**
     * Returns the BDC frame if this is a BDC-to-BDC cycle.
     */
    val bdcFrame: KeyFrame?
        get() = if (startFrame.type == PedalExtremum.BDC) startFrame else null

    /**
     * Returns the TDC frame if this is a TDC-to-TDC cycle.
     */
    val tdcFrame: KeyFrame?
        get() = if (startFrame.type == PedalExtremum.TDC) startFrame else null

    /**
     * Finds a BDC frame in this cycle (start, end, or intermediate).
     */
    fun findBdcFrame(): KeyFrame? {
        return allKeyFrames.firstOrNull { it.type == PedalExtremum.BDC }
    }

    /**
     * Finds a TDC frame in this cycle (start, end, or intermediate).
     */
    fun findTdcFrame(): KeyFrame? {
        return allKeyFrames.firstOrNull { it.type == PedalExtremum.TDC }
    }
}

/**
 * Configuration for the key frame selector.
 * 
 * @param captureIntermediateFrames Whether to capture intermediate key frames (e.g., TDC in BDC-to-BDC cycle)
 * @param segmentationType Type of segmentation (BDC-to-BDC or TDC-to-TDC)
 */
data class KeyFrameSelectorConfig(
    val captureIntermediateFrames: Boolean = true,
    val segmentationType: SegmentationType = SegmentationType.BDC_TO_BDC
)

/**
 * Defines how cycles should be segmented.
 */
enum class SegmentationType {
    /** Segment from BDC to BDC (bottom dead center to bottom dead center) */
    BDC_TO_BDC,
    /** Segment from TDC to TDC (top dead center to top dead center) */
    TDC_TO_TDC
}

/**
 * Selects and stores key frames (BDC and TDC) from detected pedal cycles.
 * 
 * This class works in conjunction with PedalCycleDetector to:
 * 1. Track detected extremum events (BDC/TDC)
 * 2. Group events into complete cycle segments
 * 3. Extract key frames at critical points
 * 4. Provide access to segmented cycles for analysis
 * 
 * Usage:
 * ```
 * val detector = PedalCycleDetector()
 * val selector = KeyFrameSelector(BodySide.LEFT)
 * 
 * // Process each frame
 * for (frame in poseFrames) {
 *     val events = detector.processFrame(frame, BodySide.LEFT)
 *     selector.processEvents(events, frame)
 * }
 * 
 * // Get completed cycle segments
 * val segments = selector.getCompletedSegments()
 * for (segment in segments) {
 *     val bdcFrame = segment.findBdcFrame()
 *     val tdcFrame = segment.findTdcFrame()
 *     // Use key frames for analysis
 * }
 * ```
 * 
 * @param side Which body side to track
 * @param config Configuration options
 */
class KeyFrameSelector(
    private val side: BodySide,
    private val config: KeyFrameSelectorConfig = KeyFrameSelectorConfig()
) {
    private val completedSegments = mutableListOf<CycleSegment>()
    private var currentSegmentStart: KeyFrame? = null
    private val intermediateFrames = mutableListOf<KeyFrame>()
    private var cycleNumber = 0

    /**
     * Processes detected extremum events and updates cycle segments.
     * 
     * @param events List of detected extremum events from PedalCycleDetector
     * @param poseFrame Optional pose frame to associate with events
     */
    fun processEvents(events: List<PedalExtremumEvent>, poseFrame: PoseFrame? = null) {
        for (event in events) {
            if (event.side != side) continue
            processEvent(event, poseFrame)
        }
    }

    /**
     * Processes a single extremum event.
     * 
     * @param event The detected extremum event
     * @param poseFrame Optional pose frame to associate with the event
     */
    fun processEvent(event: PedalExtremumEvent, poseFrame: PoseFrame? = null) {
        // Filter out events from wrong side
        if (event.side != side) return

        val keyFrame = KeyFrame(
            frameNumber = event.frameNumber,
            timestampMs = event.timestampMs,
            type = event.type,
            poseFrame = poseFrame
        )

        when (config.segmentationType) {
            SegmentationType.BDC_TO_BDC -> processBdcToBdcSegmentation(event, keyFrame)
            SegmentationType.TDC_TO_TDC -> processTdcToTdcSegmentation(event, keyFrame)
        }
    }

    /**
     * Processes events for BDC-to-BDC segmentation.
     */
    private fun processBdcToBdcSegmentation(event: PedalExtremumEvent, keyFrame: KeyFrame) {
        when (event.type) {
            PedalExtremum.BDC -> {
                if (currentSegmentStart != null) {
                    // Complete the current segment
                    completeSegment(keyFrame)
                }
                // Start new segment
                currentSegmentStart = keyFrame
                intermediateFrames.clear()
            }
            PedalExtremum.TDC -> {
                // Capture TDC as intermediate frame if enabled
                if (config.captureIntermediateFrames && currentSegmentStart != null) {
                    intermediateFrames.add(keyFrame)
                }
            }
        }
    }

    /**
     * Processes events for TDC-to-TDC segmentation.
     */
    private fun processTdcToTdcSegmentation(event: PedalExtremumEvent, keyFrame: KeyFrame) {
        when (event.type) {
            PedalExtremum.TDC -> {
                if (currentSegmentStart != null) {
                    // Complete the current segment
                    completeSegment(keyFrame)
                }
                // Start new segment
                currentSegmentStart = keyFrame
                intermediateFrames.clear()
            }
            PedalExtremum.BDC -> {
                // Capture BDC as intermediate frame if enabled
                if (config.captureIntermediateFrames && currentSegmentStart != null) {
                    intermediateFrames.add(keyFrame)
                }
            }
        }
    }

    /**
     * Completes the current segment and adds it to the list.
     */
    private fun completeSegment(endFrame: KeyFrame) {
        val startFrame = currentSegmentStart ?: return

        val segment = CycleSegment(
            cycleNumber = cycleNumber,
            startFrame = startFrame,
            endFrame = endFrame,
            intermediateFrames = intermediateFrames.toList(),
            side = side
        )

        completedSegments.add(segment)
        cycleNumber++
    }

    /**
     * Returns all completed cycle segments.
     */
    fun getCompletedSegments(): List<CycleSegment> = completedSegments.toList()

    /**
     * Returns the number of completed segments.
     */
    fun getSegmentCount(): Int = completedSegments.size

    /**
     * Returns the most recent completed segment.
     */
    fun getLastSegment(): CycleSegment? = completedSegments.lastOrNull()

    /**
     * Resets the selector, clearing all segments.
     */
    fun reset() {
        completedSegments.clear()
        currentSegmentStart = null
        intermediateFrames.clear()
        cycleNumber = 0
    }

    /**
     * Returns the body side being tracked.
     */
    fun getSide(): BodySide = side

    companion object {
        /**
         * Analyzes a sequence of frames and returns all cycle segments.
         * 
         * This is a convenience method for offline analysis.
         * 
         * @param frames List of pose frames to analyze
         * @param side Which leg to analyze
         * @param detectorConfig Configuration for the pedal cycle detector
         * @param selectorConfig Configuration for the key frame selector
         * @return List of all detected cycle segments
         */
        fun analyzeFrameSequence(
            frames: List<PoseFrame>,
            side: BodySide,
            detectorConfig: PedalCycleDetectorConfig = PedalCycleDetectorConfig(),
            selectorConfig: KeyFrameSelectorConfig = KeyFrameSelectorConfig()
        ): List<CycleSegment> {
            val detector = PedalCycleDetector(detectorConfig)
            val selector = KeyFrameSelector(side, selectorConfig)

            for (frame in frames) {
                val events = detector.processFrame(frame, side)
                selector.processEvents(events, frame)
            }

            return selector.getCompletedSegments()
        }

        /**
         * Analyzes ankle positions and returns all cycle segments.
         * 
         * @param anklePositions List of ankle positions
         * @param side Which leg is being analyzed
         * @param detectorConfig Configuration for the pedal cycle detector
         * @param selectorConfig Configuration for the key frame selector
         * @return List of all detected cycle segments
         */
        fun analyzeAnklePositions(
            anklePositions: List<AnklePosition>,
            side: BodySide,
            detectorConfig: PedalCycleDetectorConfig = PedalCycleDetectorConfig(),
            selectorConfig: KeyFrameSelectorConfig = KeyFrameSelectorConfig()
        ): List<CycleSegment> {
            val detector = PedalCycleDetector(detectorConfig)
            val selector = KeyFrameSelector(side, selectorConfig)

            for (pos in anklePositions) {
                val events = detector.processAnklePosition(
                    frameNumber = pos.frameNumber,
                    timestampMs = pos.timestampMs,
                    ankleY = pos.y,
                    visibility = pos.visibility,
                    side = side
                )
                selector.processEvents(events)
            }

            return selector.getCompletedSegments()
        }
    }
}
