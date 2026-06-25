package bikefitstudio.biomechanics

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for CrankPositionDetector.
 *
 * Tests cover:
 * - TDC detection (355-5° range with wraparound)
 * - BDC detection (175-185° range)  
 * - 3 O'Clock detection (85-95° range)
 * - Confidence calculations
 */
class CrankPositionDetectorTest {

    @Test
    fun testTdcDetectionAt0Degrees() {
        val event = CrankPositionDetector.detectPosition(0f, 100L, 1000L, BodySide.LEFT)
        assertNotNull(event)
        assertTrue(event?.position == CrankPosition.TDC)
        assertTrue(event?.confidence!! >= 0.5f && event.confidence <= 1.0f)
    }

    @Test
    fun testTdcDetectionAt355Degrees() {
        val event = CrankPositionDetector.detectPosition(355f, 100L, 1000L, BodySide.LEFT)
        assertNotNull(event)
        assertTrue(event?.position == CrankPosition.TDC)
    }

    @Test
    fun testTdcDetectionAt5Degrees() {
        val event = CrankPositionDetector.detectPosition(5f, 100L, 1000L, BodySide.LEFT)
        assertNotNull(event)
        assertTrue(event?.position == CrankPosition.TDC)
    }

    @Test
    fun testTdcDetectionOutOfRange() {
        val event = CrankPositionDetector.detectPosition(90f, 100L, 1000L, BodySide.LEFT)
        assertNotNull(event)
        assertNotEquals(CrankPosition.TDC, event?.position)
    }

    @Test
    fun testBdcDetectionAt180Degrees() {
        val event = CrankPositionDetector.detectPosition(180f, 100L, 1000L, BodySide.LEFT)
        assertNotNull(event)
        assertTrue(event?.position == CrankPosition.BDC)
        assertTrue(event?.confidence!! >= 0.9f)
    }

    @Test
    fun testBdcDetectionAt175Degrees() {
        val event = CrankPositionDetector.detectPosition(175f, 100L, 1000L, BodySide.LEFT)
        assertNotNull(event)
        assertTrue(event?.position == CrankPosition.BDC)
    }

    @Test
    fun testBdcDetectionAt185Degrees() {
        val event = CrankPositionDetector.detectPosition(185f, 100L, 1000L, BodySide.LEFT)
        assertNotNull(event)
        assertTrue(event?.position == CrankPosition.BDC)
    }

    @Test
    fun testBdcDetectionOutOfRange() {
        val event = CrankPositionDetector.detectPosition(190f, 100L, 1000L, BodySide.LEFT)
        assertNull(event)  // 190° is outside all ranges
    }

    @Test
    fun testThreeOClockDetectionAt90Degrees() {
        val event = CrankPositionDetector.detectPosition(90f, 100L, 1000L, BodySide.LEFT)
        assertNotNull(event)
        assertTrue(event?.position == CrankPosition.THREE_O_CLOCK)
        assertTrue(event?.confidence!! >= 0.9f)
    }

    @Test
    fun testThreeOClockDetectionAt85Degrees() {
        val event = CrankPositionDetector.detectPosition(85f, 100L, 1000L, BodySide.LEFT)
        assertNotNull(event)
        assertTrue(event?.position == CrankPosition.THREE_O_CLOCK)
    }

    @Test
    fun testThreeOClockDetectionAt95Degrees() {
        val event = CrankPositionDetector.detectPosition(95f, 100L, 1000L, BodySide.LEFT)
        assertNotNull(event)
        assertTrue(event?.position == CrankPosition.THREE_O_CLOCK)
    }

    @Test
    fun testThreeOClockDetectionOutOfRange() {
        val event = CrankPositionDetector.detectPosition(100f, 100L, 1000L, BodySide.LEFT)
        assertNull(event)  // 100° is outside all ranges
    }

    @Test
    fun testNoDetectionOutsideAllRanges() {
        val event = CrankPositionDetector.detectPosition(270f, 100L, 1000L, BodySide.LEFT)
        assertNull(event)
    }

    @Test
    fun testTdcConfidenceGradient() {
        val event0 = CrankPositionDetector.detectPosition(0f, 100L, 1000L, BodySide.LEFT)!!
        val event3 = CrankPositionDetector.detectPosition(3f, 100L, 1000L, BodySide.LEFT)!!
        val event5 = CrankPositionDetector.detectPosition(5f, 100L, 1000L, BodySide.LEFT)!!
        
        assertTrue(event0.confidence >= event3.confidence)
        assertTrue(event3.confidence >= event5.confidence)
    }

    @Test
    fun testBdcConfidenceGradient() {
        val event175 = CrankPositionDetector.detectPosition(175f, 100L, 1000L, BodySide.LEFT)!!
        val event180 = CrankPositionDetector.detectPosition(180f, 100L, 1000L, BodySide.LEFT)!!
        val event185 = CrankPositionDetector.detectPosition(185f, 100L, 1000L, BodySide.LEFT)!!
        
        assertTrue(event180.confidence >= event175.confidence)
        assertTrue(event180.confidence >= event185.confidence)
    }

