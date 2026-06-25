package bikefitstudio.desktop

import bikefitstudio.biomechanics.AnkleAngleCalculator
import bikefitstudio.biomechanics.BodySide
import bikefitstudio.biomechanics.CrankPosition
import bikefitstudio.biomechanics.CrankPositionDetector
import bikefitstudio.biomechanics.CycleAggregator
import bikefitstudio.biomechanics.HipAngleCalculator
import bikefitstudio.biomechanics.KneeAngleCalculator
import bikefitstudio.biomechanics.KneeOverPedalOffset
import bikefitstudio.biomechanics.TorsoAngleCalculator
import bikefitstudio.biomechanics.TorsoAngleResult
import bikefitstudio.calibration.BikeCalibration
import bikefitstudio.fit.FitAnalysisInput
import bikefitstudio.fit.FitBias
import bikefitstudio.fit.FitEngine
import bikefitstudio.fit.FitEngineConfig
import bikefitstudio.fit.FitSummary
import bikefitstudio.fit.RidingContext
import bikefitstudio.pose.PoseFrame
import bikefitstudio.pose.PoseLandmarkIndex
import kotlin.math.atan2

/**
 * Drives the reused biomechanics pipeline over a stream of pose frames to produce a [FitSummary].
 *
 * Per frame it records joint angles, then edge-triggers on crank position (TDC/BDC/3 o'clock) to
 * delimit pedal cycles, record KOPS, and feed the [CycleAggregator]. [finish] runs the [FitEngine]
 * over the aggregated cycles.
 *
 * This is the headless port of the Android VideoAnalysisActivity orchestration.
 */
class BikeFitAnalyzer(
    private val calibration: BikeCalibration,
    private val side: BodySide = BodySide.LEFT
) {
    private val aggregator = CycleAggregator(side)
    private val threeOClockFrames = mutableListOf<PoseFrame>()
    private val torsoResultsAt3oClock = mutableListOf<TorsoAngleResult>()
    private var lastPosition: CrankPosition? = null

    val cycleCount: Int
        get() = aggregator.getCycleCount()

    fun process(frame: PoseFrame) {
        val knee =
            KneeAngleCalculator.calculateKneeAngleFromLandmarks(frame.landmarks, side)
                .takeIf { it.isValid }
                ?.angle
        val hip =
            HipAngleCalculator.calculateHipAngleFromLandmarks(frame.landmarks, side)
                .takeIf { it.isValid }
                ?.angle
        val ankle =
            AnkleAngleCalculator.calculateAnkleAngleFromLandmarks(frame.landmarks, side)
                .takeIf { it.isValid }
                ?.angle
        val torsoResult =
            TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(
                frame.landmarks,
                side,
                imageWidth = frame.imageWidth,
                imageHeight = frame.imageHeight
            )
        val torso = torsoResult.takeIf { it.isValid }?.angle

        aggregator.addMeasurement(
            frame.frameNumber,
            frame.timestampMs,
            kneeAngle = knee,
            hipAngle = hip,
            torsoAngle = torso,
            ankleAngle = ankle
        )

        val crankAngle = crankAngleDegrees(frame)
        val event =
            crankAngle?.let {
                CrankPositionDetector.detectPosition(it, frame.frameNumber, frame.timestampMs, side)
            }
        if (event == null) {
            lastPosition = null
            return
        }
        if (event.position == lastPosition) return // de-bounce: act once per pass
        lastPosition = event.position

        when (event.position) {
            CrankPosition.BDC ->
                aggregator.endCycleAtBdc(
                    frame.frameNumber,
                    frame.timestampMs,
                    kneeAngle = knee,
                    ankleAngle = ankle
                )
            CrankPosition.TDC -> aggregator.recordTdc(kneeAngle = knee, hipAngle = hip)
            CrankPosition.THREE_O_CLOCK -> {
                val kops = KneeOverPedalOffset.computeAtFrame(frame, side, calibration, 0f)
                if (kops.isValid) aggregator.recordKopsAt3oClock(kops.normalizedOffset)
                threeOClockFrames.add(frame)
                if (torsoResult.isValid) torsoResultsAt3oClock.add(torsoResult)
            }
        }
    }

    fun finish(
        context: RidingContext = RidingContext.DEFAULT,
        bias: FitBias = FitBias.DEFAULT
    ): FitSummary {
        val input =
            FitAnalysisInput(
                cycleSummary = aggregator.getSummary(),
                cycleMetrics = aggregator.getCompletedCycles(),
                bikeCalibration = calibration,
                poseFramesAt3oClock = threeOClockFrames.toList(),
                torsoAngleResults = torsoResultsAt3oClock.toList()
            )
        val engine = FitEngine(FitEngineConfig.forContext(context, bias))
        val result = engine.analyze(input)
        return FitSummary.fromAnalysisResult(result, context, bias)
    }

    /**
     * Crank angle in [0,360), using the foot-to-BB vector — same convention as the core detectors.
     */
    private fun crankAngleDegrees(frame: PoseFrame): Float? {
        val bb = calibration.bottomBracket ?: return null
        val footIndex =
            if (side == BodySide.LEFT) PoseLandmarkIndex.LEFT_FOOT_INDEX
            else PoseLandmarkIndex.RIGHT_FOOT_INDEX
        val foot = frame.landmarks.getOrNull(footIndex) ?: return null
        if (foot.visibility < 0.5f) return null
        var deg =
            Math.toDegrees(atan2((foot.y - bb.y).toDouble(), (foot.x - bb.x).toDouble())).toFloat()
        if (deg < 0f) deg += 360f
        return deg
    }
}
