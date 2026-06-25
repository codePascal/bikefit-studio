package bikefitstudio.biomechanics

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.*

/**
 * Unit tests for SavitzkyGolayFilter.
 * 
 * Tests verify:
 * - Correct initialization and parameter validation
 * - Streaming buffer behavior
 * - Smoothing effectiveness on noisy data
 * - Extrema preservation
 * - Zero phase shift (centered window)
 * - Real-time operation
 */
class SavitzkyGolayFilterTest {

    private val delta = 0.01  // Tolerance for floating-point comparisons

    @Test
    fun `constructor validates window size must be odd`() {
        assertThrows(IllegalArgumentException::class.java) {
            SavitzkyGolayFilter(windowSize = 10, polynomialOrder = 3)
        }
    }

    @Test
    fun `constructor validates window size must be at least 3`() {
        assertThrows(IllegalArgumentException::class.java) {
            SavitzkyGolayFilter(windowSize = 1, polynomialOrder = 0)
        }
    }

    @Test
    fun `constructor validates polynomial order must be less than window size`() {
        assertThrows(IllegalArgumentException::class.java) {
            SavitzkyGolayFilter(windowSize = 5, polynomialOrder = 5)
        }
        
        assertThrows(IllegalArgumentException::class.java) {
            SavitzkyGolayFilter(windowSize = 5, polynomialOrder = 6)
        }
    }

    @Test
    fun `constructor validates polynomial order must be non-negative`() {
        assertThrows(IllegalArgumentException::class.java) {
            SavitzkyGolayFilter(windowSize = 5, polynomialOrder = -1)
        }
    }

    @Test
    fun `constructor accepts valid parameters`() {
        // Should not throw
        SavitzkyGolayFilter(windowSize = 3, polynomialOrder = 1)
        SavitzkyGolayFilter(windowSize = 5, polynomialOrder = 2)
        SavitzkyGolayFilter(windowSize = 11, polynomialOrder = 3)
        SavitzkyGolayFilter(windowSize = 21, polynomialOrder = 5)
    }

    @Test
    fun `addSample returns null until buffer is full`() {
        val filter = SavitzkyGolayFilter(windowSize = 5, polynomialOrder = 2)
        
        // First 4 samples should return null (window size - 1)
        assertNull(filter.addSample(1.0))
        assertNull(filter.addSample(2.0))
        assertNull(filter.addSample(3.0))
        assertNull(filter.addSample(4.0))
        
        // 5th sample should return a value
        assertNotNull(filter.addSample(5.0))
    }

    @Test
    fun `addSample returns smoothed values once buffer is full`() {
        val filter = SavitzkyGolayFilter(windowSize = 5, polynomialOrder = 2)
        
        // Fill buffer
        for (i in 1..4) {
            filter.addSample(i.toDouble())
        }
        
        // Now should get smoothed values
        val result1 = filter.addSample(5.0)
        assertNotNull(result1)
        
        val result2 = filter.addSample(6.0)
        assertNotNull(result2)
    }

    @Test
    fun `filter smooths constant signal to itself`() {
        val filter = SavitzkyGolayFilter(windowSize = 5, polynomialOrder = 2)
        
        // Add constant values
        val constant = 100.0
        for (i in 1..10) {
            val result = filter.addSample(constant)
            if (result != null) {
                assertEquals(constant, result, delta, 
                    "Constant signal should remain constant after filtering")
            }
        }
    }

    @Test
    fun `filter smooths linear ramp correctly`() {
        val filter = SavitzkyGolayFilter(windowSize = 5, polynomialOrder = 2)
        
        // Add linear ramp: 0, 1, 2, 3, 4, 5, ...
        val results = mutableListOf<Double>()
        for (i in 0..10) {
            val result = filter.addSample(i.toDouble())
            if (result != null) {
                results.add(result)
            }
        }
        
        // Linear signal should pass through with minimal distortion
        // Check that smoothed values are close to the expected linear values
        assertTrue(results.isNotEmpty())
        for ((index, smoothed) in results.withIndex()) {
            // The center of the first full window is at index 2 (0,1,2,3,4)
            val expectedValue = index + 2.0
            assertEquals(expectedValue, smoothed, 0.1,
                "Linear ramp should be preserved")
        }
    }

