package bikefitstudio.calibration

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for CalibrationRepository.
 */
class CalibrationRepositoryTest {

    @Before
    fun setup() {
        CalibrationRepository.reset()
    }

    @After
    fun tearDown() {
        CalibrationRepository.reset()
    }

    // ==================== Basic Storage Tests ====================

    @Test
    fun `getCalibration returns null when not set`() {
        assertNull(CalibrationRepository.getCalibration())
    }

    @Test
    fun `setCalibration stores calibration`() {
        val calibration = createCompleteCalibration()
        
        CalibrationRepository.setCalibration(calibration)
        
        assertEquals(calibration, CalibrationRepository.getCalibration())
    }

    @Test
    fun `clearCalibration removes stored calibration`() {
        CalibrationRepository.setCalibration(createCompleteCalibration())
        
        CalibrationRepository.clearCalibration()
        
        assertNull(CalibrationRepository.getCalibration())
    }

    // ==================== Validation Tests ====================

    @Test
    fun `hasValidCalibration returns false when not set`() {
        assertFalse(CalibrationRepository.hasValidCalibration())
    }

    @Test
    fun `hasValidCalibration returns false for incomplete calibration`() {
        val incomplete = BikeCalibration(
            saddleTop = createPoint(BikeReferencePointType.SADDLE_TOP),
            bottomBracket = null,
            handlebar = null
        )
        
        CalibrationRepository.setCalibration(incomplete)
        
        assertFalse(CalibrationRepository.hasValidCalibration())
    }

    @Test
    fun `hasValidCalibration returns true for complete calibration`() {
        CalibrationRepository.setCalibration(createCompleteCalibration())
        
        assertTrue(CalibrationRepository.hasValidCalibration())
    }

    // ==================== Point Access Tests ====================

    @Test
    fun `getPoint returns null when not calibrated`() {
        assertNull(CalibrationRepository.getPoint(BikeReferencePointType.SADDLE_TOP))
    }

    @Test
    fun `getPoint returns correct point`() {
        val calibration = createCompleteCalibration()
        CalibrationRepository.setCalibration(calibration)
        
        val saddle = CalibrationRepository.getPoint(BikeReferencePointType.SADDLE_TOP)
        val bb = CalibrationRepository.getPoint(BikeReferencePointType.BOTTOM_BRACKET)
        val handlebar = CalibrationRepository.getPoint(BikeReferencePointType.HANDLEBAR)
        
        assertEquals(calibration.saddleTop, saddle)
        assertEquals(calibration.bottomBracket, bb)
        assertEquals(calibration.handlebar, handlebar)
    }

    @Test
    fun `getPoint returns null for missing point`() {
        val partial = BikeCalibration(
            saddleTop = createPoint(BikeReferencePointType.SADDLE_TOP),
            bottomBracket = null,
            handlebar = null
        )
        CalibrationRepository.setCalibration(partial)
        
        assertNotNull(CalibrationRepository.getPoint(BikeReferencePointType.SADDLE_TOP))
        assertNull(CalibrationRepository.getPoint(BikeReferencePointType.BOTTOM_BRACKET))
        assertNull(CalibrationRepository.getPoint(BikeReferencePointType.HANDLEBAR))
    }

    // ==================== Listener Tests ====================

    @Test
    fun `listener is notified on setCalibration`() {
        var notifiedCalibration: BikeCalibration? = null
        val listener = CalibrationListener { notifiedCalibration = it }
        CalibrationRepository.addListener(listener)
        
        val calibration = createCompleteCalibration()
        CalibrationRepository.setCalibration(calibration)
        
        assertEquals(calibration, notifiedCalibration)
    }

    @Test
    fun `listener is notified on clearCalibration`() {
        var notifiedCalibration: BikeCalibration? = createCompleteCalibration()
        val listener = CalibrationListener { notifiedCalibration = it }
        CalibrationRepository.addListener(listener)
        
        CalibrationRepository.clearCalibration()
        
        assertNull(notifiedCalibration)
    }

    @Test
    fun `removed listener is not notified`() {
        var callCount = 0
        val listener = CalibrationListener { callCount++ }
        CalibrationRepository.addListener(listener)
        CalibrationRepository.removeListener(listener)
        
        CalibrationRepository.setCalibration(createCompleteCalibration())
        
        assertEquals(0, callCount)
    }

    @Test
    fun `multiple listeners are all notified`() {
        var count1 = 0
        var count2 = 0
        CalibrationRepository.addListener { count1++ }
        CalibrationRepository.addListener { count2++ }
        
        CalibrationRepository.setCalibration(createCompleteCalibration())
        
        assertEquals(1, count1)
        assertEquals(1, count2)
    }

    @Test
    fun `same listener is not added twice`() {
        var callCount = 0
        val listener = CalibrationListener { callCount++ }
        CalibrationRepository.addListener(listener)
        CalibrationRepository.addListener(listener)
        
        CalibrationRepository.setCalibration(createCompleteCalibration())
        
        assertEquals(1, callCount)
    }

    // ==================== Overwrite Tests ====================

    @Test
    fun `setCalibration overwrites previous calibration`() {
        val first = createCompleteCalibration()
        val second = BikeCalibration(
            saddleTop = createPoint(BikeReferencePointType.SADDLE_TOP, 0.1f, 0.1f),
            bottomBracket = createPoint(BikeReferencePointType.BOTTOM_BRACKET, 0.2f, 0.2f),
            handlebar = createPoint(BikeReferencePointType.HANDLEBAR, 0.3f, 0.3f)
        )
        
        CalibrationRepository.setCalibration(first)
        CalibrationRepository.setCalibration(second)
        
        assertEquals(second, CalibrationRepository.getCalibration())
    }

    // ==================== Normalized Coordinates Tests ====================

    @Test
    fun `stored points have normalized coordinates`() {
        val calibration = createCompleteCalibration()
        CalibrationRepository.setCalibration(calibration)
        
        val saddle = CalibrationRepository.getPoint(BikeReferencePointType.SADDLE_TOP)!!
        
        assertTrue(saddle.x in 0f..1f)
        assertTrue(saddle.y in 0f..1f)
    }

    // ==================== Helper Functions ====================

    private fun createPoint(
        type: BikeReferencePointType,
        x: Float = 0.5f,
        y: Float = 0.5f
    ): BikeReferencePoint {
        return BikeReferencePoint(type = type, x = x, y = y)
    }

    private fun createCompleteCalibration(): BikeCalibration {
        return BikeCalibration(
            saddleTop = createPoint(BikeReferencePointType.SADDLE_TOP, 0.3f, 0.3f),
            bottomBracket = createPoint(BikeReferencePointType.BOTTOM_BRACKET, 0.4f, 0.7f),
            handlebar = createPoint(BikeReferencePointType.HANDLEBAR, 0.7f, 0.4f)
        )
    }
}
