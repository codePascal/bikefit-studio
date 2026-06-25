package bikefitstudio.biomechanics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

/**
 * Unit tests for Vector2D.
 */
class Vector2DTest {

    // ==================== Magnitude Tests ====================

    @Test
    fun `magnitude of 3-4-5 triangle vector`() {
        val v = Vector2D(3f, 4f)
        assertEquals(5f, v.magnitude(), 0.001f)
    }

    @Test
    fun `magnitude of unit vector is 1`() {
        assertEquals(1f, Vector2D.RIGHT.magnitude(), 0.001f)
        assertEquals(1f, Vector2D.UP.magnitude(), 0.001f)
    }

    @Test
    fun `magnitude of zero vector is 0`() {
        assertEquals(0f, Vector2D.ZERO.magnitude(), 0.001f)
    }

    @Test
    fun `magnitudeSquared avoids sqrt`() {
        val v = Vector2D(3f, 4f)
        assertEquals(25f, v.magnitudeSquared(), 0.001f)
    }

    // ==================== Normalization Tests ====================

    @Test
    fun `normalized vector has magnitude 1`() {
        val v = Vector2D(3f, 4f)
        val normalized = v.normalized()
        assertEquals(1f, normalized.magnitude(), 0.001f)
    }

    @Test
    fun `normalized preserves direction`() {
        val v = Vector2D(3f, 4f)
        val normalized = v.normalized()
        assertEquals(0.6f, normalized.x, 0.001f)  // 3/5
        assertEquals(0.8f, normalized.y, 0.001f)  // 4/5
    }

    @Test
    fun `normalized zero vector returns zero`() {
        val normalized = Vector2D.ZERO.normalized()
        assertEquals(Vector2D.ZERO, normalized)
    }

    // ==================== Dot Product Tests ====================

    @Test
    fun `dot product of perpendicular vectors is zero`() {
        val dot = Vector2D.RIGHT.dot(Vector2D.UP)
        assertEquals(0f, dot, 0.001f)
    }

    @Test
    fun `dot product of parallel vectors`() {
        val v1 = Vector2D(2f, 0f)
        val v2 = Vector2D(3f, 0f)
        assertEquals(6f, v1.dot(v2), 0.001f)
    }

    @Test
    fun `dot product of opposite vectors is negative`() {
        val dot = Vector2D.RIGHT.dot(Vector2D.LEFT)
        assertEquals(-1f, dot, 0.001f)
    }

    @Test
    fun `dot product general case`() {
        val v1 = Vector2D(1f, 2f)
        val v2 = Vector2D(3f, 4f)
        // 1*3 + 2*4 = 11
        assertEquals(11f, v1.dot(v2), 0.001f)
    }

    // ==================== Cross Product Tests ====================

    @Test
    fun `cross product of parallel vectors is zero`() {
        val v1 = Vector2D(2f, 0f)
        val v2 = Vector2D(4f, 0f)
        assertEquals(0f, v1.cross(v2), 0.001f)
    }

    @Test
    fun `cross product right to up is positive`() {
        val cross = Vector2D.RIGHT.cross(Vector2D.UP)
        assertEquals(1f, cross, 0.001f)
    }

    @Test
    fun `cross product up to right is negative`() {
        val cross = Vector2D.UP.cross(Vector2D.RIGHT)
        assertEquals(-1f, cross, 0.001f)
    }

    // ==================== Angle Tests ====================

    @Test
    fun `angle between perpendicular vectors is 90 degrees`() {
        val angle = Vector2D.RIGHT.angleTo(Vector2D.UP)
        assertEquals(90f, angle, 0.001f)
    }

    @Test
    fun `angle between parallel vectors is 0 degrees`() {
        val v1 = Vector2D(1f, 0f)
        val v2 = Vector2D(5f, 0f)
        assertEquals(0f, v1.angleTo(v2), 0.001f)
    }

    @Test
    fun `angle between opposite vectors is 180 degrees`() {
        val angle = Vector2D.RIGHT.angleTo(Vector2D.LEFT)
        assertEquals(180f, angle, 0.001f)
    }

    @Test
    fun `angle between 45 degree vectors`() {
        val v1 = Vector2D(1f, 0f)
        val v2 = Vector2D(1f, 1f)
        assertEquals(45f, v1.angleTo(v2), 0.001f)
    }

