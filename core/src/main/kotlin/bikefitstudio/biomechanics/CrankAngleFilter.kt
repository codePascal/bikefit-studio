package bikefitstudio.biomechanics

import kotlin.math.abs

/**
 * Filters and smooths crank angle measurements to reduce noise and jitter from pose detection.
 * 
 * Issues addressed:
 * - Frame-to-frame angle jitter from MediaPipe pose detection
 * - Sudden angle jumps due to poor ankle landmark detection
 * - 359°→0° wrapping artifacts
 * 
 * Techniques employed:
 * 1. **Outlier Rejection**: Skip frames with unrealistic angle changes (> threshold)
 * 2. **Moving Average Smoothing**: Exponential moving average for temporal stability
 * 3. **Angle Wrapping**: Proper handling of 0°/360° discontinuity
 */
object CrankAngleFilter {
    
    // Safe logging wrapper that handles Android Log not being available in unit tests
    private fun logDebug(tag: String, message: String) {
        try {
            bikefitstudio.util.Logger.d(tag, message)
        } catch (e: Exception) {
            // Silently fail if Log is not available (unit tests)
        }
    }
    
    private fun logInfo(tag: String, message: String) {
        try {
            bikefitstudio.util.Logger.i(tag, message)
        } catch (e: Exception) {
            // Silently fail if Log is not available (unit tests)
        }
    }
    
    private fun logWarn(tag: String, message: String) {
        try {
            bikefitstudio.util.Logger.w(tag, message)
        } catch (e: Exception) {
            // Silently fail if Log is not available (unit tests)
        }
    }
    
    
    /**
     * Configuration for crank angle filtering.
     * 
     * @param maxAngleChangePerFrame Maximum allowed angle change between consecutive frames (degrees)
     *                                Default 45° = reasonable pedaling cadence (~120 RPM at 30fps)
     *                                Only used as initial fallback; adaptive velocity used when available
     * @param smoothingFactor Exponential moving average factor (0-1)
     *                        0.3 = more weight on historical average, smoother
     *                        0.7 = more weight on current measurement, more responsive
     * @param velocityHistorySize Number of recent frames to use for angular velocity estimation
     * @param velocityThresholdMultiplier Outlier threshold as multiple of estimated velocity
     *                                     e.g., 1.8x means reject if delta > 1.8 × recent_velocity
     * @param consecutiveOutlierThreshold Number of consecutive outliers before triggering recovery mode
     * @param recoveryThresholdMultiplier In recovery mode, use this larger multiplier to accept previously rejected frames
     */
    data class FilterConfig(
        val maxAngleChangePerFrame: Float = 45f,  // Increased from 30° to handle wrapping (100 RPM → ~20°/frame, with margin)
        val smoothingFactor: Float = 0.3f,
        val velocityHistorySize: Int = 10,  // Increased from 5 to 10 for better velocity stability
        val velocityThresholdMultiplier: Float = 2.5f,  // Increased to 2.5x to properly handle power zone acceleration
        val consecutiveOutlierThreshold: Int = 5,  // After 5 consecutive outliers, enter recovery mode
        val recoveryThresholdMultiplier: Float = 3.5f  // In recovery, accept deltas up to 3.5x velocity
    )
    
    /**
     * Holds filtering state across multiple frames.
     * 
     * @param lastValidAngle Last angle that passed filters
     * @param smoothedAngle Current exponential moving average filtered angle
     * @param framesSinceLastValid Count of rejected outliers since last valid measurement
     * @param recentDeltas Track recent angle changes for velocity estimation
     * @param consecutiveOutliers Count of consecutive rejected frames (triggers recovery)
     * @param recoveryMode Flag indicating filter is in recovery (accepts slightly larger deltas)
     */
    class FilterState(
        var lastValidAngle: Float? = null,
        var smoothedAngle: Float? = null,
        var framesSinceLastValid: Int = 0,
        val recentDeltas: MutableList<Float> = mutableListOf(),  // Track recent angle changes for velocity estimation
        var consecutiveOutliers: Int = 0,  // Counter for adaptive recovery
        var recoveryMode: Boolean = false   // Flag for recovery state
    )
    
    /**
     * Result of filtering a crank angle measurement.
     * 
     * @param angle Filtered crank angle (0-360°), or null if rejected
     * @param isValid True if angle passed filters
     * @param isOutlier True if angle was rejected as outlier
     * @param angleDelta Change from previous valid angle (for diagnostics)
     */
    data class FilterResult(
        val angle: Float?,
        val isValid: Boolean,
        val isOutlier: Boolean,
        val angleDelta: Float? = null
    )
    
