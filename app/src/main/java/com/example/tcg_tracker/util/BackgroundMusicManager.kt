package com.example.tcg_tracker.util

import android.content.Context
import android.media.MediaPlayer

object BackgroundMusicManager {
    private var mediaPlayer: MediaPlayer? = null
    private var isMuted = false

    fun init(context: Context) {
        if (mediaPlayer == null) {
            try {
                val resId = context.resources.getIdentifier("bgm", "raw", context.packageName)
                if (resId != 0) {
                    mediaPlayer = MediaPlayer.create(context.applicationContext, resId)?.apply {
                        isLooping = true
                        setVolume(0.4f, 0.4f)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun play(context: Context) {
        if (isMuted) return
        try {
            if (mediaPlayer == null) {
                init(context)
            }
            mediaPlayer?.let {
                if (!it.isPlaying) {
                    it.start()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pause() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleMute(context: Context): Boolean {
        isMuted = !isMuted
        if (isMuted) {
            pause()
        } else {
            play(context)
        }
        return isMuted
    }

    fun isMuted(): Boolean = isMuted
}
