package com.plugin.music_notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
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
import org.json.JSONArray
import org.json.JSONObject

class MusicPlayerService : Service() {

    companion object {
        private const val TAG = "MusicPlayerService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "MusicPlayerChannel"
        private const val PREFS_NAME = "music_notification"
        private const val PREF_SESSION = "playback_session"

        const val ACTION_PLAY = "com.plugin.music_notification.PLAY"
        const val ACTION_PAUSE = "com.plugin.music_notification.PAUSE"
        const val ACTION_RESUME = "com.plugin.music_notification.RESUME"
        const val ACTION_STOP = "com.plugin.music_notification.STOP"
        const val ACTION_NEXT = "com.plugin.music_notification.NEXT"
        const val ACTION_PREVIOUS = "com.plugin.music_notification.PREVIOUS"
        const val ACTION_SEEK = "com.plugin.music_notification.SEEK"
        const val ACTION_START_SERVICE = "com.plugin.music_notification.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.plugin.music_notification.STOP_SERVICE"
        const val ACTION_SET_VOLUME = "com.plugin.music_notification.SET_VOLUME"

        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_ALBUM = "album"
        const val EXTRA_POSITION = "position"
        const val EXTRA_VOLUME = "volume"

        var instance: MusicPlayerService? = null

        fun loadServerLibrary(context: Context): String? {
            val libName = MusicNotificationPlugin.getServerLibName(context)
                ?: "musicnotification_lib"

            try {
                System.loadLibrary(libName)
                return libName
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load server library $libName", e)
                try {
                    val appClassLoader = context.applicationContext.classLoader
                    val loadLibraryMethod = Class.forName("java.lang.System").getDeclaredMethod(
                        "loadLibrary",
                        String::class.java,
                        ClassLoader::class.java
                    )
                    loadLibraryMethod.invoke(null, libName, appClassLoader)
                    return libName
                } catch (ex: Exception) {
                    Log.e(TAG, "Failed to load server library $libName via classloader", ex)
                    return null
                }
            }
        }

        fun emptySessionSnapshot(): SessionSnapshot {
            return SessionSnapshot(
                queue = PlayingQueueSnapshot(emptyList(), null),
                runtime = PlaybackRuntimeSnapshot(false, 0L, 0L),
                playMode = "sequential"
            )
        }

        fun loadPersistedSessionSnapshot(context: Context): SessionSnapshot {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(PREF_SESSION, null) ?: return emptySessionSnapshot()

            return try {
                parseSessionJson(JSONObject(raw))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse persisted playback session", e)
                emptySessionSnapshot()
            }
        }

        fun savePersistedSessionSnapshot(context: Context, snapshot: SessionSnapshot) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(PREF_SESSION, snapshotToJson(snapshot).toString())
                .apply()
        }

        private fun parseSessionJson(json: JSONObject): SessionSnapshot {
            val queueJson = json.optJSONObject("queue") ?: JSONObject()
            val songsJson = queueJson.optJSONArray("songs") ?: JSONArray()
            val songs = mutableListOf<QueueSongInfo>()
            for (i in 0 until songsJson.length()) {
                val songJson = songsJson.optJSONObject(i) ?: continue
                songs.add(
                    QueueSongInfo(
                        id = songJson.optLong("id", -1L),
                        name = songJson.optString("name", ""),
                        path = songJson.optString("path", ""),
                        url = songJson.optString("url", ""),
                        lufs = if (songJson.has("lufs") && !songJson.isNull("lufs")) songJson.optDouble("lufs") else null
                    )
                )
            }

            val runtimeJson = json.optJSONObject("runtime") ?: JSONObject()
            val currentIndex = if (queueJson.has("currentIndex") && !queueJson.isNull("currentIndex")) {
                queueJson.optInt("currentIndex")
            } else {
                null
            }

            return SessionSnapshot(
                queue = PlayingQueueSnapshot(songs, currentIndex),
                runtime = PlaybackRuntimeSnapshot(
                    isPlaying = runtimeJson.optBoolean("isPlaying", false),
                    positionMs = runtimeJson.optLong("positionMs", 0L),
                    durationMs = runtimeJson.optLong("durationMs", 0L)
                ),
                playMode = json.optString("playMode", "sequential")
            )
        }

