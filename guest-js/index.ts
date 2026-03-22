import { invoke } from '@tauri-apps/api/core'

export async function ping(value: string): Promise<string | null> {
  return await invoke<{value?: string}>('plugin:music-notification|ping', {
    payload: {
      value,
    },
  }).then((r) => (r.value ? r.value : null));
}

export interface PlayOptions {
  url: string;
  title?: string;
  artist?: string;
  album?: string;
}

export interface PlaybackState {
  isPlaying: boolean;
  position: number;
  duration: number;
}

export async function play(options: PlayOptions): Promise<{ success: boolean; message?: string }> {
  return await invoke<{ success: boolean; message?: string }>('plugin:music-notification|play', {
    payload: options,
  });
}

export async function pause(): Promise<{ success: boolean }> {
  return await invoke<{ success: boolean }>('plugin:music-notification|pause');
}

export async function resume(): Promise<{ success: boolean }> {
  return await invoke<{ success: boolean }>('plugin:music-notification|resume');
}

export async function stop(): Promise<{ success: boolean }> {
  return await invoke<{ success: boolean }>('plugin:music-notification|stop');
}

export async function next(): Promise<{ success: boolean }> {
  return await invoke<{ success: boolean }>('plugin:music-notification|next');
}

export async function previous(): Promise<{ success: boolean }> {
  return await invoke<{ success: boolean }>('plugin:music-notification|previous');
}

export async function seek(position: number): Promise<{ success: boolean }> {
  return await invoke<{ success: boolean }>('plugin:music-notification|seek', {
    position,
  });
}

export async function getState(): Promise<PlaybackState> {
  return await invoke<PlaybackState>('plugin:music-notification|get_state');
}
