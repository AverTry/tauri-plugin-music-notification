# Tauri Plugin Music Notification

A Tauri plugin for Android that provides music playback notifications with media controls.

## Features

- 🎵 Play music from URLs with media notifications
- 🎮 Full playback controls (play, pause, resume, stop)
- ⏭️ Next/Previous track navigation
- ⏩ Seek to specific positions
- 📊 Get current playback state
- 🔔 Native Android media notification with controls
- 🎨 Lock screen controls support

## Installation

Install the plugin using your preferred package manager:

```bash
npm run tauri add music-notification-api
```

Or manually add to `package.json`:

```json
{
  "dependencies": {
    "music-notification-api": "file:../../path/to/plugin"
  }
}
```

Add the plugin to your `src-tauri/Cargo.toml`:

```toml
[dependencies]
tauri-plugin-music-notification-api = { path = "../../../path/to/plugin" }
```

## Setup

### Rust

Register the plugin in your Tauri app (`src-tauri/src/lib.rs`):

```rust
#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![...])
        .plugin(tauri_plugin_music_notification_api::init())
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
```

### Permissions

Add the required permissions to your `src-tauri/capabilities/default.json`:

```json
{
  "permissions": [
    "core:default",
    "music-notification:default"
  ]
}
```

**Important:** Do NOT copy the `permissions/` folder from the plugin to your app. The plugin's permissions are automatically loaded.

### Android - HTTP/Cleartext Traffic

If you're using HTTP URLs (not HTTPS), you need to enable cleartext traffic:

1. **Add network security config** to `src-tauri/gen/android/app/src/main/res/xml/network_security_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
            <certificates src="user" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

2. **Update AndroidManifest.xml** (`src-tauri/gen/android/app/src/main/AndroidManifest.xml`):

```xml
<application
    ...
    android:usesCleartextTraffic="true"
    android:networkSecurityConfig="@xml/network_security_config">
```

**Note:** These Android files get regenerated when you run `npx tauri android init`. You may need to reapply these changes after regenerating.

### Android Permissions

The plugin automatically includes these Android permissions:
- `INTERNET` - For streaming audio
- `FOREGROUND_SERVICE` - For background playback
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` - For media playback service
- `POST_NOTIFICATIONS` - For displaying notifications

## Supported Audio Formats

The plugin uses Android's `MediaPlayer`, which supports:
- **MP3** (.mp3)
- **AAC** (.aac, .m4a)
- **FLAC** (.flac)
- **Vorbis** (.ogg)
- **WAV** (.wav)
- **OpUS** (.opus)
- **MIDI** (.mid, .midi)

Support varies by Android version and device hardware.

## Usage

### JavaScript/TypeScript

```typescript
import { play, pause, resume, stop, next, previous, seek, getState } from 'music-notification-api';

// Play music
await play({
  url: "https://example.com/song.mp3",
  title: "Song Title",
  artist: "Artist Name",
  album: "Album Name"
});

// Pause playback
await pause();

// Resume playback
await resume();

// Stop playback
await stop();

// Skip to next track
await next();

// Go to previous track
await previous();

// Seek to position (in milliseconds)
await seek(30000); // Seek to 30 seconds

// Get current playback state
const state = await getState();
console.log(state.isPlaying); // true/false
console.log(state.position);  // Current position in ms
console.log(state.duration);  // Total duration in ms
```

## API Reference

### `play(options: PlayOptions)`

Starts playing music from a URL.

**Parameters:**
- `url` (string, required): The URL of the audio file
- `title` (string, optional): Song title
- `artist` (string, optional): Artist name
- `album` (string, optional): Album name

**Returns:** `Promise<{ success: boolean; message?: string }>`

### `pause()`

Pauses the current playback.

**Returns:** `Promise<{ success: boolean }>`

### `resume()`

Resumes paused playback.

**Returns:** `Promise<{ success: boolean }>`

### `stop()`

Stops playback and clears the notification.

**Returns:** `Promise<{ success: boolean }>`

### `next()`

Skips to the next track (if available in playlist).

**Returns:** `Promise<{ success: boolean }>`

### `previous()`

Goes back to the previous track (if available in playlist).

**Returns:** `Promise<{ success: boolean }>`

### `seek(position: number)`

Seeks to a specific position in the current track.

**Parameters:**
- `position` (number): Position in milliseconds

**Returns:** `Promise<{ success: boolean }>`

### `getState()`

Gets the current playback state.

**Returns:** `Promise<PlaybackState>`

```typescript
interface PlaybackState {
  isPlaying: boolean;
  position: number;  // in milliseconds
  duration: number;  // in milliseconds
}
```

## Platform Support

| Platform | Supported |
|----------|-----------|
| Android  | ✅        |
| iOS      | ❌        |
| Windows  | ❌        |
| macOS    | ❌        |
| Linux    | ❌        |

Currently, this plugin only supports Android. Desktop implementations return placeholder responses.

## Troubleshooting

### "music-notification|play not allowed by ACL" error

This means the plugin permissions aren't configured correctly:

1. Make sure you're using `music-notification:default` in capabilities (NOT individual permissions)
2. Do NOT copy the `permissions/` folder to your app
3. Make sure `tauri-plugin-music-notification-api` is in your `Cargo.toml`

### HTTP URLs not working (MediaPlayer errors)

Android blocks cleartext (HTTP) traffic by default. Follow the **HTTP/Cleartext Traffic** section above to enable it.

### "stop called in state 0" MediaPlayer error

This error is handled internally by the plugin and can be ignored. It occurs when the MediaPlayer is stopped while in an idle state.

### Build errors after regenerating Android project

After running `npx tauri android init`, you may need to reapply:
- Network security config (`network_security_config.xml`)
- Cleartext traffic settings in `AndroidManifest.xml`

## Example

Check out the [example app](./examples/music.notification) for a complete vanilla TypeScript implementation.

## License

MIT

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
