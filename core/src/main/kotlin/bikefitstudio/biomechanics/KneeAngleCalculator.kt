package bikefitstudio.biomechanics

import bikefitstudio.pose.Landmark
import bikefitstudio.pose.PoseFrame
import bikefitstudio.pose.PoseLandmarkIndex
import bikefitstudio.pose.PoseResult

/**
 * Represents which side of the body to analyze.
 */
enum class BodySide {
    LEFT,
    RIGHT
}

/**
 * Result of a knee angle calculation.
 * 
 * @param angle The knee angle in degrees (0-180). A straight leg is ~180°.
 * @param side Which leg was analyzed
 * @param isValid Whether all required landmarks were visible
 * @param confidence Average visibility of the landmarks used
 */
data class KneeAngleResult(
    val angle: Float,
    val side: BodySide,
    val isValid: Boolean,
    val confidence: Float
) {
    companion object {
        /**
         * Creates an invalid result when landmarks are not available.
         */
        fun invalid(side: BodySide): KneeAngleResult {
            return KneeAngleResult(
                angle = 0f,
                side = side,
                isValid = false,
                confidence = 0f
            )
        }
    }
}

/**
 * Calculates knee angles from pose landmarks.
 * 
 * The knee angle is measured as the angle at the knee joint formed by:
 * - The hip landmark
 * - The knee landmark (vertex)
 * - The ankle landmark
 * 
 * A fully extended leg has an angle close to 180°.
 * A bent knee has a smaller angle (e.g., 90° when bent at right angle).
 * 
 * For cycling, the knee angle at the bottom of the pedal stroke should
 * typically be between 140-150° for optimal efficiency.
 * 
 * All functions are pure - they take input and return output without
 * modifying any state.
 */
object KneeAngleCalculator {

    /** Minimum visibility threshold for landmarks to be considered valid */
    const val DEFAULT_VISIBILITY_THRESHOLD = 0.5f

    /**
     * Calculates the knee angle from a PoseResult.
     * 
     * @param poseResult The pose detection result containing landmarks
     * @param side Which leg to analyze (LEFT or RIGHT)
     * @param visibilityThreshold Minimum visibility for landmarks to be valid
     * @return KneeAngleResult with the calculated angle or invalid result
     */
    fun calculateKneeAngle(
        poseResult: PoseResult,
        side: BodySide,
        visibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD
    ): KneeAngleResult {
        if (!poseResult.isValid || poseResult.landmarks.isEmpty()) {
            return KneeAngleResult.invalid(side)
        }

        return calculateKneeAngleFromLandmarks(
            poseResult.landmarks,
            side,
            visibilityThreshold,
            poseResult.inputImageWidth,
            poseResult.inputImageHeight
        )
    }

    /**
     * Calculates the knee angle from a PoseFrame.
     * 
     * @param poseFrame The pose frame containing landmarks
     * @param side Which leg to analyze (LEFT or RIGHT)
     * @param visibilityThreshold Minimum visibility for landmarks to be valid
     * @return KneeAngleResult with the calculated angle or invalid result
     */
    fun calculateKneeAngle(
        poseFrame: PoseFrame,
        side: BodySide,
        visibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD
    ): KneeAngleResult {
        if (poseFrame.landmarks.isEmpty()) {
            return KneeAngleResult.invalid(side)
        }

        return calculateKneeAngleFromLandmarks(
            poseFrame.landmarks,
            side,
            visibilityThreshold,
            poseFrame.imageWidth,
            poseFrame.imageHeight
        )
    }

    /**
     * Calculates the knee angle from a list of landmarks.
     * 
     * @param landmarks List of 33 pose landmarks
     * @param side Which leg to analyze (LEFT or RIGHT)
     * @param visibilityThreshold Minimum visibility for landmarks to be valid
     * @param imageWidth Image width for aspect ratio correction (optional)
     * @param imageHeight Image height for aspect ratio correction (optional)
     * @return KneeAngleResult with the calculated angle or invalid result
     */
    fun calculateKneeAngleFromLandmarks(
        landmarks: List<Landmark>,
        side: BodySide,
        visibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD,
        imageWidth: Int = 0,
        imageHeight: Int = 0
    ): KneeAngleResult {
        if (landmarks.size < PoseLandmarkIndex.LANDMARK_COUNT) {
            return KneeAngleResult.invalid(side)
        }

        // Get landmark indices based on side
        val hipIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_HIP
        } else {
            PoseLandmarkIndex.RIGHT_HIP
        }
        
