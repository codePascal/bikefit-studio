package bikefitstudio.biomechanics

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for JointAngleFilter.
 */
class JointAngleFilterTest {

    @Test
    fun `filter returns null until buffer is full for each angle type`() {
        val filter = JointAngleFilter(windowSize = 5, polynomialOrder = 2)
        
        // All angle types should return null initially
        assertNull(filter.filterKneeAngle(100f))
        assertNull(filter.filterHipAngle(50f))
        assertNull(filter.filterAnkleAngle(20f))
        assertNull(filter.filterTorsoAngle(45f))
        
        // After a few more samples, still null
        for (i in 1..3) {
            assertNull(filter.filterKneeAngle(100f + i))
            assertNull(filter.filterHipAngle(50f + i))
            assertNull(filter.filterAnkleAngle(20f + i))
            assertNull(filter.filterTorsoAngle(45f + i))
        }
        
        // After windowSize samples, should return values
        assertNotNull(filter.filterKneeAngle(105f))
        assertNotNull(filter.filterHipAngle(55f))
        assertNotNull(filter.filterAnkleAngle(25f))
        assertNotNull(filter.filterTorsoAngle(50f))
    }

    @Test
    fun `filter handles null input gracefully`() {
        val filter = JointAngleFilter(windowSize = 5, polynomialOrder = 2)
        
        // Null input should return null without affecting filter state
        assertNull(filter.filterKneeAngle(null))
        assertNull(filter.filterHipAngle(null))
        assertNull(filter.filterAnkleAngle(null))
        assertNull(filter.filterTorsoAngle(null))
        
        // Non-null input should still work normally
        assertNull(filter.filterKneeAngle(100f))  // Still need more samples
    }

    @Test
    fun `filter with default configuration for 60 FPS`() {
        // Default: windowSize=11, polyOrder=3
        val filter = JointAngleFilter()
        
        // Should need 11 samples before returning values
        for (i in 1..10) {
            assertNull(filter.filterKneeAngle(100f + i))
        }
        
        // 11th sample should return a value
        assertNotNull(filter.filterKneeAngle(111f))
    }

    @Test
    fun `filter smooths constant values correctly`() {
        val filter = JointAngleFilter(windowSize = 5, polynomialOrder = 2)
        
        // Add constant knee angles
        val constant = 150f
        for (i in 1..10) {
            val result = filter.filterKneeAngle(constant)
            if (result != null) {
                assertEquals(constant, result, 0.01f,
                    "Constant signal should remain constant")
            }
        }
    }

    @Test
    fun `filter maintains separate state for each angle type`() {
        val filter = JointAngleFilter(windowSize = 5, polynomialOrder = 2)
        
        // Add different patterns to each angle type
        val kneeValues = mutableListOf<Float?>()
        val hipValues = mutableListOf<Float?>()
        
        for (i in 0..10) {
            val kneeResult = filter.filterKneeAngle(100f + i)  // Increasing
            val hipResult = filter.filterHipAngle(50f - i)     // Decreasing
            
            kneeValues.add(kneeResult)
            hipValues.add(hipResult)
        }
        
        // After buffer fills, both should have values but they should be different
        val kneeValue = kneeValues.last()
        val hipValue = hipValues.last()
        
        assertNotNull(kneeValue)
        assertNotNull(hipValue)
        assertNotEquals(kneeValue, hipValue,
            "Different angle types should maintain separate filter state")
    }

    @Test
    fun `reset clears all filter states`() {
        val filter = JointAngleFilter(windowSize = 5, polynomialOrder = 2)
        
        // Fill all buffers including toe and heel
        for (i in 1..5) {
            filter.filterKneeAngle(100f + i)
            filter.filterHipAngle(50f + i)
            filter.filterAnkleAngle(20f + i)
            filter.filterTorsoAngle(45f + i)
            filter.filterToeAngle(10f + i)
            filter.filterHeelAngle(15f + i)
        }
        
        // Should have non-null results
        assertNotNull(filter.filterKneeAngle(106f))
        assertNotNull(filter.filterHipAngle(56f))
        assertNotNull(filter.filterToeAngle(16f))
        assertNotNull(filter.filterHeelAngle(21f))
        
        // Reset
        filter.reset()
        
        // All should return null again
        assertNull(filter.filterKneeAngle(100f))
        assertNull(filter.filterHipAngle(50f))
        assertNull(filter.filterAnkleAngle(20f))
        assertNull(filter.filterTorsoAngle(45f))
        assertNull(filter.filterToeAngle(10f))
        assertNull(filter.filterHeelAngle(15f))
    }

