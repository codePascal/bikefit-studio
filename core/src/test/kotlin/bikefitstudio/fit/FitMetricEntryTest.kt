package bikefitstudio.fit

import org.junit.Assert.*
import org.junit.Test

class FitMetricEntryTest {

    // ==================== Status Calculation Tests ====================

    @Test
    fun `status is IN_RANGE for value in middle of range`() {
        val range = MetricRange(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 145f,
            typicalMax = 155f
        )

        val entry = FitMetricEntry(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            measuredValue = 150f,
            typicalRange = range
        )

        assertEquals(MetricStatus.IN_RANGE, entry.status)
    }

    @Test
    fun `status is BORDERLINE for value near lower boundary`() {
        val range = MetricRange(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 145f,
            typicalMax = 155f
        )

        // Range width is 10, 10% is 1.0
        // 145 + 0.5 = 145.5 is within 10% of lower boundary
        val entry = FitMetricEntry(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            measuredValue = 145.5f,
            typicalRange = range
        )

        assertEquals(MetricStatus.BORDERLINE, entry.status)
    }

    @Test
    fun `status is BORDERLINE for value near upper boundary`() {
        val range = MetricRange(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 145f,
            typicalMax = 155f
        )

        // Range width is 10, 10% is 1.0
        // 155 - 0.5 = 154.5 is within 10% of upper boundary
        val entry = FitMetricEntry(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            measuredValue = 154.5f,
            typicalRange = range
        )

        assertEquals(MetricStatus.BORDERLINE, entry.status)
    }

    @Test
    fun `status is OUT_OF_RANGE for value below range`() {
        val range = MetricRange(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 145f,
            typicalMax = 155f
        )

        val entry = FitMetricEntry(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            measuredValue = 140f,
            typicalRange = range
        )

        assertEquals(MetricStatus.OUT_OF_RANGE, entry.status)
    }

    @Test
    fun `status is OUT_OF_RANGE for value above range`() {
        val range = MetricRange(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 145f,
            typicalMax = 155f
        )

        val entry = FitMetricEntry(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            measuredValue = 160f,
            typicalRange = range
        )

        assertEquals(MetricStatus.OUT_OF_RANGE, entry.status)
    }

    @Test
    fun `status is null when no typical range is available`() {
        val entry = FitMetricEntry(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            measuredValue = 150f,
            typicalRange = null
        )

        assertNull(entry.status)
    }

    @Test
    fun `status is BORDERLINE exactly at lower boundary`() {
        val range = MetricRange(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 145f,
            typicalMax = 155f
        )

        val entry = FitMetricEntry(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            measuredValue = 145f,
            typicalRange = range
        )

        assertEquals(MetricStatus.BORDERLINE, entry.status)
    }

    @Test
    fun `status is BORDERLINE exactly at upper boundary`() {
        val range = MetricRange(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 145f,
            typicalMax = 155f
        )

        val entry = FitMetricEntry(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            measuredValue = 155f,
            typicalRange = range
        )

        assertEquals(MetricStatus.BORDERLINE, entry.status)
    }

    // ==================== Unit and Text Formatting Tests ====================

    @Test
    fun `unit returns correct unit for angle metrics`() {
        val entry = FitMetricEntry(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            measuredValue = 150f
        )

        assertEquals("degrees", entry.unit())
    }

    @Test
    fun `unit returns correct unit for KOPS offset`() {
        val entry = FitMetricEntry(
            metricType = FitMetricType.KOPS_OFFSET,
            measuredValue = 0.01f
        )

        assertEquals("normalized", entry.unit())
    }

    @Test
    fun `rangeText returns formatted string with range and unit`() {
        val range = MetricRange(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 145f,
            typicalMax = 155f
        )

        val entry = FitMetricEntry(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            measuredValue = 150f,
            typicalRange = range
        )

        assertEquals("145.0 - 155.0 degrees", entry.rangeText())
    }

    @Test
    fun `rangeText returns N_A when no range available`() {
        val entry = FitMetricEntry(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            measuredValue = 150f,
            typicalRange = null
        )

        assertEquals("N/A", entry.rangeText())
    }

