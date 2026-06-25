package bikefitstudio.biomechanics

import org.junit.Test
import org.junit.Assert.*
import bikefitstudio.pose.Landmark
import bikefitstudio.pose.PoseLandmarkIndex

class AnkleAngleAspectRatioTest {
    
    // Helper to create a landmark
    private fun lm(x: Float, y: Float): Landmark {
        return Landmark(x, y, 0f, 1.0f, 1.0f) // z=0, visibility=1, presence=1
    }

    @Test
    fun `test aspect ratio affects ankle angle calculation`() {
        // Setup a scenario where aspect ratio matters.
        // Ankle at origin (normalized 0.5, 0.5 for simplicity in logic, but let's use relative vectors)
        
        // Let's put Ankle at (0,0) for simpler math mentally, even if valid landmarks are usually 0..1
        // We act as if these are delta vectors or we position landmarks such that vectors are these.
        
        // Case: Knee is diagonal up-right from Ankle. Foot is diagonal down-right from Ankle.
        // Ankle: (0,0)
        // Knee: (1, 1) -> normalized distance 
        // Foot: (1, 0) -> Horizontal foot
        
        // Normalized Space (Square):
        // Vector Knee-Ankle: (-1, -1) -> Angle 225 deg (or 45 from horizontal)
        // Vector Foot-Ankle: (-1, 0) -> Angle 180 deg.
        // Included angle should be 45 degrees.
        
        // Portrait Image (Width 100, Height 200):
        // Ankle: (0, 0)
        // Knee: (1 * 100, 1 * 200) = (100, 200)
        // Foot: (1 * 100, 0) = (100, 0)
        
        // Vector Knee-Ankle: (100, 200). Slope 2. Angle atan(2) = 63.43 degrees from vertical? 
        // Wait, Vector Ankle->Knee is (100, 200). Angle from X axis = atan(200/100) = 63.43 deg.
        // Vector Ankle->Foot is (100, 0). Angle from X axis = 0 deg.
        // Included angle = 63.43 degrees.
        
        // So: Square = 45 deg, Portrait = 63.43 deg.
        // Reference Ankle angle logic:
        // angleAtVertex(Knee, Ankle, FootIndex)
        // Vertex is Ankle.
        // Ray 1: Ankle->Knee. Ray 2: Ankle->FootIndex.
        
        val ankle = lm(0.0f, 0.0f)
        val knee = lm(0.1f, 0.1f) // 45 deg in square
        val foot = lm(0.1f, 0.0f) // 0 deg
        
        val landmarks = MutableList(33) { lm(0f, 0f) }
        landmarks[PoseLandmarkIndex.RIGHT_ANKLE] = ankle
        landmarks[PoseLandmarkIndex.RIGHT_KNEE] = knee
        landmarks[PoseLandmarkIndex.RIGHT_FOOT_INDEX] = foot
        
        // 1. Square Aspect Ratio (Default/Normalized)
        // Expected: 45 degrees
        val resultSquare = AnkleAngleCalculator.calculateAnkleAngleFromLandmarks(
            landmarks, 
            BodySide.RIGHT,
            imageWidth = 100,
            imageHeight = 100
        )
        // Ankle Plantarflexion = Vertex - 90.
        // Vertex angle = 45.
        // Plantarflexion = 45 - 90 = -45.
        assertEquals(45f, resultSquare.angle + 90f, 0.1f)
        
        // 2. Portrait Aspect Ratio (1:2)
        // Expected: ~63.43 degrees
        val resultPortrait = AnkleAngleCalculator.calculateAnkleAngleFromLandmarks(
            landmarks,
            BodySide.RIGHT,
            imageWidth = 100,
            imageHeight = 200
        )
        
        val vertexAnglePortrait = resultPortrait.angle + 90f
        assertEquals(63.43f, vertexAnglePortrait, 0.1f)
        
        assertNotEquals(resultSquare.angle, resultPortrait.angle, 0.1f)
    }
}
