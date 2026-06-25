package bikefitstudio.pose

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests for OneEuroFilter.
 * 
 * Tests cover:
 * - Basic filtering behavior
 * - Adaptive cutoff frequency
 * - Jitter reduction
 * - Edge cases (zero delta time, first sample, reset)
 * - Parameter validation
 */
class OneEuroFilterTest {

    private lateinit var filter: OneEuroFilter

    @Before
    fun setup() {
        filter = OneEuroFilter(
            minCutoff = 1.0,
            beta = 0.02,
            dCutoff = 1.0
        )
    }

    // ==================== Initialization Tests ====================

    @Test
    fun `filter is not initialized before first call`() {
        assertFalse(filter.isInitialized())
    }

    @Test
    fun `filter is initialized after first call`() {
        filter.filter(0.5, 0.0)
        assertTrue(filter.isInitialized())
    }

    @Test
    fun `first value is returned unchanged`() {
        val result = filter.filter(0.5, 0.0)
        assertEquals(0.5, result, 0.001)
    }

    @Test
    fun `reset clears filter state`() {
        filter.filter(0.5, 0.0)
        assertTrue(filter.isInitialized())
        
        filter.reset()
        assertFalse(filter.isInitialized())
    }

    @Test
    fun `after reset first value is returned unchanged`() {
        filter.filter(0.5, 0.0)
        filter.filter(1.0, 0.1)
        
        filter.reset()
        
        val result = filter.filter(0.3, 0.0)
        assertEquals(0.3, result, 0.001)
    }

    // ==================== Basic Filtering Tests ====================

    @Test
    fun `filter smooths step change`() {
        // Initialize with 0.0
        filter.filter(0.0, 0.0)
        
        // Apply step change to 1.0
        val result = filter.filter(1.0, 0.1)
        
        // Result should be between 0.0 and 1.0 (smoothed)
        assertTrue("Expected smoothed value between 0.0 and 1.0, got $result", 
            result > 0.0 && result < 1.0)
    }

    @Test
    fun `filter converges to stable value`() {
        // Send constant value multiple times
        var result = 0.0
        for (i in 0..100) {
            result = filter.filter(1.0, i * 0.1)
        }
        
        // Should converge close to 1.0
        assertEquals(1.0, result, 0.01)
    }

    @Test
    fun `filter reduces jitter in noisy signal`() {
        // Create noisy signal oscillating around 0.5
        val noisyValues = listOf(0.5, 0.55, 0.45, 0.52, 0.48, 0.53, 0.47, 0.51, 0.49, 0.54)
        val smoothedValues = mutableListOf<Double>()
        
        noisyValues.forEachIndexed { index, value ->
            val timestamp = index * 0.1
            val result = filter.filter(value, timestamp)
            smoothedValues.add(result)
        }
        
        // Calculate variance
        val noisyVariance = variance(noisyValues)
        val smoothedVariance = variance(smoothedValues)
        
        // Smoothed variance should be lower
        assertTrue(
            "Smoothed variance ($smoothedVariance) should be less than noisy ($noisyVariance)",
            smoothedVariance < noisyVariance
        )
    }

    // ==================== Adaptive Cutoff Tests ====================

    @Test
    fun `filter is more responsive during fast movement`() {
        val filter1 = OneEuroFilter(minCutoff = 1.0, beta = 0.02, dCutoff = 1.0)
        val filter2 = OneEuroFilter(minCutoff = 1.0, beta = 0.02, dCutoff = 1.0)
        
        // Initialize both filters
        filter1.filter(0.0, 0.0)
        filter2.filter(0.0, 0.0)
        
        // Slow movement for filter1
        val slowResult = filter1.filter(0.1, 0.1)
        
        // Fast movement for filter2 (same time delta)
        val fastResult = filter2.filter(1.0, 0.1)
        
        // Fast movement should be less smoothed (closer to raw value)
        // fastResult should be larger than slowResult relative to their inputs
        val slowRatio = slowResult / 0.1
        val fastRatio = fastResult / 1.0
        
        assertTrue("Fast movement should be less smoothed", fastRatio > slowRatio)
    }

