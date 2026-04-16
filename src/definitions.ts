export interface ShelfCameraPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
