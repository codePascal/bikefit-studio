package bikefitstudio.biomechanics

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import bikefitstudio.pose.Landmark
import bikefitstudio.pose.PoseFrame
import bikefitstudio.pose.PoseLandmarkIndex
import kotlin.math.PI
import kotlin.math.sin

/**
 * Unit tests for PedalCycleDetector.
 * 
 * Tests verify:
 * - BDC detection at maximum Y positions
 * - TDC detection at minimum Y positions
 * - Handling of noisy data
 * - Incomplete cycle handling
 * - Configuration options
 */
class PedalCycleDetectorTest {

    private lateinit var detector: PedalCycleDetector

    @Before
    fun setUp() {
        detector = PedalCycleDetector()
    }

    /**
     * Creates a test Landmark with specified coordinates and visibility.
     */
    private fun createLandmark(
        x: Float = 0.5f,
        y: Float = 0.5f,
        visibility: Float = 1.0f
    ): Landmark {
        return Landmark(
            x = x,
            y = y,
            z = 0f,
            visibility = visibility,
            presence = 1.0f
        )
    }

    /**
     * Creates a PoseFrame with specified ankle Y position.
     */
    private fun createPoseFrame(
        frameNumber: Long,
        ankleY: Float,
        side: BodySide,
        visibility: Float = 1.0f
    ): PoseFrame {
        val landmarks = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) {
            createLandmark()
        }

