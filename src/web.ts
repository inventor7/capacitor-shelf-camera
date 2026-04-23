import { WebPlugin } from '@capacitor/core';

import type {
  PanoramaReadyEvent,
  PreviewFrame,
  ShelfCameraPlugin,
} from './definitions';

export class ShelfCameraWeb extends WebPlugin implements ShelfCameraPlugin {
  async start(_options: {
    resolution?: '720p' | '1080p' | '2k';
    coaching?: boolean;
  }): Promise<void> {
    throw this.unimplemented('ShelfCamera.start is not supported on web.');
  }

  async stop(): Promise<void> {
    throw this.unimplemented('ShelfCamera.stop is not supported on web.');
  }

  async setPreviewVisible(_opts: { visible: boolean }): Promise<void> {
    throw this.unimplemented('ShelfCamera.setPreviewVisible is not supported on web.');
  }

  async setPreviewFrame(_opts: PreviewFrame): Promise<void> {
    throw this.unimplemented('ShelfCamera.setPreviewFrame is not supported on web.');
  }

  async beginPanorama(_opts: {
    sessionId: string;
    mode?: 'manual';
    expectedCells?: number;
  }): Promise<void> {
    throw this.unimplemented('ShelfCamera.beginPanorama is not supported on web.');
  }

  async capturePhoto(_opts: {
    sessionId: string;
  }): Promise<{ frameId: string; fullUri: string; thumbnailUri: string }> {
    throw this.unimplemented('ShelfCamera.capturePhoto is not supported on web.');
  }

  async commitPanorama(_opts: {
    sessionId: string;
  }): Promise<PanoramaReadyEvent> {
    throw this.unimplemented('ShelfCamera.commitPanorama is not supported on web.');
  }

  async cancelPanorama(_opts: { sessionId: string }): Promise<void> {
    throw this.unimplemented('ShelfCamera.cancelPanorama is not supported on web.');
  }

  async pausePanorama(_opts: { sessionId: string }): Promise<void> {
    throw this.unimplemented('ShelfCamera.pausePanorama is not supported on web.');
  }

  async resumePanorama(_opts: { sessionId: string }): Promise<void> {
    throw this.unimplemented('ShelfCamera.resumePanorama is not supported on web.');
  }

  async getDeviceTier(): Promise<{ tier: 'low' | 'mid' | 'high' }> {
    throw this.unimplemented('ShelfCamera.getDeviceTier is not supported on web.');
  }

  // All addListener overloads throw — cast through never to satisfy the overload set.
  addListener(..._args: never[]): never {
    throw this.unimplemented('ShelfCamera events are not supported on web.');
  }
}
