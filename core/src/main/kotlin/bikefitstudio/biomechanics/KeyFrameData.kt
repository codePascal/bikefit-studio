/*
 * Derived from BikefitApp (Renato Campos, Apache-2.0).
 * Modified 2026 for desktop reuse: android.graphics.Bitmap -> java.awt.image.BufferedImage.
 */
package bikefitstudio.biomechanics

import java.awt.image.BufferedImage
import bikefitstudio.pose.PoseFrame

/**
 * Represents a key frame captured at a critical pedal position with pose and angle data.
 * 
 * @param frameNumber Frame number in the video sequence
 * @param timestampMs Timestamp in milliseconds
 * @param position The critical pedal position (TDC, BDC, or 3 O'Clock)
 * @param bitmap Optional bitmap frame image for display
 * @param poseFrame The pose detection data at this frame
 * @param side Which leg this frame represents
 */
data class KeyFrameDataPoint(
    val frameNumber: Long,
    val timestampMs: Long,
    val position: CriticalPedalPosition,
    val bitmap: BufferedImage? = null,
    val poseFrame: PoseFrame? = null,
    val side: BodySide = BodySide.LEFT
)

/**
 * Represents the three critical pedal positions in the pedal stroke.
 */
enum class CriticalPedalPosition {
    /**
     * Top Dead Center - pedal at 12 o'clock position (highest point)
     * Used for: hip angle, knee angle, overall leg extension
     */
    TDC,
    
    /**
     * Bottom Dead Center - pedal at 6 o'clock position (lowest point)
     * Used for: knee extension angle, ankle angle, leg extension
     */
    BDC,
    
    /**
     * 3 O'Clock position - pedal at horizontal right position
     * Used for: KOPS (Knee Over Pedal Spindle) measurement
     */
    THREE_O_CLOCK
}

/**
 * Container for the three key frames captured during analysis.
 * 
 * Stores frame data at TDC, BDC, and 3 o'clock positions along with
 * their associated pose information for display in the final analysis report.
 * 
 * @param tdcFrame Frame at Top Dead Center (optional)
 * @param bdcFrame Frame at Bottom Dead Center (optional)
 * @param threeOClockFrame Frame at 3 O'Clock position (optional)
 * @param side Which leg these frames represent
 */
data class KeyFrameSet(
    val tdcFrame: KeyFrameDataPoint? = null,
    val bdcFrame: KeyFrameDataPoint? = null,
    val threeOClockFrame: KeyFrameDataPoint? = null,
    val side: BodySide = BodySide.LEFT
) {
    /**
     * Returns all captured key frames in order.
     */
    fun getAllFrames(): List<KeyFrameDataPoint> {
        return listOfNotNull(tdcFrame, bdcFrame, threeOClockFrame)
    }
    
    /**
     * Returns true if at least one key frame has been captured.
     */
    fun hasAnyFrames(): Boolean = getAllFrames().isNotEmpty()
    
    /**
     * Returns true if all three critical positions have been captured.
     */
    fun isComplete(): Boolean = tdcFrame != null && bdcFrame != null && threeOClockFrame != null
    
    companion object {
        /**
         * Creates an empty key frame set.
         */
        fun empty(side: BodySide = BodySide.LEFT): KeyFrameSet = KeyFrameSet(side = side)
    }
}