    @Test
    fun `valueText returns formatted string with value and unit`() {
        val entry = FitMetricEntry(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            measuredValue = 150f
        )

        assertEquals("150.0 degrees", entry.valueText())
    }

    // ==================== Deviation Tests ====================

    @Test
    fun `deviation returns zero for value in range`() {
        val range = MetricRange(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 145f,
            typicalMax = 155f
        )

        val entry = FitMetricEntry(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            measuredValue = 150f,
            typicalRange = range
        )

        assertEquals(0f, entry.deviation()!!, 0.001f)
    }

    @Test
    fun `deviation returns negative for value below range`() {
        val range = MetricRange(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 145f,
            typicalMax = 155f
        )

        val entry = FitMetricEntry(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            measuredValue = 140f,
            typicalRange = range
        )

        assertEquals(-5f, entry.deviation()!!, 0.001f)
    }

    @Test
    fun `deviation returns positive for value above range`() {
        val range = MetricRange(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 145f,
            typicalMax = 155f
        )

        val entry = FitMetricEntry(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            measuredValue = 160f,
            typicalRange = range
        )

        assertEquals(5f, entry.deviation()!!, 0.001f)
    }

    @Test
    fun `deviation returns null when no range available`() {
        val entry = FitMetricEntry(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            measuredValue = 150f,
            typicalRange = null
        )

        assertNull(entry.deviation())
    }

    // ==================== Helper Method Tests ====================

    @Test
    fun `hasTypicalRange returns true when range is available`() {
        val range = MetricRange(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 145f,
            typicalMax = 155f
        )

        val entry = FitMetricEntry(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            measuredValue = 150f,
            typicalRange = range
        )

        assertTrue(entry.hasTypicalRange())
    }

    @Test
    fun `hasTypicalRange returns false when range is not available`() {
        val entry = FitMetricEntry(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            measuredValue = 150f,
            typicalRange = null
        )

        assertFalse(entry.hasTypicalRange())
    }

    // ==================== Factory Method Tests ====================

    @Test
    fun `create factory method looks up range from RangeLookup`() {
        val entry = FitMetricEntry.create(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            measuredValue = 150f,
            discipline = CyclingDiscipline.ROAD
        )

        assertNotNull(entry.typicalRange)
        assertEquals(FitMetricType.KNEE_ANGLE_AT_BDC, entry.metricType)
        assertEquals(150f, entry.measuredValue, 0.001f)
        assertEquals(CyclingDiscipline.ROAD, entry.discipline)
        assertEquals(145f, entry.typicalRange!!.typicalMin, 0.001f)
        assertEquals(155f, entry.typicalRange!!.typicalMax, 0.001f)
    }

    @Test
    fun `create factory method works for different disciplines`() {
        val roadEntry = FitMetricEntry.create(
            metricType = FitMetricType.TORSO_ANGLE,
            measuredValue = 35f,
            discipline = CyclingDiscipline.ROAD
        )

        val ttEntry = FitMetricEntry.create(
            metricType = FitMetricType.TORSO_ANGLE,
            measuredValue = 20f,
            discipline = CyclingDiscipline.TT
        )

        // Road should have more upright (higher) torso angle range than TT
        assertTrue(roadEntry.typicalRange!!.typicalMin > ttEntry.typicalRange!!.typicalMin)
    }

