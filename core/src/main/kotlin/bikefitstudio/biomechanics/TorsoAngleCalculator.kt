package bikefitstudio.biomechanics

import bikefitstudio.pose.Landmark
import bikefitstudio.pose.PoseFrame
import bikefitstudio.pose.PoseLandmarkIndex
import bikefitstudio.pose.PoseResult

/**
 * Result of a torso angle calculation.
 * 
 * The torso angle is the lean angle of the torso relative to the ground,
 * measured from the shoulder-hip line.
 * 
 * Convention:
 * - 0° = horizontal (torso parallel to ground, very aggressive aero)
 * - 90° = vertical (torso perpendicular to ground, fully upright)
 * 
 * @param angle The torso angle in degrees (0-90 typically)
 * @param side Which side was analyzed
 * @param isValid Whether all required landmarks were visible
 * @param confidence Average visibility of the landmarks used
 */
data class TorsoAngleResult(
    val angle: Float,
    val side: BodySide,
    val isValid: Boolean,
    val confidence: Float
) {
    companion object {
        /**
         * Creates an invalid result when landmarks are not available.
         */
        fun invalid(side: BodySide): TorsoAngleResult {
            return TorsoAngleResult(
                angle = 0f,
                side = side,
                isValid = false,
                confidence = 0f
            )
        }
    }
}

/**
 * Calculates torso lean angle from pose landmarks.
 * 
 * The torso angle is measured as the angle between the shoulder-hip line
 * and the horizontal (ground) reference.
 * 
 * Convention:
 * - 0° = horizontal (torso parallel to ground)
 * - 90° = vertical (torso perpendicular to ground)
 * 
 * For cycling:
 * - Time trial/aero positions: 15-30°
 * - Road racing positions: 30-45°
 * - Endurance/comfort positions: 45-60°
 * - Upright/city positions: 60-80°
 * 
 * Note: In image coordinates, Y increases downward, so we account for
 * this when calculating the angle.
 * 
 * All functions are pure - they take input and return output without
 * modifying any state.
 */
object TorsoAngleCalculator {

    /** Minimum visibility threshold for landmarks to be considered valid */
    const val DEFAULT_VISIBILITY_THRESHOLD = 0.5f

    /**
     * Calculates the torso angle from a PoseResult.
     * 
     * @param poseResult The pose detection result containing landmarks
     * @param side Which side to analyze (LEFT or RIGHT)
     * @param visibilityThreshold Minimum visibility for landmarks to be valid
     * @return TorsoAngleResult with the calculated angle or invalid result
     */
    fun calculateTorsoAngle(
        poseResult: PoseResult,
        side: BodySide,
        visibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD
    ): TorsoAngleResult {
        if (!poseResult.isValid || poseResult.landmarks.isEmpty()) {
            return TorsoAngleResult.invalid(side)
        }

        return calculateTorsoAngleFromLandmarks(
            poseResult.landmarks,
            side,
            visibilityThreshold,
            poseResult.inputImageWidth,
            poseResult.inputImageHeight
        )
    }

    /**
     * Calculates the torso angle from a PoseFrame.
     * 
     * @param poseFrame The pose frame containing landmarks
     * @param side Which side to analyze (LEFT or RIGHT)
     * @param visibilityThreshold Minimum visibility for landmarks to be valid
     * @return TorsoAngleResult with the calculated angle or invalid result
     */
    fun calculateTorsoAngle(
        poseFrame: PoseFrame,
        side: BodySide,
        visibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD
    ): TorsoAngleResult {
        if (poseFrame.landmarks.isEmpty()) {
            return TorsoAngleResult.invalid(side)
        }

        return calculateTorsoAngleFromLandmarks(
            poseFrame.landmarks,
            side,
            visibilityThreshold,
            poseFrame.imageWidth,
            poseFrame.imageHeight
        )
    }

    /**
     * Calculates the torso angle from a list of landmarks.
     * 
     * @param landmarks List of 33 pose landmarks
     * @param side Which side to analyze (LEFT or RIGHT)
     * @param visibilityThreshold Minimum visibility for landmarks to be valid
     * @param imageWidth Image width for aspect ratio correction (optional)
     * @param imageHeight Image height for aspect ratio correction (optional)
     * @return TorsoAngleResult with the calculated angle or invalid result
     */
    fun calculateTorsoAngleFromLandmarks(
        landmarks: List<Landmark>,
        side: BodySide,
        visibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD,
        imageWidth: Int = 0,
        imageHeight: Int = 0
    ): TorsoAngleResult {
        if (landmarks.size < PoseLandmarkIndex.LANDMARK_COUNT) {
            return TorsoAngleResult.invalid(side)
        }

        // Get landmark indices based on side
        val shoulderIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_SHOULDER
        } else {
            PoseLandmarkIndex.RIGHT_SHOULDER
        }

