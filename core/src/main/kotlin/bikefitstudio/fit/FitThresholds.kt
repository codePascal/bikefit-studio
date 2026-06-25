package bikefitstudio.fit

/**
 * Context-aware fit thresholds for all 5 key metrics.
 * 
 * Metric definitions:
 * - Hip angle: minimum internal hip angle at TDC
 * - Knee angle: internal knee angle at BDC (180° - flexion)
 * - Torso angle: torso vs horizontal (0° = flat, 90° = upright)
 * - KOPS: normalized knee–pedal offset (femur-length normalized)
 * - Ankle angle: plantarflexion at BDC
 * 
 * These ranges are typical/commonly observed, not prescriptions.
 * They provide a sensible starting point for bike fit analysis.
 */
data class FitThresholds(
    // Hip angle at TDC (minimum during cycle)
    val hipAngleMin: Float,
    val hipAngleMax: Float,
    
    // Knee angle at BDC (internal angle, NOT flexion)
    // Note: flexion = 180 - internal angle
    val kneeAngleMin: Float,  // Below = saddle too low
    val kneeAngleMax: Float,  // Above = saddle too high
    
    // Torso angle from horizontal
    val torsoAngleMin: Float,  // Below = too aggressive
    val torsoAngleMax: Float,  // Above = too upright
    
    // KOPS normalized offset (normalized by femur length)
    val kopsMin: Float,  // Knee behind pedal
    val kopsMax: Float,  // Knee ahead of pedal
    
    // Ankle plantarflexion at BDC
    val ankleAngleMin: Float,
    val ankleAngleMax: Float
) {
    companion object {
        // ==================== GLOBAL SAFETY FLOORS ====================
        // These limits are NEVER crossed regardless of context or bias.
        // Even if a pro rider does it, the app shouldn't normalize it.
        
        /**
         * Hip angle must never be less than 40°.
         * Below this, breathing is severely restricted and power is compromised.
         */
        const val SAFETY_HIP_MIN = 40f
        
        /**
         * Knee internal angle must never be less than 140° (40°+ flexion).
         * Below this, significant knee stress and power loss occurs.
         */
        const val SAFETY_KNEE_MIN = 140f
        
        /**
         * Always flag ankle plantarflexion > 35°.
         * Above this, Achilles tendon risk is significant regardless of context.
         */
        const val SAFETY_ANKLE_MAX = 35f

        // ==================== BASELINE RANGES BY CONTEXT ====================
        // Note: Knee flexion from spec is converted to internal angle:
        // internal_angle = 180 - flexion
        // Spec: 30-40° flexion → 140-150° internal angle

        /**
         * Road (race / general road): Balanced position for varied terrain.
         * 
         * Hip: 45-50° (allows good power with breathing room)
         * Knee: 140-150° internal (30-40° flexion)
         * Torso: 35-45° (moderate aerodynamic position)
         * KOPS: ±0.15 (neutral starting point)
         * Ankle: 20-30° plantarflexion
         */
        private val ROAD_BASELINE = FitThresholds(
            hipAngleMin = 45f, hipAngleMax = 50f,
            kneeAngleMin = 140f, kneeAngleMax = 150f,
            torsoAngleMin = 35f, torsoAngleMax = 45f,
            kopsMin = -0.15f, kopsMax = 0.15f,
            ankleAngleMin = 20f, ankleAngleMax = 30f
        )

        /**
         * Endurance Road: More upright, sustainable position for long rides.
         * 
         * Hip: 50-55° (open for breathing on long efforts)
         * Knee: 140-148° internal (32-40° flexion)
         * Torso: 45-55° (more upright for comfort)
         * KOPS: ±0.15 (neutral)
         * Ankle: 20-30° plantarflexion
         */
        private val ENDURANCE_BASELINE = FitThresholds(
            hipAngleMin = 50f, hipAngleMax = 55f,
            kneeAngleMin = 140f, kneeAngleMax = 148f,
            torsoAngleMin = 45f, torsoAngleMax = 55f,
            kopsMin = -0.15f, kopsMax = 0.15f,
            ankleAngleMin = 20f, ankleAngleMax = 30f
        )

        /**
         * Gravel / Adventure: Prioritizes hip openness and control.
         * 
         * Hip: 50-55° (open for terrain variability)
         * Knee: 140-148° internal (32-40° flexion)
         * Torso: 45-60° (wider range due to terrain variability)
         * KOPS: ±0.15 (neutral)
         * Ankle: 20-30° plantarflexion
         */
        private val GRAVEL_BASELINE = FitThresholds(
            hipAngleMin = 50f, hipAngleMax = 55f,
            kneeAngleMin = 140f, kneeAngleMax = 148f,
            torsoAngleMin = 45f, torsoAngleMax = 60f,
            kopsMin = -0.15f, kopsMax = 0.15f,
            ankleAngleMin = 20f, ankleAngleMax = 30f
        )

        /**
         * TT / Triathlon: Aggressive aerodynamic position.
         * 
         * Hip: 40-45° (limiting factor, not torso)
         * Knee: 142-150° internal (30-38° flexion)
         * Torso: 10-25° (very aggressive)
         * KOPS: -0.10 to +0.20 (slight forward bias acceptable)
         * Ankle: 20-30° plantarflexion
         */
        private val TT_BASELINE = FitThresholds(
            hipAngleMin = 40f, hipAngleMax = 45f,
            kneeAngleMin = 142f, kneeAngleMax = 150f,
            torsoAngleMin = 10f, torsoAngleMax = 25f,
            kopsMin = -0.10f, kopsMax = 0.20f,
            ankleAngleMin = 20f, ankleAngleMax = 30f
        )

        /**
         * Indoor (Trainer / Smart Bike): Upright, breathing-focused.
         * 
         * Why Indoor looks different:
         * - No aero requirement
         * - Less bike movement
         * - Higher sustained joint loading
         * - Breathing & cooling prioritized
         * 
         * Hip: 50-55° (open for sustained efforts)
         * Knee: 142-148° internal (32-38° flexion)
         * Torso: 50-60° (upright for breathing/cooling)
         * KOPS: ±0.15 (neutral)
         * Ankle: 20-30° plantarflexion
         */
        private val INDOOR_BASELINE = FitThresholds(
            hipAngleMin = 50f, hipAngleMax = 55f,
            kneeAngleMin = 142f, kneeAngleMax = 148f,
            torsoAngleMin = 50f, torsoAngleMax = 60f,
            kopsMin = -0.15f, kopsMax = 0.15f,
            ankleAngleMin = 20f, ankleAngleMax = 30f
        )

        /**
         * Get baseline thresholds for a riding context.
         */
        fun baselineFor(context: RidingContext): FitThresholds {
            return when (context) {
                RidingContext.ROAD -> ROAD_BASELINE
                RidingContext.ENDURANCE_ROAD -> ENDURANCE_BASELINE
                RidingContext.GRAVEL -> GRAVEL_BASELINE
                RidingContext.TT_TRIATHLON -> TT_BASELINE
                RidingContext.INDOOR -> INDOOR_BASELINE
            }
        }

        /**
         * Compute final thresholds: baseline + bias, clamped to safety floors.
         * 
         * This is the main entry point for getting context-aware thresholds.
         * It applies the three-step process:
         * 1. Get baseline for context
         * 2. Apply bias modifier
         * 3. Clamp to global safety bounds
         */
        fun forContextAndBias(context: RidingContext, bias: FitBias): FitThresholds {
            val baseline = baselineFor(context)
            
            // Apply bias modifiers
            val modified = when (bias) {
                FitBias.COMFORT -> baseline.applyComfortBias()
                FitBias.NEUTRAL -> baseline
                FitBias.PERFORMANCE -> baseline.applyPerformanceBias()
            }
            
            // Clamp to global safety floors
            return modified.clampToSafetyFloors()
        }
    }

    /**
     * Apply comfort bias: opens joints, improves breathing tolerance.
     * 
     * Modifiers:
     * - Hip: +3 to +5° (opens hip)
     * - Knee: -2° on internal angle (less extension, more flexion tolerance)
     * - Torso: +5° (more upright)
     * - KOPS: No change (movement strategy, not comfort)
     * - Ankle: No change (only interpretation changes - flag >30° earlier)
     */
    private fun applyComfortBias(): FitThresholds {
        return copy(
            hipAngleMin = hipAngleMin + 3f,
            hipAngleMax = hipAngleMax + 5f,
            kneeAngleMin = kneeAngleMin - 2f,  // Allow more flexion (lower internal angle OK)
            kneeAngleMax = kneeAngleMax - 2f,  // Reduce max extension
            torsoAngleMin = torsoAngleMin + 5f,
            torsoAngleMax = torsoAngleMax + 5f
            // KOPS and Ankle: unchanged
        )
    }

    /**
     * Apply performance bias: narrows window for mechanical leverage.
     * 
     * Modifiers:
     * - Hip: -2 to -3° (tighter hip angle acceptable)
     * - Knee: +2° (more extension allowed)
     * - Torso: -5° (more aggressive)
     * - KOPS: No change (movement strategy)
     * - Ankle: No change
     * 
     * Still stays within safe biomechanical bounds after clamping.
     */
    private fun applyPerformanceBias(): FitThresholds {
        return copy(
            hipAngleMin = hipAngleMin - 3f,
            hipAngleMax = hipAngleMax - 2f,
            kneeAngleMin = kneeAngleMin + 2f,
            kneeAngleMax = kneeAngleMax + 2f,
            torsoAngleMin = torsoAngleMin - 5f,
            torsoAngleMax = torsoAngleMax - 5f
            // KOPS and Ankle: unchanged
        )
    }

    /**
     * Clamp values to global safety floors.
     * 
     * This ensures no combination of context + bias produces
     * dangerous or nonsensical thresholds.
     */
    private fun clampToSafetyFloors(): FitThresholds {
        return copy(
            hipAngleMin = maxOf(hipAngleMin, SAFETY_HIP_MIN),
            kneeAngleMin = maxOf(kneeAngleMin, SAFETY_KNEE_MIN),
            ankleAngleMax = minOf(ankleAngleMax, SAFETY_ANKLE_MAX)
        )
    }

    /**
     * Get a human-readable summary of these thresholds.
     */
    fun toSummaryString(): String {
        return buildString {
            appendLine("Hip @ TDC: ${hipAngleMin.toInt()}-${hipAngleMax.toInt()}°")
            appendLine("Knee @ BDC: ${kneeAngleMin.toInt()}-${kneeAngleMax.toInt()}° (${(180-kneeAngleMax).toInt()}-${(180-kneeAngleMin).toInt()}° flexion)")
            appendLine("Torso: ${torsoAngleMin.toInt()}-${torsoAngleMax.toInt()}°")
            appendLine("KOPS: ${kopsMin} to ${kopsMax}")
            append("Ankle PF: ${ankleAngleMin.toInt()}-${ankleAngleMax.toInt()}°")
        }
    }
}
