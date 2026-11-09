package com.reet.music

import android.content.Intent
import android.media.audiofx.Equalizer
import android.media.audiofx.BassBoost
import android.media.audiofx.Virtualizer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import android.content.ComponentName
import androidx.core.content.ContextCompat

class MusicPlaybackService : MediaSessionService() {
    companion object {
        @Volatile var audioSessionId: Int = 0
            private set
        @Volatile var equalizerEffect: Equalizer? = null
            private set
        @Volatile var bassBoostEffect: BassBoost? = null
            private set
        @Volatile var virtualizerEffect: Virtualizer? = null
            private set
    }
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.repeatMode = Player.REPEAT_MODE_ALL
        audioSessionId = player.audioSessionId
        // Keep effects bound to the ExoPlayer audio session, not the Activity lifecycle.
        equalizerEffect = runCatching { Equalizer(0, audioSessionId).apply { enabled = true } }.getOrNull()
        bassBoostEffect = runCatching { BassBoost(0, audioSessionId).apply { enabled = true } }.getOrNull()
        virtualizerEffect = runCatching { Virtualizer(0, audioSessionId).apply { enabled = true } }.getOrNull()
        mediaSession = MediaSession.Builder(this, player).build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                val prefs = getSharedPreferences("reet_library", MODE_PRIVATE)
                val count = assets.list("backgrounds")?.size ?: 1
                if (prefs.getBoolean("widget_wallpaper_auto", true) && count > 0) {
                    prefs.edit().putInt("widget_wallpaper_index", (prefs.getInt("widget_wallpaper_index", 0) + 1) % count).apply()
                }
                updateWidgetState()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) = updateWidgetState()
            override fun onPlaybackStateChanged(playbackState: Int) = updateWidgetState()
        })
        updateWidgetState()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = mediaSession?.player
        when (intent?.action) {
            WidgetActionReceiver.PREVIOUS -> player?.seekToPreviousMediaItem()
            WidgetActionReceiver.PLAY_PAUSE -> player?.let { if (it.isPlaying) it.pause() else it.play() }
            WidgetActionReceiver.NEXT -> player?.seekToNextMediaItem()
            WidgetActionReceiver.FAVOURITE -> {
                val id = player?.currentMediaItem?.mediaId
                if (!id.isNullOrBlank()) {
                    val prefs = getSharedPreferences("reet_library", MODE_PRIVATE)
                    val favs = prefs.getStringSet("favorites", emptySet())?.toMutableSet() ?: mutableSetOf()
                    if (!favs.add(id)) favs.remove(id)
                    prefs.edit().putStringSet("favorites", favs).apply()
                    prefs.edit().putBoolean("widget_is_favourite", favs.contains(id)).apply()
                    ReetMusicWidgetProvider.updateAll(this)
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun updateWidgetState() {
        val player = mediaSession?.player ?: return
        val item = player.currentMediaItem
        val metadata = item?.mediaMetadata
        val prefs = getSharedPreferences("reet_library", MODE_PRIVATE)
        val id = item?.mediaId.orEmpty()
        val favs = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        prefs.edit()
            .putString("widget_song_title", metadata?.title?.toString() ?: "Reet Music Player")
            .putString("widget_song_artist", metadata?.artist?.toString() ?: "Your offline music")
            .putString("widget_art_uri", metadata?.artworkUri?.toString())
            .putString("widget_song_id", id)
            .putBoolean("widget_is_playing", player.isPlaying)
            .putBoolean("widget_is_favourite", favs.contains(id))
            .apply()
        ReetMusicWidgetProvider.updateAll(this)
    }


    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        runCatching { equalizerEffect?.release() }
        runCatching { bassBoostEffect?.release() }
        runCatching { virtualizerEffect?.release() }
        equalizerEffect = null
        bassBoostEffect = null
        virtualizerEffect = null
        audioSessionId = 0
        super.onDestroy()
    }
}
