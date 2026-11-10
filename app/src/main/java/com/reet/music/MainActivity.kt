package com.reet.music

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.appwidget.AppWidgetManager
import android.content.pm.PackageManager
import android.content.ComponentName
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.media.audiofx.BassBoost
import android.media.audiofx.Virtualizer
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin
import kotlin.math.PI
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.core.content.ContextCompat
import androidx.palette.graphics.Palette
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.Locale

private val Pink = Color(0xFF29B6F6)
private val Charcoal = Color(0xFF090A10)
private val Deep = Color(0xFF06070C)
private val SoftText = Color(0xFFA5A7B5)
private val Glass = Color(0xFF171A25)
private val Line = Color(0x40FFFFFF)
private val WaterTint = Color(0xFFB9E7FF)


private data class LrcLine(val timeMs: Long, val text: String)

private fun parseLrcLyrics(raw: String): List<LrcLine> {
    val stamp = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    return raw.lineSequence().flatMap { line ->
        val matches = stamp.findAll(line).toList()
        if (matches.isEmpty()) emptySequence() else {
            val lyric = stamp.replace(line, "").trim()
            matches.asSequence().map { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                val fractionRaw = match.groupValues[3]
                val millis = when (fractionRaw.length) {
                    1 -> (fractionRaw.toLongOrNull() ?: 0L) * 100L
                    2 -> (fractionRaw.toLongOrNull() ?: 0L) * 10L
                    3 -> fractionRaw.toLongOrNull() ?: 0L
                    else -> 0L
                }
                LrcLine((minutes * 60L + seconds) * 1000L + millis, lyric)
            }
        }
    }.sortedBy { it.timeMs }.toList()
}

private fun activeLrcIndex(lines: List<LrcLine>, positionMs: Long): Int =
    lines.indexOfLast { it.timeMs <= positionMs }

private data class Song(
    val id: Long, val title: String, val artist: String, val album: String,
    val duration: Long, val folder: String, val albumId: Long, val uri: Uri,
    val fileSize: Long = 0L, val dateAdded: Long = 0L
)

