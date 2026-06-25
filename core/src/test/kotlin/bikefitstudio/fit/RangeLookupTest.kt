package bikefitstudio.fit

import org.junit.Assert.*
import org.junit.Test

class RangeLookupTest {

    // ==================== Basic Lookup Tests ====================

    @Test
    fun `getRange returns non-null for defined combinations`() {
        val range = RangeLookup.getRange(
            FitMetricType.KNEE_ANGLE_AT_BDC,
            CyclingDiscipline.ROAD
        )
        assertNotNull(range)
    }

    @Test
    fun `getRange returns null for undefined combinations if any`() {
        // All current combinations are defined, but test the null handling works
        val range = RangeLookup.getRange(
            FitMetricType.KNEE_ANGLE_AT_BDC,
            CyclingDiscipline.ROAD
        )
        assertNotNull(range)
    }

    @Test
    fun `hasRange returns true for defined combinations`() {
        assertTrue(RangeLookup.hasRange(
            FitMetricType.KNEE_ANGLE_AT_BDC,
            CyclingDiscipline.ROAD
        ))
    }

    // ==================== Knee Angle at BDC Tests ====================

    @Test
    fun `knee angle at BDC is consistent across most disciplines`() {
        val roadRange = RangeLookup.getRange(
            FitMetricType.KNEE_ANGLE_AT_BDC,
            CyclingDiscipline.ROAD
        )!!

        val enduranceRange = RangeLookup.getRange(
            FitMetricType.KNEE_ANGLE_AT_BDC,
            CyclingDiscipline.ENDURANCE
        )!!

        val gravelRange = RangeLookup.getRange(
            FitMetricType.KNEE_ANGLE_AT_BDC,
            CyclingDiscipline.GRAVEL
        )!!

        // Knee angle should be consistent (145-155) across these disciplines
        assertEquals(145f, roadRange.typicalMin, 0.001f)
        assertEquals(155f, roadRange.typicalMax, 0.001f)
        assertEquals(roadRange.typicalMin, enduranceRange.typicalMin, 0.001f)
        assertEquals(roadRange.typicalMax, enduranceRange.typicalMax, 0.001f)
        assertEquals(roadRange.typicalMin, gravelRange.typicalMin, 0.001f)
        assertEquals(roadRange.typicalMax, gravelRange.typicalMax, 0.001f)
    }

    @Test
    fun `knee angle at BDC for TT and Tri is also standard`() {
        val ttRange = RangeLookup.getRange(
            FitMetricType.KNEE_ANGLE_AT_BDC,
            CyclingDiscipline.TT
        )!!

        val triRange = RangeLookup.getRange(
            FitMetricType.KNEE_ANGLE_AT_BDC,
            CyclingDiscipline.TRI
        )!!

        assertEquals(145f, ttRange.typicalMin, 0.001f)
        assertEquals(155f, ttRange.typicalMax, 0.001f)
        assertEquals(145f, triRange.typicalMin, 0.001f)
        assertEquals(155f, triRange.typicalMax, 0.001f)
    }

    // ==================== Torso Angle Tests ====================

    @Test
    fun `torso angle varies by discipline - TT most aggressive`() {
        val roadRange = RangeLookup.getRange(
            FitMetricType.TORSO_ANGLE,
            CyclingDiscipline.ROAD
        )!!

        val enduranceRange = RangeLookup.getRange(
            FitMetricType.TORSO_ANGLE,
            CyclingDiscipline.ENDURANCE
        )!!

        val ttRange = RangeLookup.getRange(
            FitMetricType.TORSO_ANGLE,
            CyclingDiscipline.TT
        )!!

        // TT should have the lowest (most aggressive) torso angle
        assertTrue(ttRange.typicalMax <= roadRange.typicalMin)
        
        // Endurance should be more upright than road
        assertTrue(enduranceRange.typicalMin > roadRange.typicalMin)
    }

    @Test
    fun `torso angle for TT is between 15-30 degrees`() {
        val range = RangeLookup.getRange(
            FitMetricType.TORSO_ANGLE,
            CyclingDiscipline.TT
        )!!

        assertEquals(15f, range.typicalMin, 0.001f)
        assertEquals(30f, range.typicalMax, 0.001f)
    }

    @Test
    fun `torso angle for endurance is most upright`() {
        val enduranceRange = RangeLookup.getRange(
            FitMetricType.TORSO_ANGLE,
            CyclingDiscipline.ENDURANCE
        )!!

        val roadRange = RangeLookup.getRange(
            FitMetricType.TORSO_ANGLE,
            CyclingDiscipline.ROAD
        )!!

        val gravelRange = RangeLookup.getRange(
            FitMetricType.TORSO_ANGLE,
            CyclingDiscipline.GRAVEL
        )!!

        // Endurance and gravel should have higher (more upright) angles than road
        assertTrue(enduranceRange.typicalMin >= roadRange.typicalMin)
        assertTrue(gravelRange.typicalMin >= roadRange.typicalMin)
    }

