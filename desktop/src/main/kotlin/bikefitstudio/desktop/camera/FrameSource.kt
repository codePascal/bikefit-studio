package bikefitstudio.desktop.camera

import java.awt.image.BufferedImage

/** A single decoded frame with the metadata the core pipeline needs. */
data class CapturedFrame(val image: BufferedImage, val frameNumber: Long, val timestampMs: Long)

/**
 * Platform-agnostic source of video frames — a webcam, a USB phone exposed as a webcam, or a video
 * file. Implementations decode to [BufferedImage] so they feed straight into the reused core via
 * [bikefitstudio.pose.PoseProvider].
 */
interface FrameSource : AutoCloseable {

    /** Frames per second (best-effort; webcams may report 0, in which case a default is used). */
    val frameRate: Double

    /** Grabs the next frame, or null when the stream ends. */
    fun nextFrame(): CapturedFrame?

    override fun close()
}
