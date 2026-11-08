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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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

private val Ink = Color(0xFF292632)
private val Rose = Color(0xFFCB8CA8)
private val Muted = Color(0xFF89838D)
private val Glass = Color(0xCCFFFFFF)

private data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: Uri
)

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        setContent {
            ReetApp(
                player = player,
                loadSongs = ::loadDeviceSongs
            )
        }
    }

    private fun loadDeviceSongs(): List<Song> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val result = mutableListOf<Song>()

        val cursor: Cursor? = contentResolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                result += Song(
                    id = id,
                    title = it.getString(titleColumn) ?: "Unknown title",
                    artist = it.getString(artistColumn)
                        ?.takeIf { artist -> artist != "<unknown>" }
                        ?: "Unknown artist",
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

@Composable
private fun ReetApp(
    player: ExoPlayer,
    loadSongs: () -> List<Song>
) {
    var songs by remember { mutableStateOf(emptyList<Song>()) }
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }

    val audioPermission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    fun refreshLibrary() {
        songs = loadSongs()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) refreshLibrary()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = player.currentMediaItemIndex
                if (index in songs.indices) selectedSong = songs[index]
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            player.applicationContext,
            audioPermission
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) refreshLibrary()
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Rose,
            onPrimary = Color.White,
            background = Color(0xFFF7F5F8),
            surface = Color.White,
            onSurface = Ink
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFFFF8FC), Color(0xFFF0EDF4))
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(28.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "REET",
                            color = Rose,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp
                        )
                        Text(
                            text = "Your music, your moments.",
                            color = Ink,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Glass),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = "Reet", tint = Rose)
                    }
                }

                Spacer(Modifier.height(26.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Your Library", fontSize = 19.sp, color = Ink, fontWeight = FontWeight.Bold)
                    Text("${songs.size} songs", color = Muted, fontSize = 12.sp)
                }

                Spacer(Modifier.height(12.dp))

                if (!hasPermission) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Glass)
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Rose,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Find music on your phone", color = Ink, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Allow audio access to load your offline songs.",
                            color = Muted,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { permissionLauncher.launch(audioPermission) }) {
                            Text("Allow access")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                } else if (songs.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No music found on this device.", color = Muted)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        items(songs, key = { it.id }) { song ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Glass)
                                    .clickable {
                                        val index = songs.indexOf(song)
                                        selectedSong = song
                                        player.setMediaItems(
                                            songs.map { MediaItem.fromUri(it.uri) },
                                            index,
                                            0L
                                        )
                                        player.prepare()
                                        player.play()
                                    }
                                    .padding(13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(Color(0xFFF3E7EE)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Rose)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        song.title,
                                        color = Ink,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        song.artist,
                                        color = Muted,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (selectedSong?.id == song.id && isPlaying) {
                                    Icon(Icons.Default.Pause, contentDescription = "Playing", tint = Rose)
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play song", tint = Rose)
                                }
                            }
                        }
                    }
                }

                selectedSong?.let { current ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.96f))
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(start = 5.dp)) {
                            Text(
                                current.title,
                                color = Ink,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(current.artist, color = Muted, fontSize = 12.sp)
                        }
                        IconButton(onClick = { player.seekToPreviousMediaItem() }) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Rose)
                        }
                        IconButton(onClick = {
                            if (player.isPlaying) player.pause() else player.play()
                        }) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Rose
                            )
                        }
                        IconButton(onClick = { player.seekToNextMediaItem() }) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Rose)
                        }
                    }
                }
            }
        }
    }
}