    @Test
    fun `higher beta increases responsiveness`() {
        val lowBetaFilter = OneEuroFilter(minCutoff = 1.0, beta = 0.01, dCutoff = 1.0)
        val highBetaFilter = OneEuroFilter(minCutoff = 1.0, beta = 0.1, dCutoff = 1.0)
        
        // Initialize both
        lowBetaFilter.filter(0.0, 0.0)
        highBetaFilter.filter(0.0, 0.0)
        
        // Apply same step change
        val lowBetaResult = lowBetaFilter.filter(1.0, 0.1)
        val highBetaResult = highBetaFilter.filter(1.0, 0.1)
        
        // Higher beta should result in value closer to raw input
        assertTrue(
            "Higher beta should be more responsive: low=$lowBetaResult, high=$highBetaResult",
            highBetaResult > lowBetaResult
        )
    }

    @Test
    fun `lower minCutoff increases smoothing`() {
        val highCutoffFilter = OneEuroFilter(minCutoff = 2.0, beta = 0.02, dCutoff = 1.0)
        val lowCutoffFilter = OneEuroFilter(minCutoff = 0.5, beta = 0.02, dCutoff = 1.0)
        
        // Initialize both
        highCutoffFilter.filter(0.0, 0.0)
        lowCutoffFilter.filter(0.0, 0.0)
        
        // Apply same step change
        val highCutoffResult = highCutoffFilter.filter(1.0, 0.1)
        val lowCutoffResult = lowCutoffFilter.filter(1.0, 0.1)
        
        // Lower cutoff should result in more smoothing (value further from raw input)
        assertTrue(
            "Lower minCutoff should produce more smoothing: high=$highCutoffResult, low=$lowCutoffResult",
            lowCutoffResult < highCutoffResult
        )
    }

    // ==================== Edge Case Tests ====================

    @Test
    fun `zero time delta returns previous filtered value`() {
        filter.filter(0.0, 1.0)
        val result1 = filter.filter(0.5, 2.0)
        
        // Same timestamp - should return previous filtered value
        val result2 = filter.filter(1.0, 2.0)
        
        assertEquals("Zero time delta should return previous value", result1, result2, 0.001)
    }

    @Test
    fun `negative time delta returns previous filtered value`() {
        filter.filter(0.0, 2.0)
        val result1 = filter.filter(0.5, 3.0)
        
        // Go backwards in time - should return previous filtered value
        val result2 = filter.filter(1.0, 1.0)
        
        assertEquals("Negative time delta should return previous value", result1, result2, 0.001)
    }

    @Test
    fun `handles large time delta`() {
        filter.filter(0.0, 0.0)
        
        // Large time gap
        val result = filter.filter(1.0, 10.0)
        
        // With large time delta, filter should be very responsive (close to raw value)
        assertTrue("Large time delta should result in minimal smoothing", result > 0.9)
    }

    @Test
    fun `handles very small time delta`() {
        filter.filter(0.0, 0.0)
        
        // Very small time gap
        val result = filter.filter(1.0, 0.001)
        
        // Result should still be valid and between 0 and 1
        assertTrue("Result should be valid: $result", result >= 0.0 && result <= 1.0)
    }

    @Test
    fun `handles negative values`() {
        filter.filter(0.0, 0.0)
        
        val result = filter.filter(-0.5, 0.1)
        
        // Should handle negative values correctly
        assertTrue("Should handle negative values", result < 0.0)
    }

    @Test
    fun `handles large values`() {
        filter.filter(0.0, 0.0)
        
        val result = filter.filter(1000.0, 0.1)
        
        // Should handle large values without overflow
        assertTrue("Should handle large values", result > 0.0 && result < 1000.0)
    }

    // ==================== Parameter Validation Tests ====================