    @Test
    fun testThreeOClockConfidenceGradient() {
        val event85 = CrankPositionDetector.detectPosition(85f, 100L, 1000L, BodySide.LEFT)!!
        val event90 = CrankPositionDetector.detectPosition(90f, 100L, 1000L, BodySide.LEFT)!!
        val event95 = CrankPositionDetector.detectPosition(95f, 100L, 1000L, BodySide.LEFT)!!
        
        assertTrue(event90.confidence >= event85.confidence)
        assertTrue(event90.confidence >= event95.confidence)
    }

    @Test
    fun testEventDataIntegrity() {
        val event = CrankPositionDetector.detectPosition(90f, 12345L, 50000L, BodySide.RIGHT)
        assertNotNull(event)
        assertEquals(12345L, event?.frameNumber)
        assertEquals(50000L, event?.timestampMs)
        assertTrue(event?.side == BodySide.RIGHT)
        assertEquals(90f, event?.crankAngleDegrees ?: 0f, 0.1f)
    }

    @Test
    fun testBothSidesProduceSameDetection() {
        val eventLeft = CrankPositionDetector.detectPosition(90f, 100L, 1000L, BodySide.LEFT)
        val eventRight = CrankPositionDetector.detectPosition(90f, 100L, 1000L, BodySide.RIGHT)
        
        assertNotNull(eventLeft)
        assertNotNull(eventRight)
        assertEquals(eventLeft?.position, eventRight?.position)
        assertEquals(eventLeft?.confidence!!, eventRight?.confidence!!, 0.01f)
    }

    @Test
    fun testTdcWraparoundLogic() {
        val event355 = CrankPositionDetector.detectPosition(355f, 100L, 1000L, BodySide.LEFT)
        val event1 = CrankPositionDetector.detectPosition(1f, 100L, 1000L, BodySide.LEFT)
        val event180 = CrankPositionDetector.detectPosition(180f, 100L, 1000L, BodySide.LEFT)
        
        assertTrue(event355?.position == CrankPosition.TDC)
        assertTrue(event1?.position == CrankPosition.TDC)
        assertNotEquals(CrankPosition.TDC, event180?.position)
    }

    @Test
    fun testConfidenceAlwaysInRange() {
        val angles = listOf(0f, 1f, 5f, 85f, 90f, 95f, 175f, 180f, 185f, 355f)
        
        for (angle in angles) {
            val event = CrankPositionDetector.detectPosition(angle, 100L, 1000L, BodySide.LEFT)
            if (event != null) {
                assertTrue("Confidence should be >= 0.5", event.confidence >= 0.5f)
                assertTrue("Confidence should be <= 1.0", event.confidence <= 1.0f)
            }
        }
    }

    @Test
    fun testPeakConfidenceValues() {
        val eventTdc = CrankPositionDetector.detectPosition(0f, 100L, 1000L, BodySide.LEFT)!!
        val eventBdc = CrankPositionDetector.detectPosition(180f, 100L, 1000L, BodySide.LEFT)!!
        val event3 = CrankPositionDetector.detectPosition(90f, 100L, 1000L, BodySide.LEFT)!!
        
        assertTrue(eventTdc.confidence >= 0.95f)
        assertTrue(eventBdc.confidence >= 0.95f)
        assertTrue(event3.confidence >= 0.95f)
    }

    @Test
    fun testDetectionSymmetryAroundTdc() {
        val event355 = CrankPositionDetector.detectPosition(355f, 100L, 1000L, BodySide.LEFT)!!
        val event5 = CrankPositionDetector.detectPosition(5f, 100L, 1000L, BodySide.LEFT)!!
        
        val diff = Math.abs(event355.confidence - event5.confidence)
        assertTrue("Confidence should be symmetric around TDC", diff < 0.02f)
    }

    @Test
    fun testDetectionSymmetryAroundBdc() {
        val event175 = CrankPositionDetector.detectPosition(175f, 100L, 1000L, BodySide.LEFT)!!
        val event185 = CrankPositionDetector.detectPosition(185f, 100L, 1000L, BodySide.LEFT)!!
        
        val diff = Math.abs(event175.confidence - event185.confidence)
        assertTrue("Confidence should be symmetric around BDC", diff < 0.02f)
    }

    @Test
    fun testEdgeCasesAt180Degrees() {
        val event = CrankPositionDetector.detectPosition(180f, 100L, 1000L, BodySide.LEFT)
        assertTrue(event?.position == CrankPosition.BDC)
    }

    @Test
    fun testMultipleAnglesInTdcRange() {
        val tdcAngles = listOf(0f, 1f, 2f, 3f, 4f, 5f, 355f, 356f, 357f, 358f, 359f)
        
        for (angle in tdcAngles) {
            val event = CrankPositionDetector.detectPosition(angle, 100L, 1000L, BodySide.LEFT)
            assertNotNull("Angle $angle should be detected as TDC", event)
            assertEquals("Angle $angle should be TDC", CrankPosition.TDC, event?.position)
        }
    }

    @Test
    fun testCrankPositionEventProperties() {
        val event = CrankPositionDetector.detectPosition(180f, 5432L, 12345L, BodySide.LEFT)!!
        
        assertTrue(event.position == CrankPosition.BDC)
        assertEquals(5432L, event.frameNumber)
        assertEquals(12345L, event.timestampMs)
        assertEquals(180f, event.crankAngleDegrees, 0.1f)
        assertTrue(event.side == BodySide.LEFT)
        assertTrue(event.confidence >= 0.5f)
        assertTrue(event.confidence <= 1.0f)
    }
}
