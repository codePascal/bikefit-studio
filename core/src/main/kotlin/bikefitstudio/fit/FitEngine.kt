package bikefitstudio.fit

import bikefitstudio.biomechanics.CycleMetrics
import bikefitstudio.biomechanics.CycleSummary
import bikefitstudio.biomechanics.HipRockingResult
import bikefitstudio.biomechanics.TorsoAngleResult
import bikefitstudio.calibration.BikeCalibration
import bikefitstudio.pose.PoseFrame

/**
 * Configuration for the FitEngine.
 * 
 * Controls which rules are enabled and their individual configurations.
 * Supports context-aware thresholds based on riding context and fit bias.
 * 
 * @param ridingContext The riding context (Road, Endurance, Gravel, TT, Indoor)
 * @param fitBias The fit priority bias (Comfort, Neutral, Performance)
 * @param saddleHeightConfig Configuration for saddle height rule
 * @param saddleForeAftConfig Configuration for saddle fore/aft rule
 * @param reachConfig Configuration for reach rule
 * @param ankleAngleConfig Configuration for ankle angle rule
 * @param hipAngleConfig Configuration for hip angle rule
 * @param enableSaddleHeight Whether to check saddle height
 * @param enableSaddleForeAft Whether to check saddle fore/aft position
 * @param enableReach Whether to check reach
 * @param enableHipRocking Whether to check hip rocking
 * @param enableAnkleAngle Whether to check ankle angle (plantarflexion)
 * @param enableHipAngle Whether to check hip angle at TDC
 * @param minCyclesForAnalysis Minimum pedal cycles required for valid analysis
 */
data class FitEngineConfig(
    val ridingContext: RidingContext = RidingContext.DEFAULT,
    val fitBias: FitBias = FitBias.DEFAULT,
    val saddleHeightConfig: SaddleHeightConfig = SaddleHeightConfig(),
    val saddleForeAftConfig: SaddleForeAftConfig = SaddleForeAftConfig(),
    val reachConfig: ReachConfig = ReachConfig(),
    val ankleAngleConfig: AnkleAngleConfig = AnkleAngleConfig(),
    val hipAngleConfig: HipAngleConfig = HipAngleConfig(),
    val enableSaddleHeight: Boolean = true,
    val enableSaddleForeAft: Boolean = true,
    val enableReach: Boolean = true,
    val enableHipRocking: Boolean = true,
    val enableAnkleAngle: Boolean = true,
    val enableHipAngle: Boolean = true,
    val minCyclesForAnalysis: Int = MIN_CYCLES_FOR_ANALYSIS
) {
    companion object {
        /**
         * Minimum pedal cycles required for reliable analysis.
         * 
         * At least 3 complete pedal cycles are needed to establish
         * consistent patterns and filter out noise.
         */
        const val MIN_CYCLES_FOR_ANALYSIS = 3

        /**
         * Create a FitEngineConfig with context-aware thresholds.
         * 
         * This factory method computes appropriate thresholds based on:
         * 1. The riding context (Road, Endurance, Gravel, TT, Indoor)
         * 2. The fit bias (Comfort, Neutral, Performance)
         * 3. Global safety floors (never crossed regardless of settings)
         * 
         * @param context The riding context
         * @param bias The fit priority bias (defaults to NEUTRAL)
         * @return A FitEngineConfig with context-appropriate thresholds
         */
        fun forContext(
            context: RidingContext,
            bias: FitBias = FitBias.NEUTRAL
        ): FitEngineConfig {
            val thresholds = FitThresholds.forContextAndBias(context, bias)
            
            return FitEngineConfig(
                ridingContext = context,
                fitBias = bias,
                saddleHeightConfig = SaddleHeightConfig(
                    minOptimalKneeAngle = thresholds.kneeAngleMin,
                    maxOptimalKneeAngle = thresholds.kneeAngleMax,
                    tooLowKneeAngle = thresholds.kneeAngleMin - 5f,
                    tooHighKneeAngle = thresholds.kneeAngleMax + 5f
                ),
                reachConfig = ReachConfig(
                    minOptimalTorsoAngle = thresholds.torsoAngleMin,
                    maxOptimalTorsoAngle = thresholds.torsoAngleMax,
                    tooAggressiveAngle = thresholds.torsoAngleMin - 5f,
                    tooUprightAngle = thresholds.torsoAngleMax + 10f
                ),
                saddleForeAftConfig = SaddleForeAftConfig(
                    maxForwardOffset = thresholds.kopsMax,
                    maxBackwardOffset = -thresholds.kopsMin
                ),
                ankleAngleConfig = AnkleAngleConfig(
                    minOptimalAngle = thresholds.ankleAngleMin,
                    maxOptimalAngle = thresholds.ankleAngleMax
                ),
                hipAngleConfig = HipAngleConfig(
                    minOptimalAngle = thresholds.hipAngleMin,
                    maxOptimalAngle = thresholds.hipAngleMax,
                    tooClosedAngle = FitThresholds.SAFETY_HIP_MIN,
                    tooOpenAngle = thresholds.hipAngleMax + 10f
                )
            )
        }
    }
}

