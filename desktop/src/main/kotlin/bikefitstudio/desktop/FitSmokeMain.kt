package bikefitstudio.desktop

import bikefitstudio.calibration.BikeCalibration
import bikefitstudio.calibration.BikeReferencePoint
import bikefitstudio.calibration.BikeReferencePointType
import bikefitstudio.pose.Landmark
import bikefitstudio.pose.PoseFrame
import bikefitstudio.pose.PoseLandmarkIndex
import kotlin.math.cos
import kotlin.math.sin

/**
 * Headless check of the full fit pipeline ([BikeFitAnalyzer]) with synthetic pedaling: the foot
 * orbits the bottom bracket so the crank sweeps through TDC/3-o'clock/BDC, driving cycle
 * detection, aggregation, and the FitEngine — no GUI, no ML.
 */
fun main() {
    val bbX = 0.5f
    val bbY = 0.6f
    val radius = 0.18f

    val calibration = BikeCalibration(
        saddleTop = BikeReferencePoint(BikeReferencePointType.SADDLE_TOP, 0.45f, 0.25f),
        bottomBracket = BikeReferencePoint(BikeReferencePointType.BOTTOM_BRACKET, bbX, bbY),
        spindle = BikeReferencePoint(BikeReferencePointType.SPINDLE, bbX + radius, bbY),
        handlebar = BikeReferencePoint(BikeReferencePointType.HANDLEBAR, 0.30f, 0.30f),
        crankLengthMm = 172
    )

    val analyzer = BikeFitAnalyzer(calibration)
    var frameNumber = 0L
    repeat(6) { // revolutions
        for (deg in 0 until 360 step 10) {
            val a = Math.toRadians(deg.toDouble())
            val fx = (bbX + radius * cos(a)).toFloat()
            val fy = (bbY + radius * sin(a)).toFloat()

            val lms = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) { Landmark(0.5f, 0.5f, 0f, 1f, 1f) }
            lms[PoseLandmarkIndex.LEFT_SHOULDER] = Landmark(0.42f, 0.30f, 0f, 1f, 1f)
            lms[PoseLandmarkIndex.LEFT_HIP] = Landmark(0.45f, 0.45f, 0f, 1f, 1f)
            lms[PoseLandmarkIndex.LEFT_ANKLE] = Landmark(fx, fy - 0.03f, 0f, 1f, 1f)
            lms[PoseLandmarkIndex.LEFT_KNEE] = Landmark((0.45f + fx) / 2f + 0.05f, (0.45f + fy) / 2f, 0f, 1f, 1f)
            lms[PoseLandmarkIndex.LEFT_FOOT_INDEX] = Landmark(fx, fy, 0f, 1f, 1f)

            analyzer.process(PoseFrame(frameNumber, frameNumber * 33L, lms, 0.95f))
            frameNumber++
        }
    }

    val summary = analyzer.finish()
    println("[fit] cycles=${summary.cycleCount}  grade=${summary.grade}  issues=${summary.totalIssueCount}")
    if (summary.recommendations.isEmpty()) {
        println("[fit] (no recommendations)")
    } else {
        summary.recommendations.forEach { println("[fit]  - [${it.severity}] ${it.title} -> ${it.action}") }
    }
    println("[fit] done.")
}
