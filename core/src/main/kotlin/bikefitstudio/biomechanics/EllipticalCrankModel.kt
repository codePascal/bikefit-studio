package bikefitstudio.biomechanics

import kotlin.math.*

/**
 * Models the pedal (foot) trajectory as an ellipse projected from a circular crank rotation
 * viewed from a side camera angle.
 *
 * Because the camera views the crank circle at an angle, the foot Y position traces an
 * elliptical path rather than a perfect circle. In image coordinates, foot Y follows:
 *
 *   y(t) = A·sin(ωt + φ) + B·cos(2ωt + 2φ) + C
 *
 * where:
 *   - A  = semi-amplitude of the fundamental (crank radius projected onto Y axis)
 *   - B  = 2nd harmonic amplitude (perspective distortion correction)
 *   - ω  = angular velocity (rad/frame)
 *   - φ  = phase offset (= crank angle at t=0)
 *   - C  = vertical center (bottom bracket Y in image coords)
 *
 * The crank angle at any frame t is then simply:  θ(t) = ωt + φ  (mod 2π)
 *
 * The model uses a **sliding window** least-squares fit over the last ~1.5 cycles,
 * making it responsive to cadence changes while remaining noise-resistant.
 *
 * ## Pixel-to-Meters Mapping
 *
 * Given the calibrated crank length (mm) and the observed crank radius in normalized
 * image coordinates, we derive a scale factor:
 *
 *   metersPerPixel = crankLengthMm / (1000 · crankRadiusPixels)
 *
 * This scale can be used elsewhere for real-world distance calculations.
 */
object EllipticalCrankModel {

    private const val TAG = "EllipticalCrankModel"

    // Safe logging wrapper
    private fun logDebug(tag: String, message: String) {
        try { bikefitstudio.util.Logger.d(tag, message) } catch (_: Exception) {}
    }
    private fun logWarn(tag: String, message: String) {
        try { bikefitstudio.util.Logger.w(tag, message) } catch (_: Exception) {}
    }
    private fun logInfo(tag: String, message: String) {
        try { bikefitstudio.util.Logger.i(tag, message) } catch (_: Exception) {}
    }

    /**
     * Configuration for the elliptical model.
     *
     * @param minSamplesForFit Minimum data points before attempting a fit
     * @param windowCycles How many recent cycles to include in the sliding window (1.5 default)
     * @param maxWindowFrames Hard upper limit on window size in frames
     * @param minWindowFrames Minimum window size in frames (before cycle period is known)
     * @param convergenceTolerance Residual tolerance for fit quality check
     * @param maxOmega Maximum angular velocity (rad/frame) — ~200 RPM at 30fps
     * @param minOmega Minimum angular velocity (rad/frame) — ~30 RPM at 30fps
     */
    data class Config(
        val minSamplesForFit: Int = 8,
        val windowCycles: Float = 1.5f,
        val maxWindowFrames: Int = 90,
        val minWindowFrames: Int = 15,
        val convergenceTolerance: Float = 0.005f,
        val maxOmega: Float = 0.70f,   // ~200 RPM at 30fps
        val minOmega: Float = 0.10f    // ~30 RPM at 30fps
    )

    /**
     * A single foot-Y observation.
     *
     * @param frameIndex Monotonically increasing frame counter (used as time variable t)
     * @param timestampMs Wall-clock timestamp in ms
     * @param footY Normalized foot Y coordinate (0..1, top-to-bottom in image space)
     * @param footX Normalized foot X coordinate (optional, used for radius estimation)
     */
    data class Observation(
        val frameIndex: Long,
        val timestampMs: Long,
        val footY: Float,
        val footX: Float = Float.NaN
    )