/**
 * Input data for fit analysis.
 * 
 * Bundles all the biomechanical data needed for a complete fit analysis.
 * 
 * @param cycleSummary Aggregated metrics across all pedal cycles
 * @param cycleMetrics Individual metrics for each pedal cycle
 * @param hipRockingResult Hip rocking analysis result (optional)
 * @param bikeCalibration Bike calibration data (optional, needed for some rules)
 * @param poseFramesAt3oClock Pose frames at 3 o'clock position (for fore/aft)
 * @param torsoAngleResults Individual torso angle measurements (optional)
 */
data class FitAnalysisInput(
    val cycleSummary: CycleSummary,
    val cycleMetrics: List<CycleMetrics> = emptyList(),
    val hipRockingResult: HipRockingResult? = null,
    val bikeCalibration: BikeCalibration? = null,
    val poseFramesAt3oClock: List<PoseFrame> = emptyList(),
    val torsoAngleResults: List<TorsoAngleResult> = emptyList()
) {
    /**
     * True if there's enough data for analysis.
     */
    fun hasMinimumData(minCycles: Int = FitEngineConfig.MIN_CYCLES_FOR_ANALYSIS): Boolean {
        return cycleSummary.cycleCount >= minCycles
    }
}

/**
 * Orchestrates all bike fit rules to produce a comprehensive analysis.
 * 
 * The FitEngine is the central component that:
 * 1. Collects biomechanical data from various sources
 * 2. Runs all enabled fit rules
 * 3. Aggregates and prioritizes detected issues
 * 4. Produces a unified FitAnalysisResult
 * 
 * Usage:
 * ```
 * val engine = FitEngine()
 * 
 * val input = FitAnalysisInput(
 *     cycleSummary = aggregator.getSummary(),
 *     hipRockingResult = hipRockingDetector.analyze(BodySide.LEFT),
 *     bikeCalibration = calibration
 * )
 * 
 * val result = engine.analyze(input)
 * 
 * for (issue in result.issues) {
 *     println("${issue.severity}: ${issue.description}")
 * }
 * ```
 * 
 * @param config Engine configuration
 */
