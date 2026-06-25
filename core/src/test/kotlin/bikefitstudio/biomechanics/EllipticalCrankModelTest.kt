package bikefitstudio.biomechanics

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import kotlin.math.*

class EllipticalCrankModelTest {

    private lateinit var state: EllipticalCrankModel.ModelState
    private val config = EllipticalCrankModel.Config()

    @BeforeEach
    fun setUp() {
        state = EllipticalCrankModel.ModelState()
    }

    // ==================== Helper: Generate Synthetic Foot-Y Data ====================

    /**
     * Generates synthetic foot Y positions for a pedal turning at constant RPM.
     *
     * y(t) = A·sin(ωt + φ₀) + C
     *
     * @param numFrames number of frames to generate
     * @param rpm cadence in RPM
     * @param fps frames per second
     * @param amplitude crank radius in normalized image coords
     * @param centerY bottom bracket Y in normalized image coords
     * @param startPhaseDeg starting crank phase in degrees
     * @param harmonicAmp 2nd harmonic amplitude (perspective distortion)
     * @param noiseStd Gaussian noise std dev
     */
    private fun generateFootY(
        numFrames: Int,
        rpm: Float = 90f,
        fps: Float = 30f,
        amplitude: Float = 0.08f,
        centerY: Float = 0.6f,
        startPhaseDeg: Float = 0f,
        harmonicAmp: Float = 0f,
        noiseStd: Float = 0f
    ): List<Pair<Long, Float>> {
        // ω in rad/frame = RPM × 2π / (60 × fps)
        val omega = rpm * 2.0 * PI / (60.0 * fps)
        val phi0 = Math.toRadians(startPhaseDeg.toDouble())
        val random = java.util.Random(42)

        return (0 until numFrames).map { i ->
            val t = i.toDouble()
            // sin-phase maps to crank convention: crankAngle = sinPhase + π
            // So sinPhase = crankAngle - π
            // We want to start at crank angle startPhaseDeg → sinPhase = startPhaseDeg - 180°
            val sinPhase = omega * t + (phi0 - PI)
            var y = amplitude * sin(sinPhase) + harmonicAmp * sin(2.0 * sinPhase) + centerY
            if (noiseStd > 0) {
                y += random.nextGaussian() * noiseStd
            }
            val timestampMs = (i * 1000.0 / fps).toLong()
            Pair(timestampMs, y.toFloat())
        }
    }

    // ==================== Basic Fit Tests ====================

    @Test
    fun `model returns invalid when not enough data`() {
        val result = EllipticalCrankModel.processObservation(
            footY = 0.5f,
            frameIndex = 0,
            timestampMs = 0,
            state = state,
            config = config
        )
        assertFalse(result.isValid)
    }

    @Test
    fun `model converges on clean sinusoidal data`() {
        val data = generateFootY(numFrames = 40, rpm = 90f)

        var lastResult = EllipticalCrankModel.FitResult.INVALID
        for ((i, pair) in data.withIndex()) {
            lastResult = EllipticalCrankModel.processObservation(
                footY = pair.second,
                frameIndex = i.toLong(),
                timestampMs = pair.first,
                state = state,
                config = config
            )
        }

        assertTrue(lastResult.isValid, "Model should converge on clean data")
        assertTrue(lastResult.confidence > 0.5f, "Confidence should be reasonable: ${lastResult.confidence}")
        // RPM should be close to 90
        assertEquals(90f, lastResult.rpm, 15f)
    }

    @Test
    fun `RPM estimation is accurate on clean data`() {
        val targetRpm = 80f
        val data = generateFootY(numFrames = 60, rpm = targetRpm)

        var lastResult = EllipticalCrankModel.FitResult.INVALID
        for ((i, pair) in data.withIndex()) {
            lastResult = EllipticalCrankModel.processObservation(
                footY = pair.second,
                frameIndex = i.toLong(),
                timestampMs = pair.first,
                state = state,
                config = config
            )
        }

        assertTrue(lastResult.isValid)
        assertEquals(targetRpm, lastResult.rpm, 5f)
    }