        val hipIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_HIP
        } else {
            PoseLandmarkIndex.RIGHT_HIP
        }

        // Get the landmarks
        val shoulder = landmarks[shoulderIndex]
        val hip = landmarks[hipIndex]

        // Check visibility
        if (!shoulder.isVisible(visibilityThreshold) ||
            !hip.isVisible(visibilityThreshold)) {
            return TorsoAngleResult.invalid(side)
        }

        // Calculate average confidence
        val confidence = (shoulder.visibility + hip.visibility) / 2f

        // Calculate angle using Vector2D
        val angle = calculateAngleToGround(shoulder, hip, imageWidth, imageHeight)

        return TorsoAngleResult(
            angle = angle,
            side = side,
            isValid = true,
            confidence = confidence
        )
    }

    /**
     * Calculates both torso angles from a PoseResult.
     * 
     * @param poseResult The pose detection result
     * @param visibilityThreshold Minimum visibility for landmarks
     * @return Pair of TorsoAngleResults (left, right)
     */
    fun calculateBothTorsoAngles(
        poseResult: PoseResult,
        visibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD
    ): Pair<TorsoAngleResult, TorsoAngleResult> {
        val leftResult = calculateTorsoAngle(poseResult, BodySide.LEFT, visibilityThreshold)
        val rightResult = calculateTorsoAngle(poseResult, BodySide.RIGHT, visibilityThreshold)
        return Pair(leftResult, rightResult)
    }

    /**
     * Calculates both torso angles from a PoseFrame.
     * 
     * @param poseFrame The pose frame
     * @param visibilityThreshold Minimum visibility for landmarks
     * @return Pair of TorsoAngleResults (left, right)
     */
    fun calculateBothTorsoAngles(
        poseFrame: PoseFrame,
        visibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD
    ): Pair<TorsoAngleResult, TorsoAngleResult> {
        val leftResult = calculateTorsoAngle(poseFrame, BodySide.LEFT, visibilityThreshold)
        val rightResult = calculateTorsoAngle(poseFrame, BodySide.RIGHT, visibilityThreshold)
        return Pair(leftResult, rightResult)
    }

    /**
     * Internal function to calculate the angle of the torso to the ground.
     * 
     * In image coordinates:
     * - Y increases downward
     * - Shoulder is typically above (smaller Y) than hip
     * 
     * We calculate the angle of the shoulder-to-hip vector from horizontal.
     * 
     * @param shoulder The shoulder landmark
     * @param hip The hip landmark
     * @param imageWidth Image width (optional, for aspect ratio correction)
     * @param imageHeight Image height (optional, for aspect ratio correction)
     * @return The angle in degrees from horizontal (0° = horizontal, 90° = vertical)
     */
    internal fun calculateAngleToGround(
        shoulder: Landmark,
        hip: Landmark,
        imageWidth: Int = 0,
        imageHeight: Int = 0
    ): Float {
        val shoulderPoint = Vector2D.fromLandmark(shoulder, imageWidth, imageHeight)
        val hipPoint = Vector2D.fromLandmark(hip, imageWidth, imageHeight)

        // Create vector from hip to shoulder (pointing up in body terms)
        val torsoVector = shoulderPoint - hipPoint

        // Horizontal reference (pointing right)
        val horizontal = Vector2D.RIGHT

        // Calculate angle between torso and horizontal
        // This gives us the angle from horizontal (0°) to vertical (90°)
        val angle = torsoVector.angleTo(horizontal)

        // The angle from angleTo is always positive (0-180)
        // For torso, we want the acute angle from horizontal
        // If angle > 90, the torso is leaning backward, return 180 - angle
        return if (angle > 90f) {
            180f - angle
        } else {
            angle
        }
    }

    /**
     * Calculates the torso angle from raw coordinate values.
     * 
     * This is a convenience function for direct calculation without
     * creating Landmark objects.
     * 
     * @param shoulderX Shoulder x coordinate
     * @param shoulderY Shoulder y coordinate
     * @param hipX Hip x coordinate
     * @param hipY Hip y coordinate
     * @return The torso angle in degrees from horizontal (0° = horizontal, 90° = vertical)
     */
    fun calculateTorsoAngleFromCoordinates(
        shoulderX: Float, shoulderY: Float,
        hipX: Float, hipY: Float
    ): Float {
        val shoulderPoint = Vector2D(shoulderX, shoulderY)
        val hipPoint = Vector2D(hipX, hipY)

        val torsoVector = shoulderPoint - hipPoint
        val horizontal = Vector2D.RIGHT

        val angle = torsoVector.angleTo(horizontal)

        return if (angle > 90f) {
            180f - angle
        } else {
            angle
        }
    }

    /**
     * Calculates the average torso angle from both sides.
     * 
     * This is useful when both sides are visible and you want a single
     * representative angle.
     * 
     * @param poseResult The pose detection result
     * @param visibilityThreshold Minimum visibility for landmarks
     * @return TorsoAngleResult with average angle, or invalid if neither side is valid
     */
    fun calculateAverageTorsoAngle(
        poseResult: PoseResult,
        visibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD
    ): TorsoAngleResult {
        val (left, right) = calculateBothTorsoAngles(poseResult, visibilityThreshold)

        return when {
            left.isValid && right.isValid -> {
                TorsoAngleResult(
                    angle = (left.angle + right.angle) / 2f,
                    side = BodySide.LEFT, // Arbitrary, could add a BOTH option
                    isValid = true,
                    confidence = (left.confidence + right.confidence) / 2f
                )
            }
            left.isValid -> left
            right.isValid -> right
            else -> TorsoAngleResult.invalid(BodySide.LEFT)
        }
    }
}