    @Test
    fun `filter reduces noise in realistic angle sequence`() {
        val filter = JointAngleFilter(windowSize = 11, polynomialOrder = 3)
        
        // Simulate noisy knee angle measurements during pedal cycle
        val noisyAngles = mutableListOf<Float>()
        val smoothedAngles = mutableListOf<Float>()
        
        for (i in 0..30) {
            // Smooth sinusoidal knee angle with noise
            val cleanAngle = 90f + 60f * kotlin.math.cos(i * 0.2).toFloat()
            val noise = (Math.random() - 0.5).toFloat() * 2f  // ±1° noise
            val noisyAngle = cleanAngle + noise
            
            noisyAngles.add(noisyAngle)
            
            val smoothed = filter.filterKneeAngle(noisyAngle)
            if (smoothed != null) {
                smoothedAngles.add(smoothed)
            }
        }
        
        // Calculate variance of last 10 smoothed values
        val smoothedVariance = calculateVariance(smoothedAngles.takeLast(10))
        
        // Calculate variance of corresponding noisy values (offset by buffer delay)
        val noisyVariance = calculateVariance(
            noisyAngles.drop(5).take(smoothedAngles.size).takeLast(10)
        )
        
        // Smoothed should have lower variance than noisy
        assertTrue(smoothedVariance < noisyVariance,
            "Smoothed angles should have lower variance than noisy input " +
            "(smoothed: $smoothedVariance, noisy: $noisyVariance)")
    }

    @Test
    fun `filter can use custom window size and polynomial order`() {
        // Small window for fast response
        val fastFilter = JointAngleFilter(windowSize = 5, polynomialOrder = 2)
        
        // Need only 5 samples
        for (i in 1..4) {
            fastFilter.filterKneeAngle(100f + i)
        }
        assertNotNull(fastFilter.filterKneeAngle(105f))
        
        // Large window for heavy smoothing
        val smoothFilter = JointAngleFilter(windowSize = 21, polynomialOrder = 5)
        
        // Need 21 samples
        for (i in 1..20) {
            smoothFilter.filterKneeAngle(100f + i)
        }
        assertNotNull(smoothFilter.filterKneeAngle(121f))
    }

    @Test
    fun `filter handles toe angle measurements`() {
        val filter = JointAngleFilter(windowSize = 5, polynomialOrder = 2)
        
        // Fill buffer with constant toe angle values
        for (i in 1..4) {
            assertNull(filter.filterToeAngle(20f))
        }
        
        // Should return smoothed value once buffer is full
        val smoothed = filter.filterToeAngle(20f)
        assertNotNull(smoothed)
        
        // Continue with constant values - should converge to the constant
        var lastResult: Float? = null
        for (i in 1..10) {
            val result = filter.filterToeAngle(20f)
            if (result != null) {
                lastResult = result
            }
        }
        
        // After enough samples, should be close to constant value
        assertNotNull(lastResult)
        assertEquals(20f, lastResult!!, 0.5f,
            "Toe angle filter should converge to constant values")
    }

    @Test
    fun `filter handles heel angle measurements`() {
        val filter = JointAngleFilter(windowSize = 5, polynomialOrder = 2)
        
        // Fill buffer with constant heel angle values
        for (i in 1..4) {
            assertNull(filter.filterHeelAngle(40f))
        }
        
        // Should return smoothed value once buffer is full
        val smoothed = filter.filterHeelAngle(40f)
        assertNotNull(smoothed)
        
        // Continue with constant values - should converge to the constant
        var lastResult: Float? = null
        for (i in 1..10) {
            val result = filter.filterHeelAngle(40f)
            if (result != null) {
                lastResult = result
            }
        }
        
        // After enough samples, should be close to constant value
        assertNotNull(lastResult)
        assertEquals(40f, lastResult!!, 0.5f,
            "Heel angle filter should converge to constant values")
    }

    @Test
    fun `toe and heel filters maintain independent state`() {
        val filter = JointAngleFilter(windowSize = 5, polynomialOrder = 2)
        
        // Add different patterns to toe and heel
        val toeValues = mutableListOf<Float?>()
        val heelValues = mutableListOf<Float?>()
        
        for (i in 0..10) {
            val toeResult = filter.filterToeAngle(10f + i)  // Increasing
            val heelResult = filter.filterHeelAngle(30f - i)  // Decreasing
            
            toeValues.add(toeResult)
            heelValues.add(heelResult)
        }
        
        // After buffer fills, both should have values but they should be different
        val toeValue = toeValues.last()
        val heelValue = heelValues.last()
        
        assertNotNull(toeValue)
        assertNotNull(heelValue)
        assertNotEquals(toeValue, heelValue,
            "Toe and heel filters should maintain independent state")
    }

    @Test
    fun `all six angle types work together`() {
        val filter = JointAngleFilter(windowSize = 5, polynomialOrder = 2)
        
        // Fill buffers for all six angle types
        for (i in 1..5) {
            filter.filterKneeAngle(150f + i)
            filter.filterHipAngle(50f + i)
            filter.filterAnkleAngle(20f + i)
            filter.filterTorsoAngle(45f + i)
            filter.filterToeAngle(10f + i)
            filter.filterHeelAngle(15f + i)
        }
        
        // All should return non-null values
        assertNotNull(filter.filterKneeAngle(156f))
        assertNotNull(filter.filterHipAngle(56f))
        assertNotNull(filter.filterAnkleAngle(26f))
        assertNotNull(filter.filterTorsoAngle(51f))
        assertNotNull(filter.filterToeAngle(16f))
        assertNotNull(filter.filterHeelAngle(21f))
    }

    /**
     * Calculates variance of a list of values.
     */
    private fun calculateVariance(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        
        val mean = values.average().toFloat()
        val sumSquaredDiff = values.sumOf { (it - mean) * (it - mean).toDouble() }
        return (sumSquaredDiff / values.size).toFloat()
    }
}
