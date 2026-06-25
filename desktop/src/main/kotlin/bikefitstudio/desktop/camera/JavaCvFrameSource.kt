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
 */
class JavaCvFrameSource
private constructor(private val grabber: FrameGrabber, override val frameRate: Double) :
    FrameSource {

    private val converter = Java2DFrameConverter()
    private var index = 0L

    override fun nextFrame(): CapturedFrame? {
        // grab() returns interleaved frames; skip any without an image plane (e.g. audio).
        while (true) {
            val frame = grabber.grab() ?: return null
            if (frame.image == null) continue
            val decoded = converter.convert(frame) ?: continue
            // The converter reuses its buffer between calls, so hand out a private copy.
            val image = deepCopy(decoded)
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

    private fun deepCopy(src: BufferedImage): BufferedImage {
        val copy = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB)
        val g = copy.createGraphics()
        g.drawImage(src, 0, 0, null)
        g.dispose()
        return copy
    }

    companion object {
        private const val DEFAULT_FPS = 30.0

        fun forVideoFile(path: String): JavaCvFrameSource {
            val grabber = FFmpegFrameGrabber(path).apply { start() }
            val fps = grabber.frameRate.takeIf { it > 0.0 } ?: DEFAULT_FPS
            return JavaCvFrameSource(grabber, fps)
        }

        fun forWebcam(deviceIndex: Int = 0): JavaCvFrameSource {
            val grabber = OpenCVFrameGrabber(deviceIndex).apply { start() }
            val fps = grabber.frameRate.takeIf { it > 0.0 } ?: DEFAULT_FPS
            return JavaCvFrameSource(grabber, fps)
        }
    }
}
