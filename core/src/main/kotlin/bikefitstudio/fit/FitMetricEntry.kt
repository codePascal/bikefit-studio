package bikefitstudio.fit

/**
 * Status of a metric value relative to its typical range.
 * 
 * Categorizes whether a measured value falls within, near, or
 * outside the discipline-specific typical range for that metric.
 */
enum class MetricStatus {
    /**
     * Value falls within the typical range.
     * No adjustment is likely needed.
     */
    IN_RANGE,

    /**
     * Value is near the edge of the typical range.
     * May warrant attention or minor adjustment.
     */
    BORDERLINE,

    /**
     * Value falls outside the typical range.
     * Adjustment is recommended.
     */
    OUT_OF_RANGE;

    /**
     * Returns a user-friendly display text.
     */
    fun displayText(): String = when (this) {
        IN_RANGE -> "In Range"
        BORDERLINE -> "Borderline"
        OUT_OF_RANGE -> "Out of Range"
    }

    /**
     * Returns an emoji indicator for the status.
     */
    fun emoji(): String = when (this) {
        IN_RANGE -> "✅"
        BORDERLINE -> "⚠️"
        OUT_OF_RANGE -> "❌"
    }
}

/**
 * Represents a single entry in the metrics vs typical ranges table.
 * 
 * This data model combines a measured metric value with its
 * discipline-specific typical range and calculates the status.
 * 
 * This is a non-prescriptive informational display that helps
 * riders understand how their measurements compare to common
 * ranges for their discipline.
 * 
 * @param metricType The type of fit metric
 * @param measuredValue The actual measured value for this metric
 * @param typicalRange The discipline-specific typical range (optional)
 * @param discipline The cycling discipline (optional)
 * 
 * Usage:
 * ```
 * val kneeRange = RangeLookup.getRange(FitMetricType.KNEE_ANGLE_AT_BDC, CyclingDiscipline.ROAD)
 * val entry = FitMetricEntry(
 *     metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
 *     measuredValue = 150f,
 *     typicalRange = kneeRange,
 *     discipline = CyclingDiscipline.ROAD
 * )
 * 
 * println("${entry.metricType.displayName()}: ${entry.measuredValue} ${entry.unit()}")
 * println("Status: ${entry.status.displayText()}")
 * println("Typical range: ${entry.rangeText()}")
 * ```
 */
data class FitMetricEntry(
    val metricType: FitMetricType,
    val measuredValue: Float,
    val typicalRange: MetricRange? = null,
    val discipline: CyclingDiscipline? = null
) {
    /**
     * The status of this metric relative to its typical range.
     * 
     * Calculated based on the measured value and typical range:
     * - IN_RANGE: value is within the typical range
     * - BORDERLINE: value is close to the range boundaries (within 10% of range width)
     * - OUT_OF_RANGE: value is outside the typical range
     * 
     * Returns null if no typical range is available.
     */
    val status: MetricStatus?
        get() = calculateStatus()

    /**
     * Calculates the metric status based on measured value and typical range.
     */
    private fun calculateStatus(): MetricStatus? {
        val range = typicalRange ?: return null

        // Check if value is within range
        if (range.contains(measuredValue)) {
            // Check if it's borderline (within 10% of range width from either edge)
            val borderlineThreshold = range.width() * 0.1f
            val distanceFromMin = measuredValue - range.typicalMin
            val distanceFromMax = range.typicalMax - measuredValue

            return if (distanceFromMin <= borderlineThreshold || distanceFromMax <= borderlineThreshold) {
                MetricStatus.BORDERLINE
            } else {
                MetricStatus.IN_RANGE
            }
        }

        // Value is outside the range
        return MetricStatus.OUT_OF_RANGE
    }

    /**
     * Returns the unit of measurement for this metric.
     */
    fun unit(): String = metricType.unit()

    /**
     * Returns a formatted string representation of the typical range.
     * 
     * Example: "145.0 - 155.0 degrees"
     * Returns "N/A" if no typical range is available.
     */
    fun rangeText(): String {
        val range = typicalRange ?: return "N/A"
        return "${range.typicalMin} - ${range.typicalMax} ${unit()}"
    }

    /**
     * Returns the deviation from the typical range.
     * 
     * - Negative: value is below the range
     * - Zero: value is within the range
     * - Positive: value is above the range
     * 
     * Returns null if no typical range is available.
     */
    fun deviation(): Float? {
        return typicalRange?.deviationFromRange(measuredValue)
    }

    /**
     * Returns true if a typical range is available for this metric.
     */
    fun hasTypicalRange(): Boolean = typicalRange != null

    /**
     * Returns a formatted string showing the measured value with unit.
     * 
     * Example: "150.0 degrees"
     */
    fun valueText(): String = "$measuredValue ${unit()}"

    companion object {
        /**
         * Creates a FitMetricEntry by looking up the typical range for the given
         * metric type and discipline.
         * 
         * @param metricType The type of fit metric
         * @param measuredValue The measured value
         * @param discipline The cycling discipline
         * @return FitMetricEntry with typical range from RangeLookup
         */
        fun create(
            metricType: FitMetricType,
            measuredValue: Float,
            discipline: CyclingDiscipline
        ): FitMetricEntry {
            val typicalRange = RangeLookup.getRange(metricType, discipline)
            return FitMetricEntry(
                metricType = metricType,
                measuredValue = measuredValue,
                typicalRange = typicalRange,
                discipline = discipline
            )
        }

        /**
         * Creates multiple FitMetricEntry instances from measured values.
         * 
         * @param measuredValues Map of metric types to their measured values
         * @param discipline The cycling discipline
         * @return List of FitMetricEntry instances
         */
        fun createAll(
            measuredValues: Map<FitMetricType, Float>,
            discipline: CyclingDiscipline
        ): List<FitMetricEntry> {
            return measuredValues.map { (metricType, value) ->
                create(metricType, value, discipline)
            }
        }
    }
}