        val ankleIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_ANKLE
        } else {
            PoseLandmarkIndex.RIGHT_ANKLE
        }

        landmarks[ankleIndex] = createLandmark(y = ankleY, visibility = visibility)

        return PoseFrame(
            landmarks = landmarks,
            timestampMs = frameNumber * 33L, // ~30 fps
            frameNumber = frameNumber,
            imageWidth = 1920,
            imageHeight = 1080,
            confidence = 0.9f
        )
    }

    /**
     * Generates a synthetic sine wave of ankle positions simulating pedaling.
     * 
     * @param startFrame Starting frame number
     * @param numFrames Number of frames to generate
     * @param cycles Number of complete pedal cycles
     * @param minY Minimum Y value (TDC - top)
     * @param maxY Maximum Y value (BDC - bottom)
     * @param phaseOffset Phase offset in radians
     */
    private fun generateSineWave(
        startFrame: Long = 0,
        numFrames: Int = 60,
        cycles: Float = 2f,
        minY: Float = 0.3f,
        maxY: Float = 0.7f,
        phaseOffset: Float = 0f
    ): List<AnklePosition> {
        val amplitude = (maxY - minY) / 2
        val center = (maxY + minY) / 2

        return (0 until numFrames).map { i ->
            val phase = (i.toFloat() / numFrames) * cycles * 2 * PI.toFloat() + phaseOffset
            val y = center + amplitude * sin(phase)

            AnklePosition(
                frameNumber = startFrame + i,
                timestampMs = (startFrame + i) * 33L,
                y = y,
                visibility = 1.0f
            )
        }
    }

    // ==================== Basic Detection Tests ====================

    @Test
    fun `detects BDC at maximum Y position`() {
        // Simple triangle pattern: goes up to max, then down
        val positions = listOf(
            AnklePosition(0, 0, 0.5f, 1f),
            AnklePosition(1, 33, 0.55f, 1f),
            AnklePosition(2, 66, 0.6f, 1f),
            AnklePosition(3, 99, 0.65f, 1f),
            AnklePosition(4, 132, 0.7f, 1f),  // BDC - max Y
            AnklePosition(5, 165, 0.65f, 1f),
            AnklePosition(6, 198, 0.6f, 1f),
            AnklePosition(7, 231, 0.55f, 1f),
            AnklePosition(8, 264, 0.5f, 1f),
            AnklePosition(9, 297, 0.45f, 1f),
            AnklePosition(10, 330, 0.4f, 1f),
            AnklePosition(11, 363, 0.35f, 1f),
            AnklePosition(12, 396, 0.3f, 1f),  // TDC - min Y
            AnklePosition(13, 429, 0.35f, 1f),
            AnklePosition(14, 462, 0.4f, 1f),
            AnklePosition(15, 495, 0.45f, 1f)
        )

        val config = PedalCycleDetectorConfig(windowSize = 3, minCycleFrames = 3)
        val events = PedalCycleDetector.analyzeAnklePositions(
            positions,
            BodySide.LEFT,
            config
        )

        val bdcEvents = events.filter { it.type == PedalExtremum.BDC }

        assertTrue("Expected at least 1 BDC event, got ${bdcEvents.size}. Events: $events", bdcEvents.isNotEmpty())

        for (event in bdcEvents) {
            assertTrue(
                "BDC ankleY should be high (near max), got ${event.ankleY}",
                event.ankleY > 0.5f
            )
        }
    }

    @Test
    fun `detects TDC at minimum Y position`() {
        val config = PedalCycleDetectorConfig(windowSize = 5, minCycleFrames = 5)
        val positions = generateSineWave(numFrames = 90, cycles = 2f)

        val events = PedalCycleDetector.analyzeAnklePositions(
            positions,
            BodySide.LEFT,
            config
        )

        val tdcEvents = events.filter { it.type == PedalExtremum.TDC }

        assertTrue("Expected at least 1 TDC event, got ${tdcEvents.size}", tdcEvents.isNotEmpty())

        // TDC should be at low Y values (near 0.3)
        for (event in tdcEvents) {
            assertTrue(
                "TDC ankleY should be low (near min), got ${event.ankleY}",
                event.ankleY < 0.5f
            )
        }
    }

    @Test
    fun `detects both BDC and TDC in complete cycle`() {
        // Use simple triangle pattern like in the BDC test
        val positions = listOf(
            AnklePosition(0, 0, 0.5f, 1f),
            AnklePosition(1, 33, 0.55f, 1f),
            AnklePosition(2, 66, 0.6f, 1f),
            AnklePosition(3, 99, 0.65f, 1f),
            AnklePosition(4, 132, 0.7f, 1f),  // BDC
            AnklePosition(5, 165, 0.65f, 1f),
            AnklePosition(6, 198, 0.6f, 1f),
            AnklePosition(7, 231, 0.55f, 1f),
            AnklePosition(8, 264, 0.5f, 1f),
            AnklePosition(9, 297, 0.45f, 1f),
            AnklePosition(10, 330, 0.4f, 1f),
            AnklePosition(11, 363, 0.35f, 1f),
            AnklePosition(12, 396, 0.3f, 1f),  // TDC
            AnklePosition(13, 429, 0.35f, 1f),
            AnklePosition(14, 462, 0.4f, 1f),
            AnklePosition(15, 495, 0.45f, 1f),
            AnklePosition(16, 528, 0.5f, 1f)
        )

        val config = PedalCycleDetectorConfig(windowSize = 3, minCycleFrames = 3)
        val events = PedalCycleDetector.analyzeAnklePositions(
            positions,
            BodySide.LEFT,
            config
        )

        val bdcEvents = events.filter { it.type == PedalExtremum.BDC }
        val tdcEvents = events.filter { it.type == PedalExtremum.TDC }

        assertTrue("Expected at least 1 BDC event, got ${bdcEvents.size}", bdcEvents.isNotEmpty())
        assertTrue("Expected at least 1 TDC event, got ${tdcEvents.size}", tdcEvents.isNotEmpty())
    }

    // ==================== Multiple Cycles Tests ====================

    @Test
    fun `detects multiple cycles correctly`() {
        val config = PedalCycleDetectorConfig(windowSize = 5, minCycleFrames = 5)
        val positions = generateSineWave(numFrames = 180, cycles = 4f)

        val events = PedalCycleDetector.analyzeAnklePositions(
            positions,
            BodySide.LEFT,
            config
        )

        val bdcEvents = events.filter { it.type == PedalExtremum.BDC }
        val tdcEvents = events.filter { it.type == PedalExtremum.TDC }

        // With 4 cycles, should detect at least 2 of each (edges may be missed)
        assertTrue(
            "Expected at least 2 BDC events for 4 cycles, got ${bdcEvents.size}",
            bdcEvents.size >= 2
        )
        assertTrue(
            "Expected at least 2 TDC events for 4 cycles, got ${tdcEvents.size}",
            tdcEvents.size >= 2
        )
    }

    @Test
    fun `events are in chronological order`() {
        val positions = generateSineWave(numFrames = 90, cycles = 3f)

        val events = PedalCycleDetector.analyzeAnklePositions(
            positions,
            BodySide.LEFT
        )

        for (i in 1 until events.size) {
            assertTrue(
                "Events should be in chronological order",
                events[i].frameNumber >= events[i - 1].frameNumber
            )
        }
    }

    // ==================== Side Detection Tests ====================

    @Test
    fun `detects left leg events correctly`() {
        val positions = generateSineWave(numFrames = 60, cycles = 2f)

        val events = PedalCycleDetector.analyzeAnklePositions(
            positions,
            BodySide.LEFT
        )

        for (event in events) {
            assertEquals(BodySide.LEFT, event.side)
        }
    }

    @Test
    fun `detects right leg events correctly`() {
        val positions = generateSineWave(numFrames = 60, cycles = 2f)

        val events = PedalCycleDetector.analyzeAnklePositions(
            positions,
            BodySide.RIGHT
        )

        for (event in events) {
            assertEquals(BodySide.RIGHT, event.side)
        }
    }

    @Test
    fun `tracks left and right legs independently`() {
        val detector = PedalCycleDetector()

        // Process same positions for both legs
        val positions = generateSineWave(numFrames = 30, cycles = 1f)

        val leftEvents = mutableListOf<PedalExtremumEvent>()
        val rightEvents = mutableListOf<PedalExtremumEvent>()

        for (pos in positions) {
            leftEvents.addAll(
                detector.processAnklePosition(
                    pos.frameNumber, pos.timestampMs, pos.y, pos.visibility, BodySide.LEFT
                )
            )
            rightEvents.addAll(
                detector.processAnklePosition(
                    pos.frameNumber, pos.timestampMs, pos.y, pos.visibility, BodySide.RIGHT
                )
            )
        }

        // Should have similar number of events for both legs
        assertEquals(leftEvents.size, rightEvents.size)
    }

    // ==================== Visibility Tests ====================

    @Test
    fun `ignores frames with low visibility`() {
        val positions = generateSineWave(numFrames = 60, cycles = 2f).map {
            it.copy(visibility = 0.3f) // Below default threshold of 0.5
        }

        val events = PedalCycleDetector.analyzeAnklePositions(
            positions,
            BodySide.LEFT
        )

        assertTrue("Should not detect events with low visibility", events.isEmpty())
    }

    @Test
    fun `respects custom visibility threshold`() {
        val positions = generateSineWave(numFrames = 90, cycles = 2f).map {
            it.copy(visibility = 0.3f)
        }

        val config = PedalCycleDetectorConfig(visibilityThreshold = 0.2f, windowSize = 5, minCycleFrames = 5)

        val events = PedalCycleDetector.analyzeAnklePositions(
            positions,
            BodySide.LEFT,
            config
        )

        assertTrue("Should detect events with lower threshold", events.isNotEmpty())
    }

    // ==================== Configuration Tests ====================

    @Test
    fun `respects minimum cycle frames`() {
        val config = PedalCycleDetectorConfig(minCycleFrames = 20)
        val detector = PedalCycleDetector(config)

        // Generate fast cycling (would trigger many detections without minCycleFrames)
        val positions = generateSineWave(numFrames = 100, cycles = 10f)

        val events = mutableListOf<PedalExtremumEvent>()
        for (pos in positions) {
            events.addAll(
                detector.processAnklePosition(
                    pos.frameNumber, pos.timestampMs, pos.y, pos.visibility, BodySide.LEFT
                )
            )
        }

        // Check that same-type events are at least minCycleFrames apart
        val bdcEvents = events.filter { it.type == PedalExtremum.BDC }
        for (i in 1 until bdcEvents.size) {
            val frameDiff = bdcEvents[i].frameNumber - bdcEvents[i - 1].frameNumber
            assertTrue(
                "BDC events should be at least ${config.minCycleFrames} frames apart, got $frameDiff",
                frameDiff >= config.minCycleFrames
            )
        }
    }

    @Test
    fun `custom window size affects detection`() {
        val configSmall = PedalCycleDetectorConfig(windowSize = 3, minCycleFrames = 5)
        val configLarge = PedalCycleDetectorConfig(windowSize = 9, minCycleFrames = 5)

        val positions = generateSineWave(numFrames = 90, cycles = 2f)

        val eventsSmall = PedalCycleDetector.analyzeAnklePositions(
            positions,
            BodySide.LEFT,
            configSmall
        )

        val eventsLarge = PedalCycleDetector.analyzeAnklePositions(
            positions,
            BodySide.LEFT,
            configLarge
        )

        // Both should detect events, but may differ in count due to sensitivity
        assertTrue("Small window should detect events", eventsSmall.isNotEmpty())
        assertTrue("Large window should detect events", eventsLarge.isNotEmpty())
    }

    // ==================== Reset Tests ====================

    @Test
    fun `reset clears all state`() {
        val detector = PedalCycleDetector()

        // Process some frames
        val positions = generateSineWave(numFrames = 30, cycles = 1f)
        for (pos in positions) {
            detector.processAnklePosition(
                pos.frameNumber, pos.timestampMs, pos.y, pos.visibility, BodySide.LEFT
            )
        }

        assertTrue(detector.getBufferSize(BodySide.LEFT) > 0)

        // Reset
        detector.reset()

        assertEquals(0, detector.getBufferSize(BodySide.LEFT))
        assertEquals(0, detector.getBufferSize(BodySide.RIGHT))
    }

    @Test
    fun `reset side clears only that side`() {
        val detector = PedalCycleDetector()

        val positions = generateSineWave(numFrames = 10, cycles = 0.5f)

        for (pos in positions) {
            detector.processAnklePosition(
                pos.frameNumber, pos.timestampMs, pos.y, pos.visibility, BodySide.LEFT
            )
            detector.processAnklePosition(
                pos.frameNumber, pos.timestampMs, pos.y, pos.visibility, BodySide.RIGHT
            )
        }

        assertTrue(detector.getBufferSize(BodySide.LEFT) > 0)
        assertTrue(detector.getBufferSize(BodySide.RIGHT) > 0)

        detector.reset(BodySide.LEFT)

        assertEquals(0, detector.getBufferSize(BodySide.LEFT))
        assertTrue(detector.getBufferSize(BodySide.RIGHT) > 0)
    }

    // ==================== Incomplete Cycle Tests ====================

    @Test
    fun `handles incomplete cycle gracefully`() {
        // Only half a cycle - should not crash, may detect partial extrema
        val positions = generateSineWave(numFrames = 15, cycles = 0.5f)

        val events = PedalCycleDetector.analyzeAnklePositions(
            positions,
            BodySide.LEFT
        )

        // Should not crash and return a valid (possibly empty) list
        assertNotNull(events)
    }

    @Test
    fun `handles single frame`() {
        val detector = PedalCycleDetector()

        val events = detector.processAnklePosition(
            frameNumber = 0,
            timestampMs = 0,
            ankleY = 0.5f,
            visibility = 1.0f,
            side = BodySide.LEFT
        )

        // Single frame can't be an extremum
        assertTrue(events.isEmpty())
    }

    @Test
    fun `handles empty frame list`() {
        val events = PedalCycleDetector.analyzeAnklePositions(
            emptyList(),
            BodySide.LEFT
        )

        assertTrue(events.isEmpty())
    }

    // ==================== PoseFrame Integration Tests ====================

    @Test
    fun `processes PoseFrame correctly`() {
        val config = PedalCycleDetectorConfig(windowSize = 3, minCycleFrames = 3)
        val detector = PedalCycleDetector(config)

        // Create frames simulating a cycle with enough frames for detection
        val frames = listOf(
            createPoseFrame(0, 0.5f, BodySide.LEFT),
            createPoseFrame(1, 0.55f, BodySide.LEFT),
            createPoseFrame(2, 0.6f, BodySide.LEFT),
            createPoseFrame(3, 0.65f, BodySide.LEFT),
            createPoseFrame(4, 0.7f, BodySide.LEFT), // BDC
            createPoseFrame(5, 0.65f, BodySide.LEFT),
            createPoseFrame(6, 0.6f, BodySide.LEFT),
            createPoseFrame(7, 0.5f, BodySide.LEFT),
            createPoseFrame(8, 0.4f, BodySide.LEFT),
            createPoseFrame(9, 0.35f, BodySide.LEFT),
            createPoseFrame(10, 0.3f, BodySide.LEFT), // TDC
            createPoseFrame(11, 0.35f, BodySide.LEFT),
            createPoseFrame(12, 0.4f, BodySide.LEFT),
            createPoseFrame(13, 0.5f, BodySide.LEFT),
            createPoseFrame(14, 0.6f, BodySide.LEFT),
            createPoseFrame(15, 0.65f, BodySide.LEFT),
            createPoseFrame(16, 0.7f, BodySide.LEFT), // BDC
            createPoseFrame(17, 0.65f, BodySide.LEFT),
            createPoseFrame(18, 0.6f, BodySide.LEFT)
        )

        val events = PedalCycleDetector.analyzeFrameSequence(frames, BodySide.LEFT, config)

        // Should detect at least one type of event
        assertTrue("Should detect events from PoseFrames", events.isNotEmpty())
    }

    @Test
    fun `handles PoseFrame with insufficient landmarks`() {
        val detector = PedalCycleDetector()

        val frame = PoseFrame(
            landmarks = emptyList(),
            timestampMs = 0,
            frameNumber = 0,
            imageWidth = 1920,
            imageHeight = 1080,
            confidence = 0f
        )

        val events = detector.processFrame(frame, BodySide.LEFT)

        assertTrue(events.isEmpty())
    }

    // ==================== Confidence Tests ====================

    @Test
    fun `confidence is valid range`() {
        val positions = generateSineWave(numFrames = 60, cycles = 2f)

        val events = PedalCycleDetector.analyzeAnklePositions(
            positions,
            BodySide.LEFT
        )

        for (event in events) {
            assertTrue(
                "Confidence should be between 0 and 1, got ${event.confidence}",
                event.confidence in 0f..1f
            )
        }
    }

    @Test
    fun `clear extrema have higher confidence`() {
        // Generate a very clear sine wave
        val clearPositions = generateSineWave(
            numFrames = 60,
            cycles = 2f,
            minY = 0.2f,
            maxY = 0.8f // Large amplitude
        )

        val clearEvents = PedalCycleDetector.analyzeAnklePositions(
            clearPositions,
            BodySide.LEFT
        )

        if (clearEvents.isNotEmpty()) {
            val avgConfidence = clearEvents.map { it.confidence }.average()
            assertTrue(
                "Clear extrema should have confidence > 0.5, got $avgConfidence",
                avgConfidence > 0.5
            )
        }
    }

    // ==================== Timestamp Tests ====================

    @Test
    fun `events have correct timestamps`() {
        val positions = generateSineWave(numFrames = 60, cycles = 2f)

        val events = PedalCycleDetector.analyzeAnklePositions(
            positions,
            BodySide.LEFT
        )

        for (event in events) {
            // Timestamp should be approximately frameNumber * 33
            val expectedTs = event.frameNumber * 33
            assertEquals(
                "Timestamp should match frame number",
                expectedTs,
                event.timestampMs
            )
        }
    }
}
