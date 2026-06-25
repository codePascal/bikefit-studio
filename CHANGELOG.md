# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased] - dd.mm.yyyy

### Added

- Desktop bike-fit application (Kotlin + Compose for Desktop).
- Pose backend via a Python MediaPipe sidecar (`PoseLandmarker`, 33-landmark BlazePose),
  reusing the same model as the upstream Android app.
- Camera/video input through JavaCV: webcam, USB phone (UVC), and recorded video files.
- Click-to-mark calibration (saddle, bottom bracket, pedal spindle, handlebar + crank length).
- Live analysis: pose-skeleton overlay, knee/hip/torso/ankle angles, KOPS plumb line, and
  per-frame knee-over-pedal-spindle at the 3 o'clock crank position.
- Full fit report: pedal-cycle detection and aggregation feeding the reused `FitEngine`,
  producing graded saddle-height / fore-aft / reach / hip-angle recommendations, tuned by
  riding context and bias.
- Headless smoke runners: `fitSmoke` (synthetic pedaling) and `videoSmoke` (real pose
  pipeline over a video).
- Tooling: ktfmt formatting (Spotless), detekt static analysis, GitHub Actions CI
  (format, unit tests, static analysis, smoke), issue/PR templates, and VS Code workspace
  config.

### Notes

- The `core/` module (biomechanics, fit engine, pose data model, calibration) is reused from
  [BikefitApp](https://github.com/ineeve/BikefitApp) under Apache-2.0; see [`NOTICE`](NOTICE).

[Unreleased]: https://github.com/<owner>/bikefit-studio/commits/main
