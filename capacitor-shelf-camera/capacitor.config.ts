import type { CapacitorConfig } from '@capacitor/cli'

const config: CapacitorConfig = {
  appId: 'io.shelf.scanner.aybinv',
  appName: 'capacitor-shelf-camera',
  webDir: 'dist',

  server: {
    url: 'http://localhost:5174',
    cleartext: true,
  },
}

export default config
