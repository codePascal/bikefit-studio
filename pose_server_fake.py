"""
Dependency-free fake pose sidecar (stdlib only) for smoke-testing the IPC + core pipeline
without MediaPipe. Returns a fixed, plausible side-view pose for every frame:
knee forward of the spindle, so KOPS should report FORWARD.

Same protocol as pose_server.py.
"""
import struct
import sys

LANDMARKS = 33


def make_pose_line():
    pts = [(0.5, 0.5)] * LANDMARKS
    pts[23] = (0.30, 0.30)  # LEFT_HIP
    pts[25] = (0.70, 0.50)  # LEFT_KNEE
    pts[31] = (0.60, 0.60)  # LEFT_FOOT_INDEX
    vals = []
    for (x, y) in pts:
        vals.extend((x, y, 0.0, 1.0, 1.0))
    return " ".join("%.6f" % v for v in vals)


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
    line = make_pose_line()
    while True:
        header = read_exact(stdin, 4)
        if header is None:
            break
        (length,) = struct.unpack(">I", header)
        if length == 0:
            break
        if read_exact(stdin, length) is None:
            break
        sys.stdout.write(line + "\n")
        sys.stdout.flush()


if __name__ == "__main__":
    main()
