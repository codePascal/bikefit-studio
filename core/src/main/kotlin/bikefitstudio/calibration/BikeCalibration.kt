package bikefitstudio.calibration

import bikefitstudio.biomechanics.BodySide
import kotlin.math.sqrt

/**
 * Types of bike reference points that can be calibrated.
 */
enum class BikeReferencePointType {
    /** Top of the saddle where the rider sits */
    SADDLE_TOP,
    
    /** Center of the bottom bracket (crank axle) */
    BOTTOM_BRACKET,
    
    /** Pedal spindle position (marked by tapping) */
    SPINDLE,
    
    /** Handlebar grip position */
    HANDLEBAR
}

/**
 * Represents the orientation of the bike in the video frame.
 */
enum class BikeOrientation {
    /** Bike is facing left (handlebars are on the left side) */
    LEFT_FACING,
    
    /** Bike is facing right (handlebars are on the right side) */
    RIGHT_FACING
}

/**
 * Represents a bike reference point marked by the user.
 * 
 * @param type The type of reference point
 * @param x Normalized x coordinate (0.0 to 1.0, left to right)
 * @param y Normalized y coordinate (0.0 to 1.0, top to bottom)
 */
data class BikeReferencePoint(
    val type: BikeReferencePointType,
    val x: Float,
    val y: Float
) {
    /**
     * Returns the pixel coordinates for a given image size.
     */
    fun toPixels(imageWidth: Int, imageHeight: Int): Pair<Float, Float> {
        return Pair(x * imageWidth, y * imageHeight)
    }
    
    companion object {
        /**
         * Creates a reference point from pixel coordinates.
         */
        fun fromPixels(
            type: BikeReferencePointType,
            pixelX: Float,
            pixelY: Float,
            imageWidth: Int,
            imageHeight: Int
        ): BikeReferencePoint {
            return BikeReferencePoint(
                type = type,
                x = pixelX / imageWidth,
                y = pixelY / imageHeight
            )
        }
    }
}

/**
 * Holds all calibration data for a bike setup.
 * 
 * @param saddleTop Top of the saddle position
 * @param bottomBracket Center of bottom bracket position
 * @param handlebar Handlebar grip position
 * @param spindle Pedal spindle position
 * @param crankLengthMm Crank length in millimeters (typically 165-180mm)
 * @param timestampMs When the calibration was performed
 */
