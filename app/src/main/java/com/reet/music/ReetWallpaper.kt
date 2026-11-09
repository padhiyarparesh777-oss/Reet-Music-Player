package com.reet.music

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory

@Composable
fun ReetWallpaper(assetName: String, darken: Boolean = true) {
    val context = LocalContext.current.applicationContext
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(assetName) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("backgrounds/$assetName").use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }
        if (loaded != null) bitmap = loaded
    }
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF080910))) {
        Crossfade(bitmap, animationSpec = tween(850), label = "reet-wallpaper") { image ->
            if (image != null) Image(image, contentDescription = "Music wallpaper", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (darken) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(
            androidx.compose.ui.graphics.Color.Black.copy(alpha = .48f),
            androidx.compose.ui.graphics.Color.Black.copy(alpha = .58f)
        ))))
    }
}
