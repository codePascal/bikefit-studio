package bikefitstudio.calibration

/**
 * In-memory repository for storing bike calibration data during the session.
 * 
 * This singleton provides centralized access to the current bike calibration,
 * making it available to the analysis flow after calibration is complete.
 * 
 * Note: Calibration data is NOT persisted across app restarts in MVP v1.0.
 * Persistence will be added in v2.
 * 
 * Usage:
 * ```
 * // Store calibration after user completes calibration flow
 * CalibrationRepository.setCalibration(calibration)
 * 
 * // Access calibration from analysis flow
 * val calibration = CalibrationRepository.getCalibration()
 * if (calibration?.isComplete == true) {
 *     // Use calibration data
 * }
 * ```
 */
object CalibrationRepository {

    /**
     * Current bike calibration, or null if not calibrated.
     */
    @Volatile
    private var currentCalibration: BikeCalibration? = null

    /**
     * Listeners to be notified when calibration changes.
     */
    private val listeners = mutableListOf<CalibrationListener>()

    /**
     * Sets the current bike calibration.
     * 
     * @param calibration The completed calibration data
     */
    @Synchronized
    fun setCalibration(calibration: BikeCalibration) {
        currentCalibration = calibration
        notifyListeners(calibration)
    }

    /**
     * Gets the current bike calibration.
     * 
     * @return The current calibration, or null if not calibrated
     */
    fun getCalibration(): BikeCalibration? {
        return currentCalibration
    }

    /**
     * Checks if a valid calibration is available.
     * 
     * @return true if calibration has all three reference points
     */
    fun hasValidCalibration(): Boolean {
        return currentCalibration?.hasAllPoints == true
    }

    /**
     * Clears the current calibration.
     */
    @Synchronized
    fun clearCalibration() {
        currentCalibration = null
        notifyListeners(null)
    }

    /**
     * Gets a specific reference point from the current calibration.
     * 
     * @param type The type of point to get
     * @return The reference point, or null if not set
     */
    fun getPoint(type: BikeReferencePointType): BikeReferencePoint? {
        return when (type) {
            BikeReferencePointType.SADDLE_TOP -> currentCalibration?.saddleTop
            BikeReferencePointType.BOTTOM_BRACKET -> currentCalibration?.bottomBracket
            BikeReferencePointType.SPINDLE -> currentCalibration?.spindle
            BikeReferencePointType.HANDLEBAR -> currentCalibration?.handlebar
        }
    }

    /**
     * Adds a listener to be notified of calibration changes.
     * 
     * @param listener The listener to add
     */
    @Synchronized
    fun addListener(listener: CalibrationListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    /**
     * Removes a calibration change listener.
     * 
     * @param listener The listener to remove
     */
    @Synchronized
    fun removeListener(listener: CalibrationListener) {
        listeners.remove(listener)
    }

    /**
     * Notifies all listeners of a calibration change.
     */
    private fun notifyListeners(calibration: BikeCalibration?) {
        listeners.forEach { it.onCalibrationChanged(calibration) }
    }

    /**
     * Resets the repository for testing purposes.
     */
    @Synchronized
    internal fun reset() {
        currentCalibration = null
        listeners.clear()
    }
}

/**
 * Listener interface for calibration changes.
 */
fun interface CalibrationListener {
    /**
     * Called when the calibration changes.
     * 
     * @param calibration The new calibration, or null if cleared
     */
    fun onCalibrationChanged(calibration: BikeCalibration?)
}