    @Test(expected = IllegalArgumentException::class)
    fun `constructor rejects negative minCutoff`() {
        OneEuroFilter(minCutoff = -1.0, beta = 0.02, dCutoff = 1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `constructor rejects zero minCutoff`() {
        OneEuroFilter(minCutoff = 0.0, beta = 0.02, dCutoff = 1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `constructor rejects negative beta`() {
        OneEuroFilter(minCutoff = 1.0, beta = -0.1, dCutoff = 1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `constructor rejects negative dCutoff`() {
        OneEuroFilter(minCutoff = 1.0, beta = 0.02, dCutoff = -1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `constructor rejects zero dCutoff`() {
        OneEuroFilter(minCutoff = 1.0, beta = 0.02, dCutoff = 0.0)
    }

    @Test
    fun `constructor accepts zero beta`() {
        // Zero beta should be valid (no adaptive behavior)
        val filter = OneEuroFilter(minCutoff = 1.0, beta = 0.0, dCutoff = 1.0)
        assertNotNull(filter)
    }

    // ==================== Default Parameters Test ====================

    @Test
    fun `default parameters work correctly`() {
        val defaultFilter = OneEuroFilter()
        
        defaultFilter.filter(0.0, 0.0)
        val result = defaultFilter.filter(1.0, 0.1)
        
        // Should produce a valid smoothed result
        assertTrue("Default parameters should work", result > 0.0 && result < 1.0)
    }

    // ==================== Performance Test ====================

    @Test
    fun `filter executes quickly`() {
        val iterations = 10000
        val startTime = System.nanoTime()
        
        for (i in 0 until iterations) {
            filter.filter(Math.sin(i * 0.1), i * 0.1)
        }
        
        val endTime = System.nanoTime()
        val durationMs = (endTime - startTime) / 1_000_000.0
        val avgTimePerCallUs = (durationMs * 1000.0) / iterations
        
        // Each call should take less than 10 microseconds on average
        assertTrue(
            "Average time per call should be < 10μs, got ${avgTimePerCallUs}μs",
            avgTimePerCallUs < 10.0
        )
    }

    // ==================== Jitter Reduction Validation ====================

    @Test
    fun `validates jitter reduction with recorded sequence`() {
        // Simulate a sequence of noisy landmark positions
        // This represents a landmark that should be at position 0.5
        // but has measurement noise
        val groundTruth = 0.5
        val noiseAmplitude = 0.05
        val samples = 100
        
        val noisySequence = (0 until samples).map { i ->
            val noise = Math.sin(i * 0.5) * noiseAmplitude + Math.cos(i * 0.3) * noiseAmplitude * 0.5
            groundTruth + noise
        }
        
        // Apply filter
        val smoothedSequence = mutableListOf<Double>()
        noisySequence.forEachIndexed { index, value ->
            val result = filter.filter(value, index * 0.04) // 25 FPS
            smoothedSequence.add(result)
        }
        
        // Skip first few samples for initialization
        val skipSamples = 10
        val noisySubset = noisySequence.drop(skipSamples)
        val smoothedSubset = smoothedSequence.drop(skipSamples)
        
        // Calculate RMS error from ground truth
        val noisyRMSE = rmse(noisySubset, groundTruth)
        val smoothedRMSE = rmse(smoothedSubset, groundTruth)
        
        // Smoothed should have lower error
        assertTrue(
            "Smoothed RMSE ($smoothedRMSE) should be less than noisy RMSE ($noisyRMSE)",
            smoothedRMSE < noisyRMSE
        )
        
        // Calculate jitter (mean absolute consecutive difference)
        val noisyJitter = jitter(noisySubset)
        val smoothedJitter = jitter(smoothedSubset)
        
        assertTrue(
            "Smoothed jitter ($smoothedJitter) should be less than noisy jitter ($noisyJitter)",
            smoothedJitter < noisyJitter
        )
    }

    // ==================== Helper Functions ====================

    private fun variance(values: List<Double>): Double {
        val mean = values.average()
        return values.map { (it - mean) * (it - mean) }.average()
    }

    private fun rmse(values: List<Double>, groundTruth: Double): Double {
        val squaredErrors = values.map { (it - groundTruth) * (it - groundTruth) }
        return Math.sqrt(squaredErrors.average())
    }

    private fun jitter(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val diffs = values.zipWithNext { a, b -> abs(b - a) }
        return diffs.average()
    }
}
