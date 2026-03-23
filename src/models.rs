use serde::{Deserialize, Serialize};

#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PingRequest {
  pub value: Option<String>,
}

#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PingResponse {
  pub value: Option<String>,
}

#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PlayRequest {
  pub url: String,
  pub title: Option<String>,
  pub artist: Option<String>,
  pub album: Option<String>,
}

#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PlayResponse {
  pub success: bool,
  pub message: Option<String>,
}

#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct EmptyRequest {}

#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct EmptyResponse {
  pub success: bool,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PlaybackState {
  pub is_playing: bool,
  pub position: i64,
  pub duration: i64,
}

#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SetVolumeRequest {
  pub volume: f32,
}

#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SetVolumeResponse {
  pub success: bool,
  pub message: Option<String>,
}

#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SetServerRequest {
  pub library_name: String,
}

#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SetServerResponse {
  pub success: bool,
  pub message: Option<String>,
}

// Server trait for example apps to implement
use std::sync::{Arc, Mutex};

/// Trait that example apps must implement to provide a custom server
pub trait Server: Send + Sync {
  fn start(self: Arc<Self>) -> Result<(), String>;
  fn stop(self: Arc<Self>) -> Result<(), String>;
  fn is_running(self: Arc<Self>) -> bool;
}

/// Global storage for the registered server implementation
static REGISTERED_SERVER: Mutex<Option<Arc<dyn Server>>> = Mutex::new(None);

/// Register a server implementation
pub fn set_server(server: Arc<dyn Server>) {
  *REGISTERED_SERVER.lock().unwrap() = Some(server);
}

/// Get the registered server
pub fn get_server() -> Option<Arc<dyn Server>> {
  REGISTERED_SERVER.lock().unwrap().clone()
}

/// Android log binding for server functions
#[cfg(target_os = "android")]
mod server_log {
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
fn server_log_info(msg: &str) {
  use std::ffi::CString;
  let tag = CString::new("ServerTrait").unwrap();
  let msg = CString::new(msg).unwrap();
  unsafe {
    server_log::__android_log_print(
      server_log::LogPriority::Info,
      tag.as_ptr() as *const i8,
      msg.as_ptr() as *const i8,
    );
  }
}

#[cfg(target_os = "android")]
fn server_log_error(msg: &str) {
  use std::ffi::CString;
  let tag = CString::new("ServerTrait").unwrap();
  let msg = CString::new(msg).unwrap();
  unsafe {
    server_log::__android_log_print(
      server_log::LogPriority::Error,
      tag.as_ptr() as *const i8,
      msg.as_ptr() as *const i8,
    );
  }
}

/// Call start on the registered server (called from JNI wrapper)
#[no_mangle]
pub extern "C" fn server_start() -> i32 {
  #[cfg(target_os = "android")]
  {
    if let Some(server) = get_server() {
      match server.start() {
        Ok(()) => {
          server_log_info("server_start: Server started successfully");
          0
        }
        Err(e) => {
          server_log_error(&format!("server_start failed: {}", e));
          -1
        }
      }
    } else {
      server_log_error("server_start: No server registered");
      -1
    }
  }
  #[cfg(not(target_os = "android"))]
  {
    let _ = get_server();
    0
  }
}

/// Call stop on the registered server (called from JNI wrapper)
#[no_mangle]
pub extern "C" fn server_stop() -> i32 {
  #[cfg(target_os = "android")]
  {
    if let Some(server) = get_server() {
      match server.stop() {
        Ok(()) => {
          server_log_info("server_stop: Server stopped successfully");
          0
        }
        Err(e) => {
          server_log_error(&format!("server_stop failed: {}", e));
          -1
        }
      }
    } else {
      server_log_error("server_stop: No server registered");
      -1
    }
  }
  #[cfg(not(target_os = "android"))]
  {
    let _ = get_server();
    0
  }
}
