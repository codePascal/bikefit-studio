package bikefitstudio.desktop

import bikefitstudio.biomechanics.BodySide
import bikefitstudio.biomechanics.KneeOverPedalOffset
import bikefitstudio.calibration.BikeCalibration
import bikefitstudio.calibration.BikeReferencePoint
import bikefitstudio.calibration.BikeReferencePointType
import bikefitstudio.desktop.pose.SidecarPoseProvider
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Headless end-to-end check of the pose sidecar: spawns the Python server, sends one frame, and (if
 * a pose is returned) runs it through the reused KOPS biomechanics. No GUI required.
 *
 * args: [scriptPath] [pythonExe] [imagePath] If imagePath is given it is sent to the server;
 * otherwise a blank frame is used.
 */
fun main(args: Array<String>) {
    val script = args.getOrNull(0) ?: "pose_server_fake.py"
    val python = args.getOrNull(1) ?: "python"
    val imagePath = args.getOrNull(2)
    println("[smoke] python='$python'")
    println("[smoke] script='$script'")

    val image: BufferedImage =
        imagePath
            ?.let { ImageIO.read(File(it)) ?: error("could not read image: $it") }
            ?.also { println("[smoke] image='$imagePath' (${it.width}x${it.height})") }
            ?: BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB).also {
                println("[smoke] image=blank 640x480")
            }

    val provider = SidecarPoseProvider(scriptPath = script, pythonExe = python)
    try {
        val frame = provider.detect(image, frameNumber = 0L, timestampMs = 0L)
        if (frame == null) {
            println("[smoke] detect -> no pose (NONE)")
        } else {
            println(
                "[smoke] detect -> ${frame.landmarks.size} landmarks, confidence=${"%.3f".format(frame.confidence)}"
            )
            val calibration =
                BikeCalibration(
                    saddleTop = BikeReferencePoint(BikeReferencePointType.SADDLE_TOP, 0.5f, 0.2f),
                    bottomBracket =
                        BikeReferencePoint(BikeReferencePointType.BOTTOM_BRACKET, 0.5f, 0.6f),
                    handlebar = BikeReferencePoint(BikeReferencePointType.HANDLEBAR, 0.5f, 0.1f),
                    crankLengthMm = 172
                )
            val kops =
                KneeOverPedalOffset.computeAtFrame(
                    frame,
                    BodySide.LEFT,
                    calibration,
                    crankScale = 0.1f
                )
            println(
                "[smoke] KOPS -> valid=${kops.isValid} alignment=${kops.alignment} " +
                    "normalizedOffset=${"%.3f".format(kops.normalizedOffset)} spindleX=${kops.spindleX}"
            )
        }
    } finally {
        provider.close()
    }
    println("[smoke] done.")
}