    @Test
    fun `amplitude recovery matches input`() {
        val inputAmplitude = 0.10f
        val data = generateFootY(numFrames = 50, rpm = 90f, amplitude = inputAmplitude)

        var lastResult = EllipticalCrankModel.FitResult.INVALID
        for ((i, pair) in data.withIndex()) {
            lastResult = EllipticalCrankModel.processObservation(
                footY = pair.second,
                frameIndex = i.toLong(),
                timestampMs = pair.first,
                state = state,
                config = config
            )
        }

        assertTrue(lastResult.isValid)
        assertEquals(inputAmplitude, lastResult.amplitude, 0.02f)
    }

    // ==================== Angle Convention Tests ====================

    @Test
    fun `BDC detected at max foot Y`() {
        // At BDC (crank angle 270° in our convention), foot Y is at maximum
        // Generate data and find where predicted angle is near 270°
        val data = generateFootY(numFrames = 60, rpm = 90f, amplitude = 0.08f, centerY = 0.6f)

        val results = mutableListOf<EllipticalCrankModel.FitResult>()
        for ((i, pair) in data.withIndex()) {
            val result = EllipticalCrankModel.processObservation(
                footY = pair.second,
                frameIndex = i.toLong(),
                timestampMs = pair.first,
                state = state,
                config = config
            )
            if (result.isValid) results.add(result)
        }

        assertTrue(results.isNotEmpty(), "Should have valid results")

        // Find the frame with maximum foot Y in the valid results range
        val validRange = data.takeLast(results.size)
        val maxYIndex = validRange.indices.maxByOrNull { validRange[it].second } ?: 0
        val angleAtMaxY = results[maxYIndex].crankAngleDeg

        // BDC should be near 270° (foot below BB)
        val distTo270 = abs(angleAtMaxY - 270f).let { if (it > 180f) 360f - it else it }
        assertTrue(distTo270 < 30f, "Angle at max Y should be near 270° (BDC), got ${angleAtMaxY}°, distance=$distTo270°")
    }

    // ==================== Elliptical (2nd Harmonic) Tests ====================

    @Test
    fun `model handles 2nd harmonic from perspective distortion`() {
        val data = generateFootY(
            numFrames = 60,
            rpm = 90f,
            amplitude = 0.08f,
            harmonicAmp = 0.015f  // ~20% perspective distortion
        )

        var lastResult = EllipticalCrankModel.FitResult.INVALID
        for ((i, pair) in data.withIndex()) {
            lastResult = EllipticalCrankModel.processObservation(
                footY = pair.second,
                frameIndex = i.toLong(),
                timestampMs = pair.first,
                state = state,
                config = config
            )
        }

        assertTrue(lastResult.isValid, "Should converge with 2nd harmonic")
        assertEquals(90f, lastResult.rpm, 10f)
        assertTrue(lastResult.harmonicAmplitude > 0.005f, "Should detect 2nd harmonic")
    }

    // ==================== Noise Robustness ====================

    @Test
    fun `model handles noisy data`() {
        // Use a more tolerant config for noisy conditions
        val noisyConfig = EllipticalCrankModel.Config(convergenceTolerance = 0.02f)
        // Seed with approximate half-cycle (90 RPM at 30fps → ~10 frames per half cycle)
        EllipticalCrankModel.seedFromHalfCycle(10, state)
        val data = generateFootY(
            numFrames = 80,
            rpm = 90f,
            noiseStd = 0.005f  // ~6% of amplitude
        )

        var lastResult = EllipticalCrankModel.FitResult.INVALID
        for ((i, pair) in data.withIndex()) {
            lastResult = EllipticalCrankModel.processObservation(
                footY = pair.second,
                frameIndex = i.toLong(),
                timestampMs = pair.first,
                state = state,
                config = noisyConfig
            )
        }

        assertTrue(lastResult.isValid, "Should converge despite noise (residual=${lastResult.residual}, " +
                "rpm=${lastResult.rpm}, amplitude=${lastResult.amplitude}, omega=${lastResult.omegaRadPerFrame}, " +
                "confidence=${lastResult.confidence})")
        assertEquals(90f, lastResult.rpm, 20f)
    }

    // ==================== Bootstrap Seeding ====================

