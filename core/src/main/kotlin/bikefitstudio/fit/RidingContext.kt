package bikefitstudio.fit

/**
 * Riding context determines the baseline fit ranges.
 * 
 * Different cycling disciplines have different optimal positions
 * based on aerodynamics, terrain, duration, and comfort requirements.
 */
enum class RidingContext(
    val displayName: String,
    val description: String
) {
    /**
     * Road racing / general road cycling.
     * Balanced position for varied terrain and efforts.
     */
    ROAD(
        displayName = "Road",
        description = "Road racing and general road cycling"
    ),

    /**
     * Endurance road cycling.
     * More upright, sustainable position for long rides.
     */
    ENDURANCE_ROAD(
        displayName = "Endurance",
        description = "Long-distance endurance riding"
    ),

    /**
     * Gravel and adventure cycling.
     * Prioritizes hip openness and control over rough terrain.
     */
    GRAVEL(
        displayName = "Gravel",
        description = "Gravel and adventure cycling"
    ),

    /**
     * Time trial and triathlon.
     * Aggressive aerodynamic position with hip angle as limiting factor.
     */
    TT_TRIATHLON(
        displayName = "TT / Triathlon",
        description = "Time trial and triathlon racing"
    ),

    /**
     * Indoor trainer / smart bike.
     * More upright position due to no aero requirement,
     * less bike movement, and prioritized breathing/cooling.
     */
    INDOOR(
        displayName = "Indoor",
        description = "Indoor trainer and smart bike"
    );

    companion object {
        /**
         * Default context for new users.
         */
        val DEFAULT = ROAD
    }
}

/**
 * Fit bias modifies the baseline ranges based on rider priority.
 * 
 * Bias is applied on top of the riding context - it nudges the ranges
 * rather than replacing them entirely.
 */
enum class FitBias(
    val displayName: String,
    val description: String
) {
    /**
     * Comfort bias opens joints and improves breathing tolerance.
     * Reduces patellofemoral stress.
     */
    COMFORT(
        displayName = "Comfort",
        description = "Prioritize comfort and joint health"
    ),

    /**
     * Neutral bias uses the baseline ranges without modification.
     */
    NEUTRAL(
        displayName = "Neutral",
        description = "Balanced comfort and performance"
    ),

    /**
     * Performance bias narrows the acceptable window.
     * Increases mechanical leverage and aerodynamic potential.
     */
    PERFORMANCE(
        displayName = "Performance",
        description = "Prioritize power and aerodynamics"
    );

    companion object {
        /**
         * Default bias for new users.
         */
        val DEFAULT = NEUTRAL
    }
}
