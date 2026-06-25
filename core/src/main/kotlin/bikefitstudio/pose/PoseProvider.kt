package bikefitstudio.pose

import java.awt.image.BufferedImage

/**
 * Platform-agnostic seam for pose estimation.
 *
 * This is the single boundary that replaces the Android-only MediaPipe wrapper. A desktop
 * implementation may back it with an ONNX BlazePose model or a Python MediaPipe sidecar;
 * either way it must return the standard 33-landmark [PoseFrame] so all reused biomechanics
 * (which index landmarks via [PoseLandmarkIndex]) keep working unchanged.
 */
interface PoseProvider : AutoCloseable {

    /**
     * Detects a single pose in [image].
     *
     * @param image source frame
     * @param frameNumber sequential frame index
     * @param timestampMs frame timestamp in milliseconds
     * @return a 33-landmark [PoseFrame], or null if no pose was detected
     */
    fun detect(image: BufferedImage, frameNumber: Long, timestampMs: Long): PoseFrame?

    override fun close() {}
}
