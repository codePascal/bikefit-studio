package bikefitstudio.biomechanics

/**
 * Savitzky-Golay smoothing filter for time series data.
 * 
 * This filter performs polynomial smoothing using a sliding window approach,
 * preserving the shape of extrema while reducing noise. It uses a centered
 * window to ensure zero phase shift.
 * 
 * Key features:
 * - Preserves extrema shape (peaks and valleys)
 * - Zero phase shift (centered window)
 * - Supports real-time streaming with buffer
 * 
 * Usage:
 * ```
 * val filter = SavitzkyGolayFilter(windowSize = 11, polynomialOrder = 3)
 * 
 * // Add samples one at a time
 * val smoothed1 = filter.addSample(value1) // Returns null until buffer is half-full
 * val smoothed2 = filter.addSample(value2)
 * // ...
 * val smoothedN = filter.addSample(valueN) // Returns smoothed value once buffer ready
 * ```
 * 
 * @param windowSize Size of the smoothing window (must be odd and >= 3)
 * @param polynomialOrder Order of the polynomial fit (must be < windowSize)
 * 
 * @throws IllegalArgumentException if windowSize is not odd, < 3, or polynomialOrder >= windowSize
 */
class SavitzkyGolayFilter(
    private val windowSize: Int,
    private val polynomialOrder: Int
) {
    init {
        require(windowSize >= 3) { "Window size must be at least 3, got $windowSize" }
        require(windowSize % 2 == 1) { "Window size must be odd, got $windowSize" }
        require(polynomialOrder < windowSize) { 
            "Polynomial order ($polynomialOrder) must be less than window size ($windowSize)" 
        }
        require(polynomialOrder >= 0) { "Polynomial order must be non-negative, got $polynomialOrder" }
    }

    /**
     * Circular buffer to store recent samples.
     */
    private val buffer = DoubleArray(windowSize)
    
    /**
     * Current position in the circular buffer.
     */
    private var bufferPosition = 0
    
    /**
     * Number of samples added so far (saturates at windowSize).
     */
    private var sampleCount = 0
    
    /**
     * Pre-computed Savitzky-Golay coefficients for the center point.
     * Computed lazily on first use.
     */
    private val coefficients: DoubleArray by lazy {
        computeSavitzkyGolayCoefficients()
    }

    /**
     * Adds a new sample to the filter and returns the smoothed value at the center of the window.
     * 
     * The filter uses a centered window, so it needs at least windowSize/2 samples before
     * it can produce output. Returns null until the buffer has enough samples.
     * 
     * @param value The new sample value
     * @return The smoothed value at the center of the current window, or null if buffer not ready
     */
    fun addSample(value: Double): Double? {
        // Add sample to circular buffer
        buffer[bufferPosition] = value
        bufferPosition = (bufferPosition + 1) % windowSize
        sampleCount = minOf(sampleCount + 1, windowSize)
        
        // Need full window for centered smoothing
        if (sampleCount < windowSize) {
            return null
        }
        
        // Apply Savitzky-Golay coefficients
        var smoothedValue = 0.0
        for (i in 0 until windowSize) {
            // Read from buffer in correct order (oldest to newest)
            val bufferIndex = (bufferPosition + i) % windowSize
            smoothedValue += coefficients[i] * buffer[bufferIndex]
        }
        
        return smoothedValue
    }

    /**
     * Resets the filter state, clearing the buffer.
     */
    fun reset() {
        bufferPosition = 0
        sampleCount = 0
        buffer.fill(0.0)
    }

    /**
     * Computes Savitzky-Golay coefficients for smoothing the center point.
     * 
     * This implementation uses a direct matrix approach based on least-squares
     * polynomial fitting. The coefficients are computed for the center position
     * of the window (zero phase shift).
     * 
     * References:
     * - Savitzky, A.; Golay, M.J.E. (1964). "Smoothing and Differentiation of Data 
     *   by Simplified Least Squares Procedures". Analytical Chemistry. 36 (8): 1627–1639.
     * - Press, W.H., et al. (1992). Numerical Recipes in C. Cambridge University Press.
     * 
     * @return Array of coefficients to apply to the window
     */
    private fun computeSavitzkyGolayCoefficients(): DoubleArray {
        val m = (windowSize - 1) / 2  // Half-width of the window
        val n = polynomialOrder
        
        // Build the design matrix A for polynomial fitting
        // A[i][j] = i^j where i ranges from -m to +m
        val a = Array(windowSize) { DoubleArray(n + 1) }
        for (i in 0 until windowSize) {
            val x = i - m  // Center the window at 0
            var xPower = 1.0
            for (j in 0..n) {
                a[i][j] = xPower
                xPower *= x
            }
        }
        
        // Compute (A^T * A)
        val ata = Array(n + 1) { DoubleArray(n + 1) }
        for (i in 0..n) {
            for (j in 0..n) {
                var sum = 0.0
                for (k in 0 until windowSize) {
                    sum += a[k][i] * a[k][j]
                }
                ata[i][j] = sum
            }
        }
        
        // Compute inverse of (A^T * A) using Gaussian elimination
        val ataInv = invertMatrix(ata)
        
        // Compute coefficients: c = A * (A^T * A)^-1 * e_0
        // where e_0 = [1, 0, 0, ..., 0] (we want the 0th derivative, i.e., smoothed value)
        val coeffs = DoubleArray(windowSize)
        for (i in 0 until windowSize) {
            var sum = 0.0
            for (j in 0..n) {
                // e_0 is [1, 0, 0, ...], so only j=0 contributes
                sum += a[i][j] * ataInv[j][0]
            }
            coeffs[i] = sum
        }
        
        return coeffs
    }

    /**
     * Inverts a square matrix using Gaussian elimination with partial pivoting.
     * 
     * @param matrix The matrix to invert (will not be modified)
     * @return The inverse matrix
     * @throws IllegalArgumentException if the matrix is singular
     */
    private fun invertMatrix(matrix: Array<DoubleArray>): Array<DoubleArray> {
        val n = matrix.size
        
        // Create augmented matrix [A | I]
        val aug = Array(n) { i ->
            DoubleArray(2 * n) { j ->
                if (j < n) matrix[i][j] else if (j == i + n) 1.0 else 0.0
            }
        }
        
        // Forward elimination with partial pivoting
        for (col in 0 until n) {
            // Find pivot
            var maxRow = col
            var maxVal = kotlin.math.abs(aug[col][col])
            for (row in col + 1 until n) {
                val absVal = kotlin.math.abs(aug[row][col])
                if (absVal > maxVal) {
                    maxRow = row
                    maxVal = absVal
                }
            }
            
            require(maxVal > 1e-10) { "Matrix is singular or nearly singular" }
            
            // Swap rows if needed
            if (maxRow != col) {
                val temp = aug[col]
                aug[col] = aug[maxRow]
                aug[maxRow] = temp
            }
            
            // Eliminate column
            for (row in col + 1 until n) {
                val factor = aug[row][col] / aug[col][col]
                for (j in col until 2 * n) {
                    aug[row][j] -= factor * aug[col][j]
                }
            }
        }
        
        // Back substitution
        for (col in n - 1 downTo 0) {
            // Normalize row
            val pivot = aug[col][col]
            for (j in col until 2 * n) {
                aug[col][j] /= pivot
            }
            
            // Eliminate column above
            for (row in 0 until col) {
                val factor = aug[row][col]
                for (j in col until 2 * n) {
                    aug[row][j] -= factor * aug[col][j]
                }
            }
        }
        
        // Extract inverse from right half of augmented matrix
        return Array(n) { i ->
            DoubleArray(n) { j ->
                aug[i][j + n]
            }
        }
    }
}
