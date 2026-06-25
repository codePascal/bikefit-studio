package bikefitstudio.biomechanics

import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * A 2D vector with x and y components.
 * 
 * This data class provides common vector operations needed for
 * biomechanical angle calculations. All operations are pure functions
 * that return new instances rather than mutating.
 * 
 * Usage:
 * ```
 * val v1 = Vector2D(3f, 4f)
 * val v2 = Vector2D(1f, 0f)
 * 
 * val magnitude = v1.magnitude()  // 5.0
 * val dot = v1.dot(v2)            // 3.0
 * val angle = v1.angleTo(v2)      // angle in degrees
 * ```
 * 
 * @param x The x component of the vector
 * @param y The y component of the vector
 */
data class Vector2D(
    val x: Float,
    val y: Float
) {
    /**
     * Calculates the magnitude (length) of this vector.
     * 
     * @return The Euclidean length of the vector
     */
    fun magnitude(): Float {
        return sqrt(x * x + y * y)
    }

    /**
     * Calculates the squared magnitude of this vector.
     * More efficient than magnitude() when only comparing lengths.
     * 
     * @return The squared length of the vector
     */
    fun magnitudeSquared(): Float {
        return x * x + y * y
    }

    /**
     * Returns a normalized (unit length) version of this vector.
     * 
     * @return A new vector with magnitude 1, or ZERO if this vector has zero length
     */
    fun normalized(): Vector2D {
        val mag = magnitude()
        return if (mag > EPSILON) {
            Vector2D(x / mag, y / mag)
        } else {
            ZERO
        }
    }

    /**
     * Calculates the dot product of this vector with another.
     * 
     * The dot product is: a·b = ax*bx + ay*by
     * 
     * @param other The other vector
     * @return The dot product
     */
    fun dot(other: Vector2D): Float {
        return x * other.x + y * other.y
    }

    /**
     * Calculates the 2D cross product (z-component of 3D cross product).
     * 
     * The cross product is: a×b = ax*by - ay*bx
     * 
     * This is useful for determining the sign of the angle between vectors.
     * Positive means counterclockwise, negative means clockwise.
     * 
     * @param other The other vector
     * @return The cross product (scalar)
     */
    fun cross(other: Vector2D): Float {
        return x * other.y - y * other.x
    }

    /**
     * Calculates the angle from this vector to another vector in degrees.
     * 
     * The angle is always positive and in the range [0, 180].
     * Use signedAngleTo() if you need the direction.
     * 
     * @param other The other vector
     * @return The angle in degrees [0, 180]
     */
    fun angleTo(other: Vector2D): Float {
        val magProduct = this.magnitude() * other.magnitude()
        if (magProduct < EPSILON) return 0f
        
        // Clamp to handle floating point errors
        val cosAngle = (this.dot(other) / magProduct).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosAngle.toDouble())).toFloat()
    }

    /**
     * Calculates the signed angle from this vector to another vector in degrees.
     * 
     * Positive angle means counterclockwise rotation from this to other.
     * Negative angle means clockwise rotation.
     * 
     * @param other The other vector
     * @return The signed angle in degrees [-180, 180]
     */
    fun signedAngleTo(other: Vector2D): Float {
        val angle = atan2(cross(other).toDouble(), dot(other).toDouble())
        return Math.toDegrees(angle).toFloat()
    }

    /**
     * Returns the angle of this vector from the positive X-axis in degrees.
     * 
     * @return The angle in degrees [-180, 180]
     */
    fun angle(): Float {
        return Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat()
    }

    /**
     * Adds another vector to this one.
     * 
     * @param other The vector to add
     * @return A new vector representing the sum
     */
    operator fun plus(other: Vector2D): Vector2D {
        return Vector2D(x + other.x, y + other.y)
    }

    /**
     * Subtracts another vector from this one.
     * 
     * @param other The vector to subtract
     * @return A new vector representing the difference
     */
    operator fun minus(other: Vector2D): Vector2D {
        return Vector2D(x - other.x, y - other.y)
    }

    /**
     * Multiplies this vector by a scalar.
     * 
     * @param scalar The scalar to multiply by
     * @return A new scaled vector
     */
    operator fun times(scalar: Float): Vector2D {
        return Vector2D(x * scalar, y * scalar)
    }

    /**
     * Divides this vector by a scalar.
     * 
     * @param scalar The scalar to divide by
     * @return A new scaled vector
     * @throws IllegalArgumentException if scalar is zero
     */
    operator fun div(scalar: Float): Vector2D {
        require(scalar != 0f) { "Cannot divide by zero" }
        return Vector2D(x / scalar, y / scalar)
    }

    /**
     * Negates this vector.
     * 
     * @return A new vector pointing in the opposite direction
     */
    operator fun unaryMinus(): Vector2D {
        return Vector2D(-x, -y)
    }

    /**
     * Calculates the distance from this point to another point.
     * 
     * @param other The other point
     * @return The Euclidean distance between the points
     */
    fun distanceTo(other: Vector2D): Float {
        return (this - other).magnitude()
    }

    /**
     * Calculates the squared distance from this point to another.
     * More efficient than distanceTo() when only comparing distances.
     * 
     * @param other The other point
     * @return The squared distance between the points
     */
    fun distanceSquaredTo(other: Vector2D): Float {
        return (this - other).magnitudeSquared()
    }

    /**
     * Linearly interpolates between this vector and another.
     * 
     * @param other The target vector
     * @param t Interpolation factor (0 = this, 1 = other)
     * @return The interpolated vector
     */
    fun lerp(other: Vector2D, t: Float): Vector2D {
        return Vector2D(
            x + (other.x - x) * t,
            y + (other.y - y) * t
        )
    }

    /**
     * Returns a vector perpendicular to this one (rotated 90° counterclockwise).
     * 
     * @return The perpendicular vector
     */
    fun perpendicular(): Vector2D {
        return Vector2D(-y, x)
    }

    /**
     * Projects this vector onto another vector.
     * 
     * @param onto The vector to project onto
     * @return The projection of this vector onto the other
     */
    fun projectOnto(onto: Vector2D): Vector2D {
        val magSquared = onto.magnitudeSquared()
        if (magSquared < EPSILON) return ZERO
        
        val scalar = this.dot(onto) / magSquared
        return onto * scalar
    }

    companion object {
        /** A very small value for floating point comparisons */
        const val EPSILON = 1e-6f

        /** The zero vector (0, 0) */
        val ZERO = Vector2D(0f, 0f)

        /** Unit vector pointing right (1, 0) */
        val RIGHT = Vector2D(1f, 0f)

        /** Unit vector pointing up (0, 1) */
        val UP = Vector2D(0f, 1f)

        /** Unit vector pointing left (-1, 0) */
        val LEFT = Vector2D(-1f, 0f)

        /** Unit vector pointing down (0, -1) */
        val DOWN = Vector2D(0f, -1f)

        /**
         * Creates a vector from two points (from -> to).
         * 
         * @param fromX Starting point X
         * @param fromY Starting point Y
         * @param toX Ending point X
         * @param toY Ending point Y
         * @return A vector from the first point to the second
         */
        fun fromPoints(fromX: Float, fromY: Float, toX: Float, toY: Float): Vector2D {
            return Vector2D(toX - fromX, toY - fromY)
        }

        /**
         * Creates a vector from two Vector2D points (from -> to).
         * 
         * @param from Starting point
         * @param to Ending point
         * @return A vector from the first point to the second
         */
        fun fromPoints(from: Vector2D, to: Vector2D): Vector2D {
            return to - from
        }

        /**
         * Creates a unit vector from an angle in degrees.
         * 
         * @param degrees Angle from positive X-axis
         * @return A unit vector at that angle
         */
        fun fromAngle(degrees: Float): Vector2D {
            val radians = Math.toRadians(degrees.toDouble())
            return Vector2D(
                kotlin.math.cos(radians).toFloat(),
                kotlin.math.sin(radians).toFloat()
            )
        }

        /**
         * Creates a vector from a Landmark, optionally scaling to image dimensions.
         * 
         * @param landmark The pose landmark
         * @param width Image width (defaults to 0, meaning no scaling)
         * @param height Image height (defaults to 0, meaning no scaling)
         * @return Vector2D with either normalized or pixel coordinates
         */
        fun fromLandmark(landmark: bikefitstudio.pose.Landmark, width: Int = 0, height: Int = 0): Vector2D {
            return if (width > 0 && height > 0) {
                Vector2D(landmark.x * width.toFloat(), landmark.y * height.toFloat())
            } else {
                Vector2D(landmark.x, landmark.y)
            }
        }

        /**
         * Calculates the angle at point B in triangle ABC.
         * 
         * This is useful for joint angle calculations where B is the joint.
         * 
         * @param a First point (e.g., hip)
         * @param b Middle point - the vertex of the angle (e.g., knee)
         * @param c Third point (e.g., ankle)
         * @return The angle at B in degrees [0, 180]
         */
        fun angleAtVertex(a: Vector2D, b: Vector2D, c: Vector2D): Float {
            val ba = a - b  // Vector from B to A
            val bc = c - b  // Vector from B to C
            return ba.angleTo(bc)
        }
        
        /**
         * Calculates the intersection point of two lines.
         * 
         * Line 1 is defined by points p1 and p2.
         * Line 2 is defined by points p3 and p4.
         * 
         * The lines are treated as infinite (extended beyond their endpoints).
         * 
         * @param p1 First point on line 1
         * @param p2 Second point on line 1
         * @param p3 First point on line 2
         * @param p4 Second point on line 2
         * @return The intersection point, or null if lines are parallel
         */
        fun lineIntersection(p1: Vector2D, p2: Vector2D, p3: Vector2D, p4: Vector2D): Vector2D? {
            // Line 1: p1 + t * (p2 - p1)
            // Line 2: p3 + u * (p4 - p3)
            val d1 = p2 - p1  // Direction of line 1
            val d2 = p4 - p3  // Direction of line 2
            
            val denominator = d1.cross(d2)
            
            // If denominator is zero, lines are parallel
            if (kotlin.math.abs(denominator) < EPSILON) {
                return null
            }
            
            val d3 = p3 - p1
            val t = d3.cross(d2) / denominator
            
            // Calculate intersection point
            return p1 + d1 * t
        }
    }
}