    @Test
    fun `filter reduces noise on sinusoidal signal`() {
        val filter = SavitzkyGolayFilter(windowSize = 11, polynomialOrder = 3)
        
        // Generate noisy sinusoid
        val cleanSignal = mutableListOf<Double>()
        val noisySignal = mutableListOf<Double>()
        val smoothedSignal = mutableListOf<Double>()
        
        for (i in 0..50) {
            val t = i * 0.1
            val clean = sin(t)
            val noise = (Math.random() - 0.5) * 0.3  // ±15% noise
            val noisy = clean + noise
            
            cleanSignal.add(clean)
            noisySignal.add(noisy)
            
            val result = filter.addSample(noisy)
            if (result != null) {
                smoothedSignal.add(result)
            }
        }
        
        // Calculate RMS error between clean and noisy
        val noisyError = calculateRMSError(
            cleanSignal.drop(5).take(smoothedSignal.size), 
            noisySignal.drop(5).take(smoothedSignal.size)
        )
        
        // Calculate RMS error between clean and smoothed
        val smoothedError = calculateRMSError(
            cleanSignal.drop(5).take(smoothedSignal.size),
            smoothedSignal
        )
        
        // Smoothed should have lower error than noisy
        assertTrue(smoothedError < noisyError,
            "Smoothed signal should be closer to clean signal than noisy signal " +
            "(smoothed error: $smoothedError, noisy error: $noisyError)")
    }

    @Test
    fun `filter preserves extrema location within 1 degree for angle data`() {
        val filter = SavitzkyGolayFilter(windowSize = 11, polynomialOrder = 3)
        
        // Create a clean peaked signal without noise for predictable testing
        // Place peak well after buffer fills (at index 30)
        val peakIndex = 30
        val cleanAngles = mutableListOf<Double>()
        val smoothedAngles = mutableListOf<Double>()
        
        for (i in 0..60) {
            // Create a smooth Gaussian peak centered at peakIndex
            val distanceFromPeak = (i - peakIndex).toDouble()
            val angle = 30.0 + 120.0 * exp(-distanceFromPeak * distanceFromPeak / 100.0)
            cleanAngles.add(angle)
            
            val result = filter.addSample(angle)
            if (result != null) {
                smoothedAngles.add(result)
            }
        }
        
        // Find peak in smoothed data
        val smoothedPeakValue = smoothedAngles.max()
        val smoothedPeakIndex = smoothedAngles.indexOf(smoothedPeakValue)
        
        // Find peak in clean input data
        val cleanPeakValue = cleanAngles.max()
        val cleanPeakIndex = cleanAngles.indexOf(cleanPeakValue)
        
        // First output corresponds to center of first window (input index 5)
        // So output[k] corresponds to input[k+5]
        // Therefore, input peak at index 30 should give output peak at index 25
        val expectedOutputPeakIndex = cleanPeakIndex - 5
        
        // Verify peak location is preserved
        val peakLocationError = abs(smoothedPeakIndex - expectedOutputPeakIndex)
        assertTrue(peakLocationError <= 2,
            "Peak location should be preserved within 2 samples " +
            "(expected index $expectedOutputPeakIndex, got $smoothedPeakIndex, error: $peakLocationError)")
        
        // Verify peak value is preserved within 1 degree
        val peakValueError = abs(smoothedPeakValue - cleanPeakValue)
        assertTrue(peakValueError < 1.0,
            "Peak value should be preserved within 1 degree " +
            "(expected ${cleanPeakValue}, got ${smoothedPeakValue}, error: ${peakValueError}°)")
    }