    /**
     * Applies crank angle filtering and smoothing to a measurement.
     * 
     * Process:
     * 1. Estimate angular velocity from recent frames
     * 2. Calculate dynamic outlier threshold based on velocity
     * 3. Check if angle change from last valid frame exceeds threshold
     * 4. If valid, apply exponential moving average smoothing
     * 5. Implement adaptive recovery when too many consecutive outliers occur
     * 6. Return smoothed angle or null if rejected
     * 
     * Recovery mechanism: After N consecutive outliers, temporarily increase threshold
     * to 3.5x velocity to allow re-synchronization. This prevents filter lock-up when
     * pose detection has systematic bias.
     * 
     * @param rawAngle Raw crank angle from pose detection (0-360°)
     * @param state Mutable filter state (updated in-place)
     * @param config Filter configuration
     * @return FilterResult with filtered angle and diagnostic info
     */
    fun filterAngle(
        rawAngle: Float,
        state: FilterState,
        config: FilterConfig = FilterConfig()
    ): FilterResult {
        // Validate input
        if (rawAngle !in 0f..360f) {
            return FilterResult(null, false, false, null)
        }
        
        // Check for outliers: angle change too large
        if (state.lastValidAngle != null) {
            val angleDelta = calculateAngleDelta(state.lastValidAngle!!, rawAngle)
            
            // Estimate angular velocity and calculate adaptive threshold
            val estimatedVelocity = estimateAngularVelocity(state, config)
            
            // Calculate threshold: use recovery threshold if in recovery mode
            val thresholdMultiplier = if (state.recoveryMode) {
                config.recoveryThresholdMultiplier  // More lenient in recovery
            } else {
                config.velocityThresholdMultiplier  // Normal mode
            }
            
            val adaptiveThreshold = maxOf(
                estimatedVelocity * thresholdMultiplier,
                config.maxAngleChangePerFrame  // Never go below static threshold
            )
            
            if (abs(angleDelta) > adaptiveThreshold) {
                // Outlier rejected - BUT still record delta with low confidence
                // so velocity estimation adapts instead of freezing RPM
                state.framesSinceLastValid++
                state.consecutiveOutliers++
                state.recentDeltas.add(abs(angleDelta) * 0.5f)  // 50% weight for outliers
                if (state.recentDeltas.size > config.velocityHistorySize) {
                    state.recentDeltas.removeAt(0)
                }
                
                // Enter recovery mode if too many consecutive outliers
                if (state.consecutiveOutliers >= config.consecutiveOutlierThreshold && !state.recoveryMode) {
                    state.recoveryMode = true
                logWarn(
                        "CrankAngleFilter",
                        "filterAngle RECOVERY ACTIVATED: ${state.consecutiveOutliers} consecutive outliers detected. " +
                        "Threshold increased to ${String.format("%.2f", adaptiveThreshold)}° for recovery."
                    )
                }
                
                logDebug(
                    "CrankAngleFilter", 
                    "filterAngle OUTLIER: rejecting angle with excessive delta. raw=$rawAngle°, delta=$angleDelta°, " +
                    "velocity=${String.format("%.2f", estimatedVelocity)}°/frame, threshold=${String.format("%.2f", adaptiveThreshold)}°, " +
                    "retaining smoothed=${state.smoothedAngle}° (recorded delta @ 0.5x weight)"
                )
                return FilterResult(
                    angle = state.smoothedAngle,  // Return last smoothed value
                    isValid = false,
                    isOutlier = true,
                    angleDelta = angleDelta
                )
            }
            
            // Valid measurement - apply smoothing and record full-weight delta
            state.lastValidAngle = rawAngle
            state.framesSinceLastValid = 0
            
            // Exit recovery mode on successful measurement
            if (state.recoveryMode && state.consecutiveOutliers > 0) {
                logInfo(
                    "CrankAngleFilter",
                    "filterAngle RECOVERY COMPLETE: filter re-synchronized after ${state.consecutiveOutliers} outliers"
                )
                state.recoveryMode = false
                state.consecutiveOutliers = 0  // Reset consecutive outlier counter
            }
            
            state.recentDeltas.add(abs(angleDelta))  // Full weight for valid measurements
            if (state.recentDeltas.size > config.velocityHistorySize) {
                state.recentDeltas.removeAt(0)
            }
            
            // Exponential moving average
            val previousSmoothed = state.smoothedAngle ?: rawAngle
            state.smoothedAngle = if (state.smoothedAngle == null) {
                rawAngle
            } else {
                // New = smoothingFactor × Current + (1 - smoothingFactor) × Historical
                config.smoothingFactor * rawAngle + (1f - config.smoothingFactor) * previousSmoothed
            }
            
            logDebug(
                "CrankAngleFilter", 
                "filterAngle VALID: frame has valid angle. raw=$rawAngle°, delta=$angleDelta°, " +
                "smoothed=${state.smoothedAngle!!}°, velocity=${String.format("%.2f", estimatedVelocity)}°/frame"
            )
            
            return FilterResult(
                angle = state.smoothedAngle!!,
                isValid = true,
                isOutlier = false,
                angleDelta = angleDelta
            )
        } else {
            // First measurement
            state.lastValidAngle = rawAngle
            state.smoothedAngle = rawAngle
            state.framesSinceLastValid = 0
            
            logDebug(
                "CrankAngleFilter", 
                "filterAngle FIRST: initializing filter state. raw=$rawAngle°, smoothed=$rawAngle°"
            )
            
            return FilterResult(
                angle = rawAngle,
                isValid = true,
                isOutlier = false,
                angleDelta = null
            )
        }
    }
    
