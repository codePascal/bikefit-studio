package bikefitstudio.pose

/**
 * Applies exponential moving average (EMA) smoothing to pose landmarks.
 * 
 * EMA smoothing reduces jitter in landmark positions by blending the current
 * value with previous values. The formula is:
 * 
 *   smoothed = alpha * current + (1 - alpha) * previous_smoothed
 * 
 * A higher alpha (closer to 1.0) gives more weight to current values (less smoothing).
 * A lower alpha (closer to 0.0) gives more weight to previous values (more smoothing).
 * 
 * Typical values for pose smoothing: 0.3 to 0.5
 * 
 * Usage:
 * ```
 * val smoother = LandmarkSmoother(alpha = 0.4f)
 * val smoothedFrame = smoother.smooth(rawFrame)
 * ```
 * 
 * Thread Safety: This class is NOT thread-safe. Use a single instance per thread.
 * 
 * @param alpha Smoothing factor (0.0 to 1.0). Default is 0.4.
 */
class LandmarkSmoother(
    val alpha: Float = DEFAULT_ALPHA
) {
    init {
        require(alpha in 0f..1f) { "Alpha must be between 0.0 and 1.0, got $alpha" }
    }

    /**
     * Previous smoothed landmarks, indexed by landmark index.
     * Null if no previous frame has been processed.
     */
    private var previousLandmarks: MutableList<Landmark>? = null

    /**
     * Applies EMA smoothing to a PoseFrame.
     * 
     * On the first call, returns the input frame unchanged (no previous data to blend).
     * Subsequent calls blend with the previous smoothed values.
     * 
     * @param frame The raw pose frame from MediaPipe
     * @return A new PoseFrame with smoothed landmark positions
     */
    fun smooth(frame: PoseFrame): PoseFrame {
        if (!frame.isValid) {
            // Don't update previous landmarks with invalid data
            return frame
        }

        val smoothedLandmarks = smoothLandmarks(frame.landmarks)

        return frame.copy(landmarks = smoothedLandmarks)
    }

    /**
     * Applies EMA smoothing to a PoseResult.
     * 
     * @param result The raw pose result from MediaPipe
     * @return A new PoseResult with smoothed landmark positions
     */
    fun smooth(result: PoseResult): PoseResult {
        if (!result.isValid) {
            return result
        }

        val smoothedLandmarks = smoothLandmarks(result.landmarks)

        return result.copy(landmarks = smoothedLandmarks)
    }

    /**
     * Applies EMA smoothing to a list of landmarks.
     */
    private fun smoothLandmarks(currentLandmarks: List<Landmark>): List<Landmark> {
        val previous = previousLandmarks

        if (previous == null || previous.size != currentLandmarks.size) {
            // First frame or landmark count changed - initialize with current values
            previousLandmarks = currentLandmarks.toMutableList()
            return currentLandmarks
        }

        val smoothed = currentLandmarks.mapIndexed { index, current ->
            val prev = previous[index]
            smoothLandmark(current, prev)
        }

        // Update previous landmarks for next frame
        previousLandmarks = smoothed.toMutableList()

        return smoothed
    }

    /**
     * Applies EMA smoothing to a single landmark.
     * 
     * Only smooths x, y, z coordinates. Visibility and presence are kept from current.
     */
    private fun smoothLandmark(current: Landmark, previous: Landmark): Landmark {
        return Landmark(
            x = ema(current.x, previous.x),
            y = ema(current.y, previous.y),
            z = ema(current.z, previous.z),
            visibility = current.visibility,  // Don't smooth visibility
            presence = current.presence        // Don't smooth presence
        )
    }

    /**
     * Calculates the exponential moving average.
     * 
     * @param current Current value
     * @param previous Previous smoothed value
     * @return Smoothed value
     */
    private fun ema(current: Float, previous: Float): Float {
        return alpha * current + (1f - alpha) * previous
    }

    /**
     * Resets the smoother, clearing all previous state.
     * 
     * Call this when starting a new recording or when there's a discontinuity
     * in the pose data (e.g., subject left frame and returned).
     */
    fun reset() {
        previousLandmarks = null
    }

    /**
     * Returns true if the smoother has been initialized with at least one frame.
     */
    fun isInitialized(): Boolean = previousLandmarks != null

    companion object {
        /** Default smoothing factor - balanced between responsiveness and smoothness */
        const val DEFAULT_ALPHA = 0.4f

        /** High responsiveness, low smoothing */
        const val ALPHA_LOW_SMOOTHING = 0.7f

        /** Balanced smoothing */
        const val ALPHA_MEDIUM_SMOOTHING = 0.4f

        /** High smoothing, low responsiveness */
        const val ALPHA_HIGH_SMOOTHING = 0.2f

        /**
         * Pure function to apply EMA smoothing to a single value.
         * 
         * @param current Current value
         * @param previous Previous smoothed value
         * @param alpha Smoothing factor
         * @return Smoothed value
         */
        fun ema(current: Float, previous: Float, alpha: Float): Float {
            require(alpha in 0f..1f) { "Alpha must be between 0.0 and 1.0" }
            return alpha * current + (1f - alpha) * previous
        }

        /**
         * Pure function to apply EMA smoothing to landmarks.
         * 
         * @param current Current landmarks
         * @param previous Previous smoothed landmarks
         * @param alpha Smoothing factor
         * @return Smoothed landmarks
         */
        fun smoothLandmarks(
            current: List<Landmark>,
            previous: List<Landmark>,
            alpha: Float
        ): List<Landmark> {
            require(current.size == previous.size) { 
                "Landmark lists must have same size: ${current.size} vs ${previous.size}" 
            }
            require(alpha in 0f..1f) { "Alpha must be between 0.0 and 1.0" }

            return current.zip(previous) { curr, prev ->
                Landmark(
                    x = ema(curr.x, prev.x, alpha),
                    y = ema(curr.y, prev.y, alpha),
                    z = ema(curr.z, prev.z, alpha),
                    visibility = curr.visibility,
                    presence = curr.presence
                )
            }
        }
    }
}
