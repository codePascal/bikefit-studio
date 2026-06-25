package bikefitstudio.desktop

import bikefitstudio.desktop.camera.JavaCvFrameSource
import bikefitstudio.desktop.pose.SidecarPoseProvider
import kotlin.system.exitProcess

/**
 * Headless end-to-end smoke test over a real video file: decode frames with JavaCV, run each
 * through the MediaPipe pose sidecar, and require that at least one pose is detected.
 *
 * Exit codes: 0 = poses detected, 1 = no poses, 2 = bad arguments. args: <videoPath> [pythonExe]
 * [scriptPath] [maxFrames]
 */
fun main(args: Array<String>) {
    val videoPath =
        args.getOrNull(0)?.takeIf { it.isNotBlank() }
            ?: run {
                System.err.println("[videoSmoke] missing video path")
                exitProcess(2)
            }
    val python = args.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "python"
    val script = args.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "pose_server.py"
    val maxFrames = args.getOrNull(3)?.toIntOrNull() ?: 300

    println("[videoSmoke] video=$videoPath python=$python script=$script maxFrames=$maxFrames")

    val source = JavaCvFrameSource.forVideoFile(videoPath)
    val provider = SidecarPoseProvider(scriptPath = script, pythonExe = python)
    var frames = 0
    var poses = 0
    try {
        while (frames < maxFrames) {
            val frame = source.nextFrame() ?: break
            frames++
            if (provider.detect(frame.image, frame.frameNumber, frame.timestampMs) != null) poses++
        }
    } finally {
        source.close()
        provider.close()
    }

    println("[videoSmoke] frames=$frames poses=$poses")
    if (poses == 0) {
        System.err.println("[videoSmoke] FAIL: no poses detected in $frames frames")
        exitProcess(1)
    }
    println("[videoSmoke] OK")
}
