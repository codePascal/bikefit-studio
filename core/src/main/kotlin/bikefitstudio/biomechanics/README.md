# Biomechanics Module

## Overview
The biomechanics module is the mathematical core of BikefitApp. It consumes streams of `PoseFrame` data and produces comprehensive `CycleMetrics` through angle calculations, cycle detection, and statistical aggregation. This module is completely independent of camera, UI, and Android framework dependencies—all functions are pure and highly testable.

## Key Metrics

The biomechanical analysis focuses on **four key metrics**:

| Metric | Landmarks | Description |
|--------|-----------|-------------|
| **A. Knee Flexion/Extension (BDC)** | hip → knee → ankle | Knee angle at bottom dead center |
| **B. Hip Angle (TDC)** | shoulder/torso → hip → knee | Minimum hip angle during crank cycle |
| **C. Torso Angle** | shoulder → hip relative to horizontal | Back angle measurement |
| **D. Ankle Plantarflexion (BDC)** | knee → ankle → foot index | Plantarflexion at bottom dead center (0° = neutral, positive = plantarflexion, typical: 20-30°) |

## Architecture

**Data Flow:**
```
PoseFrame → AngleCalculators → PedalCycleDetector → CycleAggregator → CycleMetrics
```

### Processing Pipeline

1. **Input:** `PoseFrame` with 33 MediaPipe landmarks (normalized coordinates 0-1)
2. **Angle Calculation:** Extract joint angles from pose geometry
3. **Cycle Detection:** Identify BDC/TDC events from ankle trajectory
4. **Aggregation:** Accumulate frame data, compute statistics per cycle
5. **Output:** `CycleMetrics` with `AngleStats` (min/max/avg/stddev) for each metric

## Key Components

### Angle Calculators

#### KneeAngleCalculator
**File:** [KneeAngleCalculator.kt](KneeAngleCalculator.kt) (262 lines)

Calculates knee flexion/extension angle from hip-knee-ankle landmarks.

**Usage:**
```kotlin
val result = KneeAngleCalculator.calculateKneeAngle(
    poseResult = poseResult,
    side = BodySide.LEFT
)

if (result.isValid) {
    println("Knee angle: ${result.angle}°")
}
```

**Validation:** Checks landmark visibility and geometric validity  
**Output:** `AngleResult` with angle, validity, confidence

#### HipAngleCalculator
Measures hip range of motion from shoulder-hip-knee landmarks.

#### AnkleAngleCalculator
Calculates ankle plantarflexion from knee-ankle-foot landmarks.
- 0° = neutral (foot perpendicular to shin)
- Positive = plantarflexion (foot pointing down)
- Negative = dorsiflexion (foot pointing up)
- Typical at BDC: 20-30°

#### TorsoAngleCalculator
Measures back angle relative to horizontal using shoulder-hip landmarks.

**Key Insight:** All calculators use `Vector2D` for geometric calculations and validate landmark visibility before computing angles.

### Cycle Detection

#### PedalCycleDetector
**File:** [PedalCycleDetector.kt](PedalCycleDetector.kt) (430 lines)

Analyzes ankle trajectory using sliding window peak/valley detection to identify discrete pedal strokes.

**Algorithm:** Sliding window on ankle Y-coordinate to detect:
- **BDC (Bottom Dead Center):** Ankle at lowest point (max Y)
- **TDC (Top Dead Center):** Ankle at highest point (min Y)

**Configuration:**
```kotlin
val detector = PedalCycleDetector(
    windowSize = 15,              // Frame window (~0.6s at 24 FPS)
    minPeakProminence = 0.02f,   // 2% image height
    minCycleDuration = 20         // Minimum frames between events
)
```

**Tuning Guide:**
- **Slow Cadence (<70 RPM):** Increase `windowSize` to 20-25
- **Fast Cadence (>100 RPM):** Decrease to 10-12
- **False Positives:** Increase `minPeakProminence` to 0.03
- **Missing Events:** Decrease `minPeakProminence` to 0.015

See ALGORITHMS.md for detailed algorithm description.

### Statistical Aggregation

#### CycleAggregator
**File:** [CycleAggregator.kt](CycleAggregator.kt) (480 lines)

Accumulates frame-by-frame measurements over complete pedal cycles and computes statistical summaries.

**Process:**
1. Collect frame data between cycle events
2. On cycle completion (BDC/TDC), compute statistics
3. Output `CycleMetrics` with `AngleStats` for each measurement

**Statistics Computed:**
- **Minimum:** Lowest value in cycle
- **Maximum:** Highest value in cycle
- **Average:** Mean value across all frames
- **Standard Deviation:** Variability measure
- **Range:** Max - Min

**Usage:**
```kotlin
val aggregator = CycleAggregator()

// Feed frames
aggregator.addFrame(poseFrame, cycleEvent)

// Retrieve completed cycle
if (cycleEvent == CycleEvent.BDC) {
    val metrics = aggregator.getCurrentCycleMetrics()
    println("Knee: ${metrics.kneeAngle.average}°")
}
```