    @Test
    fun `seeding from half-cycle improves convergence speed`() {
        // Seed with half-cycle: 90 RPM at 30fps → ~20 frames per revolution → ~10 frames per half
        EllipticalCrankModel.seedFromHalfCycle(10, state)

        val data = generateFootY(numFrames = 20, rpm = 90f)

        var lastResult = EllipticalCrankModel.FitResult.INVALID
        for ((i, pair) in data.withIndex()) {
            lastResult = EllipticalCrankModel.processObservation(
                footY = pair.second,
                frameIndex = i.toLong(),
                timestampMs = pair.first,
                state = state,
                config = config
            )
        }

        // With seeding, should converge faster (within 20 frames instead of needing ~40)
        assertTrue(lastResult.isValid, "Should converge quickly with seeding")
    }

    // ==================== Pixel-to-Meters Mapping ====================

    @Test
    fun `meters per normalized is derived from amplitude and crank length`() {
        val crankLengthMm = 172
        val data = generateFootY(numFrames = 50, rpm = 90f, amplitude = 0.08f)

        var lastResult = EllipticalCrankModel.FitResult.INVALID
        for ((i, pair) in data.withIndex()) {
            lastResult = EllipticalCrankModel.processObservation(
                footY = pair.second,
                frameIndex = i.toLong(),
                timestampMs = pair.first,
                state = state,
                config = config,
                crankLengthMm = crankLengthMm
            )
        }

        assertTrue(lastResult.isValid)
        assertNotNull(lastResult.metersPerNormalized)
        // Expected: 0.172m / 0.08 = 2.15 m/unit
        val expected = 0.172f / 0.08f
        assertEquals(expected, lastResult.metersPerNormalized!!, 0.5f)
    }

    // ==================== Reset ====================

    @Test
    fun `reset clears all state`() {
        val data = generateFootY(numFrames = 30, rpm = 90f)
        for ((i, pair) in data.withIndex()) {
            EllipticalCrankModel.processObservation(
                footY = pair.second,
                frameIndex = i.toLong(),
                timestampMs = pair.first,
                state = state,
                config = config
            )
        }

        assertTrue(state.observations.isNotEmpty())
        assertTrue(state.omega > 0)

        EllipticalCrankModel.reset(state)

        assertTrue(state.observations.isEmpty())
        assertEquals(0f, state.omega)
        assertEquals(0f, state.phi)
        assertFalse(state.lastFitResult.isValid)
    }

    // ==================== Sliding Window ====================

    @Test
    fun `sliding window adapts to cadence change`() {
        // Start at 90 RPM for 40 frames, then switch to 70 RPM
        val data90 = generateFootY(numFrames = 40, rpm = 90f)
        val data70 = generateFootY(numFrames = 40, rpm = 70f, startPhaseDeg = 0f)

        // Process 90 RPM phase
        var result90 = EllipticalCrankModel.FitResult.INVALID
        for ((i, pair) in data90.withIndex()) {
            result90 = EllipticalCrankModel.processObservation(
                footY = pair.second,
                frameIndex = i.toLong(),
                timestampMs = pair.first,
                state = state,
                config = config
            )
        }
        if (result90.isValid) {
            assertEquals(90f, result90.rpm, 15f)
        }

        // Process 70 RPM phase (continuing frame numbering)
        var result70 = EllipticalCrankModel.FitResult.INVALID
        for ((i, pair) in data70.withIndex()) {
            result70 = EllipticalCrankModel.processObservation(
                footY = pair.second,
                frameIndex = (40 + i).toLong(),
                timestampMs = pair.first + data90.last().first,
                state = state,
                config = config
            )
        }

        if (result70.isValid) {
            // After enough 70 RPM data, should adapt
            assertEquals(70f, result70.rpm, 15f)
        }
    }

    // ==================== Predict at Frame ====================

    @Test
    fun `predictAngleAtFrame returns consistent results`() {
        val data = generateFootY(numFrames = 50, rpm = 90f)

        for ((i, pair) in data.withIndex()) {
            EllipticalCrankModel.processObservation(
                footY = pair.second,
                frameIndex = i.toLong(),
                timestampMs = pair.first,
                state = state,
                config = config
            )
        }

        assertTrue(state.lastFitResult.isValid)

        // Predict angle at frame 25 (should be deterministic)
        val predicted1 = EllipticalCrankModel.predictAngleAtFrame(25, state)
        val predicted2 = EllipticalCrankModel.predictAngleAtFrame(25, state)
        assertNotNull(predicted1)
        assertEquals(predicted1!!, predicted2!!, 0.01f)

        // Predicted angle should be in [0, 360)
        assertTrue(predicted1 in 0f..360f)
    }
}
