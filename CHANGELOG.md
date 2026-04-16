# Changelog

## [1.0.0] — 2026-04-16

### Added

- **Camera Pipeline** — CameraX Preview + ImageAnalysis + ImageCapture with configurable resolution (720p / 1080p / 2k)
- **Coaching Signals** — Per-frame emission at 10 Hz: blur score, motion magnitude, tilt degrees, overlap %, luma mean, FPS
- **Keyframe Detection** — Automatic keyframe acceptance via `KeyframeDecider` (threshold + 300ms dwell + 400ms cooldown)
- **Overlap Analysis** — ORB feature matching + RANSAC homography for [0–100]% overlap measurement
- **Grid Inference** — Serpentine row/col tracking from cumulative frame translations
- **Incremental Stitching** — Sliding window (N=4) OpenCV Stitcher with preview image and `stitchProgress` events
- **Final Stitching** — Full-pass `commitPanorama` with seam score and output JPEG
- **Repair Capture** — `capturePhoto` for targeted single-frame replacement in the panorama grid
- **Device Tier** — Runtime classification (low / mid / high) based on CPU cores + RAM
- **Thermal Resilience** — Monitors battery temperature, charge level, and thermal status (API 29+); throttles to 5 Hz and disables incremental stitching when hot
- **Error Taxonomy** — Five structured error codes: `PERMISSION_DENIED`, `DEVICE_UNSUPPORTED`, `STITCH_FAILED`, `IO_ERROR`, `ABORTED`
- **TypeScript API** — Full typed interface with overloaded `addListener` for all 5 event types
- **Web Stub** — All methods throw `unimplemented` (Android-only plugin)
- **Example App** — 5-button demo with live signals dashboard and event log
- **CI Pipeline** — GitHub Actions: vitest (JS) + Robolectric (Android)

### Dependencies

- CameraX 1.3.4
- OpenCV 4.9.0 (Maven Central)
- Kotlin 1.9.25
- Capacitor Core ≥7.0.0 <9.0.0

### Platform Support

- ✅ Android (minSdk 26, arm64-v8a + armeabi-v7a)
- ❌ iOS (planned for v2)
- ❌ Web (stub only)
