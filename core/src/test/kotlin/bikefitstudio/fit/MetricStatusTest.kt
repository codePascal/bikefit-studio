package bikefitstudio.fit

import org.junit.Assert.*
import org.junit.Test

class MetricStatusTest {

    @Test
    fun `displayText returns correct text for IN_RANGE`() {
        assertEquals("In Range", MetricStatus.IN_RANGE.displayText())
    }

    @Test
    fun `displayText returns correct text for BORDERLINE`() {
        assertEquals("Borderline", MetricStatus.BORDERLINE.displayText())
    }

    @Test
    fun `displayText returns correct text for OUT_OF_RANGE`() {
        assertEquals("Out of Range", MetricStatus.OUT_OF_RANGE.displayText())
    }

    @Test
    fun `emoji returns correct emoji for IN_RANGE`() {
        assertEquals("✅", MetricStatus.IN_RANGE.emoji())
    }

    @Test
    fun `emoji returns correct emoji for BORDERLINE`() {
        assertEquals("⚠️", MetricStatus.BORDERLINE.emoji())
    }

    @Test
    fun `emoji returns correct emoji for OUT_OF_RANGE`() {
        assertEquals("❌", MetricStatus.OUT_OF_RANGE.emoji())
    }

    @Test
    fun `all enum values have display text`() {
        for (status in MetricStatus.values()) {
            assertNotNull(status.displayText())
            assertTrue(status.displayText().isNotEmpty())
        }
    }

    @Test
    fun `all enum values have emoji`() {
        for (status in MetricStatus.values()) {
            assertNotNull(status.emoji())
            assertTrue(status.emoji().isNotEmpty())
        }
    }
}
