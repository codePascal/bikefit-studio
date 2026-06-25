package bikefitstudio.pose

/**
 * Represents a single pose landmark with normalized coordinates.
 * 
 * @param x Normalized x coordinate (0.0 to 1.0, left to right)
 * @param y Normalized y coordinate (0.0 to 1.0, top to bottom)
 * @param z Depth relative to hip midpoint (negative = closer to camera)
 * @param visibility Likelihood the landmark is visible (0.0 to 1.0)
 * @param presence Likelihood the landmark is within the image (0.0 to 1.0)
 */
data class Landmark(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float,
    val presence: Float
) {
    /**
     * Returns true if this landmark is considered visible.
     */
    fun isVisible(threshold: Float = DEFAULT_VISIBILITY_THRESHOLD): Boolean {
        return visibility >= threshold
    }

    companion object {
        /** Default threshold for considering a landmark visible */
        const val DEFAULT_VISIBILITY_THRESHOLD = 0.5f
    }
}

/**
 * MediaPipe Pose landmark indices for body parts.
 * 
 * These indices correspond to the 33 landmarks returned by MediaPipe Pose.
 * For side-view bike fit analysis, we primarily use:
 * - Hip, Knee, Ankle (leg angles)
 * - Shoulder, Hip (torso angle)
 * - Elbow, Wrist (arm position)
 */
object PoseLandmarkIndex {
    // Face landmarks (0-10) - less relevant for bike fit
    const val NOSE = 0
    const val LEFT_EYE_INNER = 1
    const val LEFT_EYE = 2
    const val LEFT_EYE_OUTER = 3
    const val RIGHT_EYE_INNER = 4
    const val RIGHT_EYE = 5
    const val RIGHT_EYE_OUTER = 6
    const val LEFT_EAR = 7
    const val RIGHT_EAR = 8
    const val MOUTH_LEFT = 9
    const val MOUTH_RIGHT = 10

    // Upper body landmarks (11-22)
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_PINKY = 17
    const val RIGHT_PINKY = 18
    const val LEFT_INDEX = 19
    const val RIGHT_INDEX = 20
    const val LEFT_THUMB = 21
    const val RIGHT_THUMB = 22

    // Lower body landmarks (23-32) - most relevant for bike fit
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28
    const val LEFT_HEEL = 29
    const val RIGHT_HEEL = 30
    const val LEFT_FOOT_INDEX = 31
    const val RIGHT_FOOT_INDEX = 32

    /** Total number of landmarks in MediaPipe Pose */
    const val LANDMARK_COUNT = 33
}

/**
 * Result of pose estimation for a single frame.
 * 
 * @param landmarks List of 33 pose landmarks (may be empty if no pose detected)
 * @param timestampMs Frame timestamp in milliseconds
 * @param isValid True if a pose was successfully detected
 * @param confidence Overall confidence score for the pose detection (0.0 to 1.0)
 */
data class PoseResult(
    val landmarks: List<Landmark>,
    val timestampMs: Long,
    val isValid: Boolean,
    val confidence: Float = 0f,
    val inputImageWidth: Int = 0,
    val inputImageHeight: Int = 0
) {
    /**
     * Gets a landmark by its index.
     * 
     * @param index The landmark index (use PoseLandmarkIndex constants)
     * @return The landmark, or null if index is out of bounds or pose is invalid
     */
    fun getLandmark(index: Int): Landmark? {
        return if (isValid && index in landmarks.indices) {
            landmarks[index]
        } else {
            null
        }
    }

    /**
     * Checks if essential landmarks for bike fit analysis are visible.
     * For side-view analysis, we need hip, knee, ankle, and shoulder.
     */
    fun hasEssentialLandmarks(side: Side = Side.LEFT): Boolean {
        if (!isValid) return false
        
        val indices = when (side) {
            Side.LEFT -> listOf(
                PoseLandmarkIndex.LEFT_SHOULDER,
                PoseLandmarkIndex.LEFT_HIP,
                PoseLandmarkIndex.LEFT_KNEE,
                PoseLandmarkIndex.LEFT_ANKLE
            )
            Side.RIGHT -> listOf(
                PoseLandmarkIndex.RIGHT_SHOULDER,
                PoseLandmarkIndex.RIGHT_HIP,
                PoseLandmarkIndex.RIGHT_KNEE,
                PoseLandmarkIndex.RIGHT_ANKLE
            )
        }
        
        return indices.all { getLandmark(it)?.isVisible() == true }
    }

    companion object {
        /** Empty result for when no pose is detected */
        val EMPTY = PoseResult(
            landmarks = emptyList(),
            timestampMs = 0L,
            isValid = false,
            confidence = 0f,
            inputImageWidth = 0,
            inputImageHeight = 0
        )
    }
    
    /**
     * Calculates the average visibility of all landmarks.
     * Useful for determining overall pose quality.
     */
    fun averageVisibility(): Float {
        if (landmarks.isEmpty()) return 0f
        return landmarks.map { it.visibility }.average().toFloat()
    }
    
    /**
     * Calculates the average visibility of essential bike fit landmarks.
     * 
     * @param side Which side of the body to analyze
     * @return Average visibility of shoulder, hip, knee, and ankle
     */
    fun essentialLandmarkVisibility(side: Side = Side.LEFT): Float {
        if (!isValid) return 0f
        
        val indices = when (side) {
            Side.LEFT -> listOf(
                PoseLandmarkIndex.LEFT_SHOULDER,
                PoseLandmarkIndex.LEFT_HIP,
                PoseLandmarkIndex.LEFT_KNEE,
                PoseLandmarkIndex.LEFT_ANKLE
            )
            Side.RIGHT -> listOf(
                PoseLandmarkIndex.RIGHT_SHOULDER,
                PoseLandmarkIndex.RIGHT_HIP,
                PoseLandmarkIndex.RIGHT_KNEE,
                PoseLandmarkIndex.RIGHT_ANKLE
            )
        }
        
        val visibilities = indices.mapNotNull { getLandmark(it)?.visibility }
        return if (visibilities.isNotEmpty()) visibilities.average().toFloat() else 0f
    }
}

/**
 * Represents which side of the body is being analyzed.
 */
enum class Side {
    LEFT,
    RIGHT
}
