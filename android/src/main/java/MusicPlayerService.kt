package com.plugin.music_notification

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import androidx.media.session.MediaButtonReceiver

class MusicPlayerService : Service() {

    companion object {
        private const val TAG = "MusicPlayerService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "MusicPlayerChannel"
        const val ACTION_PLAY = "com.plugin.music_notification.PLAY"
        const val ACTION_PAUSE = "com.plugin.music_notification.PAUSE"
        const val ACTION_RESUME = "com.plugin.music_notification.RESUME"
        const val ACTION_STOP = "com.plugin.music_notification.STOP"
        const val ACTION_NEXT = "com.plugin.music_notification.NEXT"
        const val ACTION_PREVIOUS = "com.plugin.music_notification.PREVIOUS"
        const val ACTION_SEEK = "com.plugin.music_notification.SEEK"
        
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_ALBUM = "album"
        const val EXTRA_POSITION = "position"

        var instance: MusicPlayerService? = null
        private val tracks = mutableListOf<TrackInfo>()
        private var currentTrackIndex = 0
    }

    data class TrackInfo(
        val url: String,
        val title: String,
        val artist: String,
        val album: String
    )

    private var mediaPlayer: MediaPlayer? = null
    private var isPrepared = false
    private lateinit var handler: Handler
    private lateinit var progressRunnable: Runnable
    private lateinit var mediaSession: MediaSessionCompat
    private var currentUrl: String? = null

    override fun onCreate() {
        Log.d(TAG, "========== onCreate ==========")
        super.onCreate()
        instance = this
        handler = Handler(Looper.getMainLooper())
        mediaSession = MediaSessionCompat(this, "MusicPlayerService")
        Log.d(TAG, "MediaSession created")

        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                Log.d(TAG, "MediaSession callback: onPlay")
                resumeMusic()
            }

            override fun onPause() {
                Log.d(TAG, "MediaSession callback: onPause")
                pauseMusic()
            }

            override fun onSkipToNext() {
                Log.d(TAG, "MediaSession callback: onSkipToNext")
                playNextTrack()
            }

            override fun onSkipToPrevious() {
                Log.d(TAG, "MediaSession callback: onSkipToPrevious")
                playPreviousTrack()
            }

            override fun onStop() {
                Log.d(TAG, "MediaSession callback: onStop")
                stopMusic()
            }

