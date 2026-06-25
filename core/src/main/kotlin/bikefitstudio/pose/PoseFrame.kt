package bikefitstudio.pose

/**
 * Represents a single frame of pose data captured from video.
 * 
 * This is the primary data model for pose information, containing
 * all landmarks detected in a single video frame along with metadata.
 * 
 * For bike fit analysis, this frame is part of a sequence that will
 * be analyzed to detect pedal cycles and calculate joint angles.
 * 
 * @param frameNumber Sequential frame number in the recording
 * @param timestampMs Frame timestamp in milliseconds since recording start
 * @param landmarks List of 33 pose landmarks with coordinates and visibility
 * @param confidence Overall confidence score for the pose detection (0.0 to 1.0)
 * @param imageWidth Width of the source image in pixels
 * @param imageHeight Height of the source image in pixels
 */
data class PoseFrame(
    val frameNumber: Long,
    val timestampMs: Long,
    val landmarks: List<Landmark>,
    val confidence: Float,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0
) {
    /**
     * True if this frame contains valid pose data.
     */
    val isValid: Boolean
        get() = landmarks.isNotEmpty() && confidence > 0f

    /**
     * Gets a landmark by its index.
     * 
     * @param index The landmark index (use PoseLandmarkIndex constants)
     * @return The landmark, or null if index is out of bounds
     */
    fun getLandmark(index: Int): Landmark? {
        return landmarks.getOrNull(index)
    }

    /**
     * Gets the pixel coordinates of a landmark.
     * 
     * @param index The landmark index
     * @return Pair of (x, y) pixel coordinates, or null if landmark not found
     */
    fun getLandmarkPixels(index: Int): Pair<Float, Float>? {
        val landmark = getLandmark(index) ?: return null
        return Pair(
            landmark.x * imageWidth,
            landmark.y * imageHeight
        )
    }

    /**
     * Checks if essential landmarks for bike fit analysis are visible.
     */
    fun hasEssentialLandmarks(side: Side = Side.LEFT): Boolean {
        if (!isValid) return false
        
        val indices = when (side) {
            Side.LEFT -> ESSENTIAL_LEFT_INDICES
            Side.RIGHT -> ESSENTIAL_RIGHT_INDICES
        }
        
        return indices.all { getLandmark(it)?.isVisible() == true }
    }

    /**
     * Calculates the average visibility of essential landmarks.
     */
    fun essentialLandmarkVisibility(side: Side = Side.LEFT): Float {
        val indices = when (side) {
            Side.LEFT -> ESSENTIAL_LEFT_INDICES
            Side.RIGHT -> ESSENTIAL_RIGHT_INDICES
        }
        
        val visibilities = indices.mapNotNull { getLandmark(it)?.visibility }
        return if (visibilities.isNotEmpty()) visibilities.average().toFloat() else 0f
    }

    /**
     * Converts to PoseResult for compatibility with wrapper output.
     */
    fun toPoseResult(): PoseResult {
        return PoseResult(
            landmarks = landmarks,
            timestampMs = timestampMs,
            isValid = isValid,
            confidence = confidence
        )
    }

    companion object {
        /** Essential landmarks for left side bike fit analysis */
        private val ESSENTIAL_LEFT_INDICES = listOf(
            PoseLandmarkIndex.LEFT_SHOULDER,
            PoseLandmarkIndex.LEFT_HIP,
            PoseLandmarkIndex.LEFT_KNEE,
            PoseLandmarkIndex.LEFT_ANKLE
        )

        /** Essential landmarks for right side bike fit analysis */
        private val ESSENTIAL_RIGHT_INDICES = listOf(
            PoseLandmarkIndex.RIGHT_SHOULDER,
            PoseLandmarkIndex.RIGHT_HIP,
            PoseLandmarkIndex.RIGHT_KNEE,
            PoseLandmarkIndex.RIGHT_ANKLE
        )

        /** Empty frame for when no pose is detected */
        val EMPTY = PoseFrame(
            frameNumber = 0,
            timestampMs = 0L,
            landmarks = emptyList(),
            confidence = 0f
        )

        /**
         * Creates a PoseFrame from a PoseResult.
         * 
         * @param result The PoseResult from MediaPipe wrapper
         * @param frameNumber Sequential frame number
         * @param imageWidth Source image width in pixels
         * @param imageHeight Source image height in pixels
         */
        fun fromPoseResult(
            result: PoseResult,
            frameNumber: Long,
            imageWidth: Int = 0,
            imageHeight: Int = 0
        ): PoseFrame {
            return PoseFrame(
                frameNumber = frameNumber,
                timestampMs = result.timestampMs,
                landmarks = result.landmarks,
                confidence = result.confidence,
                imageWidth = imageWidth,
                imageHeight = imageHeight
            )
        }
    }
}
