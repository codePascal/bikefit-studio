"""
Pose sidecar for BikeFit Studio.

Reads JPEG frames from stdin and writes 33-landmark BlazePose results to stdout, using the
MediaPipe Tasks PoseLandmarker with the same `pose_landmarker_lite.task` model the Android
app ships -- so the reused biomechanics behave identically.

Protocol (one exchange per frame):
    in  : 4-byte big-endian length N, then N bytes of JPEG. N == 0 means "stop".
    out : one line -- "NONE", or 33*5 space-separated floats
          (x y z visibility presence, per landmark).

Run via the project venv:
    .venv/Scripts/python.exe pose_server.py
"""
import os
import struct
import sys

import cv2
import mediapipe as mp
import numpy as np
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision

MODEL_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "models", "pose_landmarker_lite.task")


def read_exact(stream, n):
    buf = bytearray()
    while len(buf) < n:
        chunk = stream.read(n - len(buf))
        if not chunk:
            return None
        buf.extend(chunk)
    return bytes(buf)


def main():
    stdin = sys.stdin.buffer
    out = sys.stdout

    options = vision.PoseLandmarkerOptions(
        base_options=mp_python.BaseOptions(model_asset_path=MODEL_PATH),
        running_mode=vision.RunningMode.VIDEO,
        num_poses=1,
    )
    landmarker = vision.PoseLandmarker.create_from_options(options)
    timestamp_ms = 0

    with landmarker:
        while True:
            header = read_exact(stdin, 4)
            if header is None:
                break
            (length,) = struct.unpack(">I", header)
            if length == 0:
                break
            data = read_exact(stdin, length)
            if data is None:
                break

            arr = np.frombuffer(data, dtype=np.uint8)
            bgr = cv2.imdecode(arr, cv2.IMREAD_COLOR)
            if bgr is None:
                out.write("NONE\n")
                out.flush()
                continue

            rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)
            mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
            result = landmarker.detect_for_video(mp_image, timestamp_ms)
            timestamp_ms += 33  # monotonically increasing as VIDEO mode requires

            poses = result.pose_landmarks
            if not poses:
                out.write("NONE\n")
                out.flush()
                continue

            vals = []
            for lm in poses[0]:
                vals.extend((lm.x, lm.y, lm.z, lm.visibility, lm.presence))
            out.write(" ".join("%.6f" % v for v in vals))
            out.write("\n")
            out.flush()


if __name__ == "__main__":
    main()
