import { PluginListener } from "@tauri-apps/api/core";
export interface PlaybackEvent {
    action: "play" | "pause" | "next" | "prev";
    currentIndex: number;
    isPlaying: boolean;
    trackId?: number;
}
export declare function onPlay(handler: (data: PlaybackEvent) => void): Promise<PluginListener>;
export declare function onPause(handler: (data: PlaybackEvent) => void): Promise<PluginListener>;
export declare function onNext(handler: (data: PlaybackEvent) => void): Promise<PluginListener>;
export declare function onPrev(handler: (data: PlaybackEvent) => void): Promise<PluginListener>;
export declare function ping(value: string): Promise<string | null>;
export interface PlayOptions {
    url: string;
    title?: string;
    artist?: string;
    album?: string;
    coverUrl?: string;
}
export interface PlaybackState {
    isPlaying: boolean;
    position: number;
    duration: number;
}
export type PlayMode = "sequential" | "shuffle" | "loop";
export interface QueueSong {
    id: number;
    name: string;
    path: string;
    url: string;
    lufs: number | null;
    coverUrl?: string | null;
}
export interface PlayingQueue {
    songs: QueueSong[];
    currentIndex: number | null;
}
export interface PlaybackRuntime {
    isPlaying: boolean;
    positionMs: number;
    durationMs: number;
}
export interface PlaybackSession {
    queue: PlayingQueue;
    runtime: PlaybackRuntime;
    playMode: PlayMode;
    currentSongId: number | null;
}
export declare function play(options: PlayOptions): Promise<{
    success: boolean;
    message?: string;
}>;
export declare function pause(): Promise<{
    success: boolean;
}>;
export declare function pauseAfter(delayMs: number): Promise<{
    success: boolean;
}>;
export declare function resume(): Promise<{
    success: boolean;
}>;
export declare function stop(): Promise<{
    success: boolean;
}>;
export declare function next(): Promise<{
    success: boolean;
}>;
export declare function previous(): Promise<{
    success: boolean;
}>;
export declare function seek(position: number): Promise<{
    success: boolean;
}>;
export declare function seekAndPlay(position: number): Promise<{
    success: boolean;
}>;
export declare function getState(): Promise<PlaybackState>;
export declare function setPlayingQueue(queue: PlayingQueue, playMode: PlayMode): Promise<{
    success: boolean;
    message?: string;
}>;
export declare function getPlaybackSession(): Promise<PlaybackSession>;
export declare function clearPlayingQueue(): Promise<{
    success: boolean;
    message?: string;
}>;
export declare function setPlayMode(playMode: PlayMode): Promise<{
    success: boolean;
    message?: string;
}>;
export declare function startService(): Promise<{
    success: boolean;
    message?: string;
}>;
export declare function stopService(): Promise<{
    success: boolean;
    message?: string;
}>;
export interface SetVolumeOptions {
    volume: number;
}
export declare function setVolume(options: SetVolumeOptions): Promise<{
    success: boolean;
    message?: string;
}>;
export type NormalizationMode = "auto" | "manual" | "fixed";
export interface SetNormalizationConfigOptions {
    mode: NormalizationMode;
    manualVolume: number;
    fixedLufs: number;
}
export declare function setNormalizationConfig(options: SetNormalizationConfigOptions): Promise<{
    success: boolean;
    message?: string;
}>;
export declare function setServer(libraryName: string): Promise<{
    success: boolean;
    message?: string;
}>;
