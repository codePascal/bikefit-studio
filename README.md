# BikeFit Studio

BikeFit Studio analyzes side-on cycling video — from a webcam, a USB phone, or a recorded
clip — using on-device pose estimation (MediaPipe, 33-landmark BlazePose) and rule-based
biomechanics. It detects pedal cycles, computes joint angles and knee-over-pedal-spindle
(KOPS), and produces graded fit recommendations (saddle height, fore/aft, reach, hip angle)
tuned to your riding context and bias.

It reuses the biomechanics, fit-analysis, and pose-processing core of
[BikefitApp](https://github.com/ineeve/BikefitApp) (Apache-2.0) and replaces the Android
camera/UI/MediaPipe layers with desktop equivalents. See [`NOTICE`](NOTICE) for attribution.

## Architecture

```
bikefit-studio/
├── core/      Pure-Kotlin/JVM — biomechanics, fit engine, pose data model + smoothers,
│              calibration. Reused verbatim from BikefitApp (952 unit tests).
├── desktop/   Compose for Desktop UI, JavaCV camera/video input, the pose backend client.
├── pose_server.py        MediaPipe PoseLandmarker sidecar (real model).
├── pose_server_fake.py   Dependency-free fake sidecar (for offline smoke tests).
└── models/pose_landmarker_lite.task   The 33-landmark BlazePose model (from BikefitApp).
```

The single seam between platform-agnostic logic and the platform is
`core/.../pose/PoseProvider.kt`: anything that returns a 33-landmark `PoseFrame` works with the
reused biomechanics. The desktop implementation (`SidecarPoseProvider`) pipes JPEG frames to a
Python MediaPipe process.

## Prerequisites

- JDK 17+ (a JDK 21 toolchain is fine).
- Python 3.11 or 3.12 (MediaPipe has no wheels for 3.13/3.14).
- For webcam/USB-phone input: a camera. An Android phone in UVC webcam mode (Android 14
  native, or DroidCam/Iriun) appears as a normal camera index.

## Setup

```powershell
# from bikefit-studio/
py -3.11 -m venv .venv
.venv\Scripts\python -m pip install -r requirements.txt
```

The app auto-discovers `.venv` and `pose_server.py`. If MediaPipe isn't available it falls back
to a no-op pose backend (preview still works, but no landmarks).

## Run

```powershell
./gradlew :desktop:run            # launch the GUI app
./gradlew :desktop:packageMsi     # build a Windows installer (needs jpackage)
```

For tests, formatting, static analysis, and the headless smoke checks, see
[CONTRIBUTING.md](CONTRIBUTING.md).

## Using the app

1. On the home screen pick a **riding context** (Road, TT/Triathlon, …) and **bias**
   (Comfort/Neutral/Performance).
2. **Calibrate from video…** — choose a side-on clip, then click the four reference points
   (saddle top, bottom bracket, pedal spindle at 3 o'clock, handlebar) and enter the crank
   length.
3. **Live analysis** — the video plays with the pose skeleton overlaid, live knee/hip/torso/
   ankle angles, the KOPS plumb line through the spindle, and KOPS at 3 o'clock.
4. **Finish & report** — runs the fit engine over the detected pedal cycles and shows a graded
   list of recommendations.

There are also webcam / video **preview** modes (skeleton + angles, no calibration) and a
**core self-test** that runs a KOPS calculation through the reused biomechanics.

## Recording tips

Side-on, stationary-trainer footage works best: the camera level with the bottom bracket,
the whole drive-side leg visible, several full pedal strokes, even lighting.

## License

Apache-2.0. Portions derived from BikefitApp (Renato Campos and contributors); see `LICENSE`
and `NOTICE`.
