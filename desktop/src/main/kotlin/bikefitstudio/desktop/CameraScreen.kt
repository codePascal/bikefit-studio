package bikefitstudio.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import bikefitstudio.biomechanics.AnkleAngleCalculator
import bikefitstudio.biomechanics.BodySide
import bikefitstudio.biomechanics.HipAngleCalculator
import bikefitstudio.biomechanics.KneeAngleCalculator
import bikefitstudio.biomechanics.KneeOverPedalOffset
import bikefitstudio.biomechanics.TorsoAngleCalculator
import bikefitstudio.calibration.BikeCalibration
import bikefitstudio.desktop.camera.FrameSource
import bikefitstudio.fit.FitBias
import bikefitstudio.fit.FitSummary
import bikefitstudio.fit.RidingContext
import bikefitstudio.pose.PoseFrame
import bikefitstudio.pose.PoseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Live preview with the pose skeleton overlaid and live left-side knee/hip angles. When a complete
 * [calibration] is supplied, KOPS is computed at 3 o'clock frames using the marked spindle.
 */
@Composable
fun CameraScreen(
    sourceFactory: () -> FrameSource,
    poseProvider: PoseProvider,
    onBack: () -> Unit,
    calibration: BikeCalibration? = null,
    ridingContext: RidingContext = RidingContext.DEFAULT,
    fitBias: FitBias = FitBias.DEFAULT,
    onFitReady: ((FitSummary) -> Unit)? = null
) {
    val analyzer =
        remember(calibration) { calibration?.takeIf { it.isComplete }?.let { BikeFitAnalyzer(it) } }
    var finishRequested by remember { mutableStateOf(false) }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var imageWidth by remember { mutableStateOf(0) }
    var imageHeight by remember { mutableStateOf(0) }
    var pose by remember { mutableStateOf<PoseFrame?>(null) }
    var status by remember { mutableStateOf("Opening source…") }
    var frameCount by remember { mutableStateOf(0L) }
    var posesDetected by remember { mutableStateOf(0L) }
    var readout by remember { mutableStateOf("") }
    var kopsReadout by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val source =
            withContext(Dispatchers.IO) {
                runCatching { sourceFactory() }
                    .getOrElse {
                        status = "Failed to open source: ${it.message}"
                        null
                    }
            } ?: return@LaunchedEffect

        status = "Streaming @ ${"%.0f".format(source.frameRate)} fps"
        val frameDelayMs = (1000.0 / source.frameRate).toLong().coerceAtLeast(1L)
        try {
            while (isActive && !finishRequested) {
                val frame = withContext(Dispatchers.IO) { source.nextFrame() } ?: break
                bitmap = frame.image.toComposeImageBitmap()
                imageWidth = frame.image.width
                imageHeight = frame.image.height
                frameCount = frame.frameNumber + 1

                val detected =
                    withContext(Dispatchers.IO) {
                        poseProvider.detect(frame.image, frame.frameNumber, frame.timestampMs)
                    }
                pose = detected
                if (detected != null) {
                    posesDetected++
                    readout = angleReadout(detected)
                    analyzer?.process(detected)
                    val cal = calibration
                    if (
                        cal != null &&
                            cal.isComplete &&
                            KneeOverPedalOffset.isAtThreeOClock(detected, BodySide.LEFT, cal)
                    ) {
                        val kops =
                            KneeOverPedalOffset.computeAtFrame(detected, BodySide.LEFT, cal, 0f)
                        if (kops.isValid) {
                            kopsReadout =
                                "KOPS @3 o'clock: ${kops.alignment} " +
                                    "(offset ${"%.3f".format(kops.normalizedOffset)})"
                        }
                    }
                }
                delay(frameDelayMs)
            }
            if (status.startsWith("Streaming")) status = "Stream ended"
        } finally {
            withContext(Dispatchers.IO) { source.close() }
        }
        val a = analyzer
        if (a != null && onFitReady != null) {
            status = "Analyzing ${a.cycleCount} cycles…"
            val summary = withContext(Dispatchers.Default) { a.finish(ridingContext, fitBias) }
            onFitReady(summary)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("Back") }
            if (analyzer != null) {
                Spacer(Modifier.width(12.dp))
                Button(enabled = !finishRequested, onClick = { finishRequested = true }) {
                    Text("Finish & report")
                }
            }
            Spacer(Modifier.width(16.dp))
            Text("$status   •   frames=$frameCount   •   poses=$posesDetected")
        }
        if (readout.isNotEmpty()) {
            Spacer(Modifier.padding(4.dp))
            Text(readout)
        }
        if (kopsReadout.isNotEmpty()) {
            Spacer(Modifier.padding(2.dp))
            Text(kopsReadout)
        }
        Spacer(Modifier.padding(8.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            val current = bitmap
            if (current != null) {
                Image(
                    bitmap = current,
                    contentDescription = "camera frame",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                pose?.let { PoseOverlay(it, imageWidth, imageHeight, Modifier.matchParentSize()) }
                calibration?.let {
                    ReferenceOverlay(it, pose, imageWidth, imageHeight, Modifier.matchParentSize())
                }
            } else {
                Text("Waiting for first frame…")
            }
        }
    }
}

private fun angleReadout(pose: PoseFrame): String {
    val knee = KneeAngleCalculator.calculateKneeAngleFromLandmarks(pose.landmarks, BodySide.LEFT)
    val hip = HipAngleCalculator.calculateHipAngleFromLandmarks(pose.landmarks, BodySide.LEFT)
    val torso =
        TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(
            pose.landmarks,
            BodySide.LEFT,
            imageWidth = pose.imageWidth,
            imageHeight = pose.imageHeight
        )
    val ankle = AnkleAngleCalculator.calculateAnkleAngleFromLandmarks(pose.landmarks, BodySide.LEFT)
    fun fmt(valid: Boolean, angle: Float) = if (valid) "${"%.0f".format(angle)}°" else "—"
    return "Knee ${fmt(knee.isValid, knee.angle)}   " +
        "Hip ${fmt(hip.isValid, hip.angle)}   " +
        "Torso ${fmt(torso.isValid, torso.angle)}   " +
        "Ankle ${fmt(ankle.isValid, ankle.angle)}"
}