class FitEngine(
    private val config: FitEngineConfig = FitEngineConfig()
) {
    // Individual rule instances
    private val saddleHeightRule = SaddleHeightRule(config.saddleHeightConfig)
    private val saddleForeAftRule = SaddleForeAftRule(config.saddleForeAftConfig)
    private val reachRule = ReachRule(config.reachConfig)
    private val ankleAngleRule = AnkleAngleRule(config.ankleAngleConfig)
    private val hipAngleRule = HipAngleRule(config.hipAngleConfig)

    /**
     * Performs a complete bike fit analysis.
     * 
     * Runs all enabled rules against the provided input data and
     * returns a consolidated result with all detected issues.
     * 
     * @param input The biomechanical data to analyze
     * @return FitAnalysisResult containing all detected issues
     */
    fun analyze(input: FitAnalysisInput): FitAnalysisResult {
        if (!input.hasMinimumData(config.minCyclesForAnalysis)) {
            return FitAnalysisResult(
                issues = emptyList(),
                cycleCount = input.cycleSummary.cycleCount,
                cycleSummary = input.cycleSummary
            )
        }

        val allIssues = mutableListOf<FitIssue>()

        // Run saddle height analysis
        if (config.enableSaddleHeight) {
            allIssues.addAll(analyzeSaddleHeight(input))
        }

        // Run saddle fore/aft analysis
        if (config.enableSaddleForeAft) {
            allIssues.addAll(analyzeSaddleForeAft(input))
        }

        // Run reach analysis
        if (config.enableReach) {
            allIssues.addAll(analyzeReach(input))
        }

        // Run hip rocking analysis
        if (config.enableHipRocking) {
            allIssues.addAll(analyzeHipRocking(input))
        }

        // Run ankle angle analysis
        if (config.enableAnkleAngle) {
            allIssues.addAll(analyzeAnkleAngle(input))
        }

        // Run hip angle analysis
        if (config.enableHipAngle) {
            allIssues.addAll(analyzeHipAngle(input))
        }

        // Sort issues by severity (highest first)
        val sortedIssues = allIssues.sortedByDescending { it.severity.ordinal }

        return FitAnalysisResult(
            issues = sortedIssues,
            cycleCount = input.cycleSummary.cycleCount,
            cycleSummary = input.cycleSummary
        )
    }

    /**
     * Analyzes saddle height using knee angle at BDC.
     */
    private fun analyzeSaddleHeight(input: FitAnalysisInput): List<FitIssue> {
        // Analyze using cycle summary with optional hip rocking
        return saddleHeightRule.analyze(input.cycleSummary, input.hipRockingResult)
            .filter { it.type == FitIssueType.SADDLE_HEIGHT } // Filter out hip rocking issues (handled separately)
    }

    /**
     * Analyzes saddle fore/aft position using KOPS method.
     */
    private fun analyzeSaddleForeAft(input: FitAnalysisInput): List<FitIssue> {
        val calibration = input.bikeCalibration ?: return emptyList()
        val issues = mutableListOf<FitIssue>()

        // Analyze each pose frame at 3 o'clock position
        for (poseFrame in input.poseFramesAt3oClock) {
            issues.addAll(
                saddleForeAftRule.analyze(
                    poseFrame,
                    calibration,
                    input.cycleSummary.side
                )
            )
        }

        // If we have multiple frames, create a consolidated issue
        if (issues.size > 1) {
            return listOf(consolidateForeAftIssues(issues))
        }

        return issues
    }

    /**
     * Consolidates multiple fore/aft issues into a single representative issue.
     */
    private fun consolidateForeAftIssues(issues: List<FitIssue>): FitIssue {
        // Use the most severe issue as the base
        val mostSevere = issues.maxByOrNull { it.severity.ordinal } ?: issues.first()

        // Calculate average measured value
        val measuredValues = issues.mapNotNull { it.measuredValue }
        val avgMeasuredValue = if (measuredValues.isNotEmpty()) {
            measuredValues.average().toFloat()
        } else {
            mostSevere.measuredValue
        }

        return FitIssue(
            type = FitIssueType.SADDLE_FORE_AFT,
            severity = mostSevere.severity,
            description = mostSevere.description,
            measuredValue = avgMeasuredValue,
            optimalRange = mostSevere.optimalRange,
            recommendation = mostSevere.recommendation
        )
    }

    /**
     * Analyzes reach based on torso angle.
     */
    private fun analyzeReach(input: FitAnalysisInput): List<FitIssue> {
        // Analyze using cycle summary (primary method)
        val summaryIssues = reachRule.analyze(input.cycleSummary)
        if (summaryIssues.isNotEmpty()) {
            return summaryIssues
        }

        // If we have individual torso angle results, use them for more detailed analysis
        if (input.torsoAngleResults.isNotEmpty()) {
            for (result in input.torsoAngleResults) {
                val resultIssues = reachRule.analyze(result)
                if (resultIssues.isNotEmpty()) {
                    return resultIssues
                }
            }
        }

        return emptyList()
    }

    /**
     * Analyzes hip rocking.
     */
    private fun analyzeHipRocking(input: FitAnalysisInput): List<FitIssue> {
        val hipRocking = input.hipRockingResult ?: return emptyList()

        if (!hipRocking.isExcessive) {
            return emptyList()
        }

        val severity = when {
            hipRocking.amplitude > config.saddleHeightConfig.hipRockingThreshold * 2 -> Severity.HIGH
            hipRocking.amplitude > config.saddleHeightConfig.hipRockingThreshold * 1.5 -> Severity.MEDIUM
            else -> Severity.LOW
        }

        return listOf(
            FitIssue(
                type = FitIssueType.HIP_ROCKING,
                severity = severity,
                description = "Excessive lateral hip movement detected during pedaling",
                measuredValue = hipRocking.amplitude,
                optimalRange = 0f..config.saddleHeightConfig.hipRockingThreshold,
                recommendation = "Check saddle height (may be too high) or work on core stability"
            )
        )
    }

    /**
     * Analyzes ankle angle (plantarflexion) at BDC.
     */
    private fun analyzeAnkleAngle(input: FitAnalysisInput): List<FitIssue> {
        // Analyze using cycle summary
        return ankleAngleRule.analyze(input.cycleSummary)
    }

    /**
     * Analyzes hip angle at TDC.
     */
    private fun analyzeHipAngle(input: FitAnalysisInput): List<FitIssue> {
        // Analyze using cycle summary
        return hipAngleRule.analyze(input.cycleSummary)
    }

    /**
     * Analyzes a single pedal cycle.
     * 
     * Useful for real-time feedback during recording.
     * 
     * @param cycleMetrics Metrics for the cycle to analyze
     * @param hipRockingResult Optional hip rocking data
     * @return List of issues detected in this cycle
     */
    fun analyzeCycle(
        cycleMetrics: CycleMetrics,
        hipRockingResult: HipRockingResult? = null
    ): List<FitIssue> {
        val issues = mutableListOf<FitIssue>()

        if (config.enableSaddleHeight) {
            issues.addAll(
                saddleHeightRule.analyze(cycleMetrics, hipRockingResult)
                    .filter { it.type == FitIssueType.SADDLE_HEIGHT }
            )
        }

        return issues.sortedByDescending { it.severity.ordinal }
    }

    /**
     * Quick check if any issues are likely present.
     * 
     * Performs a lightweight analysis that can be used for
     * early feedback without full analysis.
     * 
     * @param cycleSummary The cycle summary to check
     * @return True if issues are likely present
     */
    fun hasLikelyIssues(cycleSummary: CycleSummary): Boolean {
        if (!cycleSummary.isValid) return false

        // Quick saddle height check
        cycleSummary.averageKneeAngleAtBdc?.let { kneeAngle ->
            if (SaddleHeightRule.isSaddleTooHigh(kneeAngle, config.saddleHeightConfig) ||
                SaddleHeightRule.isSaddleTooLow(kneeAngle, config.saddleHeightConfig)) {
                return true
            }
        }

        // Quick reach check
        if (ReachRule.isReachTooLong(cycleSummary.averageTorsoAngle, config.reachConfig) ||
            ReachRule.isReachTooShort(cycleSummary.averageTorsoAngle, config.reachConfig)) {
            return true
        }

        return false
    }

    /**
     * Gets a list of issues sorted by priority.
     * 
     * Priority is determined by:
     * 1. Severity (HIGH > MEDIUM > LOW)
     * 2. Issue type order (safety-critical issues first)
     * 
     * @param issues List of issues to prioritize
     * @return Issues sorted by priority
     */
    fun prioritizeIssues(issues: List<FitIssue>): List<FitIssue> {
        return issues.sortedWith(
            compareByDescending<FitIssue> { it.severity.ordinal }
                .thenBy { issueTypePriority(it.type) }
        )
    }

    /**
     * Returns priority order for issue types.
     * Lower number = higher priority.
     */
    private fun issueTypePriority(type: FitIssueType): Int {
        return when (type) {
            FitIssueType.SADDLE_HEIGHT -> 1  // Most common and impactful
            FitIssueType.HIP_ROCKING -> 2    // Often related to saddle height
            FitIssueType.SADDLE_FORE_AFT -> 3
            FitIssueType.REACH -> 4
            FitIssueType.HIP_ANGLE -> 5      // Hip angle at TDC
            FitIssueType.HANDLEBAR_HEIGHT -> 6
            FitIssueType.CLEAT_POSITION -> 7
            FitIssueType.CRANK_LENGTH -> 8
        }
    }

    /**
     * Creates a summary text for the analysis result.
     * 
     * @param result The analysis result to summarize
     * @return Human-readable summary
     */
    fun summarize(result: FitAnalysisResult): String {
        if (!result.hasIssues()) {
            return "No significant fit issues detected. Your bike setup appears to be within optimal ranges."
        }

        val sb = StringBuilder()
        sb.appendLine("Bike Fit Analysis Summary")
        sb.appendLine("=".repeat(30))
        sb.appendLine("Issues found: ${result.issues.size}")
        sb.appendLine()

        val prioritized = prioritizeIssues(result.issues)

        for ((index, issue) in prioritized.withIndex()) {
            sb.appendLine("${index + 1}. [${issue.severity}] ${issue.type.getDescription()}")
            sb.appendLine("   ${issue.description}")
            issue.recommendation?.let { sb.appendLine("   ➡ $it") }
            sb.appendLine()
        }

        return sb.toString()
    }

    companion object {
        /**
         * Creates a FitEngine with all rules enabled.
         */
        fun default(): FitEngine = FitEngine()

        /**
         * Creates a FitEngine with only essential rules (saddle height, hip rocking).
         */
        fun essential(): FitEngine = FitEngine(
            FitEngineConfig(
                enableSaddleHeight = true,
                enableSaddleForeAft = false,
                enableReach = false,
                enableHipRocking = true
            )
        )

        /**
         * Creates a FitEngine configured for quick analysis.
         * Requires fewer cycles and runs essential rules only.
         */
        fun quickAnalysis(): FitEngine = FitEngine(
            FitEngineConfig(
                enableSaddleHeight = true,
                enableSaddleForeAft = false,
                enableReach = true,
                enableHipRocking = true,
                minCyclesForAnalysis = 2
            )
        )
    }
}
