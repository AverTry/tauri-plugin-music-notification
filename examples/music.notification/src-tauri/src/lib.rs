use tauri::Manager;
use tauri_plugin_music_notification_api::MusicNotificationExt;
use std::ffi::CString;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

// Global flag to track if server is running
static SERVER_RUNNING: AtomicBool = AtomicBool::new(false);

// Android log binding
#[cfg(target_os = "android")]
mod android_log {
    pub enum LogPriority {
        Info = 4,
        Error = 6,
    }

    extern "C" {
        #[link_name = "__android_log_print"]
        pub fn __android_log_print(prio: LogPriority, tag: *const i8, msg: *const i8) -> i32;
    }
}

#[cfg(target_os = "android")]
fn log_info(msg: &str) {
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

#[cfg(target_os = "android")]
fn log_error(msg: &str) {
    let tag = CString::new("RustServer").unwrap();
    let msg = CString::new(msg).unwrap();
    unsafe {
        android_log::__android_log_print(
            android_log::LogPriority::Error,
            tag.as_ptr() as *const i8,
            msg.as_ptr() as *const i8,
        );
    }
}

// HTTP server that runs in a background thread
// Exported as extern "C" for FFI/setter pattern
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn run_http_server() -> std::io::Result<()> {
    run_http_server_impl();
    Ok(())
}

#[cfg(target_os = "android")]
fn run_http_server_impl() {
    use std::thread;
    use std::time::Duration;

    thread::spawn(move || {
        log_info("HTTP server thread started, binding to port 2090...");

        let server = match tiny_http::Server::http("0.0.0.0:2090") {
            Ok(s) => {
                log_info("HTTP server successfully bound to port 2090");
                s
            }
            Err(e) => {
                log_error(&format!("Failed to bind HTTP server: {}", e));
                return;
            }
        };

        log_info("HTTP server ready to accept requests on port 2090");

        for request in server.incoming_requests() {
            let url = request.url().to_string();
            let response = match request.url() {
                "/" => {
                    let html = r#"
<!DOCTYPE html>
<html>
<head><title>Music Player Server</title></head>
<body>
    <h1>Music Player is Running</h1>
    <p>Server is alive on port 2090</p>
    <p>Running in foreground service</p>
</body>
</html>
                    "#;
                    tiny_http::Response::from_string(html)
                        .with_header(
                            tiny_http::Header::from_bytes(&b"Content-Type"[..], &b"text/html"[..]).unwrap()
                        )
                }
                _ => tiny_http::Response::from_string("Not Found").with_status_code(404),
            };
            let _ = request.respond(response);
            log_info(&format!("Served: {}", url));
        }
    });
}

// JNI-exported hello world function for testing Rust-to-Kotlin integration
// Package is "music.notification" which mangles to "music_1notification"
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_example_music_1notification_MainActivity_rustHelloWorld(
    mut env: jni::JNIEnv,
    _class: jni::objects::JClass,
) {
    // This logs to Android logcat - you can see it with: adb logcat | grep RustHello
    let tag = CString::new("RustHello").unwrap();
    let msg = CString::new("Hello from Rust! JNI is working!").unwrap();
    unsafe {
        android_log::__android_log_print(
            android_log::LogPriority::Info,
            tag.as_ptr() as *const i8,
            msg.as_ptr() as *const i8,
        );
    };
}

// JNI-exported function to start the HTTP server from Kotlin/Android service
// Note: For instance methods in Kotlin, we use jobject instead of JClass
// Note: Package name "music_notification" gets mangled to "music_1notification" in JNI
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_plugin_music_1notification_MusicPlayerService_startHttpServer(
    mut env: jni::JNIEnv,
    _this: jni::objects::JObject,
) {
    log_info("startHttpServer called from MusicPlayerService instance");

    if SERVER_RUNNING.load(Ordering::Relaxed) {
        log_info("Server already running, skipping");
        return;
    }

    SERVER_RUNNING.store(true, Ordering::Relaxed);
    run_http_server();
}

// Learn more about Tauri commands at https://v2.tauri.app/develop/calling-rust/
#[tauri::command]
fn greet(name: &str) -> String {
    format!("Hello, {}! You've been greeted from Rust!", name)
}

// Simple HTTP server on port 2090
fn start_http_server() -> std::io::Result<()> {
    let server = tiny_http::Server::http("0.0.0.0:2090")
        .map_err(|e| std::io::Error::new(std::io::ErrorKind::Other, e))?;
    println!("HTTP server running on http://localhost:2090");

    for request in server.incoming_requests() {
        let response = match request.url() {
            "/" => {
                let html = r#"
<!DOCTYPE html>
<html>
<head><title>Music Player Server</title></head>
<body>
    <h1>Music Player is Running</h1>
    <p>Server is alive on port 2090</p>
</body>
</html>
                "#;
                tiny_http::Response::from_string(html)
                    .with_header(
                        tiny_http::Header::from_bytes(&b"Content-Type"[..], &b"text/html"[..]).unwrap()
                    )
            }
            _ => tiny_http::Response::from_string("Not Found").with_status_code(404),
        };
        let _ = request.respond(response);
    }
    Ok(())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![greet])
        .plugin(tauri_plugin_music_notification_api::init())
        .setup(|app| {
            // Register the server starter function
            #[cfg(target_os = "android")]
            app.music_notification().set_server_starter(|| run_http_server());

            #[cfg(not(target_os = "android"))]
            app.music_notification().set_server_starter(start_http_server);
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
