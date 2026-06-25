package bikefitstudio.biomechanics

import org.junit.Test
import org.junit.Assert.*
import bikefitstudio.pose.Landmark
import bikefitstudio.pose.PoseLandmarkIndex

class KneeAngleAspectRatioTest {
    
    // Helper to create a landmark
    private fun lm(x: Float, y: Float): Landmark {
        return Landmark(x, y, 0f, 1.0f, 1.0f)
    }

    @Test
    fun `test aspect ratio affects knee angle calculation`() {
        // Hip(0,0) (Top Left)
        // Knee(1,1) (Middle Right)
        // Ankle(1,2) (Bottom Right - vertical shin relative to knee if aspect was square)
        
        // In normalized space:
        // Hip=(0,0), Knee=(1,1), Ankle=(1,2)
        // Knee Angle (Vertex at Knee)
        // Vector Knee->Hip: (-1, -1) -> 135 degrees from X axis (top left)
        // Vector Knee->Ankle: (0, 1) -> 90 degrees from X axis (straight down)
        // Angle between them = 45 degrees? No.
        // (-1, -1) dot (0, 1) = -1.
        // Mag1 = sqrt(2). Mag2 = 1.
        // Cos = -1 / sqrt(2) = -0.707.
        // Angle = acos(-0.707) = 135 degrees.
        
        val hip = lm(0.0f, 0.0f)
        val knee = lm(0.1f, 0.1f)
        val ankle = lm(0.1f, 0.2f)
        
        val landmarks = MutableList(33) { lm(0f, 0f) }
        landmarks[PoseLandmarkIndex.RIGHT_HIP] = hip
        landmarks[PoseLandmarkIndex.RIGHT_KNEE] = knee
        landmarks[PoseLandmarkIndex.RIGHT_ANKLE] = ankle
        
        // 1. Square Aspect Ratio (Normalized equivalent)
        // Expected: 135 degrees
        val resultSquare = KneeAngleCalculator.calculateKneeAngleFromLandmarks(
            landmarks, 
            BodySide.RIGHT,
            imageWidth = 100,
            imageHeight = 100
        )
        assertEquals(135f, resultSquare.angle, 0.1f)
        
        // 2. Portrait Aspect Ratio (1:2)
        // Hip(0,0)
        // Knee(10, 20) (x*100, y*200)
        // Ankle(10, 40)
        
        // Vector Knee->Hip: (-10, -20).
        // Vector Knee->Ankle: (0, 20).
        // Dot: -400.
        // Mag1: sqrt(100 + 400) = sqrt(500) = 22.36
        // Mag2: 20
        // Cos: -400 / (22.36 * 20) = -400 / 447.2 = -0.8944
        // Angle: acos(-0.8944) = 153.43 degrees
        
        val resultPortrait = KneeAngleCalculator.calculateKneeAngleFromLandmarks(
            landmarks,
            BodySide.RIGHT,
            imageWidth = 100,
            imageHeight = 200
        )
        assertEquals(153.43f, resultPortrait.angle, 0.1f)
        
        assertNotEquals(resultSquare.angle, resultPortrait.angle, 0.1f)
    }
}