data class BikeCalibration(
    val saddleTop: BikeReferencePoint? = null,
    val bottomBracket: BikeReferencePoint? = null,
    val handlebar: BikeReferencePoint? = null,
    val spindle: BikeReferencePoint? = null,
    val crankLengthMm: Int? = null,
    val timestampMs: Long = System.currentTimeMillis()
) {
    /**
     * Returns true if all reference points, crank length, and spindle have been set.
     */
    val isComplete: Boolean
        get() = saddleTop != null && bottomBracket != null && handlebar != null && spindle != null && crankLengthMm != null
    
    /**
     * Returns true if all three reference points have been set (excluding crank length and spindle).
     */
    val hasAllPoints: Boolean
        get() = saddleTop != null && bottomBracket != null && handlebar != null
    
    /**
     * Returns the number of points that have been set.
     */
    val pointCount: Int
        get() = listOfNotNull(saddleTop, bottomBracket, handlebar, spindle).size
    
    /**
     * Returns a list of all set reference points.
     */
    fun getPoints(): List<BikeReferencePoint> {
        return listOfNotNull(saddleTop, bottomBracket, spindle, handlebar)
    }
    
    /**
     * Returns the next point type that needs to be set, or null if complete.
     * Order: Saddle, Bottom Bracket, Spindle, Handlebar
     */
    fun getNextPointType(): BikeReferencePointType? {
        return when {
            saddleTop == null -> BikeReferencePointType.SADDLE_TOP
            bottomBracket == null -> BikeReferencePointType.BOTTOM_BRACKET
            spindle == null -> BikeReferencePointType.SPINDLE
            handlebar == null -> BikeReferencePointType.HANDLEBAR
            else -> null
        }
    }
    
    /**
     * Returns a copy with the given point added/updated.
     */
    fun withPoint(point: BikeReferencePoint): BikeCalibration {
        return when (point.type) {
            BikeReferencePointType.SADDLE_TOP -> copy(saddleTop = point)
            BikeReferencePointType.BOTTOM_BRACKET -> copy(bottomBracket = point)
            BikeReferencePointType.SPINDLE -> copy(spindle = point)
            BikeReferencePointType.HANDLEBAR -> copy(handlebar = point)
        }
    }
    
    /**
     * Returns a copy with the spindle position set.
     */
    fun withSpindle(spindle: BikeReferencePoint): BikeCalibration {
        return copy(spindle = spindle)
    }
    
    /**
     * Calculates the saddle height relative to bottom bracket.
     * Returns null if either point is not set.
     */
    fun getSaddleHeightRatio(): Float? {
        val saddle = saddleTop ?: return null
        val bb = bottomBracket ?: return null
        return bb.y - saddle.y  // Positive means saddle is above BB
    }
    
    /**
     * Determines the bike orientation based on handlebar position relative to saddle.
     * 
     * The bike is considered LEFT_FACING if handlebars are to the left of the saddle
     * (handlebar.x < saddle.x), and RIGHT_FACING otherwise.
     * 
     * @return The bike orientation, or null if required points are not set
     */
    fun getBikeOrientation(): BikeOrientation? {
        val saddle = saddleTop ?: return null
        val handlebar = handlebar ?: return null
        
        return if (handlebar.x < saddle.x) {
            BikeOrientation.LEFT_FACING
        } else {
            BikeOrientation.RIGHT_FACING
        }
    }
    
    /**
     * Gets the body side that is visible to the camera based on bike orientation.
     * 
     * When the bike faces left, the camera sees the left side of the rider's body.
     * When the bike faces right, the camera sees the right side of the rider's body.
     * 
     * @return The body side visible to camera, or null if orientation cannot be determined
     */
    fun getCameraSide(): BodySide? {
        return when (getBikeOrientation()) {
            BikeOrientation.LEFT_FACING -> BodySide.LEFT
            BikeOrientation.RIGHT_FACING -> BodySide.RIGHT
            null -> null
        }
    }
    
    /**
     * Calculates the pedal position at 3 o'clock (horizontal forward position).
     * 
     * At 3 o'clock, the pedal is horizontally forward of the bottom bracket
     * by the crank length distance. This method returns the offset in normalized
     * coordinates relative to the bike's saddle-to-BB distance.
     * \n     * @return Pedal X offset from BB in normalized coordinates, or null if BB or crank length not set
     */
    fun getPedalOffsetAt3OClock(): Float? {
        val bb = bottomBracket ?: return null
        val crankMm = crankLengthMm ?: return null
        val saddleToBBDistance = getSaddleToBottomBracketDistance() ?: return null
        
        // Typical saddle-to-BB distance is ~700-800mm (approximate bike seat tube length)
        // We normalize the crank length by this distance
        val estimatedBikeSizeMm = 750f
        
        // Calculate crank length as a fraction of bike size, then scale to normalized coordinates
        val crankLengthNormalized = (crankMm.toFloat() / estimatedBikeSizeMm) * saddleToBBDistance
        
        return crankLengthNormalized
    }
    
    /**
     * Calculates the absolute pedal X position at 3 o'clock.
     * 
     * @return Pedal X position in normalized coordinates, or null if incomplete
     */
    fun getPedalPositionAt3OClock(): Float? {
        val bb = bottomBracket ?: return null
        val offset = getPedalOffsetAt3OClock() ?: return bb.x
        return bb.x + offset
    }
    
    /**
     * Calculates the horizontal distance (reach) from saddle to handlebars.
     * Returns normalized distance, or null if either point is not set.
     */
    fun getSaddleToHandlebarReach(): Float? {
        val saddle = saddleTop ?: return null
        val handlebar = handlebar ?: return null
        return kotlin.math.abs(handlebar.x - saddle.x)
    }
    
    /**
     * Calculates the Euclidean distance from saddle top to bottom bracket.
     * This can be used to normalize other measurements relative to bike size.
     * Returns normalized distance, or null if either point is not set.
     */
    fun getSaddleToBottomBracketDistance(): Float? {
        val saddle = saddleTop ?: return null
        val bb = bottomBracket ?: return null
        val dx = saddle.x - bb.x
        val dy = saddle.y - bb.y
        return sqrt(dx * dx + dy * dy)
    }
    
    /**
     * Validates that the three calibration points form a reasonable bike configuration.
     * 
     * Checks:
     * - Points are not collinear
     * - Saddle is above bottom bracket
     * - Handlebar and saddle are roughly at similar heights
     * 
     * @return Validation result with error message if invalid, or null if valid
     */
    fun validate(): String? {
        if (!hasAllPoints) return "Calibration incomplete - missing reference points"
        
        val saddle = saddleTop!!
        val bb = bottomBracket!!
        val handlebar = handlebar!!
        
        // Check saddle is above bottom bracket
        if (saddle.y >= bb.y) {
            return "Saddle must be above bottom bracket"
        }
        
        // Check points are not collinear (area of triangle > threshold)
        val area = kotlin.math.abs(
            (saddle.x * (bb.y - handlebar.y) + 
             bb.x * (handlebar.y - saddle.y) + 
             handlebar.x * (saddle.y - bb.y)) / 2.0f
        )
        
        if (area < 0.001f) { // ~0.1% of normalized space
            return "Calibration points cannot be in a straight line"
        }
        
        // Check handlebar is not behind saddle (both should be on same horizontal plane roughly)
        val saddleToBB = kotlin.math.abs(saddle.x - bb.x)
        val handlebarToSaddle = kotlin.math.abs(handlebar.x - saddle.x)
        
        // Handlebar should be further from BB than saddle is
        if (handlebarToSaddle < saddleToBB * 0.3f) {
            return "Handlebar appears to be too close to saddle"
        }
        
        // If spindle is marked, validate it against geometric estimate
        if (spindle != null) {
            val validationError = validateSpindlePosition()
            if (validationError != null) {
                return validationError
            }
        }
        
        return null
    }
    
    /**
     * Estimates the spindle X position geometrically (for validation purposes).
     * This is based on the assumption that at 3 o'clock, the crank extends forward
     * from the bottom bracket by the crank length distance.
     * 
     * @return Estimated spindle X position, or null if BB or crank length not set
     */
    fun estimateGeometricSpindleX(): Float? {
        val bb = bottomBracket ?: return null
        val crankMm = crankLengthMm ?: return null
        val saddleToBBDistance = getSaddleToBottomBracketDistance() ?: return null
        
        // Estimate spindle as: BB + (crank_length / typical_bike_size) * saddle_to_BB_distance
        // This assumes crank extends forward from BB at 3 o'clock position
        val estimatedBikeSizeMm = 750f
        val crankLengthNormalized = (crankMm.toFloat() / estimatedBikeSizeMm) * saddleToBBDistance
        
        return bb.x + crankLengthNormalized
    }
    
    /**
     * Validates the marked spindle position against geometric estimate.
     * Tolerance is ±10% of the geometric estimate offset from BB.
     * 
     * @return Error message if validation fails, null if valid
     */
    fun validateSpindlePosition(): String? {
        val markedSpindle = spindle?.x ?: return null
        val bb = bottomBracket ?: return "Cannot validate spindle without bottom bracket"
        
        val geometricSpindle = estimateGeometricSpindleX() 
            ?: return "Cannot estimate spindle position without crank length"
        
        val offset = kotlin.math.abs(markedSpindle - geometricSpindle)
        val geometricOffset = kotlin.math.abs(geometricSpindle - bb.x)
        val tolerance = geometricOffset * 0.10f  // ±10%
        
        if (offset > tolerance) {
            return "Spindle position is too far from estimated position (±${(tolerance * 100).toInt()}% tolerance)"
        }
        
        return null
    }
    
    companion object {
        /** Empty calibration with no points set */
        val EMPTY = BikeCalibration()
    }
}