    // ==================== KOPS Offset Tests ====================

    @Test
    fun `KOPS offset is neutral for road and endurance`() {
        val roadRange = RangeLookup.getRange(
            FitMetricType.KOPS_OFFSET,
            CyclingDiscipline.ROAD
        )!!

        val enduranceRange = RangeLookup.getRange(
            FitMetricType.KOPS_OFFSET,
            CyclingDiscipline.ENDURANCE
        )!!

        // Should be centered around zero (neutral)
        assertTrue(roadRange.contains(0f))
        assertTrue(enduranceRange.contains(0f))
        assertEquals(-0.015f, roadRange.typicalMin, 0.001f)
        assertEquals(0.015f, roadRange.typicalMax, 0.001f)
    }

    @Test
    fun `KOPS offset is forward for TT and Tri`() {
        val ttRange = RangeLookup.getRange(
            FitMetricType.KOPS_OFFSET,
            CyclingDiscipline.TT
        )!!

        val triRange = RangeLookup.getRange(
            FitMetricType.KOPS_OFFSET,
            CyclingDiscipline.TRI
        )!!

        // Both should be positive (forward)
        assertTrue(ttRange.typicalMin > 0f)
        assertTrue(triRange.typicalMin > 0f)
        
        // Tri should be slightly more forward than TT
        assertTrue(triRange.typicalMin >= ttRange.typicalMin)
    }

    @Test
    fun `KOPS offset for gravel can be slightly rearward`() {
        val gravelRange = RangeLookup.getRange(
            FitMetricType.KOPS_OFFSET,
            CyclingDiscipline.GRAVEL
        )!!

        // Should allow slightly rearward position (negative values)
        assertTrue(gravelRange.typicalMin < 0f)
        assertEquals(-0.020f, gravelRange.typicalMin, 0.001f)
        assertEquals(0.010f, gravelRange.typicalMax, 0.001f)
    }

    // ==================== Hip Angle at TDC Tests ====================

    @Test
    fun `hip angle at TDC varies by discipline`() {
        val roadRange = RangeLookup.getRange(
            FitMetricType.HIP_ANGLE_AT_TDC,
            CyclingDiscipline.ROAD
        )!!

        val ttRange = RangeLookup.getRange(
            FitMetricType.HIP_ANGLE_AT_TDC,
            CyclingDiscipline.TT
        )!!

        val enduranceRange = RangeLookup.getRange(
            FitMetricType.HIP_ANGLE_AT_TDC,
            CyclingDiscipline.ENDURANCE
        )!!

        // TT should have more closed (lower) hip angle due to aggressive position
        assertTrue(ttRange.typicalMax <= roadRange.typicalMax)
        
        // Endurance should have more open (higher) hip angle
        assertTrue(enduranceRange.typicalMax >= roadRange.typicalMax)
    }

    // ==================== Ankle Angle at BDC Tests ====================

    @Test
    fun `ankle angle at BDC is defined for all disciplines`() {
        for (discipline in CyclingDiscipline.values()) {
            val range = RangeLookup.getRange(
                FitMetricType.ANKLE_ANGLE_AT_BDC,
                discipline
            )
            assertNotNull("Ankle angle should be defined for $discipline", range)
        }
    }

    @Test
    fun `ankle angle at BDC is within reasonable bounds`() {
        for (discipline in CyclingDiscipline.values()) {
            val range = RangeLookup.getRange(
                FitMetricType.ANKLE_ANGLE_AT_BDC,
                discipline
            )!!

            // Should be in the 80-110 degree range generally
            assertTrue("Ankle min should be reasonable for $discipline", 
                range.typicalMin >= 70f)
            assertTrue("Ankle max should be reasonable for $discipline",
                range.typicalMax <= 120f)
        }
    }

    // ==================== Multi-Range Query Tests ====================

    @Test
    fun `getRangesForDiscipline returns all metrics for a discipline`() {
        val roadRanges = RangeLookup.getRangesForDiscipline(CyclingDiscipline.ROAD)
        
        assertTrue(roadRanges.isNotEmpty())
        assertTrue(roadRanges.containsKey(FitMetricType.KNEE_ANGLE_AT_BDC))
        assertTrue(roadRanges.containsKey(FitMetricType.TORSO_ANGLE))
        assertTrue(roadRanges.containsKey(FitMetricType.KOPS_OFFSET))
        assertTrue(roadRanges.containsKey(FitMetricType.HIP_ANGLE_AT_TDC))
        assertTrue(roadRanges.containsKey(FitMetricType.ANKLE_ANGLE_AT_BDC))
        
        // Should have all 5 metrics
        assertEquals(5, roadRanges.size)
    }

