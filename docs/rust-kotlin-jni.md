# Calling Rust from Kotlin in Android (Tauri)

This document explains how to call Rust functions from Kotlin in a Tauri Android app using JNI.

> **Current plugin design:** the plugin now owns the fixed
> `MusicPlayerService_serverStart` / `MusicPlayerService_serverStop` JNI exports.
> App code should implement the Rust `Server` trait and register it with
> `set_server(...)`; app code no longer needs to define those JNI wrappers itself.

## Overview

In this plugin, Rust HTTP server code runs in the foreground service to persist when the app goes to background. The communication flow is:

```
┌─────────────┐     ┌──────────────┐     ┌──────────────────┐     ┌─────────────┐
│   Frontend  │ ──▶ │   Plugin     │ ──▶ │   Foreground     │ ──▶ │    Rust     │
│  (TS/JS)    │     │  (Kotlin)    │     │   Service        │     │   Server    │
└─────────────┘     └──────────────┘     └──────────────────┘     └─────────────┘
                         │                        │
                         │                        │ JNI
                         ▼                        ▼
                   Tauri Commands           System.loadLibrary()
```

## Architecture

### Code Path When Pressing Play

1. **Frontend**: User clicks play button → calls `invoke('plugin:music-notification|play')`
2. **Plugin (Kotlin)**: `play()` command starts `MusicPlayerService` (foreground service)
3. **Service (Kotlin)**: `onCreate()` loads the app native library and calls the plugin JNI symbol
4. **Plugin (Rust)**: JNI forwards to the registered `Server` trait object
5. **Rust App Server**: HTTP server starts on port 2090 in a background thread

### Module Layout

```
examples/music.notification/src-tauri/src/lib.rs  ← Rust HTTP server + JNI exports
android/src/main/java/MusicPlayerService.kt       ← Service that calls JNI
android/src/main/java/ExamplePlugin.kt            ← Tauri plugin commands
```

---

## Implementation

### Step 1: Define Rust HTTP Server

In `examples/music.notification/src-tauri/src/lib.rs`:

```rust
use std::sync::atomic::{AtomicBool, Ordering};

static SERVER_RUNNING: AtomicBool = AtomicBool::new(false);

// HTTP server implementation
#[cfg(target_os = "android")]
fn run_http_server_impl() {
    use std::thread;

    thread::spawn(move || {
        let server = match tiny_http::Server::http("0.0.0.0:2090") {
            Ok(s) => s,
            Err(e) => {
                log_error(&format!("Failed to bind: {}", e));
                return;
            }
        };

        for request in server.incoming_requests() {
            let response = match request.url() {
                "/" => tiny_http::Response::from_string("<h1>Music Player Running</h1>"),
                _ => tiny_http::Response::from_string("Not Found").with_status_code(404),
            };
            let _ = request.respond(response);
        }
    });
}

// Exported function for JNI (must be extern "C")
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn run_http_server() -> std::io::Result<()> {
    run_http_server_impl();
    Ok(())
}
```

### Step 2: Define JNI Function

The JNI function is the bridge between Kotlin and Rust:

```rust
// JNI-exported function for Kotlin to call
// Package: com.plugin.music_notification → mangles to com_plugin_music_1notification
// Class: MusicPlayerService
// Method: startHttpServer
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_plugin_music_1notification_MusicPlayerService_startHttpServer(
    _env: jni::JNIEnv,
    _this: jni::objects::JObject,  // JObject for instance methods
) {
    if SERVER_RUNNING.load(Ordering::Relaxed) {
        return;
    }

    SERVER_RUNNING.store(true, Ordering::Relaxed);
    run_http_server();
}
```

**Key points:**
- `#[no_mangle]` prevents Rust name mangling
- `extern "C"` uses C calling convention
- Instance methods use `JObject`, static methods use `JClass`
- Underscores in package names become `_1` in JNI

### Step 3: Load Library and Call from Kotlin

In `android/src/main/java/MusicPlayerService.kt`:

```kotlin
class MusicPlayerService : Service() {
    companion object {
        private const val TAG = "MusicPlayerService"

        // Load the native library
        fun loadNativeLibrary(context: Context) {
            try {
                System.loadLibrary("musicnotification_lib")
                Log.d(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library: ${e.message}")
            }
        }
    }

    // Declare the native Rust function
    private external fun startHttpServer()

    override fun onCreate() {
        super.onCreate()

        // Load the native library
        loadNativeLibrary(this)

        // Start the Rust HTTP server
        Log.d(TAG, "Starting Rust HTTP server...")
        try {
            startHttpServer()
            Log.d(TAG, "Rust HTTP server started")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to start server: ${e.message}")
        }
    }
}
```

### Step 4: Start Service from Plugin Command

In `android/src/main/java/ExamplePlugin.kt`:

