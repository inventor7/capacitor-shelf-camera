import { WebPlugin } from '@capacitor/core';

import type { ShelfCameraPlugin } from './definitions';

export class ShelfCameraWeb extends WebPlugin implements ShelfCameraPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
