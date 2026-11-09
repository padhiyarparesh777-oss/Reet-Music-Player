package com.reet.music

import android.content.ContentUris
import android.content.Intent
import android.media.audiofx.Equalizer
import android.media.audiofx.BassBoost
import android.media.audiofx.Virtualizer
import android.os.Build
import android.provider.MediaStore
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.LibraryResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class MusicPlaybackService : MediaLibraryService() {
    companion object {
        @Volatile var audioSessionId: Int = 0
            private set
        @Volatile var equalizerEffect: Equalizer? = null
            private set
        @Volatile var bassBoostEffect: BassBoost? = null
            private set
        @Volatile var virtualizerEffect: Virtualizer? = null
            private set
        private const val ROOT_ID = "reet_root"
        private const val SONGS_ID = "reet_songs"
        private const val ALBUMS_ID = "reet_albums"
    }

    private var mediaSession: MediaLibrarySession? = null

    private fun folder(id: String, title: String, folderType: Int = MediaMetadata.FOLDER_TYPE_NONE): MediaItem =
        MediaItem.Builder().setMediaId(id).setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false)
                .setFolderType(folderType).build()
        ).build()

    private fun songItem(id: Long, title: String, artist: String, album: String, albumId: Long): MediaItem {
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        val artwork = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)
        return MediaItem.Builder().setMediaId(id.toString()).setUri(uri).setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album)
                .setArtworkUri(artwork).setIsBrowsable(false).setIsPlayable(true).build()
        ).build()
    }

    private fun querySongs(selection: String? = null, args: Array<String>? = null): List<MediaItem> {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) return emptyList()
        if (Build.VERSION.SDK_INT < 33 && checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) return emptyList()
        val result = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID
        )
        val filter = if (selection.isNullOrBlank()) "${MediaStore.Audio.Media.IS_MUSIC} != 0" else "(${MediaStore.Audio.Media.IS_MUSIC} != 0) AND ($selection)"
        return runCatching {
            contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, filter, args, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                while (cursor.moveToNext()) {
                    result += songItem(cursor.getLong(idCol), cursor.getString(titleCol) ?: "Unknown title",
                        cursor.getString(artistCol) ?: "Unknown artist", cursor.getString(albumCol) ?: "Unknown album",
                        cursor.getLong(albumIdCol))
                }
            }
            result
        }.getOrDefault(emptyList())
    }

    private fun queryAlbums(): List<MediaItem> {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) return emptyList()
        if (Build.VERSION.SDK_INT < 33 && checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) return emptyList()
        val result = mutableListOf<MediaItem>()
        return runCatching {
            contentResolver.query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.NUMBER_OF_SONGS),
                null, null, MediaStore.Audio.Albums.ALBUM + " COLLATE NOCASE ASC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Unknown album"
                    result += folder("album:$id", name, MediaMetadata.FOLDER_TYPE_ALBUMS)
                }
            }
            result
        }.getOrDefault(emptyList())
    }

    private val libraryCallback = object : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT_ID, "Reet Music"), params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>> {
            val children = when {
                parentId == ROOT_ID -> listOf(
                    folder(SONGS_ID, "All Songs", MediaMetadata.FOLDER_TYPE_TITLES),
                    folder(ALBUMS_ID, "Albums", MediaMetadata.FOLDER_TYPE_ALBUMS)
                )
                parentId == SONGS_ID -> querySongs()
                parentId == ALBUMS_ID -> queryAlbums()
                parentId.startsWith("album:") -> querySongs(
                    "${MediaStore.Audio.Media.ALBUM_ID} = ?", arrayOf(parentId.substringAfter(":"))
                )
                else -> emptyList()
            }
            val start = (page * pageSize).coerceAtLeast(0)
            val pageItems = children.drop(start).take(pageSize)
            return Futures.immediateFuture(LibraryResult.ofItemList(pageItems, params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val id = mediaId.toLongOrNull()
            val item = when {
                mediaId == ROOT_ID -> folder(ROOT_ID, "Reet Music")
                mediaId == SONGS_ID -> folder(SONGS_ID, "All Songs", MediaMetadata.FOLDER_TYPE_TITLES)
                mediaId == ALBUMS_ID -> folder(ALBUMS_ID, "Albums", MediaMetadata.FOLDER_TYPE_ALBUMS)
                mediaId.startsWith("album:") -> folder(mediaId, "Album")
                id != null -> querySongs("${MediaStore.Audio.Media._ID} = ?", arrayOf(id.toString())).firstOrNull()
                else -> null
            }
            return if (item != null) Futures.immediateFuture(LibraryResult.ofItem(item, null))
            else Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE, null))
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.repeatMode = Player.REPEAT_MODE_ALL
        audioSessionId = player.audioSessionId
        equalizerEffect = runCatching { Equalizer(0, audioSessionId).apply { enabled = true } }.getOrNull()
        bassBoostEffect = runCatching { BassBoost(0, audioSessionId).apply { enabled = true } }.getOrNull()
        virtualizerEffect = runCatching { Virtualizer(0, audioSessionId).apply { enabled = true } }.getOrNull()
        mediaSession = MediaLibrarySession.Builder(this, player, libraryCallback).build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

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
