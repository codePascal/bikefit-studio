package bikefitstudio.biomechanics

import kotlin.math.abs
import kotlin.math.atan2

/**
 * Tracks continuous crank angle across all pedal cycle frames using an
 * [EllipticalCrankModel] that fits foot-Y motion to a sinusoidal + 2nd-harmonic model.
 *
 * The model derives crank angle from foot Y position assuming the pedal traces an
 * ellipse (circle projected by the side-view camera). This avoids the 0°/360°
 * discontinuity that plagued the previous atan2 + EMA approach.
 *
 * Features:
 * - **Elliptical fit**: y(t) = A·sin(ωt + φ) + B·cos(2ωt) + C
 * - **Sliding window**: last ~1.5 cycles for cadence-change responsiveness
 * - **Bootstrap from PedalCycleDetector**: seeds ω from BDC/TDC half-cycle spacing
 * - **RPM from ω directly**: stable, noise-free cadence
 * - **Pixel-to-meters mapping**: from crank radius amplitude vs. known crank length
 * - **Per-side tracking** (left and right legs)
 * - **Fallback atan2**: used only during the first few frames before the model converges
 *
 * Exposed metrics: current angle, instantaneous RPM, angular velocity, model confidence.
 */
object CrankAngleTracker {

    /**
     * Configuration for crank angle tracking.
     *
     * @param cadenceWindowMs Time window for legacy RPM calculation (unused by new model)
     * @param maxBufferSize Maximum entries in the measurement rolling buffer
     * @param enableFiltering Whether to use the elliptical model (true) or raw atan2 (false)
     * @param modelConfig Configuration for the [EllipticalCrankModel]
     * @param visibilityThreshold Minimum visibility for a measurement to be valid (0-1)
     * @param fpsFrameBuffer Number of frames to collect before computing adaptive FPS
     * @param minVisibilityForSide Minimum average visibility required for a side
     */
    data class Config(
        val cadenceWindowMs: Long = 500L,
        val maxBufferSize: Int = 30,
        val enableFiltering: Boolean = true,
        val modelConfig: EllipticalCrankModel.Config = EllipticalCrankModel.Config(),
        val visibilityThreshold: Float = 0.5f,
        val fpsFrameBuffer: Int = 10,
        val minVisibilityForSide: Float = 0.7f
    )

    /**
     * A single tracking measurement in the rolling window.
     *
     * @param frameNumber Frame identifier
     * @param timestampMs Timestamp in milliseconds
     * @param rawAngle Raw crank angle from atan2 (0-360°)
     * @param filteredAngle Model-derived angle (0-360°)
     * @param confidence Angle quality (0-1)
     * @param instantaneousRpm RPM from the elliptical model's ω
     */
    data class AngleMeasurement(
        val frameNumber: Long,
        val timestampMs: Long,
        val rawAngle: Float,
        val filteredAngle: Float,
        val confidence: Float,
        val instantaneousRpm: Float
    )

    /**
     * Represents tracked crank angle state for one body side.
     */
    class TrackingState(
        val modelState: EllipticalCrankModel.ModelState = EllipticalCrankModel.ModelState(),
        val measurementBuffer: MutableList<AngleMeasurement> = mutableListOf(),
        val fpsBuffer: MutableList<Long> = mutableListOf(),
        var lastAngle: Float? = null,
        var lastTimestampMs: Long? = null,
        var frameCount: Long = 0,
        var adaptiveFps: Float = 30f
    )

    /**
     * Result of tracking a frame.
     *
     * @param frameNumber Frame being processed
     * @param timestampMs Timestamp in milliseconds
     * @param rawAngle Raw crank angle from atan2 fallback (0-360°)
     * @param filteredAngle Model-derived angle (0-360°)
     * @param wasFiltered True if the model was not yet valid (used atan2 fallback)
     * @param instantaneousRpm RPM from the model
     * @param windowedRpm Same as instantaneousRpm (kept for API compat)
     * @param confidence Model fit confidence (0-1)
     */
    data class TrackingResult(
        val frameNumber: Long,
        val timestampMs: Long,
        val rawAngle: Float,
        val filteredAngle: Float,
        val wasFiltered: Boolean,
        val instantaneousRpm: Float,
        val windowedRpm: Float,
        val confidence: Float
    )

    // Per-side tracking state
    private val trackingStateLeft = TrackingState()
    private val trackingStateRight = TrackingState()

    /**
     * Computes raw crank angle from foot position relative to bottom bracket.
     *
     * Used as a fallback before the elliptical model converges, and for legacy callers.
     *
     * @param footX X coordinate of foot index landmark
     * @param footY Y coordinate of foot index landmark
     * @param bbX X coordinate of bottom bracket (crank pivot)
     * @param bbY Y coordinate of bottom bracket
     * @return Crank angle in degrees [0, 360)
     */
    fun computeRawCrankAngle(
        footX: Float,
        footY: Float,
        bbX: Float,
        bbY: Float
    ): Float {
        val dx = footX - bbX
        val dy = footY - bbY

        // Negate Y because MediaPipe Y increases downward (image space)
        // but crank angles need standard Cartesian orientation
        val crankAngleRadians = atan2(-dy.toDouble(), dx.toDouble())
        var crankAngleDegrees = Math.toDegrees(crankAngleRadians).toFloat()

        // Normalize to [0, 360)
        if (crankAngleDegrees < 0) crankAngleDegrees += 360f

        return crankAngleDegrees
    }

