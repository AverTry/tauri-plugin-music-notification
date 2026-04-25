'use strict';

var core = require('@tauri-apps/api/core');

async function onPlay(handler) {
    return await core.addPluginListener("music-notification", "onPlay", handler);
}
async function onPause(handler) {
    return await core.addPluginListener("music-notification", "onPause", handler);
}
async function onNext(handler) {
    return await core.addPluginListener("music-notification", "onNext", handler);
}
async function onPrev(handler) {
    return await core.addPluginListener("music-notification", "onPrev", handler);
}
async function ping(value) {
    return await core.invoke("plugin:music-notification|ping", {
        payload: {
            value,
        },
    }).then((r) => (r.value ? r.value : null));
}
async function play(options) {
    return await core.invoke("plugin:music-notification|play", {
        payload: options,
    });
}
async function pause() {
    return await core.invoke("plugin:music-notification|pause");
}
async function pauseAfter(delayMs) {
    return await core.invoke("plugin:music-notification|pause_after", {
        payload: {
            delayMs,
        },
    });
}
async function resume() {
    return await core.invoke("plugin:music-notification|resume");
}
async function stop() {
    return await core.invoke("plugin:music-notification|stop");
}
async function next() {
    return await core.invoke("plugin:music-notification|next");
}
async function previous() {
    return await core.invoke("plugin:music-notification|previous");
}
async function seek(position) {
    return await core.invoke("plugin:music-notification|seek", {
        position,
    });
}
async function seekAndPlay(position) {
    return await core.invoke("plugin:music-notification|seek_and_play", {
        position,
    });
}
async function getState() {
    return await core.invoke("plugin:music-notification|get_state");
}
async function setPlayingQueue(queue, playMode) {
    return await core.invoke("plugin:music-notification|set_playing_queue", {
        payload: {
            queue,
            playMode,
        },
    });
}
async function getPlaybackSession() {
    return await core.invoke("plugin:music-notification|get_playback_session");
}
async function clearPlayingQueue() {
    return await core.invoke("plugin:music-notification|clear_playing_queue");
}
async function setPlayMode(playMode) {
    return await core.invoke("plugin:music-notification|set_play_mode", {
        payload: {
            playMode,
        },
    });
}
async function startService() {
    return await core.invoke("plugin:music-notification|start_service");
}
async function stopService() {
    return await core.invoke("plugin:music-notification|stop_service");
}
async function setVolume(options) {
    return await core.invoke("plugin:music-notification|set_volume", {
        payload: options,
    });
}
async function setNormalizationConfig(options) {
    return await core.invoke("plugin:music-notification|set_normalization_config", {
        payload: options,
    });
}
async function setServer(libraryName) {
    return await core.invoke("plugin:music-notification|set_server", {
        libraryName,
    });
}

exports.clearPlayingQueue = clearPlayingQueue;
exports.getPlaybackSession = getPlaybackSession;
exports.getState = getState;
exports.next = next;
exports.onNext = onNext;
exports.onPause = onPause;
exports.onPlay = onPlay;
exports.onPrev = onPrev;
exports.pause = pause;
exports.pauseAfter = pauseAfter;
exports.ping = ping;
exports.play = play;
exports.previous = previous;
exports.resume = resume;
exports.seek = seek;
exports.seekAndPlay = seekAndPlay;
exports.setNormalizationConfig = setNormalizationConfig;
exports.setPlayMode = setPlayMode;
exports.setPlayingQueue = setPlayingQueue;
exports.setServer = setServer;
exports.setVolume = setVolume;
exports.startService = startService;
exports.stop = stop;
exports.stopService = stopService;
