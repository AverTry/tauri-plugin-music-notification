use serde::de::DeserializeOwned;
use std::sync::Mutex;
use std::thread;
use tauri::{
  plugin::{PluginApi, PluginHandle},
  AppHandle, Runtime,
};

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

#[cfg(target_os = "ios")]
tauri::ios_plugin_binding!(init_plugin_music_notification);

// initializes the Kotlin or Swift plugin classes
pub fn init<R: Runtime, C: DeserializeOwned>(
  _app: &AppHandle<R>,
  api: PluginApi<R, C>,
) -> crate::Result<MusicNotification<R>> {
  #[cfg(target_os = "android")]
  let handle = api.register_android_plugin("com.plugin.music_notification", "MusicNotificationPlugin")?;
  #[cfg(target_os = "ios")]
  let handle = api.register_ios_plugin(init_plugin_music_notification)?;
  Ok(MusicNotification(handle, MusicNotificationState::default()))
}

/// Access to the music-notification APIs.
pub struct MusicNotification<R: Runtime>(PluginHandle<R>, MusicNotificationState);

impl<R: Runtime> MusicNotification<R> {
  pub fn ping(&self, payload: PingRequest) -> crate::Result<PingResponse> {
    self
      .0
      .run_mobile_plugin("ping", payload)
      .map_err(Into::into)
  }

  pub fn play(&self, payload: PlayRequest) -> crate::Result<PlayResponse> {
    self
      .0
      .run_mobile_plugin("play", payload)
      .map_err(Into::into)
  }

  pub fn pause(&self) -> crate::Result<EmptyResponse> {
    self
      .0
      .run_mobile_plugin("pause", EmptyRequest {})
      .map_err(Into::into)
  }

  pub fn resume(&self) -> crate::Result<EmptyResponse> {
    self
      .0
      .run_mobile_plugin("resume", EmptyRequest {})
      .map_err(Into::into)
  }

  pub fn stop(&self) -> crate::Result<EmptyResponse> {
    self
      .0
      .run_mobile_plugin("stop", EmptyRequest {})
      .map_err(Into::into)
  }

  pub fn next(&self) -> crate::Result<EmptyResponse> {
    self
      .0
      .run_mobile_plugin("next", EmptyRequest {})
      .map_err(Into::into)
  }

  pub fn previous(&self) -> crate::Result<EmptyResponse> {
    self
      .0
      .run_mobile_plugin("previous", EmptyRequest {})
      .map_err(Into::into)
  }

  pub fn seek(&self, position: i64) -> crate::Result<EmptyResponse> {
    #[derive(serde::Serialize)]
    struct SeekRequest {
      position: i64,
    }
    self
      .0
      .run_mobile_plugin("seek", SeekRequest { position })
      .map_err(Into::into)
  }

  pub fn get_state(&self) -> crate::Result<PlaybackState> {
    self
      .0
      .run_mobile_plugin("getState", EmptyRequest {})
      .map_err(Into::into)
  }

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

  pub fn set_server_starter<F>(&self, f: F)
  where
    F: FnOnce() -> std::io::Result<()> + Send + 'static,
  {
    let mut starter = self.1.server_starter.lock().unwrap();
    *starter = Some(Box::new(f));
  }
}
