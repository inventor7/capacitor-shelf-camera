import type { PluginListenerHandle } from '@capacitor/core';

export type CoachingSignals = {
  blurScore: number;
  motionMagnitude: number;
  tiltDeg: number;
  overlapPct: number;
  lumaMean: number;
  fps: number;
  timestamp: number;
  /**
   * When a panorama session is active and the most recent frame was NOT
   * accepted as a keyframe, this describes why (e.g. "blur too low",
   * "tilt too high", "waiting for stillness", "dwell (150ms / 300ms)").
   * Undefined when a keyframe was just accepted or no session is active.
   */
  rejectionReason?: string;
};

export type KeyframeCell = { row: number; col: number };

export type KeyframeAcceptedEvent = {
  frameId: string;
  thumbnailUri: string;
  fullUri: string;
  gridCell: KeyframeCell;
  qualityScore: number;
  signals: CoachingSignals;
};

export type StitchProgressEvent = {
  sessionId: string;
  completedCells: number;
  totalCells: number;
  previewUri: string | null;
  seamScore: number;
};

export type PanoramaReadyEvent = {
  sessionId: string;
  uri: string;
  width: number;
  height: number;
  gridRows: number;
  gridCols: number;
  durationMs: number;
  seamScore: number;
};

export type VideoProgressEvent = {
  sessionId: string;
  /** Frames decoded from the video so far */
  extractedFrames: number;
  /** Frames accepted by KeyframeDecider */
  acceptedFrames: number;
  /** Current processing phase */
  phase: 'recording' | 'extracting' | 'stitching';
};

export type ShelfCameraError = {
  code:
    | 'PERMISSION_DENIED'
    | 'DEVICE_UNSUPPORTED'
    | 'STITCH_FAILED'
    | 'NO_KEYFRAMES'
    | 'INSUFFICIENT_KEYFRAMES'
    | 'IO_ERROR'
    | 'ABORTED';
  message: string;
};

export interface ShelfCameraPlugin {
  start(options: {
    resolution?: '720p' | '1080p' | '2k';
    coaching?: boolean;
    /** Emits full frame signals at 30fps when true. */
    diagnostic?: boolean;
  }): Promise<void>;

  stop(): Promise<void>;

  setPreviewVisible(opts: { visible: boolean }): Promise<void>;

  beginPanorama(opts: {
    sessionId: string;
    /**
     * - `'sweep'`      — automatic keyframe acceptance via KeyframeDecider thresholds.
     * - `'manual'`     — automatic acceptance is disabled; call `capturePhoto()` to add each frame.
     * - `'singleShot'` — legacy single-frame mode.
     */
    mode: 'sweep' | 'singleShot' | 'manual';
    expectedCells?: number;
    keyframeThresholds?: {
      minBlur?: number;
      maxMotion?: number;
      maxTiltDeg?: number;
      minOverlapPct?: number;
    };
  }): Promise<void>;

  capturePhoto(opts: {
    sessionId: string;
    targetCell?: KeyframeCell;
  }): Promise<{ frameId: string; fullUri: string; thumbnailUri: string }>;

  commitPanorama(opts: { sessionId: string }): Promise<PanoramaReadyEvent>;

  cancelPanorama(opts: { sessionId: string }): Promise<void>;

  pausePanorama(opts: { sessionId: string }): Promise<void>;

  resumePanorama(opts: { sessionId: string }): Promise<void>;

  /** Video Mode: begin recording a short clip (max 10 s by default). */
  startVideoCapture(opts: {
    sessionId: string;
    maxDurationMs?: number;
  }): Promise<void>;

  /** Video Mode: stop the recording early. Resolves with the local file URI. */
  stopVideoCapture(opts: { sessionId: string }): Promise<{ videoUri: string }>;

  /**
   * Video Mode: decode frames from a recorded video file, run each through
   * KeyframeDecider, emit `keyframeAccepted` for every passing frame, and
   * trigger final stitching. Emits `videoProgress` during processing.
   */
  processVideo(opts: {
    sessionId: string;
    videoUri: string;
    keyframeThresholds?: {
      minBlur?: number;
      maxMotion?: number;
      maxTiltDeg?: number;
      minOverlapPct?: number;
    };
  }): Promise<void>;

  getDeviceTier(): Promise<{ tier: 'low' | 'mid' | 'high' }>;

  addListener(
    eventName: 'frame',
    handler: (s: CoachingSignals) => void,
  ): Promise<PluginListenerHandle>;

  addListener(
    eventName: 'keyframeAccepted',
    handler: (e: KeyframeAcceptedEvent) => void,
  ): Promise<PluginListenerHandle>;

  addListener(
    eventName: 'stitchProgress',
    handler: (e: StitchProgressEvent) => void,
  ): Promise<PluginListenerHandle>;

  addListener(
    eventName: 'panoramaReady',
    handler: (e: PanoramaReadyEvent) => void,
  ): Promise<PluginListenerHandle>;

  addListener(
    eventName: 'videoProgress',
    handler: (e: VideoProgressEvent) => void,
  ): Promise<PluginListenerHandle>;

  addListener(
    eventName: 'error',
    handler: (e: ShelfCameraError) => void,
  ): Promise<PluginListenerHandle>;

  removeAllListeners(): Promise<void>;
}
