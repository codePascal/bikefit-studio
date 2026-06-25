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
 * Unit tests for KeyFrameSelector.
 * 
 * Tests verify:
 * - BDC-to-BDC cycle segmentation
 * - TDC-to-TDC cycle segmentation
 * - Intermediate frame capture
 * - Key frame extraction
 * - Configuration options
 */
class KeyFrameSelectorTest {

    private lateinit var selector: KeyFrameSelector

    @Before
    fun setUp() {
        selector = KeyFrameSelector(BodySide.LEFT)
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
            timestampMs = frameNumber * 33L,
            frameNumber = frameNumber,
            imageWidth = 1920,
            imageHeight = 1080,
            confidence = 0.9f
        )
    }

    /**
     * Generates a synthetic sine wave of ankle positions simulating pedaling.
     */
    private fun generateSineWave(
        startFrame: Long = 0,
        numFrames: Int = 60,
        cycles: Float = 2f,
        minY: Float = 0.3f,
        maxY: Float = 0.7f
    ): List<AnklePosition> {
        val amplitude = (maxY - minY) / 2
        val center = (maxY + minY) / 2

        return (0 until numFrames).map { i ->
            val phase = (i.toFloat() / numFrames) * cycles * 2 * PI.toFloat()
            val y = center + amplitude * sin(phase)

            AnklePosition(
                frameNumber = startFrame + i,
                timestampMs = (startFrame + i) * 33L,
                y = y,
                visibility = 1.0f
            )
        }
    }

    // ==================== Basic Construction Tests ====================

    @Test
    fun `selector initializes with zero segments`() {
        assertEquals(0, selector.getSegmentCount())
        assertTrue(selector.getCompletedSegments().isEmpty())
    }

    @Test
    fun `selector initializes with correct side`() {
        val leftSelector = KeyFrameSelector(BodySide.LEFT)
        val rightSelector = KeyFrameSelector(BodySide.RIGHT)

        assertEquals(BodySide.LEFT, leftSelector.getSide())
        assertEquals(BodySide.RIGHT, rightSelector.getSide())
    }

    // ==================== BDC-to-BDC Segmentation Tests ====================

    @Test
    fun `BDC-to-BDC segmentation creates segment from two BDCs`() {
        val config = KeyFrameSelectorConfig(
            segmentationType = SegmentationType.BDC_TO_BDC,
            captureIntermediateFrames = false
        )
        val selector = KeyFrameSelector(BodySide.LEFT, config)

        val bdc1 = PedalExtremumEvent(
            type = PedalExtremum.BDC,
            frameNumber = 10,
            timestampMs = 330,
            ankleY = 0.7f,
            side = BodySide.LEFT,
            confidence = 0.9f
        )

        val bdc2 = PedalExtremumEvent(
            type = PedalExtremum.BDC,
            frameNumber = 40,
            timestampMs = 1320,
            ankleY = 0.7f,
            side = BodySide.LEFT,
            confidence = 0.9f
        )

        selector.processEvent(bdc1)
        assertEquals("First BDC should not complete a segment", 0, selector.getSegmentCount())

        selector.processEvent(bdc2)
        assertEquals("Second BDC should complete a segment", 1, selector.getSegmentCount())

        val segment = selector.getCompletedSegments().first()
        assertEquals(10L, segment.startFrame.frameNumber)
        assertEquals(40L, segment.endFrame.frameNumber)
        assertEquals("Start frame should be BDC", PedalExtremum.BDC, segment.startFrame.type)
        assertEquals("End frame should be BDC", PedalExtremum.BDC, segment.endFrame.type)
    }

    @Test
    fun `BDC-to-BDC segmentation captures TDC as intermediate frame`() {
        val config = KeyFrameSelectorConfig(
            segmentationType = SegmentationType.BDC_TO_BDC,
            captureIntermediateFrames = true
        )
        val selector = KeyFrameSelector(BodySide.LEFT, config)

        val bdc1 = createExtremumEvent(PedalExtremum.BDC, 10, 330)
        val tdc = createExtremumEvent(PedalExtremum.TDC, 25, 825)
        val bdc2 = createExtremumEvent(PedalExtremum.BDC, 40, 1320)

        selector.processEvent(bdc1)
        selector.processEvent(tdc)
        selector.processEvent(bdc2)

        val segment = selector.getCompletedSegments().first()
        assertEquals(1, segment.intermediateFrames.size)
        assertEquals("Intermediate frame should be TDC", PedalExtremum.TDC, segment.intermediateFrames[0].type)
        assertEquals(25L, segment.intermediateFrames[0].frameNumber)
    }

    @Test
    fun `BDC-to-BDC segmentation without intermediate capture ignores TDC`() {
        val config = KeyFrameSelectorConfig(
            segmentationType = SegmentationType.BDC_TO_BDC,
            captureIntermediateFrames = false
        )
        val selector = KeyFrameSelector(BodySide.LEFT, config)

        val bdc1 = createExtremumEvent(PedalExtremum.BDC, 10, 330)
        val tdc = createExtremumEvent(PedalExtremum.TDC, 25, 825)
        val bdc2 = createExtremumEvent(PedalExtremum.BDC, 40, 1320)

        selector.processEvent(bdc1)
        selector.processEvent(tdc)
        selector.processEvent(bdc2)

        val segment = selector.getCompletedSegments().first()
        assertEquals(0, segment.intermediateFrames.size)
    }

    // ==================== TDC-to-TDC Segmentation Tests ====================

    @Test
    fun `TDC-to-TDC segmentation creates segment from two TDCs`() {
        val config = KeyFrameSelectorConfig(
            segmentationType = SegmentationType.TDC_TO_TDC,
            captureIntermediateFrames = false
        )
        val selector = KeyFrameSelector(BodySide.LEFT, config)

        val tdc1 = createExtremumEvent(PedalExtremum.TDC, 10, 330)
        val tdc2 = createExtremumEvent(PedalExtremum.TDC, 40, 1320)

        selector.processEvent(tdc1)
        assertEquals(0, selector.getSegmentCount())

        selector.processEvent(tdc2)
        assertEquals(1, selector.getSegmentCount())

        val segment = selector.getCompletedSegments().first()
        assertEquals("Start frame should be TDC", PedalExtremum.TDC, segment.startFrame.type)
        assertEquals("End frame should be TDC", PedalExtremum.TDC, segment.endFrame.type)
    }

    @Test
    fun `TDC-to-TDC segmentation captures BDC as intermediate frame`() {
        val config = KeyFrameSelectorConfig(
            segmentationType = SegmentationType.TDC_TO_TDC,
            captureIntermediateFrames = true
        )
        val selector = KeyFrameSelector(BodySide.LEFT, config)

        val tdc1 = createExtremumEvent(PedalExtremum.TDC, 10, 330)
        val bdc = createExtremumEvent(PedalExtremum.BDC, 25, 825)
        val tdc2 = createExtremumEvent(PedalExtremum.TDC, 40, 1320)

        selector.processEvent(tdc1)
        selector.processEvent(bdc)
        selector.processEvent(tdc2)

        val segment = selector.getCompletedSegments().first()
        assertEquals(1, segment.intermediateFrames.size)
        assertEquals("Intermediate frame should be BDC", PedalExtremum.BDC, segment.intermediateFrames[0].type)
    }

    // ==================== Multiple Cycles Tests ====================

    @Test
    fun `creates multiple segments from multiple cycles`() {
        val config = KeyFrameSelectorConfig(segmentationType = SegmentationType.BDC_TO_BDC)
        val selector = KeyFrameSelector(BodySide.LEFT, config)

        // Create 4 BDCs = 3 complete segments
        for (i in 0..3) {
            val bdc = createExtremumEvent(PedalExtremum.BDC, i * 30L, i * 990L)
            selector.processEvent(bdc)
        }

        assertEquals(3, selector.getSegmentCount())
    }

    @Test
    fun `segments have sequential cycle numbers`() {
        val selector = KeyFrameSelector(BodySide.LEFT)

        for (i in 0..4) {
            val bdc = createExtremumEvent(PedalExtremum.BDC, i * 30L, i * 990L)
            selector.processEvent(bdc)
        }

        val segments = selector.getCompletedSegments()
        for (i in segments.indices) {
            assertEquals(i, segments[i].cycleNumber)
        }
    }

    // ==================== Side Filtering Tests ====================

    @Test
    fun `ignores events from wrong side`() {
        val leftSelector = KeyFrameSelector(BodySide.LEFT)

        val rightBdc1 = PedalExtremumEvent(
            type = PedalExtremum.BDC,
            frameNumber = 10,
            timestampMs = 330,
            ankleY = 0.7f,
            side = BodySide.RIGHT,
            confidence = 0.9f
        )

        val rightBdc2 = PedalExtremumEvent(
            type = PedalExtremum.BDC,
            frameNumber = 40,
            timestampMs = 1320,
            ankleY = 0.7f,
            side = BodySide.RIGHT,
            confidence = 0.9f
        )

        leftSelector.processEvent(rightBdc1)
        leftSelector.processEvent(rightBdc2)

        assertEquals(0, leftSelector.getSegmentCount())
    }

    @Test
    fun `processes events from correct side`() {
        val leftSelector = KeyFrameSelector(BodySide.LEFT)

        val leftBdc1 = createExtremumEvent(PedalExtremum.BDC, 10, 330, BodySide.LEFT)
        val leftBdc2 = createExtremumEvent(PedalExtremum.BDC, 40, 1320, BodySide.LEFT)

        leftSelector.processEvent(leftBdc1)
        leftSelector.processEvent(leftBdc2)

        assertEquals(1, leftSelector.getSegmentCount())
    }

    // ==================== PoseFrame Association Tests ====================

    @Test
    fun `associates pose frame with key frame`() {
        val selector = KeyFrameSelector(BodySide.LEFT)
        val poseFrame = createPoseFrame(10, 0.7f, BodySide.LEFT)

        val bdc1 = createExtremumEvent(PedalExtremum.BDC, 10, 330)
        val bdc2 = createExtremumEvent(PedalExtremum.BDC, 40, 1320)

        selector.processEvent(bdc1, poseFrame)
        selector.processEvent(bdc2)

        val segment = selector.getCompletedSegments().first()
        assertNotNull(segment.startFrame.poseFrame)
        assertEquals(10L, segment.startFrame.poseFrame?.frameNumber)
    }

    @Test
    fun `key frame without pose frame has null poseFrame`() {
        val selector = KeyFrameSelector(BodySide.LEFT)

        val bdc1 = createExtremumEvent(PedalExtremum.BDC, 10, 330)
        val bdc2 = createExtremumEvent(PedalExtremum.BDC, 40, 1320)

        selector.processEvent(bdc1)
        selector.processEvent(bdc2)

        val segment = selector.getCompletedSegments().first()
        assertNull(segment.startFrame.poseFrame)
        assertNull(segment.endFrame.poseFrame)
    }

    // ==================== CycleSegment Tests ====================

    @Test
    fun `cycle segment calculates frame count correctly`() {
        val selector = KeyFrameSelector(BodySide.LEFT)

        val bdc1 = createExtremumEvent(PedalExtremum.BDC, 10, 330)
        val bdc2 = createExtremumEvent(PedalExtremum.BDC, 40, 1320)

        selector.processEvent(bdc1)
        selector.processEvent(bdc2)

        val segment = selector.getCompletedSegments().first()
        assertEquals(31L, segment.frameCount) // 40 - 10 + 1
    }

    @Test
    fun `cycle segment calculates duration correctly`() {
        val selector = KeyFrameSelector(BodySide.LEFT)

        val bdc1 = createExtremumEvent(PedalExtremum.BDC, 10, 1000)
        val bdc2 = createExtremumEvent(PedalExtremum.BDC, 40, 2500)

        selector.processEvent(bdc1)
        selector.processEvent(bdc2)

        val segment = selector.getCompletedSegments().first()
        assertEquals(1500L, segment.durationMs)
    }

    @Test
    fun `cycle segment returns all key frames in order`() {
        val config = KeyFrameSelectorConfig(captureIntermediateFrames = true)
        val selector = KeyFrameSelector(BodySide.LEFT, config)

        val bdc1 = createExtremumEvent(PedalExtremum.BDC, 10, 330)
        val tdc = createExtremumEvent(PedalExtremum.TDC, 25, 825)
        val bdc2 = createExtremumEvent(PedalExtremum.BDC, 40, 1320)

        selector.processEvent(bdc1)
        selector.processEvent(tdc)
        selector.processEvent(bdc2)

        val segment = selector.getCompletedSegments().first()
        val allFrames = segment.allKeyFrames

        assertEquals(3, allFrames.size)
        assertEquals(10L, allFrames[0].frameNumber)
        assertEquals(25L, allFrames[1].frameNumber)
        assertEquals(40L, allFrames[2].frameNumber)
    }

    @Test
    fun `BDC-to-BDC segment has BDC frame`() {
        val config = KeyFrameSelectorConfig(segmentationType = SegmentationType.BDC_TO_BDC)
        val selector = KeyFrameSelector(BodySide.LEFT, config)

        val bdc1 = createExtremumEvent(PedalExtremum.BDC, 10, 330)
        val bdc2 = createExtremumEvent(PedalExtremum.BDC, 40, 1320)

        selector.processEvent(bdc1)
        selector.processEvent(bdc2)

        val segment = selector.getCompletedSegments().first()
        assertNotNull(segment.bdcFrame)
        assertEquals(10L, segment.bdcFrame?.frameNumber)
    }

    @Test
    fun `TDC-to-TDC segment has TDC frame`() {
        val config = KeyFrameSelectorConfig(segmentationType = SegmentationType.TDC_TO_TDC)
        val selector = KeyFrameSelector(BodySide.LEFT, config)

        val tdc1 = createExtremumEvent(PedalExtremum.TDC, 10, 330)
        val tdc2 = createExtremumEvent(PedalExtremum.TDC, 40, 1320)

        selector.processEvent(tdc1)
        selector.processEvent(tdc2)

        val segment = selector.getCompletedSegments().first()
        assertNotNull(segment.tdcFrame)
        assertEquals(10L, segment.tdcFrame?.frameNumber)
    }

    @Test
    fun `findBdcFrame returns BDC from intermediate frames`() {
        val config = KeyFrameSelectorConfig(
            segmentationType = SegmentationType.TDC_TO_TDC,
            captureIntermediateFrames = true
        )
        val selector = KeyFrameSelector(BodySide.LEFT, config)

        val tdc1 = createExtremumEvent(PedalExtremum.TDC, 10, 330)
        val bdc = createExtremumEvent(PedalExtremum.BDC, 25, 825)
        val tdc2 = createExtremumEvent(PedalExtremum.TDC, 40, 1320)

        selector.processEvent(tdc1)
        selector.processEvent(bdc)
        selector.processEvent(tdc2)

        val segment = selector.getCompletedSegments().first()
        val bdcFrame = segment.findBdcFrame()

        assertNotNull(bdcFrame)
        assertEquals(25L, bdcFrame?.frameNumber)
    }

    @Test
    fun `findTdcFrame returns TDC from intermediate frames`() {
        val config = KeyFrameSelectorConfig(
            segmentationType = SegmentationType.BDC_TO_BDC,
            captureIntermediateFrames = true
        )
        val selector = KeyFrameSelector(BodySide.LEFT, config)

        val bdc1 = createExtremumEvent(PedalExtremum.BDC, 10, 330)
        val tdc = createExtremumEvent(PedalExtremum.TDC, 25, 825)
        val bdc2 = createExtremumEvent(PedalExtremum.BDC, 40, 1320)

        selector.processEvent(bdc1)
        selector.processEvent(tdc)
        selector.processEvent(bdc2)

        val segment = selector.getCompletedSegments().first()
        val tdcFrame = segment.findTdcFrame()

        assertNotNull(tdcFrame)
        assertEquals(25L, tdcFrame?.frameNumber)
    }

    // ==================== Reset Tests ====================

    @Test
    fun `reset clears all segments`() {
        val selector = KeyFrameSelector(BodySide.LEFT)

        val bdc1 = createExtremumEvent(PedalExtremum.BDC, 10, 330)
        val bdc2 = createExtremumEvent(PedalExtremum.BDC, 40, 1320)

        selector.processEvent(bdc1)
        selector.processEvent(bdc2)
        assertEquals(1, selector.getSegmentCount())

        selector.reset()
        assertEquals(0, selector.getSegmentCount())
        assertNull(selector.getLastSegment())
    }

    @Test
    fun `reset allows new segments to be recorded`() {
        val selector = KeyFrameSelector(BodySide.LEFT)

        val bdc1 = createExtremumEvent(PedalExtremum.BDC, 10, 330)
        val bdc2 = createExtremumEvent(PedalExtremum.BDC, 40, 1320)

        selector.processEvent(bdc1)
        selector.processEvent(bdc2)
        selector.reset()

        val bdc3 = createExtremumEvent(PedalExtremum.BDC, 50, 1650)
        val bdc4 = createExtremumEvent(PedalExtremum.BDC, 80, 2640)

        selector.processEvent(bdc3)
        selector.processEvent(bdc4)

        assertEquals(1, selector.getSegmentCount())
        assertEquals(0, selector.getCompletedSegments().first().cycleNumber)
    }

    // ==================== processEvents (Batch) Tests ====================

    @Test
    fun `processEvents handles multiple events`() {
        val selector = KeyFrameSelector(BodySide.LEFT)

        val events = listOf(
            createExtremumEvent(PedalExtremum.BDC, 10, 330),
            createExtremumEvent(PedalExtremum.TDC, 25, 825),
            createExtremumEvent(PedalExtremum.BDC, 40, 1320)
        )

        selector.processEvents(events)

        assertEquals(1, selector.getSegmentCount())
    }

    @Test
    fun `processEvents filters by side`() {
        val leftSelector = KeyFrameSelector(BodySide.LEFT)

        val events = listOf(
            createExtremumEvent(PedalExtremum.BDC, 10, 330, BodySide.LEFT),
            createExtremumEvent(PedalExtremum.BDC, 20, 660, BodySide.RIGHT),
            createExtremumEvent(PedalExtremum.BDC, 40, 1320, BodySide.LEFT)
        )

        leftSelector.processEvents(events)

        assertEquals(1, leftSelector.getSegmentCount())
    }

    // ==================== Static Analysis Methods Tests ====================

    @Test
    fun `analyzeFrameSequence detects segments from pose frames`() {
        val frames = createSyntheticCycleFrames(numFrames = 90, cycles = 2f)

        val segments = KeyFrameSelector.analyzeFrameSequence(
            frames = frames,
            side = BodySide.LEFT
        )

        assertTrue("Should detect at least 1 segment", segments.isNotEmpty())
        for (segment in segments) {
            assertEquals(BodySide.LEFT, segment.side)
        }
    }

    @Test
    fun `analyzeAnklePositions detects segments from ankle data`() {
        val positions = generateSineWave(numFrames = 90, cycles = 2f)

        val segments = KeyFrameSelector.analyzeAnklePositions(
            anklePositions = positions,
            side = BodySide.LEFT
        )

        assertTrue("Should detect at least 1 segment", segments.isNotEmpty())
    }

    @Test
    fun `analyzeFrameSequence respects configuration`() {
        val frames = createSyntheticCycleFrames(numFrames = 90, cycles = 2f)

        val config = KeyFrameSelectorConfig(
            segmentationType = SegmentationType.TDC_TO_TDC,
            captureIntermediateFrames = false
        )

        val segments = KeyFrameSelector.analyzeFrameSequence(
            frames = frames,
            side = BodySide.LEFT,
            selectorConfig = config
        )

        for (segment in segments) {
            assertEquals("Start frame should be TDC", PedalExtremum.TDC, segment.startFrame.type)
            assertEquals("End frame should be TDC", PedalExtremum.TDC, segment.endFrame.type)
            assertEquals(0, segment.intermediateFrames.size)
        }
    }

    // ==================== getLastSegment Tests ====================

    @Test
    fun `getLastSegment returns null when no segments`() {
        assertNull(selector.getLastSegment())
    }

    @Test
    fun `getLastSegment returns most recent segment`() {
        val bdc1 = createExtremumEvent(PedalExtremum.BDC, 10, 330)
        val bdc2 = createExtremumEvent(PedalExtremum.BDC, 40, 1320)
        val bdc3 = createExtremumEvent(PedalExtremum.BDC, 70, 2310)

        selector.processEvent(bdc1)
        selector.processEvent(bdc2)
        selector.processEvent(bdc3)

        val lastSegment = selector.getLastSegment()
        assertNotNull(lastSegment)
        assertEquals(1, lastSegment?.cycleNumber)
        assertEquals(40L, lastSegment?.startFrame?.frameNumber)
        assertEquals(70L, lastSegment?.endFrame?.frameNumber)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `handles single extremum without creating segment`() {
        val bdc = createExtremumEvent(PedalExtremum.BDC, 10, 330)
        selector.processEvent(bdc)

        assertEquals(0, selector.getSegmentCount())
    }

    @Test
    fun `handles empty event list`() {
        selector.processEvents(emptyList())
        assertEquals(0, selector.getSegmentCount())
    }

    @Test
    fun `segment side matches selector side`() {
        val rightSelector = KeyFrameSelector(BodySide.RIGHT)

        val bdc1 = createExtremumEvent(PedalExtremum.BDC, 10, 330, BodySide.RIGHT)
        val bdc2 = createExtremumEvent(PedalExtremum.BDC, 40, 1320, BodySide.RIGHT)

        rightSelector.processEvent(bdc1)
        rightSelector.processEvent(bdc2)

        val segment = rightSelector.getCompletedSegments().first()
        assertEquals(BodySide.RIGHT, segment.side)
    }

    // ==================== Helper Functions ====================

    private fun createExtremumEvent(
        type: PedalExtremum,
        frameNumber: Long,
        timestampMs: Long,
        side: BodySide = BodySide.LEFT
    ): PedalExtremumEvent {
        return PedalExtremumEvent(
            type = type,
            frameNumber = frameNumber,
            timestampMs = timestampMs,
            ankleY = if (type == PedalExtremum.BDC) 0.7f else 0.3f,
            side = side,
            confidence = 0.9f
        )
    }

    private fun createSyntheticCycleFrames(
        numFrames: Int,
        cycles: Float
    ): List<PoseFrame> {
        val positions = generateSineWave(numFrames = numFrames, cycles = cycles)
        return positions.map { pos ->
            createPoseFrame(
                frameNumber = pos.frameNumber,
                ankleY = pos.y,
                side = BodySide.LEFT,
                visibility = pos.visibility
            )
        }
    }
}
