package bikefitstudio.biomechanics

import org.junit.Test
import org.junit.Assert.*
import bikefitstudio.pose.Landmark
import bikefitstudio.pose.PoseLandmarkIndex

class HipAngleAspectRatioTest {
    
    // Helper to create a landmark
    private fun lm(x: Float, y: Float): Landmark {
        return Landmark(x, y, 0f, 1.0f, 1.0f)
    }

    @Test
    fun `test aspect ratio affects hip angle calculation`() {
        // Shoulder(0,0)
        // Hip(1,1)
        // Knee(2,1)
        
        // Square (1:1):
        // Hip->Shoulder (-1,-1). Angle 135 from horizontal (top left).
        // Hip->Knee (1,0). Angle 0.
        // Included angle = 135.
        
        val shoulder = lm(0.0f, 0.0f)
        val hip = lm(0.1f, 0.1f)
        val knee = lm(0.2f, 0.1f)
        
        val landmarks = MutableList(33) { lm(0f, 0f) }
        landmarks[PoseLandmarkIndex.RIGHT_SHOULDER] = shoulder
        landmarks[PoseLandmarkIndex.RIGHT_HIP] = hip
        landmarks[PoseLandmarkIndex.RIGHT_KNEE] = knee
        
        // 1. Square Aspect Ratio
        // Expected: 135 degrees
        val resultSquare = HipAngleCalculator.calculateHipAngleFromLandmarks(
            landmarks, 
            BodySide.RIGHT,
            imageWidth = 100,
            imageHeight = 100
        )
        assertEquals(135f, resultSquare.angle, 0.1f)
        
        // 2. Portrait Aspect Ratio (1:2)
        // Shoulder(0,0)
        // Hip(10, 20)
        // Knee(20, 20)
        
        // Hip->Shoulder (-10, -20).
        // Hip->Knee (10, 0).
        // Dot: -100.
        // Mag1: 22.36.
        // Mag2: 10.
        // Cos: -100 / 223.6 = -0.4472.
        // Angle: acos(-0.4472) = 116.56 degrees.
        
        val resultPortrait = HipAngleCalculator.calculateHipAngleFromLandmarks(
            landmarks,
            BodySide.RIGHT,
            imageWidth = 100,
            imageHeight = 200
        )
        assertEquals(116.56f, resultPortrait.angle, 0.1f)
        
        assertNotEquals(resultSquare.angle, resultPortrait.angle, 0.1f)
    }
}
