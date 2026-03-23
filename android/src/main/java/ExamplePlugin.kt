package com.plugin.music_notification

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import app.tauri.plugin.Invoke

@InvokeArg
class PingArgs {
  var value: String? = null
}

@InvokeArg
class PlayArgs {
  var url: String = ""
  var title: String? = null
  var artist: String? = null
  var album: String? = null
}

@InvokeArg
class SeekArgs {
  var position: Long = 0
}

@InvokeArg
class SetVolumeArgs {
  var volume: Float = 0.5f
}

@InvokeArg
class SetServerArgs {
  var libraryName: String? = null
}

@TauriPlugin
class MusicNotificationPlugin(private val activity: Activity): Plugin(activity) {

    companion object {
        private const val TAG = "MusicNotificationPlugin"
        private const val PREFS_NAME = "music_notification"
        private const val PREF_SERVER_LIB_NAME = "server_lib_name"

        fun getServerLibName(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(PREF_SERVER_LIB_NAME, null)
        }
    }

    @Command
    fun ping(invoke: Invoke) {
        val args = invoke.parseArgs(PingArgs::class.java)
        val ret = JSObject()
        ret.put("value", args.value ?: "pong from music notification plugin")
        invoke.resolve(ret)
    }

    @Command
    fun play(invoke: Invoke) {
        try {
            val args = invoke.parseArgs(PlayArgs::class.java)

            if (args.url.isEmpty()) {
                val ret = JSObject()
                ret.put("success", false)
                ret.put("message", "URL is required")
                invoke.resolve(ret)
                return
            }

            val serviceIntent = Intent(activity, MusicPlayerService::class.java).apply {
                action = MusicPlayerService.ACTION_PLAY
                putExtra(MusicPlayerService.EXTRA_URL, args.url)
                putExtra(MusicPlayerService.EXTRA_TITLE, args.title ?: "Unknown Title")
                putExtra(MusicPlayerService.EXTRA_ARTIST, args.artist ?: "Unknown Artist")
                putExtra(MusicPlayerService.EXTRA_ALBUM, args.album ?: "Unknown Album")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity.startForegroundService(serviceIntent)
            } else {
                activity.startService(serviceIntent)
            }

            val ret = JSObject()
            ret.put("success", true)
            ret.put("message", "Playing music")
            invoke.resolve(ret)
        } catch (e: Exception) {
            val ret = JSObject()
            ret.put("success", false)
            ret.put("message", e.message)
            invoke.resolve(ret)
        }
    }

    @Command
    fun pause(invoke: Invoke) {
        try {
            val serviceIntent = Intent(activity, MusicPlayerService::class.java).apply {
                action = MusicPlayerService.ACTION_PAUSE
            }
            activity.startService(serviceIntent)

            val ret = JSObject()
            ret.put("success", true)
            invoke.resolve(ret)
        } catch (e: Exception) {
            val ret = JSObject()
            ret.put("success", false)
            invoke.resolve(ret)
        }
    }

    @Command
    fun resume(invoke: Invoke) {
        try {
            val serviceIntent = Intent(activity, MusicPlayerService::class.java).apply {
                action = MusicPlayerService.ACTION_RESUME
            }
            activity.startService(serviceIntent)

            val ret = JSObject()
            ret.put("success", true)
            invoke.resolve(ret)
        } catch (e: Exception) {
            val ret = JSObject()
            ret.put("success", false)
            invoke.resolve(ret)
        }
    }

    @Command
    fun stop(invoke: Invoke) {
        try {
            val serviceIntent = Intent(activity, MusicPlayerService::class.java).apply {
                action = MusicPlayerService.ACTION_STOP
            }
            activity.startService(serviceIntent)

            val ret = JSObject()
            ret.put("success", true)
            invoke.resolve(ret)
        } catch (e: Exception) {
            val ret = JSObject()
            ret.put("success", false)
            invoke.resolve(ret)
        }
    }

    @Command
    fun next(invoke: Invoke) {
        try {
            val serviceIntent = Intent(activity, MusicPlayerService::class.java).apply {
                action = MusicPlayerService.ACTION_NEXT
            }
            activity.startService(serviceIntent)

            val ret = JSObject()
            ret.put("success", true)
            invoke.resolve(ret)
        } catch (e: Exception) {
            val ret = JSObject()
            ret.put("success", false)
            invoke.resolve(ret)
        }
    }

