import { registerPlugin } from '@capacitor/core';

import type { ShelfCameraPlugin } from './definitions';

const ShelfCamera = registerPlugin<ShelfCameraPlugin>('ShelfCamera', {
  web: () => import('./web').then((m) => new m.ShelfCameraWeb()),
});

export * from './definitions';
export { ShelfCamera };
