package bikefitstudio.desktop

import bikefitstudio.desktop.pose.SidecarPoseProvider
import bikefitstudio.desktop.pose.StubPoseProvider
import bikefitstudio.pose.PoseProvider
import java.io.File

/**
 * Builds the pose backend used by the app: the real Python/MediaPipe sidecar when its script
 * and the project venv can be located, otherwise a no-op [StubPoseProvider] so the UI still runs.
 */
fun createPoseProvider(): PoseProvider {
    val script = findUpwards("pose_server.py") ?: return StubPoseProvider()
    val python = findUpwards(".venv/Scripts/python.exe")?.absolutePath ?: "python"
    return runCatching {
        SidecarPoseProvider(scriptPath = script.absolutePath, pythonExe = python)
    }.getOrElse { StubPoseProvider() }
}

/** Searches the working directory and up to four parents for [relativePath]. */
private fun findUpwards(relativePath: String): File? {
    var dir: File? = File(System.getProperty("user.dir"))
    repeat(5) {
        val current = dir ?: return null
        val candidate = File(current, relativePath)
        if (candidate.exists()) return candidate
        dir = current.parentFile
    }
    return null
}