    @Command
    fun previous(invoke: Invoke) {
        try {
            val serviceIntent = Intent(activity, MusicPlayerService::class.java).apply {
                action = MusicPlayerService.ACTION_PREVIOUS
            }
            activity.startService(serviceIntent)

            val ret = JSObject()
            ret.put("success", true)
            invoke.resolve(ret)
        } catch (e: Exception) {
            val ret = JSObject()
            ret.put("success", false)
            invoke.resolve(ret)
        }
    }

    @Command
    fun seek(invoke: Invoke) {
        try {
            val args = invoke.parseArgs(SeekArgs::class.java)
            val serviceIntent = Intent(activity, MusicPlayerService::class.java).apply {
                action = MusicPlayerService.ACTION_SEEK
                putExtra(MusicPlayerService.EXTRA_POSITION, args.position)
            }
            activity.startService(serviceIntent)

            val ret = JSObject()
            ret.put("success", true)
            invoke.resolve(ret)
        } catch (e: Exception) {
            val ret = JSObject()
            ret.put("success", false)
            invoke.resolve(ret)
        }
    }

    @Command
    fun getState(invoke: Invoke) {
        try {
            val service = MusicPlayerService.instance
            val ret = JSObject()

            if (service != null) {
                val (isPlaying, position, duration) = service.getPlaybackState()
                ret.put("isPlaying", isPlaying)
                ret.put("position", position)
                ret.put("duration", duration)
            } else {
                ret.put("isPlaying", false)
                ret.put("position", 0)
                ret.put("duration", 0)
            }

            invoke.resolve(ret)
        } catch (e: Exception) {
            val ret = JSObject()
            ret.put("isPlaying", false)
            ret.put("position", 0)
            ret.put("duration", 0)
            invoke.resolve(ret)
        }
    }

    @Command
    fun startService(invoke: Invoke) {
        try {
            val serviceIntent = Intent(activity, MusicPlayerService::class.java).apply {
                action = MusicPlayerService.ACTION_START_SERVICE
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity.startForegroundService(serviceIntent)
            } else {
                activity.startService(serviceIntent)
            }

            val ret = JSObject()
            ret.put("success", true)
            ret.put("message", "Service started")
            invoke.resolve(ret)
        } catch (e: Exception) {
            val ret = JSObject()
            ret.put("success", false)
            ret.put("message", e.message)
            invoke.resolve(ret)
        }
    }

    @Command
    fun stopService(invoke: Invoke) {
        try {
            val serviceIntent = Intent(activity, MusicPlayerService::class.java).apply {
                action = MusicPlayerService.ACTION_STOP_SERVICE
            }
            activity.startService(serviceIntent)

            val ret = JSObject()
            ret.put("success", true)
            ret.put("message", "Service stop requested")
            invoke.resolve(ret)
        } catch (e: Exception) {
            val ret = JSObject()
            ret.put("success", false)
            ret.put("message", e.message)
            invoke.resolve(ret)
        }
    }

    @Command
    fun setVolume(invoke: Invoke) {
        try {
            val args = invoke.parseArgs(SetVolumeArgs::class.java)
            val serviceIntent = Intent(activity, MusicPlayerService::class.java).apply {
                action = MusicPlayerService.ACTION_SET_VOLUME
                putExtra(MusicPlayerService.EXTRA_VOLUME, args.volume)
            }
            activity.startService(serviceIntent)

            val ret = JSObject()
            ret.put("success", true)
            invoke.resolve(ret)
        } catch (e: Exception) {
            val ret = JSObject()
            ret.put("success", false)
            ret.put("message", e.message)
            invoke.resolve(ret)
        }
    }

    @Command
    fun setServer(invoke: Invoke) {
        try {
            val args = invoke.parseArgs(SetServerArgs::class.java)

            if (args.libraryName == null) {
                val ret = JSObject()
                ret.put("success", false)
                ret.put("message", "libraryName is required")
                invoke.resolve(ret)
                return
            }

            // Store the server library name in SharedPreferences
            val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(PREF_SERVER_LIB_NAME, args.libraryName)
                .apply()

            Log.d(TAG, "Server library registered: ${args.libraryName}")

            val ret = JSObject()
            ret.put("success", true)
            ret.put("message", "Server library registered")
            invoke.resolve(ret)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register server library: ${e.message}")
            val ret = JSObject()
            ret.put("success", false)
            ret.put("message", e.message)
            invoke.resolve(ret)
        }
    }
}