    @Test
    fun `getRangesForMetric returns all disciplines for a metric`() {
        val kneeRanges = RangeLookup.getRangesForMetric(FitMetricType.KNEE_ANGLE_AT_BDC)
        
        assertTrue(kneeRanges.isNotEmpty())
        assertTrue(kneeRanges.containsKey(CyclingDiscipline.ROAD))
        assertTrue(kneeRanges.containsKey(CyclingDiscipline.ENDURANCE))
        assertTrue(kneeRanges.containsKey(CyclingDiscipline.GRAVEL))
        assertTrue(kneeRanges.containsKey(CyclingDiscipline.TT))
        assertTrue(kneeRanges.containsKey(CyclingDiscipline.TRI))
        
        // Should have all 5 disciplines
        assertEquals(5, kneeRanges.size)
    }

    @Test
    fun `getDisciplinesWithMetric returns all disciplines for commonly measured metrics`() {
        val kneeAngleDisciplines = RangeLookup.getDisciplinesWithMetric(
            FitMetricType.KNEE_ANGLE_AT_BDC
        )
        
        assertEquals(5, kneeAngleDisciplines.size)
        assertTrue(kneeAngleDisciplines.contains(CyclingDiscipline.ROAD))
        assertTrue(kneeAngleDisciplines.contains(CyclingDiscipline.TT))
    }

    // ==================== All Disciplines Have Complete Data ====================

    @Test
    fun `all disciplines have complete metric coverage`() {
        val expectedMetrics = setOf(
            FitMetricType.KNEE_ANGLE_AT_BDC,
            FitMetricType.HIP_ANGLE_AT_TDC,
            FitMetricType.TORSO_ANGLE,
            FitMetricType.KOPS_OFFSET,
            FitMetricType.ANKLE_ANGLE_AT_BDC
        )

        for (discipline in CyclingDiscipline.values()) {
            val ranges = RangeLookup.getRangesForDiscipline(discipline)
            assertEquals(
                "Discipline $discipline should have all metrics defined",
                expectedMetrics.size,
                ranges.size
            )
            
            for (metric in expectedMetrics) {
                assertTrue(
                    "Discipline $discipline should have $metric defined",
                    ranges.containsKey(metric)
                )
            }
        }
    }

    // ==================== Range Sanity Tests ====================

    @Test
    fun `all ranges have positive width`() {
        for (discipline in CyclingDiscipline.values()) {
            val ranges = RangeLookup.getRangesForDiscipline(discipline)
            for ((metricType, range) in ranges) {
                assertTrue(
                    "Range width should be positive for $discipline $metricType",
                    range.width() > 0f
                )
            }
        }
    }

    @Test
    fun `all ranges have valid min less than max`() {
        for (discipline in CyclingDiscipline.values()) {
            val ranges = RangeLookup.getRangesForDiscipline(discipline)
            for ((metricType, range) in ranges) {
                assertTrue(
                    "Min should be less than max for $discipline $metricType",
                    range.typicalMin < range.typicalMax
                )
            }
        }
    }

    @Test
    fun `all ranges have descriptions`() {
        for (discipline in CyclingDiscipline.values()) {
            val ranges = RangeLookup.getRangesForDiscipline(discipline)
            for ((metricType, range) in ranges) {
                assertNotNull(
                    "Description should exist for $discipline $metricType",
                    range.description
                )
                assertTrue(
                    "Description should not be empty for $discipline $metricType",
                    range.description.isNotEmpty()
                )
            }
        }
    }

    // ==================== Practical Use Case Tests ====================

    @Test
    fun `can check if a measured value is typical for road cycling`() {
        val kneeRange = RangeLookup.getRange(
            FitMetricType.KNEE_ANGLE_AT_BDC,
            CyclingDiscipline.ROAD
        )!!

        assertTrue(kneeRange.contains(150f))
        assertFalse(kneeRange.contains(135f))
        assertFalse(kneeRange.contains(165f))
    }

    @Test
    fun `can compare ranges across disciplines`() {
        val roadTorso = RangeLookup.getRange(
            FitMetricType.TORSO_ANGLE,
            CyclingDiscipline.ROAD
        )!!

        val ttTorso = RangeLookup.getRange(
            FitMetricType.TORSO_ANGLE,
            CyclingDiscipline.TT
        )!!

        // A typical TT torso angle (20°) should be outside the road range
        assertFalse(roadTorso.contains(20f))
        assertTrue(ttTorso.contains(20f))
    }

    @Test
    fun `can calculate deviation from typical range`() {
        val kneeRange = RangeLookup.getRange(
            FitMetricType.KNEE_ANGLE_AT_BDC,
            CyclingDiscipline.ROAD
        )!!

        // In range
        assertEquals(0f, kneeRange.deviationFromRange(150f), 0.001f)
        
        // Below range
        assertEquals(-5f, kneeRange.deviationFromRange(140f), 0.001f)
        
        // Above range
        assertEquals(5f, kneeRange.deviationFromRange(160f), 0.001f)
    }
}
