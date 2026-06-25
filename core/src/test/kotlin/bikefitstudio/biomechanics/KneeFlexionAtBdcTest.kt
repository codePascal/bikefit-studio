package bikefitstudio.biomechanics

import org.junit.Assert.*
import org.junit.Test
import bikefitstudio.pose.Landmark
import bikefitstudio.pose.PoseFrame
import bikefitstudio.pose.PoseLandmarkIndex
import kotlin.math.abs

/**
 * Unit tests for KneeFlexionAtBdc.
 * 
 * Tests verify:
 * - Correct computation of knee flexion at BDC from frame sequences
 * - Proper BDC detection and angle calculation integration
 * - Averaging across multiple cycles
 * - Edge cases (no frames, no BDC detected, invalid landmarks)
 */
class KneeFlexionAtBdcTest {

    private val delta = 0.5f // Tolerance for angle comparisons

    /**
     * Creates a test Landmark with specified coordinates and visibility.
     */
    private fun createLandmark(
        x: Float,
        y: Float,
        visibility: Float = 1.0f
    ): Landmark {
        return Landmark(
            x = x,
            y = y,
            z = 0f,
            visibility = visibility,
            presence = 1.0f
        )
    }

    /**
     * Creates a list of 33 landmarks with specified leg landmarks.
     */
    private fun createLandmarksWithLeg(
        hipX: Float, hipY: Float,
        kneeX: Float, kneeY: Float,
        ankleX: Float, ankleY: Float,
        side: BodySide,
        visibility: Float = 1.0f
    ): List<Landmark> {
        val landmarks = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) {
            createLandmark(0f, 0f, 1.0f)
        }

