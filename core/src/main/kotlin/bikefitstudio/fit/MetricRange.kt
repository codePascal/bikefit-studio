package bikefitstudio.fit

/**
 * Types of bike fit metrics that can have discipline-specific ranges.
 */
enum class FitMetricType {
    /**
     * Knee angle at bottom dead center (BDC) in degrees.
     * Typical range: 140-160°
     */
    KNEE_ANGLE_AT_BDC,

    /**
     * Hip angle at top dead center (TDC) in degrees.
     * Indicates how closed the hip becomes at the top of pedal stroke.
     * Typical range: 40-70°
     */
    HIP_ANGLE_AT_TDC,

    /**
     * Torso angle relative to horizontal in degrees.
     * Lower angle = more aerodynamic but less comfortable.
     * Typical range: 25-50°
     */
    TORSO_ANGLE,

    /**
     * Knee Over Pedal Spindle (KOPS) offset in normalized coordinates.
     * Positive = knee ahead of pedal, negative = knee behind pedal.
     * Typical range: -0.02 to 0.02 (roughly neutral)
     */
    KOPS_OFFSET,

    /**
     * Ankle flexion angle at BDC in degrees.
     * Indicates heel drop vs toe drop at bottom of pedal stroke.
     * Typical range: 80-110°
     */
    ANKLE_ANGLE_AT_BDC;

    /**
     * Returns a user-friendly display name.
     */
    fun displayName(): String = when (this) {
        KNEE_ANGLE_AT_BDC -> "Knee Angle at BDC"
        HIP_ANGLE_AT_TDC -> "Hip Angle at TDC"
        TORSO_ANGLE -> "Torso Angle"
        KOPS_OFFSET -> "KOPS Offset"
        ANKLE_ANGLE_AT_BDC -> "Ankle Angle at BDC"
    }

    /**
     * Returns the unit of measurement.
     */
    fun unit(): String = when (this) {
        KNEE_ANGLE_AT_BDC, HIP_ANGLE_AT_TDC, TORSO_ANGLE, ANKLE_ANGLE_AT_BDC -> "degrees"
        KOPS_OFFSET -> "normalized"
    }
}

/**
 * Represents a typical range for a specific fit metric.
 * 
 * This is NOT a prescriptive recommendation, but rather a reference
 * range showing what is commonly seen in a particular discipline.
 * 
 * @param metricType The type of metric this range applies to
 * @param discipline The cycling discipline
 * @param typicalMin Lower bound of the typical range
 * @param typicalMax Upper bound of the typical range
 * @param description Brief explanation of this range for the discipline
 */
data class MetricRange(
    val metricType: FitMetricType,
    val discipline: CyclingDiscipline,
    val typicalMin: Float,
    val typicalMax: Float,
    val description: String = ""
) {
    /**
     * The typical range as a ClosedFloatingPointRange.
     */
    val range: ClosedFloatingPointRange<Float>
        get() = typicalMin..typicalMax

    /**
     * Returns the midpoint of the typical range.
     */
    fun midpoint(): Float = (typicalMin + typicalMax) / 2f

    /**
     * Returns the width of the typical range.
     */
    fun width(): Float = typicalMax - typicalMin

    /**
     * Checks if a value falls within the typical range.
     */
    fun contains(value: Float): Boolean = value in range

    /**
     * Returns true if the value is below the typical range.
     */
    fun isBelowRange(value: Float): Boolean = value < typicalMin

    /**
     * Returns true if the value is above the typical range.
     */
    fun isAboveRange(value: Float): Boolean = value > typicalMax

    /**
     * Calculates how far the value is from the typical range.
     * Returns 0 if within range, negative if below, positive if above.
     */
    fun deviationFromRange(value: Float): Float = when {
        value < typicalMin -> value - typicalMin
        value > typicalMax -> value - typicalMax
        else -> 0f
    }
}
