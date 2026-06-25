package bikefitstudio.fit

/**
 * Static lookup for discipline-specific typical ranges for bike fit metrics.
 * 
 * This class provides non-prescriptive reference ranges showing what is
 * commonly observed in different cycling disciplines. These are NOT
 * personalized recommendations but rather informational ranges to help
 * understand how different disciplines typically differ in their fit.
 * 
 * All ranges are based on industry standards and common bike fit practices.
 * Individual riders may fall outside these ranges and still have an optimal fit.
 * 
 * Usage:
 * ```
 * val kneeRange = RangeLookup.getRange(FitMetricType.KNEE_ANGLE_AT_BDC, CyclingDiscipline.ROAD)
 * println("Road cycling typical knee angle: ${kneeRange.typicalMin}-${kneeRange.typicalMax}°")
 * 
 * val isTypical = kneeRange.contains(150f)
 * ```
 */
object RangeLookup {
    
    /**
     * All defined metric ranges organized by discipline and metric type.
     */
    private val ranges: Map<CyclingDiscipline, Map<FitMetricType, MetricRange>> = buildRanges()

    /**
     * Gets the typical range for a specific metric and discipline.
     * 
     * @param metricType The type of fit metric
     * @param discipline The cycling discipline
     * @return MetricRange if defined, null if no range exists for this combination
     */
    fun getRange(metricType: FitMetricType, discipline: CyclingDiscipline): MetricRange? {
        return ranges[discipline]?.get(metricType)
    }

    /**
     * Gets all defined ranges for a specific discipline.
     * 
     * @param discipline The cycling discipline
     * @return Map of metric types to their ranges
     */
    fun getRangesForDiscipline(discipline: CyclingDiscipline): Map<FitMetricType, MetricRange> {
        return ranges[discipline] ?: emptyMap()
    }

    /**
     * Gets all defined ranges for a specific metric type across all disciplines.
     * 
     * @param metricType The type of fit metric
     * @return Map of disciplines to their ranges for this metric
     */
    fun getRangesForMetric(metricType: FitMetricType): Map<CyclingDiscipline, MetricRange> {
        return CyclingDiscipline.values().mapNotNull { discipline ->
            getRange(metricType, discipline)?.let { discipline to it }
        }.toMap()
    }

    /**
     * Checks if a range is defined for the given metric and discipline.
     */
    fun hasRange(metricType: FitMetricType, discipline: CyclingDiscipline): Boolean {
        return getRange(metricType, discipline) != null
    }

    /**
     * Gets all disciplines that have a defined range for the given metric.
     */
    fun getDisciplinesWithMetric(metricType: FitMetricType): List<CyclingDiscipline> {
        return CyclingDiscipline.values().filter { discipline ->
            hasRange(metricType, discipline)
        }
    }

    /**
     * Builds the complete range lookup table.
     */
    private fun buildRanges(): Map<CyclingDiscipline, Map<FitMetricType, MetricRange>> {
        return mapOf(
            CyclingDiscipline.ROAD to buildRoadRanges(),
            CyclingDiscipline.ENDURANCE to buildEnduranceRanges(),
            CyclingDiscipline.GRAVEL to buildGravelRanges(),
            CyclingDiscipline.TT to buildTTRanges(),
            CyclingDiscipline.TRI to buildTriRanges()
        )
    }

