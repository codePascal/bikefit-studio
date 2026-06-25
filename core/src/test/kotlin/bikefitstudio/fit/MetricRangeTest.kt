package bikefitstudio.fit

import org.junit.Assert.*
import org.junit.Test

class MetricRangeTest {

    @Test
    fun `range property returns correct ClosedFloatingPointRange`() {
        val metricRange = MetricRange(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 145f,
            typicalMax = 155f
        )

        val range = metricRange.range
        assertEquals(145f, range.start, 0.001f)
        assertEquals(155f, range.endInclusive, 0.001f)
    }

    @Test
    fun `midpoint calculates correctly`() {
        val metricRange = MetricRange(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 140f,
            typicalMax = 160f
        )

        assertEquals(150f, metricRange.midpoint(), 0.001f)
    }

    @Test
    fun `width calculates correctly`() {
        val metricRange = MetricRange(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 145f,
            typicalMax = 155f
        )

        assertEquals(10f, metricRange.width(), 0.001f)
    }

    @Test
    fun `contains returns true for value in range`() {
        val metricRange = MetricRange(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 145f,
            typicalMax = 155f
        )

        assertTrue(metricRange.contains(150f))
        assertTrue(metricRange.contains(145f))
        assertTrue(metricRange.contains(155f))
    }

    @Test
    fun `contains returns false for value outside range`() {
        val metricRange = MetricRange(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 145f,
            typicalMax = 155f
        )

        assertFalse(metricRange.contains(140f))
        assertFalse(metricRange.contains(160f))
    }

    @Test
    fun `isBelowRange returns true for values below`() {
        val metricRange = MetricRange(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 145f,
            typicalMax = 155f
        )

        assertTrue(metricRange.isBelowRange(140f))
        assertTrue(metricRange.isBelowRange(144f))
        assertFalse(metricRange.isBelowRange(145f))
        assertFalse(metricRange.isBelowRange(150f))
    }

    @Test
    fun `isAboveRange returns true for values above`() {
        val metricRange = MetricRange(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 145f,
            typicalMax = 155f
        )

        assertTrue(metricRange.isAboveRange(160f))
        assertTrue(metricRange.isAboveRange(156f))
        assertFalse(metricRange.isAboveRange(155f))
        assertFalse(metricRange.isAboveRange(150f))
    }

    @Test
    fun `deviationFromRange returns zero for values in range`() {
        val metricRange = MetricRange(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 145f,
            typicalMax = 155f
        )

        assertEquals(0f, metricRange.deviationFromRange(150f), 0.001f)
        assertEquals(0f, metricRange.deviationFromRange(145f), 0.001f)
        assertEquals(0f, metricRange.deviationFromRange(155f), 0.001f)
    }

    @Test
    fun `deviationFromRange returns negative for values below`() {
        val metricRange = MetricRange(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 145f,
            typicalMax = 155f
        )

        assertEquals(-5f, metricRange.deviationFromRange(140f), 0.001f)
        assertEquals(-15f, metricRange.deviationFromRange(130f), 0.001f)
    }

    @Test
    fun `deviationFromRange returns positive for values above`() {
        val metricRange = MetricRange(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 145f,
            typicalMax = 155f
        )

        assertEquals(5f, metricRange.deviationFromRange(160f), 0.001f)
        assertEquals(10f, metricRange.deviationFromRange(165f), 0.001f)
    }

    @Test
    fun `MetricRange works with KOPS offset normalized values`() {
        val metricRange = MetricRange(
            metricType = FitMetricType.KOPS_OFFSET,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = -0.015f,
            typicalMax = 0.015f
        )

        assertTrue(metricRange.contains(0f))
        assertTrue(metricRange.contains(0.01f))
        assertTrue(metricRange.contains(-0.01f))
        assertFalse(metricRange.contains(0.02f))
        assertFalse(metricRange.contains(-0.02f))
    }

    @Test
    fun `description is preserved`() {
        val description = "Test description for range"
        val metricRange = MetricRange(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 145f,
            typicalMax = 155f,
            description = description
        )

        assertEquals(description, metricRange.description)
    }

    @Test
    fun `metricType and discipline are preserved`() {
        val metricRange = MetricRange(
            metricType = FitMetricType.TORSO_ANGLE,
            discipline = CyclingDiscipline.TT,
            typicalMin = 15f,
            typicalMax = 30f
        )

        assertEquals(FitMetricType.TORSO_ANGLE, metricRange.metricType)
        assertEquals(CyclingDiscipline.TT, metricRange.discipline)
    }
}