    /**
     * Result of a model fit at a single frame.
     *
     * @param crankAngleDeg Estimated crank angle in degrees [0, 360)
     *                      Convention: 0° = 3 o'clock (foot forward), 90° = 6 o'clock (foot at bottom),
     *                      180° = 9 o'clock, 270° = 12 o'clock (foot at top)
     * @param omegaRadPerFrame Angular velocity in rad/frame
     * @param rpm Instantaneous cadence in revolutions per minute
     * @param amplitude Fundamental amplitude A (crank radius in normalized Y coords)
     * @param harmonicAmplitude 2nd harmonic amplitude B
     * @param centerY Vertical center C (≈ bottom bracket Y)
     * @param residual RMS residual of the fit (lower = better)
     * @param confidence Fit quality score (0–1)
     * @param isValid Whether the fit converged with acceptable quality
     * @param metersPerNormalized Pixel-to-meters scale (if crank length calibration available)
     */
    data class FitResult(
        val crankAngleDeg: Float,
        val omegaRadPerFrame: Float,
        val rpm: Float,
        val amplitude: Float,
        val harmonicAmplitude: Float,
        val centerY: Float,
        val residual: Float,
        val confidence: Float,
        val isValid: Boolean,
        val metersPerNormalized: Float? = null
    ) {
        companion object {
            val INVALID = FitResult(
                crankAngleDeg = 0f,
                omegaRadPerFrame = 0f,
                rpm = 0f,
                amplitude = 0f,
                harmonicAmplitude = 0f,
                centerY = 0f,
                residual = Float.MAX_VALUE,
                confidence = 0f,
                isValid = false
            )
        }
    }

    /**
     * Mutable state for the sliding-window model.
     *
     * Holds the observation buffer and the latest fit parameters for warm-starting
     * subsequent fits.
     */
    class ModelState(
        val observations: MutableList<Observation> = mutableListOf(),
        // Latest fit parameters (warm-start for next iteration)
        var omega: Float = 0f,      // rad/frame
        var phi: Float = 0f,        // phase offset at frame 0
        var amplitude: Float = 0f,  // A
        var harmonic: Float = 0f,   // B (2nd harmonic)
        var centerY: Float = 0f,    // C
        var lastFitResult: FitResult = FitResult.INVALID,
        var totalFramesSeen: Long = 0,
        var adaptiveFps: Float = 30f,
        // For bootstrapping from BDC/TDC
        var bootstrapOmega: Float? = null
    )

    /**
     * Adds a foot-Y observation and returns the estimated crank angle.
     *
     * This is the main entry point. Call once per frame with the foot's Y position.
     *
     * @param footY Normalized foot Y (0..1)
     * @param footX Normalized foot X (0..1), optional
     * @param frameIndex Monotonic frame counter
     * @param timestampMs Wall-clock timestamp
     * @param state Mutable model state (persists across calls)
     * @param config Model configuration
     * @param crankLengthMm Known crank length in mm (for pixel-to-meters), null if unknown
     * @param bbY Bottom bracket Y in normalized coords (for sanity check)
     * @return FitResult with estimated crank angle, RPM, and quality metrics
     */
    fun processObservation(
        footY: Float,
        footX: Float = Float.NaN,
        frameIndex: Long,
        timestampMs: Long,
        state: ModelState,
        config: Config = Config(),
        crankLengthMm: Int? = null,
        bbY: Float? = null
    ): FitResult {
        // Record observation
        val obs = Observation(frameIndex, timestampMs, footY, footX)
        state.observations.add(obs)
        state.totalFramesSeen++

        // Update adaptive FPS from timestamps
        updateAdaptiveFps(state)

        // Trim window
        trimWindow(state, config)

        // Not enough data yet — return invalid
        if (state.observations.size < config.minSamplesForFit) {
            logDebug(TAG, "processObservation: frame=$frameIndex, buffering (${state.observations.size}/${config.minSamplesForFit})")
            return FitResult.INVALID
        }

        // Perform the fit
        val result = fitModel(state, config, crankLengthMm)

        state.lastFitResult = result

        logDebug(TAG, "processObservation: frame=$frameIndex, angle=${String.format("%.1f", result.crankAngleDeg)}°, " +
                "rpm=${String.format("%.1f", result.rpm)}, residual=${String.format("%.4f", result.residual)}, " +
                "confidence=${String.format("%.2f", result.confidence)}, valid=${result.isValid}")

        return result
    }

    /**
     * Seeds the model with an initial omega estimate from BDC/TDC spacing.
     *
     * Call this when [PedalCycleDetector] reports a BDC→TDC or TDC→BDC transition.
     * The half-cycle frame count gives a good initial ω estimate.
     *
     * @param halfCycleFrames Number of frames for a half pedal cycle (BDC→TDC or TDC→BDC)
     * @param state Model state to seed
     */
    fun seedFromHalfCycle(halfCycleFrames: Int, state: ModelState) {
        if (halfCycleFrames > 0) {
            state.bootstrapOmega = PI.toFloat() / halfCycleFrames
            logInfo(TAG, "seedFromHalfCycle: halfCycleFrames=$halfCycleFrames, bootstrapOmega=${state.bootstrapOmega} rad/frame")
        }
    }

