package com.reet.music

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import java.util.Locale

private val Night = Color(0xFF07111F)
private val DeepNight = Color(0xFF03070E)
private val Sky = Color(0xFF63C7FF)
private val Ice = Color(0xFFBCEBFF)
private val TextMain = Color(0xFFF2F8FF)
private val TextMuted = Color(0xFF91A7BB)
private val GlassFill = Color(0x332A526F)
private val GlassLine = Color(0x557ECDF5)
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
        val result = mutableListOf<Song>()
        contentResolver.query(
            collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
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
                val path = if (pathCol >= 0) cursor.getString(pathCol).orEmpty() else "Music/"
                result += Song(
                    id = id,
                    title = cursor.getString(titleCol) ?: "Unknown title",
                    artist = cursor.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Unknown artist",
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
    var showPlayer by remember { mutableStateOf(false) }
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
                    recent = (listOf(song.id.toString()) + recent.filterNot { it == song.id.toString() }).take(80)
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
            delay(400)
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

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Sky, onPrimary = Night, background = Night, surface = Color(0xFF102235),
        onSurface = TextMain, secondary = Ice, onBackground = TextMain
    )) {
        Box(Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF102B45), Night, DeepNight, Color(0xFF071B2C)))
        )) {
            // Soft ambient highlights create an iOS-inspired glass depth without expensive live blur.
            Box(Modifier.align(Alignment.TopEnd).padding(top = 70.dp, end = 8.dp).size(190.dp).clip(CircleShape).background(Color(0x2267C8FF)))
            Box(Modifier.align(Alignment.BottomStart).padding(bottom = 120.dp, start = 8.dp).size(220.dp).clip(CircleShape).background(Color(0x183B8DC7)))

            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 13.dp)) {
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Reet l 🌍🫶🏻", color = TextMain, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                        Text("YOUR MUSIC, YOUR MOMENTS", color = Sky, fontSize = 8.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
                    }
                    SmallGlassButton(Icons.Default.Settings, "Settings", 36) { showSettings = true }
                }
                Spacer(Modifier.height(11.dp))

                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GlassFill)
                        .border(1.dp, GlassLine.copy(alpha = 0.55f), RoundedCornerShape(16.dp)).padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    tabs.forEach { tab ->
                        val selected = activeTab == tab
                        val bg by animateColorAsState(if (selected) Color(0x665AAFE0) else Color.Transparent, label = "tab")
                        Text(
                            tab, modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(bg)
                                .clickable { activeTab = tab; folderFilter = null }.padding(vertical = 8.dp),
                            color = if (selected) Color.White else TextMuted, fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(9.dp))
                Row(
                    Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(14.dp))
                        .background(Color(0x332B5978)).border(1.dp, GlassLine.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = Sky, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    TextField(
                        value = search, onValueChange = { search = it },
                        placeholder = { Text("Search songs, artists, albums", color = TextMuted, fontSize = 11.sp) },
                        modifier = Modifier.weight(1f), singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = TextMain, unfocusedTextColor = TextMain,
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = Sky
                        )
                    )
                    if (search.isNotEmpty()) {
                        Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(16.dp).clickable { search = "" })
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (activeTab == "Folder" && folderFilter != null) folderFilter!! else activeTab, color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("${filtered.size} songs on this device", color = TextMuted, fontSize = 9.sp)
                    }
                    if (activeTab == "Folder" && folderFilter != null) {
                        Text("All folders", color = Sky, fontSize = 10.sp, modifier = Modifier.clickable { folderFilter = null }.padding(6.dp))
                    }
                    if (hasPermission) SmallGlassButton(Icons.Default.Refresh, "Scan library", 30) { refreshLibrary() }
                }

                if (!hasPermission) {
                    Column(Modifier.fillMaxWidth().padding(top = 28.dp).clip(RoundedCornerShape(22.dp))
                        .background(GlassFill).border(1.dp, GlassLine, RoundedCornerShape(22.dp)).padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LibraryMusic, null, tint = Sky, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Your offline library", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Allow audio access to find music stored on your phone.", color = TextMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { permissionLauncher.launch(audioPermission) }, shape = RoundedCornerShape(13.dp)) { Text("Allow access", fontSize = 12.sp) }
                    }
                    Spacer(Modifier.weight(1f))
                } else if (activeTab == "Folder" && folderFilter == null && search.isBlank()) {
                    val folders = songs.groupBy { it.folder }.toList().sortedBy { it.first.lowercase(Locale.getDefault()) }
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 4.dp, bottom = if (selectedSong != null) 8.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(folders, key = { it.first }) { (folder, tracks) ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GlassFill)
                                .border(1.dp, GlassLine.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                .clickable { folderFilter = folder }.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                GlassTile(Icons.Default.Folder, 32)
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(folder, color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    Text("${tracks.size} tracks", color = TextMuted, fontSize = 9.sp)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = Sky, modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                } else if (filtered.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(if (activeTab == "Favourite") Icons.Default.FavoriteBorder else Icons.Default.MusicOff, null, tint = Sky, modifier = Modifier.size(34.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(if (songs.isEmpty()) "No offline music found" else "Nothing here yet", color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(if (songs.isEmpty()) "Add audio files and scan again." else "Try another tab or search.", color = TextMuted, fontSize = 10.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(top = 3.dp, bottom = if (selectedSong != null) 8.dp else 14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filtered, key = { it.id }) { song ->
                            val isCurrent = selectedSong?.id == song.id
                            Row(
                                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(13.dp))
                                    .background(if (isCurrent) Color(0x554B9BC7) else Color(0x222E5874))
                                    .border(0.7.dp, if (isCurrent) Sky.copy(alpha = 0.55f) else GlassLine.copy(alpha = 0.25f), RoundedCornerShape(13.dp))
                                    .clickable {
                                        val index = songs.indexOfFirst { it.id == song.id }
                                        selectedSong = song
                                        player.setMediaItems(songs.map { MediaItem.Builder().setUri(it.uri).setMediaId(it.id.toString()).build() }, index.coerceAtLeast(0), 0L)
                                        player.prepare()
                                        player.play()
                                        showPlayer = true
                                    }.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlassTile(if (isCurrent && isPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote, 34)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                                    Text(song.title, color = TextMain, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                                    Text("${song.artist} · ${song.album}", color = TextMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(
                                    if (favorites.contains(song.id.toString())) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    null, tint = if (favorites.contains(song.id.toString())) Sky else TextMuted,
                                    modifier = Modifier.size(15.dp).clickable {
                                        val id = song.id.toString()
                                        favorites = if (favorites.contains(id)) favorites - id else favorites + id
                                        prefs.edit().putStringSet("favorites", favorites).apply()
                                    }
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(formatTime(song.duration), color = TextMuted, fontSize = 9.sp)
                            }
                        }
                    }
                }

                selectedSong?.let { current ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 5.dp).height(54.dp)
                            .clip(RoundedCornerShape(17.dp)).background(Color(0xCC132A3D))
                            .border(1.dp, GlassLine, RoundedCornerShape(17.dp))
                            .clickable { showPlayer = true }.padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassTile(Icons.Default.Album, 35)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(current.title, color = TextMain, fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 11.sp, overflow = TextOverflow.Ellipsis)
                            Text(current.artist, color = TextMuted, fontSize = 9.sp, maxLines = 1)
                        }
                        IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }, modifier = Modifier.size(34.dp)) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Sky, modifier = Modifier.size(23.dp))
                        }
                        IconButton(onClick = { player.seekToNextMediaItem() }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.SkipNext, null, tint = TextMain, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    if (showPlayer && selectedSong != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPlayer = false },
            sheetState = sheetState,
            containerColor = Color(0xF20A1726),
            contentColor = TextMain,
            tonalElevation = 0.dp,
            scrimColor = Color.Black.copy(alpha = 0.58f),
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            dragHandle = {
                Box(Modifier.padding(vertical = 11.dp).size(width = 38.dp, height = 4.dp).clip(CircleShape).background(Color(0xFF7294AC)))
            }
        ) {
            val current = selectedSong!!
            Column(
                Modifier.fillMaxWidth().fillMaxHeight(0.88f).padding(horizontal = 23.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("NOW PLAYING", color = Sky, fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    SmallGlassButton(Icons.Default.KeyboardArrowDown, "Close player", 34) { showPlayer = false }
                }
                Spacer(Modifier.height(22.dp))
                Box(
                    Modifier.fillMaxWidth().weight(1f).padding(horizontal = 14.dp).clip(RoundedCornerShape(28.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF275A7B), Color(0xFF10283E), Color(0xFF07111F))))
                        .border(1.dp, GlassLine, RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(180.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF8AD9FF), Color(0xFF286D9D), Color(0xFF0A1B2B)))), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(78.dp))
                    }
                    Text("REET", color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp, letterSpacing = 5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp))
                }
                Spacer(Modifier.height(24.dp))
                Text(current.title, color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Text(current.artist, color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp), maxLines = 1)
                Spacer(Modifier.height(18.dp))
                Slider(
                    value = if (duration > 0) position.coerceIn(0, duration).toFloat() else 0f,
                    onValueChange = { position = it.toLong() },
                    onValueChangeFinished = { player.seekTo(position) },
                    valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                    modifier = Modifier.fillMaxWidth().height(30.dp),
                    colors = SliderDefaults.colors(thumbColor = Sky, activeTrackColor = Sky, inactiveTrackColor = Color(0xFF34546B))
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(position), color = TextMuted, fontSize = 10.sp)
                    Text(formatTime(duration), color = TextMuted, fontSize = 10.sp)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    SmallGlassButton(Icons.Default.SkipPrevious, "Previous", 46) { player.seekToPreviousMediaItem() }
                    Box(
                        Modifier.size(68.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Sky, Color(0xFF2589C4))))
                            .clickable { if (player.isPlaying) player.pause() else player.play() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Night, modifier = Modifier.size(35.dp))
                    }
                    SmallGlassButton(Icons.Default.SkipNext, "Next", 46) { player.seekToNextMediaItem() }
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    val isFavorite = favorites.contains(current.id.toString())
                    IconButton(onClick = {
                        val id = current.id.toString()
                        favorites = if (favorites.contains(id)) favorites - id else favorites + id
                        prefs.edit().putStringSet("favorites", favorites).apply()
                    }) {
                        Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (isFavorite) Sky else TextMuted)
                    }
                    Text(current.album, color = TextMuted, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 10.dp))
                    IconButton(onClick = { player.shuffleModeEnabled = !player.shuffleModeEnabled }) {
                        Icon(Icons.Default.Shuffle, null, tint = if (player.shuffleModeEnabled) Sky else TextMuted)
                    }
                }
                Spacer(Modifier.height(22.dp))
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = Color(0xFF102235),
            title = { Text("REET Settings", color = TextMain) },
            text = { Text("Offline mode · Music is read directly from your device. Favourites and recent plays are saved on this phone.", color = TextMuted, fontSize = 12.sp) },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done", color = Sky) } }
        )
    }
}

@Composable
private fun SmallGlassButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, size: Int = 36, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Color(0x443B789D))
            .border(1.dp, GlassLine.copy(alpha = 0.7f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = Sky, modifier = Modifier.size((size * 0.48f).dp))
    }
}

@Composable
private fun GlassTile(icon: androidx.compose.ui.graphics.vector.ImageVector, size: Int = 36) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(11.dp))
            .background(Brush.linearGradient(listOf(Color(0x665FADD5), Color(0x33315B78))))
            .border(0.8.dp, GlassLine.copy(alpha = 0.6f), RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Ice, modifier = Modifier.size((size * 0.48f).dp))
    }
}

private fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000L
    return String.format(Locale.getDefault(), "%d:%02d", total / 60, total % 60)
}