### Advanced Analysis

#### HipRockingDetector
**File:** [HipRockingDetector.kt](HipRockingDetector.kt) (316 lines)

Detects excessive vertical hip motion using two-metric analysis:
1. **Variance Analysis:** Measures hip position variability
2. **Peak-to-Peak Amplitude:** Measures total hip excursion

**Thresholds:**
- Normal: <3% image height amplitude
- Mild: 3-5%
- Significant: 5-8%
- Severe: >8%

**Correlation:** Hip rocking typically indicates saddle too high (knee angle >155° at BDC).

#### KneeOverPedalOffset (KOPS)
**File:** [KneeOverPedalOffset.kt](KneeOverPedalOffset.kt) (407 lines)

Calculates horizontal offset between knee and pedal spindle at 3 o'clock position, normalized by femur length.

**Formula:**
```
KOPS = (knee_x - pedal_spindle_x) / femur_length
```

**Optimal:** ±3% tolerance  
**Requires:** Bike calibration (3 reference points)

See ALGORITHMS.md for detailed calculation method.

#### KeyFrameSelector
Identifies key positions in pedal stroke for analysis:
- BDC frame (maximum knee extension)
- TDC frame (maximum knee flexion)
- 3 o'clock frame (for KOPS analysis)

#### Specialized Extractors
- `AnkleFlexionAtBdc` - Extract ankle angle at bottom of stroke
- `KneeFlexionAtBdc` - Extract knee angle at bottom of stroke
- `HipAngleAtTdc` - Extract hip angle at top of stroke

### Vector Math Library

#### Vector2D
**File:** [Vector2D.kt](Vector2D.kt) (315 lines)

Complete 2D vector mathematics library with comprehensive geometric operations.

**Core Operations:**
```kotlin
val v1 = Vector2D(1f, 0f)
val v2 = Vector2D(0f, 1f)

// Magnitude and normalization
val length = v1.magnitude()              // 1.0
val normalized = v1.normalized()         // Unit vector

// Dot and cross products
val dot = v1.dot(v2)                    // 0.0 (perpendicular)
val cross = v1.cross(v2)                // 1.0

// Angles
val angle = v1.angleTo(v2)              // 90.0°
val signed = v1.signedAngleTo(v2, up)   // -90.0° or +90.0°

// Distance
val dist = v1.distanceTo(v2)            // sqrt(2)
val distSq = v1.distanceSquaredTo(v2)   // 2.0 (faster, no sqrt)

// Transformations
val rotated = v1.rotate(45f)            // Rotate by degrees
val projected = v1.project(onto = v2)   // Project onto v2
val lerped = v1.lerp(v2, t = 0.5f)     // Linear interpolation
```

**Static Constants:**
```kotlin
Vector2D.ZERO   // (0, 0)
Vector2D.RIGHT  // (1, 0)
Vector2D.UP     // (0, 1)
Vector2D.LEFT   // (-1, 0)
Vector2D.DOWN   // (0, -1)
```

**Documentation:** Comprehensive KDoc with mathematical formulas for all operations.

## Coordinate Systems

The biomechanics module operates on **normalized MediaPipe coordinates** (0-1 range):

- **Origin:** Top-left corner
- **X-axis:** Left (0) to right (1)
- **Y-axis:** Top (0) to bottom (1)
- **Invariant:** Resolution-independent

**Physical Coordinates:** Transformations to physical measurements (mm) handled by calibration module's `CoordinateTransformer`.

## Data Models

### CycleMetrics
**File:** [CycleMetrics.kt](CycleMetrics.kt)

Complete cycle data with per-metric statistics for the four key bike fit metrics.

```kotlin
data class CycleMetrics(
    val cycleNumber: Int,
    val startFrameNumber: Long,
    val endFrameNumber: Long,
    val startTimestampMs: Long,
    val endTimestampMs: Long,
    val kneeAngle: AngleStats,           // General knee angle stats
    val hipAngle: AngleStats,            // General hip angle stats
    val torsoAngle: AngleStats,          // C. Torso angle stats
    val ankleAngle: AngleStats,          // General ankle angle stats
    val kneeAngleAtBdc: Float?,          // A. Knee angle at BDC
    val kneeAngleAtTdc: Float?,          // Knee angle at TDC
    val hipAngleAtTdc: Float?,           // B. Hip angle at TDC (minimum)
    val ankleAngleAtBdc: Float?,         // D. Ankle angle at BDC
    val side: BodySide
)
```

### CycleSummary

Aggregated statistics across multiple pedal cycles for the four key metrics.

