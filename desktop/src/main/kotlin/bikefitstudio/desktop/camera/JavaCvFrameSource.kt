package bikefitstudio.desktop.camera

import java.awt.image.BufferedImage
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import org.bytedeco.javacv.OpenCVFrameGrabber

/**
 * [FrameSource] backed by JavaCV (OpenCV + FFmpeg).
 *
 * Use [forVideoFile] for a recorded clip, or [forWebcam] for a live camera. A USB phone running in
 * UVC webcam mode (Android 14 native, or DroidCam/Iriun) simply appears as another camera index, so
 * [forWebcam] handles it with no extra code.
 *
 * Phone recordings often carry display-rotation metadata that FFmpeg decode does not apply, so the
 * frame would otherwise appear rotated (e.g. upside down). [forVideoFile] reads that metadata and
 * [rotationDegrees] is applied to each frame.
 */
class JavaCvFrameSource
private constructor(
    private val grabber: FrameGrabber,
    override val frameRate: Double,
    private val rotationDegrees: Int,
) : FrameSource {

    private val converter = Java2DFrameConverter()
    private var index = 0L

    override fun nextFrame(): CapturedFrame? {
        // grab() returns interleaved frames; skip any without an image plane (e.g. audio).
        while (true) {
            val frame = grabber.grab() ?: return null
            if (frame.image == null) continue
            val decoded = converter.convert(frame) ?: continue
            // The converter reuses its buffer between calls, so hand out a private (and, if needed,
            // rotation-corrected) copy.
            val image = rotated(decoded, rotationDegrees)
            val tsMs =
                if (grabber.timestamp > 0L) grabber.timestamp / 1000L
                else (index * 1000.0 / frameRate).toLong()
            return CapturedFrame(image, index++, tsMs)
        }
    }

    override fun close() {
        runCatching { grabber.stop() }
        runCatching { grabber.release() }
        runCatching { converter.close() }
    }

    /**
     * Returns an independent copy of [src], rotated clockwise by [degrees] (one of 0/90/180/270). A
     * rotation of 0 still yields a private copy, which the caller relies on.
     */
    private fun rotated(src: BufferedImage, degrees: Int): BufferedImage {
        val swapsAxes = degrees == QUARTER_TURN || degrees == THREE_QUARTER_TURN
        val width = if (swapsAxes) src.height else src.width
        val height = if (swapsAxes) src.width else src.height
        val dst = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = dst.createGraphics()
        g.translate(((width - src.width) / 2).toDouble(), ((height - src.height) / 2).toDouble())
        g.rotate(
            Math.toRadians(degrees.toDouble()),
            (src.width / 2).toDouble(),
            (src.height / 2).toDouble()
        )
        g.drawImage(src, 0, 0, null)
        g.dispose()
        return dst
    }

    companion object {
        private const val DEFAULT_FPS = 30.0
        private const val QUARTER_TURN = 90
        private const val THREE_QUARTER_TURN = 270
        private const val QUARTER_TURNS = 4

        fun forVideoFile(path: String): JavaCvFrameSource {
            val grabber = FFmpegFrameGrabber(path).apply { start() }
            val fps = grabber.frameRate.takeIf { it > 0.0 } ?: DEFAULT_FPS
            return JavaCvFrameSource(grabber, fps, snapRotation(grabber.displayRotation))
        }

        fun forWebcam(deviceIndex: Int = 0): JavaCvFrameSource {
            val grabber = OpenCVFrameGrabber(deviceIndex).apply { start() }
            val fps = grabber.frameRate.takeIf { it > 0.0 } ?: DEFAULT_FPS
            return JavaCvFrameSource(grabber, fps, rotationDegrees = 0)
        }

        /** Snaps an arbitrary display rotation (degrees; may be NaN/negative) to 0/90/180/270. */
        private fun snapRotation(raw: Double): Int {
            if (raw.isNaN()) return 0
            val quarter = Math.round(raw / QUARTER_TURN).toInt()
            return (((quarter % QUARTER_TURNS) + QUARTER_TURNS) % QUARTER_TURNS) * QUARTER_TURN
        }
    }
}
