package bikefitstudio.util

/**
 * Minimal logger for the biomechanics core. Routes to stderr with an Android-style
 * "LEVEL/Tag: message" prefix; swap the [log] body for a logging framework if desired.
 */
object Logger {
    fun v(tag: String, message: String) = log("V", tag, message)
    fun d(tag: String, message: String) = log("D", tag, message)
    fun i(tag: String, message: String) = log("I", tag, message)
    fun w(tag: String, message: String) = log("W", tag, message)
    fun e(tag: String, message: String) = log("E", tag, message)

    private fun log(level: String, tag: String, message: String) {
        System.err.println("$level/$tag: $message")
    }
}
