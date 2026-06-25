package bikefitstudio.biomechanics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import bikefitstudio.pose.Landmark
import bikefitstudio.pose.PoseLandmarkIndex
import bikefitstudio.pose.PoseResult

class TorsoAngleAspectRatioTest {

    private fun createLandmark(x: Float, y: Float): Landmark {
        return Landmark(x, y, 0f, 1f, 1f)
    }

    @Test
    fun `calculate torso angle with aspect ratio correction`() {
        // Setup: Vector (0.1, 0.1) in normalized space.
        // H = (0, 0.2), S = (0.1, 0.1).
        // Vector H->S = (0.1, -0.1). (Up and Right).
        // Angle to Horizontal (1,0): 45 degrees.
        
        val hip = createLandmark(0f, 0.2f)
        val shoulder = createLandmark(0.1f, 0.1f)
        
        val landmarks = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) { createLandmark(0f, 0f) }
        landmarks[PoseLandmarkIndex.RIGHT_HIP] = hip
        landmarks[PoseLandmarkIndex.RIGHT_SHOULDER] = shoulder
        
        // Case 1: No dimensions provided (Square/Normalized assumption)
        val resultSquare = TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(
            landmarks, BodySide.RIGHT, 0.5f, 0, 0
        )
        // atan(0.1/0.1) = 45 deg
        assertEquals(45f, resultSquare.angle, 0.1f, "Square aspect ratio should yield 45 degrees")
        
        // Case 2: Portrait dimensions (Height = 2 * Width)
        // W = 1000, H = 2000.
        // H_px = (0, 400). S_px = (100, 200).
        // V_px = (100, -200). (Up and Right, but steep).
        // Angle = atan(200/100) = 63.43 degrees.
        val resultPortrait = TorsoAngleCalculator.calculateTorsoAngleFromLandmarks(
            landmarks, BodySide.RIGHT, 0.5f, 1000, 2000
        )
        assertEquals(63.43f, resultPortrait.angle, 0.1f, "Portrait aspect ratio should yield ~63.4 degrees")
    }
}
