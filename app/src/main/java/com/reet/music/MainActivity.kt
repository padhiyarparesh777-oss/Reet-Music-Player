package com.reet.music

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.core.content.ContextCompat
import androidx.palette.graphics.Palette
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

private val Pink = Color(0xFFFF78A8)
private val Charcoal = Color(0xFF171419)
private val Deep = Color(0xFF100E12)
private val SoftText = Color(0xFFAAA2AA)
private val Glass = Color(0xFF252128)
private val Line = Color(0xFF403640)
private val WaterTint = Color(0xFFBDEBFF)

private data class Song(
    val id: Long, val title: String, val artist: String, val album: String,
    val duration: Long, val folder: String, val albumId: Long, val uri: Uri,
    val fileSize: Long = 0L, val dateAdded: Long = 0L
)

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.attributes = window.attributes.apply { preferredRefreshRate = 120f }
        }
        player = ExoPlayer.Builder(this).build()
        setContent { ReetApp(player, ::loadDeviceSongs) }
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
    override fun onDestroy() { player.release(); super.onDestroy() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReetApp(player: ExoPlayer, loadSongs: () -> List<Song>) {
    val context = LocalContext.current
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
    var playlistDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var showPlayer by remember { mutableStateOf(false) }
    var utility by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(prefs.getString("sort", "Name") ?: "Name") }
    var ascending by remember { mutableStateOf(true) }
    var showSortOptions by remember { mutableStateOf(false) }
    var lightMode by remember { mutableStateOf(prefs.getBoolean("light_mode", false)) }
    var accentName by remember { mutableStateOf(prefs.getString("accent", "Pink") ?: "Pink") }
    var lyrics by remember { mutableStateOf("") }
    var lyricsEdit by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    val accent = when (accentName) {
        "Purple" -> Color(0xFFB99AFF)
        "Blue" -> Color(0xFF76BFFF)
        "Red" -> Color(0xFFFF6474)
        "Mint" -> Color(0xFF65DDB5)
        "Gold" -> Color(0xFFE8C16C)
        else -> Pink
    }
    val bg = if (lightMode) Color(0xFFF7F2F5) else Charcoal
    val panel = if (lightMode) Color(0xBFFFFFFF) else Color(0x702B3440)
    val glassStroke = if (lightMode) Color.White.copy(alpha = .92f) else Color.White.copy(alpha = .30f)
    val fg = if (lightMode) Color(0xFF211A20) else Color(0xFFF8F1F5)
    val muted = if (lightMode) Color(0xFF817680) else SoftText
    val audioPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    fun scan() { songs = loadSongs() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission = it
        if (it) scan()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                current = songs.firstOrNull { it.id.toString() == mediaItem?.mediaId }
                current?.let { s ->
                    recent = (listOf(s.id.toString()) + recent.filterNot { it == s.id.toString() }).take(100)
                    prefs.edit().putString("recent", recent.joinToString("|")).apply()
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
    LaunchedEffect(current?.id) {
        val id = current?.id?.toString() ?: return@LaunchedEffect
        lyrics = prefs.getString("lyrics_" + id, "") ?: ""
    }

    val source = when {
        tab == "Favourites" -> songs.filter { favorites.contains(it.id.toString()) }
        (tab == "Library" || tab == "Playlists") && selectedPlaylist != null -> when (selectedPlaylist) {
            "__recent__" -> recent.mapNotNull { id -> songs.firstOrNull { it.id.toString() == id } }
            else -> songs.filter { prefs.getStringSet("playlist_" + selectedPlaylist, emptySet())?.contains(it.id.toString()) == true }
        }
        else -> songs
    }
    val visible = remember(source, query, sort, ascending, mediaCategory) {
        val found = source.filter { query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }
        val ordered = when (sort) {
            "Artist" -> found.sortedBy { it.artist.lowercase(Locale.getDefault()) }
            "Album" -> found.sortedBy { it.album.lowercase(Locale.getDefault()) }
            "Time" -> found.sortedByDescending { it.dateAdded }
            "Size" -> found.sortedByDescending { it.fileSize }
            "Length" -> found.sortedBy { it.duration }
            else -> found.sortedBy { it.title.lowercase(Locale.getDefault()) }
        }
        if (ascending) ordered else ordered.reversed()
    }

    MaterialTheme(colorScheme = if (lightMode) lightColorScheme(primary = accent, background = bg, surface = panel, onSurface = fg) else darkColorScheme(primary = accent, background = bg, surface = panel, onSurface = fg)) {
      CompositionLocalProvider(LocalIndication provides ripple(color = Color.White.copy(alpha = .48f))) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(if (lightMode) Color(0xFFEAF8FF) else Color(0xFF17242D), bg, if (lightMode) Color(0xFFF5EFFF) else Color(0xFF101820))))) {
            // Water-glass atmosphere with soft cyan and violet light blooms.
            Box(Modifier.align(Alignment.TopEnd).padding(top = 45.dp, end = 4.dp).size(260.dp).clip(CircleShape).background(WaterTint.copy(alpha = if (lightMode) .32f else .20f)).blur(78.dp))
            Box(Modifier.align(Alignment.BottomStart).padding(bottom = 145.dp, start = 0.dp).size(220.dp).clip(CircleShape).background(Color(0xFFB6A7FF).copy(alpha = if (lightMode) .24f else .18f)).blur(82.dp))
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GraphicEq, null, tint = accent, modifier = Modifier.size(27.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("REET", color = fg, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.5).sp)
                            Text("Music Player", color = muted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    CircleAction(Icons.Default.Search, fg, panel) { tab = "Search"; mediaCategory = "Songs" }
                    Spacer(Modifier.width(8.dp))
                    CircleAction(Icons.Default.NotificationsNone, fg, panel) { scan() }
                }
                AnimatedVisibility(visible = current != null, enter = androidx.compose.animation.fadeIn(tween(220)) + androidx.compose.animation.expandVertically(tween(260)), exit = androidx.compose.animation.fadeOut(tween(160)) + androidx.compose.animation.shrinkVertically(tween(180))) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 9.dp).height(46.dp)
                            .clip(RoundedCornerShape(24.dp))
                             .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = .22f), panel.copy(alpha = .42f), accent.copy(alpha = .18f), Color.White.copy(alpha = .10f))))
                            .border(1.dp, Color.White.copy(alpha = .34f), RoundedCornerShape(24.dp))
                            .clickable { showPlayer = true }.padding(horizontal = 11.dp)
                            .animateContentSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(27.dp).clip(CircleShape).background(accent.copy(alpha = .22f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.GraphicEq, null, tint = accent, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(current?.title.orEmpty(), color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (playing) "NOW PLAYING" else "PAUSED", color = accent, fontSize = 7.sp, letterSpacing = 1.2.sp)
                        }
                        IconButton(onClick = { player.seekToPreviousMediaItem() }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.SkipPrevious, null, tint = fg, modifier = Modifier.size(17.dp)) }
                        IconButton(onClick = { if (playing) player.pause() else player.play() }, modifier = Modifier.size(30.dp)) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = accent, modifier = Modifier.size(19.dp)) }
                        IconButton(onClick = { player.seekToNextMediaItem() }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.SkipNext, null, tint = fg, modifier = Modifier.size(17.dp)) }
                        IconButton(onClick = {
                            current?.let { song ->
                                val key = song.id.toString()
                                favorites = if (favorites.contains(key)) favorites - key else favorites + key
                                prefs.edit().putStringSet("favorites", favorites).apply()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }, modifier = Modifier.size(30.dp)) {
                            Icon(if (current?.let { favorites.contains(it.id.toString()) } == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = accent, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    listOf("Songs", "Albums", "Artists", "Folders").forEach { category ->
                        val selected = mediaCategory == category
                        Text(
                            category,
                            color = if (selected) Color.White else muted,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(18.dp))
                                .background(if (selected) accent else panel.copy(alpha = .72f))
                                .border(1.dp, if (selected) accent else glassStroke, RoundedCornerShape(18.dp))
                                .clickable { mediaCategory = category; selectedPlaylist = null }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
                if (tab == "Search") Row(Modifier.fillMaxWidth().height(45.dp).clip(RoundedCornerShape(15.dp)).background(Brush.verticalGradient(listOf(panel.copy(alpha = .94f), panel.copy(alpha = .72f)))).border(1.dp, glassStroke, RoundedCornerShape(15.dp)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(color = fg, fontSize = 12.sp),
                        decorationBox = { inner -> Box { if (query.isEmpty()) Text("Search your offline music", color = muted, fontSize = 12.sp); inner() } })
                    if (query.isNotEmpty()) Icon(Icons.Default.Close, null, tint = muted, modifier = Modifier.size(16.dp).clickable { query = "" })
                }
                Spacer(Modifier.height(17.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(when { tab == "Home" -> "All Songs (" + songs.size + ")"; tab == "Search" -> "Search Results"; tab == "Playlists" -> "Your playlists"; tab == "Favourites" -> "Your favourites"; selectedPlaylist != null -> if (selectedPlaylist == "__recent__") "Recently played" else selectedPlaylist!!; else -> "Your library" }, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(visible.size.toString() + if (mediaCategory == "Songs") " tracks · Offline library" else " " + mediaCategory.lowercase(Locale.getDefault()), color = muted, fontSize = 10.sp)
                    }
                    Text("Sort: $sort", color = accent, fontSize = 10.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(panel.copy(alpha = .65f)).clickable { showSortOptions = true }.padding(horizontal = 10.dp, vertical = 8.dp))
                    CircleAction(Icons.Default.SwapVert, fg, panel, 34) { ascending = !ascending }
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
                                    current?.let { SongArtwork(it.albumId, accent, 42) }
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
                            Row(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(17.dp))
                                .background(if (active) Brush.horizontalGradient(listOf(Color.White.copy(alpha = .20f), accent.copy(alpha = .13f), panel.copy(alpha = .30f))) else Brush.horizontalGradient(listOf(Color.White.copy(alpha = .055f), Color.Transparent)))
                                .border(if (active) 1.dp else .6.dp, if (active) Color.White.copy(alpha = .48f) else glassStroke.copy(alpha = .35f), RoundedCornerShape(17.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    current = song
                                    val index = songs.indexOfFirst { it.id == song.id }
                                    player.setMediaItems(songs.map { MediaItem.Builder().setUri(it.uri).setMediaId(it.id.toString()).build() }, index.coerceAtLeast(0), 0L)
                                    player.prepare(); player.play(); showPlayer = true
                                }.padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                SongArtwork(song.albumId, accent, 42)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(when(mediaCategory) { "Albums" -> song.album; "Artists" -> song.artist; "Folders" -> song.folder; else -> song.title }, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (mediaCategory == "Songs") song.artist + " · " + song.album else songs.count { when(mediaCategory) { "Albums" -> it.album == song.album; "Artists" -> it.artist == song.artist; else -> it.folder == song.folder } }.toString() + " tracks", color = muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(formatTime(song.duration), color = muted, fontSize = 9.sp)
                                Spacer(Modifier.width(5.dp))
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
                AnimatedVisibility(visible = current != null) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp).height(62.dp).clip(RoundedCornerShape(18.dp))
                          .background(Brush.linearGradient(listOf(Color.White.copy(alpha = .22f), panel.copy(alpha = .42f), accent.copy(alpha = .17f)))).border(1.dp, Color.White.copy(alpha = .32f), RoundedCornerShape(22.dp))
                        .clickable { showPlayer = true }.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        current?.let { SongArtwork(it.albumId, accent, 42) } ?: ArtworkPlaceholder(accent, 42) 
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(current?.title.orEmpty(), color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(current?.artist.orEmpty(), color = muted, fontSize = 9.sp, maxLines = 1)
                        }
                        IconButton(onClick = { if (playing) player.pause() else player.play() }) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = accent) }
                        IconButton(onClick = { utility = "queue" }) { Icon(Icons.Default.QueueMusic, null, tint = fg) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 5.dp).clip(RoundedCornerShape(25.dp)).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .25f), panel.copy(alpha = .72f), Color.White.copy(alpha = .08f)))).border(1.dp, Color.White.copy(alpha = .48f), RoundedCornerShape(25.dp)).padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                    listOf(Triple("Home", Icons.Default.Home, "Home"), Triple("Search", Icons.Default.Search, "Search"), Triple("Playlists", Icons.Default.QueueMusic, "Playlists"), Triple("Library", Icons.Default.LibraryMusic, "Library")).forEach { (name, icon, label) ->
                        val selected = tab == name
                        Column(Modifier.weight(1f).clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            tab = name; selectedPlaylist = null
                            if (name != "Search") query = ""
                        }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icon, null, tint = if (selected) accent else muted, modifier = Modifier.size(19.dp))
                            Text(label, color = if (selected) accent else muted, fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            }
        }
      }
    }

    if (showSortOptions) {
        AlertDialog(onDismissRequest = { showSortOptions = false },
            containerColor = if (lightMode) Color(0xEFFFFFFF) else Color(0xE925202A),
            shape = RoundedCornerShape(28.dp),
            title = { Text("Sort your music", color = fg, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Name", "Time", "Size", "Length", "Artist", "Album").forEach { option ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
                            .background(if (sort == option) accent.copy(alpha = .18f) else Color.White.copy(alpha = .045f))
                            .clickable {
                                sort = option
                                prefs.edit().putString("sort", option).apply()
                                showSortOptions = false
                            }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(when(option) { "Name" -> "Song name"; "Time" -> "Recently added"; "Size" -> "File size"; "Length" -> "Duration"; else -> option }, color = fg, modifier = Modifier.weight(1f), fontSize = 13.sp)
                            if (sort == option) Icon(Icons.Default.CheckCircle, null, tint = accent)
                        }
                    }
                    Text("Order", color = muted, fontSize = 10.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Ascending" to true, "Descending" to false).forEach { (label, value) ->
                            Text(label, color = if (ascending == value) Color.White else muted, modifier = Modifier.clip(RoundedCornerShape(18.dp))
                                .background(if (ascending == value) accent else Glass).clickable { ascending = value }
                                .padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 10.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSortOptions = false }) { Text("Done", color = accent) } })
    }

    if (showPlayer && current != null) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showPlayer = false }, sheetState = sheet,
            containerColor = if (lightMode) Color(0xCFEAF8FF) else Color(0xC91A2731),
            scrimColor = Color.Black.copy(alpha = .48f),
            shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
            dragHandle = { Box(Modifier.padding(vertical = 11.dp).size(38.dp, 4.dp).clip(CircleShape).background(muted)) }) {
            val song = current!!
            val art = rememberArtwork(song.albumId)
            val dynamicAccent by produceState(initialValue = accent, key1 = art, key2 = accent) {
                value = withContext(Dispatchers.Default) {
                    try { art?.let { Palette.from(it).generate().vibrantSwatch?.rgb?.let { rgb -> Color(rgb) } } ?: accent }
                    catch (_: Exception) { accent }
                }
            }
            val artMotion = rememberInfiniteTransition(label = "artMotion")
            val artScale by artMotion.animateFloat(1f, 1.035f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "artScale")
            Column(Modifier.fillMaxWidth().fillMaxHeight(.91f).padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Playing from", color = muted, fontSize = 10.sp)
                        Text("My Songs", color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = { showPlayer = false }) { Icon(Icons.Default.KeyboardArrowDown, null, tint = fg) }
                    IconButton(onClick = { utility = "queue" }) { Icon(Icons.Default.MoreVert, null, tint = fg) }
                }
                Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(26.dp)), contentAlignment = Alignment.Center) {
                    if (art != null) androidx.compose.foundation.Image(art.asImageBitmap(), null, modifier = Modifier.fillMaxSize().blur(38.dp))
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(dynamicAccent.copy(alpha = .44f), bg.copy(alpha = .92f)))))
                    if (art != null) androidx.compose.foundation.Image(art.asImageBitmap(), null, modifier = Modifier.fillMaxWidth(.72f).aspectRatio(1f).graphicsLayer { scaleX = artScale; scaleY = artScale }.clip(RoundedCornerShape(24.dp)))
                    else ArtworkPlaceholder(accent, 190)
                    Text("REET", color = Color.White.copy(alpha = .72f), fontSize = 9.sp, letterSpacing = 5.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
                }
                Spacer(Modifier.height(17.dp))
                Text(song.title, color = fg, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist + " · " + song.album, color = muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
                Spacer(Modifier.height(12.dp))
                Slider(value = if (duration > 0) position.coerceIn(0, duration).toFloat() else 0f, onValueChange = { position = it.toLong() }, onValueChangeFinished = { player.seekTo(position) },
                    valueRange = 0f..duration.coerceAtLeast(1L).toFloat(), colors = SliderDefaults.colors(thumbColor = dynamicAccent, activeTrackColor = dynamicAccent, inactiveTrackColor = Line))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatTime(position), color = muted, fontSize = 9.sp); Text(formatTime(duration), color = muted, fontSize = 9.sp) }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { player.shuffleModeEnabled = !player.shuffleModeEnabled }) { Icon(Icons.Default.Shuffle, null, tint = if (player.shuffleModeEnabled) accent else muted) }
                    IconButton(onClick = { player.seekToPreviousMediaItem() }) { Icon(Icons.Default.SkipPrevious, null, tint = fg, modifier = Modifier.size(31.dp)) }
                    Box(Modifier.size(62.dp).clip(CircleShape).background(dynamicAccent).clickable { if (playing) player.pause() else player.play() }, contentAlignment = Alignment.Center) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(33.dp))
                    }
                    IconButton(onClick = { player.seekToNextMediaItem() }) { Icon(Icons.Default.SkipNext, null, tint = fg, modifier = Modifier.size(31.dp)) }
                    IconButton(onClick = { val key = song.id.toString(); favorites = if (favorites.contains(key)) favorites - key else favorites + key; prefs.edit().putStringSet("favorites", favorites).apply() }) {
                        Icon(if (favorites.contains(song.id.toString())) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = accent)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    PlayerTool(Icons.Default.QueueMusic, "Queue") { utility = "queue" }
                    PlayerTool(Icons.Default.Description, "Lyrics") { utility = "lyrics" }
                    PlayerTool(Icons.Default.GraphicEq, "Equalizer") { utility = "equalizer" }
                    PlayerTool(Icons.Default.PlaylistAdd, "Add to") { utility = "add" }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (utility.isNotEmpty()) {
        val isLyrics = utility == "lyrics"
        val isQueue = utility == "queue"
        val isAdd = utility == "add"
        AlertDialog(onDismissRequest = { utility = "" }, containerColor = panel,
            title = { Text(when(utility) { "lyrics" -> "Lyrics"; "queue" -> "Play queue"; "add" -> "Add to playlist"; else -> "Equalizer visualizer" }, color = fg) },
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
                        if (lyrics.isBlank()) Text("No saved lyrics yet. Add lyrics for offline access.", color = muted, fontSize = 12.sp)
                        else Text(lyrics, color = fg, fontSize = 14.sp, lineHeight = 24.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Edit lyrics", color = accent, modifier = Modifier.clickable { lyricsEdit = true })
                    }
                    isQueue -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        item { Text("NOW PLAYING", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                        item { current?.let { s -> Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            SongArtwork(s.albumId, accent, 42); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Text(s.title, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(s.artist, color = muted, fontSize = 9.sp) }
                            Icon(Icons.Default.GraphicEq, null, tint = accent)
                        } } }
                        item { Text("UP NEXT", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 5.dp)) }
                        items(songs.filterNot { it.id == current?.id }) { s -> Row(Modifier.fillMaxWidth().clickable {
                            val index = songs.indexOf(s); current = s
                            player.setMediaItems(songs.map { MediaItem.Builder().setUri(it.uri).setMediaId(it.id.toString()).build() }, index, 0L)
                            player.prepare(); player.play(); utility = ""
                        }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DragHandle, null, tint = muted, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp)); SongArtwork(s.albumId, accent, 36); Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) { Text(s.title, color = fg, fontSize = 11.sp, maxLines = 1); Text(s.artist, color = muted, fontSize = 9.sp, maxLines = 1) }
                            Text(formatTime(s.duration), color = muted, fontSize = 9.sp)
                        } }
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
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Custom     Bass     Pop     Rock     Vocal", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(14.dp))
                        EqualizerBars(accent, Modifier.fillMaxWidth().height(90.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("60 Hz       230 Hz      910 Hz      3.6 kHz      14 kHz", color = muted, fontSize = 8.sp)
                        Text("Animated equalizer visualizer", color = muted, fontSize = 9.sp)
                        Text("Audio effects are a visual preview; DSP is not connected.", color = muted, fontSize = 9.sp, textAlign = TextAlign.Center)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { utility = "" }) { Text("Close", color = accent) } })
    }
    if (lyricsEdit && current != null) {
        var draft by remember(current?.id) { mutableStateOf(lyrics) }
        AlertDialog(onDismissRequest = { lyricsEdit = false }, containerColor = panel,
            title = { Text("Save offline lyrics", color = fg) },
            text = { Column {
                OutlinedTextField(value = draft, onValueChange = { draft = it }, minLines = 5, maxLines = 9, label = { Text("Paste lyrics") })
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
        AlertDialog(onDismissRequest = { showSettings = false }, containerColor = panel,
            title = { Text("Personalize REET", color = fg) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Appearance", color = muted, fontSize = 11.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (lightMode) "Light theme" else "Dark theme", color = fg, modifier = Modifier.weight(1f))
                    Switch(checked = lightMode, onCheckedChange = { lightMode = it; prefs.edit().putBoolean("light_mode", it).apply() })
                }
                Text("Accent colour", color = muted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("Pink" to Pink, "Purple" to Color(0xFFB99AFF), "Blue" to Color(0xFF76BFFF), "Red" to Color(0xFFFF6474), "Mint" to Color(0xFF65DDB5), "Gold" to Color(0xFFE8C16C)).forEach { (name, color) ->
                        Box(Modifier.size(29.dp).clip(CircleShape).background(color).border(if (accentName == name) 3.dp else 0.dp, fg, CircleShape).clickable {
                            accentName = name; prefs.edit().putString("accent", name).apply()
                        })
                    }
                }
                Divider(color = glassStroke)
                Text("Audio & Playback", color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showSettings = false; utility = "equalizer" }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GraphicEq, null, tint = accent); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text("Equalizer & Effects", color = fg, fontSize = 11.sp); Text("Visualizer and sound presets", color = muted, fontSize = 9.sp) }
                    Icon(Icons.Default.ChevronRight, null, tint = muted)
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showSettings = false; utility = "queue" }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QueueMusic, null, tint = accent); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text("Playback Queue", color = fg, fontSize = 11.sp); Text("View your songs", color = muted, fontSize = 9.sp) }
                    Icon(Icons.Default.ChevronRight, null, tint = muted)
                }
                Divider(color = glassStroke)
                Text("Library", color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth().clickable { scan(); showSettings = false }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Refresh, null, tint = accent); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text("Scan Music Library", color = fg, fontSize = 11.sp); Text("Refresh offline songs", color = muted, fontSize = 9.sp) }
                    Icon(Icons.Default.ChevronRight, null, tint = muted)
                }
                Text("Sort order: " + sort + if (ascending) " ↑" else " ↓", color = fg, fontSize = 11.sp)
                Text("Tap the sort label on the library screen to change sorting.", color = muted, fontSize = 10.sp)
                Text("Offline mode · No streaming or account required.", color = muted, fontSize = 10.sp)
            } },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done", color = accent) } })
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
private fun SongArtwork(albumId: Long, accent: Color, size: Int) {
    val bitmap = rememberArtwork(albumId)
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

@Composable
private fun CircleAction(icon: androidx.compose.ui.graphics.vector.ImageVector, fg: Color, panel: Color, size: Int = 40, onClick: () -> Unit) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(panel).border(1.dp, Line, CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun PlayerTool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp)) {
        Icon(icon, null, tint = Pink, modifier = Modifier.size(21.dp))
        Text(label, color = SoftText, fontSize = 9.sp)
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

private fun formatTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1000L
    return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
}