/**
 * State of the calibration process.
 */
sealed class CalibrationState {
    /** Waiting for user to tap saddle top */
    object WaitingForSaddle : CalibrationState()
    
    /** Waiting for user to tap bottom bracket */
    object WaitingForBottomBracket : CalibrationState()
    
    /** Waiting for user to mark pedal spindle position */
    object WaitingForSpindle : CalibrationState()
    
    /** Waiting for user to tap handlebar */
    object WaitingForHandlebar : CalibrationState()
    
    /** Waiting for user to input crank length */
    object WaitingForCrankLength : CalibrationState()
    
    /** All data collected, ready to confirm */
    object ReadyToConfirm : CalibrationState()
    
    /** Calibration confirmed and complete */
    data class Confirmed(val calibration: BikeCalibration) : CalibrationState()
    
    /**
     * Returns the instruction text for the current state.
     */
    fun getInstructionText(): String {
        return when (this) {
            is WaitingForSaddle -> "Step 1/4: Tap the top of the saddle"
            is WaitingForBottomBracket -> "Step 2/4: Tap the center of the bottom bracket"
            is WaitingForSpindle -> "Step 3/4: Tap the pedal spindle"
            is WaitingForHandlebar -> "Step 4/4: Tap the handlebar grip"
            is WaitingForCrankLength -> "Enter crank length (mm)"
            is ReadyToConfirm -> "Review and confirm calibration"
            is Confirmed -> "Calibration complete"
        }
    }
    
    /**
     * Returns the current point type being collected.
     */
    fun getCurrentPointType(): BikeReferencePointType? {
        return when (this) {
            is WaitingForSaddle -> BikeReferencePointType.SADDLE_TOP
            is WaitingForBottomBracket -> BikeReferencePointType.BOTTOM_BRACKET
            is WaitingForSpindle -> BikeReferencePointType.SPINDLE
            is WaitingForHandlebar -> BikeReferencePointType.HANDLEBAR
            else -> null
        }
    }
}
