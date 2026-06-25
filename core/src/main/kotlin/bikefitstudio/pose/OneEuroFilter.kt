package bikefitstudio.pose

import kotlin.math.abs

/**
 * One Euro Filter for temporal smoothing with adaptive cutoff frequency.
 * 
 * Based on Casiez et al. 2012: "1€ Filter: A Simple Speed-based Low-pass Filter
 * for Noisy Input in Interactive Systems"
 * 
 * The One Euro Filter adapts its cutoff frequency based on the signal's speed:
 * - When the signal moves slowly, uses high smoothing (low cutoff frequency)
 * - When the signal moves quickly, reduces smoothing (high cutoff frequency) to maintain responsiveness
 * 
 * This approach reduces jitter during slow movements while maintaining responsiveness
 * during fast movements, making it ideal for pose landmark smoothing.
 * 
 * Formula:
 * - cutoff_frequency = minCutoff + beta * |velocity|
 * - alpha = 1 / (1 + tau / deltaTime) where tau = 1 / (2 * PI * cutoff_frequency)
 * - filtered_value = alpha * raw_value + (1 - alpha) * previous_filtered
 * 
 * @param minCutoff Minimum cutoff frequency (Hz). Lower = more smoothing. Default: 1.0 Hz
 * @param beta Speed coefficient. Controls how much the cutoff frequency increases with velocity. Default: 0.02
 * @param dCutoff Cutoff frequency for velocity computation (Hz). Default: 1.0 Hz
 * 
 * Thread Safety: This class is NOT thread-safe. Use a single instance per thread.
 */
class OneEuroFilter(
    private val minCutoff: Double = DEFAULT_MIN_CUTOFF,
    private val beta: Double = DEFAULT_BETA,
    private val dCutoff: Double = DEFAULT_D_CUTOFF
) {
    init {
        require(minCutoff > 0.0) { "minCutoff must be positive, got $minCutoff" }
        require(beta >= 0.0) { "beta must be non-negative, got $beta" }
        require(dCutoff > 0.0) { "dCutoff must be positive, got $dCutoff" }
    }

    /**
     * Previous filtered value. Null if no value has been filtered yet.
     */
    private var previousFiltered: Double? = null

    /**
     * Previous filtered derivative (velocity). Null if no derivative computed yet.
     */
    private var previousFilteredDerivative: Double? = null

    /**
     * Timestamp of the previous sample in seconds.
     */
    private var previousTimestamp: Double? = null

    /**
     * Filters a single value using the One Euro Filter algorithm.
     * 
     * @param value The raw input value to filter
     * @param timestamp The timestamp of the value in seconds
     * @return The filtered value
     */
    fun filter(value: Double, timestamp: Double): Double {
        val prevFiltered = previousFiltered
        val prevTimestamp = previousTimestamp
        
        // First call - initialize with current value
        if (prevFiltered == null || prevTimestamp == null) {
            previousFiltered = value
            previousTimestamp = timestamp
            previousFilteredDerivative = 0.0
            return value
        }

        // Calculate time delta
        val deltaTime = timestamp - prevTimestamp
        
        // Prevent division by zero and handle invalid timestamps
        if (deltaTime <= 0.0) {
            // If timestamps are the same or invalid, return the previous filtered value
            return prevFiltered
        }

        // Calculate raw derivative (velocity)
        val derivative = (value - prevFiltered) / deltaTime

        // Filter the derivative using low-pass filter with dCutoff
        val alpha = calculateAlpha(deltaTime, dCutoff)
        val prevFilteredDeriv = previousFilteredDerivative ?: derivative
        val filteredDerivative = alpha * derivative + (1.0 - alpha) * prevFilteredDeriv

        // Calculate adaptive cutoff frequency based on filtered derivative
        val cutoff = minCutoff + beta * abs(filteredDerivative)

        // Filter the value using low-pass filter with adaptive cutoff
        val valueAlpha = calculateAlpha(deltaTime, cutoff)
        val filteredValue = valueAlpha * value + (1.0 - valueAlpha) * prevFiltered

        // Update state for next call
        previousFiltered = filteredValue
        previousFilteredDerivative = filteredDerivative
        previousTimestamp = timestamp

        return filteredValue
    }

    /**
     * Resets the filter state, clearing all previous values.
     * 
     * Call this when starting a new sequence or when there's a discontinuity
     * in the data (e.g., tracking lost and reacquired).
     */
    fun reset() {
        previousFiltered = null
        previousFilteredDerivative = null
        previousTimestamp = null
    }

    /**
     * Returns true if the filter has been initialized with at least one value.
     */
    fun isInitialized(): Boolean = previousFiltered != null

    /**
     * Calculates the smoothing factor (alpha) for the low-pass filter.
     * 
     * Formula: alpha = 1 / (1 + tau / deltaTime)
     * where tau = 1 / (2 * PI * cutoff_frequency)
     * 
     * @param deltaTime Time difference between samples (seconds)
     * @param cutoff Cutoff frequency (Hz)
     * @return Smoothing factor alpha (0.0 to 1.0)
     */
    private fun calculateAlpha(deltaTime: Double, cutoff: Double): Double {
        val tau = 1.0 / (2.0 * Math.PI * cutoff)
        return 1.0 / (1.0 + tau / deltaTime)
    }

    companion object {
        /** Default minimum cutoff frequency in Hz */
        const val DEFAULT_MIN_CUTOFF = 1.0

        /** Default speed coefficient */
        const val DEFAULT_BETA = 0.02

        /** Default derivative cutoff frequency in Hz */
        const val DEFAULT_D_CUTOFF = 1.0
    }
}
