import { play, pause, resume, stop, next, previous, seek, getState, startServer, type PlaybackState } from "music-notification-api";

// UI Elements
const urlInput = document.querySelector("#urlInput") as HTMLInputElement;
const titleInput = document.querySelector("#titleInput") as HTMLInputElement;
const artistInput = document.querySelector("#artistInput") as HTMLInputElement;
const albumInput = document.querySelector("#albumInput") as HTMLInputElement;

const playBtn = document.querySelector("#playBtn") as HTMLButtonElement;
const pauseBtn = document.querySelector("#pauseBtn") as HTMLButtonElement;
const resumeBtn = document.querySelector("#resumeBtn") as HTMLButtonElement;
const stopBtn = document.querySelector("#stopBtn") as HTMLButtonElement;
const prevBtn = document.querySelector("#prevBtn") as HTMLButtonElement;
const nextBtn = document.querySelector("#nextBtn") as HTMLButtonElement;
const seekBtn = document.querySelector("#seekBtn") as HTMLButtonElement;
const stateBtn = document.querySelector("#stateBtn") as HTMLButtonElement;
const startServerBtn = document.querySelector("#startServerBtn") as HTMLButtonElement;

const isPlayingEl = document.querySelector("#isPlaying") as HTMLElement;
const positionEl = document.querySelector("#position") as HTMLElement;
const durationEl = document.querySelector("#duration") as HTMLElement;
const logOutput = document.querySelector("#logOutput") as HTMLElement;

// Default values
const DEFAULT_URL = "http://localhost:2080/api/music/id/42";
const DEFAULT_TITLE = "Test Music";
const DEFAULT_ARTIST = "Unknown Artist";
const DEFAULT_ALBUM = "Unknown Album";

// Initialize with default values
urlInput.value = DEFAULT_URL;
titleInput.value = DEFAULT_TITLE;
artistInput.value = DEFAULT_ARTIST;
albumInput.value = DEFAULT_ALBUM;

// Logging
function log(message: string, isError = false) {
  const time = new Date().toLocaleTimeString();
  const entry = document.createElement("div");
  entry.className = isError ? "log-entry error" : "log-entry";
  entry.textContent = `[${time}] ${message}`;
  logOutput.appendChild(entry);
  logOutput.scrollTop = logOutput.scrollHeight;
}

// Update status display
function updateStatus(state: PlaybackState) {
  isPlayingEl.textContent = state.isPlaying ? "Yes" : "No";
  positionEl.textContent = Math.floor(state.position / 1000).toString();
  durationEl.textContent = Math.floor(state.duration / 1000).toString();
}

// Event Handlers
playBtn.addEventListener("click", async () => {
  const url = urlInput.value.trim() || DEFAULT_URL;
  const title = titleInput.value.trim() || DEFAULT_TITLE;
  const artist = artistInput.value.trim() || DEFAULT_ARTIST;
  const album = albumInput.value.trim() || DEFAULT_ALBUM;

  log(`Playing: ${title} by ${artist}`);
  log(`URL: ${url}`);

  try {
    const result = await play({ url, title, artist, album });
    if (result.success) {
      log("✓ Play command sent");
    } else {
      log(`✗ Play failed: ${result.message || "Unknown error"}`, true);
    }
  } catch (e) {
    log(`✗ Play error: ${e}`, true);
  }
});

pauseBtn.addEventListener("click", async () => {
  log("Pausing...");
  try {
    const result = await pause();
    log(result.success ? "✓ Paused" : "✗ Pause failed", !result.success);
  } catch (e) {
    log(`✗ Pause error: ${e}`, true);
  }
});

resumeBtn.addEventListener("click", async () => {
  log("Resuming...");
  try {
    const result = await resume();
    log(result.success ? "✓ Resumed" : "✗ Resume failed", !result.success);
  } catch (e) {
    log(`✗ Resume error: ${e}`, true);
  }
});

stopBtn.addEventListener("click", async () => {
  log("Stopping...");
  try {
    const result = await stop();
    log(result.success ? "✓ Stopped" : "✗ Stop failed", !result.success);
  } catch (e) {
    log(`✗ Stop error: ${e}`, true);
  }
});

prevBtn.addEventListener("click", async () => {
  log("Previous track...");
  try {
    const result = await previous();
    log(result.success ? "✓ Previous" : "✗ Previous failed", !result.success);
  } catch (e) {
    log(`✗ Previous error: ${e}`, true);
  }
});

nextBtn.addEventListener("click", async () => {
  log("Next track...");
  try {
    const result = await next();
    log(result.success ? "✓ Next" : "✗ Next failed", !result.success);
  } catch (e) {
    log(`✗ Next error: ${e}`, true);
  }
});

seekBtn.addEventListener("click", async () => {
  log("Seeking to 30s...");
  try {
    const result = await seek(30000);
    log(result.success ? "✓ Seeked" : "✗ Seek failed", !result.success);
  } catch (e) {
    log(`✗ Seek error: ${e}`, true);
  }
});

stateBtn.addEventListener("click", async () => {
  try {
    const state = await getState();
    updateStatus(state);
    log(`State: playing=${state.isPlaying}, pos=${Math.floor(state.position / 1000)}s, dur=${Math.floor(state.duration / 1000)}s`);
  } catch (e) {
    log(`✗ Get state error: ${e}`, true);
  }
});

startServerBtn.addEventListener("click", async () => {
  log("Starting HTTP server on port 2090...");
  try {
    const result = await startServer();
    if (result.success) {
      log("✓ Server started - check http://localhost:2090");
    } else {
      log("✗ Server failed to start", true);
    }
  } catch (e) {
    log(`✗ Start server error: ${e}`, true);
  }
});

// Auto-update state every 2 seconds when playing
setInterval(async () => {
  try {
    const state = await getState();
    updateStatus(state);
  } catch (e) {
    // Ignore errors for background state updates
  }
}, 2000);

// Initial log
log("Music Notification Plugin loaded");
log("Ready to play audio");
