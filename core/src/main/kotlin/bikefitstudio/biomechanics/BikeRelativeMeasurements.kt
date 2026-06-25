package bikefitstudio.biomechanics

import bikefitstudio.calibration.BikeCalibration
import bikefitstudio.pose.Landmark
import kotlin.math.abs

/**
 * Utilities for computing measurements relative to bike dimensions.
 * 
 * Provides methods to normalize body measurements using bike reference points,
 * enabling cross-bike comparisons and scale-independent analysis.
 */
object BikeRelativeMeasurements {
    
    /**
     * Normalizes a horizontal distance using saddle-to-bottom-bracket distance as reference.
     * 
     * This allows for scale-independent measurements that can be compared across
     * different bike sizes and body dimensions.
     * 
     * @param distance The raw distance in normalized coordinates (0.0-1.0)
     * @param calibration The bike calibration containing reference points
     * @return Normalized distance relative to bike size, or null if calibration is incomplete
     */
    fun normalizeByBikeSize(distance: Float, calibration: BikeCalibration?): Float? {
        val bikeSize = calibration?.getSaddleToBottomBracketDistance() ?: return null
        if (bikeSize < 0.001f) return null // Guard against invalid calibration
        return distance / bikeSize
    }
    
    /**
     * Computes knee-over-pedal offset using actual bottom bracket position and crank length.
     * 
     * This method uses the calibrated bottom bracket position and crank length to calculate
     * the actual pedal location at 3 o'clock position, providing more accurate KOPS measurement
     * than using ankle as pedal proxy.
     * 
     * At 3 o'clock position:
     * - Pedal is horizontally forward of bottom bracket by crank length
     * - Pedal X position = BB X + crank length (in normalized coordinates)
     * 
     * @param knee Knee landmark
     * @param hip Hip landmark (for femur length calculation)
     * @param calibration Bike calibration containing bottom bracket position and crank length
     * @return Normalized KOPS offset, or null if calibration is incomplete or invalid
     */
    fun computeKOPSWithBottomBracket(
        knee: Landmark,
        hip: Landmark,
        calibration: BikeCalibration?
    ): Float? {
        val bottomBracket = calibration?.bottomBracket ?: return null
        
        // Get pedal position at 3 o'clock using crank length
        val pedalX = calibration.getPedalPositionAt3OClock() ?: bottomBracket.x
        
        // Calculate horizontal offset from knee to pedal
        val horizontalOffset = knee.x - pedalX
        
        // Calculate femur length for normalization
        val hipPoint = Vector2D(hip.x, hip.y)
        val kneePoint = Vector2D(knee.x, knee.y)
        val femurLength = hipPoint.distanceTo(kneePoint)
        
        if (femurLength < Vector2D.EPSILON) return null
        
        // Return normalized offset (dimensionless)
        return horizontalOffset / femurLength
    }
    
    /**
     * Computes reach measurement normalized by bike dimensions.
     * 
     * Measures horizontal distance from hip to shoulder and normalizes
     * by saddle-to-handlebar reach for cross-bike comparison.
     * 
     * @param shoulder Shoulder landmark
     * @param hip Hip landmark
     * @param calibration Bike calibration containing handlebar and saddle positions
     * @return Normalized reach ratio (body reach / bike reach), or null if calibration incomplete
     */
    fun computeNormalizedReach(
        shoulder: Landmark,
        hip: Landmark,
        calibration: BikeCalibration?
    ): Float? {
        val bikeReach = calibration?.getSaddleToHandlebarReach() ?: return null
        if (bikeReach < 0.001f) return null
        
        // Calculate horizontal body reach (hip to shoulder distance)
        val bodyReachX = abs(shoulder.x - hip.x)
        
        // Return normalized reach (dimensionless ratio)
        return bodyReachX / bikeReach
    }
    
    /**
     * Computes torso angle normalized by bike geometry.
     * 
     * Measures the torso angle and normalizes by the saddle-to-handlebar
     * angle to understand how rider position relates to bike geometry.
     * 
     * @param shoulder Shoulder landmark
     * @param hip Hip landmark
     * @param calibration Bike calibration
     * @return Normalized torso-to-bike angle ratio, or null if calibration incomplete
     */
    fun computeTorsoToBikeRatio(
        shoulder: Landmark,
        hip: Landmark,
        calibration: BikeCalibration?
    ): Float? {
        val saddle = calibration?.saddleTop ?: return null
        val handlebar = calibration.handlebar ?: return null
        
        // Calculate bike cockpit angle (saddle to handlebar)
        val bikeDx = handlebar.x - saddle.x
        val bikeDy = handlebar.y - saddle.y
        val bikeVector = Vector2D(bikeDx, bikeDy)
        
        // Calculate torso vector (hip to shoulder)
        val torsoDx = shoulder.x - hip.x
        val torsoDy = shoulder.y - hip.y
        val torsoVector = Vector2D(torsoDx, torsoDy)
        
        // Compute angle between torso and bike vectors
        // Use arctan2 to get angle from horizontal
        val bikeAngle = kotlin.math.atan2(bikeDy.toDouble(), bikeDx.toDouble()).toFloat()
        val torsoAngle = kotlin.math.atan2(torsoDy.toDouble(), torsoDx.toDouble()).toFloat()
        
        // Return ratio (how much rider is leaning relative to bike geometry)
        if (kotlin.math.abs(bikeAngle) < Vector2D.EPSILON) return null
        return torsoAngle / bikeAngle
    }
    
    /**
     * Validates that a measurement is within reasonable bounds.
     * 
     * @param value The normalized measurement value
     * @param minBound Minimum expected value
     * @param maxBound Maximum expected value
     * @return True if value is within bounds
     */
    fun isWithinBounds(value: Float, minBound: Float, maxBound: Float): Boolean {
        return value in minBound..maxBound
    }
}
