package bikefitstudio.biomechanics

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CycleAggregatorTest {

    private lateinit var aggregator: CycleAggregator

    @BeforeEach
    fun setup() {
        aggregator = CycleAggregator(BodySide.LEFT)
        // Reset counters for each test
        testFrameCounter = 0L
        testTimeCounter = 0L
    }

    // ==================== Basic Construction Tests ====================

    @Test
    fun `aggregator initializes with zero cycles`() {
        assertEquals(0, aggregator.getCycleCount())
        assertTrue(aggregator.getCompletedCycles().isEmpty())
    }

    @Test
    fun `aggregator initializes with correct side`() {
        val leftAgg = CycleAggregator(BodySide.LEFT)
        val rightAgg = CycleAggregator(BodySide.RIGHT)

        assertEquals(BodySide.LEFT, leftAgg.getSide())
        assertEquals(BodySide.RIGHT, rightAgg.getSide())
    }

    // ==================== Adding Measurements Tests ====================

    @Test
    fun `adding measurements does not create completed cycle`() {
        aggregator.addMeasurement(
            frameNumber = 0,
            timestampMs = 0,
            kneeAngle = 30f,
            hipAngle = 70f,
            torsoAngle = 45f
        )

        assertEquals(0, aggregator.getCycleCount())
    }

    @Test
    fun `multiple measurements can be added`() {
        for (i in 0..30) {
            aggregator.addMeasurement(
                frameNumber = i.toLong(),
                timestampMs = i * 33L,
                kneeAngle = 30f + i,
                hipAngle = 70f,
                torsoAngle = 45f
            )
        }

        assertEquals(0, aggregator.getCycleCount())
    }

    @Test
    fun `null measurements are not added to statistics`() {
        aggregator.addMeasurement(
            frameNumber = 0,
            timestampMs = 0,
            kneeAngle = null,
            hipAngle = null,
            torsoAngle = null
        )

        // End cycle to check - need valid knee angles with sufficient ROM
        aggregator.endCycleAtBdc(0, 0, null) // First BDC starts cycle
        // Add varying knee angles to meet minimum ROM requirement
        for (i in 1..15) {
            val kneeAngle = 30f + (i * 4f) // Range 34-90
            aggregator.addMeasurement(i.toLong(), i * 33L, kneeAngle, null, null)
        }
        val cycle = aggregator.endCycleAtBdc(30, 1000, 30f)
        
        // Ensure cycle is not null before checking stats
        assertNotNull(cycle, "Cycle should have been completed")

        // Knee angles were added (15 samples)
        assertEquals(15, cycle?.kneeAngle?.sampleCount)
        assertEquals(0, cycle?.hipAngle?.sampleCount)
        assertEquals(0, cycle?.torsoAngle?.sampleCount)
    }

    // ==================== Cycle Completion Tests ====================

    @Test
    fun `first BDC does not complete a cycle`() {
        // First BDC only starts the cycle, does not complete one
        val result = aggregator.endCycleAtBdc(0, 0, 30f)

        assertNull(result)
        assertEquals(0, aggregator.getCycleCount())
    }

    @Test
    fun `second BDC completes first cycle`() {
        // First BDC starts cycle
        aggregator.endCycleAtBdc(0, 0, 30f)

        // Add measurements during cycle with enough knee ROM (range >= 40 degrees)
        for (i in 1..30) {
            // Knee angle varies from 30 to 90 degrees (range = 60 degrees)
            val kneeAngle = 30f + (i * 2f)
            aggregator.addMeasurement(i.toLong(), i * 33L, kneeAngle, 70f, 45f)
        }

        // Second BDC completes cycle
        val cycle = aggregator.endCycleAtBdc(31, 1023, 32f)

        assertNotNull(cycle)
        assertEquals(1, aggregator.getCycleCount())
        assertEquals(0, cycle!!.cycleNumber)
    }

    @Test
    fun `multiple cycles can be completed`() {
        // Simulate 5 complete cycles with proper knee ROM
        for (cycleNum in 0..5) {
            val startFrame = cycleNum * 30L
            aggregator.endCycleAtBdc(startFrame, startFrame * 33, 30f)

            for (i in 1..29) {
                val frame = startFrame + i
                // Vary knee angle to meet minimum ROM (40 degrees)
                val kneeAngle = 30f + (i * 2f).coerceAtMost(60f) // Range 30-90
                aggregator.addMeasurement(frame, frame * 33, kneeAngle, 70f, 45f)
            }
        }

        // 5 completed cycles (6 BDCs = 5 cycles)
        assertEquals(5, aggregator.getCycleCount())
    }

    @Test
    fun `completed cycle has correct frame range`() {
        aggregator.endCycleAtBdc(100, 3300, 30f)

        for (i in 1..30) {
            // Vary knee angle to meet minimum ROM requirement
            val kneeAngle = 30f + (i * 2f) // Range 32-90
            aggregator.addMeasurement(100 + i.toLong(), 3300 + i * 33L, kneeAngle, 70f, 45f)
        }

        val cycle = aggregator.endCycleAtBdc(131, 4323, 30f)!!

        assertEquals(100L, cycle.startFrameNumber)
        assertEquals(131L, cycle.endFrameNumber)
    }

    @Test
    fun `completed cycle has correct timestamps`() {
        aggregator.endCycleAtBdc(0, 1000, 30f)

        for (i in 1..30) {
            // Vary knee angle to meet minimum ROM requirement
            val kneeAngle = 30f + (i * 2f) // Range 32-90
            aggregator.addMeasurement(i.toLong(), 1000 + i * 33L, kneeAngle, 70f, 45f)
        }

        val cycle = aggregator.endCycleAtBdc(31, 2023, 30f)!!

        assertEquals(1000L, cycle.startTimestampMs)
        assertEquals(2023L, cycle.endTimestampMs)
        assertEquals(1023L, cycle.durationMs)
    }

    // ==================== Angle Statistics Tests ====================

    @Test
    fun `cycle has correct knee angle statistics`() {
        aggregator.endCycleAtBdc(0, 0, 30f)

        // Add angles from 30 to 90
        for (i in 0..60) {
            aggregator.addMeasurement(i.toLong(), i * 16L, 30f + i, null, null)
        }

        val cycle = aggregator.endCycleAtBdc(61, 976, 30f)!!

        assertEquals(30f, cycle.kneeAngle.min, 0.001f)
        assertEquals(90f, cycle.kneeAngle.max, 0.001f)
        assertEquals(60f, cycle.kneeAngle.average, 0.001f)
        assertEquals(61, cycle.kneeAngle.sampleCount)
    }

    @Test
    fun `cycle has correct hip angle statistics`() {
        aggregator.endCycleAtBdc(0, 0, 30f)

        // Add constant hip angle but varying knee angles for ROM
        for (i in 0..29) {
            val kneeAngle = 30f + (i * 2f) // Range 30-88
            aggregator.addMeasurement(i.toLong(), i * 33L, kneeAngle, 70f, null)
        }

        val cycle = aggregator.endCycleAtBdc(30, 990, 30f)!!

        assertEquals(70f, cycle.hipAngle.min, 0.001f)
        assertEquals(70f, cycle.hipAngle.max, 0.001f)
        assertEquals(70f, cycle.hipAngle.average, 0.001f)
    }

    @Test
    fun `cycle has correct torso angle statistics`() {
        aggregator.endCycleAtBdc(0, 0, 30f)

        // Add varying torso angles and varying knee angles for ROM
        // Need enough duration and knee ROM
        aggregator.addMeasurement(0, 0, 30f, null, 40f)
        aggregator.addMeasurement(5, 165, 50f, null, 50f)
        aggregator.addMeasurement(10, 330, 70f, null, 45f)
        aggregator.addMeasurement(15, 495, 90f, null, 45f)

        val cycle = aggregator.endCycleAtBdc(20, 660, 30f)!!

        assertEquals(40f, cycle.torsoAngle.min, 0.001f)
        assertEquals(50f, cycle.torsoAngle.max, 0.001f)
        assertEquals(45f, cycle.torsoAngle.average, 0.001f)
    }

    @Test
    fun `cycle records knee angle at BDC`() {
        aggregator.endCycleAtBdc(0, 0, 28f)
        // Add varying knee angles to meet ROM requirement
        for (i in 1..15) {
            val kneeAngle = 30f + (i * 4f) // Range 34-90
            aggregator.addMeasurement(i.toLong(), i * 33L, kneeAngle, 70f, 45f)
        }
        val cycle = aggregator.endCycleAtBdc(30, 990, 32f)!!

        assertEquals(32f, cycle.kneeAngleAtBdc)
    }

    @Test
    fun `cycle records knee angle at TDC`() {
        aggregator.endCycleAtBdc(0, 0, 30f)
        // Add varying knee angles to meet ROM requirement
        for (i in 1..15) {
            val kneeAngle = 30f + (i * 4f)
            aggregator.addMeasurement(i.toLong(), i * 33L, kneeAngle, 70f, 45f)
        }
        aggregator.recordTdc(95f)
        for (i in 16..29) {
            val kneeAngle = 90f - ((i - 15) * 4f)
            aggregator.addMeasurement(i.toLong(), i * 33L, kneeAngle, 70f, 45f)
        }
        val cycle = aggregator.endCycleAtBdc(30, 990, 30f)!!

        assertEquals(95f, cycle.kneeAngleAtTdc)
    }

    @Test
    fun `cycle records hip angle at TDC`() {
        aggregator.endCycleAtBdc(0, 0, 30f)
        // Add varying knee angles to meet ROM requirement
        for (i in 1..15) {
            val kneeAngle = 30f + (i * 4f)
            aggregator.addMeasurement(i.toLong(), i * 33L, kneeAngle, 70f, 45f)
        }
        aggregator.recordTdc(95f, 55f) // knee angle = 95f, hip angle = 55f
        for (i in 16..29) {
            val kneeAngle = 90f - ((i - 15) * 4f)
            aggregator.addMeasurement(i.toLong(), i * 33L, kneeAngle, 70f, 45f)
        }
        val cycle = aggregator.endCycleAtBdc(30, 990, 30f)!!

        assertEquals(55f, cycle.hipAngleAtTdc)
    }

    // ==================== TDC-based Cycle Tests ====================

    @Test
    fun `cycle can end at TDC`() {
        aggregator.endCycleAtTdc(0, 0, 90f)
        // Add varying knee angles to meet ROM requirement
        for (i in 1..29) {
            val kneeAngle = 30f + (i * 2f) // Range 32-88
            aggregator.addMeasurement(i.toLong(), i * 33L, kneeAngle, 70f, 45f)
        }
        val cycle = aggregator.endCycleAtTdc(30, 990, 92f)

        assertNotNull(cycle)
        assertEquals(92f, cycle!!.kneeAngleAtTdc)
    }

    // ==================== Summary Tests ====================

    @Test
    fun `summary returns invalid when no cycles`() {
        val summary = aggregator.getSummary()

        assertFalse(summary.isValid)
        assertEquals(0, summary.cycleCount)
        assertEquals(BodySide.LEFT, summary.side)
    }

    @Test
    fun `summary calculates average BDC angle across cycles`() {
        // Create 3 cycles with distinct BDC (Max Extension) angles
        createCycleWithAngles(bdcAngle = 138f, tdcAngle = 70f)
        createCycleWithAngles(bdcAngle = 140f, tdcAngle = 70f)
        createCycleWithAngles(bdcAngle = 142f, tdcAngle = 70f)

        val summary = aggregator.getSummary()

        assertEquals(3, summary.cycleCount)
        assertEquals(140f, summary.averageKneeAngleAtBdc!!, 0.001f)
    }

    @Test
    fun `summary calculates average TDC angle across cycles`() {
        createCycleWithAngles(bdcAngle = 30f, tdcAngle = 88f)
        createCycleWithAngles(bdcAngle = 30f, tdcAngle = 90f)
        createCycleWithAngles(bdcAngle = 30f, tdcAngle = 92f)

        val summary = aggregator.getSummary()

        assertEquals(90f, summary.averageKneeAngleAtTdc!!, 0.001f)
    }

    @Test
    fun `summary calculates average knee range`() {
        // Cycles with knee ranges of 60 (30-90), 50 (35-85), 55 (32-87)
        createCycleWithKneeRange(30f, 90f)
        createCycleWithKneeRange(35f, 85f)
        createCycleWithKneeRange(32f, 87f)

        val summary = aggregator.getSummary()

        // Average range = (60 + 50 + 55) / 3 = 55
        assertEquals(55f, summary.averageKneeAngleRange, 0.001f)
    }

    @Test
    fun `summary calculates average cadence`() {
        // Create cycles with known duration and sufficient ROM
        aggregator.endCycleAtBdc(0, 0, 30f)
        for (i in 1..15) {
            val kneeAngle = 30f + (i * 4f) // ROM = 60 degrees
            aggregator.addMeasurement(i.toLong(), i * 33L, kneeAngle, 70f, 45f)
        }
        aggregator.endCycleAtBdc(30, 1000, 30f) // 1 second = 60 RPM
        for (i in 1..15) {
            val kneeAngle = 30f + (i * 4f)
            aggregator.addMeasurement((30 + i).toLong(), 1000 + i * 33L, kneeAngle, 70f, 45f)
        }
        aggregator.endCycleAtBdc(60, 2000, 30f) // 1 second = 60 RPM

        val summary = aggregator.getSummary()

        assertEquals(60f, summary.averageCadenceRpm!!, 1f)
    }

    @Test
    fun `summary has BDC stats with min max avg`() {
        createCycleWithAngles(bdcAngle = 135f, tdcAngle = 70f)
        createCycleWithAngles(bdcAngle = 140f, tdcAngle = 70f)
        createCycleWithAngles(bdcAngle = 145f, tdcAngle = 70f)

        val summary = aggregator.getSummary()

        assertEquals(135f, summary.kneeAngleAtBdcStats.min, 0.001f)
        assertEquals(145f, summary.kneeAngleAtBdcStats.max, 0.001f)
        assertEquals(140f, summary.kneeAngleAtBdcStats.average, 0.001f)
    }

    // ==================== GetLastCycle Tests ====================

    @Test
    fun `getLastCycle returns null when no cycles`() {
        assertNull(aggregator.getLastCycle())
    }

    @Test
    fun `getLastCycle returns most recent cycle`() {
        createCycleWithAngles(bdcAngle = 28f, tdcAngle = 90f)
        createCycleWithAngles(bdcAngle = 30f, tdcAngle = 92f)
        createCycleWithAngles(bdcAngle = 32f, tdcAngle = 94f)

        val lastCycle = aggregator.getLastCycle()!!

        assertEquals(2, lastCycle.cycleNumber)
        assertEquals(32f, lastCycle.kneeAngleAtBdc)
    }

    // ==================== Reset Tests ====================

    @Test
    fun `reset clears all data`() {
        createCycleWithAngles(bdcAngle = 30f, tdcAngle = 90f)
        createCycleWithAngles(bdcAngle = 30f, tdcAngle = 90f)

        assertEquals(2, aggregator.getCycleCount())

        aggregator.reset()

        assertEquals(0, aggregator.getCycleCount())
        assertTrue(aggregator.getCompletedCycles().isEmpty())
        assertNull(aggregator.getLastCycle())
    }

    @Test
    fun `reset allows new cycles to be recorded`() {
        createCycleWithAngles(bdcAngle = 30f, tdcAngle = 90f)
        aggregator.reset()
        createCycleWithAngles(bdcAngle = 35f, tdcAngle = 95f)

        assertEquals(1, aggregator.getCycleCount())
        assertEquals(0, aggregator.getLastCycle()!!.cycleNumber)
    }

    // ==================== Static Aggregation Tests ====================

    @Test
    fun `static aggregateCycles works with cycle list`() {
        val cycles = listOf(
            createTestCycleMetrics(cycleNumber = 0, bdcAngle = 28f, tdcAngle = 88f),
            createTestCycleMetrics(cycleNumber = 1, bdcAngle = 30f, tdcAngle = 90f),
            createTestCycleMetrics(cycleNumber = 2, bdcAngle = 32f, tdcAngle = 92f)
        )

        val summary = CycleAggregator.aggregateCycles(cycles, BodySide.RIGHT)

        assertEquals(3, summary.cycleCount)
        assertEquals(30f, summary.averageKneeAngleAtBdc!!, 0.001f)
        assertEquals(90f, summary.averageKneeAngleAtTdc!!, 0.001f)
        assertEquals(BodySide.RIGHT, summary.side)
    }

    @Test
    fun `static aggregateCycles returns invalid for empty list`() {
        val summary = CycleAggregator.aggregateCycles(emptyList(), BodySide.LEFT)

        assertFalse(summary.isValid)
        assertEquals(0, summary.cycleCount)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `cycle with minimal measurements still needs valid ROM`() {
        aggregator.endCycleAtBdc(0, 0, 30f)
        // Add measurements with sufficient knee ROM (min 40 degrees)
        aggregator.addMeasurement(5, 165, 30f, 70f, 42f)
        aggregator.addMeasurement(10, 330, 70f, 70f, 42f)
        val cycle = aggregator.endCycleAtBdc(20, 660, 30f)!!

        assertEquals(2, cycle.kneeAngle.sampleCount)
        assertEquals(50f, cycle.kneeAngle.average, 0.001f)
        assertEquals(40f, cycle.kneeAngle.range, 0.001f)
    }

    @Test
    fun `cycle side is correct`() {
        val rightAgg = CycleAggregator(BodySide.RIGHT)
        rightAgg.endCycleAtBdc(0, 0, 30f)
        // Add measurements with sufficient knee ROM
        for (i in 1..15) {
            val kneeAngle = 30f + (i * 4f)
            rightAgg.addMeasurement(i.toLong(), i * 33L, kneeAngle, 70f, 42f)
        }
        val cycle = rightAgg.endCycleAtBdc(30, 990, 30f)!!

        assertEquals(BodySide.RIGHT, cycle.side)
    }

    @Test
    fun `getCompletedCycles returns copy of list`() {
        createCycleWithAngles(bdcAngle = 30f, tdcAngle = 90f)

        val cycles1 = aggregator.getCompletedCycles()
        val cycles2 = aggregator.getCompletedCycles()

        assertEquals(cycles1, cycles2)
        assertNotSame(cycles1, cycles2)
    }

    // ==================== Helper Functions ====================

    private var testFrameCounter = 0L
    private var testTimeCounter = 0L

    private fun createCycleWithAngles(bdcAngle: Float, tdcAngle: Float) {
        if (aggregator.getCycleCount() == 0 && testFrameCounter == 0L) {
            // Initialize first BDC
            // BDC is Max Extension
            aggregator.endCycleAtBdc(testFrameCounter, testTimeCounter, bdcAngle)
            testFrameCounter++
            testTimeCounter += 33
        }

        // Simulate cycle from BDC (Max) down to TDC (Min) and back to BDC
        val steps = 15
        val range = bdcAngle - tdcAngle
        
        // Downstroke (Extension -> Flexion)
        for (i in 0..steps) {
            val progress = i.toFloat() / steps
            val kneeAngle = bdcAngle - (range * progress)
            aggregator.addMeasurement(
                testFrameCounter,
                testTimeCounter,
                kneeAngle = kneeAngle,
                hipAngle = 70f,
                torsoAngle = 45f
            )
            testFrameCounter++
            testTimeCounter += 33
        }

        aggregator.recordTdc(tdcAngle)

        // Upstroke (Flexion -> Extension)
        for (i in 0..steps) {
            val progress = i.toFloat() / steps
            val kneeAngle = tdcAngle + (range * progress)
            aggregator.addMeasurement(
                testFrameCounter,
                testTimeCounter,
                kneeAngle = kneeAngle,
                hipAngle = 70f,
                torsoAngle = 45f
            )
            testFrameCounter++
            testTimeCounter += 33
        }

        aggregator.endCycleAtBdc(testFrameCounter, testTimeCounter, bdcAngle)
        testFrameCounter++
        testTimeCounter += 33
    }

    private fun createCycleWithKneeRange(minKnee: Float, maxKnee: Float) {
        if (aggregator.getCycleCount() == 0 && testFrameCounter == 0L) {
            aggregator.endCycleAtBdc(testFrameCounter, testTimeCounter, minKnee)
            testFrameCounter++
            testTimeCounter += 33
        }

        // Add more measurements to meet minimum duration (300ms = ~10 frames at 33ms each)
        aggregator.addMeasurement(testFrameCounter, testTimeCounter, minKnee, 70f, 45f)
        testFrameCounter++
        testTimeCounter += 100 // Larger step to ensure > 300ms total

        // Middle measurements
        val midKnee = (minKnee + maxKnee) / 2f
        aggregator.addMeasurement(testFrameCounter, testTimeCounter, midKnee, 70f, 45f)
        testFrameCounter++
        testTimeCounter += 100

        aggregator.addMeasurement(testFrameCounter, testTimeCounter, maxKnee, 70f, 45f)
        testFrameCounter++
        testTimeCounter += 100

        aggregator.addMeasurement(testFrameCounter, testTimeCounter, midKnee, 70f, 45f)
        testFrameCounter++
        testTimeCounter += 100

        aggregator.endCycleAtBdc(testFrameCounter, testTimeCounter, minKnee)
        testFrameCounter++
        testTimeCounter += 33
    }

    private fun createTestCycleMetrics(
        cycleNumber: Int,
        bdcAngle: Float,
        tdcAngle: Float
    ): CycleMetrics {
        return CycleMetrics(
            cycleNumber = cycleNumber,
            startFrameNumber = cycleNumber * 30L,
            endFrameNumber = (cycleNumber + 1) * 30L,
            startTimestampMs = cycleNumber * 1000L,
            endTimestampMs = (cycleNumber + 1) * 1000L,
            kneeAngle = AngleStats(min = tdcAngle, max = bdcAngle, average = (bdcAngle + tdcAngle) / 2, sampleCount = 30),
            hipAngle = AngleStats(min = 65f, max = 75f, average = 70f, sampleCount = 30),
            torsoAngle = AngleStats(min = 40f, max = 50f, average = 45f, sampleCount = 30),
            ankleAngle = AngleStats.INVALID,
            kneeAngleAtBdc = bdcAngle,
            kneeAngleAtTdc = tdcAngle,
            hipAngleAtTdc = null,
            ankleAngleAtBdc = null,
            kopsNormalized = null,
            side = BodySide.LEFT
        )
    }

    // ==================== Outlier Filtering Tests ====================

    @Test
    fun `filterOutliers removes extreme BDC angles`() {
        val cycles = listOf(
            createTestCycleMetrics(0, 30f, 90f),  // Normal
            createTestCycleMetrics(1, 32f, 90f),  // Normal
            createTestCycleMetrics(2, 31f, 90f),  // Normal
            createTestCycleMetrics(3, 28f, 90f),  // Normal
            createTestCycleMetrics(4, 100f, 90f)  // Outlier - extreme BDC
        )

        val filtered = CycleAggregator.filterOutliers(cycles)

        // Should remove the outlier
        assertEquals(4, filtered.size)
        assertFalse(filtered.any { it.kneeAngleAtBdc == 100f })
    }

    @Test
    fun `filterOutliers removes extreme cadences`() {
        val cycles = listOf(
            createTestCycleMetricsWithCadence(0, 80f),  // Normal
            createTestCycleMetricsWithCadence(1, 82f),  // Normal
            createTestCycleMetricsWithCadence(2, 78f),  // Normal
            createTestCycleMetricsWithCadence(3, 81f),  // Normal
            createTestCycleMetricsWithCadence(4, 200f)  // Outlier - too fast
        )

        val filtered = CycleAggregator.filterOutliers(cycles)

        // Should remove the outlier
        assertTrue(filtered.size < cycles.size)
    }

    @Test
    fun `filterOutliers keeps all cycles when less than 4`() {
        val cycles = listOf(
            createTestCycleMetrics(0, 30f, 90f),
            createTestCycleMetrics(1, 100f, 90f),  // Would be outlier with more data
            createTestCycleMetrics(2, 32f, 90f)
        )

        val filtered = CycleAggregator.filterOutliers(cycles)

        // Should keep all when sample size too small
        assertEquals(3, filtered.size)
    }

    @Test
    fun `calculateDataQuality returns high quality for consistent data`() {
        val cycles = listOf(
            createTestCycleMetrics(0, 30f, 90f),
            createTestCycleMetrics(1, 31f, 91f),
            createTestCycleMetrics(2, 30f, 89f),
            createTestCycleMetrics(3, 29f, 90f),
            createTestCycleMetrics(4, 30f, 90f)
        )

        val quality = CycleAggregator.calculateDataQuality(cycles)

        // Should be high quality (> 0.7) due to low variation
        assertTrue(quality > 0.7f)
    }

    @Test
    fun `calculateDataQuality returns low quality for inconsistent data`() {
        val cycles = listOf(
            createTestCycleMetrics(0, 20f, 70f),
            createTestCycleMetrics(1, 40f, 100f),
            createTestCycleMetrics(2, 15f, 65f),
            createTestCycleMetrics(3, 45f, 110f)
        )

        val quality = CycleAggregator.calculateDataQuality(cycles)

        // Should be lower quality due to high variation
        assertTrue(quality < 0.7f)
    }

    @Test
    fun `calculateDataQuality returns low quality for few samples`() {
        val cycles = listOf(
            createTestCycleMetrics(0, 30f, 90f),
            createTestCycleMetrics(1, 30f, 90f)
        )

        val quality = CycleAggregator.calculateDataQuality(cycles)

        // Should be low quality (< 0.5) with only 2 samples
        assertTrue(quality < 0.5f)
    }

    @Test
    fun `aggregateCycles applies outlier filtering by default`() {
        val cycles = listOf(
            createTestCycleMetrics(0, 30f, 90f),
            createTestCycleMetrics(1, 32f, 90f),
            createTestCycleMetrics(2, 31f, 90f),
            createTestCycleMetrics(3, 28f, 90f),
            createTestCycleMetrics(4, 100f, 90f)  // Outlier
        )

        val summary = CycleAggregator.aggregateCycles(cycles, BodySide.LEFT)

        // Should have removed 1 outlier
        assertEquals(1, summary.outlierCount)
        assertEquals(4, summary.cycleCount)
    }

    @Test
    fun `aggregateCycles without outlier filtering keeps all cycles`() {
        val cycles = listOf(
            createTestCycleMetrics(0, 30f, 90f),
            createTestCycleMetrics(1, 32f, 90f),
            createTestCycleMetrics(2, 100f, 90f)  // Would be outlier
        )

        val summary = CycleAggregator.aggregateCycles(cycles, BodySide.LEFT, applyOutlierFiltering = false)

        // Should keep all cycles
        assertEquals(0, summary.outlierCount)
        assertEquals(3, summary.cycleCount)
    }

    @Test
    fun `getSummary applies outlier filtering by default`() {
        // Create aggregator with outlier
        for (i in 0..4) {
            aggregator.endCycleAtBdc(i * 30L, i * 1000L, if (i == 4) 100f else 30f)
            // Add measurements with sufficient knee ROM
            for (j in 1..15) {
                val kneeAngle = 30f + (j * 4f)
                aggregator.addMeasurement(i * 30L + j, i * 1000L + j * 33L, kneeAngle, 70f, 45f)
            }
        }
        aggregator.endCycleAtBdc(150, 5000, 30f)

        val summary = aggregator.getSummary()

        // Should have filtered the outlier
        assertTrue(summary.outlierCount > 0)
        assertTrue(summary.cycleCount < aggregator.getCycleCount())
    }

    private fun createTestCycleMetricsWithCadence(
        cycleNumber: Int,
        cadenceRpm: Float
    ): CycleMetrics {
        val durationMs = (60000 / cadenceRpm).toLong()  // Convert RPM to ms
        return CycleMetrics(
            cycleNumber = cycleNumber,
            startFrameNumber = cycleNumber * 30L,
            endFrameNumber = (cycleNumber + 1) * 30L,
            startTimestampMs = cycleNumber * durationMs,
            endTimestampMs = (cycleNumber + 1) * durationMs,
            kneeAngle = AngleStats(min = 70f, max = 140f, average = 105f, sampleCount = 30),
            hipAngle = AngleStats(min = 65f, max = 75f, average = 70f, sampleCount = 30),
            torsoAngle = AngleStats(min = 40f, max = 50f, average = 45f, sampleCount = 30),
            ankleAngle = AngleStats.INVALID,
            kneeAngleAtBdc = 140f,
            kneeAngleAtTdc = 70f,
            hipAngleAtTdc = null,
            ankleAngleAtBdc = null,
            kopsNormalized = null,
            side = BodySide.LEFT
        )
    }
}