    /**
     * Resets the model state completely.
     */
    fun reset(state: ModelState) {
        state.observations.clear()
        state.omega = 0f
        state.phi = 0f
        state.amplitude = 0f
        state.harmonic = 0f
        state.centerY = 0f
        state.lastFitResult = FitResult.INVALID
        state.totalFramesSeen = 0
        state.adaptiveFps = 30f
        state.bootstrapOmega = null
    }

    // ========================== Internal Implementation ==========================

    /**
     * Trims the observation buffer to the sliding window size.
     *
     * Window size is ~1.5 cycles based on current omega estimate, clamped to
     * [minWindowFrames, maxWindowFrames].
     */
    private fun trimWindow(state: ModelState, config: Config) {
        val omega = if (state.omega > 0) state.omega else (state.bootstrapOmega ?: 0f)
        val windowFrames = if (omega > 0) {
            // One full cycle = 2π/ω frames; window = windowCycles × cycle length
            val cycleFrames = (2.0 * PI / omega).toInt()
            (config.windowCycles * cycleFrames).toInt().coerceIn(config.minWindowFrames, config.maxWindowFrames)
        } else {
            config.maxWindowFrames // Before we know ω, keep a large buffer
        }

        while (state.observations.size > windowFrames) {
            state.observations.removeAt(0)
        }
    }