    /**
     * Tracks a crank angle measurement using the elliptical foot-Y model.
     *
     * The primary input is [footY]; [rawAngle] from atan2 is kept only as a fallback
     * for the first few frames before the model converges.
     *
     * @param rawAngle Raw crank angle from atan2 (0-360°) — used as fallback only
     * @param frameNumber Frame identifier
     * @param timestampMs Timestamp in milliseconds
     * @param side Which leg (BodySide.LEFT or BodySide.RIGHT)
     * @param calibratedSide The side facing the camera (from bike calibration)
     * @param config Tracking configuration
     * @param footY Normalized foot Y coordinate (0..1, required for model)
     * @param footX Normalized foot X coordinate (0..1, optional)
     * @param bbY Bottom bracket Y in normalized coords (optional sanity check)
     * @param crankLengthMm Known crank length in mm (for pixel-to-meters scale)
     * @return TrackingResult or null if side doesn't match calibration
     */
    fun trackAngle(
        rawAngle: Float,
        frameNumber: Long,
        timestampMs: Long,
        side: BodySide = BodySide.LEFT,
        calibratedSide: BodySide? = null,
        config: Config = Config(),
        footY: Float = Float.NaN,
        footX: Float = Float.NaN,
        bbY: Float? = null,
        crankLengthMm: Int? = null
    ): TrackingResult? {
        // === VALIDATE SIDE: Only track measurements from calibrated camera side ===
        if (calibratedSide != null && side != calibratedSide) {
            return null
        }

        val state = if (side == BodySide.LEFT) trackingStateLeft else trackingStateRight

        // === UPDATE ADAPTIVE FPS ===
        state.fpsBuffer.add(timestampMs)
        if (state.fpsBuffer.size > config.fpsFrameBuffer) {
            state.fpsBuffer.removeAt(0)
        }
        if (state.fpsBuffer.size >= config.fpsFrameBuffer) {
            val timeDeltaMs = state.fpsBuffer.last() - state.fpsBuffer.first()
            if (timeDeltaMs > 0) {
                val numFrames = state.fpsBuffer.size - 1
                state.adaptiveFps = (numFrames * 1000f) / timeDeltaMs
                state.modelState.adaptiveFps = state.adaptiveFps
            }
        }

        // === ELLIPTICAL MODEL PATH ===
        var filteredAngle = rawAngle
        var instantaneousRpm = 0f
        var confidence = 0f
        var wasFiltered = false

        if (config.enableFiltering && !footY.isNaN()) {
            val fitResult = EllipticalCrankModel.processObservation(
                footY = footY,
                footX = footX,
                frameIndex = frameNumber,
                timestampMs = timestampMs,
                state = state.modelState,
                config = config.modelConfig,
                crankLengthMm = crankLengthMm,
                bbY = bbY
            )

            if (fitResult.isValid) {
                filteredAngle = fitResult.crankAngleDeg
                instantaneousRpm = fitResult.rpm
                confidence = fitResult.confidence
                wasFiltered = false
            } else {
                // Model not yet converged — use raw atan2 as fallback
                filteredAngle = rawAngle
                instantaneousRpm = 0f
                confidence = 0.1f
                wasFiltered = true
            }
        }

        // Add measurement to buffer
        val measurement = AngleMeasurement(
            frameNumber = frameNumber,
            timestampMs = timestampMs,
            rawAngle = rawAngle,
            filteredAngle = filteredAngle,
            confidence = confidence,
            instantaneousRpm = instantaneousRpm
        )

        state.measurementBuffer.add(measurement)
        if (state.measurementBuffer.size > config.maxBufferSize) {
            state.measurementBuffer.removeAt(0)
        }

        state.lastAngle = filteredAngle
        state.lastTimestampMs = timestampMs
        state.frameCount++

        bikefitstudio.util.Logger.d(
            "CrankAngleTracker",
            "trackAngle: frame=$frameNumber, side=$side, raw=${String.format("%.1f", rawAngle)}°, " +
                    "model=${String.format("%.1f", filteredAngle)}°, rpm=${String.format("%.1f", instantaneousRpm)}, " +
                    "confidence=${String.format("%.2f", confidence)}, modelValid=${!wasFiltered}"
        )

        return TrackingResult(
            frameNumber = frameNumber,
            timestampMs = timestampMs,
            rawAngle = rawAngle,
            filteredAngle = filteredAngle,
            wasFiltered = wasFiltered,
            instantaneousRpm = instantaneousRpm,
            windowedRpm = instantaneousRpm,  // model-derived RPM is already stable
            confidence = confidence
        )
    }

