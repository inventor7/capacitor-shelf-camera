import { Capacitor } from '@capacitor/core';

/**
 * Converts a native `file://...` URI to a WebView-safe URL.
 * On native platforms this uses Capacitor's convertFileSrc;
 * on web it returns the URI as-is.
 */
export function toWebSrc(fileUri: string | null | undefined): string {
    if (!fileUri) return '';
    if (fileUri.startsWith('file://')) {
        return Capacitor.convertFileSrc(fileUri);
    }
    return fileUri;
}
