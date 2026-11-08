package com.reet.music

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import java.util.Locale

private val Ink = Color(0xFF392D38)
private val Rose = Color(0xFFD66F9C)
private val DeepRose = Color(0xFFB84F80)
private val Muted = Color(0xFF8D7F89)
private val Glass = Color(0xBFFFFFFF)
private val tabs = listOf("Tracks", "Favourite", "Recent", "Folder")

private data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val folder: String,
    val uri: Uri
)

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        setContent { ReetApp(player = player, loadSongs = ::loadDeviceSongs) }
    }

    private fun loadDeviceSongs(): List<Song> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.Audio.Media.RELATIVE_PATH)
        }.toTypedArray()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val result = mutableListOf<Song>()
        contentResolver.query(
            collection, projection, selection, null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH) else -1
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val rawArtist = cursor.getString(artistCol)
                val path = if (pathCol >= 0) cursor.getString(pathCol).orEmpty() else "Music/"
                result += Song(
                    id = id,
                    title = cursor.getString(titleCol) ?: "Unknown title",
                    artist = rawArtist?.takeIf { it != "<unknown>" } ?: "Unknown artist",
                    album = cursor.getString(albumCol) ?: "Unknown album",
                    duration = cursor.getLong(durationCol),
                    folder = path.trim('/').substringAfterLast('/').ifBlank { "Music" },
                    uri = Uri.withAppendedPath(collection, id.toString())
                )
            }
        }
        return result
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReetApp(player: ExoPlayer, loadSongs: () -> List<Song>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("reet_library", android.content.Context.MODE_PRIVATE) }
    var songs by remember { mutableStateOf(emptyList<Song>()) }
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf("Tracks") }
    var search by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()) }
    var recent by remember { mutableStateOf(prefs.getString("recent", "").orEmpty().split(",").filter { it.isNotBlank() }) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showSettings by remember { mutableStateOf(false) }
    var folderFilter by remember { mutableStateOf<String?>(null) }

    val audioPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    fun refreshLibrary() { songs = loadSongs() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) refreshLibrary()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                selectedSong = songs.firstOrNull { it.id.toString() == mediaItem?.mediaId }
                selectedSong?.let { song ->
                    recent = (listOf(song.id.toString()) + recent.filterNot { it == song.id.toString() }).take(60)
                    prefs.edit().putString("recent", recent.joinToString(",")).apply()
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) refreshLibrary()
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0 } ?: 0L
            delay(500)
        }
    }

    val filtered = remember(songs, activeTab, search, favorites, recent, folderFilter) {
        val base = when (activeTab) {
            "Favourite" -> songs.filter { favorites.contains(it.id.toString()) }
            "Recent" -> recent.mapNotNull { id -> songs.firstOrNull { it.id.toString() == id } }
            "Folder" -> songs.filter { folderFilter == null || it.folder == folderFilter }
            else -> songs
        }
        if (search.isBlank()) base else base.filter {
            it.title.contains(search, true) || it.artist.contains(search, true) || it.album.contains(search, true)
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Rose, onPrimary = Color.White, background = Color(0xFFFFF7FB), surface = Color.White, onSurface = Ink)) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFEFA8C5), Color(0xFFF7DDE8), Color(0xFFF8F5F7), Color(0xFFECE8ED))))) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Reet l 🌍🫶🏻", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                        Text("YOUR MUSIC, YOUR MOMENTS", color = DeepRose, fontSize = 9.sp, letterSpacing = 2.2.sp, fontWeight = FontWeight.Bold)
                    }
                    GlassIconButton(Icons.Default.Settings, "Settings") { showSettings = true }
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Glass).padding(5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    tabs.forEach { tab ->
                        val selected = activeTab == tab
                        val bg by animateColorAsState(if (selected) Color.White.copy(alpha = 0.95f) else Color.Transparent, label = "tab")
                        Text(
                            tab,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(17.dp)).background(bg).clickable {
                                activeTab = tab
                                folderFilter = null
                            }.padding(vertical = 11.dp),
                            color = if (selected) DeepRose else Muted,
                            fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.72f)).padding(horizontal = 14.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = Rose)
                    Spacer(Modifier.width(9.dp))
                    TextField(
                        value = search, onValueChange = { search = it },
                        placeholder = { Text("Search songs, artists, albums…", color = Muted, fontSize = 13.sp) },
                        modifier = Modifier.weight(1f), singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, null, tint = Muted) }
                }
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (activeTab == "Folder" && folderFilter != null) folderFilter!! else activeTab, color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text("${filtered.size} songs on this device", color = Muted, fontSize = 11.sp)
                    }
                    if (activeTab == "Folder" && folderFilter != null) TextButton(onClick = { folderFilter = null }) { Text("All folders", color = DeepRose) }
                    if (hasPermission) TextButton(onClick = { refreshLibrary() }) { Icon(Icons.Default.Refresh, null, tint = DeepRose, modifier = Modifier.size(17.dp)); Text(" Scan", color = DeepRose) }
                }

                if (!hasPermission) {
                    Column(Modifier.fillMaxWidth().padding(top = 36.dp).clip(RoundedCornerShape(28.dp)).background(Glass).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LibraryMusic, null, tint = Rose, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Your offline library", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Allow audio access to find music stored on your phone.", color = Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { permissionLauncher.launch(audioPermission) }, shape = RoundedCornerShape(16.dp)) { Text("Allow music access") }
                    }
                    Spacer(Modifier.weight(1f))
                } else if (activeTab == "Folder" && folderFilter == null && search.isBlank()) {
                    val folders = songs.groupBy { it.folder }.toList().sortedBy { it.first.lowercase(Locale.getDefault()) }
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(folders, key = { it.first }) { (folder, tracks) ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Glass).clickable { folderFilter = folder }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                GlassTile(Icons.Default.Folder)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) { Text(folder, color = Ink, fontWeight = FontWeight.SemiBold); Text("${tracks.size} tracks", color = Muted, fontSize = 11.sp) }
                                Icon(Icons.Default.ChevronRight, null, tint = Rose)
                            }
                        }
                    }
                } else if (filtered.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(if (activeTab == "Favourite") Icons.Default.FavoriteBorder else Icons.Default.MusicOff, null, tint = Rose, modifier = Modifier.size(42.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(if (songs.isEmpty()) "No offline music found" else "Nothing here yet", color = Ink, fontWeight = FontWeight.SemiBold)
                            Text(if (songs.isEmpty()) "Add audio files to your phone and scan again." else "Try another tab or search.", color = Muted, fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        items(filtered, key = { it.id }) { song ->
                            val isCurrent = selectedSong?.id == song.id
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                                    .background(if (isCurrent) Color.White.copy(alpha = 0.94f) else Glass)
                                    .clickable {
                                        val index = songs.indexOfFirst { it.id == song.id }
                                        selectedSong = song
                                        player.setMediaItems(songs.map { MediaItem.Builder().setUri(it.uri).setMediaId(it.id.toString()).build() }, index, 0L)
                                        player.prepare()
                                        player.play()
                                    }.padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlassTile(if (isCurrent && isPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote)
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(song.title, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                                    Text("${song.artist}  ·  ${song.album}", color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = {
                                    val id = song.id.toString()
                                    favorites = if (favorites.contains(id)) favorites - id else favorites + id
                                    prefs.edit().putStringSet("favorites", favorites).apply()
                                }) {
                                    Icon(if (favorites.contains(song.id.toString())) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (favorites.contains(song.id.toString())) DeepRose else Muted, modifier = Modifier.size(19.dp))
                                }
                                Text(formatTime(song.duration), color = Muted, fontSize = 10.sp)
                            }
                        }
                    }
                }

                selectedSong?.let { current ->
                    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(Color(0xF8FFFFFF), Color(0xDDFCEAF2)))).padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlassTile(Icons.Default.Album, 46)
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(current.title, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                                Text(current.artist, color = Muted, fontSize = 11.sp, maxLines = 1)
                            }
                            IconButton(onClick = {
                                val id = current.id.toString()
                                favorites = if (favorites.contains(id)) favorites - id else favorites + id
                                prefs.edit().putStringSet("favorites", favorites).apply()
                            }) { Icon(if (favorites.contains(current.id.toString())) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = DeepRose) }
                        }
                        Slider(
                            value = if (duration > 0) position.coerceIn(0, duration).toFloat() else 0f,
                            onValueChange = { position = it.toLong() },
                            onValueChangeFinished = { player.seekTo(position) },
                            valueRange = 0f..(duration.coerceAtLeast(1L).toFloat()),
                            modifier = Modifier.fillMaxWidth().height(28.dp),
                            colors = SliderDefaults.colors(thumbColor = DeepRose, activeTrackColor = Rose, inactiveTrackColor = Rose.copy(alpha = 0.18f))
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatTime(position), color = Muted, fontSize = 9.sp)
                            Text(formatTime(duration), color = Muted, fontSize = 9.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { player.seekToPreviousMediaItem() }) { Icon(Icons.Default.SkipPrevious, null, tint = Ink, modifier = Modifier.size(30.dp)) }
                            Spacer(Modifier.width(22.dp))
                            Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Rose, DeepRose))).clickable { if (player.isPlaying) player.pause() else player.play() }, contentAlignment = Alignment.Center) {
                                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(29.dp))
                            }
                            Spacer(Modifier.width(22.dp))
                            IconButton(onClick = { player.seekToNextMediaItem() }) { Icon(Icons.Default.SkipNext, null, tint = Ink, modifier = Modifier.size(30.dp)) }
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("REET Settings", color = Ink) },
            text = { Text("Offline mode · Music is read directly from your device. Favourites and recent plays are saved on this phone.", color = Muted) },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done", color = DeepRose) } }
        )
    }
}

@Composable
private fun GlassIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.62f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = DeepRose)
    }
}

@Composable
private fun GlassTile(icon: androidx.compose.ui.graphics.vector.ImageVector, size: Int = 44) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.92f), Color(0xFFF4DCE7).copy(alpha = 0.82f)))), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Rose, modifier = Modifier.size((size * 0.48f).dp))
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L)
    return String.format(Locale.getDefault(), "%d:%02d", total / 60, total % 60)
}