    @Test
    fun `createAll factory method creates entries for multiple metrics`() {
        val measuredValues = mapOf(
            FitMetricType.KNEE_ANGLE_AT_BDC to 150f,
            FitMetricType.TORSO_ANGLE to 35f,
            FitMetricType.KOPS_OFFSET to 0.01f
        )

        val entries = FitMetricEntry.createAll(measuredValues, CyclingDiscipline.ROAD)

        assertEquals(3, entries.size)
        
        // Verify each entry has the correct metric type and value
        val kneeEntry = entries.find { it.metricType == FitMetricType.KNEE_ANGLE_AT_BDC }
        assertNotNull(kneeEntry)
        assertEquals(150f, kneeEntry!!.measuredValue, 0.001f)
        assertNotNull(kneeEntry.typicalRange)

        val torsoEntry = entries.find { it.metricType == FitMetricType.TORSO_ANGLE }
        assertNotNull(torsoEntry)
        assertEquals(35f, torsoEntry!!.measuredValue, 0.001f)
        assertNotNull(torsoEntry.typicalRange)

        val kopsEntry = entries.find { it.metricType == FitMetricType.KOPS_OFFSET }
        assertNotNull(kopsEntry)
        assertEquals(0.01f, kopsEntry!!.measuredValue, 0.001f)
        assertNotNull(kopsEntry.typicalRange)
    }

    @Test
    fun `createAll returns empty list for empty input`() {
        val entries = FitMetricEntry.createAll(emptyMap(), CyclingDiscipline.ROAD)
        assertTrue(entries.isEmpty())
    }

    // ==================== Integration Tests with Different Metric Types ====================

    @Test
    fun `works with KOPS offset normalized values`() {
        val range = MetricRange(
            metricType = FitMetricType.KOPS_OFFSET,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = -0.015f,
            typicalMax = 0.015f
        )

        val entry = FitMetricEntry(
            metricType = FitMetricType.KOPS_OFFSET,
            measuredValue = 0f,
            typicalRange = range
        )

        assertEquals(MetricStatus.IN_RANGE, entry.status)
        assertEquals("normalized", entry.unit())
    }

    @Test
    fun `works with hip angle metric`() {
        val range = MetricRange(
            metricType = FitMetricType.HIP_ANGLE_AT_TDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 45f,
            typicalMax = 65f
        )

        val entry = FitMetricEntry(
            metricType = FitMetricType.HIP_ANGLE_AT_TDC,
            measuredValue = 55f,
            typicalRange = range
        )

        assertEquals(MetricStatus.IN_RANGE, entry.status)
        assertEquals("degrees", entry.unit())
    }

    @Test
    fun `works with ankle angle metric`() {
        val range = MetricRange(
            metricType = FitMetricType.ANKLE_ANGLE_AT_BDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 85f,
            typicalMax = 105f
        )

        val entry = FitMetricEntry(
            metricType = FitMetricType.ANKLE_ANGLE_AT_BDC,
            measuredValue = 95f,
            typicalRange = range
        )

        assertEquals(MetricStatus.IN_RANGE, entry.status)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `borderline calculation works with narrow ranges`() {
        val range = MetricRange(
            metricType = FitMetricType.KOPS_OFFSET,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = -0.015f,
            typicalMax = 0.015f
        )

        // Range width is 0.03, 10% is 0.003
        // Value at -0.013 is within 0.003 of lower boundary (-0.015)
        val entry = FitMetricEntry(
            metricType = FitMetricType.KOPS_OFFSET,
            measuredValue = -0.013f,
            typicalRange = range
        )

        assertEquals(MetricStatus.BORDERLINE, entry.status)
    }

    @Test
    fun `data class properties are preserved`() {
        val range = MetricRange(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 145f,
            typicalMax = 155f
        )

        val entry = FitMetricEntry(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            measuredValue = 150f,
            typicalRange = range,
            discipline = CyclingDiscipline.ROAD
        )

        assertEquals(FitMetricType.KNEE_ANGLE_AT_BDC, entry.metricType)
        assertEquals(150f, entry.measuredValue, 0.001f)
        assertEquals(range, entry.typicalRange)
        assertEquals(CyclingDiscipline.ROAD, entry.discipline)
    }

    @Test
    fun `can be created without discipline`() {
        val range = MetricRange(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            discipline = CyclingDiscipline.ROAD,
            typicalMin = 145f,
            typicalMax = 155f
        )

        val entry = FitMetricEntry(
            metricType = FitMetricType.KNEE_ANGLE_AT_BDC,
            measuredValue = 150f,
            typicalRange = range
        )

        assertNull(entry.discipline)
        assertEquals(MetricStatus.IN_RANGE, entry.status)
    }
}
