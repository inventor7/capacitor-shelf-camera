import { describe, it, expect } from 'vitest';
import { ShelfCameraWeb } from '../src/web';

describe('ShelfCameraWeb stub', () => {
  const plugin = new ShelfCameraWeb();

  it('throws unimplemented on start()', async () => {
    await expect(plugin.start({})).rejects.toThrow(/unimplemented/i);
  });

  it('throws unimplemented on stop()', async () => {
    await expect(plugin.stop()).rejects.toThrow(/unimplemented/i);
  });

  it('throws unimplemented on beginPanorama()', async () => {
    await expect(
      plugin.beginPanorama({ sessionId: 'test', mode: 'sweep' }),
    ).rejects.toThrow(/unimplemented/i);
  });

  it('throws unimplemented on commitPanorama()', async () => {
    await expect(plugin.commitPanorama({ sessionId: 'test' })).rejects.toThrow(
      /unimplemented/i,
    );
  });

  it('throws unimplemented on getDeviceTier()', async () => {
    await expect(plugin.getDeviceTier()).rejects.toThrow(/unimplemented/i);
  });
});