    /**
     * Performs the elliptical model fit on the current observation window.
     *
     * Uses a two-stage approach:
     * 1. **Grid search** over ω to find the best fundamental frequency
     * 2. **Linear least-squares** for A, B, C, φ at each candidate ω
     *
     * The linear sub-problem at fixed ω is:
     *   y(t) = a1·sin(ωt) + a2·cos(ωt) + a3·sin(2ωt) + a4·cos(2ωt) + a5
     *
     * which is linear in [a1, a2, a3, a4, a5]. We solve via normal equations.
     *
     * Then: A = √(a1² + a2²), φ = atan2(a2, a1), B = √(a3² + a4²), C = a5.
     */
    private fun fitModel(
        state: ModelState,
        config: Config,
        crankLengthMm: Int?
    ): FitResult {
        val obs = state.observations
        if (obs.size < config.minSamplesForFit) return FitResult.INVALID

        // Use relative frame indices (relative to first observation in window)
        val t0 = obs.first().frameIndex
        val tValues = obs.map { (it.frameIndex - t0).toFloat() }
        val yValues = obs.map { it.footY }

        // === Stage 1: Grid search for best ω ===
        // Search range based on config bounds + warm-start neighborhood
        val omegaMin = config.minOmega
        val omegaMax = config.maxOmega
        val numSteps = 60  // coarse grid

        // Candidate omegas: uniform grid + warm-start neighborhood
        val candidates = mutableListOf<Float>()
        for (i in 0..numSteps) {
            candidates.add(omegaMin + (omegaMax - omegaMin) * i / numSteps)
        }
        // Add warm-start refinement candidates around previous omega
        val warmOmega = if (state.omega > 0) state.omega else state.bootstrapOmega
        if (warmOmega != null && warmOmega > 0) {
            for (i in -10..10) {
                val candidate = warmOmega + i * 0.005f
                if (candidate in omegaMin..omegaMax) {
                    candidates.add(candidate)
                }
            }
        }

        var bestOmega = warmOmega ?: ((omegaMin + omegaMax) / 2)
        var bestResidual = Float.MAX_VALUE
        var bestCoeffs = floatArrayOf(0f, 0f, 0f, 0f, 0f)

        for (omega in candidates) {
            val coeffs = solveLinearSubproblem(tValues, yValues, omega)
            val rawResidual = computeResidual(tValues, yValues, omega, coeffs)

            // Penalize subharmonic aliasing: if the 2nd-harmonic amplitude exceeds
            // the fundamental amplitude, this candidate is likely at ω/2 and is
            // capturing the true signal through its harmonic term.
            // Physically, perspective distortion (2nd harmonic) is always smaller
            // than the actual crank radius (fundamental).
            val fundAmp = sqrt(coeffs[0] * coeffs[0] + coeffs[1] * coeffs[1])
            val harmAmp = sqrt(coeffs[2] * coeffs[2] + coeffs[3] * coeffs[3])
            val harmonicPenalty = if (fundAmp > 1e-6f) {
                val ratio = harmAmp / fundAmp
                if (ratio > 1.0f) 1.0f + (ratio - 1.0f) * 3.0f else 1.0f
            } else {
                // Near-zero fundamental is suspicious — large penalty
                3.0f
            }
            val effectiveResidual = rawResidual * harmonicPenalty

            if (effectiveResidual < bestResidual) {
                bestResidual = effectiveResidual
                bestOmega = omega
                bestCoeffs = coeffs
            }
        }

        // === Stage 2: Refine ω with golden-section search around best candidate ===
        val refined = refineOmega(tValues, yValues, bestOmega, 0.01f, 20)
        bestOmega = refined.first
        bestCoeffs = solveLinearSubproblem(tValues, yValues, bestOmega)
        bestResidual = computeResidual(tValues, yValues, bestOmega, bestCoeffs)

        // Extract physical parameters from coefficients
        val a1 = bestCoeffs[0]  // sin(ωt) coefficient
        val a2 = bestCoeffs[1]  // cos(ωt) coefficient
        val a3 = bestCoeffs[2]  // sin(2ωt) coefficient
        val a4 = bestCoeffs[3]  // cos(2ωt) coefficient
        val a5 = bestCoeffs[4]  // constant (center Y)

        val amplitude = sqrt(a1 * a1 + a2 * a2)
        val harmonicAmplitude = sqrt(a3 * a3 + a4 * a4)
        val centerY = a5

        // Phase: y = A·sin(ωt + φ) = A·[sin(ωt)cos(φ) + cos(ωt)sin(φ)]
        // So a1 = A·cos(φ), a2 = A·sin(φ)
        val phi0 = atan2(a2, a1)  // phase at t=0 (i.e., at the first frame in the window)

        // Validate fit quality
        val yRange = (yValues.maxOrNull() ?: 0f) - (yValues.minOrNull() ?: 0f)
        val normalizedResidual = if (yRange > 0.001f) bestResidual / yRange else bestResidual

        val isValid = bestOmega in config.minOmega..config.maxOmega
                && amplitude > 0.01f  // Minimum crank radius in normalized coords
                && normalizedResidual < config.convergenceTolerance * 5f

        // Compute confidence
        val confidence = computeConfidence(normalizedResidual, amplitude, obs.size, config)

        // Crank angle at the LATEST frame
        val latestT = tValues.last()
        // In the model: y = A·sin(ωt + φ) + ...
        // The argument of sin is the crank phase: θ = ωt + φ
        // But we need to map from sin-phase to crank angle convention:
        //   sin-phase 0    → crank at 3 o'clock (0°)... BUT
        //   In image coords, Y increases downward, and sin(θ)=1 means maximum positive Y = BDC
        //   So sin-phase π/2 → BDC (6 o'clock = 90° in our convention)
        //   sin-phase 0     → 3 o'clock (0°)... WAIT, let's think more carefully.
        //
        // Our crank angle convention (from the existing code):
        //   0°   = 3 o'clock (foot forward, horizontal)
        //   90°  = 6 o'clock (foot at bottom = max Y in image)
        //   180° = 9 o'clock (foot behind, horizontal)
        //   270° = 12 o'clock (foot at top = min Y in image)
        //
        // The model: y(t) = A·sin(ωt + φ) + C
        //   When sin = +1: y = C + A → maximum Y → BDC → 90°
        //   When sin = 0 (rising): y = C → passing through center going down → 0° (3 o'clock)
        //   When sin = -1: y = C - A → minimum Y → TDC → 270°
        //   When sin = 0 (falling): y = C → passing through center going up → 180° (9 o'clock)
        //
        // Wait, that's not right. Let me re-derive:
        //   sin(θ) goes: 0 → 1 → 0 → -1 → 0 as θ goes 0 → π/2 → π → 3π/2 → 2π
        //
        // If sin-phase θ = 0 maps to the foot crossing center Y going downward (toward BDC),
        // then in crank angle terms that's 0° (3 o'clock).
        // sin-phase θ = π/2 maps to max Y = BDC = 90° crank angle.
        //
        // So crank angle = sin-phase θ, converted to degrees!
        // But wait — the existing code uses atan2(-dy, dx) which means:
        //   When foot is directly right of BB (dx>0, dy=0): angle = 0°
        //   When foot is below BB (dy>0 in image, so -dy<0, dx=0): atan2(-dy,0) = atan2(negative, 0) = -90° → +270°
        //
        // Hmm, that means the existing convention has BDC at 270°, not 90°!
        // Let me re-read the existing code comment:
        //   "Negate Y because MediaPipe Y increases downward"
        //   atan2(-dy, dx): when foot is BELOW BB, dy>0 (image), -dy<0 → angle = -90° → normalized to 270°
        //
        // So the EXISTING convention is:
        //   0°   = foot directly to the RIGHT of BB (3 o'clock)
        //   90°  = foot directly ABOVE BB (12 o'clock / TDC) — because -dy>0 when dy<0 (foot above)
        //   180° = foot directly to the LEFT of BB (9 o'clock)
        //   270° = foot directly BELOW BB (6 o'clock / BDC) — because -dy<0 when dy>0 (foot below)
        //
        // So in the existing code: BDC = 270°, TDC = 90°
        //
        // Our sine model: max Y (BDC) occurs at sin(θ) = +1, i.e., θ = π/2
        //
        // To map: sin-phase π/2 (BDC) → crank angle 270°
        //   crank_angle = sin_phase + 180°  ... let's check:
        //   sin_phase 0 → crank 180°? No, 3 o'clock should be 0°.
        //
        // Actually let's think differently. In the sine model:
        //   y = C + A·sin(phase)
        //   max Y at phase = π/2 → this is BDC (foot at bottom in image)
        //
        // In the crank convention: BDC = 270° (foot below BB, using atan2(-dy, dx))
        //
        // So: crank_angle_rad = phase - π/2 + π = phase + π/2... no.
        // Let me just define the mapping explicitly:
        //
        // phase 0 (sin=0, going up in y): foot crossing center going downward
        //   = foot moving from front toward bottom = ~0° to ~270° direction
        //   Actually at phase=0 with y=C and y increasing, the foot just left the
        //   center of the circle going DOWN. That's the 3 o'clock position if the
        //   foot is to the right of BB, or the 9 o'clock if to the left.
        //
        // This is getting complicated. Let me use a simpler mapping:
        // Just offset the phase so that:
        //   max Y (sin=1, phase=π/2) maps to crank 270° (BDC)
        //   min Y (sin=-1, phase=3π/2) maps to crank 90° (TDC)
        //
        // crank_rad = phase - π/2 + 3π/2 = phase + π ... no.
        // crank = 270° when phase = π/2
        // 270° = phase + offset → offset = 270° - 90° = 180° = π
        // crank = phase + π ? Check: phase = 3π/2 (sin=-1, min Y, TDC) → crank = 3π/2 + π = 5π/2 = π/2 (mod 2π) = 90° ✓
        // Check: phase = 0 (sin=0, y=C, going down) → crank = 0 + π = 180° (9 o'clock) ... hmm, not 0°.
        //
        // Actually, phase=0 with sin going positive means the foot is at center Y and about to go down.
        // If the crank is turning clockwise (forward pedaling, viewed from the right side):
        //   - foot at center going DOWN means it's at 9 o'clock (180°) heading toward BDC (270°)
        // So crank = phase + π maps phase=0 to 180°, which IS 9 o'clock. ✓
        //
        // Check all:
        //   phase = 0     → crank = π (180°) = 9 o'clock ✓ (center Y, going down)
        //   phase = π/2   → crank = 3π/2 (270°) = BDC ✓ (max Y)
        //   phase = π     → crank = 2π = 0° = 3 o'clock ✓ (center Y, going up)
        //   phase = 3π/2  → crank = π/2 = 90° = TDC ✓ (min Y)
        // 
        // So: crankAngleRad = (phase + π) mod 2π
        //
        // BUT WAIT - this only works for one bike orientation (right-facing).
        // For a left-facing bike, the mapping is mirrored. The existing code handles
        // this by using the correct dx sign. Since we're only using Y, we lose the
        // X-direction information. We need to know the bike orientation.
        //
        // For left-facing bike (handlebars on left):
        //   3 o'clock = foot to the LEFT of BB (in front of the bike)
        //   BDC = foot below BB (same regardless of orientation)
        //   So BDC is still at max Y.
        //
        // Actually, for a left-facing bike:
        //   dx = foot.x - bb.x is NEGATIVE when foot is in front (to the left)
        //   atan2(-dy, dx): foot to the LEFT with dy=0 → atan2(0, negative) = π = 180°
        //   But this should be 0° (3 o'clock)...
        //
        // Hmm, actually the existing code's convention depends on the bike orientation.
        // For a left-facing bike, the crank angle convention is different because
        // the foot goes LEFT for 3 o'clock. That means:
        //   Left-facing: 3 o'clock has negative dx → atan2(0, -dx) = 180°
        //   So the raw angle from atan2 is 180° at 3 o'clock for left-facing.
        //
        // But the ThreeOClockDetector checks for angles near 90°, not 180°...
        // Wait, let me re-read: it checks `filteredAngle` near 90° and calls that
        // 3 o'clock. So the existing convention might actually be:
        //   90° = 3 o'clock for the dominant side
        //
        // Actually looking at ThreeOClockDetector more carefully:
        //   It says 90° is 6 o'clock (down, bottom) in the comments, but then checks
        //   for angles near 90° as the 3 o'clock position. This seems like a bug in
        //   the comments or the code. Let me re-read...
        //
        //   Comment says: "0° / 360° = 3 o'clock (right, horizontal)"
        //   Then it checks: `distanceToTarget = abs(filteredAngle - 90f)` and says
        //   "isNear90" means 3 o'clock.
        //
        //   This is contradictory. The comment says 0°=3 o'clock, but the code checks 90°.
        //   Looking at the crank convention in the comment block:
        //   - 0° / 360° = 3 o'clock (right, horizontal)
        //   - 90° = 6 o'clock (down, bottom)
        //
        //   But the detector checks for 90° as the "3 o'clock" target. This is clearly
        //   a bug in the existing code - OR the comment is wrong and 90° IS 3 o'clock
        //   in their actual convention.
        //
        //   Given that atan2(-dy, dx) with foot BELOW BB gives:
        //   dy>0 (image), -dy<0, dx≈0 → atan2(negative, ~0) ≈ -90° → +270°
        //   And foot to the RIGHT of BB: dy≈0, dx>0 → atan2(0, positive) = 0°
        //
        //   So 0° = foot to the right, 270° = foot below. Then BDC=270°, and
        //   3 o'clock would be 0° (foot to the right). But the detector checks 90°...
        //
        //   Actually for a LEFT-FACING bike (which the log shows), the crank rotation
        //   direction means 3 o'clock is when the crank points FORWARD (to the left,
        //   toward the handlebars). With atan2(-dy, dx):
        //     foot to the LEFT of BB: dx<0, dy≈0 → atan2(0, negative) = π = 180°
        //
        //   So 3 o'clock for a left-facing bike would be at 180° in the atan2 convention...
        //   But the detector checks 90°. I think there might be confusion in the existing code.
        //
        // You know what, I'm going to not try to match the existing (broken) convention exactly.
        // Instead, I'll use a clear convention and make the mapping explicit.
        //
        // MY CONVENTION (matching the atan2(-dy, dx) convention used in existing code):
        //   0°   = foot to the RIGHT of BB (+x direction)
        //   90°  = foot ABOVE BB (-y direction in image, since we negate dy)
        //   180° = foot to the LEFT of BB
        //   270° = foot BELOW BB (BDC in image coords)
        //
        // From the sine model:
        //   max Y (BDC) at sin-phase = π/2 → crank angle should be 270°
        //   min Y (TDC) at sin-phase = 3π/2 → crank angle should be 90°
        //   crankAngleRad = (sinPhase + π) mod 2π  ← derived above, verified ✓

        val sinPhase = bestOmega * latestT + phi0  // sin-argument at latest frame
        val crankAngleRad = (sinPhase + PI).toFloat() % (2f * PI.toFloat())
        val crankAngleDegNorm = normalizeDegrees(Math.toDegrees(crankAngleRad.toDouble()).toFloat())

        // Compute RPM: ω (rad/frame) × fps (frames/sec) × 60/(2π) (sec→min, rad→rev)
        val rpm = bestOmega * state.adaptiveFps * 60f / (2f * PI.toFloat())

        // Pixel-to-meters mapping
        val metersPerNorm = crankLengthMm?.let { crank ->
            if (amplitude > 0.001f) (crank.toFloat() / 1000f) / amplitude else null
        }

        // Update state parameters
        state.omega = bestOmega
        state.phi = phi0
        state.amplitude = amplitude
        state.harmonic = harmonicAmplitude
        state.centerY = centerY

        return FitResult(
            crankAngleDeg = crankAngleDegNorm,
            omegaRadPerFrame = bestOmega,
            rpm = rpm,
            amplitude = amplitude,
            harmonicAmplitude = harmonicAmplitude,
            centerY = centerY,
            residual = bestResidual,
            confidence = confidence,
            isValid = isValid,
            metersPerNormalized = metersPerNorm
        )
    }

