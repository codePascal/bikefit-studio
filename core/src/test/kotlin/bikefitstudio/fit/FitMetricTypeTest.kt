package bikefitstudio.fit

import org.junit.Assert.*
import org.junit.Test

class FitMetricTypeTest {

    @Test
    fun `all metric types have display names`() {
        for (metricType in FitMetricType.values()) {
            assertNotNull(metricType.displayName())
            assertTrue(metricType.displayName().isNotEmpty())
        }
    }

    @Test
    fun `all metric types have units`() {
        for (metricType in FitMetricType.values()) {
            assertNotNull(metricType.unit())
            assertTrue(metricType.unit().isNotEmpty())
        }
    }

    @Test
    fun `angle metrics have degrees unit`() {
        assertEquals("degrees", FitMetricType.KNEE_ANGLE_AT_BDC.unit())
        assertEquals("degrees", FitMetricType.HIP_ANGLE_AT_TDC.unit())
        assertEquals("degrees", FitMetricType.TORSO_ANGLE.unit())
        assertEquals("degrees", FitMetricType.ANKLE_ANGLE_AT_BDC.unit())
    }

    @Test
    fun `KOPS offset has normalized unit`() {
        assertEquals("normalized", FitMetricType.KOPS_OFFSET.unit())
    }

    @Test
    fun `KNEE_ANGLE_AT_BDC has correct display name`() {
        assertEquals("Knee Angle at BDC", FitMetricType.KNEE_ANGLE_AT_BDC.displayName())
    }

    @Test
    fun `HIP_ANGLE_AT_TDC has correct display name`() {
        assertEquals("Hip Angle at TDC", FitMetricType.HIP_ANGLE_AT_TDC.displayName())
    }

    @Test
    fun `TORSO_ANGLE has correct display name`() {
        assertEquals("Torso Angle", FitMetricType.TORSO_ANGLE.displayName())
    }

    @Test
    fun `KOPS_OFFSET has correct display name`() {
        assertEquals("KOPS Offset", FitMetricType.KOPS_OFFSET.displayName())
    }

    @Test
    fun `ANKLE_ANGLE_AT_BDC has correct display name`() {
        assertEquals("Ankle Angle at BDC", FitMetricType.ANKLE_ANGLE_AT_BDC.displayName())
    }

    @Test
    fun `metric types are enumerated in expected order`() {
        val metricTypes = FitMetricType.values()
        assertEquals(5, metricTypes.size)
        assertEquals(FitMetricType.KNEE_ANGLE_AT_BDC, metricTypes[0])
        assertEquals(FitMetricType.HIP_ANGLE_AT_TDC, metricTypes[1])
        assertEquals(FitMetricType.TORSO_ANGLE, metricTypes[2])
        assertEquals(FitMetricType.KOPS_OFFSET, metricTypes[3])
        assertEquals(FitMetricType.ANKLE_ANGLE_AT_BDC, metricTypes[4])
    }
}
