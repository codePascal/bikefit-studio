package bikefitstudio.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import bikefitstudio.calibration.BikeCalibration
import bikefitstudio.pose.PoseFrame
import bikefitstudio.pose.PoseLandmarkIndex

/** BlazePose body skeleton connections (face landmarks omitted for clarity). */
private val CONNECTIONS =
    listOf(
        11 to 12, // shoulders
        11 to 13,
        13 to 15, // left arm
        12 to 14,
        14 to 16, // right arm
        11 to 23,
        12 to 24,
        23 to 24, // torso
        23 to 25,
        25 to 27,
        27 to 29,
        29 to 31,
        27 to 31, // left leg
        24 to 26,
        26 to 28,
        28 to 30,
        30 to 32,
        28 to 32 // right leg
    )

private const val VISIBILITY = 0.5f
private val BONE_COLOR = Color(0xFF00E5FF)
private val JOINT_COLOR = Color(0xFFFFC107)

/**
 * Draws the 33-landmark skeleton over a frame shown with ContentScale.Fit. The same letterbox math
 * (scale + centering) is applied so the overlay lines up with the image.
 */
@Composable
fun PoseOverlay(pose: PoseFrame, imageWidth: Int, imageHeight: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (imageWidth <= 0 || imageHeight <= 0) return@Canvas
        val scale = minOf(size.width / imageWidth, size.height / imageHeight)
        val dispW = imageWidth * scale
        val dispH = imageHeight * scale
        val offX = (size.width - dispW) / 2f
        val offY = (size.height - dispH) / 2f

        fun point(index: Int): Offset? {
            val lm = pose.landmarks.getOrNull(index) ?: return null
            if (lm.visibility < VISIBILITY) return null
            return Offset(offX + lm.x * dispW, offY + lm.y * dispH)
        }

        for ((a, b) in CONNECTIONS) {
            val pa = point(a) ?: continue
            val pb = point(b) ?: continue
            drawLine(color = BONE_COLOR, start = pa, end = pb, strokeWidth = 3f)
        }
        for (i in pose.landmarks.indices) {
            val p = point(i) ?: continue
            drawCircle(color = JOINT_COLOR, radius = 4f, center = p)
        }
    }
}

private val BB_COLOR = Color(0xFFFFEB3B)
private val REF_COLOR = Color(0xFF8BC34A)
private val SPINDLE_COLOR = Color(0xFFFF1744)
private val KNEE_COLOR = Color(0xFF2196F3)

/**
 * Draws the calibration reference points, the KOPS plumb line through the marked spindle, and the
 * knee — so the knee-over-pedal-spindle relationship is visible on the frame.
 */
@Composable
fun ReferenceOverlay(
    calibration: BikeCalibration,
    pose: PoseFrame?,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (imageWidth <= 0 || imageHeight <= 0) return@Canvas
        val scale = minOf(size.width / imageWidth, size.height / imageHeight)
        val dispW = imageWidth * scale
        val dispH = imageHeight * scale
        val offX = (size.width - dispW) / 2f
        val offY = (size.height - dispH) / 2f
        fun pt(x: Float, y: Float) = Offset(offX + x * dispW, offY + y * dispH)

        calibration.saddleTop?.let { drawCircle(REF_COLOR, 5f, pt(it.x, it.y)) }
        calibration.handlebar?.let { drawCircle(REF_COLOR, 5f, pt(it.x, it.y)) }
        calibration.bottomBracket?.let { drawCircle(BB_COLOR, 6f, pt(it.x, it.y)) }
        calibration.spindle?.let { s ->
            val x = offX + s.x * dispW
            drawLine(
                SPINDLE_COLOR.copy(alpha = 0.5f),
                Offset(x, offY),
                Offset(x, offY + dispH),
                strokeWidth = 2f
            )
            drawCircle(SPINDLE_COLOR, 6f, pt(s.x, s.y))
        }
        pose?.landmarks?.getOrNull(PoseLandmarkIndex.LEFT_KNEE)?.let { knee ->
            if (knee.visibility >= VISIBILITY) drawCircle(KNEE_COLOR, 7f, pt(knee.x, knee.y))
        }
    }
}
