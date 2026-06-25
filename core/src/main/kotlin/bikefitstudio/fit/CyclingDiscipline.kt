package bikefitstudio.fit

/**
 * Cycling disciplines with different optimal fit characteristics.
 * 
 * Each discipline has different typical ranges for bike fit metrics
 * due to variations in riding position, aerodynamics requirements,
 * and terrain demands.
 */
enum class CyclingDiscipline {
    /**
     * Road cycling - balanced position for climbing and flat riding.
     * Moderate aerodynamics with comfort for long rides.
     */
    ROAD,

    /**
     * Endurance/Gran Fondo - comfort-oriented position.
     * More upright than road racing, optimized for all-day rides.
     */
    ENDURANCE,

    /**
     * Gravel/Adventure - similar to endurance but with more stability focus.
     * Slightly more upright for control on rough terrain.
     */
    GRAVEL,

    /**
     * Time Trial - aggressive aerodynamic position.
     * Lower torso angle, forward saddle position for power.
     */
    TT,

    /**
     * Triathlon - aerodynamic but sustainable for run afterwards.
     * Forward position to open hip angle for running.
     */
    TRI;

    /**
     * Returns a user-friendly display name.
     */
    fun displayName(): String = when (this) {
        ROAD -> "Road"
        ENDURANCE -> "Endurance"
        GRAVEL -> "Gravel"
        TT -> "Time Trial"
        TRI -> "Triathlon"
    }

    /**
     * Returns a brief description of the discipline's fit characteristics.
     */
    fun description(): String = when (this) {
        ROAD -> "Balanced position for racing and fast group rides"
        ENDURANCE -> "Comfort-oriented for long-distance riding"
        GRAVEL -> "Upright and stable for off-road terrain"
        TT -> "Aggressive aerodynamic position for speed"
        TRI -> "Forward position optimized for swim-bike-run"
    }
}