    /**
     * Seeds the elliptical model with a half-cycle estimate from BDC/TDC detection.
     *
     * Call this when [PedalCycleDetector] reports a BDC→TDC or TDC→BDC transition
     * to bootstrap the model's frequency estimate.
     *
     * @param halfCycleFrames Number of frames in the half-cycle
     * @param side Which leg
     */
    fun seedFromHalfCycle(halfCycleFrames: Int, side: BodySide) {
        val state = if (side == BodySide.LEFT) trackingStateLeft else trackingStateRight
        EllipticalCrankModel.seedFromHalfCycle(halfCycleFrames, state.modelState)
    }

    /**
     * Gets the current tracked angle and metrics for a body side.
     */
    fun getCurrentMeasurement(side: BodySide = BodySide.LEFT): AngleMeasurement? {
        val state = if (side == BodySide.LEFT) trackingStateLeft else trackingStateRight
        return state.measurementBuffer.lastOrNull()
    }

    /**
     * Gets all measurements from the rolling buffer for a body side.
     */
    fun getMeasurementBuffer(side: BodySide = BodySide.LEFT): List<AngleMeasurement> {
        val state = if (side == BodySide.LEFT) trackingStateLeft else trackingStateRight
        return state.measurementBuffer.toList()
    }

    /**
     * Gets high-confidence measurements near a target angle.
     *
     * @param targetAngle Target angle in degrees
     * @param toleranceDegrees Acceptable angle range
     * @param minConfidence Minimum confidence threshold (0-1)
     * @param side Which leg
     * @return Filtered measurements matching criteria
     */
    fun getMeasurementsNear(
        targetAngle: Float,
        toleranceDegrees: Float = 5f,
        minConfidence: Float = 0.5f,
        side: BodySide = BodySide.LEFT
    ): List<AngleMeasurement> {
        val state = if (side == BodySide.LEFT) trackingStateLeft else trackingStateRight

        return state.measurementBuffer.filter { measurement ->
            val angleDiff = abs(angleDelta(targetAngle, measurement.filteredAngle))
            angleDiff <= toleranceDegrees && measurement.confidence >= minConfidence
        }
    }

    /**
     * Gets the model state for a body side (for advanced queries like predictAngleAtFrame).
     */
    fun getModelState(side: BodySide = BodySide.LEFT): EllipticalCrankModel.ModelState {
        val state = if (side == BodySide.LEFT) trackingStateLeft else trackingStateRight
        return state.modelState
    }

    /**
     * Resets tracking state for a body side.
     *
     * @param side Which leg, or null for both
     */
    fun reset(side: BodySide? = null) {
        if (side == null || side == BodySide.LEFT) {
            EllipticalCrankModel.reset(trackingStateLeft.modelState)
            trackingStateLeft.measurementBuffer.clear()
            trackingStateLeft.fpsBuffer.clear()
            trackingStateLeft.lastAngle = null
            trackingStateLeft.lastTimestampMs = null
            trackingStateLeft.frameCount = 0
            trackingStateLeft.adaptiveFps = 30f
        }

        if (side == null || side == BodySide.RIGHT) {
            EllipticalCrankModel.reset(trackingStateRight.modelState)
            trackingStateRight.measurementBuffer.clear()
            trackingStateRight.fpsBuffer.clear()
            trackingStateRight.lastAngle = null
            trackingStateRight.lastTimestampMs = null
            trackingStateRight.frameCount = 0
            trackingStateRight.adaptiveFps = 30f
        }

        bikefitstudio.util.Logger.d("CrankAngleTracker", "Tracking state reset for side $side")
    }

    /**
     * Gets diagnostic information about tracking state.
     */
    fun getDiagnostics(side: BodySide = BodySide.LEFT): Map<String, Any> {
        val state = if (side == BodySide.LEFT) trackingStateLeft else trackingStateRight
        val latest = state.measurementBuffer.lastOrNull()
        val model = state.modelState.lastFitResult

        return mapOf(
            "frameCount" to state.frameCount,
            "bufferSize" to state.measurementBuffer.size,
            "lastAngle" to (latest?.filteredAngle ?: "N/A"),
            "lastInstantRpm" to (latest?.instantaneousRpm ?: "N/A"),
            "lastConfidence" to (latest?.confidence ?: "N/A"),
            "modelValid" to model.isValid,
            "modelAmplitude" to model.amplitude,
            "modelOmega" to model.omegaRadPerFrame,
            "modelResidual" to model.residual
        )
    }

    // ========================== Utility ==========================

    /**
     * Signed angle delta in [-180, 180], handling wrapping.
     */
    internal fun angleDelta(fromAngle: Float, toAngle: Float): Float {
        var delta = toAngle - fromAngle
        while (delta > 180f) delta -= 360f
        while (delta < -180f) delta += 360f
        return delta
    }
}
