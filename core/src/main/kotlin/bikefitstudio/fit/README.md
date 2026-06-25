# Fit Module

## Overview
The fit module is the expert system that transforms objective biomechanical measurements into actionable fit recommendations. It applies evidence-based bike fitting rules to `CycleMetrics` data and generates prioritized `FitIssue` objects with specific adjustment guidance.

## Architecture

**Data Flow:**
```
CycleMetrics → FitEngine → FitRules → FitIssues → FitSummary → UI Display
```

### Processing Pipeline

1. **Input:** `CycleMetrics` with statistical summaries from biomechanics module
2. **Orchestration:** `FitEngine` feeds metrics to enabled rules
3. **Evaluation:** Each `FitRule` evaluates metrics against thresholds
4. **Prioritization:** Issues sorted by severity (HIGH → MEDIUM → LOW)
5. **Output:** `FitSummary` with grade, issues, and recommendations

## Key Components

### FitEngine
**File:** [FitEngine.kt](FitEngine.kt) (419 lines)

Central orchestrator that coordinates fit rule evaluation and result aggregation.

**Configuration:**
```kotlin
val engine = FitEngine(
    enableSaddleHeightRule = true,
    enableSaddleForeAftRule = true,
    enableReachRule = true,
    minCyclesRequired = 3
)
```

**Usage:**
```kotlin
val summary = engine.analyze(
    cycleMetrics = metrics,
    calibration = bikeCalibration,
    discipline = CyclingDiscipline.ROAD
)

println("Grade: ${summary.grade}")
summary.issues.forEach { issue ->
    println("${issue.severity}: ${issue.title}")
}
```

**Features:**
- Rule enabling/disabling per analysis
- Minimum cycle validation (default: 3 cycles)
- Priority-based issue sorting
- Discipline-specific range application

### Fit Rules

#### SaddleHeightRule
**File:** [SaddleHeightRule.kt](SaddleHeightRule.kt) (343 lines)

Evaluates knee extension at Bottom Dead Center (BDC).

**Optimal Range:** 145-155° knee angle at BDC

**Thresholds:**
```kotlin
object SaddleHeightThresholds {
    const val OPTIMAL_MIN = 145f    // Minimum optimal
    const val OPTIMAL_MAX = 155f    // Maximum optimal
    const val TOO_LOW = 140f        // Below = too low
    const val TOO_HIGH = 160f       // Above = too high
}
```

**Issue Detection:**
- **Saddle Too Low:** Knee angle < 140° at BDC
  - Severity: HIGH
  - Recommendation: "Raise saddle by 10-15mm"
  - Symptoms: Excessive knee flexion, reduced power

- **Saddle Too High:** Knee angle > 160° at BDC
  - Severity: HIGH
  - Recommendation: "Lower saddle by 10-15mm"
  - Symptoms: Reaching for pedals, often with hip rocking
  - Correlation: Analyzes hip rocking detector output

**Severity Escalation:**
- Combines with hip rocking analysis
- Escalates to HIGH if hip rocking detected
- Provides combined recommendations

#### SaddleForeAftRule
**File:** [SaddleForeAftRule.kt](SaddleForeAftRule.kt) (352 lines)

Evaluates knee position relative to pedal spindle using KOPS (Knee Over Pedal Spindle) method.

**Requires:** Bike calibration (3 reference points)

**Optimal Range:** ±3% KOPS tolerance (normalized by femur length)

**Thresholds:**
```kotlin
object KOPSThresholds {
    const val OPTIMAL_TOLERANCE = 0.03f    // ±3%
    const val WARNING_TOLERANCE = 0.05f    // ±5%
}
```

**Issue Detection:**
- **Saddle Too Far Forward:** KOPS < -3%
  - Severity: MEDIUM
  - Recommendation: "Move saddle 8-10mm backward"
  - Symptoms: Knee behind pedal spindle, quads overloaded

- **Saddle Too Far Back:** KOPS > +3%
  - Severity: MEDIUM
  - Recommendation: "Move saddle 10-12mm forward"
  - Symptoms: Knee ahead of pedal spindle, hamstrings overloaded

**Calibration Requirement:**
- Returns empty list if no calibration
- Logs warning and skips analysis
- KOPS meaningless without bike reference points

#### ReachRule
**File:** [ReachRule.kt](ReachRule.kt) (313 lines)

Evaluates cockpit reach and torso position.

**Optimal Range:** 30-60° torso angle (discipline-specific)

**Thresholds:**
```kotlin
object ReachThresholds {
    const val TOO_AGGRESSIVE = 25f    // < 25° = too low/aggressive
    const val TOO_UPRIGHT = 70f       // > 70° = too upright
    
    // Discipline-specific ranges
    val ROAD = 35f..55f
    val ENDURANCE = 40f..60f
    val GRAVEL = 40f..60f
    val TIME_TRIAL = 20f..40f
    val TRIATHLON = 20f..40f
}
```

**Issue Detection:**
- **Reach Too Aggressive:** Torso angle < 25°
  - Severity: MEDIUM
  - Recommendation: "Shorten reach via shorter stem or add spacers"
  - Symptoms: Shoulder/neck strain, very horizontal position

