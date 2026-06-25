package bikefitstudio.biomechanics

/**
 * Internal data structure for tracking measurements during a cycle.
 */
internal data class CycleMeasurements(
    val kneeAngles: MutableList<Float> = mutableListOf(),
    val hipAngles: MutableList<Float> = mutableListOf(),
    val torsoAngles: MutableList<Float> = mutableListOf(),
    val ankleAngles: MutableList<Float> = mutableListOf(),
    val kopsValues: MutableList<Float> = mutableListOf(),
    var kneeAngleAtBdc: Float? = null,
    var kneeAngleAtTdc: Float? = null,
    var hipAngleAtTdc: Float? = null,
    var ankleAngleAtBdc: Float? = null,
    var kopsAt3oClock: Float? = null,
    var startFrameNumber: Long = 0,
    var endFrameNumber: Long = 0,
    var startTimestampMs: Long = 0,
    var endTimestampMs: Long = 0
)

/**
 * Aggregates biomechanical measurements per pedal cycle.
 * 
 * This class collects angle measurements during pedaling and groups them
 * into complete cycles based on pedal extrema (BDC/TDC). It calculates
 * average, min, max statistics for each cycle to produce stable metrics
 * for the fit engine.
 * 
 * Usage:
 * ```
 * val aggregator = CycleAggregator(BodySide.LEFT)
 * 
 * // As each frame is processed:
 * aggregator.addMeasurement(
 *     frameNumber = frame.frameNumber,
 *     timestampMs = frame.timestampMs,
 *     kneeAngle = kneeResult.angle,
 *     hipAngle = hipResult.angle,
 *     torsoAngle = torsoResult.angle
 * )
 * 
 * // When a pedal extremum is detected:
 * if (extremum.type == PedalExtremum.BDC) {
 *     val completedCycle = aggregator.endCycleAtBdc(
 *         frameNumber = extremum.frameNumber,
 *         timestampMs = extremum.timestampMs,
 *         kneeAngleAtBdc = currentKneeAngle
 *     )
 * }
 * 
 * // Get final summary:
 * val summary = aggregator.getSummary()
 * ```
 * 
 * @param side Which body side to track
 */
