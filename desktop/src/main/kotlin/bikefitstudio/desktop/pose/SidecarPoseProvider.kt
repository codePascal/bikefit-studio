package bikefitstudio.desktop.pose

import bikefitstudio.pose.Landmark
import bikefitstudio.pose.PoseFrame
import bikefitstudio.pose.PoseLandmarkIndex
import bikefitstudio.pose.PoseProvider
import java.awt.image.BufferedImage
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStreamReader
import javax.imageio.ImageIO

/**
 * [PoseProvider] backed by a Python sidecar process (see pose_server.py).
 *
 * Protocol (binary in, text out, one exchange per frame):
 *   Kotlin -> Python : 4-byte big-endian length N, then N bytes of JPEG.
 *   Python -> Kotlin : one line — either "NONE", or 33*5 space-separated floats
 *                      (x y z visibility presence per landmark).
 * A length of 0 tells the server to exit.
 *
 * This keeps the same 33-landmark BlazePose schema the Android app uses, so all reused
 * biomechanics work unchanged.
 */
class SidecarPoseProvider(
    scriptPath: String,
    pythonExe: String = "python"
) : PoseProvider {

    private val process: Process = ProcessBuilder(pythonExe, scriptPath)
        .redirectError(ProcessBuilder.Redirect.INHERIT) // surface the server's stderr/logs
        .start()

    private val toServer = DataOutputStream(process.outputStream.buffered())
    private val fromServer = BufferedReader(InputStreamReader(process.inputStream))

    override fun detect(image: BufferedImage, frameNumber: Long, timestampMs: Long): PoseFrame? {
        val jpeg = ByteArrayOutputStream().use { bos ->
            ImageIO.write(ensureRgb(image), "jpg", bos)
            bos.toByteArray()
        }

        return synchronized(this) {
            if (!process.isAlive) return@synchronized null
            toServer.writeInt(jpeg.size)
            toServer.write(jpeg)
            toServer.flush()

            val line = fromServer.readLine()?.trim() ?: return@synchronized null
            if (line.isEmpty() || line == "NONE") return@synchronized null

            val parts = line.split(" ")
            val count = PoseLandmarkIndex.LANDMARK_COUNT
            if (parts.size < count * 5) return@synchronized null

            val landmarks = ArrayList<Landmark>(count)
            var i = 0
            repeat(count) {
                landmarks.add(
                    Landmark(
                        x = parts[i].toFloat(),
                        y = parts[i + 1].toFloat(),
                        z = parts[i + 2].toFloat(),
                        visibility = parts[i + 3].toFloat(),
                        presence = parts[i + 4].toFloat()
                    )
                )
                i += 5
            }
            PoseFrame(
                frameNumber = frameNumber,
                timestampMs = timestampMs,
                landmarks = landmarks,
                confidence = landmarks.map { it.visibility }.average().toFloat(),
                imageWidth = image.width,
                imageHeight = image.height
            )
        }
    }

    override fun close() {
        runCatching { synchronized(this) { toServer.writeInt(0); toServer.flush() } } // sentinel: stop
        runCatching { toServer.close() }
        runCatching { fromServer.close() }
        runCatching { process.waitFor() }
        runCatching { process.destroy() }
    }

    private fun ensureRgb(src: BufferedImage): BufferedImage {
        if (src.type == BufferedImage.TYPE_INT_RGB || src.type == BufferedImage.TYPE_3BYTE_BGR) return src
        val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.drawImage(src, 0, 0, null)
        g.dispose()
        return out
    }
}
