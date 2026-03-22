use serde::de::DeserializeOwned;
use std::sync::Mutex;
use std::thread;
use tauri::{plugin::PluginApi, AppHandle, Runtime};

use crate::models::*;

type ServerStarter = Box<dyn FnOnce() -> std::io::Result<()> + Send>;

pub struct MusicNotificationState {
  pub server_starter: Mutex<Option<ServerStarter>>,
}

impl Default for MusicNotificationState {
  fn default() -> Self {
    Self {
      server_starter: Mutex::new(None),
    }
  }
}

pub fn init<R: Runtime, C: DeserializeOwned>(
  app: &AppHandle<R>,
  _api: PluginApi<R, C>,
) -> crate::Result<MusicNotification<R>> {
  Ok(MusicNotification(app.clone(), MusicNotificationState::default()))
}

/// Access to the music-notification APIs.
pub struct MusicNotification<R: Runtime>(AppHandle<R>, MusicNotificationState);

impl<R: Runtime> MusicNotification<R> {
  pub fn ping(&self, payload: PingRequest) -> crate::Result<PingResponse> {
    Ok(PingResponse {
      value: payload.value,
    })
  }

  pub fn play(&self, _payload: PlayRequest) -> crate::Result<PlayResponse> {
    Ok(PlayResponse {
      success: false,
      message: Some("Desktop implementation not available".to_string()),
    })
  }

  pub fn pause(&self) -> crate::Result<EmptyResponse> {
    Ok(EmptyResponse { success: false })
  }

  pub fn resume(&self) -> crate::Result<EmptyResponse> {
    Ok(EmptyResponse { success: false })
  }

  pub fn stop(&self) -> crate::Result<EmptyResponse> {
    Ok(EmptyResponse { success: false })
  }

  pub fn next(&self) -> crate::Result<EmptyResponse> {
    Ok(EmptyResponse { success: false })
  }

  pub fn previous(&self) -> crate::Result<EmptyResponse> {
    Ok(EmptyResponse { success: false })
  }

  pub fn seek(&self, _position: i64) -> crate::Result<EmptyResponse> {
    Ok(EmptyResponse { success: false })
  }

  pub fn get_state(&self) -> crate::Result<PlaybackState> {
    Ok(PlaybackState {
      is_playing: false,
      position: 0,
      duration: 0,
    })
  }

  /// Register a function that starts the HTTP server
  pub fn set_server_starter<F>(&self, f: F)
  where
    F: FnOnce() -> std::io::Result<()> + Send + 'static,
  {
    let mut starter = self.1.server_starter.lock().unwrap();
    *starter = Some(Box::new(f));
  }

  /// Start the registered server (if any)
  pub fn start_server(&self) -> crate::Result<EmptyResponse> {
    let mut starter = self.1.server_starter.lock().unwrap();
    if let Some(f) = starter.take() {
      thread::spawn(move || {
        if let Err(e) = f() {
          eprintln!("Server error: {}", e);
        }
      });
      Ok(EmptyResponse { success: true })
    } else {
      Err(crate::Error::Io(std::io::Error::new(
        std::io::ErrorKind::NotFound,
        "No server starter registered",
      )))
    }
  }
}