    @Test
    fun `signedAngleTo counterclockwise is positive`() {
        val angle = Vector2D.RIGHT.signedAngleTo(Vector2D.UP)
        assertEquals(90f, angle, 0.001f)
    }

    @Test
    fun `signedAngleTo clockwise is negative`() {
        val angle = Vector2D.UP.signedAngleTo(Vector2D.RIGHT)
        assertEquals(-90f, angle, 0.001f)
    }

    @Test
    fun `angle from x-axis`() {
        assertEquals(0f, Vector2D.RIGHT.angle(), 0.001f)
        assertEquals(90f, Vector2D.UP.angle(), 0.001f)
        assertEquals(180f, Vector2D.LEFT.angle(), 0.1f)
        assertEquals(-90f, Vector2D.DOWN.angle(), 0.001f)
    }

    // ==================== Arithmetic Operations Tests ====================

    @Test
    fun `vector addition`() {
        val v1 = Vector2D(1f, 2f)
        val v2 = Vector2D(3f, 4f)
        val sum = v1 + v2
        assertEquals(4f, sum.x, 0.001f)
        assertEquals(6f, sum.y, 0.001f)
    }

    @Test
    fun `vector subtraction`() {
        val v1 = Vector2D(5f, 7f)
        val v2 = Vector2D(2f, 3f)
        val diff = v1 - v2
        assertEquals(3f, diff.x, 0.001f)
        assertEquals(4f, diff.y, 0.001f)
    }

    @Test
    fun `scalar multiplication`() {
        val v = Vector2D(2f, 3f)
        val scaled = v * 4f
        assertEquals(8f, scaled.x, 0.001f)
        assertEquals(12f, scaled.y, 0.001f)
    }

    @Test
    fun `scalar division`() {
        val v = Vector2D(8f, 12f)
        val scaled = v / 4f
        assertEquals(2f, scaled.x, 0.001f)
        assertEquals(3f, scaled.y, 0.001f)
    }

