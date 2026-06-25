package bikefitstudio.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import bikefitstudio.biomechanics.BodySide
import bikefitstudio.biomechanics.KneeOverPedalOffset
import bikefitstudio.calibration.BikeCalibration
import bikefitstudio.calibration.BikeReferencePoint
import bikefitstudio.calibration.BikeReferencePointType
import bikefitstudio.desktop.camera.FrameSource
import bikefitstudio.desktop.camera.JavaCvFrameSource
import bikefitstudio.fit.FitBias
import bikefitstudio.fit.FitSummary
import bikefitstudio.fit.RidingContext
import bikefitstudio.pose.Landmark
import bikefitstudio.pose.PoseFrame
import bikefitstudio.pose.PoseLandmarkIndex
import bikefitstudio.pose.PoseProvider
import java.awt.FileDialog
import java.awt.Frame
import java.awt.image.BufferedImage
import java.io.File

private sealed interface Route {
    data object Home : Route

    data object SelfTest : Route

    data class Calibrate(val image: BufferedImage, val factory: () -> FrameSource) : Route

    data class Camera(val factory: () -> FrameSource, val calibration: BikeCalibration?) : Route

    data class Report(val summary: FitSummary) : Route
}

@Composable
fun App() {
    MaterialTheme {
        var route by remember { mutableStateOf<Route>(Route.Home) }
        var ridingContext by remember { mutableStateOf(RidingContext.DEFAULT) }
        var fitBias by remember { mutableStateOf(FitBias.DEFAULT) }
        val poseProvider: PoseProvider = remember { createPoseProvider() }

        Surface(modifier = Modifier.fillMaxSize()) {
            when (val r = route) {
                Route.Home ->
                    HomeScreen(
                        ridingContext = ridingContext,
                        fitBias = fitBias,
                        onContext = { ridingContext = it },
                        onBias = { fitBias = it },
                        onWebcam = {
                            route = Route.Camera({ JavaCvFrameSource.forWebcam(0) }, null)
                        },
                        onVideoFile = {
                            chooseVideoFile()?.let { path ->
                                route = Route.Camera({ JavaCvFrameSource.forVideoFile(path) }, null)
                            }
                        },
                        onCalibrate = {
                            chooseVideoFile()?.let { path ->
                                val factory = { JavaCvFrameSource.forVideoFile(path) }
                                grabFirstFrame(factory)?.let { image ->
                                    route = Route.Calibrate(image, factory)
                                }
                            }
                        },
                        onSelfTest = { route = Route.SelfTest }
                    )
                Route.SelfTest -> SelfTestScreen(onBack = { route = Route.Home })
                is Route.Calibrate ->
                    CalibrationScreen(
                        image = r.image,
                        onDone = { calibration -> route = Route.Camera(r.factory, calibration) },
                        onBack = { route = Route.Home }
                    )
                is Route.Camera ->
                    CameraScreen(
                        sourceFactory = r.factory,
                        poseProvider = poseProvider,
                        onBack = { route = Route.Home },
                        calibration = r.calibration,
                        ridingContext = ridingContext,
                        fitBias = fitBias,
                        onFitReady = { summary -> route = Route.Report(summary) }
                    )
                is Route.Report ->
                    FitReportScreen(summary = r.summary, onBack = { route = Route.Home })
            }
        }
    }
}

@Composable
private fun HomeScreen(
    ridingContext: RidingContext,
    fitBias: FitBias,
    onContext: (RidingContext) -> Unit,
    onBias: (FitBias) -> Unit,
    onWebcam: () -> Unit,
    onVideoFile: () -> Unit,
    onCalibrate: () -> Unit,
    onSelfTest: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("BikeFit Studio", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("See your fit. Fix your fit.")
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DropdownSelector("Context: ${ridingContext.displayName}", RidingContext.entries) {
                onContext(it)
            }
            Spacer(Modifier.width(12.dp))
            DropdownSelector("Bias: ${fitBias.displayName}", FitBias.entries) { onBias(it) }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onCalibrate) { Text("Calibrate from video…  (full fit)") }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onWebcam) { Text("Use webcam / USB phone (preview)") }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onVideoFile) { Text("Open video file… (preview)") }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onSelfTest) { Text("Core self-test (KOPS)") }
    }
}

@Composable
private fun <T> DropdownSelector(label: String, options: List<T>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }) { Text(label) }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.toString()) },
                onClick = {
                    onSelect(option)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun SelfTestScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Button(onClick = onBack) { Text("Back") }
        Spacer(Modifier.height(16.dp))
        Text("Core self-test (KOPS)", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(coreSelfTest())
    }
}

/** Opens a native file dialog and returns the chosen path, or null if cancelled. */
private fun chooseVideoFile(): String? {
    val dialog = FileDialog(null as Frame?, "Choose a side-view video", FileDialog.LOAD)
    dialog.isVisible = true
    val file = dialog.file ?: return null
    return File(dialog.directory, file).absolutePath
}

/** Opens a frame source, grabs its first frame, and closes it again. */
private fun grabFirstFrame(factory: () -> FrameSource): BufferedImage? {
    val source = runCatching { factory() }.getOrNull() ?: return null
    return try {
        source.nextFrame()?.image
    } finally {
        source.close()
    }
}

/**
 * Calls the reused biomechanics core to prove the desktop module links against it. Mirrors the
 * "knee forward of spindle" scenario from the core unit tests.
 */
private fun coreSelfTest(): String {
    val landmarks = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) { Landmark(0f, 0f, 0f, 1f, 1f) }
    landmarks[PoseLandmarkIndex.LEFT_HIP] = Landmark(0.3f, 0.3f, 0f, 1f, 1f)
    landmarks[PoseLandmarkIndex.LEFT_KNEE] = Landmark(0.7f, 0.5f, 0f, 1f, 1f)
    landmarks[PoseLandmarkIndex.LEFT_FOOT_INDEX] = Landmark(0.6f, 0.6f, 0f, 1f, 1f)
    val frame = PoseFrame(1L, 100L, landmarks, 0.9f)

    val calibration =
        BikeCalibration(
            saddleTop = BikeReferencePoint(BikeReferencePointType.SADDLE_TOP, 0.5f, 0.2f),
            bottomBracket = BikeReferencePoint(BikeReferencePointType.BOTTOM_BRACKET, 0.5f, 0.6f),
            handlebar = BikeReferencePoint(BikeReferencePointType.HANDLEBAR, 0.5f, 0.1f),
            crankLengthMm = 172
        )

    val result =
        KneeOverPedalOffset.computeAtFrame(frame, BodySide.LEFT, calibration, crankScale = 0.1f)
    return "valid=${result.isValid}  alignment=${result.alignment}  " +
        "normalizedOffset=${"%.3f".format(result.normalizedOffset)}  spindleX=${result.spindleX}"
}