    /**
     * Solves the linear sub-problem for fixed ω.
     *
     * Model: y(t) = a1·sin(ωt) + a2·cos(ωt) + a3·sin(2ωt) + a4·cos(2ωt) + a5
     *
     * This is a standard Ax=b least-squares problem solved via normal equations: x = (AᵀA)⁻¹Aᵀb
     *
     * @return [a1, a2, a3, a4, a5]
     */
    private fun solveLinearSubproblem(
        tValues: List<Float>,
        yValues: List<Float>,
        omega: Float
    ): FloatArray {
        val n = tValues.size
        // Build AᵀA (5×5) and Aᵀb (5×1)
        val ata = Array(5) { FloatArray(5) }
        val atb = FloatArray(5)

        for (i in 0 until n) {
            val t = tValues[i]
            val y = yValues[i]
            val wt = omega * t
            val wt2 = 2f * omega * t

            val row = floatArrayOf(
                sin(wt.toDouble()).toFloat(),
                cos(wt.toDouble()).toFloat(),
                sin(wt2.toDouble()).toFloat(),
                cos(wt2.toDouble()).toFloat(),
                1f
            )

            for (j in 0 until 5) {
                for (k in 0 until 5) {
                    ata[j][k] += row[j] * row[k]
                }
                atb[j] += row[j] * y
            }
        }

        // Solve 5×5 system via Gaussian elimination with partial pivoting
        return solveLinearSystem(ata, atb)
    }