class CycleAggregator(
    private val side: BodySide
) {
    private val completedCycles = mutableListOf<CycleMetrics>()
    private var currentMeasurements = CycleMeasurements()
    private var cycleNumber = 0
    private var cycleStarted = false

    /**
     * Adds a measurement to the current cycle.
     * 
     * @param frameNumber Frame number for this measurement
     * @param timestampMs Timestamp in milliseconds
     * @param kneeAngle Current knee angle (may be null if not visible)
     * @param hipAngle Current hip angle (may be null if not visible)
     * @param torsoAngle Current torso angle (may be null if not visible)
     * @param ankleAngle Current ankle angle (may be null if not visible)
     * @param kopsNormalized Current KOPS normalized value (may be null if not visible)
     */
    fun addMeasurement(
        frameNumber: Long,
        timestampMs: Long,
        kneeAngle: Float? = null,
        hipAngle: Float? = null,
        torsoAngle: Float? = null,
        ankleAngle: Float? = null,
        kopsNormalized: Float? = null
    ) {
        // Initialize cycle start if this is the first measurement
        if (!cycleStarted) {
            currentMeasurements.startFrameNumber = frameNumber
            currentMeasurements.startTimestampMs = timestampMs
            cycleStarted = true
        }

        // Add valid measurements
        kneeAngle?.let { currentMeasurements.kneeAngles.add(it) }
        hipAngle?.let { currentMeasurements.hipAngles.add(it) }
        torsoAngle?.let { currentMeasurements.torsoAngles.add(it) }
        ankleAngle?.let { currentMeasurements.ankleAngles.add(it) }
        kopsNormalized?.let { currentMeasurements.kopsValues.add(it) }

        // Update end frame/timestamp
        currentMeasurements.endFrameNumber = frameNumber
        currentMeasurements.endTimestampMs = timestampMs
    }

    /**
     * Marks bottom dead center and optionally ends the current cycle.
     * 
     * If this is not the first BDC, it completes the current cycle and
     * starts a new one.
     * 
     * @param frameNumber Frame number at BDC
     * @param timestampMs Timestamp at BDC
     * @param kneeAngle Knee angle at BDC
     * @param ankleAngle Ankle angle at BDC
     * @return Completed CycleMetrics, or null if this is the first BDC
     */
    fun endCycleAtBdc(
        frameNumber: Long,
        timestampMs: Long,
        kneeAngle: Float?,
        ankleAngle: Float? = null
    ): CycleMetrics? {
        // Record angles at BDC for current cycle
        currentMeasurements.kneeAngleAtBdc = kneeAngle
        currentMeasurements.ankleAngleAtBdc = ankleAngle

        // If we have accumulated measurements, complete the cycle
        val completed = if (cycleStarted && hasMeasurements()) {
            finalizeCycle(frameNumber, timestampMs)
        } else {
            null
        }

        // Start new cycle
        startNewCycle(frameNumber, timestampMs)

        return completed
    }

    /**
     * Records top dead center in the current cycle.
     * 
     * @param kneeAngle Knee angle at TDC
     * @param hipAngle Hip angle at TDC (minimum hip angle during cycle)
     */
    fun recordTdc(kneeAngle: Float?, hipAngle: Float? = null) {
        currentMeasurements.kneeAngleAtTdc = kneeAngle
        currentMeasurements.hipAngleAtTdc = hipAngle
    }

    /**
     * Records KOPS (Knee Over Pedal) measurement at 3 o'clock position.
     * 
     * @param kopsNormalized Normalized KOPS value (offset / femur length)
     */
    fun recordKopsAt3oClock(kopsNormalized: Float?) {
        kopsNormalized?.let { 
            currentMeasurements.kopsValues.add(it)
            currentMeasurements.kopsAt3oClock = it 
        }
    }

    /**
     * Ends the current cycle at TDC.
     * 
     * @param frameNumber Frame number at TDC
     * @param timestampMs Timestamp at TDC
     * @param kneeAngle Knee angle at TDC
     * @return Completed CycleMetrics, or null if no valid data
     */
    fun endCycleAtTdc(
        frameNumber: Long,
        timestampMs: Long,
        kneeAngle: Float?
    ): CycleMetrics? {
        currentMeasurements.kneeAngleAtTdc = kneeAngle

        val completed = if (cycleStarted && hasMeasurements()) {
            finalizeCycle(frameNumber, timestampMs)
        } else {
            null
        }

        startNewCycle(frameNumber, timestampMs)

        return completed
    }

    /**
     * Gets all completed cycles.
     */
    fun getCompletedCycles(): List<CycleMetrics> = completedCycles.toList()

    /**
     * Gets the number of completed cycles.
     */
    fun getCycleCount(): Int = completedCycles.size

    /**
     * Gets a summary of all completed cycles with outlier filtering applied.
     * 
     * @param applyOutlierFiltering Whether to filter outliers (default: true)
     */
    fun getSummary(applyOutlierFiltering: Boolean = true): CycleSummary {
        return aggregateCycles(completedCycles, side, applyOutlierFiltering)
    }

    /**
     * Gets the most recent completed cycle.
     */
    fun getLastCycle(): CycleMetrics? = completedCycles.lastOrNull()

    /**
     * Clears all data and resets the aggregator.
     */
    fun reset() {
        completedCycles.clear()
        currentMeasurements = CycleMeasurements()
        cycleNumber = 0
        cycleStarted = false
    }

    /**
     * Returns the side being tracked.
     */
    fun getSide(): BodySide = side

    /**
     * Returns true if there are measurements in the current cycle.
     */
    private fun hasMeasurements(): Boolean {
        return currentMeasurements.kneeAngles.isNotEmpty() ||
               currentMeasurements.hipAngles.isNotEmpty() ||
               currentMeasurements.torsoAngles.isNotEmpty() ||
               currentMeasurements.ankleAngles.isNotEmpty()
    }

    /**
     * Finalizes the current cycle and adds it to completed cycles.
     */
    private fun finalizeCycle(endFrame: Long, endTimestamp: Long): CycleMetrics? {
        currentMeasurements.endFrameNumber = endFrame
        currentMeasurements.endTimestampMs = endTimestamp

        // 1. Validate Duration
        val duration = endTimestamp - currentMeasurements.startTimestampMs
        if (duration < MIN_CYCLE_DURATION_MS) {
            return null // Discard short noise
        }
        
        // If the gap is too long, it's not a continuous cycle. 
        // We reset the history because the user stopped pedaling.
        if (duration > MAX_CYCLE_DURATION_MS) {
            completedCycles.clear()
            cycleNumber = 0
            return null
        }

        val kneeStats = AngleStats.fromValues(currentMeasurements.kneeAngles)

        // 2. Validate Range of Motion (prevents accepting standing still jitters)
        if (kneeStats.range < MIN_KNEE_ROM_DEGREES) {
            return null // Discard cycle
        }

        // Compute KOPS at 3 o'clock - use max value (when knee is most forward)
        val kopsAt3oClock = if (currentMeasurements.kopsValues.isNotEmpty()) {
            currentMeasurements.kopsValues.maxOrNull()
        } else {
            null
        }

        val metrics = CycleMetrics(
            cycleNumber = cycleNumber,
            startFrameNumber = currentMeasurements.startFrameNumber,
            endFrameNumber = currentMeasurements.endFrameNumber,
            startTimestampMs = currentMeasurements.startTimestampMs,
            endTimestampMs = currentMeasurements.endTimestampMs,
            kneeAngle = kneeStats,
            hipAngle = AngleStats.fromValues(currentMeasurements.hipAngles),
            torsoAngle = AngleStats.fromValues(currentMeasurements.torsoAngles),
            ankleAngle = AngleStats.fromValues(currentMeasurements.ankleAngles),
            kneeAngleAtBdc = currentMeasurements.kneeAngleAtBdc,
            kneeAngleAtTdc = currentMeasurements.kneeAngleAtTdc,
            hipAngleAtTdc = currentMeasurements.hipAngleAtTdc,
            ankleAngleAtBdc = currentMeasurements.ankleAngleAtBdc,
            kopsNormalized = kopsAt3oClock,
            side = side
        )

        completedCycles.add(metrics)
        cycleNumber++

        return metrics
    }

    /**
     * Starts a new cycle.
     */
    private fun startNewCycle(frameNumber: Long, timestampMs: Long) {
        currentMeasurements = CycleMeasurements(
            startFrameNumber = frameNumber,
            startTimestampMs = timestampMs
        )
        cycleStarted = true
    }

    companion object {
        // Validation constants
        // 200 RPM max (300ms) - Filters noise/twitches
        private const val MIN_CYCLE_DURATION_MS = 300L
        // 10 RPM min (6000ms) - Filters standing still/pauses
        private const val MAX_CYCLE_DURATION_MS = 6000L
        // Minimum 40 degrees movement to count as a pedal stroke
        private const val MIN_KNEE_ROM_DEGREES = 40.0f
        // IQR multiplier for outlier detection
        private const val IQR_MULTIPLIER = 1.5f
        
        // Data quality constants
        private const val LOW_SAMPLE_SIZE_QUALITY = 0.3f
        private const val MISSING_BDC_DATA_PENALTY = 1f
        private const val MISSING_CADENCE_DATA_PENALTY = 0.5f
        private const val MAX_BDC_CV = 0.2f  // Typical good CV for angles: < 10%
        private const val MAX_CADENCE_CV = 0.1f  // Typical good CV for cadence: < 5%
        private const val IDEAL_SAMPLE_SIZE = 10f
        private const val BDC_QUALITY_WEIGHT = 0.4f
        private const val CADENCE_QUALITY_WEIGHT = 0.4f
        private const val SAMPLE_SIZE_WEIGHT = 0.2f

        /**
         * Filters outliers from a list of cycles using the IQR method.
         * 
         * Uses the Interquartile Range (IQR) method to identify and remove
         * cycles with extreme values in key metrics like knee angle at BDC,
         * cadence, or knee range of motion.
         * 
         * @param cycles List of cycle metrics to filter
         * @return Filtered list with outliers removed
         */
        fun filterOutliers(cycles: List<CycleMetrics>): List<CycleMetrics> {
            if (cycles.size < 4) {
                // Need at least 4 cycles to compute meaningful quartiles
                return cycles
            }

            // Extract key metrics for outlier detection
            val bdcAngles = cycles.mapNotNull { it.kneeAngleAtBdc }
            val cadences = cycles.mapNotNull { it.cadenceRpm }
            val kneeRanges = cycles.map { it.kneeAngle.range }

            // Calculate outlier bounds for each metric
            val bdcBounds = calculateOutlierBounds(bdcAngles)
            val cadenceBounds = calculateOutlierBounds(cadences)
            val rangeBounds = calculateOutlierBounds(kneeRanges)

            // Filter cycles that are outliers in any key metric
            return cycles.filter { cycle ->
                val bdcOk = cycle.kneeAngleAtBdc?.let { 
                    it >= bdcBounds.first && it <= bdcBounds.second 
                } ?: true
                
                val cadenceOk = cycle.cadenceRpm?.let {
                    it >= cadenceBounds.first && it <= cadenceBounds.second
                } ?: true
                
                val rangeOk = cycle.kneeAngle.range >= rangeBounds.first && 
                              cycle.kneeAngle.range <= rangeBounds.second

                bdcOk && cadenceOk && rangeOk
            }
        }

        /**
         * Calculates outlier bounds using IQR method.
         * Returns (lowerBound, upperBound).
         */
        private fun calculateOutlierBounds(values: List<Float>): Pair<Float, Float> {
            if (values.size < 4) {
                return Pair(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)
            }

            val sorted = values.sorted()
            val q1Index = (sorted.size * 0.25).toInt()
            val q3Index = (sorted.size * 0.75).toInt()
            
            val q1 = sorted[q1Index]
            val q3 = sorted[q3Index]
            val iqr = q3 - q1
            
            val lowerBound = q1 - (IQR_MULTIPLIER * iqr)
            val upperBound = q3 + (IQR_MULTIPLIER * iqr)
            
            return Pair(lowerBound, upperBound)
        }

        /**
         * Calculates data quality score based on consistency and sample size.
         * 
         * Quality is computed from coefficient of variation (CV) for key metrics
         * and sample size. Lower CV indicates more consistent pedaling.
         * 
         * @param cycles List of cycles to evaluate
         * @return Quality score from 0 to 1
         */
        fun calculateDataQuality(cycles: List<CycleMetrics>): Float {
            if (cycles.isEmpty()) return 0f
            if (cycles.size < 3) return LOW_SAMPLE_SIZE_QUALITY

            // Calculate coefficient of variation for key metrics
            val bdcAngles = cycles.mapNotNull { it.kneeAngleAtBdc }
            val cadences = cycles.mapNotNull { it.cadenceRpm }

            val bdcCV = if (bdcAngles.isNotEmpty()) {
                calculateCoefficientOfVariation(bdcAngles)
            } else {
                MISSING_BDC_DATA_PENALTY  // Poor if no BDC data
            }

            val cadenceCV = if (cadences.isNotEmpty()) {
                calculateCoefficientOfVariation(cadences)
            } else {
                MISSING_CADENCE_DATA_PENALTY  // Moderate if no cadence data
            }

            // Lower CV = better quality (more consistent)
            val bdcQuality = (1f - (bdcCV / MAX_BDC_CV)).coerceIn(0f, 1f)
            val cadenceQuality = (1f - (cadenceCV / MAX_CADENCE_CV)).coerceIn(0f, 1f)

            // Sample size factor: more cycles = better quality
            val sampleQuality = (cycles.size / IDEAL_SAMPLE_SIZE).coerceIn(0f, 1f)

            // Weighted average
            return (bdcQuality * BDC_QUALITY_WEIGHT + 
                    cadenceQuality * CADENCE_QUALITY_WEIGHT + 
                    sampleQuality * SAMPLE_SIZE_WEIGHT)
        }

        /**
         * Calculates coefficient of variation (std dev / mean).
         */
        private fun calculateCoefficientOfVariation(values: List<Float>): Float {
            if (values.isEmpty()) return 0f
            
            val mean = values.average().toFloat()
            if (mean == 0f) return 0f
            
            val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
            val stdDev = kotlin.math.sqrt(variance)
            
            return stdDev / kotlin.math.abs(mean)
        }

        /**
         * Aggregates a list of CycleMetrics into a CycleSummary.
         * 
         * Computes summary statistics for the four key bike fit metrics:
         * - A. Knee Flexion/Extension at BDC (hip → knee → ankle)
         * - B. Hip Angle at TDC (minimum hip angle during crank cycle)
         * - C. Torso Angle (shoulder → hip relative to horizontal)
         * - D. Ankle Angle at BDC (knee → ankle → foot index)
         * 
         * @param cycles List of cycle metrics to aggregate
         * @param side Body side for the summary
         * @param applyOutlierFiltering Whether to filter outliers before aggregation
         * @return CycleSummary with aggregated statistics
         */
        fun aggregateCycles(
            cycles: List<CycleMetrics>, 
            side: BodySide,
            applyOutlierFiltering: Boolean = true
        ): CycleSummary {
            if (cycles.isEmpty()) {
                return CycleSummary.Companion.invalid(side)
            }

            val originalCount = cycles.size
            val filteredCycles = if (applyOutlierFiltering) {
                filterOutliers(cycles)
            } else {
                cycles
            }
            val outlierCount = originalCount - filteredCycles.size

            if (filteredCycles.isEmpty()) {
                // All cycles were outliers - return invalid
                return CycleSummary.Companion.invalid(side)
            }

            // A. Knee Flexion/Extension at BDC
            // We use the maximum knee angle observed during the cycle (Max Extension) represents the
            // true biomechanical parameter relevant for saddle height, even if it occurs slightly
            // off the exact BDC frame.
            val kneeBdcAngles = filteredCycles.map { it.kneeAngle.max }
            val kneeTdcAngles = filteredCycles.mapNotNull { it.kneeAngleAtTdc }
            val kneeRanges = filteredCycles.map { it.kneeAngle.range }
            
            // B. Hip Angle at TDC (minimum hip angle during crank cycle)
            val hipTdcAngles = filteredCycles.mapNotNull { it.hipAngleAtTdc }
            
            // C. Torso Angle
            val torsoAverages = filteredCycles.filter { it.torsoAngle.isValid }.map { it.torsoAngle.average }
            
            // D. Ankle Angle at BDC
            val ankleBdcAngles = filteredCycles.mapNotNull { it.ankleAngleAtBdc }
            
            // E. Knee Over Pedal (Normalized) at 3 o'clock
            val kopsNormalizedValues = filteredCycles.mapNotNull { it.kopsNormalized }
            
            val cadences = filteredCycles.mapNotNull { it.cadenceRpm }

            val dataQuality = calculateDataQuality(filteredCycles)

            return CycleSummary(
                cycleCount = filteredCycles.size,
                averageKneeAngleAtBdc = if (kneeBdcAngles.isNotEmpty()) kneeBdcAngles.average().toFloat() else null,
                averageKneeAngleAtTdc = if (kneeTdcAngles.isNotEmpty()) kneeTdcAngles.average().toFloat() else null,
                averageKneeAngleRange = if (kneeRanges.isNotEmpty()) kneeRanges.average().toFloat() else 0f,
                averageHipAngleAtTdc = if (hipTdcAngles.isNotEmpty()) hipTdcAngles.average().toFloat() else null,
                averageTorsoAngle = if (torsoAverages.isNotEmpty()) torsoAverages.average().toFloat() else 0f,
                averageAnkleAngleAtBdc = if (ankleBdcAngles.isNotEmpty()) ankleBdcAngles.average().toFloat() else null,
                averageKopsNormalized = if (kopsNormalizedValues.isNotEmpty()) kopsNormalizedValues.average().toFloat() else null,
                averageCadenceRpm = if (cadences.isNotEmpty()) cadences.average().toFloat() else null,
                kneeAngleAtBdcStats = AngleStats.fromValues(kneeBdcAngles),
                kneeAngleAtTdcStats = AngleStats.fromValues(kneeTdcAngles),
                hipAngleAtTdcStats = AngleStats.fromValues(hipTdcAngles),
                torsoAngleStats = AngleStats.fromValues(
                    filteredCycles.filter { it.torsoAngle.isValid }.flatMap {
                        listOf(it.torsoAngle.min, it.torsoAngle.average, it.torsoAngle.max)
                    }
                ),
                ankleAngleAtBdcStats = AngleStats.fromValues(ankleBdcAngles),
                kopsNormalizedStats = AngleStats.fromValues(kopsNormalizedValues),
                side = side,
                dataQuality = dataQuality,
                outlierCount = outlierCount
            )
        }
    }
}
