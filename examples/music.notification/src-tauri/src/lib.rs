use tauri_plugin_music_notification_api::{Server, set_server};
use std::ffi::CString;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{mpsc, Arc};
use std::thread;
use std::time::Duration;

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

// HTTP server implementation that implements the Server trait
struct HttpServer {
    running: Arc<AtomicBool>,
}

impl Server for HttpServer {
    fn library_name(&self) -> &str {
        "musicnotification_lib"
    }

    fn start(self: Arc<Self>) -> Result<(), String> {
        if self.running.load(Ordering::Relaxed) {
            log_info("Server already running, skipping start");
            return Ok(());
        }

        self.running.store(true, Ordering::Relaxed);
        log_info("Server trait: Starting HTTP server");
        run_http_server_impl();
        Ok(())
    }

    fn stop(self: Arc<Self>) -> Result<(), String> {
        self.running.store(false, Ordering::Relaxed);
        log_info("Server trait: Stopping HTTP server");
        stop_http_server_impl();
        Ok(())
    }

    fn is_running(self: Arc<Self>) -> bool {
        self.running.load(Ordering::Relaxed)
    }
}

// HTTP server implementation
#[cfg(target_os = "android")]
fn run_http_server_impl() {
    use std::thread;

    let (tx, rx) = mpsc::channel();

    // Store the sender globally so we can signal stop later
    unsafe {
        SERVER_STOP_TX = Some(tx);
    }

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

        // Server loop with periodic stop checks
        'server_loop: loop {
            // Check for stop signal first (non-blocking)
            match rx.try_recv() {
                Ok(_) | Err(mpsc::TryRecvError::Disconnected) => {
                    log_info("Received stop signal, shutting down HTTP server");
                    break 'server_loop;
                }
                Err(mpsc::TryRecvError::Empty) => {
                    // Continue - process requests
                }
            }

            // Process requests with a timeout check
            // Since incoming_requests() blocks, we use recv_timeout on the channel
            // to wake up periodically and check if we should stop
            let deadline = std::time::Instant::now() + Duration::from_millis(100);
            let mut processed = false;

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

                // Only process one request per cycle, then check for stop
                processed = true;

                // Check time to avoid blocking too long
                if std::time::Instant::now() > deadline {
                    break;
                }
            }

            // If we didn't process any request, sleep a bit to avoid busy-waiting
            if !processed {
                thread::sleep(Duration::from_millis(50));
            }
        }

        log_info("HTTP server thread exited");
    });
}

// Global channel sender for stopping the server
#[cfg(target_os = "android")]
static mut SERVER_STOP_TX: Option<mpsc::Sender<()>> = None;

// Function to stop the HTTP server
#[cfg(target_os = "android")]
fn stop_http_server_impl() {
    unsafe {
        if let Some(tx) = &SERVER_STOP_TX {
            log_info("Sending stop signal to HTTP server");
            let _ = tx.send(());
        } else {
            log_info("No HTTP server running to stop");
        }
    }
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
    // Register the HTTP server implementation with the plugin
    let http_server = HttpServer {
        running: Arc::new(AtomicBool::new(false)),
    };
    set_server(Arc::new(http_server));
    log_info("Registered HttpServer with plugin");

    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![greet])
        .plugin(tauri_plugin_music_notification_api::init())
        .setup(|app| {
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
