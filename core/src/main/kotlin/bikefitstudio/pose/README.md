# Pose Module

## Overview
The `pose` module encapsulates the MediaPipe Vision Tasks API. It takes raw image frames and returns structured `PoseResult` objects containing landmarks.

## Key Components

### `PoseLandmarkerWrapper`
- Configures the MediaPipe `PoseLandmarker`.
- Loads the model asset (`pose_landmarker_lite.task`).
- Runs inference on provided images.

### `LandmarkSmoother`
- Raw ML output can be jittery. This component applies exponential moving average (EMA) smoothing to stabilize landmark coordinates over time.

### `OneEuroFilter` & `OneEuroLandmarkSmoother`
- Advanced adaptive smoothing based on Casiez et al. 2012.
- Automatically adjusts smoothing strength based on movement velocity.
- Applied to key bike fit landmarks: hip, knee, ankle, heel, and toe.
- Reduces jitter during slow movements while maintaining responsiveness during fast movements.
- **Integrated into processing pipeline**: Applied after pose estimation in both live camera preview and video analysis.

### `PoseValidator`
- Checks if necessary landmarks (e.g., hip, knee, ankle, foot) are visible in the frame.
- Filters out low-confidence detections to prevent bad data from entering the biomechanics engine.
