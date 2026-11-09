package com.reet.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.RemoteViews
import java.io.FileNotFoundException

class ReetMusicWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateVariant(context, manager, ids, "Medium")
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        updateVariant(context, manager, intArrayOf(appWidgetId), "Medium")
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH || intent.action == AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED) updateAll(context)
    }

    companion object {
        const val ACTION_REFRESH = "com.reet.music.WIDGET_REFRESH"
        private const val PREFS = "reet_library"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            listOf(
                ReetCompactWidgetProvider::class.java to "Compact",
                ReetMusicWidgetProvider::class.java to "Medium",
                ReetLargeWidgetProvider::class.java to "Large"
            ).forEach { (provider, mode) ->
                val ids = manager.getAppWidgetIds(ComponentName(context, provider))
                updateVariant(context, manager, ids, mode)
            }
        }

        fun updateVariant(context: Context, manager: AppWidgetManager, ids: IntArray, mode: String) {
            ids.forEach { updateOne(context, manager, it, mode) }
        }

        private fun action(context: Context, action: String, request: Int): PendingIntent {
            val intent = Intent(context, WidgetActionReceiver::class.java).setAction(action)
            return PendingIntent.getBroadcast(context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, id: Int, providerMode: String) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val layoutRes = when (providerMode) {
                "Compact" -> R.layout.widget_reet_music_compact
                "Large" -> R.layout.widget_reet_music_large
                else -> R.layout.widget_reet_music
            }
            val views = RemoteViews(context.packageName, layoutRes)
            val title = prefs.getString("widget_song_title", "Choose a song") ?: "Choose a song"
            val artist = prefs.getString("widget_song_artist", "Reet Music Player") ?: "Reet Music Player"
            val playing = prefs.getBoolean("widget_is_playing", false)
            val isFavourite = prefs.getBoolean("widget_is_favourite", false)
            views.setTextViewText(R.id.widget_title, if (title == "Choose a song") title else "$title  -  $artist")
            views.setViewVisibility(R.id.widget_artist, android.view.View.GONE)
            views.setImageViewResource(R.id.widget_play_pause, if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
            views.setImageViewResource(R.id.widget_favourite, if (isFavourite) R.drawable.ic_widget_favorite_filled else R.drawable.ic_widget_favorite)
            views.setOnClickPendingIntent(R.id.widget_previous, action(context, WidgetActionReceiver.PREVIOUS, id * 10 + 1))
            views.setOnClickPendingIntent(R.id.widget_play_pause, action(context, WidgetActionReceiver.PLAY_PAUSE, id * 10 + 2))
            views.setOnClickPendingIntent(R.id.widget_next, action(context, WidgetActionReceiver.NEXT, id * 10 + 3))
            views.setOnClickPendingIntent(R.id.widget_favourite, action(context, WidgetActionReceiver.FAVOURITE, id * 10 + 4))
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(
                context, id * 10 + 5,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))

            val artwork = loadArtwork(context, prefs.getString("widget_art_uri", null))
            if (artwork != null) views.setImageViewBitmap(R.id.widget_art, artwork)
            else views.setImageViewResource(R.id.widget_art, R.drawable.ic_reet_launcher)

            try {
                manager.updateAppWidget(id, views)
            } catch (_: Exception) {
                val fallback = RemoteViews(context.packageName, layoutRes)
                fallback.setTextViewText(R.id.widget_title, if (title == "Choose a song") title else "$title  -  $artist")
                fallback.setViewVisibility(R.id.widget_artist, android.view.View.GONE)
                fallback.setImageViewResource(R.id.widget_art, R.drawable.ic_reet_launcher)
                fallback.setImageViewResource(R.id.widget_play_pause, if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
                fallback.setImageViewResource(R.id.widget_favourite, if (isFavourite) R.drawable.ic_widget_favorite_filled else R.drawable.ic_widget_favorite)
                fallback.setOnClickPendingIntent(R.id.widget_previous, action(context, WidgetActionReceiver.PREVIOUS, id * 10 + 1))
                fallback.setOnClickPendingIntent(R.id.widget_play_pause, action(context, WidgetActionReceiver.PLAY_PAUSE, id * 10 + 2))
                fallback.setOnClickPendingIntent(R.id.widget_next, action(context, WidgetActionReceiver.NEXT, id * 10 + 3))
                fallback.setOnClickPendingIntent(R.id.widget_favourite, action(context, WidgetActionReceiver.FAVOURITE, id * 10 + 4))
                fallback.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(
                    context, id * 10 + 5,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ))
                manager.updateAppWidget(id, fallback)
            }
        }

        private fun decodeScaled(open: () -> java.io.InputStream, maxSide: Int): Bitmap? = try {
            val bytes = open().use { it.readBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                inSampleSize = sample.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.RGB_565
            })
        } catch (_: Exception) { null }

        private fun loadArtwork(context: Context, uriString: String?): Bitmap? {
            if (uriString.isNullOrBlank()) return null
            return try {
                decodeScaled({ context.contentResolver.openInputStream(Uri.parse(uriString)) ?: throw FileNotFoundException() }, 112)
            } catch (_: Exception) { null }
        }
    }
}

class WidgetActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val service = Intent(context, MusicPlaybackService::class.java).setAction(intent.action)
        if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(service) else context.startService(service)
    }

    companion object {
        const val PREVIOUS = "com.reet.music.WIDGET_PREVIOUS"
        const val PLAY_PAUSE = "com.reet.music.WIDGET_PLAY_PAUSE"
        const val NEXT = "com.reet.music.WIDGET_NEXT"
        const val FAVOURITE = "com.reet.music.WIDGET_FAVOURITE"
    }
}

class ReetCompactWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ReetMusicWidgetProvider.updateVariant(context, manager, ids, "Compact")
    }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        ReetMusicWidgetProvider.updateVariant(context, manager, intArrayOf(appWidgetId), "Compact")
    }
}

class ReetLargeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ReetMusicWidgetProvider.updateVariant(context, manager, ids, "Large")
    }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        ReetMusicWidgetProvider.updateVariant(context, manager, intArrayOf(appWidgetId), "Large")
    }
}