    /**
     * Road cycling ranges - balanced for performance and comfort.
     */
    private fun buildRoadRanges(): Map<FitMetricType, MetricRange> {
        return mapOf(
            FitMetricType.KNEE_ANGLE_AT_BDC to MetricRange(
                metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
                discipline = CyclingDiscipline.ROAD,
                typicalMin = 145f,
                typicalMax = 155f,
                description = "Standard range for road racing - balances power and efficiency"
            ),
            FitMetricType.HIP_ANGLE_AT_TDC to MetricRange(
                metricType = FitMetricType.HIP_ANGLE_AT_TDC,
                discipline = CyclingDiscipline.ROAD,
                typicalMin = 45f,
                typicalMax = 65f,
                description = "Moderate hip closure for balanced power and aerodynamics"
            ),
            FitMetricType.TORSO_ANGLE to MetricRange(
                metricType = FitMetricType.TORSO_ANGLE,
                discipline = CyclingDiscipline.ROAD,
                typicalMin = 30f,
                typicalMax = 45f,
                description = "Moderately aggressive position for road racing"
            ),
            FitMetricType.KOPS_OFFSET to MetricRange(
                metricType = FitMetricType.KOPS_OFFSET,
                discipline = CyclingDiscipline.ROAD,
                typicalMin = -0.015f,
                typicalMax = 0.015f,
                description = "Near-neutral KOPS for road racing"
            ),
            FitMetricType.ANKLE_ANGLE_AT_BDC to MetricRange(
                metricType = FitMetricType.ANKLE_ANGLE_AT_BDC,
                discipline = CyclingDiscipline.ROAD,
                typicalMin = 85f,
                typicalMax = 105f,
                description = "Slight heel drop at BDC for efficient power transfer"
            )
        )
    }

    /**
     * Endurance/Gran Fondo ranges - prioritizes comfort for long rides.
     */
    private fun buildEnduranceRanges(): Map<FitMetricType, MetricRange> {
        return mapOf(
            FitMetricType.KNEE_ANGLE_AT_BDC to MetricRange(
                metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
                discipline = CyclingDiscipline.ENDURANCE,
                typicalMin = 145f,
                typicalMax = 155f,
                description = "Same as road - knee angle optimal range is consistent"
            ),
            FitMetricType.HIP_ANGLE_AT_TDC to MetricRange(
                metricType = FitMetricType.HIP_ANGLE_AT_TDC,
                discipline = CyclingDiscipline.ENDURANCE,
                typicalMin = 50f,
                typicalMax = 70f,
                description = "Slightly more open hip angle for comfort on long rides"
            ),
            FitMetricType.TORSO_ANGLE to MetricRange(
                metricType = FitMetricType.TORSO_ANGLE,
                discipline = CyclingDiscipline.ENDURANCE,
                typicalMin = 35f,
                typicalMax = 50f,
                description = "More upright position for all-day comfort"
            ),
            FitMetricType.KOPS_OFFSET to MetricRange(
                metricType = FitMetricType.KOPS_OFFSET,
                discipline = CyclingDiscipline.ENDURANCE,
                typicalMin = -0.015f,
                typicalMax = 0.015f,
                description = "Near-neutral KOPS similar to road"
            ),
            FitMetricType.ANKLE_ANGLE_AT_BDC to MetricRange(
                metricType = FitMetricType.ANKLE_ANGLE_AT_BDC,
                discipline = CyclingDiscipline.ENDURANCE,
                typicalMin = 85f,
                typicalMax = 105f,
                description = "Standard ankle position for comfort and efficiency"
            )
        )
    }

    /**
     * Gravel/Adventure ranges - stability and control for rough terrain.
     */
    private fun buildGravelRanges(): Map<FitMetricType, MetricRange> {
        return mapOf(
            FitMetricType.KNEE_ANGLE_AT_BDC to MetricRange(
                metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
                discipline = CyclingDiscipline.GRAVEL,
                typicalMin = 145f,
                typicalMax = 155f,
                description = "Consistent knee angle range across disciplines"
            ),
            FitMetricType.HIP_ANGLE_AT_TDC to MetricRange(
                metricType = FitMetricType.HIP_ANGLE_AT_TDC,
                discipline = CyclingDiscipline.GRAVEL,
                typicalMin = 50f,
                typicalMax = 70f,
                description = "Open hip angle for stability and control"
            ),
            FitMetricType.TORSO_ANGLE to MetricRange(
                metricType = FitMetricType.TORSO_ANGLE,
                discipline = CyclingDiscipline.GRAVEL,
                typicalMin = 38f,
                typicalMax = 52f,
                description = "Upright position for handling and visibility on rough terrain"
            ),
            FitMetricType.KOPS_OFFSET to MetricRange(
                metricType = FitMetricType.KOPS_OFFSET,
                discipline = CyclingDiscipline.GRAVEL,
                typicalMin = -0.020f,
                typicalMax = 0.010f,
                description = "Slightly rearward for traction and control on climbs"
            ),
            FitMetricType.ANKLE_ANGLE_AT_BDC to MetricRange(
                metricType = FitMetricType.ANKLE_ANGLE_AT_BDC,
                discipline = CyclingDiscipline.GRAVEL,
                typicalMin = 85f,
                typicalMax = 105f,
                description = "Standard ankle position"
            )
        )
    }