    /**
     * Estimates angular velocity (degrees per frame) from recent frame history.
     * 
     * Uses MEDIAN of recent deltas to avoid outlier spikes skewing the estimate.
     * Measurements rejected as outliers are included with reduced weight to avoid RPM freezing.
     * 
     * Requires at least 5 samples before using median to ensure stability.
     * Falls back to average if insufficient samples.
     * 
     * @param state Current filter state with recent deltas and visibility info
     * @param config Filter configuration with velocity history size
     * @return Estimated angular velocity in degrees/frame (always positive)
     */
    fun estimateAngularVelocity(state: FilterState, config: FilterConfig): Float {
        if (state.recentDeltas.isEmpty()) {
            return config.maxAngleChangePerFrame / config.velocityThresholdMultiplier
        }
        
        // Use median to reject outlier spikes in velocity estimate
        // This prevents a single large frame from causing threshold collapse
        val sortedDeltas = state.recentDeltas.sorted()
        val medianDelta = if (sortedDeltas.size % 2 == 0) {
            (sortedDeltas[sortedDeltas.size / 2 - 1] + sortedDeltas[sortedDeltas.size / 2]) / 2f
        } else {
            sortedDeltas[sortedDeltas.size / 2]
        }
        
        // Use 75th percentile instead of median to better capture faster movements
        // This prevents underestimating velocity during acceleration phases (like 0-90° power zone)
        val sortedVelocity = if (sortedDeltas.size >= 4) {
            val index75 = ((sortedDeltas.size - 1) * 0.75f).toInt()
            sortedDeltas[index75]
        } else {
            medianDelta
        }
        
        // Require at least 5 recent samples for velocity estimate confidence
        return if (state.recentDeltas.size >= 5) {
            sortedVelocity
        } else if (state.recentDeltas.size >= 3) {
            // Blend 75th percentile with average during ramp-up
            0.6f * sortedVelocity + 0.4f * state.recentDeltas.average().toFloat()
        } else {
            // Early on, blend estimated with fallback threshold
            val avgDelta = state.recentDeltas.average().toFloat()
            val weight = state.recentDeltas.size / 3f
            weight * avgDelta + (1f - weight) * (config.maxAngleChangePerFrame / config.velocityThresholdMultiplier)
        }
    }
    
    /**
     * Calculates the signed angle difference between two angles, handling wrapping.
     * 
     * For crank angle, prefers forward rotation (positive deltas) when wrapping occurs.
     * This assumes the cyclist is pedaling forward.
     * 
     * Examples:
     * - 10° to 20° = 10°
     * - 350° to 10° = 20° (not -340°, treats as forward rotation)
     * - 180° to 179° = -1°
     * - 5° to 355° = -10° (not 350°, treats as backward rotation)
     * 
     * @param fromAngle Starting angle (0-360°)
     * @param toAngle Ending angle (0-360°)
     * @return Signed angle delta (-180° to +180°), preferring forward rotation
     */
    fun calculateAngleDelta(fromAngle: Float, toAngle: Float): Float {
        var delta = toAngle - fromAngle
        
        // Normalize to [-180, 180] range
        while (delta > 180f) delta -= 360f
        while (delta < -180f) delta += 360f
        
        return delta
    }
    
    /**\n     * Converts angular velocity (degrees per frame) to RPM using adaptive frame rate.
     * 
     * @param degreesPerFrame Angular velocity in degrees/frame
     * @param fps Actual frame rate in frames per second (adaptive or default ~30)
     * @return Revolutions per minute (RPM)
     */
    fun getInstantaneousRpm(degreesPerFrame: Float, fps: Float = 30f): Float {
        // RPM = (deg/frame) / 360° × (frames/second) × 60 seconds/min
        // = (deg/frame) × fps × 60 / 360
        // = (deg/frame) × fps / 6
        return degreesPerFrame * fps / 6f
    }
    
    /**
     * Resets filter state (useful when detection is lost or restarting).
     */
    fun reset(state: FilterState) {
        state.lastValidAngle = null
        state.smoothedAngle = null
        state.framesSinceLastValid = 0
        state.recentDeltas.clear()
    }
}