        private fun snapshotToJson(snapshot: SessionSnapshot): JSONObject {
            val queueJson = JSONObject()
            val songsJson = JSONArray()

            snapshot.queue.songs.forEach { song ->
                val songJson = JSONObject()
                songJson.put("id", song.id)
                songJson.put("name", song.name)
                songJson.put("path", song.path)
                songJson.put("url", song.url)
                if (song.lufs == null) {
                    songJson.put("lufs", JSONObject.NULL)
                } else {
                    songJson.put("lufs", song.lufs)
                }
                songsJson.put(songJson)
            }

            queueJson.put("songs", songsJson)
            if (snapshot.queue.currentIndex == null) {
                queueJson.put("currentIndex", JSONObject.NULL)
            } else {
                queueJson.put("currentIndex", snapshot.queue.currentIndex)
            }

            val runtimeJson = JSONObject()
            runtimeJson.put("isPlaying", snapshot.runtime.isPlaying)
            runtimeJson.put("positionMs", snapshot.runtime.positionMs)
            runtimeJson.put("durationMs", snapshot.runtime.durationMs)

            val json = JSONObject()
            json.put("queue", queueJson)
            json.put("runtime", runtimeJson)
            json.put("playMode", snapshot.playMode)
            return json
        }
    }

    data class QueueSongInfo(
        val id: Long,
        val name: String,
        val path: String,
        val url: String,
        val lufs: Double?
    )

    data class PlayingQueueSnapshot(
        val songs: List<QueueSongInfo>,
        val currentIndex: Int?
    )

    data class PlaybackRuntimeSnapshot(
        val isPlaying: Boolean,
        val positionMs: Long,
        val durationMs: Long
    )

    data class SessionSnapshot(
        val queue: PlayingQueueSnapshot,
        val runtime: PlaybackRuntimeSnapshot,
        val playMode: String
    )

    private external fun serverStart(): Int
    private external fun serverStop(): Int

    private var httpServerRunning = false
    private var musicPlayerActive = false
    private var tracks = mutableListOf<QueueSongInfo>()
    private var currentTrackIndex = -1
    private var playMode = "sequential"

    private var mediaPlayer: MediaPlayer? = null
    private var isPrepared = false
    private lateinit var handler: Handler
    private lateinit var progressRunnable: Runnable
    private lateinit var mediaSession: MediaSessionCompat
    private var currentUrl: String? = null
    private var startCommandCount = 0L
    private var playbackGeneration = 0L

    private fun updateServiceLifetime() {
        if (!httpServerRunning && !musicPlayerActive) {
            Log.d(TAG, "Both server and player done, stopping service")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        handler = Handler(Looper.getMainLooper())
        mediaSession = MediaSessionCompat(this, "MusicPlayerService")
        restorePersistedSession()

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
                stopMusic(clearQueue = false)
            }

            override fun onSeekTo(pos: Long) {
                Log.d(TAG, "MediaSession callback: onSeekTo $pos")
                mediaPlayer?.seekTo(pos.toInt())
                persistSession(isPlayingOverride = mediaPlayer?.isPlaying)
                updatePlaybackState()
            }
        })

        mediaSession.isActive = true

        progressRunnable = object : Runnable {
            override fun run() {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        persistSession(isPlayingOverride = true)
                        updatePlaybackState()
                        updateNotification()
                        handler.postDelayed(this, 1000)
                    }
                }
            }
        }

        val loadedLib = loadServerLibrary(this)
        if (loadedLib == null) {
            Log.e(TAG, "No server library could be loaded, JNI call will likely fail")
        }

        val result = try {
            serverStart()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "serverStart() JNI symbol missing", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "serverStart() threw unexpected exception", e)
            throw e
        }
        if (result == 0) {
            httpServerRunning = true
        } else {
            Log.e(TAG, "Failed to start server, code: $result")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startCommandCount += 1
        Log.d(TAG, "========== onStartCommand ==========")
        Log.d(
            TAG,
            "onStartCommand#${startCommandCount}: startId=$startId flags=$flags action=${intent?.action}"
        )

        if (intent?.action == Intent.ACTION_MEDIA_BUTTON) {
            Log.d(TAG, "onStartCommand#${startCommandCount}: forwarding ACTION_MEDIA_BUTTON to MediaButtonReceiver")
            MediaButtonReceiver.handleIntent(mediaSession, intent)
        }

        intent?.let {
            when (it.action) {
                ACTION_START_SERVICE -> {
                    Log.d(TAG, "Action: START_SERVICE")
                }
                ACTION_STOP_SERVICE -> {
                    Log.d(TAG, "Action: STOP_SERVICE")
                    val result = serverStop()
                    if (result == 0) {
                        Log.d(TAG, "Server stopped successfully")
                    } else {
                        Log.e(TAG, "Failed to stop server, code: $result")
                    }
                    httpServerRunning = false
                    updateServiceLifetime()
                }
                ACTION_PLAY -> {
                    Log.d(TAG, "Action: PLAY")
                    val url = it.getStringExtra(EXTRA_URL) ?: run {
                        Log.e(TAG, "URL is null, returning")
                        return START_STICKY
                    }
                    val title = it.getStringExtra(EXTRA_TITLE) ?: "Unknown Title"
                    val artist = it.getStringExtra(EXTRA_ARTIST) ?: "Unknown Artist"
                    val album = it.getStringExtra(EXTRA_ALBUM) ?: "Unknown Album"
                    playTrackByUrl(url, title, artist, album)
                }
                ACTION_PAUSE -> pauseMusic()
                ACTION_RESUME -> resumeMusic()
                ACTION_STOP -> stopMusic(clearQueue = false)
                ACTION_NEXT -> {
                    Log.d(
                        TAG,
                        "onStartCommand#${startCommandCount}: handling ACTION_NEXT currentTrackIndex=$currentTrackIndex queueSize=${tracks.size}"
                    )
                    playNextTrack()
                }
                ACTION_PREVIOUS -> {
                    Log.d(
                        TAG,
                        "onStartCommand#${startCommandCount}: handling ACTION_PREVIOUS currentTrackIndex=$currentTrackIndex queueSize=${tracks.size}"
                    )
                    playPreviousTrack()
                }
                ACTION_SEEK -> {
                    val position = it.getLongExtra(EXTRA_POSITION, 0)
                    Log.d(TAG, "onStartCommand#${startCommandCount}: ACTION_SEEK to $position")
                    mediaPlayer?.seekTo(position.toInt())
                    persistSession(isPlayingOverride = mediaPlayer?.isPlaying)
                    updatePlaybackState()
                }
                ACTION_SET_VOLUME -> {
                    val volume = it.getFloatExtra(EXTRA_VOLUME, 1.0f)
                    Log.d(TAG, "Action: SET_VOLUME to $volume")
                    mediaPlayer?.setVolume(volume, volume)
                }
                else -> Log.w(TAG, "Unknown action: ${it.action}")
            }
        } ?: Log.w(TAG, "Intent is null")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        return START_STICKY
    }

    fun setPlayingQueue(songs: List<QueueSongInfo>, currentIndex: Int?, newPlayMode: String) {
        tracks = songs.toMutableList()
        currentTrackIndex = when {
            tracks.isEmpty() -> -1
            currentIndex == null -> 0
            currentIndex < 0 -> 0
            currentIndex >= tracks.size -> tracks.lastIndex
            else -> currentIndex
        }
        playMode = normalizePlayMode(newPlayMode)
        if (currentTrackIndex in tracks.indices) {
            currentUrl = tracks[currentTrackIndex].url
        }
        persistSession(isPlayingOverride = mediaPlayer?.isPlaying)
        updateNotification()
    }

    fun setPlayMode(newPlayMode: String) {
        playMode = normalizePlayMode(newPlayMode)
        persistSession(isPlayingOverride = mediaPlayer?.isPlaying)
    }

    fun clearPlayingQueue() {
        stopMusic(clearQueue = true)
    }

    fun getPlaybackSession(): SessionSnapshot {
        return buildSessionSnapshot()
    }

    fun getPlaybackState(): Triple<Boolean, Long, Long> {
        val runtime = buildRuntimeSnapshot()
        return Triple(runtime.isPlaying, runtime.positionMs, runtime.durationMs)
    }

    private fun restorePersistedSession() {
        val snapshot = loadPersistedSessionSnapshot(this)
        tracks = snapshot.queue.songs.toMutableList()
        currentTrackIndex = when {
            tracks.isEmpty() -> -1
            snapshot.queue.currentIndex == null -> 0
            snapshot.queue.currentIndex < 0 -> 0
            snapshot.queue.currentIndex >= tracks.size -> tracks.lastIndex
            else -> snapshot.queue.currentIndex
        }
        playMode = normalizePlayMode(snapshot.playMode)
        currentUrl = if (currentTrackIndex in tracks.indices) tracks[currentTrackIndex].url else null
        musicPlayerActive = snapshot.runtime.isPlaying
    }

    private fun buildSessionSnapshot(): SessionSnapshot {
        val currentIndex = if (currentTrackIndex in tracks.indices) currentTrackIndex else null
        return SessionSnapshot(
            queue = PlayingQueueSnapshot(tracks.toList(), currentIndex),
            runtime = buildRuntimeSnapshot(),
            playMode = playMode
        )
    }

    private fun buildRuntimeSnapshot(): PlaybackRuntimeSnapshot {
        mediaPlayer?.let { player ->
            return PlaybackRuntimeSnapshot(
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.toLong(),
                durationMs = player.duration.toLong()
            )
        }

        val persisted = loadPersistedSessionSnapshot(this)
        return PlaybackRuntimeSnapshot(
            isPlaying = false,
            positionMs = persisted.runtime.positionMs,
            durationMs = persisted.runtime.durationMs
        )
    }

    private fun persistSession(isPlayingOverride: Boolean? = null) {
        val runtime = mediaPlayer?.let { player ->
            PlaybackRuntimeSnapshot(
                isPlaying = isPlayingOverride ?: player.isPlaying,
                positionMs = player.currentPosition.toLong(),
                durationMs = player.duration.toLong()
            )
        } ?: PlaybackRuntimeSnapshot(
            isPlaying = isPlayingOverride ?: false,
            positionMs = 0L,
            durationMs = 0L
        )

        savePersistedSessionSnapshot(
            this,
            SessionSnapshot(
                queue = PlayingQueueSnapshot(
                    songs = tracks.toList(),
                    currentIndex = if (currentTrackIndex in tracks.indices) currentTrackIndex else null
                ),
                runtime = runtime,
                playMode = playMode
            )
        )
    }

    private fun normalizePlayMode(rawMode: String?): String {
        return when (rawMode) {
            "shuffle" -> "shuffle"
            "loop" -> "loop"
            else -> "sequential"
        }
    }

    private fun playTrackByUrl(url: String, title: String, artist: String, album: String) {
        val queueIndex = tracks.indexOfFirst { it.url == url }
        if (queueIndex >= 0) {
            Log.d(TAG, "playTrackByUrl: matched queueIndex=$queueIndex title=${tracks[queueIndex].name}")
            currentTrackIndex = queueIndex
            val track = tracks[queueIndex]
            playTrack(track, artist, album)
            return
        }

        val fallbackTrack = QueueSongInfo(
            id = -1L,
            name = title,
            path = "",
            url = url,
            lufs = null
        )
        tracks = mutableListOf(fallbackTrack)
        currentTrackIndex = 0
        playMode = "sequential"
        playTrack(fallbackTrack, artist, album)
    }

    private fun playTrack(track: QueueSongInfo, artist: String = "Unknown Artist", album: String = "Unknown Album") {
        Log.d(TAG, "========== playTrack called ==========")
        Log.d(TAG, "Track: ${track.name} (${track.url}) currentTrackIndex=$currentTrackIndex queueSize=${tracks.size}")

        musicPlayerActive = true

        if (currentUrl == track.url && mediaPlayer != null && isPrepared) {
            Log.d(TAG, "Same URL already prepared, resuming")
            resumeMusic()
            return
        }

        mediaPlayer?.let { player ->
            try {
                player.setOnPreparedListener(null)
                player.setOnErrorListener(null)
                player.setOnCompletionListener(null)
                if (isPrepared || player.isPlaying) {
                    player.stop()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping MediaPlayer", e)
            }
            try {
                player.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing MediaPlayer", e)
            }
        }

        mediaPlayer = null
        isPrepared = false
        currentUrl = track.url
        playbackGeneration += 1
        val generation = playbackGeneration
        persistSession(isPlayingOverride = false)

        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(track.url)

                setOnPreparedListener { mp ->
                    if (mediaPlayer !== this || playbackGeneration != generation || currentUrl != track.url) {
                        Log.d(
                            TAG,
                            "Ignoring stale onPrepared for generation=$generation currentGeneration=$playbackGeneration track=${track.name}"
                        )
                        return@setOnPreparedListener
                    }
                    Log.d(TAG, "========== onPrepared called ==========")
                    isPrepared = true
                    mediaSession.setMetadata(
                        MediaMetadataCompat.Builder()
                            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.name)
                            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mp.duration.toLong())
                            .build()
                    )
                    persistSession(isPlayingOverride = false)
                    resumeMusic()
                }

                setOnErrorListener { _, what, extra ->
                    if (mediaPlayer !== this || playbackGeneration != generation || currentUrl != track.url) {
                        Log.d(
                            TAG,
                            "Ignoring stale onError for generation=$generation currentGeneration=$playbackGeneration what=$what extra=$extra"
                        )
                        return@setOnErrorListener true
                    }
                    Log.e(TAG, "MediaPlayer error - what: $what, extra: $extra")
                    isPrepared = false
                    persistSession(isPlayingOverride = false)
                    true
                }

                setOnCompletionListener {
                    if (mediaPlayer !== this || playbackGeneration != generation || currentUrl != track.url) {
                        Log.d(
                            TAG,
                            "Ignoring stale onCompletion for generation=$generation currentGeneration=$playbackGeneration track=${track.name}"
                        )
                        return@setOnCompletionListener
                    }
                    Log.d(TAG, "========== onCompletion called ==========")
                    playNextTrack()
                }

                prepareAsync()
            } catch (e: Exception) {
                Log.e(TAG, "Exception in playTrack", e)
                isPrepared = false
                persistSession(isPlayingOverride = false)
            }
        }
    }

    private fun resumeMusic() {
        mediaPlayer?.let {
            if (isPrepared && !it.isPlaying) {
                it.start()
                handler.post(progressRunnable)
                persistSession(isPlayingOverride = true)
                updatePlaybackState()
                updateNotification()
            }
        } ?: Log.w(TAG, "MediaPlayer is null")
    }

    private fun pauseMusic() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                handler.removeCallbacks(progressRunnable)
                persistSession(isPlayingOverride = false)
                updatePlaybackState()
                updateNotification()
            }
        } ?: Log.w(TAG, "MediaPlayer is null")
    }

    private fun stopMusic(clearQueue: Boolean) {
        handler.removeCallbacks(progressRunnable)
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping media player", e)
            }
            try {
                player.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing media player", e)
            }
        }
        mediaPlayer = null
        isPrepared = false
        currentUrl = null
        musicPlayerActive = false

        if (clearQueue) {
            tracks = mutableListOf()
            currentTrackIndex = -1
        }

        persistSession(isPlayingOverride = false)
        updatePlaybackState()
        updateNotification()
        updateServiceLifetime()
    }

    private fun playNextTrack() {
        if (tracks.isEmpty()) {
            Log.w(TAG, "No tracks in queue")
            return
        }

        val previousIndex = currentTrackIndex
        if (playMode == "loop" && currentTrackIndex in tracks.indices) {
            Log.d(
                TAG,
                "playNextTrack: loop mode replaying currentTrackIndex=$currentTrackIndex track=${tracks[currentTrackIndex].name}"
            )
            playTrack(tracks[currentTrackIndex])
            return
        }

        currentTrackIndex = if (currentTrackIndex in tracks.indices) {
            (currentTrackIndex + 1) % tracks.size
        } else {
            0
        }
        Log.d(
            TAG,
            "playNextTrack: previousIndex=$previousIndex newIndex=$currentTrackIndex playMode=$playMode nextTrack=${tracks[currentTrackIndex].name}"
        )
        playTrack(tracks[currentTrackIndex])
    }

    private fun playPreviousTrack() {
        if (tracks.isEmpty()) {
            Log.w(TAG, "No tracks in queue")
            return
        }

        val previousIndex = currentTrackIndex
        if (playMode == "loop" && currentTrackIndex in tracks.indices) {
            Log.d(
                TAG,
                "playPreviousTrack: loop mode replaying currentTrackIndex=$currentTrackIndex track=${tracks[currentTrackIndex].name}"
            )
            playTrack(tracks[currentTrackIndex])
            return
        }

        currentTrackIndex = if (currentTrackIndex in tracks.indices) {
            if (currentTrackIndex == 0) tracks.lastIndex else currentTrackIndex - 1
        } else {
            0
        }
        Log.d(
            TAG,
            "playPreviousTrack: previousIndex=$previousIndex newIndex=$currentTrackIndex playMode=$playMode previousTrack=${tracks[currentTrackIndex].name}"
        )
        playTrack(tracks[currentTrackIndex])
    }

    private fun createNotification(): Notification {
        val controller = mediaSession.controller
        val mediaMetadata = controller.metadata
        val description = mediaMetadata?.description
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)

        val isPlaying = mediaPlayer?.isPlaying == true
        val title = if (musicPlayerActive && mediaPlayer != null) {
            description?.title ?: "Music Player"
        } else {
            "No song is playing"
        }

        val playPauseAction = NotificationCompat.Action(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) "Pause" else "Play",
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE)
        )

        val previousAction = NotificationCompat.Action(
            android.R.drawable.ic_media_previous,
            "Previous",
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
        )

        val nextAction = NotificationCompat.Action(
            android.R.drawable.ic_media_next,
            "Next",
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
        )

        builder.setContentTitle(title)
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
        } ?: run {
            val playbackState = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(PlaybackStateCompat.STATE_STOPPED, 0L, 0f)
                .build()
            mediaSession.setPlaybackState(playbackState)
        }
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
                Log.e(TAG, "Error stopping in onDestroy", e)
            }
            try {
                release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing in onDestroy", e)
            }
        }
        mediaPlayer = null
        persistSession(isPlayingOverride = false)
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