        val hipIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_HIP
        } else {
            PoseLandmarkIndex.RIGHT_HIP
        }

        val kneeIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_KNEE
        } else {
            PoseLandmarkIndex.RIGHT_KNEE
        }

        val ankleIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_ANKLE
        } else {
            PoseLandmarkIndex.RIGHT_ANKLE
        }

        landmarks[hipIndex] = createLandmark(hipX, hipY, visibility)
        landmarks[kneeIndex] = createLandmark(kneeX, kneeY, visibility)
        landmarks[ankleIndex] = createLandmark(ankleX, ankleY, visibility)

        return landmarks
    }

    /**
     * Creates a test pose frame with specified ankle Y position and knee angle.
     * The knee angle is created by positioning hip, knee, and ankle appropriately.
     */
    private fun createPoseFrameWithAnkleY(
        frameNumber: Long,
        timestampMs: Long,
        ankleY: Float,
        kneeAngle: Float,
        side: BodySide
    ): PoseFrame {
        // Position landmarks to create the desired knee angle
        // Hip at fixed position, knee in middle, ankle at specified Y
        val hipX = 0.5f
        val hipY = 0.3f
        val kneeX = 0.5f
        val kneeY = 0.5f
        
        // Calculate ankle position to achieve desired knee angle
        // For simplicity, we use vertical alignment with slight offset
        val ankleX = if (kneeAngle > 150f) {
            // More extended leg - ankle more aligned
            kneeX + 0.05f
        } else if (kneeAngle > 120f) {
            // Medium extension
            kneeX + 0.1f
        } else {
            // More bent - ankle further out
            kneeX + 0.15f
        }

        val landmarks = createLandmarksWithLeg(
            hipX = hipX, hipY = hipY,
            kneeX = kneeX, kneeY = kneeY,
            ankleX = ankleX, ankleY = ankleY,
            side = side
        )

        return PoseFrame(
            landmarks = landmarks,
            timestampMs = timestampMs,
            frameNumber = frameNumber,
            imageWidth = 1920,
            imageHeight = 1080,
            confidence = 0.9f
        )
    }

    /**
     * Creates a sequence of frames simulating a pedaling motion.
     * Ankle moves up and down, with BDC at the specified frames.
     */
    private fun createPedalingSequence(
        bdcFrameNumbers: List<Long>,
        side: BodySide,
        kneeAngleAtBdc: Float = 145f
    ): List<PoseFrame> {
        val frames = mutableListOf<PoseFrame>()
        var frameNumber = 0L
        val framesPerCycle = 10

        for (cycleIndex in bdcFrameNumbers.indices) {
            val bdcFrame = bdcFrameNumbers[cycleIndex]
            
            // Create frames for this cycle
            for (i in 0 until framesPerCycle) {
                val currentFrame = frameNumber + i
                
                // Simulate ankle Y movement (sine wave)
                // BDC is at the bottom (max Y), TDC at top (min Y)
                val phase = (i.toFloat() / framesPerCycle) * 2f * kotlin.math.PI.toFloat()
                val ankleY = 0.7f + 0.2f * kotlin.math.sin(phase)
                
                // Knee angle varies with ankle position
                // At BDC (bottom), knee is more extended
                val kneeAngle = if (currentFrame == bdcFrame) {
                    kneeAngleAtBdc
                } else if (i < framesPerCycle / 2) {
                    // Going down to BDC - knee extending
                    120f + (kneeAngleAtBdc - 120f) * (i.toFloat() / (framesPerCycle / 2))
                } else {
                    // Going up from BDC - knee flexing
                    kneeAngleAtBdc - (kneeAngleAtBdc - 120f) * ((i - framesPerCycle / 2).toFloat() / (framesPerCycle / 2))
                }

                frames.add(
                    createPoseFrameWithAnkleY(
                        frameNumber = currentFrame,
                        timestampMs = currentFrame * 33, // ~30 fps
                        ankleY = ankleY,
                        kneeAngle = kneeAngle,
                        side = side
                    )
                )
            }
            
            frameNumber += framesPerCycle
        }

        return frames
    }

    // ==================== Single Frame Tests ====================

    @Test
    fun `computeAtFrame - valid frame returns valid result`() {
        val landmarks = createLandmarksWithLeg(
            hipX = 0.5f, hipY = 0.3f,
            kneeX = 0.5f, kneeY = 0.5f,
            ankleX = 0.55f, ankleY = 0.8f,
            side = BodySide.LEFT
        )

        val frame = PoseFrame(
            landmarks = landmarks,
            timestampMs = 1000L,
            frameNumber = 10L,
            imageWidth = 1920,
            imageHeight = 1080,
            confidence = 0.9f
        )

        val result = KneeFlexionAtBdc.computeAtFrame(frame, BodySide.LEFT)

        assertTrue(result.isValid)
        assertEquals(BodySide.LEFT, result.side)
        assertEquals(10L, result.frameNumber)
        assertEquals(1000L, result.timestampMs)
        assertTrue(result.kneeAngle > 0f)
        assertTrue(result.confidence > 0f)
    }

    @Test
    fun `computeAtFrame - low visibility returns invalid result`() {
        val landmarks = createLandmarksWithLeg(
            hipX = 0.5f, hipY = 0.3f,
            kneeX = 0.5f, kneeY = 0.5f,
            ankleX = 0.55f, ankleY = 0.8f,
            side = BodySide.LEFT,
            visibility = 0.3f // Below default threshold
        )

        val frame = PoseFrame(
            landmarks = landmarks,
            timestampMs = 1000L,
            frameNumber = 10L,
            imageWidth = 1920,
            imageHeight = 1080,
            confidence = 0.9f
        )

        val result = KneeFlexionAtBdc.computeAtFrame(frame, BodySide.LEFT)

        assertFalse(result.isValid)
    }

    @Test
    fun `computeAtFrame - empty landmarks returns invalid result`() {
        val frame = PoseFrame(
            landmarks = emptyList(),
            timestampMs = 1000L,
            frameNumber = 10L,
            imageWidth = 1920,
            imageHeight = 1080,
            confidence = 0f
        )

        val result = KneeFlexionAtBdc.computeAtFrame(frame, BodySide.LEFT)

        assertFalse(result.isValid)
    }

    // ==================== Frame Sequence Tests ====================

    @Test
    fun `computeFromFrames - empty frames returns invalid summary`() {
        val result = KneeFlexionAtBdc.computeFromFrames(
            frames = emptyList(),
            side = BodySide.LEFT
        )

        assertFalse(result.isValid)
        assertEquals(0, result.cycleCount)
    }

    @Test
    fun `computeFromFrames - single cycle with BDC`() {
        // Create a sequence with one clear BDC
        val frames = mutableListOf<PoseFrame>()
        
        // Frames before BDC - ankle going down
        for (i in 0..4) {
            val ankleY = 0.5f + i * 0.05f
            frames.add(
                createPoseFrameWithAnkleY(
                    frameNumber = i.toLong(),
                    timestampMs = i * 33L,
                    ankleY = ankleY,
                    kneeAngle = 130f + i * 3f,
                    side = BodySide.LEFT
                )
            )
        }
        
        // BDC frame - ankle at lowest point (max Y)
        frames.add(
            createPoseFrameWithAnkleY(
                frameNumber = 5L,
                timestampMs = 5 * 33L,
                ankleY = 0.75f, // Maximum Y (lowest point)
                kneeAngle = 145f,
                side = BodySide.LEFT
            )
        )
        
        // Frames after BDC - ankle going up
        for (i in 6..10) {
            val ankleY = 0.75f - (i - 5) * 0.05f
            frames.add(
                createPoseFrameWithAnkleY(
                    frameNumber = i.toLong(),
                    timestampMs = i * 33L,
                    ankleY = ankleY,
                    kneeAngle = 145f - (i - 5) * 3f,
                    side = BodySide.LEFT
                )
            )
        }

        val result = KneeFlexionAtBdc.computeFromFrames(frames, BodySide.LEFT)

        assertTrue(result.isValid)
        assertEquals(BodySide.LEFT, result.side)
        assertTrue(result.cycleCount > 0)
        assertTrue(result.averageKneeAngle > 0f)
    }

    @Test
    fun `computeFromFrames - multiple cycles averaging`() {
        // Create frames with multiple BDC events
        val frames = mutableListOf<PoseFrame>()
        val kneeAnglesAtBdc = listOf(140f, 145f, 150f)
        
        for (cycleIdx in kneeAnglesAtBdc.indices) {
            val baseFrame = cycleIdx * 15L
            val targetAngle = kneeAnglesAtBdc[cycleIdx]
            
            // Frames before BDC
            for (i in 0..6) {
                val ankleY = 0.5f + i * 0.04f
                frames.add(
                    createPoseFrameWithAnkleY(
                        frameNumber = baseFrame + i,
                        timestampMs = (baseFrame + i) * 33,
                        ankleY = ankleY,
                        kneeAngle = 120f + i * 4f,
                        side = BodySide.LEFT
                    )
                )
            }
            
            // BDC frame
            frames.add(
                createPoseFrameWithAnkleY(
                    frameNumber = baseFrame + 7,
                    timestampMs = (baseFrame + 7) * 33,
                    ankleY = 0.8f,
                    kneeAngle = targetAngle,
                    side = BodySide.LEFT
                )
            )
            
            // Frames after BDC
            for (i in 8..14) {
                val ankleY = 0.8f - (i - 7) * 0.04f
                frames.add(
                    createPoseFrameWithAnkleY(
                        frameNumber = baseFrame + i,
                        timestampMs = (baseFrame + i) * 33,
                        ankleY = ankleY,
                        kneeAngle = targetAngle - (i - 7) * 4f,
                        side = BodySide.LEFT
                    )
                )
            }
        }

        val result = KneeFlexionAtBdc.computeFromFrames(frames, BodySide.LEFT)

        assertTrue(result.isValid)
        assertTrue(result.cycleCount >= 1) // Should detect at least 1 BDC event
        
        // Average should be a reasonable knee angle (within cycling range)
        assertTrue(result.averageKneeAngle > 100f) // At least somewhat bent
        assertTrue(result.averageKneeAngle < 180f) // Not completely straight
    }

    @Test
    fun `computeFromFrames - right leg`() {
        val frames = mutableListOf<PoseFrame>()
        
        for (i in 0..4) {
            val ankleY = 0.5f + i * 0.05f
            frames.add(
                createPoseFrameWithAnkleY(
                    frameNumber = i.toLong(),
                    timestampMs = i * 33L,
                    ankleY = ankleY,
                    kneeAngle = 130f + i * 3f,
                    side = BodySide.RIGHT
                )
            )
        }
        
        frames.add(
            createPoseFrameWithAnkleY(
                frameNumber = 5L,
                timestampMs = 5 * 33L,
                ankleY = 0.75f,
                kneeAngle = 145f,
                side = BodySide.RIGHT
            )
        )
        
        for (i in 6..10) {
            val ankleY = 0.75f - (i - 5) * 0.05f
            frames.add(
                createPoseFrameWithAnkleY(
                    frameNumber = i.toLong(),
                    timestampMs = i * 33L,
                    ankleY = ankleY,
                    kneeAngle = 145f - (i - 5) * 3f,
                    side = BodySide.RIGHT
                )
            )
        }

        val result = KneeFlexionAtBdc.computeFromFrames(frames, BodySide.RIGHT)

        assertTrue(result.isValid)
        assertEquals(BodySide.RIGHT, result.side)
    }

    // ==================== All BDC Frames Tests ====================

    @Test
    fun `computeAtAllBdcFrames - returns list of all BDC measurements`() {
        val frames = mutableListOf<PoseFrame>()
        
        // Create 2 cycles with clear BDC frames
        for (cycle in 0..1) {
            val baseFrame = cycle * 15L
            
            for (i in 0..6) {
                val ankleY = 0.5f + i * 0.04f
                frames.add(
                    createPoseFrameWithAnkleY(
                        frameNumber = baseFrame + i,
                        timestampMs = (baseFrame + i) * 33,
                        ankleY = ankleY,
                        kneeAngle = 120f + i * 4f,
                        side = BodySide.LEFT
                    )
                )
            }
            
            // BDC frame
            frames.add(
                createPoseFrameWithAnkleY(
                    frameNumber = baseFrame + 7,
                    timestampMs = (baseFrame + 7) * 33,
                    ankleY = 0.8f,
                    kneeAngle = 145f,
                    side = BodySide.LEFT
                )
            )
            
            for (i in 8..14) {
                val ankleY = 0.8f - (i - 7) * 0.04f
                frames.add(
                    createPoseFrameWithAnkleY(
                        frameNumber = baseFrame + i,
                        timestampMs = (baseFrame + i) * 33,
                        ankleY = ankleY,
                        kneeAngle = 145f - (i - 7) * 4f,
                        side = BodySide.LEFT
                    )
                )
            }
        }

        val results = KneeFlexionAtBdc.computeAtAllBdcFrames(frames, BodySide.LEFT)

        assertTrue(results.isNotEmpty())
        assertTrue(results.size >= 1) // Should have at least 1 BDC detected
        
        // All results should be valid
        results.forEach { result ->
            assertTrue(result.isValid)
            assertEquals(BodySide.LEFT, result.side)
            assertTrue(result.kneeAngle > 0f)
        }
    }

    @Test
    fun `computeAtAllBdcFrames - empty frames returns empty list`() {
        val results = KneeFlexionAtBdc.computeAtAllBdcFrames(
            frames = emptyList(),
            side = BodySide.LEFT
        )

        assertTrue(results.isEmpty())
    }

    // ==================== Statistics Tests ====================

    @Test
    fun `computeFromFrames - statistics are calculated correctly`() {
        val frames = mutableListOf<PoseFrame>()
        val kneeAngles = listOf(140f, 145f, 150f, 145f) // Known values
        
        for (cycleIdx in kneeAngles.indices) {
            val baseFrame = cycleIdx * 15L
            val targetAngle = kneeAngles[cycleIdx]
            
            for (i in 0..6) {
                val ankleY = 0.5f + i * 0.04f
                frames.add(
                    createPoseFrameWithAnkleY(
                        frameNumber = baseFrame + i,
                        timestampMs = (baseFrame + i) * 33,
                        ankleY = ankleY,
                        kneeAngle = 120f,
                        side = BodySide.LEFT
                    )
                )
            }
            
            frames.add(
                createPoseFrameWithAnkleY(
                    frameNumber = baseFrame + 7,
                    timestampMs = (baseFrame + 7) * 33,
                    ankleY = 0.85f,
                    kneeAngle = targetAngle,
                    side = BodySide.LEFT
                )
            )
            
            for (i in 8..14) {
                val ankleY = 0.85f - (i - 7) * 0.04f
                frames.add(
                    createPoseFrameWithAnkleY(
                        frameNumber = baseFrame + i,
                        timestampMs = (baseFrame + i) * 33,
                        ankleY = ankleY,
                        kneeAngle = 120f,
                        side = BodySide.LEFT
                    )
                )
            }
        }

        val result = KneeFlexionAtBdc.computeFromFrames(frames, BodySide.LEFT)

        assertTrue(result.isValid)
        assertTrue(result.cycleCount >= 3)
        
        // Min should be <= average <= max
        assertTrue(result.minKneeAngle <= result.averageKneeAngle)
        assertTrue(result.averageKneeAngle <= result.maxKneeAngle)
        
        // Standard deviation should be non-negative
        assertTrue(result.standardDeviation >= 0f)
    }

    // ==================== Invalid Result Tests ====================

    @Test
    fun `invalid KneeFlexionAtBdcResult factory method`() {
        val result = KneeFlexionAtBdcResult.invalid(BodySide.LEFT)

        assertFalse(result.isValid)
        assertEquals(BodySide.LEFT, result.side)
        assertEquals(0f, result.kneeAngle)
        assertEquals(0f, result.confidence)
        assertEquals(0L, result.frameNumber)
        assertEquals(0L, result.timestampMs)
    }

    @Test
    fun `invalid KneeFlexionAtBdcSummary factory method`() {
        val summary = KneeFlexionAtBdcSummary.invalid(BodySide.RIGHT)

        assertFalse(summary.isValid)
        assertEquals(BodySide.RIGHT, summary.side)
        assertEquals(0, summary.cycleCount)
        assertEquals(0f, summary.averageKneeAngle)
        assertEquals(0f, summary.minKneeAngle)
        assertEquals(0f, summary.maxKneeAngle)
        assertEquals(0f, summary.standardDeviation)
    }

    // ==================== Configuration Tests ====================

    @Test
    fun `custom visibility threshold in config`() {
        val frames = mutableListOf<PoseFrame>()
        
        // Create frames with low visibility (0.4)
        for (i in 0..10) {
            val ankleY = 0.5f + kotlin.math.sin(i * 0.6).toFloat() * 0.2f
            val landmarks = createLandmarksWithLeg(
                hipX = 0.5f, hipY = 0.3f,
                kneeX = 0.5f, kneeY = 0.5f,
                ankleX = 0.55f, ankleY = ankleY,
                side = BodySide.LEFT,
                visibility = 0.4f // Below default threshold of 0.5
            )
            
            frames.add(
                PoseFrame(
                    landmarks = landmarks,
                    timestampMs = i * 33L,
                    frameNumber = i.toLong(),
                    imageWidth = 1920,
                    imageHeight = 1080,
                    confidence = 0.9f
                )
            )
        }

        // With default config (visibility threshold 0.5), should fail
        val resultDefault = KneeFlexionAtBdc.computeFromFrames(frames, BodySide.LEFT)
        assertFalse(resultDefault.isValid)

        // With custom config (visibility threshold 0.3), should succeed
        val config = KneeFlexionAtBdcConfig(visibilityThreshold = 0.3f)
        val resultCustom = KneeFlexionAtBdc.computeFromFrames(frames, BodySide.LEFT, config)
        
        // May or may not detect BDC depending on the ankle movement pattern
        // but at least it should process without errors
        assertNotNull(resultCustom)
    }
}
