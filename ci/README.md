# CI assets

Drop a short side-on cycling clip here named **`sample.mp4`** to enable the smoke-test job in
[`.github/workflows/ci.yml`](../.github/workflows/ci.yml).

The smoke job decodes the video with JavaCV, runs each frame through the MediaPipe pose
sidecar, and fails if no pose is detected. Until `ci/sample.mp4` exists, the job logs a warning
and passes (skips).

A few seconds at 30 fps with a visible rider is plenty. Keep it small (a few MB) so the repo
stays lean.