    @Test
    fun `filter with default configuration for 60 FPS`() {
        // Default configuration from requirements: windowSize=11, polyOrder=3
        val filter = SavitzkyGolayFilter(windowSize = 11, polynomialOrder = 3)
        
        // Should not throw and should work
        val results = mutableListOf<Double?>()
        for (i in 0..20) {
            results.add(filter.addSample(i.toDouble()))
        }
        
        // First 10 should be null (buffer filling)
        for (i in 0..9) {
            assertNull(results[i], "First 10 samples should be null")
        }
        
        // Remaining should have values
        for (i in 10..20) {
            assertNotNull(results[i], "Samples after buffer full should have values")
        }
    }

    @Test
    fun `filter exhibits zero phase shift with centered window`() {
        val filter = SavitzkyGolayFilter(windowSize = 11, polynomialOrder = 3)
        
        // Create a signal with a clear feature (step function smoothed)
        val signal = mutableListOf<Double>()
        for (i in 0..40) {
            // Smooth step at i=20
            val value = if (i < 20) 0.0 else 100.0
            signal.add(value)
        }
        
        // Apply filter
        val smoothed = mutableListOf<Double>()
        for (value in signal) {
            val result = filter.addSample(value)
            if (result != null) {
                smoothed.add(result)
            }
        }
        
        // Find midpoint of transition in smoothed signal
        val midValue = 50.0
        val transitionIndex = smoothed.indexOfFirst { it > midValue }
        
        // Transition should happen around the same place as input
        // Account for buffer offset
        val expectedTransitionIndex = 20 - 5  // 20 - (windowSize-1)/2
        
        // Should be within a couple samples (phase shift tolerance)
        val phaseShift = abs(transitionIndex - expectedTransitionIndex)
        assertTrue(phaseShift <= 2,
            "Phase shift should be minimal (was $phaseShift samples)")
    }

    @Test
    fun `reset clears filter state`() {
        val filter = SavitzkyGolayFilter(windowSize = 5, polynomialOrder = 2)
        
        // Fill buffer
        for (i in 1..5) {
            filter.addSample(i.toDouble())
        }
        
        // Should get non-null result
        val result1 = filter.addSample(6.0)
        assertNotNull(result1)
        
        // Reset
        filter.reset()
        
        // Should need to fill buffer again
        assertNull(filter.addSample(1.0))
        assertNull(filter.addSample(2.0))
        assertNull(filter.addSample(3.0))
        assertNull(filter.addSample(4.0))
        
        // Now should get a result
        assertNotNull(filter.addSample(5.0))
    }

    @Test
    fun `filter works with small window size`() {
        val filter = SavitzkyGolayFilter(windowSize = 3, polynomialOrder = 1)
        
        // Should work with minimum valid configuration
        assertNull(filter.addSample(1.0))
        assertNull(filter.addSample(2.0))
        
        val result = filter.addSample(3.0)
        assertNotNull(result)
        
        // Result should be reasonable (close to center value)
        assertEquals(2.0, result!!, 0.5)
    }

    @Test
    fun `filter handles large window size`() {
        val filter = SavitzkyGolayFilter(windowSize = 21, polynomialOrder = 5)
        
        // Fill buffer with linear ramp
        for (i in 0..30) {
            val result = filter.addSample(i.toDouble())
            if (result != null) {
                // Should produce reasonable values
                assertTrue(result >= 0.0 && result <= 30.0)
            }
        }
    }

    /**
     * Calculates root mean square error between two signals.
     */
    private fun calculateRMSError(signal1: List<Double>, signal2: List<Double>): Double {
        require(signal1.size == signal2.size) { "Signals must have same length" }
        
        var sumSquaredError = 0.0
        for (i in signal1.indices) {
            val error = signal1[i] - signal2[i]
            sumSquaredError += error * error
        }
        
        return sqrt(sumSquaredError / signal1.size)
    }
}
