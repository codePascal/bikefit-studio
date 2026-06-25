package bikefitstudio.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import bikefitstudio.calibration.BikeCalibration
import bikefitstudio.calibration.BikeReferencePoint
import bikefitstudio.calibration.BikeReferencePointType
import java.awt.image.BufferedImage

private val STEPS = listOf(
    BikeReferencePointType.SADDLE_TOP to "saddle top",
    BikeReferencePointType.BOTTOM_BRACKET to "bottom bracket",
    BikeReferencePointType.SPINDLE to "pedal spindle (crank at 3 o'clock)",
    BikeReferencePointType.HANDLEBAR to "handlebar"
)

/**
 * Click-to-mark calibration: the user taps the four bike reference points on a still frame and
 * enters the crank length, producing a [BikeCalibration].
 */
@Composable
fun CalibrationScreen(
    image: BufferedImage,
    onDone: (BikeCalibration) -> Unit,
    onBack: () -> Unit
) {
    val bitmap = remember(image) { image.toComposeImageBitmap() }
    val points = remember { mutableStateMapOf<BikeReferencePointType, BikeReferencePoint>() }
    var stepIndex by remember { mutableStateOf(0) }
    var crankText by remember { mutableStateOf("172") }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("Back") }
            Spacer(Modifier.width(12.dp))
            val instruction = if (stepIndex < STEPS.size) "Click the ${STEPS[stepIndex].second}"
            else "All points marked — set crank length and start"
            Text(instruction)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Crank length (mm): ")
            OutlinedTextField(
                value = crankText,
                onValueChange = { v -> crankText = v.filter { it.isDigit() }.take(3) },
                singleLine = true,
                modifier = Modifier.width(110.dp)
            )
            Spacer(Modifier.width(12.dp))
            if (points.isNotEmpty()) {
                Button(onClick = { points.clear(); stepIndex = 0 }) { Text("Reset points") }
                Spacer(Modifier.width(12.dp))
            }
            val crank = crankText.toIntOrNull()
            val complete = points.size == STEPS.size && crank != null
            Button(
                enabled = complete,
                onClick = {
                    onDone(
                        BikeCalibration(
                            saddleTop = points[BikeReferencePointType.SADDLE_TOP],
                            bottomBracket = points[BikeReferencePointType.BOTTOM_BRACKET],
                            spindle = points[BikeReferencePointType.SPINDLE],
                            handlebar = points[BikeReferencePointType.HANDLEBAR],
                            crankLengthMm = crank
                        )
                    )
                }
            ) { Text("Start analysis") }
        }
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier.fillMaxSize()
                .onSizeChanged { boxSize = it }
                .pointerInput(stepIndex) {
                    detectTapGestures { offset ->
                        if (stepIndex >= STEPS.size) return@detectTapGestures
                        val norm = toNormalized(offset, boxSize, image.width, image.height)
                            ?: return@detectTapGestures
                        val type = STEPS[stepIndex].first
                        points[type] = BikeReferencePoint(type, norm.first, norm.second)
                        stepIndex += 1
                    }
                }
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = "calibration frame",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            CalibrationMarkers(points.values.toList(), image.width, image.height, Modifier.matchParentSize())
        }
    }
}

@Composable
private fun CalibrationMarkers(
    points: List<BikeReferencePoint>,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier
) {
    Canvas(modifier.fillMaxSize()) {
        if (imageWidth <= 0 || imageHeight <= 0) return@Canvas
        val scale = minOf(size.width / imageWidth, size.height / imageHeight)
        val dispW = imageWidth * scale
        val dispH = imageHeight * scale
        val offX = (size.width - dispW) / 2f
        val offY = (size.height - dispH) / 2f
        points.forEach { p ->
            drawCircle(
                color = Color(0xFFFF1744),
                radius = 7f,
                center = Offset(offX + p.x * dispW, offY + p.y * dispH)
            )
        }
    }
}

/** Maps a tap in the Box to normalized image coordinates, accounting for ContentScale.Fit. */
private fun toNormalized(offset: Offset, box: IntSize, imageWidth: Int, imageHeight: Int): Pair<Float, Float>? {
    if (box.width == 0 || box.height == 0 || imageWidth == 0 || imageHeight == 0) return null
    val scale = minOf(box.width.toFloat() / imageWidth, box.height.toFloat() / imageHeight)
    val dispW = imageWidth * scale
    val dispH = imageHeight * scale
    val offX = (box.width - dispW) / 2f
    val offY = (box.height - dispH) / 2f
    val nx = (offset.x - offX) / dispW
    val ny = (offset.y - offY) / dispH
    if (nx < 0f || nx > 1f || ny < 0f || ny > 1f) return null
    return nx to ny
}
