import { onUnmounted, ref, shallowRef } from 'vue';
import { ShelfCamera } from 'capacitor-shelf-camera';
import type { CoachingSignals, ShelfCameraError } from 'capacitor-shelf-camera';
import type { PluginListenerHandle } from '@capacitor/core';

export type CommitResult =
    | { success: true }
    | { success: false; code: ShelfCameraError['code'] | 'UNKNOWN'; message: string };

export function useShelfCamera() {
    const isCameraActive = ref(false);
    const latestFrame = shallowRef<CoachingSignals | null>(null);
    const acceptedKeyframes = ref<string[]>([]);
    const seamScore = ref(0);
    const stitchProgress = ref(0);
    const stitchPreviewUri = ref<string | null>(null);
    const panoramaUri = ref<string | null>(null);
    const lastError = shallowRef<ShelfCameraError | null>(null);

    let listeners: PluginListenerHandle[] = [];
    let currentSessionId: string | null = null;

    const registerListeners = async () => {
        listeners.push(
            await ShelfCamera.addListener('frame', (signals) => {
                latestFrame.value = signals;
            })
        );
        listeners.push(
            await ShelfCamera.addListener('keyframeAccepted', (e) => {
                acceptedKeyframes.value.push(e.thumbnailUri);
            })
        );
        listeners.push(
            await ShelfCamera.addListener('stitchProgress', (e) => {
                stitchProgress.value = (e.completedCells / Math.max(1, e.totalCells));
                stitchPreviewUri.value = e.previewUri;
            })
        );
        listeners.push(
            await ShelfCamera.addListener('panoramaReady', (e) => {
                panoramaUri.value = e.uri;
                seamScore.value = e.seamScore;
            })
        );
        listeners.push(
            await ShelfCamera.addListener('error', (e) => {
                lastError.value = e;
                // eslint-disable-next-line no-console
                console.error('[ShelfCamera] native error', e);
            })
        );
    };

    const startSweep = async () => {
        try {
            // Register listeners BEFORE start() so we don't miss the first frame event.
            await registerListeners();
            await ShelfCamera.start({ resolution: '1080p', coaching: true });
            isCameraActive.value = true;
            currentSessionId = `session-${Date.now()}`;
            acceptedKeyframes.value = [];
            panoramaUri.value = null;
            stitchPreviewUri.value = null;
            stitchProgress.value = 0;
            lastError.value = null;
            await ShelfCamera.beginPanorama({
                mode: 'sweep',
                sessionId: currentSessionId,
                keyframeThresholds: {
                    // Balanced thresholds: relaxed enough for handheld grab,
                    // but strict enough that OpenCV stitcher won't fail (STITCH_FAILED)
                    minBlur: 0.35,
                    maxMotion: 0.35,
                    maxTiltDeg: 20,
                    minOverlapPct: 20,
                },
            });
        } catch (e) {
            console.error('Failed to start sweep', e);
        }
    };

    const stop = async () => {
        try {
            await ShelfCamera.removeAllListeners();
            listeners = [];
            await ShelfCamera.stop();
            isCameraActive.value = false;
            currentSessionId = null;
        } catch (e) {
            console.error('Failed to stop camera', e);
        }
    };

    const commit = async (): Promise<CommitResult> => {
        if (!currentSessionId) {
            return { success: false, code: 'ABORTED', message: 'No active session' };
        }
        if (acceptedKeyframes.value.length === 0) {
            return {
                success: false,
                code: 'NO_KEYFRAMES',
                message:
                    'No keyframes captured yet. Keep panning the shelf slowly until frames appear below, then try again.',
            };
        }
        try {
            await ShelfCamera.commitPanorama({ sessionId: currentSessionId });
            return { success: true };
        } catch (e: any) {
            const code = (e?.code as ShelfCameraError['code']) || 'UNKNOWN';
            const message = e?.message || String(e);
            lastError.value = { code: code as any, message };
            return { success: false, code, message };
        }
    };

    const cancel = async () => {
        if (!currentSessionId) return;
        try {
            await ShelfCamera.cancelPanorama({ sessionId: currentSessionId });
        } catch (e) {
            console.error('Failed to cancel panorama', e);
        }
    };

    onUnmounted(() => {
        stop();
    });

    return {
        isCameraActive,
        latestFrame,
        acceptedKeyframes,
        stitchProgress,
        stitchPreviewUri,
        panoramaUri,
        seamScore,
        lastError,
        startSweep,
        commit,
        cancel,
        stop,
    };
}