class MainActivity : ComponentActivity() {
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.attributes = window.attributes.apply { preferredRefreshRate = 120f }
        }
        val token = SessionToken(this, ComponentName(this, MusicPlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val controller = future.get()
                setContent { ReetApp(controller, ::loadDeviceSongs) }
            } catch (_: Exception) {
                setContent {
                    MaterialTheme(colorScheme = darkColorScheme()) {
                        Box(Modifier.fillMaxSize().background(Charcoal), contentAlignment = Alignment.Center) {
                            Text("REET could not connect to the playback service. Please reopen the app.", color = Color.White)
                        }
                    }
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }
    private fun loadDeviceSongs(): List<Song> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATE_ADDED
        ).apply { if (Build.VERSION.SDK_INT >= 29) add(MediaStore.Audio.Media.RELATIVE_PATH) }.toTypedArray()
        val result = mutableListOf<Song>()
        contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val size = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateAdded = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val path = if (Build.VERSION.SDK_INT >= 29) c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH) else -1
            while (c.moveToNext()) {
                val songId = c.getLong(id)
                val relative = if (path >= 0) c.getString(path).orEmpty() else "Music/"
                result += Song(songId, c.getString(title) ?: "Unknown title",
                    c.getString(artist)?.takeIf { it != "<unknown>" } ?: "Unknown artist",
                    c.getString(album) ?: "Unknown album", c.getLong(duration),
                    relative.trim('/').substringAfterLast('/').ifBlank { "Music" },
                    c.getLong(albumId), Uri.withAppendedPath(collection, songId.toString()),
                    c.getLong(size), c.getLong(dateAdded))
            }
        }
        return result
    }
    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReetApp(player: Player, loadSongs: () -> List<Song>) {
    val context = LocalContext.current
    val scanScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("reet_library", android.content.Context.MODE_PRIVATE) }
    val haptic = LocalHapticFeedback.current
    var songs by remember { mutableStateOf(emptyList<Song>()) }
    var current by remember { mutableStateOf<Song?>(null) }
    var playing by remember { mutableStateOf(false) }
    var permission by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf("Home") }
    var mediaCategory by remember { mutableStateOf("Songs") }
    var query by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()) }
    var recent by remember { mutableStateOf(prefs.getString("recent", "").orEmpty().split("|").filter { it.isNotBlank() }) }
    var playlists by remember { mutableStateOf<List<String>>(prefs.getStringSet("playlist_names", emptySet())?.toList()?.sorted() ?: emptyList<String>()) }
    var selectedPlaylist by remember { mutableStateOf<String?>(null) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var playlistDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var showPlayer by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var sleepTimerSeconds by remember { mutableLongStateOf(0L) }
    var utility by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(prefs.getString("sort", "Name") ?: "Name") }
    var ascending by remember { mutableStateOf(true) }
    var showSortOptions by remember { mutableStateOf(false) }
    var lightMode by remember { mutableStateOf(prefs.getBoolean("light_mode", true)) }
    var accentName by remember { mutableStateOf(prefs.getString("accent", "Sky Blue") ?: "Sky Blue") }
    var skipShortTracks by remember { mutableStateOf(prefs.getBoolean("skip_under_60s", false)) }
    var lyrics by remember { mutableStateOf("") }
    var lyricsEdit by remember { mutableStateOf(false) }
    var metadataEdit by remember { mutableStateOf(false) }
    var metadataTitle by remember { mutableStateOf("") }
    var metadataArtist by remember { mutableStateOf("") }
    var metadataAlbum by remember { mutableStateOf("") }
    var metadataRevision by remember { mutableIntStateOf(0) }
    val metadataRefresh = metadataRevision
    var editingSongId by remember { mutableStateOf<Long?>(null) }
    var pendingTagValues by remember { mutableStateOf<Map<String, String>?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var wallpaperMode by remember { mutableStateOf(prefs.getString("wallpaper_mode", "AUTO") ?: "AUTO") }
    var wallpaperNames by remember { mutableStateOf(emptyList<String>()) }
    var fixedWallpaper by remember { mutableStateOf(prefs.getString("fixed_wallpaper", "") ?: "") }
    var wallpaperIndex by remember { mutableIntStateOf(0) }
    var widgetLayout by remember { mutableStateOf(prefs.getString("widget_layout", "Medium") ?: "Medium") }
    var widgetAutoWallpaper by remember { mutableStateOf(prefs.getBoolean("widget_wallpaper_auto", true)) }
    var widgetWallpaper by remember { mutableStateOf(prefs.getString("widget_wallpaper", "") ?: "") }
    var audioSessionId by remember { mutableIntStateOf(MusicPlaybackService.audioSessionId) }
    LaunchedEffect(Unit) {
        while (audioSessionId <= 0) {
            delay(250)
            audioSessionId = MusicPlaybackService.audioSessionId
        }
    }
    // Audio effects are owned by the playback service so they survive Activity recreation.
    val equalizer = remember(audioSessionId) { MusicPlaybackService.equalizerEffect }
    val bassBoost = remember(audioSessionId) { MusicPlaybackService.bassBoostEffect }
    val virtualizer = remember(audioSessionId) { MusicPlaybackService.virtualizerEffect }
    var bassLevel by remember { mutableFloatStateOf(prefs.getInt("eq_bass", 0).toFloat()) }
    var trebleLevel by remember { mutableFloatStateOf(prefs.getInt("eq_treble", 500).toFloat()) }
    var virtualLevel by remember { mutableFloatStateOf(prefs.getInt("eq_virtual", 0).toFloat()) }
    var selectedEqPreset by remember { mutableStateOf(prefs.getString("eq_preset", "Custom") ?: "Custom") }
    var eqBandLevels by remember(audioSessionId) { mutableStateOf((0 until (equalizer?.numberOfBands?.toInt() ?: 0)).map { index -> runCatching { prefs.getFloat("eq_band_$index", (equalizer?.getBandLevel(index.toShort()) ?: 0).toFloat() / 100f) }.getOrDefault(0f) }) }
    // Do not release effects here: the Activity can be recreated while music keeps playing.
    LaunchedEffect(bassLevel, virtualLevel) {
        runCatching { bassBoost?.setStrength(bassLevel.toInt().toShort()) }
        runCatching { virtualizer?.setStrength(virtualLevel.toInt().toShort()) }
    }
    LaunchedEffect(equalizer) {
        val eq = equalizer ?: return@LaunchedEffect
        runCatching {
            eq.enabled = true
            for (band in 0 until eq.numberOfBands.toInt()) {
                val savedDb = prefs.getFloat("eq_band_$band", 0f)
                val range = eq.bandLevelRange
                val level = (savedDb * 100f).toInt().coerceIn(range[0].toInt(), range[1].toInt()).toShort()
                eq.setBandLevel(band.toShort(), level)
            }
        }
    }
    LaunchedEffect(Unit) { wallpaperNames = withContext(Dispatchers.IO) { runCatching { context.assets.list("backgrounds")?.filter { it.endsWith(".jpg", true) || it.endsWith(".png", true) || it.endsWith(".webp", true) }?.sorted().orEmpty() }.getOrDefault(emptyList()) } }

    val baseAccent = when (accentName) {
        "Sky Blue" -> Color(0xFF29B6F6)
        "Purple" -> Color(0xFFB99AFF)
        "Blue" -> Color(0xFF76BFFF)
        "Red" -> Color(0xFFFF6474)
        "Mint" -> Color(0xFF65DDB5)
        "Gold" -> Color(0xFFE8C16C)
        else -> Color(0xFF29B6F6)
    }
    val heroGlow = Color(0xFFEAF7FF)
    val bg = if (lightMode) Color(0xFFFFFFFF) else Charcoal
    val panel = if (lightMode) Color(0xD9FFFFFF) else Color(0xB8171B28)
    val glassStroke = if (lightMode) Color.White.copy(alpha = .88f) else Color.White.copy(alpha = .16f)
    val selectedWallpaperForTheme = when (wallpaperMode) {
        "AUTO" -> wallpaperNames.getOrNull(wallpaperIndex)
        "FIXED" -> fixedWallpaper.takeIf { it in wallpaperNames }
        else -> null
    }
    val wallpaperPalette by produceState(initialValue = Triple(baseAccent, Color.White, false), key1 = selectedWallpaperForTheme, key2 = baseAccent, key3 = lightMode) {
        value = withContext(Dispatchers.IO) {
            if (lightMode || selectedWallpaperForTheme == null) Triple(baseAccent, Color.White, false)
            else runCatching {
                val bitmap = context.assets.open("backgrounds/$selectedWallpaperForTheme").use { BitmapFactory.decodeStream(it) }
                val palette = bitmap?.let { Palette.from(it).generate() }
                val swatch = palette?.vibrantSwatch ?: palette?.dominantSwatch ?: palette?.mutedSwatch
                val sampled = swatch?.rgb?.let { Color(it) } ?: baseAccent
                val isBright = sampled.luminance() > .56f
                Triple(sampled, if (isBright) Color(0xFF11131A) else Color.White, isBright)
            }.getOrDefault(Triple(baseAccent, Color.White, false))
        }
    }
    val accent = wallpaperPalette.first
    val fg = if (lightMode) Color(0xFF17131A) else wallpaperPalette.second
    val muted = if (lightMode) Color(0xFF514A55) else fg.copy(alpha = .72f)
    val audioPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    fun scan() {
        scanScope.launch {
            val library = withContext(Dispatchers.IO) { loadSongs() }
            songs = library
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission = it
        if (it) scan()
    }
    val exportM3uLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) {
            runCatching {
                val content = buildString {
                    append("#EXTM3U\n")
                    songs.forEach { song ->
                        append("#EXTINF:")
                        append(song.duration / 1000L)
                        append(",")
                        append(song.title.replace("\n", " "))
                        append(" - ")
                        append(song.artist.replace("\n", " "))
                        append("\n")
                        append(song.uri.toString())
                        append("\n")
                    }
                }
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
            }
        }
    }
    val artworkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = editingSongId
        if (uri != null && id != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            prefs.edit().putString("custom_art_$id", uri.toString()).apply()
            metadataRevision++
            if (current?.id == id) {
                val song = current
                val index = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == id.toString() } ?: -1
                if (song != null && index >= 0) {
                    val old = player.getMediaItemAt(index)
                    val updated = MediaItem.Builder().setUri(old.localConfiguration?.uri ?: song.uri).setMediaId(id.toString())
                        .setMediaMetadata(old.mediaMetadata.buildUpon().setArtworkUri(uri).build()).build()
                    player.replaceMediaItem(index, updated)
                }
                prefs.edit().putString("widget_art_uri", uri.toString()).apply()
                ReetMusicWidgetProvider.updateAll(context)
            }
        }
    }

    fun persistSongMetadata(id: Long, title: String, artist: String, album: String) {
        val song = songs.firstOrNull { it.id == id } ?: current?.takeIf { it.id == id } ?: return
        prefs.edit().putString("meta_title_$id", title).putString("meta_artist_$id", artist)
            .putString("meta_album_$id", album).apply()
        metadataRevision++
        val art = prefs.getString("custom_art_$id", null)?.let { Uri.parse(it) }
            ?: Uri.parse("content://media/external/audio/albumart/${song.albumId}")
        val index = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == id.toString() } ?: -1
        if (index >= 0) {
            val item = MediaItem.Builder().setUri(song.uri).setMediaId(id.toString())
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).setArtworkUri(art).build()).build()
            player.replaceMediaItem(index, item)
        }
        prefs.edit().putString("widget_song_title", title).putString("widget_song_artist", artist)
            .putString("widget_art_uri", art.toString()).apply()
        ReetMusicWidgetProvider.updateAll(context)
    }
    val writeTagsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val pending = pendingTagValues
        pendingTagValues = null
        if (pending != null) {
            val id = pending["id"]?.toLongOrNull()
            val song = songs.firstOrNull { it.id == id }
            if (id != null && song != null) {
                if (result.resultCode == Activity.RESULT_OK) {
                    runCatching {
                        val values = ContentValues().apply {
                            put(MediaStore.Audio.Media.TITLE, pending["title"])
                            put(MediaStore.Audio.Media.ARTIST, pending["artist"])
                            put(MediaStore.Audio.Media.ALBUM, pending["album"])
                        }
                        context.contentResolver.update(song.uri, values, null, null)
                    }
                }
                // Keep the in-app metadata even when the user declines the system write prompt.
                persistSongMetadata(id, pending["title"].orEmpty(), pending["artist"].orEmpty(), pending["album"].orEmpty())
                metadataEdit = false
            }
        }
    }
    val importM3uLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val lines = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readLines() }.orEmpty()
                val imported = mutableListOf<Song>()
                var pendingTitle: String? = null
                var pendingArtist: String? = null
                lines.filter { it.isNotBlank() }.forEach { rawLine ->
                    val line = rawLine.trim()
                    when {
                        line.startsWith("#EXTINF:", true) -> {
                            val info = line.substringAfter(",", "")
                            pendingTitle = info.substringBefore(" - ").trim()
                            pendingArtist = info.substringAfter(" - ", "").trim().takeIf { it.isNotBlank() }
                        }
                        line.startsWith("#") -> Unit
                        else -> {
                            val exact = songs.firstOrNull { it.uri.toString() == line }
                            val decodedPath = runCatching { Uri.decode(Uri.parse(line).lastPathSegment ?: line) }.getOrDefault(line)
                            val fileName = decodedPath.substringAfterLast('/').substringBeforeLast('.').trim()
                            val title = pendingTitle?.takeIf { it.isNotBlank() } ?: fileName
                            val artist = pendingArtist
                            val byTitleAndArtist = songs.firstOrNull {
                                it.title.equals(title, true) && (artist == null || it.artist.equals(artist, true))
                            }
                            val match = exact ?: byTitleAndArtist ?: songs.firstOrNull {
                                it.title.equals(title, true) || it.title.equals(fileName, true)
                            }
                            if (match != null && imported.none { it.id == match.id }) imported += match
                            pendingTitle = null
                            pendingArtist = null
                        }
                    }
                }
                if (imported.isNotEmpty()) {
                    player.setMediaItems(imported.map { song ->
                        MediaItem.Builder().setUri(song.uri).setMediaId(song.id.toString())
                            .setMediaMetadata(MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist).setAlbumTitle(song.album).build()).build()
                    })
                    player.prepare()
                    player.play()
                    current = imported.first()
                    showPlayer = true
                }
            }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                current = songs.firstOrNull { it.id.toString() == mediaItem?.mediaId }
                if (mediaItem != null && wallpaperMode == "AUTO" && wallpaperNames.isNotEmpty()) wallpaperIndex = (wallpaperIndex + 1) % wallpaperNames.size
                current?.let { s ->
                    recent = (listOf(s.id.toString()) + recent.filterNot { it == s.id.toString() }).take(100)
                    prefs.edit().putString("recent", recent.joinToString("|")).apply()
                    if (skipShortTracks && s.duration in 1L..59_999L) player.seekToNextMediaItem()
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(Unit) {
        permission = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        if (permission) scan()
    }
    LaunchedEffect(player) {
        while (true) { position = player.currentPosition.coerceAtLeast(0L); duration = player.duration.takeIf { it > 0 } ?: 0L; delay(350) }
    }
    LaunchedEffect(sleepTimerSeconds) {
        if (sleepTimerSeconds > 0L) {
            delay(1_000L)
            sleepTimerSeconds = (sleepTimerSeconds - 1L).coerceAtLeast(0L)
            if (sleepTimerSeconds == 0L) player.pause()
        }
    }
    LaunchedEffect(current?.id) {
        val id = current?.id?.toString() ?: return@LaunchedEffect
        lyrics = prefs.getString("lyrics_" + id, "") ?: ""
    }

    val source = when {
        mediaCategory == "Favourites" || tab == "Favourites" -> songs.filter { favorites.contains(it.id.toString()) }
        mediaCategory == "New" -> songs.sortedByDescending { it.dateAdded }.take(50)
        mediaCategory == "Duplicates" -> songs.groupBy { it.title.trim().lowercase(Locale.getDefault()) + "|" + it.artist.trim().lowercase(Locale.getDefault()) }.values.filter { it.size > 1 }.flatten()
        (tab == "Library" || tab == "Playlists") && selectedPlaylist != null -> when (selectedPlaylist) {
            "__recent__" -> recent.mapNotNull { id -> songs.firstOrNull { it.id.toString() == id } }
            else -> songs.filter { prefs.getStringSet("playlist_" + selectedPlaylist, emptySet())?.contains(it.id.toString()) == true }
        }
        else -> songs
    }
    val visible = remember(source, query, sort, ascending, mediaCategory, selectedGroup) {
        val groupedSource = if (selectedGroup == null) source else source.filter { song -> when (mediaCategory) { "Albums" -> song.album == selectedGroup; "Artists" -> song.artist == selectedGroup; "Folders" -> song.folder == selectedGroup; else -> true } }
        val found = groupedSource.filter { query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }
        val ordered = when (sort) {
            "Artist" -> found.sortedBy { it.artist.lowercase(Locale.getDefault()) }
            "Album" -> found.sortedBy { it.album.lowercase(Locale.getDefault()) }
            "Time" -> found.sortedByDescending { it.dateAdded }
            "Size" -> found.sortedByDescending { it.fileSize }
            "Length" -> found.sortedBy { it.duration }
            else -> found.sortedBy { it.title.lowercase(Locale.getDefault()) }
        }
        val sorted = if (ascending) ordered else ordered.reversed()
        when (mediaCategory) {
            "Albums" -> if (selectedGroup == null) sorted.distinctBy { it.album.trim().lowercase(Locale.getDefault()) } else sorted
            "Artists" -> if (selectedGroup == null) sorted.distinctBy { it.artist.trim().lowercase(Locale.getDefault()) } else sorted
            "Folders" -> if (selectedGroup == null) sorted.distinctBy { it.folder.trim().lowercase(Locale.getDefault()) } else sorted
            else -> sorted
        }
    }

    MaterialTheme(colorScheme = if (lightMode) lightColorScheme(primary = accent, background = bg, surface = panel, onSurface = fg) else darkColorScheme(primary = accent, background = bg, surface = panel, onSurface = fg)) {
      CompositionLocalProvider(LocalIndication provides ripple(color = Color.White.copy(alpha = .48f))) {
        Box(Modifier.fillMaxSize().background(bg)) {
            val selectedWallpaper = selectedWallpaperForTheme
            if (!lightMode && selectedWallpaper != null) ReetWallpaper(selectedWallpaper)
            else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(if (lightMode) Color(0xFFFFFFFF) else heroGlow, bg, if (lightMode) Color(0xFFF4FBFF) else Color(0xFF080910)))))
            // Water-glass atmosphere with soft cyan and violet light blooms.
            Box(Modifier.align(Alignment.TopEnd).padding(top = 45.dp, end = 4.dp).size(260.dp).clip(CircleShape).background((if (lightMode) WaterTint else accent).copy(alpha = if (lightMode) .32f else .24f)).blur(78.dp))
            Box(Modifier.align(Alignment.BottomStart).padding(bottom = 145.dp, start = 0.dp).size(220.dp).clip(CircleShape).background((if (lightMode) Color(0xFFB9E7FF) else heroGlow).copy(alpha = if (lightMode) .22f else .30f)).blur(82.dp))
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GraphicEq, null, tint = accent, modifier = Modifier.size(27.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Reet", color = fg, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.5).sp)
                            Text("Music Player", color = muted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    CircleAction(Icons.Default.Search, fg, panel) {
                        showSearchBar = !showSearchBar
                        if (showSearchBar) tab = "Search" else { tab = "Home"; query = "" }
                    }
                    Spacer(Modifier.width(5.dp))
                    CircleAction(Icons.Default.Settings, fg, panel) { showSettings = true }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 3.dp, bottom = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    listOf("Songs", "New", "Favourites", "Albums", "Artists", "Folders", "Duplicates").forEach { category ->
                        val selected = mediaCategory == category
                        Text(
                            category,
                            color = if (selected) Color.White else muted,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(18.dp))
                                .background(if (selected) accent else panel.copy(alpha = .72f))
                                .border(1.dp, if (selected) accent else glassStroke, RoundedCornerShape(18.dp))
                                .softWhiteTap {
                                    mediaCategory = category
                                    selectedGroup = null
                                    selectedPlaylist = null
                                    tab = if (category == "Favourites") "Favourites" else "Home"
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
                AnimatedVisibility(
                    visible = showSearchBar,
                    enter = scaleIn(initialScale = .82f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) + fadeIn(),
                    exit = scaleOut(targetScale = .92f) + fadeOut()
                ) {
                    Row(Modifier.fillMaxWidth().padding(top = 5.dp).height(46.dp).clip(RoundedCornerShape(18.dp))
                        .background(Brush.verticalGradient(listOf(panel.copy(alpha = .98f), panel.copy(alpha = .82f))))
                        .border(1.dp, glassStroke, RoundedCornerShape(18.dp)).padding(horizontal = 13.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, tint = accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(9.dp))
                        BasicTextField(value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(color = fg, fontSize = 12.sp),
                            decorationBox = { inner -> Box { if (query.isEmpty()) Text("Search your offline music", color = muted, fontSize = 12.sp); inner() } })
                        if (query.isNotEmpty()) Icon(Icons.Default.Close, null, tint = muted, modifier = Modifier.size(17.dp).clickable { query = "" })
                        Icon(Icons.Default.Close, null, tint = muted, modifier = Modifier.padding(start = 8.dp).size(17.dp).clickable { showSearchBar = false; tab = "Home"; query = "" })
                    }
                }
                Spacer(Modifier.height(17.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selectedGroup != null) Text("‹ Back", color = accent, fontSize = 11.sp, modifier = Modifier.clickable { selectedGroup = null }.padding(end = 10.dp))
                        Text(when { selectedGroup != null -> selectedGroup!!; mediaCategory == "New" -> "Recently added"; mediaCategory == "Albums" -> "Albums"; mediaCategory == "Artists" -> "Artists"; mediaCategory == "Folders" -> "Folders"; mediaCategory == "Favourites" || tab == "Favourites" -> "Your favourites"; tab == "Home" -> "All Songs (" + songs.size + ")"; tab == "Search" -> "Search Results"; tab == "Playlists" -> "Your playlists"; selectedPlaylist != null -> if (selectedPlaylist == "__recent__") "Recently played" else selectedPlaylist!!; else -> "Your library" }, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    }
                }
                Spacer(Modifier.height(9.dp))
                if (!permission) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ArtworkPlaceholder(accent, 92)
                            Spacer(Modifier.height(14.dp))
                            Text("Your music lives here", color = fg, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("Allow access to scan songs stored on your phone.", color = muted, fontSize = 11.sp, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = { permissionLauncher.launch(audioPermission) }, colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)) { Text("Allow music access") }
                        }
                    }
                } else if (tab == "Playlists" && selectedPlaylist == null) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp)) {
                            Text("Your playlists", color = fg, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text("+ New playlist", color = accent, fontSize = 11.sp, modifier = Modifier.clickable { playlistName = ""; playlistDialog = true }.padding(6.dp))
                        }
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(playlists) { name ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Brush.verticalGradient(listOf(panel.copy(alpha = .95f), panel.copy(alpha = .68f)))).border(1.dp, glassStroke, RoundedCornerShape(15.dp)).clickable { selectedPlaylist = name }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    current?.let { SongArtwork(it.albumId, accent, 42, prefs.getString("custom_art_" + it.id, null)) }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) { Text(name, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 12.sp); Text("Personal playlist", color = muted, fontSize = 10.sp) }
                                    Icon(Icons.Default.ChevronRight, null, tint = accent)
                                }
                            }
                            item {
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Brush.verticalGradient(listOf(panel.copy(alpha = .95f), panel.copy(alpha = .68f)))).border(1.dp, glassStroke, RoundedCornerShape(15.dp)).clickable { selectedPlaylist = "__recent__" }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    ArtworkPlaceholder(accent, 42); Spacer(Modifier.width(10.dp))
                                    Column { Text("Recently played", color = fg, fontWeight = FontWeight.SemiBold, fontSize = 12.sp); Text("Your listening history", color = muted, fontSize = 10.sp) }
                                }
                            }
                        }
                    }
                } else if (visible.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(if (tab == "Favourites") Icons.Default.FavoriteBorder else Icons.Default.MusicOff, null, tint = accent, modifier = Modifier.size(38.dp))
                            Spacer(Modifier.height(8.dp)); Text("Nothing to play yet", color = fg, fontWeight = FontWeight.Bold)
                            Text("Add music to your device or try another search.", color = muted, fontSize = 10.sp)
                            Text("Scan library", color = accent, fontSize = 11.sp, modifier = Modifier.clickable { scan() }.padding(10.dp))
                        }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(visible, key = { it.id }) { song ->
                            val active = current?.id == song.id
                            Row(Modifier.fillMaxWidth().height(58.dp)
                                .background(if (active) accent.copy(alpha = .10f) else Color.Transparent)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (mediaCategory in listOf("Albums", "Artists", "Folders") && selectedGroup == null) {
                                        selectedGroup = when (mediaCategory) { "Albums" -> song.album; "Artists" -> song.artist; else -> song.folder }
                                    } else {
                                        current = song
                                        val queueSongs = when (mediaCategory) {
                                            "Albums" -> source.filter { it.album == song.album }
                                            "Artists" -> source.filter { it.artist == song.artist }
                                            "Folders" -> source.filter { it.folder == song.folder }
                                            else -> source
                                        }
                                        val index = queueSongs.indexOfFirst { it.id == song.id }
                                        player.setMediaItems(queueSongs.map { MediaItem.Builder().setUri(it.uri).setMediaId(it.id.toString()).setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).setArtist(it.artist).setAlbumTitle(it.album).setArtworkUri(Uri.parse("content://media/external/audio/albumart/" + it.albumId)).build()).build() }, index.coerceAtLeast(0), 0L)
                                        player.prepare(); player.play(); showPlayer = true
                                    }
                                }.padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                SongArtwork(song.albumId, accent, 42, prefs.getString("custom_art_" + song.id, null))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(when(mediaCategory) { "Albums" -> prefs.getString("meta_album_" + song.id, song.album) ?: song.album; "Artists" -> prefs.getString("meta_artist_" + song.id, song.artist) ?: song.artist; "Folders" -> song.folder; else -> prefs.getString("meta_title_" + song.id, song.title) ?: song.title }, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (mediaCategory == "Songs") (prefs.getString("meta_artist_" + song.id, song.artist) ?: song.artist) + " · " + (prefs.getString("meta_album_" + song.id, song.album) ?: song.album) else source.count { when(mediaCategory) { "Albums" -> it.album == song.album; "Artists" -> it.artist == song.artist; else -> it.folder == song.folder } }.toString() + " tracks", color = muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(formatTime(song.duration), color = muted, fontSize = 9.sp)
                                Spacer(Modifier.width(5.dp))
                                Icon(Icons.Default.PlaylistAdd, "Play next", tint = accent, modifier = Modifier.size(18.dp).clickable {
                                    val item = MediaItem.Builder().setUri(song.uri).setMediaId(song.id.toString())
                                        .setMediaMetadata(MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist)
                                            .setAlbumTitle(song.album).setArtworkUri(Uri.parse("content://media/external/audio/albumart/" + song.albumId)).build()).build()
                                    val exists = (0 until player.mediaItemCount).any { player.getMediaItemAt(it).mediaId == song.id.toString() }
                                    if (exists) {
                                        val oldIndex = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == song.id.toString() } ?: -1
                                        if (oldIndex >= 0) player.removeMediaItem(oldIndex)
                                    }
                                    val nextIndex = if (player.mediaItemCount == 0) 0 else (player.currentMediaItemIndex + 1).coerceIn(0, player.mediaItemCount)
                                    player.addMediaItem(nextIndex, item)
                                })
                                Icon(if (favorites.contains(song.id.toString())) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null,
                                    tint = if (favorites.contains(song.id.toString())) accent else muted,
                                    modifier = Modifier.size(17.dp).clickable {
                                        val key = song.id.toString()
                                        favorites = if (favorites.contains(key)) favorites - key else favorites + key
                                        prefs.edit().putStringSet("favorites", favorites).apply()
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    })
                                if (tab == "Library" && selectedPlaylist != null && selectedPlaylist != "__recent__") {
                                    Spacer(Modifier.width(5.dp))
                                    Icon(Icons.Default.RemoveCircleOutline, null, tint = muted, modifier = Modifier.size(16.dp).clickable {
                                        val set = prefs.getStringSet("playlist_" + selectedPlaylist, emptySet())?.toMutableSet() ?: mutableSetOf()
                                        set.remove(song.id.toString()); prefs.edit().putStringSet("playlist_" + selectedPlaylist, set).apply()
                                    })
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
            }
            AnimatedVisibility(
                visible = current != null,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(tween(220)),
                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }, animationSpec = tween(180)) + fadeOut(tween(140))
            ) {
                Row(
                    Modifier.fillMaxWidth().height(72.dp).shadow(16.dp, RoundedCornerShape(22.dp), ambientColor = accent.copy(alpha = .10f), spotColor = accent.copy(alpha = .12f))
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(listOf(Color.White.copy(alpha = .96f), Color(0xFFF4FBFF).copy(alpha = .96f), accent.copy(alpha = .10f))))
                        .border(1.dp, Color(0xFFB9E7FF).copy(alpha = .75f), RoundedCornerShape(22.dp))
                        .softWhiteTap { showPlayer = true }.padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    current?.let { SongArtwork(it.albumId, accent, 48, prefs.getString("custom_art_" + it.id, null)) } ?: ArtworkPlaceholder(accent, 48)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f).clickable { showPlayer = true }) {
                        Text(current?.let { prefs.getString("meta_title_" + it.id, it.title) ?: it.title }.orEmpty(), color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(current?.let { prefs.getString("meta_artist_" + it.id, it.artist) ?: it.artist }.orEmpty(), color = muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { player.seekToPreviousMediaItem() }) { Icon(Icons.Default.SkipPrevious, "Previous", tint = fg) }
                    IconButton(onClick = { if (playing) player.pause() else player.play() }) { Icon(if (playing) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled, "Play or pause", tint = accent, modifier = Modifier.size(29.dp)) }
                    IconButton(onClick = { player.seekToNextMediaItem() }) { Icon(Icons.Default.SkipNext, "Next", tint = fg) }
                    IconButton(onClick = {
                        current?.let { song ->
                            val key = song.id.toString()
                            favorites = if (favorites.contains(key)) favorites - key else favorites + key
                            prefs.edit().putStringSet("favorites", favorites).apply()
                        }
                    }) { Icon(if (current?.let { favorites.contains(it.id.toString()) } == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favourite", tint = accent) }
                }
            }
        }
      }
    }

    if (showSortOptions) {
        Dialog(onDismissRequest = { showSortOptions = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val popupScale = remember { Animatable(0.82f) }
            LaunchedEffect(Unit) { popupScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
            val popupShape = RoundedCornerShape(30.dp)
            Column(
                Modifier.padding(horizontal = 24.dp).fillMaxWidth()
                    .graphicsLayer { scaleX = popupScale.value; scaleY = popupScale.value }
                    .shadow(24.dp, popupShape).clip(popupShape)
                    .background(Brush.linearGradient(listOf(Color.White.copy(alpha = .97f), Color(0xFFFFE6EF).copy(alpha = .96f))))
                    .border(1.dp, Color.White, popupShape).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Sort your music", color = Color(0xFF3B2331), fontWeight = FontWeight.Bold, fontSize = 19.sp)
                listOf("Time", "Name", "Size", "Length", "Artist", "Album").forEach { option ->
                    val selected = sort == option
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
                            .background(if (selected) Color(0xFFFFD9E6) else Color.White.copy(alpha = .78f))
                            .softWhiteTap { sort = option; prefs.edit().putString("sort", option).apply() }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            when (option) { "Time" -> "Recently added"; "Name" -> "Song name"; "Size" -> "File size"; "Length" -> "Duration"; else -> option },
                            color = Color(0xFF3B2331), modifier = Modifier.weight(1f), fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                        if (selected) Icon(Icons.Default.CheckCircle, null, tint = Color(0xFFE5558A))
                    }
                }
                Text("Order", color = Color(0xFF7D5A6C), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    listOf("Ascending" to true, "Descending" to false).forEach { (label, value) ->
                        val selected = ascending == value
                        Text(label, color = if (selected) Color.White else Color(0xFF3B2331),
                            modifier = Modifier.clip(RoundedCornerShape(50))
                                .background(if (selected) Color(0xFFE5558A) else Color.White.copy(alpha = .75f))
                                .clickable { ascending = value }.padding(horizontal = 14.dp, vertical = 9.dp),
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text("Done", color = Color.White,
                    modifier = Modifier.align(Alignment.End).clip(RoundedCornerShape(50))
                        .background(Color(0xFFE5558A)).softWhiteTap { showSortOptions = false }
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                    fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }

    if (showPlayer && current != null) {
        Dialog(onDismissRequest = { showPlayer = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val song = current!!
            val art = rememberArtwork(song.albumId)
            val dynamicAccent = accent
            val artMotion = rememberInfiniteTransition(label = "artMotion")
            val artScale by artMotion.animateFloat(1f, 1.035f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "artScale")
            Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
                val playerWallpaper = when (wallpaperMode) {
                    "AUTO" -> wallpaperNames.getOrNull(wallpaperIndex)
                    "FIXED" -> fixedWallpaper.takeIf { it in wallpaperNames }
                    else -> null
                }
                if (!lightMode && playerWallpaper != null) {
                    Box(Modifier.fillMaxSize().blur(24.dp)) { ReetWallpaper(playerWallpaper) }
                } else if (art != null) androidx.compose.foundation.Image(art.asImageBitmap(), null,
                    modifier = Modifier.fillMaxSize().blur(64.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                    if (lightMode) Color.White.copy(alpha = .42f) else Color.Black.copy(alpha = .48f), bg.copy(alpha = .56f), if (lightMode) Color(0xFFEAF7FF).copy(alpha = .35f) else Color.Black.copy(alpha = .52f)
                ))))
                val cardShape = RoundedCornerShape(34.dp)
                Box(
                    Modifier.fillMaxWidth(.88f).fillMaxHeight(.78f)
                        .shadow(26.dp, cardShape, ambientColor = accent.copy(alpha = .12f), spotColor = accent.copy(alpha = .16f))
                        .clip(cardShape)
                ) {
                    if (!lightMode && playerWallpaper != null) ReetWallpaper(playerWallpaper, darken = false)
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(
                        Color.White.copy(alpha = if (lightMode) .72f else .10f),
                        Color(0xFFEAF7FF).copy(alpha = if (lightMode) .72f else .08f),
                        Color.White.copy(alpha = if (lightMode) .48f else .10f)
                    ))))
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 19.dp, vertical = 13.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showPlayer = false }) { Icon(Icons.Default.KeyboardArrowDown, "Close player", tint = fg) }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("NOW PLAYING", color = muted, fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                            Text("REET · OFFLINE", color = accent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                        IconButton(onClick = { utility = "queue" }) { Icon(Icons.Default.MoreVert, "More options", tint = fg) }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(prefs.getString("meta_title_" + song.id, song.title) ?: song.title, color = fg, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text((prefs.getString("meta_artist_" + song.id, song.artist) ?: song.artist) + " · " + (prefs.getString("meta_album_" + song.id, song.album) ?: song.album), color = muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(58.dp), contentAlignment = Alignment.Center) {
                        val progress = if (duration > 0L) position.coerceIn(0L, duration).toFloat() / duration.toFloat() else 0f
                        Canvas(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(28.dp)) {
                            val centerY = size.height / 2f
                            val startX = 0f
                            val endX = size.width
                            val currentX = startX + (endX - startX) * progress
                            drawLine(Color.White.copy(alpha = .38f), Offset(startX, centerY), Offset(endX, centerY),
                                strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(Color.White.copy(alpha = .28f), Offset(startX, centerY), Offset(endX, centerY),
                                strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                            if (currentX > startX) {
                                drawLine(accent.copy(alpha = .72f), Offset(startX, centerY), Offset(currentX, centerY),
                                    strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                                drawCircle(accent.copy(alpha = .20f), radius = 15.dp.toPx(), center = Offset(currentX, centerY))
                                drawCircle(accent.copy(alpha = .42f), radius = 9.dp.toPx(), center = Offset(currentX, centerY))
                                drawCircle(accent, radius = 5.dp.toPx(), center = Offset(currentX, centerY))
                            }
                        }
                        Slider(value = if (duration > 0) position.coerceIn(0, duration).toFloat() else 0f,
                            onValueChange = { position = it.toLong() }, onValueChangeFinished = { player.seekTo(position) },
                            valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                            colors = SliderDefaults.colors(thumbColor = Color.Transparent, activeTrackColor = Color.Transparent,
                                inactiveTrackColor = Color.Transparent, disabledThumbColor = Color.Transparent))
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(position), color = muted, fontSize = 10.sp)
                        Text(formatTime(duration), color = muted, fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(7.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { player.shuffleModeEnabled = !player.shuffleModeEnabled }) { Icon(Icons.Default.Shuffle, "Shuffle", tint = if (player.shuffleModeEnabled) dynamicAccent else muted) }
                        IconButton(onClick = {
                            player.repeatMode = when (player.repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                        }) {
                            Icon(Icons.Default.Repeat, "Repeat", tint = if (player.repeatMode == Player.REPEAT_MODE_OFF) muted else dynamicAccent)
                        }
                        IconButton(onClick = { player.seekToPreviousMediaItem() }) { Icon(Icons.Default.SkipPrevious, "Previous", tint = fg, modifier = Modifier.size(30.dp)) }
                        Box(Modifier.size(66.dp).shadow(12.dp, CircleShape).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(dynamicAccent.copy(alpha = .95f), dynamicAccent.copy(alpha = .62f))))
                            .clickable { if (playing) player.pause() else player.play() }, contentAlignment = Alignment.Center) {
                            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                        IconButton(onClick = { player.seekToNextMediaItem() }) { Icon(Icons.Default.SkipNext, "Next", tint = fg, modifier = Modifier.size(30.dp)) }
                        IconButton(onClick = {
                            val key = song.id.toString()
                            favorites = if (favorites.contains(key)) favorites - key else favorites + key
                            prefs.edit().putStringSet("favorites", favorites).apply()
                        }) { Icon(if (favorites.contains(song.id.toString())) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favourite", tint = accent) }
                    }
                    Spacer(Modifier.height(5.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        PlayerTool(Icons.Default.Edit, "Edit", dynamicAccent, fg) {
                            current?.let { song ->
                                editingSongId = song.id
                                metadataTitle = prefs.getString("meta_title_" + song.id, song.title) ?: song.title
                                metadataArtist = prefs.getString("meta_artist_" + song.id, song.artist) ?: song.artist
                                metadataAlbum = prefs.getString("meta_album_" + song.id, song.album) ?: song.album
                                metadataEdit = true
                            }
                        }
                        PlayerTool(Icons.Default.QueueMusic, "Queue", dynamicAccent, fg) { utility = "queue" }
                        PlayerTool(Icons.Default.Description, "Lyrics", dynamicAccent, fg) { utility = "lyrics" }
                        PlayerTool(Icons.Default.GraphicEq, "Equalizer", dynamicAccent, fg) { utility = ""; showSettings = true }
                        PlayerTool(Icons.Default.PlaylistAdd, "Add to", dynamicAccent, fg) { utility = "add" }
                        PlayerTool(Icons.Default.Bedtime, if (sleepTimerSeconds > 0L) "${sleepTimerSeconds / 60}m left" else "Sleep", dynamicAccent, fg) { showSleepTimer = true }
                    }
                    Spacer(Modifier.height(5.dp))
                    }
                }
            }
        }
    }

    if (utility.isNotEmpty()) {
        val isLyrics = utility == "lyrics"
        val isQueue = utility == "queue"
        val isAdd = utility == "add"
        AlertDialog(onDismissRequest = { utility = "" }, containerColor = panel,
            title = { Text(when(utility) { "lyrics" -> "Lyrics"; "queue" -> "Play queue"; "add" -> "Add to playlist"; "about" -> "About Reet Music"; else -> "Equalizer" }, color = fg) },
            text = {
                when {
                    isLyrics -> Column(Modifier.heightIn(max = 400.dp)) {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Glass), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Text("Lyrics", color = Color.White, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(accent).padding(horizontal = 22.dp, vertical = 9.dp))
                            Text("Details", color = muted, modifier = Modifier.padding(horizontal = 22.dp, vertical = 9.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(current?.title.orEmpty(), color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(current?.artist.orEmpty(), color = muted, fontSize = 10.sp)
                        Spacer(Modifier.height(18.dp))
                        val syncedLines = remember(lyrics) { parseLrcLyrics(lyrics) }
                        val activeLine = activeLrcIndex(syncedLines, position)
                        if (lyrics.isBlank()) {
                            Text("No saved lyrics yet. Paste plain lyrics or timestamped .LRC lyrics for offline access.", color = muted, fontSize = 12.sp)
                        } else if (syncedLines.isNotEmpty()) {
                            val lyricListState = androidx.compose.foundation.lazy.rememberLazyListState()
                            LaunchedEffect(activeLine) {
                                if (activeLine >= 0) lyricListState.animateScrollToItem(activeLine)
                            }
                            LazyColumn(state = lyricListState, modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                items(syncedLines.size) { index ->
                                    val line = syncedLines[index]
                                    Text(line.text.ifBlank { "♪" }, color = if (index == activeLine) accent else muted.copy(alpha = if (index in (activeLine + 1)..(activeLine + 2)) .85f else .45f), fontSize = if (index == activeLine) 18.sp else 13.sp, fontWeight = if (index == activeLine) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp))
                                }
                            }
                        } else Text(lyrics, color = fg, fontSize = 14.sp, lineHeight = 24.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Edit / paste LRC lyrics", color = accent, modifier = Modifier.clickable { lyricsEdit = true })
                    }
                    isQueue -> {
                        val queuedSongs = (0 until player.mediaItemCount).mapNotNull { index ->
                            val id = player.getMediaItemAt(index).mediaId
                            songs.firstOrNull { it.id.toString() == id }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("NOW PLAYING · ${if (player.mediaItemCount == 0) 0 else player.currentMediaItemIndex + 1}/${player.mediaItemCount}", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("Add library", color = accent, fontSize = 10.sp, modifier = Modifier.clickable {
                                val existing = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
                                val additions = songs.filter { it.id.toString() !in existing }.map { song ->
                                    MediaItem.Builder().setUri(song.uri).setMediaId(song.id.toString())
                                        .setMediaMetadata(MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist)
                                            .setAlbumTitle(song.album).setArtworkUri(Uri.parse("content://media/external/audio/albumart/" + song.albumId)).build()).build()
                                }
                                if (additions.isNotEmpty()) player.addMediaItems(additions)
                            })
                            Text("Clear", color = muted, fontSize = 10.sp, modifier = Modifier.clickable { player.clearMediaItems() })
                        }
                        Spacer(Modifier.height(6.dp))
                        LazyColumn(Modifier.heightIn(max = 360.dp)) {
                            items(queuedSongs, key = { it.id }) { queued ->
                                val queueIndex = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == queued.id.toString() } ?: -1
                                val active = queued.id.toString() == player.currentMediaItem?.mediaId
                                var dragDistance by remember(queueIndex) { mutableFloatStateOf(0f) }
                                Row(Modifier.fillMaxWidth()
                                    .pointerInput(queueIndex, player.mediaItemCount) {
                                        detectDragGesturesAfterLongPress(
                                            onDragEnd = {
                                                if (dragDistance > 48f && queueIndex >= 0 && queueIndex < player.mediaItemCount - 1) player.moveMediaItem(queueIndex, queueIndex + 1)
                                                else if (dragDistance < -48f && queueIndex > 0) player.moveMediaItem(queueIndex, queueIndex - 1)
                                                dragDistance = 0f
                                            },
                                            onDragCancel = { dragDistance = 0f },
                                            onDrag = { change, amount -> dragDistance += amount.y; change.consume() }
                                        )
                                    }
                                    .background(if (active) accent.copy(alpha = .10f) else Color.Transparent)
                                    .clickable {
                                        if (queueIndex >= 0) { player.seekTo(queueIndex, 0L); player.play(); utility = "" }
                                    }.padding(vertical = 7.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (active) "▶" else "${queuedSongs.indexOf(queued) + 1}", color = if (active) accent else muted, fontSize = 10.sp, modifier = Modifier.width(24.dp))
                                    SongArtwork(queued.albumId, accent, 36, prefs.getString("custom_art_" + queued.id, null)); Spacer(Modifier.width(9.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(queued.title, color = if (active) accent else fg, fontSize = 11.sp, maxLines = 1)
                                        Text(queued.artist, color = muted, fontSize = 9.sp, maxLines = 1)
                                    }
                                    Text(formatTime(queued.duration), color = muted, fontSize = 9.sp)
                                    Spacer(Modifier.width(5.dp))
                                    Text("↑", color = muted, fontSize = 14.sp, modifier = Modifier.clickable {
                                        if (queueIndex > 0) player.moveMediaItem(queueIndex, queueIndex - 1)
                                    })
                                    Text("↓", color = muted, fontSize = 14.sp, modifier = Modifier.clickable {
                                        if (queueIndex >= 0 && queueIndex < player.mediaItemCount - 1) player.moveMediaItem(queueIndex, queueIndex + 1)
                                    })
                                    Spacer(Modifier.width(5.dp))
                                    Text("×", color = muted, fontSize = 18.sp, modifier = Modifier.clickable {
                                        if (queueIndex >= 0) player.removeMediaItem(queueIndex)
                                    })
                                }
                            }
                            if (queuedSongs.isEmpty()) item { Text("Queue is empty. Play a song to get started.", color = muted, fontSize = 11.sp) }
                        }
                    }
                    isAdd -> Column {
                        if (playlists.isEmpty()) Text("Create a playlist first from Library.", color = muted, fontSize = 11.sp)
                        playlists.forEach { name ->
                            Text(name, color = fg, modifier = Modifier.fillMaxWidth().clickable {
                                val set = prefs.getStringSet("playlist_" + name, emptySet())?.toMutableSet() ?: mutableSetOf()
                                current?.let { set.add(it.id.toString()) }; prefs.edit().putStringSet("playlist_" + name, set).apply(); utility = ""
                            }.padding(vertical = 8.dp))
                        }
                    }
                    utility == "about" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.GraphicEq, null, tint = accent, modifier = Modifier.size(42.dp).align(Alignment.CenterHorizontally))
                        Text("REET Music Player", color = fg, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Text("Version 1.0", color = accent, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Text("An offline music player for your own music library. Play local songs, create playlists and save favourites without streaming.", color = muted, fontSize = 11.sp, textAlign = TextAlign.Center)
                        Text("Made with love for music lovers.", color = fg, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                    else -> {
                        var effectsEnabled by remember(audioSessionId) { mutableStateOf(equalizer?.enabled == true) }
                        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Reet Audio Engine", color = fg, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(if (audioSessionId > 0 && equalizer != null) "Live effects · Session $audioSessionId" else "Play a song to attach audio effects", color = muted, fontSize = 10.sp)
                                }
                                Switch(checked = effectsEnabled, enabled = equalizer != null, onCheckedChange = { enabled ->
                                    effectsEnabled = enabled
                                    runCatching { equalizer?.enabled = enabled; bassBoost?.enabled = enabled; virtualizer?.enabled = enabled }
                                })
                            }
                            if (equalizer != null) {
                                Text("PRESETS", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    (0 until equalizer.numberOfPresets.toInt()).forEach { preset ->
                                        val name = runCatching { equalizer.getPresetName(preset.toShort()).toString() }.getOrDefault("Preset $preset")
                                        Text(name, color = if (selectedEqPreset == name) Color.White else fg, fontSize = 10.sp,
                                            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (selectedEqPreset == name) accent else panel).clickable {
                                                runCatching { equalizer.usePreset(preset.toShort()) }
                                                eqBandLevels = (0 until equalizer.numberOfBands.toInt()).map { equalizer.getBandLevel(it.toShort()) / 100f }
                                                selectedEqPreset = name; prefs.edit().putString("eq_preset", name).apply()
                                            }.padding(horizontal = 12.dp, vertical = 9.dp))
                                    }
                                    Text("Flat", color = fg, fontSize = 10.sp, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(panel).clickable {
                                        runCatching { for (band in 0 until equalizer.numberOfBands.toInt()) equalizer.setBandLevel(band.toShort(), 0) }
                                        eqBandLevels = (0 until equalizer.numberOfBands.toInt()).map { 0f }; selectedEqPreset = "Flat"
                                    }.padding(horizontal = 12.dp, vertical = 9.dp))
                                }
                                Text("FREQUENCY BANDS", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    (0 until equalizer.numberOfBands.toInt()).forEach { band ->
                                        val range = equalizer.bandLevelRange
                                        val minDb = range[0].toFloat() / 100f; val maxDb = range[1].toFloat() / 100f
                                        val value = eqBandLevels.getOrNull(band) ?: 0f
                                        Column(Modifier.width(46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            VerticalEqualizerBand(value.coerceIn(minDb, maxDb), minDb, maxDb, accent, fg.copy(alpha = .18f), Modifier.width(38.dp).height(170.dp)) { next ->
                                                val copy = eqBandLevels.toMutableList(); if (band < copy.size) copy[band] = next
                                                eqBandLevels = copy; selectedEqPreset = "Custom"
                                                prefs.edit().putFloat("eq_band_$band", next).putString("eq_preset", "Custom").apply()
                                                runCatching { equalizer.setBandLevel(band.toShort(), (next * 100f).toInt().coerceIn(range[0].toInt(), range[1].toInt()).toShort()) }
                                            }
                                            val hz = runCatching { equalizer.getCenterFreq(band.toShort()) / 1000 }.getOrDefault(0)
                                            Text(if (hz >= 1000) String.format(Locale.getDefault(), "%.1f k", hz / 1000f) else "$hz", color = muted, fontSize = 9.sp, maxLines = 1)
                                            Text(String.format(Locale.getDefault(), "%.1f", value), color = fg, fontSize = 9.sp)
                                        }
                                    }
                                }
                            } else Text("Equalizer is unavailable until playback starts. Start a song and reopen this panel.", color = muted, fontSize = 11.sp)
                            HorizontalDivider(color = glassStroke)
                            Column {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Bass Boost", color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Text("${bassLevel.toInt() / 10}%", color = accent, fontSize = 11.sp)
                                }
                                Slider(value = bassLevel, onValueChange = { bassLevel = it; prefs.edit().putInt("eq_bass", it.toInt()).apply() }, enabled = bassBoost != null, valueRange = 0f..1000f, colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))
                                Text("Adds low-frequency punch", color = muted, fontSize = 9.sp)
                            }
                            Column {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Treble", color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Text("${trebleLevel.toInt() / 10}%", color = accent, fontSize = 11.sp)
                                }
                                Slider(value = trebleLevel, onValueChange = { value ->
                                    trebleLevel = value; prefs.edit().putInt("eq_treble", value.toInt()).apply()
                                    runCatching { val last = (equalizer?.numberOfBands?.toInt() ?: 0) - 1; if (last >= 0) { val range = equalizer!!.bandLevelRange; equalizer.setBandLevel(last.toShort(), (range[0] + (range[1] - range[0]) * (value / 1000f)).toInt().toShort()) } }
                                    selectedEqPreset = "Custom"
                                }, enabled = equalizer != null, valueRange = 0f..1000f, colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))
                                Text("Shapes high-frequency detail", color = muted, fontSize = 9.sp)
                            }
                            Column {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("3D Surround", color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Text("${virtualLevel.toInt() / 10}%", color = accent, fontSize = 11.sp)
                                }
                                Slider(value = virtualLevel, onValueChange = { virtualLevel = it; prefs.edit().putInt("eq_virtual", it.toInt()).apply() }, enabled = virtualizer != null, valueRange = 0f..1000f, colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))
                                Text("Widens stereo field where supported", color = muted, fontSize = 9.sp)
                            }
                            Text("Reset all effects", color = accent, fontSize = 11.sp, modifier = Modifier.align(Alignment.End).clickable {
                                runCatching { equalizer?.usePreset(0) }
                                eqBandLevels = (0 until (equalizer?.numberOfBands?.toInt() ?: 0)).map { 0f }
                                bassLevel = 0f; trebleLevel = 500f; virtualLevel = 0f; selectedEqPreset = "Custom"
                                prefs.edit().putInt("eq_bass", 0).putInt("eq_treble", 500).putInt("eq_virtual", 0).putString("eq_preset", "Custom").apply()
                            })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { utility = "" }) { Text("Close", color = accent) } })
    }
    if (showSleepTimer) {
        AlertDialog(
            onDismissRequest = { showSleepTimer = false },
            containerColor = panel,
            title = { Text("Sleep timer", color = fg) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (sleepTimerSeconds > 0L) "Timer active: ${sleepTimerSeconds / 60} min ${sleepTimerSeconds % 60} sec" else "Pause playback automatically after:", color = muted, fontSize = 12.sp)
                    listOf(15, 30, 45, 60, 90).forEach { minutes ->
                        Text("${minutes} minutes", color = fg, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { sleepTimerSeconds = minutes * 60L; showSleepTimer = false }
                            .padding(vertical = 9.dp, horizontal = 10.dp))
                    }
                    if (sleepTimerSeconds > 0L) Text("Cancel timer", color = accent, modifier = Modifier.clickable {
                        sleepTimerSeconds = 0L; showSleepTimer = false
                    }.padding(vertical = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { showSleepTimer = false }) { Text("Close", color = accent) } }
        )
    }
    if (metadataEdit && current != null) {
        AlertDialog(
            onDismissRequest = { metadataEdit = false },
            containerColor = panel,
            title = { Text("Edit song details", color = fg) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedTextField(value = metadataTitle, onValueChange = { metadataTitle = it }, singleLine = true, label = { Text("Song title") })
                    OutlinedTextField(value = metadataArtist, onValueChange = { metadataArtist = it }, singleLine = true, label = { Text("Artist") })
                    OutlinedTextField(value = metadataAlbum, onValueChange = { metadataAlbum = it }, singleLine = true, label = { Text("Album") })
                    Text("Choose custom album artwork", color = accent, modifier = Modifier.clickable { artworkPicker.launch(arrayOf("image/*")) }.padding(vertical = 8.dp))
                    Text(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "Android will ask permission to update the audio file tags. If access is unavailable, edits stay inside Reet Music." else "On this Android version, edits are saved inside Reet Music; original audio tags are unchanged.", color = muted, fontSize = 10.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = editingSongId
                    val song = current
                    if (id != null && song != null) {
                        val title = metadataTitle.trim().ifBlank { song.title }
                        val artist = metadataArtist.trim().ifBlank { song.artist }
                        val album = metadataAlbum.trim().ifBlank { song.album }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && song.uri.scheme == "content") {
                            pendingTagValues = mapOf("id" to id.toString(), "title" to title, "artist" to artist, "album" to album)
                            runCatching {
                                val request = MediaStore.createWriteRequest(context.contentResolver, listOf(song.uri))
                                writeTagsLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                            }.onFailure {
                                pendingTagValues = null
                                persistSongMetadata(id, title, artist, album)
                                metadataEdit = false
                            }
                        } else {
                            persistSongMetadata(id, title, artist, album)
                            metadataEdit = false
                        }
                    } else metadataEdit = false
                }) { Text("Save", color = accent) }
            },
            dismissButton = { TextButton(onClick = { metadataEdit = false }) { Text("Cancel", color = muted) } }
        )
    }

    if (lyricsEdit && current != null) {
        var draft by remember(current?.id) { mutableStateOf(lyrics) }
        AlertDialog(onDismissRequest = { lyricsEdit = false }, containerColor = panel,
            title = { Text("Save offline lyrics", color = fg) },
            text = { Column {
                OutlinedTextField(value = draft, onValueChange = { draft = it }, minLines = 5, maxLines = 9, label = { Text("Paste plain lyrics or timestamped .LRC") })
            } },
            confirmButton = { TextButton(onClick = {
                lyrics = draft; prefs.edit().putString("lyrics_" + current!!.id, draft).apply(); lyricsEdit = false
            }) { Text("Save", color = accent) } },
            dismissButton = { TextButton(onClick = { lyricsEdit = false }) { Text("Cancel", color = muted) } })
    }
    if (playlistDialog) {
        AlertDialog(onDismissRequest = { playlistDialog = false }, containerColor = panel,
            title = { Text("Create playlist", color = fg) },
            text = { OutlinedTextField(value = playlistName, onValueChange = { playlistName = it }, singleLine = true, label = { Text("Playlist name") }) },
            confirmButton = { TextButton(onClick = {
                val name = playlistName.trim()
                if (name.isNotEmpty() && !playlists.contains(name)) {
                    playlists = (playlists + name).sorted()
                    prefs.edit().putStringSet("playlist_names", playlists.toSet()).apply()
                    current?.let { s -> prefs.edit().putStringSet("playlist_" + name, setOf(s.id.toString())).apply() }
                }
                playlistDialog = false
            }) { Text("Create", color = accent) } },
            dismissButton = { TextButton(onClick = { playlistDialog = false }) { Text("Cancel", color = muted) } })
    }
    if (showSettings) {
        Dialog(onDismissRequest = { showSettings = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val settingsVisibility = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
            AnimatedVisibility(
                visibleState = settingsVisibility,
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -it }, animationSpec = tween(360)) + androidx.compose.animation.fadeIn(tween(260)),
                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(240)) + androidx.compose.animation.fadeOut(tween(180))
            ) {
                Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircleAction(Icons.Default.ArrowBack, fg, panel, size = 44) { showSettings = false }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Settings", color = fg, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Make Reet yours", color = muted, fontSize = 11.sp)
                        }
                    }
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("THEME & APPEARANCE", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text("Light theme", color = fg, fontSize = 13.sp); Text("Choose light or dark appearance", color = muted, fontSize = 10.sp) }
                            Switch(checked = lightMode, onCheckedChange = { lightMode = it; prefs.edit().putBoolean("light_mode", it).apply() })
                        }
                        Text("WALLPAPER MODE", color = muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf("AUTO" to "Auto", "FIXED" to "Fixed", "OFF" to "Off").forEach { (mode, label) ->
                                Text(label, color = if (wallpaperMode == mode) Color.White else muted, fontSize = 11.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (wallpaperMode == mode) accent.copy(alpha = .72f) else panel)
                                        .clickable { wallpaperMode = mode; prefs.edit().putString("wallpaper_mode", mode).apply() }.padding(horizontal = 15.dp, vertical = 11.dp))
                            }
                        }
                        if (wallpaperMode == "FIXED" && wallpaperNames.isNotEmpty()) {
                            Text("CHOOSE WALLPAPER", color = muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                                wallpaperNames.forEach { name -> Text(name.substringBeforeLast('.').replace('_', ' '), color = if (fixedWallpaper == name) accent else fg, fontSize = 11.sp,
                                    modifier = Modifier.fillMaxWidth().clickable { fixedWallpaper = name; prefs.edit().putString("fixed_wallpaper", name).apply() }.padding(vertical = 7.dp)) }
                            }
                        }
                        Text("ACCENT COLOUR", color = muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            listOf("Sky Blue" to Color(0xFF29B6F6), "Purple" to Color(0xFFB99AFF), "Blue" to Color(0xFF76BFFF), "Pink" to Pink, "Mint" to Color(0xFF65DDB5), "Gold" to Color(0xFFE8C16C)).forEach { (name, color) ->
                                Box(Modifier.size(31.dp).clip(CircleShape).background(color).border(if (accentName == name) 3.dp else 0.dp, fg, CircleShape)
                                    .clickable { accentName = name; prefs.edit().putString("accent", name).apply() })
                            }
                        }
                        HorizontalDivider(color = glassStroke)
                        Text("PLAYLIST FILES", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Text("Import or export standard M3U playlists for songs already in your device library.", color = muted, fontSize = 10.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Import M3U", color = fg, fontSize = 11.sp, modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(panel).clickable { importM3uLauncher.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "text/plain", "application/octet-stream")) }.padding(13.dp), textAlign = TextAlign.Center)
                            Text("Export M3U", color = fg, fontSize = 11.sp, modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(panel).clickable { exportM3uLauncher.launch("Reet-Playlist.m3u") }.padding(13.dp), textAlign = TextAlign.Center)
                        }
                        HorizontalDivider(color = glassStroke)
                        Text("AUDIO & PLAYBACK", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text("Skip tracks under 60 seconds", color = fg, fontSize = 12.sp); Text("Automatically skip short audio clips", color = muted, fontSize = 10.sp) }
                            Switch(checked = skipShortTracks, onCheckedChange = { skipShortTracks = it; prefs.edit().putBoolean("skip_under_60s", it).apply() })
                        }
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showSettings = false; utility = "equalizer" }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GraphicEq, null, tint = accent); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Text("Equalizer", color = fg, fontSize = 12.sp); Text("Open device sound effects and EQ", color = muted, fontSize = 10.sp) }
                            Icon(Icons.Default.ChevronRight, null, tint = muted)
                        }
                        HorizontalDivider(color = glassStroke)
                        Text("EQUALIZER · BASS · TREBLE", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Text("Audio effects apply to the active music session.", color = muted, fontSize = 10.sp)
                        if (equalizer != null) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                (0 until equalizer.numberOfPresets.toInt()).forEach { preset ->
                                    val name = runCatching { equalizer.getPresetName(preset.toShort()).toString() }.getOrDefault("Preset $preset")
                                    Text(name, color = if (selectedEqPreset == name) Color.White else muted, fontSize = 10.sp,
                                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (selectedEqPreset == name) accent else panel)
                                            .clickable {
                                                runCatching { equalizer.usePreset(preset.toShort()) }
                                                eqBandLevels = (0 until equalizer.numberOfBands.toInt()).map { equalizer.getBandLevel(it.toShort()) / 100f }
                                                selectedEqPreset = name; prefs.edit().putString("eq_preset", name).apply()
                                            }.padding(horizontal = 12.dp, vertical = 8.dp))
                                }
                                Text("Custom", color = if (selectedEqPreset == "Custom") Color.White else muted, fontSize = 10.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (selectedEqPreset == "Custom") accent else panel)
                                        .clickable { selectedEqPreset = "Custom" }.padding(horizontal = 12.dp, vertical = 8.dp))
                            }
                            val bandCount = equalizer.numberOfBands.toInt()
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                (0 until bandCount).forEach { band ->
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        val range = equalizer.bandLevelRange
                                        val minDb = range[0].toFloat() / 100f
                                        val maxDb = range[1].toFloat() / 100f
                                        val value = eqBandLevels.getOrNull(band) ?: 0f
                                        VerticalEqualizerBand(
                                            value = value.coerceIn(minDb, maxDb), min = minDb, max = maxDb,
                                            accent = accent, track = fg.copy(alpha = .18f),
                                            modifier = Modifier.width(38.dp).height(174.dp),
                                            onValueChange = { next ->
                                                val copy = eqBandLevels.toMutableList()
                                                if (band < copy.size) copy[band] = next
                                                eqBandLevels = copy; selectedEqPreset = "Custom"
                                                prefs.edit().putFloat("eq_band_$band", next).putString("eq_preset", "Custom").apply()
                                                runCatching { equalizer.setBandLevel(band.toShort(), (next * 100f).toInt().coerceIn(range[0].toInt(), range[1].toInt()).toShort()) }
                                            })
                                        val centerHz = runCatching { equalizer.getCenterFreq(band.toShort()) / 1000 }.getOrDefault(0)
                                        val frequencyLabel = if (centerHz >= 1000) String.format(Locale.getDefault(), "%.1f kHz", centerHz / 1000f) else "$centerHz Hz"
                                        Text(frequencyLabel, color = muted, fontSize = 8.sp, maxLines = 1)
                                        Text(String.format(Locale.getDefault(), "%.1f", value) + " dB", color = fg, fontSize = 9.sp)
                                    }
                                }
                            }
                        } else Text("Start playback to enable the equalizer.", color = muted, fontSize = 11.sp)
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Bass Boost", color = fg, fontSize = 11.sp); Text("${bassLevel.toInt() / 10}%", color = accent, fontSize = 11.sp)
                            }
                            Slider(value = bassLevel, onValueChange = { bassLevel = it; prefs.edit().putInt("eq_bass", it.toInt()).apply() },
                                valueRange = 0f..1000f, colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))
                        }
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Treble", color = fg, fontSize = 11.sp); Text("${trebleLevel.toInt() / 10}%", color = accent, fontSize = 11.sp)
                            }
                            Slider(value = trebleLevel, onValueChange = {
                                trebleLevel = it; prefs.edit().putInt("eq_treble", it.toInt()).apply()
                                runCatching {
                                    val lastBand = (equalizer?.numberOfBands?.toInt() ?: 0) - 1
                                    if (lastBand >= 0) {
                                        val range = equalizer!!.bandLevelRange
                                        val level = (range[0] + (range[1] - range[0]) * (it / 1000f)).toInt().toShort()
                                        equalizer.setBandLevel(lastBand.toShort(), level)
                                    }
                                }
                                selectedEqPreset = "Custom"
                            }, valueRange = 0f..1000f, colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))
                        }
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("3D / Surround", color = fg, fontSize = 11.sp); Text("${virtualLevel.toInt() / 10}%", color = accent, fontSize = 11.sp)
                            }
                            Slider(value = virtualLevel, onValueChange = { virtualLevel = it; prefs.edit().putInt("eq_virtual", it.toInt()).apply() },
                                valueRange = 0f..1000f, colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))
                        }
                        Text("Reset equalizer", color = accent, fontSize = 11.sp, modifier = Modifier.clickable {
                            runCatching { equalizer?.usePreset(0) }
                            eqBandLevels = (0 until (equalizer?.numberOfBands?.toInt() ?: 0)).map { 0f }
                            bassLevel = 0f; trebleLevel = 500f; virtualLevel = 0f; selectedEqPreset = "Custom"
                            prefs.edit().putInt("eq_bass", 0).putInt("eq_treble", 500).putInt("eq_virtual", 0).putString("eq_preset", "Custom").apply()
                        })
                        HorizontalDivider(color = glassStroke)
                        Text("HOME SCREEN MUSIC WIDGET", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Text("Live previews show how each size will look on your Home screen.", color = muted, fontSize = 10.sp, lineHeight = 15.sp)
                        listOf("Compact", "Medium", "Large").forEach { mode ->
                            val isCompact = mode == "Compact"
                            val isLarge = mode == "Large"
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                .background(Brush.linearGradient(listOf(accent.copy(alpha = .25f), panel, Color.Black.copy(alpha = .16f))))
                                .border(1.dp, glassStroke, RoundedCornerShape(18.dp)).padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    current?.let { SongArtwork(it.albumId, accent, if (isCompact) 34 else 46, prefs.getString("custom_art_" + it.id, null)) }
                                        ?: ArtworkPlaceholder(accent, if (isCompact) 34 else 46)
                                    Spacer(Modifier.width(9.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(current?.title ?: "Reet Music Player", color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (!isCompact) Text(current?.artist ?: "Your offline music", color = muted, fontSize = 9.sp, maxLines = 1)
                                    }
                                    if (isLarge) Icon(Icons.Default.GraphicEq, null, tint = accent, modifier = Modifier.size(20.dp))
                                }
                                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.SkipPrevious, "Previous", tint = fg, modifier = Modifier.size(if (isCompact) 17.dp else 21.dp))
                                    Icon(if (playing) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled, "Play or pause", tint = accent, modifier = Modifier.size(if (isCompact) 23.dp else 29.dp))
                                    Icon(Icons.Default.SkipNext, "Next", tint = fg, modifier = Modifier.size(if (isCompact) 17.dp else 21.dp))
                                    Icon(Icons.Default.FavoriteBorder, "Favourite", tint = accent, modifier = Modifier.size(if (isCompact) 17.dp else 21.dp))
                                }
                                Text("$mode preview", color = muted, fontSize = 8.sp, modifier = Modifier.align(Alignment.End))
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf("Compact", "Medium", "Large").forEach { mode ->
                                Text("Add $mode", color = Color.White, fontSize = 10.sp,
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(accent)
                                        .clickable {
                                            val provider = when (mode) {
                                                "Compact" -> ReetCompactWidgetProvider::class.java
                                                "Large" -> ReetLargeWidgetProvider::class.java
                                                else -> ReetMusicWidgetProvider::class.java
                                            }
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, provider), null, null)
                                            } else {
                                                context.startActivity(Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                            }
                                        }.padding(vertical = 10.dp), textAlign = TextAlign.Center)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf("Compact", "Medium", "Large").forEach { mode ->
                                Text(mode, color = if (widgetLayout == mode) Color.White else muted, fontSize = 10.sp,
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(13.dp))
                                        .background(if (widgetLayout == mode) accent else panel)
                                        .clickable {
                                            widgetLayout = mode
                                            prefs.edit().putString("widget_layout", mode).apply()
                                            ReetMusicWidgetProvider.updateAll(context)
                                        }.padding(vertical = 10.dp), textAlign = TextAlign.Center)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Auto-change Marvel wallpaper", color = fg, fontSize = 12.sp)
                                Text("Switch background when the song changes", color = muted, fontSize = 10.sp)
                            }
                            Switch(checked = widgetAutoWallpaper, onCheckedChange = {
                                widgetAutoWallpaper = it
                                prefs.edit().putBoolean("widget_wallpaper_auto", it).apply()
                                ReetMusicWidgetProvider.updateAll(context)
                            })
                        }
                        if (!widgetAutoWallpaper && wallpaperNames.isNotEmpty()) {
                            Text("WIDGET BACKGROUND", color = muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            val widgetWallIndex = wallpaperNames.indexOf(widgetWallpaper).let { if (it >= 0) it else 0 }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("‹", color = accent, fontSize = 26.sp, modifier = Modifier.clickable {
                                    val next = (widgetWallIndex - 1 + wallpaperNames.size) % wallpaperNames.size
                                    widgetWallpaper = wallpaperNames[next]
                                    prefs.edit().putString("widget_wallpaper", widgetWallpaper).apply()
                                    ReetMusicWidgetProvider.updateAll(context)
                                }.padding(horizontal = 12.dp, vertical = 6.dp))
                                Text(wallpaperNames[widgetWallIndex].substringBeforeLast('.').replace('_', ' '),
                                    color = fg, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("›", color = accent, fontSize = 26.sp, modifier = Modifier.clickable {
                                    val next = (widgetWallIndex + 1) % wallpaperNames.size
                                    widgetWallpaper = wallpaperNames[next]
                                    prefs.edit().putString("widget_wallpaper", widgetWallpaper).apply()
                                    ReetMusicWidgetProvider.updateAll(context)
                                }.padding(horizontal = 12.dp, vertical = 6.dp))
                            }
                        } else if (widgetAutoWallpaper) {
                            Text("31 bundled Marvel-style backgrounds rotate with your songs.", color = muted, fontSize = 10.sp)
                        }
                        HorizontalDivider(color = glassStroke)
                        Text("LIBRARY", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Row(Modifier.fillMaxWidth().clickable { scan(); showSettings = false }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, null, tint = accent); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Text("Scan Music Library", color = fg, fontSize = 12.sp); Text("Refresh offline songs", color = muted, fontSize = 10.sp) }
                            Icon(Icons.Default.ChevronRight, null, tint = muted)
                        }
                        Text("Sort order: " + sort + if (ascending) " ↑" else " ↓", color = fg, fontSize = 11.sp)
                        HorizontalDivider(color = glassStroke)
                        Text("ABOUT", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showSettings = false; utility = "about" }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = accent); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Text("About Reet Music", color = fg, fontSize = 12.sp); Text("App information and version", color = muted, fontSize = 10.sp) }
                            Icon(Icons.Default.ChevronRight, null, tint = muted)
                        }
                        Text("Offline mode · No streaming or account required.", color = muted, fontSize = 10.sp)
                        Spacer(Modifier.height(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalEqualizerBand(
    value: Float,
    min: Float,
    max: Float,
    accent: Color,
    track: Color,
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit
) {
    val fraction = ((value - min) / (max - min).coerceAtLeast(.001f)).coerceIn(0f, 1f)
    Canvas(modifier.pointerInput(min, max) {
        detectDragGestures { change, _ ->
            change.consume()
            val next = (1f - (change.position.y / size.height).coerceIn(0f, 1f))
            onValueChange(min + (max - min) * next)
        }
    }) {
        val x = size.width / 2f
        val top = 10.dp.toPx()
        val bottom = size.height - 10.dp.toPx()
        val center = (top + bottom) / 2f
        val y = bottom - (bottom - top) * fraction
        drawLine(track, Offset(x, top), Offset(x, bottom), strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
        drawLine(accent.copy(alpha = .92f), Offset(x, center), Offset(x, y), strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(accent.copy(alpha = .18f), radius = 13.dp.toPx(), center = Offset(x, y))
        drawCircle(accent, radius = 6.dp.toPx(), center = Offset(x, y))
        drawLine(track.copy(alpha = .75f), Offset(x - 8.dp.toPx(), center), Offset(x + 8.dp.toPx(), center), strokeWidth = 1.dp.toPx())
    }
}

@Composable
private fun rememberArtwork(albumId: Long): Bitmap? {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = albumId) {
        value = withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse("content://media/external/audio/albumart/" + albumId)
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) { null }
        }
    }
    return bitmap
}

@Composable
private fun SongArtwork(albumId: Long, accent: Color, size: Int, customArtUri: String? = null) {
    val context = LocalContext.current
    val customBitmap by produceState<Bitmap?>(initialValue = null, key1 = customArtUri) {
        value = withContext(Dispatchers.IO) {
            if (customArtUri.isNullOrBlank()) null else runCatching {
                context.contentResolver.openInputStream(Uri.parse(customArtUri))?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }
    val bitmap = customBitmap ?: rememberArtwork(albumId)
    if (bitmap != null) {
        androidx.compose.foundation.Image(bitmap.asImageBitmap(), null,
            modifier = Modifier.size(size.dp).clip(RoundedCornerShape((size / 4).coerceAtLeast(8).dp)))
    } else ArtworkPlaceholder(accent, size)
}

@Composable
private fun ArtworkPlaceholder(accent: Color, size: Int) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size / 4).coerceAtLeast(9).dp))
        .background(Brush.linearGradient(listOf(accent.copy(alpha = .9f), Color(0xFF573744), Color(0xFF25212A)))),
        contentAlignment = Alignment.Center) {
        Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(alpha = .9f), modifier = Modifier.size((size * .48f).dp))
    }
}