    /**
     * Time Trial ranges - aggressive aerodynamic position.
     */
    private fun buildTTRanges(): Map<FitMetricType, MetricRange> {
        return mapOf(
            FitMetricType.KNEE_ANGLE_AT_BDC to MetricRange(
                metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
                discipline = CyclingDiscipline.TT,
                typicalMin = 145f,
                typicalMax = 155f,
                description = "Standard knee angle maintained even in aggressive position"
            ),
            FitMetricType.HIP_ANGLE_AT_TDC to MetricRange(
                metricType = FitMetricType.HIP_ANGLE_AT_TDC,
                discipline = CyclingDiscipline.TT,
                typicalMin = 40f,
                typicalMax = 60f,
                description = "More closed hip angle due to aggressive forward position"
            ),
            FitMetricType.TORSO_ANGLE to MetricRange(
                metricType = FitMetricType.TORSO_ANGLE,
                discipline = CyclingDiscipline.TT,
                typicalMin = 15f,
                typicalMax = 30f,
                description = "Very low torso angle for maximum aerodynamics"
            ),
            FitMetricType.KOPS_OFFSET to MetricRange(
                metricType = FitMetricType.KOPS_OFFSET,
                discipline = CyclingDiscipline.TT,
                typicalMin = 0.020f,
                typicalMax = 0.050f,
                description = "Forward saddle position for power in aggressive position"
            ),
            FitMetricType.ANKLE_ANGLE_AT_BDC to MetricRange(
                metricType = FitMetricType.ANKLE_ANGLE_AT_BDC,
                discipline = CyclingDiscipline.TT,
                typicalMin = 80f,
                typicalMax = 100f,
                description = "Slightly more heel drop for power in TT position"
            )
        )
    }

    /**
     * Triathlon ranges - forward position to preserve running muscles.
     */
    private fun buildTriRanges(): Map<FitMetricType, MetricRange> {
        return mapOf(
            FitMetricType.KNEE_ANGLE_AT_BDC to MetricRange(
                metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
                discipline = CyclingDiscipline.TRI,
                typicalMin = 145f,
                typicalMax = 155f,
                description = "Standard knee angle range"
            ),
            FitMetricType.HIP_ANGLE_AT_TDC to MetricRange(
                metricType = FitMetricType.HIP_ANGLE_AT_TDC,
                discipline = CyclingDiscipline.TRI,
                typicalMin = 45f,
                typicalMax = 65f,
                description = "More open hip angle than TT to preserve running muscles"
            ),
            FitMetricType.TORSO_ANGLE to MetricRange(
                metricType = FitMetricType.TORSO_ANGLE,
                discipline = CyclingDiscipline.TRI,
                typicalMin = 20f,
                typicalMax = 35f,
                description = "Low but not as extreme as TT, to maintain running form"
            ),
            FitMetricType.KOPS_OFFSET to MetricRange(
                metricType = FitMetricType.KOPS_OFFSET,
                discipline = CyclingDiscipline.TRI,
                typicalMin = 0.025f,
                typicalMax = 0.055f,
                description = "Forward position to open hip angle and reduce hip flexor load"
            ),
            FitMetricType.ANKLE_ANGLE_AT_BDC to MetricRange(
                metricType = FitMetricType.ANKLE_ANGLE_AT_BDC,
                discipline = CyclingDiscipline.TRI,
                typicalMin = 80f,
                typicalMax = 100f,
                description = "Heel drop for efficient power transfer"
            )
        )
    }
}
