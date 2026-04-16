# capacitor-shelf-camera

Coached panoramic shelf capture Capacitor plugin.  
Provides live camera preview, per-frame coaching signals (blur, tilt, motion, overlap, exposure), incremental OpenCV stitching, and targeted single-frame repair capture.

**Android-only for v1.** iOS API stubs exist for v2.

## Install

```bash
pnpm add capacitor-shelf-camera
npx cap sync android
```

## Android Setup

Add the camera permission to your app's `AndroidManifest.xml` (the plugin declares it, but your app must also):

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

The plugin will request runtime camera permission automatically on `start()`.

**Minimum requirements:**
- `minSdkVersion 26`
- `targetSdkVersion 34`
- `compileSdkVersion 34`

## Quickstart

```ts
import { ShelfCamera } from 'capacitor-shelf-camera';

// 1. Start the camera
await ShelfCamera.start({ resolution: '1080p', coaching: true });

// 2. Listen for coaching signals
const frameHandle = await ShelfCamera.addListener('frame', (signals) => {
  console.log('Blur:', signals.blurScore, 'Motion:', signals.motionMagnitude);
});

// 3. Begin a panorama sweep
await ShelfCamera.beginPanorama({
  sessionId: 'session-001',
  mode: 'sweep',
  keyframeThresholds: {
    minBlur: 0.55,
    maxMotion: 0.35,
    maxTiltDeg: 8,
    minOverlapPct: 25,
  },
});

// 4. Listen for accepted keyframes
const kfHandle = await ShelfCamera.addListener('keyframeAccepted', (e) => {
  console.log('Keyframe accepted:', e.frameId, 'Cell:', e.gridCell);
});

// 5. Listen for stitch progress
const spHandle = await ShelfCamera.addListener('stitchProgress', (e) => {
  console.log('Stitch:', e.completedCells, '/', e.totalCells);
});

// 6. Commit the panorama when done sweeping
const result = await ShelfCamera.commitPanorama({ sessionId: 'session-001' });
console.log('Panorama ready:', result.uri, result.width, 'x', result.height);

// 7. Or do a repair capture for a bad cell
const repair = await ShelfCamera.capturePhoto({
  sessionId: 'session-001',
  targetCell: { row: 0, col: 2 },
});

// 8. Stop the camera
await ShelfCamera.stop();
```

## API Reference

### `start(options)`

Start the camera preview and frame analysis pipeline.

| Param | Type | Default | Description |
|---|---|---|---|
| `resolution` | `'720p' \| '1080p' \| '2k'` | `'1080p'` | Target capture resolution |
| `coaching` | `boolean` | `true` | Enable coaching signal emission |
| `diagnostic` | `boolean` | `false` | Emit full diagnostic signals at 30fps |

### `stop()`

Stop the camera and release all resources.

### `setPreviewVisible(opts)`

Show or hide the native camera preview.

| Param | Type | Description |
|---|---|---|
| `visible` | `boolean` | Whether the preview should be visible |

### `beginPanorama(opts)`

Begin a new panorama capture session.

| Param | Type | Required | Description |
|---|---|---|---|
| `sessionId` | `string` | ✅ | Unique session identifier |
| `mode` | `'sweep' \| 'singleShot'` | ✅ | Capture mode |
| `expectedCells` | `number` | ❌ | Hint for grid size |
| `keyframeThresholds` | `object` | ❌ | Override default thresholds |

### `capturePhoto(opts)`

Capture a single photo (for repair frames).

| Param | Type | Required | Description |
|---|---|---|---|
| `sessionId` | `string` | ✅ | Active session ID |
| `targetCell` | `{ row, col }` | ❌ | Cell to replace in the panorama |

**Returns:** `{ frameId, fullUri, thumbnailUri }`

### `commitPanorama(opts)`

Run final stitching and produce the panorama image.

| Param | Type | Required |
|---|---|---|
| `sessionId` | `string` | ✅ |

**Returns:** `PanoramaReadyEvent`

### `cancelPanorama(opts)`

Cancel the active panorama and delete all captured frames.

### `getDeviceTier()`

Detect device performance tier.

**Returns:** `{ tier: 'low' | 'mid' | 'high' }`

## Events

| Event | Payload | Frequency |
|---|---|---|
| `frame` | `CoachingSignals` | 10 Hz |
| `keyframeAccepted` | `KeyframeAcceptedEvent` | Per keyframe |
| `stitchProgress` | `StitchProgressEvent` | Per keyframe |
| `panoramaReady` | `PanoramaReadyEvent` | Once at commit |
| `error` | `ShelfCameraError` | On error |

## Error Codes

| Code | Description |
|---|---|
| `PERMISSION_DENIED` | Camera permission not granted |
| `DEVICE_UNSUPPORTED` | OpenCV failed to initialize |
| `STITCH_FAILED` | Panorama stitching failed |
| `IO_ERROR` | File I/O error |
| `ABORTED` | Operation was cancelled |

## Architecture

- **CameraX** Preview + ImageAnalysis + ImageCapture
- **FrameAnalyzer** orchestrates per-frame analysis at 10 Hz
- **BlurAnalyzer** — Laplacian variance → [0,1] sharpness score
- **MotionAnalyzer** — Farneback optical flow → [0,1] motion score
- **OverlapAnalyzer** — ORB + RANSAC → [0,100]% overlap
- **TiltSensor** — ROTATION_VECTOR → degrees from upright
- **KeyframeDecider** — threshold + 300ms dwell + 400ms cooldown
- **KeyframeStore** — JPEG persistence with thumbnails
- **GridInferrer** — serpentine row/col tracking
- **StitchEngine** — OpenCV Stitcher with incremental (N=4 window) and full-pass modes
- **DeviceTier** — adaptive quality based on cores + RAM

## Known Limitations

- **Android only** — iOS implementation planned for v2
- **OpenCV 4.9** adds ~15MB to APK (arm64 + armeabi-v7a)
- No audio feedback (deliberately silent)
- Back camera only
- No video recording (frames only)

## License

MIT