```kotlin
@Command
fun play(invoke: Invoke) {
    val args = invoke.parseArgs(PlayArgs::class.java)

    val serviceIntent = Intent(activity, MusicPlayerService::class.java).apply {
        action = MusicPlayerService.ACTION_PLAY
        putExtra(MusicPlayerService.EXTRA_URL, args.url)
        putExtra(MusicPlayerService.EXTRA_TITLE, args.title)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        activity.startForegroundService(serviceIntent)
    } else {
        activity.startService(serviceIntent)
    }

    invoke.resolve(JSObject().apply { put("success", true) })
}
```

---

## JNI Name Mangling

Package names with underscores get mangled in JNI:

| Package Name | JNI Mangled |
|--------------|-------------|
| `music.notification` | `music_1notification` |
| `com.plugin.music_notification` | `com_plugin_music_1notification` |

JNI function name pattern:
```
Java_<mangled_package>_<Class>_<Method>
```

Examples:
```rust
// music.notification.MainActivity.rustHelloWorld()
Java_com_example_music_1notification_MainActivity_rustHelloWorld

// com.plugin.music_notification.MusicPlayerService.startHttpServer()
Java_com_plugin_music_1notification_MusicPlayerService_startHttpServer
```

---

## Build and Verify

### Build Android App

```bash
cd examples/music.notification
bash build-android.sh
```

### Verify JNI Symbols

```bash
# Find the .so file location
find . -name "libmusicnotification_lib.so"

# Check exported symbols
nm -D path/to/libmusicnotification_lib.so | grep Java_
```

Expected output:
```
000000000025e5c8 T Java_com_example_music_1notification_MainActivity_rustHelloWorld
000000000025e610 T Java_com_plugin_music_1notification_MusicPlayerService_startHttpServer
```

### Check Logs

```bash
adb logcat | grep -E "RustServer|MusicPlayerService"
```

---

## Android Logging from Rust

```rust
#[cfg(target_os = "android")]
mod android_log {
    pub enum LogPriority { Info = 4, Error = 6 }

    extern "C" {
        #[link_name = "__android_log_print"]
        pub fn __android_log_print(prio: LogPriority, tag: *const i8, msg: *const i8) -> i32;
    }
}

#[cfg(target_os = "android")]
fn log_info(msg: &str) {
    use std::ffi::CString;
    let tag = CString::new("RustServer").unwrap();
    let msg = CString::new(msg).unwrap();
    unsafe {
        android_log::__android_log_print(
            android_log::LogPriority::Info,
            tag.as_ptr() as *const i8,
            msg.as_ptr() as *const i8,
        );
    }
}
```

---

## Common Issues

### "No implementation found" Error

```
No implementation found for void com.plugin.music_notification.MusicPlayerService.startHttpServer()
```

**Causes:**
1. Function name mismatch (check `_1` for underscores)
2. Library not loaded before calling
3. Symbol not exported (missing `#[no_mangle]` or `extern "C"`)

**Fix:**
```bash
# 1. Verify symbol exists
nm -D libmusicnotification_lib.so | grep startHttpServer

# 2. Check function name matches package
# Package: com.plugin.music_notification
# JNI: com_plugin_music_1notification_MusicPlayerService_startHttpServer
```

### Server Stops When App Backgrounded

**Cause**: Service not running as foreground service with notification.

**Fix**: Ensure service calls `startForeground()` in `onCreate()`:

```kotlin
override fun onCreate() {
    super.onCreate()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val notification = createNotification()
        startForeground(1, notification)
    }
}
```

---

## Dependencies

### Cargo.toml (Example App)

```toml
[dependencies]
tauri = { version = "2", features = ["devtools"] }
tauri-plugin-music-notification-api = { path = "../../../" }
tiny_http = "0.12"
jni = "0.21"
```

### Gradle (Android)

```kotlin
// Already included in Tauri Android template
implementation("com.github.tauri-apps.tauri:tauri-android:2.x")
```

---

## Summary

| Component | Location | Purpose |
|-----------|----------|---------|
| `run_http_server()` | `src-tauri/src/lib.rs` | Actual server implementation |
| `Java_..._startHttpServer()` | `src-tauri/src/lib.rs` | JNI bridge function |
| `MusicPlayerService` | `android/src/main/java/` | Foreground service that calls JNI |
| `ExamplePlugin.play()` | `android/src/main/java/` | Tauri command to start service |

**Key Takeaway**: Use JNI when you need Rust code to run inside Android native services (foreground services, broadcast receivers, etc.). The native library must be loaded before calling any JNI functions.

## References

- [JNI Specification](https://docs.oracle.com/javase/8/docs/technotes/guides/jni/)
- [Tauri Android Docs](https://v2.tauri.app/start/migrate/from-tauri-1/)
- [Rust JNI Crate](https://docs.rs/jni/)