- **Reach Too Upright:** Torso angle > 70°
  - Severity: LOW
  - Recommendation: "Lengthen reach via longer stem"
  - Symptoms: Less aerodynamic, weight too far back

**Discipline Variation:**
- Road/Endurance: More upright (40-60°)
- TT/Triathlon: More aggressive (20-40°)

### Cycling Disciplines

**File:** [CyclingDiscipline.kt](CyclingDiscipline.kt)

Defines 5 cycling disciplines with different fit characteristics:

```kotlin
enum class CyclingDiscipline {
    ROAD,        // Traditional road racing position
    ENDURANCE,   // Comfort-oriented road position
    GRAVEL,      // Upright, stable position
    TIME_TRIAL,  // Aggressive, aerodynamic position
    TRIATHLON    // Similar to TT but different reach priorities
}
```

**Discipline-Specific Ranges:**

Managed by `RangeLookup` class with discipline-specific optimal ranges:

```kotlin
// Example ranges (simplified)
ROAD:
  - Knee at BDC: 145-155°
  - Torso angle: 35-55°
  
ENDURANCE:
  - Knee at BDC: 145-155°
  - Torso angle: 40-60°
  
TIME_TRIAL:
  - Knee at BDC: 145-155°
  - Torso angle: 20-40°
```

### Fit Output Models

#### FitIssue
**File:** [FitIssue.kt](FitIssue.kt) (295 lines)

Represents a single fit problem with severity and recommendation.

```kotlin
data class FitIssue(
    val type: FitIssueType,
    val severity: Severity,
    val title: String,
    val description: String,
    val recommendation: String,
    val affectedMetrics: List<String>,
    val category: FitIssueCategory
)
```

**Severity Levels:**
```kotlin
enum class Severity {
    LOW,      // Optional adjustment
    MEDIUM,   // Recommended adjustment
    HIGH      // Critical issue, adjust immediately
}
```

**Issue Categories:**
```kotlin
enum class FitIssueCategory {
    SADDLE,     // Height, fore-aft position
    COCKPIT,    // Reach, handlebar position
    PEDALING,   // Ankle, pedaling dynamics
    STABILITY   // Hip rocking, stability issues
}
```

**Issue Types (7 total):**
```kotlin
enum class FitIssueType {
    SADDLE_TOO_HIGH,
    SADDLE_TOO_LOW,
    SADDLE_TOO_FAR_FORWARD,
    SADDLE_TOO_FAR_BACK,
    REACH_TOO_AGGRESSIVE,
    REACH_TOO_UPRIGHT,
    HIP_ROCKING_EXCESSIVE
}
```

#### FitSummary
**File:** [FitSummary.kt](FitSummary.kt) (389 lines)

Complete analysis summary with grade, issues, and metrics.

```kotlin
data class FitSummary(
    val grade: FitGrade,
    val issues: List<FitIssue>,
    val metricRanges: Map<FitMetricType, MetricRange>,
    val cycleCount: Int,
    val discipline: CyclingDiscipline,
    val timestamp: Long
)
```

**Fit Grades:**
```kotlin
enum class FitGrade {
    EXCELLENT,  // No issues, all metrics optimal
    GOOD,       // 1-2 minor issues
    FAIR,       // 3-4 issues or 1 high-severity
    POOR        // 5+ issues or multiple high-severity
}
```

**Grade Calculation Logic:**
```kotlin
fun calculateGrade(issues: List<FitIssue>): FitGrade {
    val highCount = issues.count { it.severity == Severity.HIGH }
    val totalCount = issues.size
    
    return when {
        totalCount == 0 -> FitGrade.EXCELLENT
        highCount >= 2 || totalCount >= 5 -> FitGrade.POOR
        highCount == 1 || totalCount >= 3 -> FitGrade.FAIR
        else -> FitGrade.GOOD
    }
}
```

**Issue Organization:**
```kotlin
// Access by category
summary.issuesByCategory[FitIssueCategory.SADDLE]

// Access by severity
summary.highSeverityIssues
summary.mediumSeverityIssues
summary.lowSeverityIssues
```

### Supporting Classes

#### MetricRange
**File:** [MetricRange.kt](MetricRange.kt)

Defines optimal, warning, and critical ranges for each metric.

```kotlin
data class MetricRange(
    val optimal: ClosedFloatingPointRange<Float>,
    val warning: ClosedFloatingPointRange<Float>,
    val critical: ClosedFloatingPointRange<Float>
)
```

#### RangeLookup
**File:** [RangeLookup.kt](RangeLookup.kt)

Provides discipline-specific ranges for all metrics.

```kotlin
object RangeLookup {
    fun getKneeAngleRange(discipline: CyclingDiscipline): MetricRange {
        return when (discipline) {
            CyclingDiscipline.ROAD -> MetricRange(
                optimal = 145f..155f,
                warning = 140f..160f,
                critical = 135f..165f
            )
            // ... other disciplines
        }
    }
}
```

## Configuration

### Engine Configuration