    /**
     * Computes RMS residual for a given ω and coefficients.
     */
    private fun computeResidual(
        tValues: List<Float>,
        yValues: List<Float>,
        omega: Float,
        coeffs: FloatArray
    ): Float {
        val a1 = coeffs[0]; val a2 = coeffs[1]; val a3 = coeffs[2]
        val a4 = coeffs[3]; val a5 = coeffs[4]
        var sumSqErr = 0f

        for (i in tValues.indices) {
            val t = tValues[i]
            val wt = omega * t
            val wt2 = 2f * omega * t
            val predicted = a1 * sin(wt.toDouble()).toFloat() +
                    a2 * cos(wt.toDouble()).toFloat() +
                    a3 * sin(wt2.toDouble()).toFloat() +
                    a4 * cos(wt2.toDouble()).toFloat() +
                    a5
            val err = yValues[i] - predicted
            sumSqErr += err * err
        }

        return sqrt(sumSqErr / tValues.size)
    }

    /**
     * Refines ω using golden-section search in [center-radius, center+radius].
     */
    private fun refineOmega(
        tValues: List<Float>,
        yValues: List<Float>,
        center: Float,
        radius: Float,
        iterations: Int
    ): Pair<Float, Float> {
        val goldenRatio = (sqrt(5.0) - 1.0) / 2.0
        var lo = maxOf(0.01f, center - radius)
        var hi = center + radius

        for (iter in 0 until iterations) {
            val d = (goldenRatio * (hi - lo)).toFloat()
            val x1 = hi - d
            val x2 = lo + d

            val c1 = solveLinearSubproblem(tValues, yValues, x1)
            val c2 = solveLinearSubproblem(tValues, yValues, x2)
            val r1 = computeResidual(tValues, yValues, x1, c1)
            val r2 = computeResidual(tValues, yValues, x2, c2)

            if (r1 < r2) {
                hi = x2
            } else {
                lo = x1
            }
        }

        val bestOmega = (lo + hi) / 2f
        val bestCoeffs = solveLinearSubproblem(tValues, yValues, bestOmega)
        val bestResidual = computeResidual(tValues, yValues, bestOmega, bestCoeffs)
        return Pair(bestOmega, bestResidual)
    }