            override fun onSeekTo(pos: Long) {
                Log.d(TAG, "MediaSession callback: onSeekTo $pos")
                mediaPlayer?.seekTo(pos.toInt())
                updatePlaybackState()
            }
        })

        mediaSession.isActive = true
        Log.d(TAG, "MediaSession activated")

        progressRunnable = object : Runnable {
            override fun run() {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        updatePlaybackState()
                        updateNotification()
                        handler.postDelayed(this, 1000)
                    }
                }
            }
        }
        Log.d(TAG, "onCreate complete")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "========== onStartCommand ==========")
        Log.d(TAG, "Action: ${intent?.action}")

        MediaButtonReceiver.handleIntent(mediaSession, intent)

        intent?.let {
            when (it.action) {
                ACTION_PLAY -> {
                    Log.d(TAG, "Action: PLAY")
                    val url = it.getStringExtra(EXTRA_URL) ?: run {
                        Log.e(TAG, "URL is null, returning")
                        return START_STICKY
                    }
                    val title = it.getStringExtra(EXTRA_TITLE) ?: "Unknown Title"
                    val artist = it.getStringExtra(EXTRA_ARTIST) ?: "Unknown Artist"
                    val album = it.getStringExtra(EXTRA_ALBUM) ?: "Unknown Album"
                    playMusic(url, title, artist, album)
                }
                ACTION_PAUSE -> {
                    Log.d(TAG, "Action: PAUSE")
                    pauseMusic()
                }
                ACTION_RESUME -> {
                    Log.d(TAG, "Action: RESUME")
                    resumeMusic()
                }
                ACTION_STOP -> {
                    Log.d(TAG, "Action: STOP")
                    stopMusic()
                }
                ACTION_NEXT -> {
                    Log.d(TAG, "Action: NEXT")
                    playNextTrack()
                }
                ACTION_PREVIOUS -> {
                    Log.d(TAG, "Action: PREVIOUS")
                    playPreviousTrack()
                }
                ACTION_SEEK -> {
                    val position = it.getLongExtra(EXTRA_POSITION, 0)
                    Log.d(TAG, "Action: SEEK to $position")
                    mediaPlayer?.seekTo(position.toInt())
                    updatePlaybackState()
                }
                else -> Log.w(TAG, "Unknown action: ${it.action}")
            }
        } ?: Log.w(TAG, "Intent is null")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        return START_STICKY
    }

    fun addTrack(url: String, title: String, artist: String, album: String) {
        tracks.add(TrackInfo(url, title, artist, album))
    }

    private fun playMusic(url: String, title: String, artist: String, album: String) {
        Log.d(TAG, "========== playMusic called ==========")
        Log.d(TAG, "URL: $url")
        Log.d(TAG, "Title: $title, Artist: $artist, Album: $album")
        Log.d(TAG, "Current state - mediaPlayer: ${mediaPlayer != null}, isPrepared: $isPrepared, currentUrl: $currentUrl")

        if (currentUrl == url && mediaPlayer != null && isPrepared) {
            Log.d(TAG, "Same URL already playing, resuming...")
            resumeMusic()
            return
        }

        Log.d(TAG, "Cleaning up existing MediaPlayer...")
        mediaPlayer?.let { player ->
            try {
                Log.d(TAG, "Existing player state - isPlaying: ${player.isPlaying}")
                if (isPrepared || player.isPlaying) {
                    Log.d(TAG, "Stopping MediaPlayer...")
                    player.stop()
                    Log.d(TAG, "MediaPlayer stopped")
                } else {
                    Log.d(TAG, "MediaPlayer not playing, skipping stop")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping MediaPlayer: ${e.message}", e)
            }
            try {
                Log.d(TAG, "Releasing MediaPlayer...")
                player.release()
                Log.d(TAG, "MediaPlayer released")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing MediaPlayer: ${e.message}", e)
            }
        }
        mediaPlayer = null
        isPrepared = false
        currentUrl = url
        Log.d(TAG, "State reset complete, creating new MediaPlayer...")

        mediaPlayer = MediaPlayer().apply {
            try {
                Log.d(TAG, "Setting data source: $url")
                setDataSource(url)
                Log.d(TAG, "Data source set successfully")

                setOnPreparedListener { mp ->
                    Log.d(TAG, "========== onPrepared called ==========")
                    Log.d(TAG, "Duration: ${mp.duration}ms")
                    isPrepared = true
                    Log.d(TAG, "isPrepared set to true")

                    mediaSession.setMetadata(
                        MediaMetadataCompat.Builder()
                            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mp.duration.toLong())
                            .build()
                    )
                    Log.d(TAG, "Metadata set, calling resumeMusic()...")
                    resumeMusic()
                }

                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "========== onError called ==========")
                    Log.e(TAG, "MediaPlayer error - what: $what, extra: $extra")
                    isPrepared = false
                    false
                }

                setOnCompletionListener {
                    Log.d(TAG, "========== onCompletion called ==========")
                    playNextTrack()
                }

                Log.d(TAG, "Calling prepareAsync()...")
                prepareAsync()
                Log.d(TAG, "prepareAsync() called, waiting for onPrepared...")

            } catch (e: Exception) {
                Log.e(TAG, "========== Exception in playMusic ==========")
                Log.e(TAG, "Error: ${e.message}", e)
                e.printStackTrace()
                isPrepared = false
            }
        }
    }

    private fun resumeMusic() {
        Log.d(TAG, "========== resumeMusic called ==========")
        mediaPlayer?.let {
            Log.d(TAG, "MediaPlayer exists, isPrepared: $isPrepared, isPlaying: ${it.isPlaying}")
            if (isPrepared && !it.isPlaying) {
                Log.d(TAG, "Starting playback...")
                it.start()
                handler.post(progressRunnable)
                updatePlaybackState()
                updateNotification()
                Log.d(TAG, "Playback started")
            } else if (!isPrepared) {
                Log.w(TAG, "Cannot resume - not prepared yet")
            } else {
                Log.w(TAG, "Already playing")
            }
        } ?: Log.w(TAG, "MediaPlayer is null")
    }

    private fun pauseMusic() {
        Log.d(TAG, "========== pauseMusic called ==========")
        mediaPlayer?.let {
            if (it.isPlaying) {
                Log.d(TAG, "Pausing playback...")
                it.pause()
                handler.removeCallbacks(progressRunnable)
                updatePlaybackState()
                updateNotification()
                Log.d(TAG, "Playback paused")
            } else {
                Log.w(TAG, "Not playing, nothing to pause")
            }
        } ?: Log.w(TAG, "MediaPlayer is null")
    }

    private fun stopMusic() {
        Log.d(TAG, "========== stopMusic called ==========")
        handler.removeCallbacks(progressRunnable)
        mediaPlayer?.let { player ->
            try {
                Log.d(TAG, "isPlaying: ${player.isPlaying}")
                if (player.isPlaying) {
                    Log.d(TAG, "Stopping MediaPlayer...")
                    player.stop()
                    Log.d(TAG, "MediaPlayer stopped")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping: ${e.message}", e)
            }
            try {
                Log.d(TAG, "Releasing MediaPlayer...")
                player.release()
                Log.d(TAG, "MediaPlayer released")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing: ${e.message}", e)
            }
        }
        mediaPlayer = null
        isPrepared = false
        currentUrl = null
        Log.d(TAG, "State reset, stopping service")
        updatePlaybackState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun playNextTrack() {
        Log.d(TAG, "========== playNextTrack ==========")
        if (tracks.isEmpty()) {
            Log.w(TAG, "No tracks in playlist")
            return
        }
        currentTrackIndex = (currentTrackIndex + 1) % tracks.size
        Log.d(TAG, "Moving to track $currentTrackIndex of ${tracks.size}")
        val track = tracks[currentTrackIndex]
        playMusic(track.url, track.title, track.artist, track.album)
    }

    private fun playPreviousTrack() {
        Log.d(TAG, "========== playPreviousTrack ==========")
        if (tracks.isEmpty()) {
            Log.w(TAG, "No tracks in playlist")
            return
        }
        currentTrackIndex = if (currentTrackIndex - 1 < 0) tracks.size - 1 else currentTrackIndex - 1
        Log.d(TAG, "Moving to track $currentTrackIndex of ${tracks.size}")
        val track = tracks[currentTrackIndex]
        playMusic(track.url, track.title, track.artist, track.album)
    }

    private fun createNotification(): Notification {
        val controller = mediaSession.controller
        val mediaMetadata = controller.metadata
        val description = mediaMetadata?.description

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)

        val isPlaying = mediaPlayer?.isPlaying == true

        val playPauseAction = NotificationCompat.Action(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) "Pausar" else "Reproducir",
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE)
        )

        val previousAction = NotificationCompat.Action(
            android.R.drawable.ic_media_previous, "Anterior",
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
        )

        val nextAction = NotificationCompat.Action(
            android.R.drawable.ic_media_next, "Siguiente",
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
        )

        builder.setContentTitle(description?.title ?: "Music Player")
            .setContentText(description?.subtitle ?: "")
            .setSubText(description?.description ?: "")
            .setContentIntent(controller.sessionActivity)
            .setDeleteIntent(
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP)
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(isPlaying)
            .addAction(previousAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        return builder.build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun updatePlaybackState() {
        mediaPlayer?.let {
            val isPlaying = it.isPlaying
            val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
            val position = it.currentPosition.toLong()
            val duration = it.duration.toLong()

            Log.d(TAG, "updatePlaybackState: isPlaying=$isPlaying, position=${position}ms, duration=${duration}ms")

            val playbackState = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(state, position, 1.0f)
                .build()
            mediaSession.setPlaybackState(playbackState)
        } ?: Log.d(TAG, "updatePlaybackState: mediaPlayer is null")
    }

    fun getPlaybackState(): Triple<Boolean, Long, Long> {
        mediaPlayer?.let {
            val state = Triple(it.isPlaying, it.currentPosition.toLong(), it.duration.toLong())
            Log.d(TAG, "getPlaybackState: ${state.first}, ${state.second}ms, ${state.third}ms")
            return state
        }
        Log.d(TAG, "getPlaybackState: mediaPlayer is null, returning defaults")
        return Triple(false, 0L, 0L)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "========== onDestroy ==========")
        super.onDestroy()
        instance = null
        handler.removeCallbacks(progressRunnable)
        mediaSession.release()
        mediaPlayer?.apply {
            try {
                if (isPlaying) stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping in onDestroy: ${e.message}")
            }
            try {
                release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing in onDestroy: ${e.message}")
            }
        }
        mediaPlayer = null
        Log.d(TAG, "onDestroy complete")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Player Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
