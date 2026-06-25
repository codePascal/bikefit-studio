package bikefitstudio.pose

/**
 * Applies One Euro filtering to pose landmarks for temporal smoothing.
 * 
 * This smoother uses the One Euro Filter algorithm to reduce jitter in landmark
 * positions while maintaining responsiveness during fast movements. The filter
 * is applied independently to each axis (X, Y, Z) of each landmark.
 * 
 * For bike fit analysis, this is particularly important for accurate joint angle
 * calculations, especially for the key landmarks: hip, knee, ankle, and toe.
 * 
 * Usage:
 * ```
 * val smoother = OneEuroLandmarkSmoother(
 *     minCutoff = 1.0,
 *     beta = 0.02,
 *     dCutoff = 1.0
 * )
 * val smoothedFrame = smoother.smooth(rawFrame)
 * ```
 * 
 * Thread Safety: This class is NOT thread-safe. Use a single instance per thread.
 * 
 * @param minCutoff Minimum cutoff frequency (Hz). Lower = more smoothing. Default: 1.0 Hz
 * @param beta Speed coefficient. Higher = more adaptive to fast movements. Default: 0.02
 * @param dCutoff Cutoff frequency for velocity computation (Hz). Default: 1.0 Hz
 * @param targetLandmarks Set of landmark indices to apply filtering to. If null, filters all landmarks.
 *                        Default filters: hip, knee, ankle, toe landmarks (both sides)
 */
class OneEuroLandmarkSmoother(
    private val minCutoff: Double = OneEuroFilter.DEFAULT_MIN_CUTOFF,
    private val beta: Double = OneEuroFilter.DEFAULT_BETA,
    private val dCutoff: Double = OneEuroFilter.DEFAULT_D_CUTOFF,
    private val targetLandmarks: Set<Int>? = DEFAULT_TARGET_LANDMARKS
) {
    /**
     * Map of landmark index to filters for X, Y, Z coordinates.
     * Only contains entries for landmarks that have been initialized.
     */
    private val landmarkFilters = mutableMapOf<Int, LandmarkAxisFilters>()

    /**
     * Applies One Euro filtering to a PoseFrame.
     * 
     * On the first call, returns the input frame unchanged (no previous data to filter).
     * Subsequent calls apply filtering to the configured target landmarks.
     * 
     * @param frame The raw pose frame from MediaPipe
     * @return A new PoseFrame with filtered landmark positions
     */
    fun smooth(frame: PoseFrame): PoseFrame {
        if (!frame.isValid) {
            // Don't update filters with invalid data
            return frame
        }

        // Convert timestampMs to seconds for the filter
        val timestampSeconds = frame.timestampMs / 1000.0

        val smoothedLandmarks = frame.landmarks.mapIndexed { index, landmark ->
            // Only filter target landmarks, pass through others unchanged
            if (shouldFilterLandmark(index)) {
                filterLandmark(index, landmark, timestampSeconds)
            } else {
                landmark
            }
        }

        return frame.copy(landmarks = smoothedLandmarks)
    }

    /**
     * Applies One Euro filtering to a PoseResult.
     * 
     * @param result The raw pose result from MediaPipe
     * @return A new PoseResult with filtered landmark positions
     */
    fun smooth(result: PoseResult): PoseResult {
        if (!result.isValid) {
            return result
        }

        val timestampSeconds = result.timestampMs / 1000.0

        val smoothedLandmarks = result.landmarks.mapIndexed { index, landmark ->
            if (shouldFilterLandmark(index)) {
                filterLandmark(index, landmark, timestampSeconds)
            } else {
                landmark
            }
        }

        return result.copy(landmarks = smoothedLandmarks)
    }

    /**
     * Filters a single landmark using One Euro filters for each axis.
     */
    private fun filterLandmark(index: Int, landmark: Landmark, timestamp: Double): Landmark {
        // Get or create filters for this landmark
        val filters = landmarkFilters.getOrPut(index) {
            LandmarkAxisFilters(
                xFilter = OneEuroFilter(minCutoff, beta, dCutoff),
                yFilter = OneEuroFilter(minCutoff, beta, dCutoff),
                zFilter = OneEuroFilter(minCutoff, beta, dCutoff)
            )
        }

        // Apply filtering to each axis independently
        val filteredX = filters.xFilter.filter(landmark.x.toDouble(), timestamp).toFloat()
        val filteredY = filters.yFilter.filter(landmark.y.toDouble(), timestamp).toFloat()
        val filteredZ = filters.zFilter.filter(landmark.z.toDouble(), timestamp).toFloat()

        return Landmark(
            x = filteredX,
            y = filteredY,
            z = filteredZ,
            visibility = landmark.visibility,  // Don't filter visibility
            presence = landmark.presence        // Don't filter presence
        )
    }

    /**
     * Checks if a landmark should be filtered based on the target landmarks configuration.
     */
    private fun shouldFilterLandmark(index: Int): Boolean {
        return targetLandmarks?.contains(index) ?: true
    }

    /**
     * Resets all filters, clearing previous state.
     * 
     * Call this when starting a new recording or when there's a discontinuity
     * in the pose data (e.g., subject left frame and returned).
     */
    fun reset() {
        landmarkFilters.values.forEach { filters ->
            filters.xFilter.reset()
            filters.yFilter.reset()
            filters.zFilter.reset()
        }
        landmarkFilters.clear()
    }

    /**
     * Returns true if any filters have been initialized.
     */
    fun isInitialized(): Boolean = landmarkFilters.isNotEmpty()

    /**
     * Container for the three axis filters for a single landmark.
     */
    private data class LandmarkAxisFilters(
        val xFilter: OneEuroFilter,
        val yFilter: OneEuroFilter,
        val zFilter: OneEuroFilter
    )

    companion object {
        /**
         * Default target landmarks for bike fit analysis.
         * Includes hip, knee, ankle, heel, and toe landmarks on both sides.
         */
        val DEFAULT_TARGET_LANDMARKS: Set<Int> = setOf(
            PoseLandmarkIndex.LEFT_HIP,
            PoseLandmarkIndex.RIGHT_HIP,
            PoseLandmarkIndex.LEFT_KNEE,
            PoseLandmarkIndex.RIGHT_KNEE,
            PoseLandmarkIndex.LEFT_ANKLE,
            PoseLandmarkIndex.RIGHT_ANKLE,
            PoseLandmarkIndex.LEFT_HEEL,
            PoseLandmarkIndex.RIGHT_HEEL,
            PoseLandmarkIndex.LEFT_FOOT_INDEX,  // Toe
            PoseLandmarkIndex.RIGHT_FOOT_INDEX  // Toe
        )

        /**
         * Preset for low smoothing (high responsiveness).
         * Use when you need to capture rapid movements accurately.
         */
        const val PRESET_LOW_SMOOTHING_MIN_CUTOFF = 2.0
        const val PRESET_LOW_SMOOTHING_BETA = 0.01

        /**
         * Preset for medium smoothing (balanced).
         * Good default for most bike fit scenarios.
         */
        const val PRESET_MEDIUM_SMOOTHING_MIN_CUTOFF = 1.0
        const val PRESET_MEDIUM_SMOOTHING_BETA = 0.02

        /**
         * Preset for high smoothing (low responsiveness).
         * Use when jitter is severe and movements are slow.
         */
        const val PRESET_HIGH_SMOOTHING_MIN_CUTOFF = 0.5
        const val PRESET_HIGH_SMOOTHING_BETA = 0.05
    }
}
