package bikefitstudio.pose

/**
 * Result of pose validation, indicating whether a pose is usable for analysis.
 * 
 * @param isValid True if the pose passes all validation criteria
 * @param reason Human-readable reason if validation failed
 * @param issues List of specific validation issues found
 * @param missingLandmarks Indices of landmarks that are missing or below visibility threshold
 */
data class ValidationResult(
    val isValid: Boolean,
    val reason: String = "",
    val issues: List<ValidationIssue> = emptyList(),
    val missingLandmarks: List<Int> = emptyList()
) {
    companion object {
        /** Validation passed */
        val VALID = ValidationResult(isValid = true)
        
        /** Empty or null pose */
        val EMPTY_POSE = ValidationResult(
            isValid = false,
            reason = "No pose detected",
            issues = listOf(ValidationIssue.NO_POSE_DETECTED)
        )
    }
}

/**
 * Types of validation issues that can occur.
 */
enum class ValidationIssue {
    /** No pose was detected in the frame */
    NO_POSE_DETECTED,
    
    /** Overall pose confidence is below threshold */
    LOW_CONFIDENCE,
    
    /** Essential landmarks for bike fit are missing */
    MISSING_ESSENTIAL_LANDMARKS,
    
    /** Some landmarks have low visibility */
    LOW_LANDMARK_VISIBILITY,
    
    /** Pose appears to be facing wrong direction (not side view) */
    WRONG_ORIENTATION,
    
    /** Landmarks have invalid/impossible positions */
    INVALID_LANDMARK_POSITIONS
}

/**
 * Validates pose data for quality and completeness.
 * 
 * This class determines whether a pose detection result is suitable for
 * bike fit analysis. It checks overall confidence, landmark visibility,
 * and essential landmark presence.
 * 
 * Usage:
 * ```
 * val validator = PoseValidator()
 * val result = validator.validate(poseFrame, Side.LEFT)
 * if (result.isValid) {
 *     // Process the pose
 * } else {
 *     Log.d("Pose", "Skipping frame: ${result.reason}")
 * }
 * ```
 * 
 * @param minOverallConfidence Minimum overall pose confidence (0.0 to 1.0)
 * @param minLandmarkVisibility Minimum visibility for individual landmarks
 * @param minEssentialVisibility Minimum average visibility for essential landmarks
 */
