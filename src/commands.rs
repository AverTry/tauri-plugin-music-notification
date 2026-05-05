use tauri::{command, AppHandle, Runtime, Emitter};

use crate::models::*;
use crate::MusicNotificationExt;
use crate::Result;

#[command]
pub(crate) async fn ping<R: Runtime>(
    app: AppHandle<R>,
    payload: PingRequest,
) -> Result<PingResponse> {
    app.music_notification().ping(payload)
}

#[command]
pub(crate) async fn play<R: Runtime>(
    app: AppHandle<R>,
    payload: PlayRequest,
) -> Result<PlayResponse> {
    // Removed .await because play() returns Result, not a Future
    let result = app.music_notification().play(payload)?; 
    
    #[cfg(desktop)]
    if result.success {
        let state = app.music_notification().get_state()?;
        let session = app.music_notification().get_playback_session()?;
        
        let event = PlaybackEvent {
            action: "play".to_string(),
            current_index: session.queue.current_index,
            is_playing: state.is_playing,
            track_id: session.current_song_id,
        };
        app.emit("onPlay", event).ok();
    }
    Ok(result)
}

#[command]
pub(crate) async fn pause<R: Runtime>(app: AppHandle<R>) -> Result<EmptyResponse> {
    let result = app.music_notification().pause();
    #[cfg(desktop)]
    {
        let state = app.music_notification().get_state()?;
        let session = app.music_notification().get_playback_session()?;
        
        let event = PlaybackEvent {
            action: "pause".to_string(),
            current_index: session.queue.current_index,
            is_playing: state.is_playing,
            track_id: session.current_song_id,
        };
        app.emit("onPause", event).ok();
    }
    result
}

#[command]
pub(crate) async fn pause_after<R: Runtime>(
    app: AppHandle<R>,
    payload: PauseAfterRequest,
) -> Result<EmptyResponse> {
    app.music_notification().pause_after(payload)
}

#[command]
pub(crate) async fn resume<R: Runtime>(app: AppHandle<R>) -> Result<EmptyResponse> {
    app.music_notification().resume()
}

#[command]
pub(crate) async fn stop<R: Runtime>(app: AppHandle<R>) -> Result<EmptyResponse> {
    app.music_notification().stop()
}

#[command]
pub(crate) async fn next<R: Runtime>(app: AppHandle<R>) -> Result<EmptyResponse> {
    let result = app.music_notification().next()?;
    #[cfg(desktop)]
    {
        let state = app.music_notification().get_state()?;
        let session = app.music_notification().get_playback_session()?;
        
        let event = PlaybackEvent {
            action: "next".to_string(),
            current_index: session.queue.current_index,
            is_playing: state.is_playing,
            track_id: session.current_song_id,
        };
        app.emit("onNext", event).ok();
    }
    Ok(result)
}

#[command]
pub(crate) async fn previous<R: Runtime>(app: AppHandle<R>) -> Result<EmptyResponse> {
    let result = app.music_notification().previous()?;
    #[cfg(desktop)]
    {
        let state = app.music_notification().get_state()?;
        let session = app.music_notification().get_playback_session()?;
        
        let event = PlaybackEvent {
            action: "prev".to_string(),
            current_index: session.queue.current_index,
            is_playing: state.is_playing,
            track_id: session.current_song_id,
        };
        app.emit("onPrev", event).ok();
    }
    Ok(result)
}

#[command]
pub(crate) async fn seek<R: Runtime>(app: AppHandle<R>, position: i64) -> Result<EmptyResponse> {
    app.music_notification().seek(position)
}

#[command]
pub(crate) async fn seek_and_play<R: Runtime>(
    app: AppHandle<R>,
    position: i64,
) -> Result<EmptyResponse> {
    app.music_notification().seek_and_play(position)
}

#[command]
pub(crate) async fn get_state<R: Runtime>(app: AppHandle<R>) -> Result<PlaybackState> {
    app.music_notification().get_state()
}

#[command]
pub(crate) async fn set_playing_queue<R: Runtime>(
    app: AppHandle<R>,
    payload: SetPlayingQueueRequest,
) -> Result<QueueMutationResponse> {
    app.music_notification().set_playing_queue(payload)
}

#[command]
pub(crate) async fn get_playback_session<R: Runtime>(
    app: AppHandle<R>,
) -> Result<PlaybackSession> {
    app.music_notification().get_playback_session()
}

#[command]
pub(crate) async fn clear_playing_queue<R: Runtime>(
    app: AppHandle<R>,
) -> Result<QueueMutationResponse> {
    app.music_notification().clear_playing_queue()
}

#[command]
pub(crate) async fn set_play_mode<R: Runtime>(
    app: AppHandle<R>,
    payload: SetPlayModeRequest,
) -> Result<QueueMutationResponse> {
    app.music_notification().set_play_mode(payload)
}

#[command]
pub(crate) async fn start_service<R: Runtime>(app: AppHandle<R>) -> Result<EmptyResponse> {
    app.music_notification().start_service()
}

#[command]
pub(crate) async fn stop_service<R: Runtime>(app: AppHandle<R>) -> Result<EmptyResponse> {
    app.music_notification().stop_service()
}

#[command]
pub(crate) async fn set_volume<R: Runtime>(
    app: AppHandle<R>,
    payload: SetVolumeRequest,
) -> Result<SetVolumeResponse> {
    app.music_notification().set_volume(payload)
}

#[command]
pub(crate) async fn set_normalization_config<R: Runtime>(
    app: AppHandle<R>,
    payload: SetNormalizationConfigRequest,
) -> Result<SetNormalizationConfigResponse> {
    app.music_notification().set_normalization_config(payload)
}

#[command]
pub(crate) async fn set_server<R: Runtime>(
    app: AppHandle<R>,
    library_name: String,
) -> Result<SetServerResponse> {
    app.music_notification().set_server(library_name)
}

#[command(rename = "registerListener")]
pub(crate) fn register_listener() {
    // Handshake
}

#[command(rename = "removeListener")]
pub(crate) fn remove_listener() {
    // Handshake
}