```kotlin
// Enable/disable specific rules
val engine = FitEngine(
    enableSaddleHeightRule = true,
    enableSaddleForeAftRule = true,  // Requires calibration
    enableReachRule = true,
    minCyclesRequired = 3             // Minimum data quality
)
```

### Threshold Tuning

All thresholds defined as documented constants:

```kotlin
object SaddleHeightThresholds {
    /** Minimum optimal knee angle at BDC */
    const val OPTIMAL_MIN = 145f
    
    /** Maximum optimal knee angle at BDC */
    const val OPTIMAL_MAX = 155f
    
    /** Below this indicates saddle too low */
    const val TOO_LOW = 140f
    
    /** Above this indicates saddle too high */
    const val TOO_HIGH = 160f
}
```

**Customization Example:**
```kotlin
// For more conservative recommendations
const val OPTIMAL_MIN = 147f  // Tighter range
const val OPTIMAL_MAX = 153f
```

## Extension Guide

### Adding a Custom Fit Rule

1. **Implement FitRule Interface:**

```kotlin
class CustomRule : FitRule {
    override fun evaluate(
        cycleMetrics: CycleMetrics,
        calibration: BikeCalibration?,
        discipline: CyclingDiscipline
    ): List<FitIssue> {
        val issues = mutableListOf<FitIssue>()
        
        // Extract relevant metric
        val metric = cycleMetrics.customMetric.average
        
        // Check against threshold
        if (metric < THRESHOLD) {
            issues.add(FitIssue(
                type = FitIssueType.CUSTOM,
                severity = Severity.MEDIUM,
                title = "Custom Issue Detected",
                description = "Metric ${metric} below threshold ${THRESHOLD}",
                recommendation = "Adjust X by Y amount",
                affectedMetrics = listOf("custom_metric"),
                category = FitIssueCategory.PEDALING
            ))
        }
        
        return issues
    }
    
    companion object {
        const val THRESHOLD = 50f
    }
}
```

2. **Register in FitEngine:**

```kotlin
val engine = FitEngine(
    customRules = listOf(CustomRule())
)
```

3. **Add Tests:**

```kotlin
class CustomRuleTest {
    @Test
    fun `detects issue when below threshold`() {
        val rule = CustomRule()
        val metrics = createTestMetrics(customMetric = 45f)
        
        val issues = rule.evaluate(metrics, null, CyclingDiscipline.ROAD)
        
        assertEquals(1, issues.size)
        assertEquals(Severity.MEDIUM, issues[0].severity)
    }
}
```

## Testing

### Test Coverage

12 comprehensive test files covering all fit components:

- [FitEngineTest.kt](../../../../test/kotlin/bikefitstudio/fit/FitEngineTest.kt)
- [SaddleHeightRuleTest.kt](../../../../test/kotlin/bikefitstudio/fit/SaddleHeightRuleTest.kt)
- [SaddleForeAftRuleTest.kt](../../../../test/kotlin/bikefitstudio/fit/SaddleForeAftRuleTest.kt)
- [ReachRuleTest.kt](../../../../test/kotlin/bikefitstudio/fit/ReachRuleTest.kt)
- [FitSummaryTest.kt](../../../../test/kotlin/bikefitstudio/fit/FitSummaryTest.kt)
- [FitIssueTest.kt](../../../../test/kotlin/bikefitstudio/fit/FitIssueTest.kt)
- Additional tests for disciplines, ranges, metrics

### Test Strategy

**Boundary Testing:**
```kotlin
@Test
fun `no issue at optimal minimum`() {
    val metrics = createTestMetrics(kneeAngle = 145f)
    val issues = rule.evaluate(metrics)
    assertTrue(issues.isEmpty())
}

@Test
fun `detects issue just below optimal`() {
    val metrics = createTestMetrics(kneeAngle = 139f)
    val issues = rule.evaluate(metrics)
    assertEquals(1, issues.size)
}
```

**Severity Testing:**
```kotlin
@Test
fun `high severity for extreme values`() {
    val metrics = createTestMetrics(kneeAngle = 130f)
    val issues = rule.evaluate(metrics)
    assertEquals(Severity.HIGH, issues[0].severity)
}
```

**Integration Testing:**
```kotlin
@Test
fun `fit engine orchestrates multiple rules`() {
    val engine = FitEngine(enableAllRules = true)
    val summary = engine.analyze(metrics, calibration, discipline)
    assertTrue(summary.issues.size >= 2)
}
```

## Dependencies

**Internal:**
- `biomechanics` module: `CycleMetrics`, `AngleStats`
- `calibration` module: `BikeCalibration` (for KOPS analysis)

**External:** None (pure Kotlin)

**Testing:** JUnit 5 Jupiter

## Performance

- **Rule Evaluation:** <1ms per rule
- **Summary Generation:** <5ms total
- **Memory:** ~10KB for FitSummary with 10 issues

Negligible performance impact compared to pose estimation and biomechanics analysis.

## Additional Resources

- API.md - API reference with usage examples
- architecture.md - System architecture
- USERGUIDE.md - User interpretation guide
- Individual rule files with comprehensive KDoc comments