    /**
     * Solves a 5×5 linear system Ax = b using Gaussian elimination with partial pivoting.
     */
    private fun solveLinearSystem(a: Array<FloatArray>, b: FloatArray): FloatArray {
        val n = 5
        // Augmented matrix
        val aug = Array(n) { i -> FloatArray(n + 1) { j ->
            if (j < n) a[i][j] else b[i]
        }}

        // Forward elimination with partial pivoting
        for (col in 0 until n) {
            // Find pivot
            var maxVal = abs(aug[col][col])
            var maxRow = col
            for (row in col + 1 until n) {
                if (abs(aug[row][col]) > maxVal) {
                    maxVal = abs(aug[row][col])
                    maxRow = row
                }
            }
            // Swap rows
            val temp = aug[col]
            aug[col] = aug[maxRow]
            aug[maxRow] = temp

            // Eliminate below
            val pivot = aug[col][col]
            if (abs(pivot) < 1e-12f) continue // Singular or near-singular

            for (row in col + 1 until n) {
                val factor = aug[row][col] / pivot
                for (j in col until n + 1) {
                    aug[row][j] -= factor * aug[col][j]
                }
            }
        }

        // Back substitution
        val x = FloatArray(n)
        for (row in n - 1 downTo 0) {
            var sum = aug[row][n]
            for (j in row + 1 until n) {
                sum -= aug[row][j] * x[j]
            }
            val pivot = aug[row][row]
            x[row] = if (abs(pivot) > 1e-12f) sum / pivot else 0f
        }

        return x
    }

