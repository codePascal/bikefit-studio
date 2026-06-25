package bikefitstudio.desktop.pose

import bikefitstudio.pose.PoseFrame
import bikefitstudio.pose.PoseProvider
import java.awt.image.BufferedImage

/**
 * Placeholder pose backend that detects nothing.
 *
 * Replace with the real backend per the porting guide §5 — either an ONNX BlazePose model (in-JVM)
 * or a Python MediaPipe sidecar — both returning a 33-landmark [PoseFrame].
 */
class StubPoseProvider : PoseProvider {
    override fun detect(image: BufferedImage, frameNumber: Long, timestampMs: Long): PoseFrame? =
        null
}
