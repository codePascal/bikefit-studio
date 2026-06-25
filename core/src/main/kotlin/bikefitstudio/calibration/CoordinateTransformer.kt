package bikefitstudio.calibration

import bikefitstudio.pose.Landmark
import bikefitstudio.pose.PoseFrame
import bikefitstudio.pose.PoseResult
import kotlin.math.sqrt

/**
 * Transforms pose landmarks to a bike-relative coordinate system.
 * 
 * This class normalizes pose landmark coordinates based on the bike's
 * calibrated reference points (saddle, bottom bracket, handlebar).
 * 
 * The bike coordinate system uses:
 * - Origin: Bottom bracket position
 * - X-axis: Positive towards handlebar (horizontal)
 * - Y-axis: Positive upward (vertical)
 * - Scale: Normalized to the distance from bottom bracket to saddle
 * 
 * This allows pose analysis to be independent of:
 * - Camera position and distance
 * - Bike position in frame
 * - Image resolution
 * 
 * Usage:
 * ```
 * val transformer = CoordinateTransformer(calibration)
 * val normalizedLandmarks = transformer.transformLandmarks(rawLandmarks)
 * ```
 */
class CoordinateTransformer(
    private val calibration: BikeCalibration
) {
    /**
     * Reference scale: distance from bottom bracket to saddle top.
     * All coordinates are normalized to this distance.
     */
    private val referenceScale: Float

    /**
     * Origin point (bottom bracket position).
     */
    private val origin: BikeReferencePoint

    init {
        require(calibration.hasAllPoints) { 
            "Calibration must have all reference points to create transformer" 
        }
        
        origin = calibration.bottomBracket!!
        
        // Calculate reference scale as distance from BB to saddle
        val saddle = calibration.saddleTop!!
        referenceScale = distance(
            origin.x, origin.y,
            saddle.x, saddle.y
        )
        
        require(referenceScale > 0.001f) { 
            "Reference scale too small - saddle and BB may be at same position" 
        }
    }

    /**
     * Transforms a list of landmarks to bike-relative coordinates.
     * 
     * @param landmarks Raw landmarks in normalized image coordinates (0-1)
     * @return Landmarks in bike-relative coordinates
     */
    fun transformLandmarks(landmarks: List<Landmark>): List<Landmark> {
        return landmarks.map { transformLandmark(it) }
    }

    /**
     * Transforms a single landmark to bike-relative coordinates.
     * 
     * @param landmark Raw landmark in normalized image coordinates
     * @return Landmark in bike-relative coordinates
     */
    fun transformLandmark(landmark: Landmark): Landmark {
        // Step 1: Translate to origin at bottom bracket
        val translatedX = landmark.x - origin.x
        // Invert Y because image Y is top-down, but we want Y up
        val translatedY = origin.y - landmark.y
        
        // Step 2: Scale normalize to reference distance
        val normalizedX = translatedX / referenceScale
        val normalizedY = translatedY / referenceScale
        
        // Z coordinate is already relative, just scale it
        val normalizedZ = landmark.z / referenceScale
        
        return Landmark(
            x = normalizedX,
            y = normalizedY,
            z = normalizedZ,
            visibility = landmark.visibility,
            presence = landmark.presence
        )
    }

    /**
     * Transforms a PoseResult to bike-relative coordinates.
     * 
     * @param result Raw pose result
     * @return PoseResult with transformed landmarks
     */
    fun transformPoseResult(result: PoseResult): PoseResult {
        if (!result.isValid) return result
        
        return result.copy(
            landmarks = transformLandmarks(result.landmarks)
        )
    }

    /**
     * Transforms a PoseFrame to bike-relative coordinates.
     * 
     * @param frame Raw pose frame
     * @return PoseFrame with transformed landmarks
     */
    fun transformPoseFrame(frame: PoseFrame): PoseFrame {
        if (!frame.isValid) return frame
        
        return frame.copy(
            landmarks = transformLandmarks(frame.landmarks)
        )
    }

    /**
     * Gets the transformed position of a bike reference point.
     * Useful for verification and debugging.
     */
    fun getTransformedBikePoint(type: BikeReferencePointType): Pair<Float, Float> {
        val point = when (type) {
            BikeReferencePointType.SADDLE_TOP -> calibration.saddleTop!!
            BikeReferencePointType.BOTTOM_BRACKET -> calibration.bottomBracket!!
            BikeReferencePointType.SPINDLE -> error("Spindle position should be queried separately")
            BikeReferencePointType.HANDLEBAR -> calibration.handlebar!!
        }
        
        val translatedX = point.x - origin.x
        val translatedY = origin.y - point.y
        
        return Pair(
            translatedX / referenceScale,
            translatedY / referenceScale
        )
    }

    /**
     * Returns the reference scale (BB to saddle distance in normalized coords).
     */
    fun getReferenceScale(): Float = referenceScale

    companion object {
        /**
         * Calculates Euclidean distance between two points.
         */
        fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
            val dx = x2 - x1
            val dy = y2 - y1
            return sqrt(dx * dx + dy * dy)
        }

        /**
         * Pure function to translate a landmark relative to an origin.
         * 
         * @param landmark The landmark to translate
         * @param originX Origin X coordinate
         * @param originY Origin Y coordinate
         * @return Translated landmark
         */
        fun translateLandmark(
            landmark: Landmark,
            originX: Float,
            originY: Float
        ): Landmark {
            return landmark.copy(
                x = landmark.x - originX,
                y = originY - landmark.y  // Invert Y axis
            )
        }

        /**
         * Pure function to scale a landmark.
         * 
         * @param landmark The landmark to scale
         * @param scale Scale factor to divide by
         * @return Scaled landmark
         */
        fun scaleLandmark(landmark: Landmark, scale: Float): Landmark {
            require(scale > 0) { "Scale must be positive" }
            return landmark.copy(
                x = landmark.x / scale,
                y = landmark.y / scale,
                z = landmark.z / scale
            )
        }

        /**
         * Pure function to transform landmarks using calibration.
         * 
         * @param landmarks Raw landmarks
         * @param calibration Bike calibration data
         * @return Transformed landmarks in bike-relative coordinates
         */
        fun transform(
            landmarks: List<Landmark>,
            calibration: BikeCalibration
        ): List<Landmark> {
            require(calibration.hasAllPoints) { "Calibration must have all reference points" }
            
            val origin = calibration.bottomBracket!!
            val saddle = calibration.saddleTop!!
            val scale = distance(origin.x, origin.y, saddle.x, saddle.y)
            
            return landmarks.map { landmark ->
                val translated = translateLandmark(landmark, origin.x, origin.y)
                scaleLandmark(translated, scale)
            }
        }

        /**
         * Creates a transformer from the current calibration repository.
         * 
         * @return CoordinateTransformer if valid calibration exists, null otherwise
         */
        fun fromRepository(): CoordinateTransformer? {
            val calibration = CalibrationRepository.getCalibration()
            return if (calibration?.hasAllPoints == true) {
                CoordinateTransformer(calibration)
            } else {
                null
            }
        }
    }
}