    @Test
    fun `division by zero throws exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            Vector2D(1f, 1f) / 0f
        }
    }

    @Test
    fun `vector negation`() {
        val v = Vector2D(3f, -4f)
        val negated = -v
        assertEquals(-3f, negated.x, 0.001f)
        assertEquals(4f, negated.y, 0.001f)
    }

    // ==================== Distance Tests ====================

    @Test
    fun `distanceTo calculates euclidean distance`() {
        val v1 = Vector2D(0f, 0f)
        val v2 = Vector2D(3f, 4f)
        assertEquals(5f, v1.distanceTo(v2), 0.001f)
    }

    @Test
    fun `distanceTo is symmetric`() {
        val v1 = Vector2D(1f, 2f)
        val v2 = Vector2D(4f, 6f)
        assertEquals(v1.distanceTo(v2), v2.distanceTo(v1), 0.001f)
    }

    @Test
    fun `distanceSquaredTo avoids sqrt`() {
        val v1 = Vector2D(0f, 0f)
        val v2 = Vector2D(3f, 4f)
        assertEquals(25f, v1.distanceSquaredTo(v2), 0.001f)
    }

    // ==================== Interpolation Tests ====================

    @Test
    fun `lerp at 0 returns start`() {
        val v1 = Vector2D(0f, 0f)
        val v2 = Vector2D(10f, 10f)
        val result = v1.lerp(v2, 0f)
        assertEquals(0f, result.x, 0.001f)
        assertEquals(0f, result.y, 0.001f)
    }

    @Test
    fun `lerp at 1 returns end`() {
        val v1 = Vector2D(0f, 0f)
        val v2 = Vector2D(10f, 10f)
        val result = v1.lerp(v2, 1f)
        assertEquals(10f, result.x, 0.001f)
        assertEquals(10f, result.y, 0.001f)
    }

    @Test
    fun `lerp at 0_5 returns midpoint`() {
        val v1 = Vector2D(0f, 0f)
        val v2 = Vector2D(10f, 10f)
        val result = v1.lerp(v2, 0.5f)
        assertEquals(5f, result.x, 0.001f)
        assertEquals(5f, result.y, 0.001f)
    }

    // ==================== Perpendicular Tests ====================

    @Test
    fun `perpendicular rotates 90 degrees counterclockwise`() {
        val perp = Vector2D.RIGHT.perpendicular()
        assertEquals(0f, perp.x, 0.001f)
        assertEquals(1f, perp.y, 0.001f)
    }

    @Test
    fun `perpendicular is perpendicular`() {
        val v = Vector2D(3f, 4f)
        val perp = v.perpendicular()
        assertEquals(0f, v.dot(perp), 0.001f)
    }

    // ==================== Projection Tests ====================

    @Test
    fun `projectOnto parallel vector`() {
        val v = Vector2D(5f, 0f)
        val onto = Vector2D(1f, 0f)
        val proj = v.projectOnto(onto)
        assertEquals(5f, proj.x, 0.001f)
        assertEquals(0f, proj.y, 0.001f)
    }

    @Test
    fun `projectOnto perpendicular vector is zero`() {
        val v = Vector2D(5f, 0f)
        val onto = Vector2D(0f, 1f)
        val proj = v.projectOnto(onto)
        assertEquals(0f, proj.magnitude(), 0.001f)
    }

    @Test
    fun `projectOnto diagonal`() {
        val v = Vector2D(4f, 0f)
        val onto = Vector2D(1f, 1f)
        val proj = v.projectOnto(onto)
        assertEquals(2f, proj.x, 0.001f)
        assertEquals(2f, proj.y, 0.001f)
    }

    // ==================== Factory Method Tests ====================

    @Test
    fun `fromPoints creates vector between points`() {
        val v = Vector2D.fromPoints(1f, 2f, 4f, 6f)
        assertEquals(3f, v.x, 0.001f)
        assertEquals(4f, v.y, 0.001f)
    }

    @Test
    fun `fromPoints with Vector2D arguments`() {
        val from = Vector2D(1f, 2f)
        val to = Vector2D(4f, 6f)
        val v = Vector2D.fromPoints(from, to)
        assertEquals(3f, v.x, 0.001f)
        assertEquals(4f, v.y, 0.001f)
    }

    @Test
    fun `fromAngle creates unit vector`() {
        val v = Vector2D.fromAngle(0f)
        assertEquals(1f, v.x, 0.001f)
        assertEquals(0f, v.y, 0.001f)

        val v90 = Vector2D.fromAngle(90f)
        assertEquals(0f, v90.x, 0.001f)
        assertEquals(1f, v90.y, 0.001f)
    }

    // ==================== Angle at Vertex Tests ====================

    @Test
    fun `angleAtVertex for right angle`() {
        val a = Vector2D(0f, 0f)
        val b = Vector2D(1f, 0f)  // Vertex
        val c = Vector2D(1f, 1f)
        val angle = Vector2D.angleAtVertex(a, b, c)
        assertEquals(90f, angle, 0.001f)
    }

    @Test
    fun `angleAtVertex for straight line is 180 degrees`() {
        val a = Vector2D(0f, 0f)
        val b = Vector2D(1f, 0f)  // Vertex
        val c = Vector2D(2f, 0f)
        val angle = Vector2D.angleAtVertex(a, b, c)
        assertEquals(180f, angle, 0.001f)
    }

    @Test
    fun `angleAtVertex for 60 degree triangle`() {
        // Equilateral triangle
        val a = Vector2D(0f, 0f)
        val b = Vector2D(1f, 0f)  // Vertex
        val c = Vector2D(0.5f, sqrt(3f) / 2f)
        val angle = Vector2D.angleAtVertex(a, b, c)
        assertEquals(60f, angle, 0.1f)
    }

    @Test
    fun `angleAtVertex for knee joint simulation`() {
        // Simulate hip-knee-ankle with 90 degree bend
        val hip = Vector2D(0f, 1f)
        val knee = Vector2D(0f, 0f)
        val ankle = Vector2D(1f, 0f)
        val angle = Vector2D.angleAtVertex(hip, knee, ankle)
        assertEquals(90f, angle, 0.001f)
    }

    // ==================== Constant Tests ====================

    @Test
    fun `unit vectors are correct`() {
        assertEquals(Vector2D(1f, 0f), Vector2D.RIGHT)
        assertEquals(Vector2D(0f, 1f), Vector2D.UP)
        assertEquals(Vector2D(-1f, 0f), Vector2D.LEFT)
        assertEquals(Vector2D(0f, -1f), Vector2D.DOWN)
    }

    @Test
    fun `zero vector is zero`() {
        assertEquals(0f, Vector2D.ZERO.x, 0.001f)
        assertEquals(0f, Vector2D.ZERO.y, 0.001f)
    }
}