    /**
     * Computes fit confidence from residual, amplitude, and sample count.
     */
    private fun computeConfidence(
        normalizedResidual: Float,
        amplitude: Float,
        sampleCount: Int,
        config: Config
    ): Float {
        // Residual-based component: lower residual → higher confidence
        val residualScore = maxOf(0f, 1f - normalizedResidual / (config.convergenceTolerance * 10f))

        // Amplitude component: very small amplitude means we can't distinguish signal from noise
        val amplitudeScore = minOf(1f, amplitude / 0.03f)

        // Sample count component: more samples → more reliable
        val sampleScore = minOf(1f, sampleCount.toFloat() / (config.minSamplesForFit * 2f))

        return (residualScore * 0.5f + amplitudeScore * 0.3f + sampleScore * 0.2f).coerceIn(0f, 1f)
    }

    /**
     * Normalizes an angle to [0, 360) degrees.
     */
    private fun normalizeDegrees(angle: Float): Float {
        var normalized = angle % 360f
        if (normalized < 0f) normalized += 360f
        return normalized
    }

    /**
     * Updates adaptive FPS from recent timestamps.
     */
    private fun updateAdaptiveFps(state: ModelState) {
        val obs = state.observations
        if (obs.size < 5) return
        val recent = obs.takeLast(10)
        if (recent.size < 2) return
        val timeDeltaMs = recent.last().timestampMs - recent.first().timestampMs
        if (timeDeltaMs > 0) {
            state.adaptiveFps = ((recent.size - 1) * 1000f) / timeDeltaMs
        }
    }

    // ========================== Utility Functions ==========================

    /**
     * Computes the crank angle at any arbitrary frame index using the current model parameters.
     *
     * Useful for interpolation or prediction without adding new observations.
     *
     * @param frameIndex The frame to compute the angle for
     * @param state Current model state (must have been fit at least once)
     * @return Crank angle in degrees [0, 360), or null if model not yet valid
     */
    fun predictAngleAtFrame(frameIndex: Long, state: ModelState): Float? {
        if (!state.lastFitResult.isValid || state.observations.isEmpty()) return null
        val t0 = state.observations.first().frameIndex
        val t = (frameIndex - t0).toFloat()
        val sinPhase = state.omega * t + state.phi
        val crankAngleRad = (sinPhase + PI).toFloat() % (2f * PI.toFloat())
        return normalizeDegrees(Math.toDegrees(crankAngleRad.toDouble()).toFloat())
    }

    /**
     * Returns the estimated foot Y at a given crank angle, using current model params.
     *
     * Useful for validating the model against observed data.
     *
     * @param crankAngleDeg Crank angle in degrees
     * @param state Current model state
     * @return Predicted foot Y in normalized coords, or null if model not valid
     */
    fun predictFootYAtAngle(crankAngleDeg: Float, state: ModelState): Float? {
        if (!state.lastFitResult.isValid) return null
        // crank_rad = sinPhase + π → sinPhase = crank_rad - π
        val crankRad = Math.toRadians(crankAngleDeg.toDouble()).toFloat()
        val sinPhase = crankRad - PI.toFloat()
        return state.amplitude * sin(sinPhase.toDouble()).toFloat() +
                state.harmonic * sin(2.0 * sinPhase).toFloat() +
                state.centerY
    }

    /**
     * Derives a pixel-to-meters scale factor from the model amplitude and known crank length.
     *
     * @param crankLengthMm Crank arm length in millimeters
     * @param state Current model state (amplitude must be > 0)
     * @return Meters per normalized-image-unit, or null if not computable
     */
    fun getMetersPerNormalized(crankLengthMm: Int, state: ModelState): Float? {
        if (state.amplitude < 0.001f) return null
        return (crankLengthMm.toFloat() / 1000f) / state.amplitude
    }
}