/**
 * Reusable soft white-light press animation for tappable surfaces.
 * Keeps the existing screen/layout intact while adding a gentle glassy
 * scale response and a brief white glow on touch.
 */
private fun Modifier.softWhiteTap(onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "softTapScale"
    )
    val glow by animateFloatAsState(
        targetValue = if (pressed) 0.28f else 0f,
        animationSpec = tween(if (pressed) 90 else 260),
        label = "softTapGlow"
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .drawWithContent {
            drawContent()
            if (glow > 0.01f) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = glow), Color.Transparent),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.maxDimension * 0.78f
                    )
                )
            }
        }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

@Composable
private fun CircleAction(icon: androidx.compose.ui.graphics.vector.ImageVector, fg: Color, panel: Color, size: Int = 40, onClick: () -> Unit) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(panel).border(1.dp, Line, CircleShape).softWhiteTap(onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun PlayerTool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, accent: Color = Pink, textColor: Color = SoftText, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.softWhiteTap(onClick).padding(horizontal = 8.dp)) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(21.dp))
        Text(label, color = textColor, fontSize = 9.sp)
    }
}

@Composable
private fun EqualizerBars(accent: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val values = (0..19).map { i -> transition.animateFloat(
        initialValue = .18f + (i % 4) * .08f, targetValue = .35f + ((i * 7) % 6) * .1f,
        animationSpec = infiniteRepeatable(tween(380 + i * 31), RepeatMode.Reverse), label = "bar$i"
    ) }
    Canvas(modifier) {
        val gap = size.width / 55f
        val barWidth = (size.width - gap * 19) / 20f
        values.forEachIndexed { i, v ->
            val h = size.height * v.value
            drawRoundRect(accent.copy(alpha = .55f + (i % 3) * .15f),
                topLeft = androidx.compose.ui.geometry.Offset(i * (barWidth + gap), (size.height - h) / 2),
                size = androidx.compose.ui.geometry.Size(barWidth, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2))
        }
    }
}

@Composable
private fun PulsingLines(active: Boolean, accent: Color, modifier: Modifier = Modifier) {
    val motion = rememberInfiniteTransition(label = "progressWave")
    val phase by motion.animateFloat(0f, (PI * 2f).toFloat(),
        infiniteRepeatable(tween(if (active) 1050 else 2600, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase")
    Canvas(modifier) {
        val count = 34
        val slot = size.width / count
        val barWidth = slot * .42f
        for (i in 0 until count) {
            val wave = sin(phase + i * .48f) * .5f + .5f
            val heightFactor = if (active) .16f + wave * .72f else .16f + wave * .12f
            val barHeight = size.height * heightFactor
            drawRoundRect(
                color = (if (i % 3 == 0) accent else Color.White).copy(alpha = if (active) .62f else .34f),
                topLeft = Offset(i * slot + (slot - barWidth) / 2f, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight), cornerRadius = CornerRadius(barWidth / 2f))
        }
    }
}

private fun formatTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1000L
    return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
}