```kotlin
data class CycleSummary(
    val cycleCount: Int,
    val averageKneeAngleAtBdc: Float?,   // A. Knee Flexion/Extension at BDC
    val averageHipAngleAtTdc: Float?,    // B. Hip Angle at TDC
    val averageTorsoAngle: Float,        // C. Torso Angle
    val averageAnkleAngleAtBdc: Float?,  // D. Ankle Angle at BDC
    val kneeAngleAtBdcStats: AngleStats,
    val hipAngleAtTdcStats: AngleStats,
    val torsoAngleStats: AngleStats,
    val ankleAngleAtBdcStats: AngleStats,
    val side: BodySide,
    val dataQuality: Float
)
```

### AngleStats

Statistical summary for a metric over one cycle.

```kotlin
data class AngleStats(
    val min: Float,
    val max: Float,
    val average: Float,
    val stddev: Float,
    val range: Float = max - min
)
```

**Example:**
```kotlin
AngleStats(
    min = 140.2f,
    max = 152.8f,
    average = 148.1f,
    stddev = 3.4f,
    range = 12.6f
)
```

## Configuration & Tuning

### Global Constants

**Cycle Detection:**
```kotlin
const val WINDOW_SIZE = 15           // Frame window
const val MIN_PEAK_PROMINENCE = 0.02f // 2% image height
const val MIN_CYCLE_DURATION = 20     // Frames between events
```

**Hip Rocking:**
```kotlin
const val VARIANCE_THRESHOLD = 0.0002f
const val AMPLITUDE_THRESHOLD = 0.05f  // 5% image height
```

**KOPS:**
```kotlin
const val OPTIMAL_TOLERANCE = 0.03f    // ±3%
const val WARNING_TOLERANCE = 0.05f    // ±5%
```

### Tuning Scenarios

**Noisy Pose Detection:**
- Increase visibility thresholds (0.6-0.7)
- Increase MIN_PEAK_PROMINENCE (0.03)
- Smooth more aggressively in pose module (alpha=0.3)

**Variable Cadence:**
- Increase WINDOW_SIZE (20-25 frames)
- Decrease MIN_PEAK_PROMINENCE (0.015)

**Performance Optimization:**
- Use `distanceSquaredTo()` instead of `distanceTo()` (avoids sqrt)
- Pre-allocate Vector2D objects in hot paths
- Batch process frames when possible

## Testing

### Test Coverage
All biomechanics components have comprehensive unit tests (14 test files):

- [Vector2DTest.kt](../../../../test/kotlin/bikefitstudio/biomechanics/Vector2DTest.kt) (392 lines) - Math verification
- [KneeAngleCalculatorTest.kt](../../../../test/kotlin/bikefitstudio/biomechanics/KneeAngleCalculatorTest.kt)
- [PedalCycleDetectorTest.kt](../../../../test/kotlin/bikefitstudio/biomechanics/PedalCycleDetectorTest.kt)
- [CycleAggregatorTest.kt](../../../../test/kotlin/bikefitstudio/biomechanics/CycleAggregatorTest.kt)
- [HipRockingDetectorTest.kt](../../../../test/kotlin/bikefitstudio/biomechanics/HipRockingDetectorTest.kt)
- [KneeOverPedalOffsetTest.kt](../../../../test/kotlin/bikefitstudio/biomechanics/KneeOverPedalOffsetTest.kt)
- Additional tests for hip, ankle, torso calculators and extractors

### Test Strategy

**Known Geometric Cases:**
```kotlin
@Test
fun `90 degree right angle`() {
    val v1 = Vector2D(1f, 0f)
    val v2 = Vector2D(0f, 1f)
    assertEquals(90f, v1.angleTo(v2), 0.1f)
}
```

**Boundary Conditions:**
```kotlin
@Test
fun `knee angle at optimal minimum`() {
    val result = calculator.calculate(kneeAngle = 145f)
    assertTrue(result.isInOptimalRange)
}
```

**Edge Cases:**
```kotlin
@Test
fun `zero vector handling`() {
    val result = Vector2D.ZERO.angleTo(Vector2D.RIGHT)
    assertFalse(result.isValid)
}
```

## Dependencies

**External:** None (pure Kotlin, no Android dependencies)

**Internal:**
- `pose` module: `PoseResult`, `PoseFrame` data models
- `calibration` module: `BikeCalibration` for KOPS analysis

**Testing:** JUnit 5 Jupiter

## Performance Characteristics

- **Angle Calculation:** <1ms per frame (4 angles)
- **Cycle Detection:** <2ms per frame
- **Aggregation:** O(F) where F = frames per cycle (~60-80)
- **Memory:** ~16KB per cycle for frame accumulation

On mid-range Android (Snapdragon 765G): Full pipeline runs at 24 FPS with <5ms latency.

## Additional Resources

- ALGORITHMS.md - Detailed algorithm descriptions
- API.md - API reference with examples
- architecture.md - System-wide architecture
- [Vector2D.kt](Vector2D.kt) - Comprehensive KDoc for vector operations
