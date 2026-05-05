import { createSignal, createEffect, onMount, onCleanup } from "solid-js";
import { invoke } from "@tauri-apps/api/core";
import {
  play,
  pause,
  resume,
  stop,
  next,
  previous,
  seek,
  getState,
  setPlayingQueue,
  setVolume,
} from "music-notification-api";
import { onPlay, onPause, onNext, onPrev } from "music-notification-api";
import { listen } from "@tauri-apps/api/event";
import "./App.css";

function App() {
  // ===== STATE MANAGEMENT =====
  const [playlist, setPlaylist] = createSignal([]);
  const [position, setPosition] = createSignal(0);
  const [duration, setDuration] = createSignal(0);
  const [isPlaying, setIsPlaying] = createSignal(false);
  const [playerReady, setPlayerReady] = createSignal(false); // Track if player is prepared

  // ===== EVENT LISTENERS SETUP =====
  onMount(async () => {
    console.log("Setting up event listeners...");
    
    const unPlay = await onPlay(() => {
      console.log("Playing");
      setIsPlaying(true);
    });
    
    const unPause = await onPause(() => {
      console.log("Paused");
      setIsPlaying(false);
    });
    
    const unNext = await onNext(() => console.log("Next Track"));
    const unPrev = await onPrev(() => console.log("Previous Track"));

    const unTrackChanged = await listen("onTrackChanged", (event) => {
      console.log("Track changed!", event.payload);
      // Reset position when track changes
      setPosition(0);
      setPlayerReady(false); // Player needs time to prepare new track
    });

    onCleanup(() => {
      console.log("Cleaning up event listeners");
      unPlay();
      unPause();
      unNext();
      unPrev();
      unTrackChanged();
    });
  });

  // ===== UI HELPER FUNCTIONS =====
  const formatTime = (ms) => {
    if (!ms || ms <= 0) return "0:00";
    const seconds = Math.floor((ms / 1000) % 60);
    const minutes = Math.floor(ms / (1000 * 60));
    return `${minutes}:${seconds.toString().padStart(2, "0")}`;
  };

  // ===== CORE MUSIC FUNCTIONS =====
  const pickFolderAndLoad = async () => {
    try {
      const uris = await invoke("pick_and_scan_music");

      if (!uris || uris.length === 0) {
        console.log("No music files found or picker cancelled.");
        return;
      }

      const formattedSongs = uris.map((uri, index) => {
        const fileName = decodeURIComponent(uri.split("/").pop() || `Track ${index + 1}`);
        return {
          id: index,
          name: fileName,
          path: uri,
          url: uri,
          lufs: null,
          coverUrl: "",
        };
      });

      setPlaylist(formattedSongs);

      await setPlayingQueue(
        {
          songs: formattedSongs,
          currentIndex: 0,
        },
        "sequential"
      );

      // Play first track
      setPlayerReady(false); // Mark player as not ready
      await play({
        url: formattedSongs[0].url,
        title: formattedSongs[0].name,
        artist: "Local Storage",
        album: "My Music",
      });
      
      // Give the native player time to prepare
      setTimeout(() => setPlayerReady(true), 500);
    } catch (err) {
      console.error("Failed to load music and set queue:", err);
    }
  };

  const playTrack = async (index) => {
    const song = playlist()[index];
    if (!song) return;

    setPlayerReady(false); // Mark player as not ready
    await play({
      url: song.url,
      title: song.name,
      artist: "Local Library",
      album: "My Music",
    });
    
    // Give the native player time to prepare
    setTimeout(() => setPlayerReady(true), 500);
  };

  const togglePlay = async () => {
    const currentlyPlaying = isPlaying();

    if (currentlyPlaying) {
      await pause();
      setIsPlaying(false);
    } else {
      if (playlist().length > 0) {
        await resume();
        setIsPlaying(true);
      }
    }

    // Verify state from Android
    setTimeout(async () => {
      const state = await getState();
      setIsPlaying(state.isPlaying);
    }, 100);
  };

  // ===== HEARTBEAT: Update progress while playing =====
  createEffect(() => {
    if (isPlaying() && playerReady()) {
      // Only start polling after player is confirmed ready
      const interval = setInterval(async () => {
        try {
          const state = await getState();
          
          // Only update if we get valid numbers (duration > 0)
          if (state.duration > 0) {
            setPosition(state.position);
            setDuration(state.duration);
          }
          
          // If player stopped, clear interval
          if (!state.isPlaying) {
            setIsPlaying(false);
            clearInterval(interval);
          }
        } catch (err) {
          console.error("Error fetching state:", err);
        }
      }, 1000); // Poll once per second

      onCleanup(() => clearInterval(interval));
    }
  });

  // ===== INITIAL STATE CHECK =====
  onMount(() => {
    // Check initial state on app load
    const timeout = setTimeout(async () => {
      try {
        const state = await getState();
        setIsPlaying(state.isPlaying);
        if (state.duration > 0) {
          setDuration(state.duration);
          setPosition(state.position);
          setPlayerReady(true);
        }
      } catch (err) {
        console.error("Error checking initial state:", err);
      }
    }, 1000);

    onCleanup(() => clearTimeout(timeout));
  });

  // ===== RENDER =====
  return (
    <div>
      <h2>Music Player (Queue Mode)</h2>
      <button onClick={pickFolderAndLoad}>Load Folder</button>
      <br />

      {/* Manual Controls */}
      <div style="display: flex; gap: 10px; margin-top: 20px;">
        <div class="transport-controls" style="display: flex; gap: 20px; align-items: center;">
          <button onClick={() => previous()}>⏮️</button>

          <button 
            onClick={togglePlay} 
            style="font-size: 2rem; background: none; border: none; cursor: pointer;"
          >
            {isPlaying() ? "⏸️" : "▶️"}
          </button>

          <button onClick={() => next()}>⏭️</button>
        </div>
      </div>

      {/* Progress Bar */}
      <div class="progress-container">
        <span>{formatTime(position())}</span>

        <input
          type="range"
          min="0"
          max={duration() || 100}
          value={position()}
          onInput={(e) => {
            const newPos = parseInt(e.target.value);
            setPosition(newPos);
            seek(newPos);
          }}
        />

        <span>{formatTime(duration())}</span>
      </div>

      {/* Playlist */}
      <ul style="margin-top: 20px; list-style: none; padding: 0;">
        {playlist().map((song, i) => (
          <li 
            onClick={() => playTrack(i)} 
            style="padding: 10px; cursor: pointer; border: 1px solid #ccc; margin: 5px 0;"
          >
            {song.name}
          </li>
        ))}
      </ul>
    </div>
  );
}

export default App;