        val kneeIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_KNEE
        } else {
            PoseLandmarkIndex.RIGHT_KNEE
        }
        
        val ankleIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_ANKLE
        } else {
            PoseLandmarkIndex.RIGHT_ANKLE
        }

        // Get the landmarks
        val hip = landmarks[hipIndex]
        val knee = landmarks[kneeIndex]
        val ankle = landmarks[ankleIndex]

        // Check visibility
        if (!hip.isVisible(visibilityThreshold) ||
            !knee.isVisible(visibilityThreshold) ||
            !ankle.isVisible(visibilityThreshold)) {
            return KneeAngleResult.invalid(side)
        }

        // Calculate average confidence
        val confidence = (hip.visibility + knee.visibility + ankle.visibility) / 3f

        // Calculate angle using Vector2D
        val angle = calculateAngleAtKnee(hip, knee, ankle, imageWidth, imageHeight)

        return KneeAngleResult(
            angle = angle,
            side = side,
            isValid = true,
            confidence = confidence
        )
    }

    /**
     * Calculates both knee angles from a PoseResult.
     * 
     * @param poseResult The pose detection result
     * @param visibilityThreshold Minimum visibility for landmarks
     * @return Pair of KneeAngleResults (left, right)
     */
    fun calculateBothKneeAngles(
        poseResult: PoseResult,
        visibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD
    ): Pair<KneeAngleResult, KneeAngleResult> {
        val leftResult = calculateKneeAngle(poseResult, BodySide.LEFT, visibilityThreshold)
        val rightResult = calculateKneeAngle(poseResult, BodySide.RIGHT, visibilityThreshold)
        return Pair(leftResult, rightResult)
    }

    /**
     * Calculates both knee angles from a PoseFrame.
     * 
     * @param poseFrame The pose frame
     * @param visibilityThreshold Minimum visibility for landmarks
     * @return Pair of KneeAngleResults (left, right)
     */
    fun calculateBothKneeAngles(
        poseFrame: PoseFrame,
        visibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD
    ): Pair<KneeAngleResult, KneeAngleResult> {
        val leftResult = calculateKneeAngle(poseFrame, BodySide.LEFT, visibilityThreshold)
        val rightResult = calculateKneeAngle(poseFrame, BodySide.RIGHT, visibilityThreshold)
        return Pair(leftResult, rightResult)
    }

    /**
     * Internal function to calculate the angle at the knee joint.
     * 
     * Uses the Vector2D.angleAtVertex function to calculate the angle
     * formed at the knee between the hip and ankle.
     * 
     * @param hip The hip landmark
     * @param knee The knee landmark (vertex of the angle)
     * @param ankle The ankle landmark
     * @return The angle in degrees (0-180)
     */
    internal fun calculateAngleAtKnee(
        hip: Landmark,
        knee: Landmark,
        ankle: Landmark,
        imageWidth: Int = 0,
        imageHeight: Int = 0
    ): Float {
        val hipPoint = Vector2D.fromLandmark(hip, imageWidth, imageHeight)
        val kneePoint = Vector2D.fromLandmark(knee, imageWidth, imageHeight)
        val anklePoint = Vector2D.fromLandmark(ankle, imageWidth, imageHeight)

        return Vector2D.angleAtVertex(
            a = hipPoint,
            b = kneePoint,
            c = anklePoint
        )
    }

    /**
     * Calculates the knee angle from raw coordinate values.
     * 
     * This is a convenience function for direct calculation without
     * creating Landmark objects.
     * 
     * @param hipX Hip x coordinate
     * @param hipY Hip y coordinate
     * @param kneeX Knee x coordinate
     * @param kneeY Knee y coordinate
     * @param ankleX Ankle x coordinate
     * @param ankleY Ankle y coordinate
     * @return The knee angle in degrees (0-180)
     */
    fun calculateKneeAngleFromCoordinates(
        hipX: Float, hipY: Float,
        kneeX: Float, kneeY: Float,
        ankleX: Float, ankleY: Float
    ): Float {
        return Vector2D.angleAtVertex(
            a = Vector2D(hipX, hipY),
            b = Vector2D(kneeX, kneeY),
            c = Vector2D(ankleX, ankleY)
        )
    }
}