class PoseValidator(
    val minOverallConfidence: Float = DEFAULT_MIN_CONFIDENCE,
    val minLandmarkVisibility: Float = DEFAULT_MIN_LANDMARK_VISIBILITY,
    val minEssentialVisibility: Float = DEFAULT_MIN_ESSENTIAL_VISIBILITY
) {
    init {
        require(minOverallConfidence in 0f..1f) { 
            "minOverallConfidence must be between 0.0 and 1.0" 
        }
        require(minLandmarkVisibility in 0f..1f) { 
            "minLandmarkVisibility must be between 0.0 and 1.0" 
        }
        require(minEssentialVisibility in 0f..1f) { 
            "minEssentialVisibility must be between 0.0 and 1.0" 
        }
    }

    /**
     * Validates a PoseFrame for bike fit analysis.
     * 
     * @param frame The pose frame to validate
     * @param side Which side of the body is being analyzed
     * @return ValidationResult indicating whether the pose is usable
     */
    fun validate(frame: PoseFrame, side: Side = Side.LEFT): ValidationResult {
        return validate(frame.toPoseResult(), side)
    }

    /**
     * Validates a PoseResult for bike fit analysis.
     * 
     * @param result The pose result to validate
     * @param side Which side of the body is being analyzed
     * @return ValidationResult indicating whether the pose is usable
     */
    fun validate(result: PoseResult, side: Side = Side.LEFT): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()
        val missingLandmarks = mutableListOf<Int>()

        // Check 1: Is there a pose at all?
        if (!result.isValid || result.landmarks.isEmpty()) {
            return ValidationResult.EMPTY_POSE
        }

        // Check 2: Overall confidence
        if (result.confidence < minOverallConfidence) {
            issues.add(ValidationIssue.LOW_CONFIDENCE)
        }

        // Check 3: Essential landmarks visibility
        val essentialIndices = getEssentialLandmarkIndices(side)
        val essentialLandmarks = essentialIndices.mapNotNull { index ->
            result.getLandmark(index)?.let { index to it }
        }

        // Check which essential landmarks are missing or low visibility
        for (index in essentialIndices) {
            val landmark = result.getLandmark(index)
            if (landmark == null || landmark.visibility < minLandmarkVisibility) {
                missingLandmarks.add(index)
            }
        }

        if (missingLandmarks.isNotEmpty()) {
            issues.add(ValidationIssue.MISSING_ESSENTIAL_LANDMARKS)
        }

        // Check 4: Average visibility of essential landmarks
        val avgEssentialVisibility = if (essentialLandmarks.isNotEmpty()) {
            essentialLandmarks.map { it.second.visibility }.average().toFloat()
        } else {
            0f
        }

        if (avgEssentialVisibility < minEssentialVisibility) {
            issues.add(ValidationIssue.LOW_LANDMARK_VISIBILITY)
        }

        // Determine if valid (no critical issues)
        val criticalIssues = listOf(
            ValidationIssue.NO_POSE_DETECTED,
            ValidationIssue.MISSING_ESSENTIAL_LANDMARKS,
            ValidationIssue.LOW_CONFIDENCE
        )
        val hasCriticalIssue = issues.any { it in criticalIssues }

        return ValidationResult(
            isValid = !hasCriticalIssue,
            reason = if (hasCriticalIssue) formatIssues(issues) else "",
            issues = issues,
            missingLandmarks = missingLandmarks
        )
    }

    /**
     * Quick check if a pose is valid without detailed analysis.
     */
    fun isValid(result: PoseResult, side: Side = Side.LEFT): Boolean {
        return validate(result, side).isValid
    }

    /**
     * Quick check if a pose frame is valid without detailed analysis.
     */
    fun isValid(frame: PoseFrame, side: Side = Side.LEFT): Boolean {
        return validate(frame, side).isValid
    }

    /**
     * Filters a list of pose results, keeping only valid ones.
     */
    fun filterValid(results: List<PoseResult>, side: Side = Side.LEFT): List<PoseResult> {
        return results.filter { isValid(it, side) }
    }

    /**
     * Filters a list of pose frames, keeping only valid ones.
     */
    fun filterValidFrames(frames: List<PoseFrame>, side: Side = Side.LEFT): List<PoseFrame> {
        return frames.filter { isValid(it, side) }
    }

    /**
     * Returns the count and percentage of valid poses in a list.
     */
    fun validationStats(results: List<PoseResult>, side: Side = Side.LEFT): ValidationStats {
        val validCount = results.count { isValid(it, side) }
        val percentage = if (results.isNotEmpty()) {
            (validCount.toFloat() / results.size) * 100f
        } else {
            0f
        }
        return ValidationStats(
            totalFrames = results.size,
            validFrames = validCount,
            invalidFrames = results.size - validCount,
            validPercentage = percentage
        )
    }

    /**
     * Gets the indices of essential landmarks for bike fit analysis.
     */
    private fun getEssentialLandmarkIndices(side: Side): List<Int> {
        return when (side) {
            Side.LEFT -> ESSENTIAL_LEFT_LANDMARKS
            Side.RIGHT -> ESSENTIAL_RIGHT_LANDMARKS
        }
    }

    /**
     * Formats validation issues into a human-readable string.
     */
    private fun formatIssues(issues: List<ValidationIssue>): String {
        if (issues.isEmpty()) return ""
        
        return issues.joinToString("; ") { issue ->
            when (issue) {
                ValidationIssue.NO_POSE_DETECTED -> "No pose detected"
                ValidationIssue.LOW_CONFIDENCE -> "Low confidence"
                ValidationIssue.MISSING_ESSENTIAL_LANDMARKS -> "Missing essential landmarks"
                ValidationIssue.LOW_LANDMARK_VISIBILITY -> "Low landmark visibility"
                ValidationIssue.WRONG_ORIENTATION -> "Wrong orientation"
                ValidationIssue.INVALID_LANDMARK_POSITIONS -> "Invalid positions"
            }
        }
    }

    companion object {
        /** 
         * Default minimum overall pose confidence.
         * Poses below this threshold are considered unreliable.
         */
        const val DEFAULT_MIN_CONFIDENCE = 0.5f

        /**
         * Default minimum visibility for individual landmarks.
         * Landmarks below this are considered missing/occluded.
         */
        const val DEFAULT_MIN_LANDMARK_VISIBILITY = 0.5f

        /**
         * Default minimum average visibility for essential landmarks.
         * Determines if enough of the key landmarks are visible.
         */
        const val DEFAULT_MIN_ESSENTIAL_VISIBILITY = 0.6f

        /**
         * Strict thresholds for high-quality analysis.
         */
        const val STRICT_MIN_CONFIDENCE = 0.7f
        const val STRICT_MIN_LANDMARK_VISIBILITY = 0.7f
        const val STRICT_MIN_ESSENTIAL_VISIBILITY = 0.8f

        /**
         * Lenient thresholds for challenging conditions.
         */
        const val LENIENT_MIN_CONFIDENCE = 0.3f
        const val LENIENT_MIN_LANDMARK_VISIBILITY = 0.3f
        const val LENIENT_MIN_ESSENTIAL_VISIBILITY = 0.4f

        /**
         * Essential landmarks for left side bike fit analysis.
         * These must be visible for accurate angle calculations.
         */
        val ESSENTIAL_LEFT_LANDMARKS = listOf(
            PoseLandmarkIndex.LEFT_SHOULDER,
            PoseLandmarkIndex.LEFT_HIP,
            PoseLandmarkIndex.LEFT_KNEE,
            PoseLandmarkIndex.LEFT_ANKLE
        )

        /**
         * Essential landmarks for right side bike fit analysis.
         */
        val ESSENTIAL_RIGHT_LANDMARKS = listOf(
            PoseLandmarkIndex.RIGHT_SHOULDER,
            PoseLandmarkIndex.RIGHT_HIP,
            PoseLandmarkIndex.RIGHT_KNEE,
            PoseLandmarkIndex.RIGHT_ANKLE
        )

        /**
         * All landmarks useful for bike fit analysis (both sides).
         */
        val BIKE_FIT_LANDMARKS = listOf(
            // Upper body
            PoseLandmarkIndex.LEFT_SHOULDER,
            PoseLandmarkIndex.RIGHT_SHOULDER,
            PoseLandmarkIndex.LEFT_ELBOW,
            PoseLandmarkIndex.RIGHT_ELBOW,
            PoseLandmarkIndex.LEFT_WRIST,
            PoseLandmarkIndex.RIGHT_WRIST,
            // Lower body
            PoseLandmarkIndex.LEFT_HIP,
            PoseLandmarkIndex.RIGHT_HIP,
            PoseLandmarkIndex.LEFT_KNEE,
            PoseLandmarkIndex.RIGHT_KNEE,
            PoseLandmarkIndex.LEFT_ANKLE,
            PoseLandmarkIndex.RIGHT_ANKLE,
            PoseLandmarkIndex.LEFT_HEEL,
            PoseLandmarkIndex.RIGHT_HEEL,
            PoseLandmarkIndex.LEFT_FOOT_INDEX,
            PoseLandmarkIndex.RIGHT_FOOT_INDEX
        )

        /**
         * Creates a validator with strict thresholds.
         */
        fun strict(): PoseValidator = PoseValidator(
            minOverallConfidence = STRICT_MIN_CONFIDENCE,
            minLandmarkVisibility = STRICT_MIN_LANDMARK_VISIBILITY,
            minEssentialVisibility = STRICT_MIN_ESSENTIAL_VISIBILITY
        )

        /**
         * Creates a validator with lenient thresholds.
         */
        fun lenient(): PoseValidator = PoseValidator(
            minOverallConfidence = LENIENT_MIN_CONFIDENCE,
            minLandmarkVisibility = LENIENT_MIN_LANDMARK_VISIBILITY,
            minEssentialVisibility = LENIENT_MIN_ESSENTIAL_VISIBILITY
        )
    }
}

/**
 * Statistics about pose validation for a sequence of frames.
 */
data class ValidationStats(
    val totalFrames: Int,
    val validFrames: Int,
    val invalidFrames: Int,
    val validPercentage: Float
) {
    /**
     * Returns true if enough frames are valid for analysis.
     * Typically need at least 70% valid frames.
     */
    fun hasEnoughValidFrames(minPercentage: Float = 70f): Boolean {
        return validPercentage >= minPercentage
    }
}
