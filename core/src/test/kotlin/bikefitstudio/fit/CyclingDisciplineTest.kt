package bikefitstudio.fit

import org.junit.Assert.*
import org.junit.Test

class CyclingDisciplineTest {

    @Test
    fun `all disciplines have display names`() {
        for (discipline in CyclingDiscipline.values()) {
            assertNotNull(discipline.displayName())
            assertTrue(discipline.displayName().isNotEmpty())
        }
    }

    @Test
    fun `all disciplines have descriptions`() {
        for (discipline in CyclingDiscipline.values()) {
            assertNotNull(discipline.description())
            assertTrue(discipline.description().isNotEmpty())
        }
    }

    @Test
    fun `ROAD has correct display name`() {
        assertEquals("Road", CyclingDiscipline.ROAD.displayName())
    }

    @Test
    fun `ENDURANCE has correct display name`() {
        assertEquals("Endurance", CyclingDiscipline.ENDURANCE.displayName())
    }

    @Test
    fun `GRAVEL has correct display name`() {
        assertEquals("Gravel", CyclingDiscipline.GRAVEL.displayName())
    }

    @Test
    fun `TT has correct display name`() {
        assertEquals("Time Trial", CyclingDiscipline.TT.displayName())
    }

    @Test
    fun `TRI has correct display name`() {
        assertEquals("Triathlon", CyclingDiscipline.TRI.displayName())
    }

    @Test
    fun `disciplines are enumerated in expected order`() {
        val disciplines = CyclingDiscipline.values()
        assertEquals(5, disciplines.size)
        assertEquals(CyclingDiscipline.ROAD, disciplines[0])
        assertEquals(CyclingDiscipline.ENDURANCE, disciplines[1])
        assertEquals(CyclingDiscipline.GRAVEL, disciplines[2])
        assertEquals(CyclingDiscipline.TT, disciplines[3])
        assertEquals(CyclingDiscipline.TRI, disciplines[4])
    }
}
