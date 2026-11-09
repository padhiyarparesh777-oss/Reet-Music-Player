package com.reet.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.widget.RemoteViews
import androidx.media3.common.MediaMetadata
import java.io.FileNotFoundException

class ReetMusicWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateVariant(context, manager, ids, "Medium")
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) { updateOne(context, manager, appWidgetId) }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH || intent.action == AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED) {
            updateAll(context)
        }
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
            val views = RemoteViews(context.packageName, R.layout.widget_reet_music)
            val title = prefs.getString("widget_song_title", "Reet Music Player") ?: "Reet Music Player"
            val artist = prefs.getString("widget_song_artist", "Your offline music") ?: "Your offline music"
            val playing = prefs.getBoolean("widget_is_playing", false)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)
            views.setTextViewText(R.id.widget_play_pause, if (playing) "Ⅱ" else "▶")
            views.setTextViewText(R.id.widget_favourite, if (prefs.getBoolean("widget_is_favourite", false)) "♥" else "♡")
            views.setOnClickPendingIntent(R.id.widget_previous, action(context, WidgetActionReceiver.PREVIOUS, id * 10 + 1))
            views.setOnClickPendingIntent(R.id.widget_play_pause, action(context, WidgetActionReceiver.PLAY_PAUSE, id * 10 + 2))
            views.setOnClickPendingIntent(R.id.widget_next, action(context, WidgetActionReceiver.NEXT, id * 10 + 3))
            views.setOnClickPendingIntent(R.id.widget_favourite, action(context, WidgetActionReceiver.FAVOURITE, id * 10 + 4))
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, id * 10 + 5,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

            val artwork = loadArtwork(context, prefs.getString("widget_art_uri", null))
            if (artwork != null) views.setImageViewBitmap(R.id.widget_art, artwork)
            else views.setImageViewResource(R.id.widget_art, R.drawable.ic_reet_launcher)

            val wallpaper = loadWallpaper(context, prefs)
            if (wallpaper != null) views.setImageViewBitmap(R.id.widget_background, wallpaper)
            views.setInt(R.id.widget_overlay, "setBackgroundColor", Color.argb(178, 10, 10, 16))

            val options = manager.getAppWidgetOptions(id)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 90)
            val layoutMode = providerMode
            val compact = layoutMode == "Compact" || minWidth < 190 || minHeight < 82
            val large = layoutMode == "Large" && minWidth >= 220 && minHeight >= 95
            views.setViewVisibility(R.id.widget_artist, if (compact) android.view.View.GONE else android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_favourite, if (compact) android.view.View.GONE else android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_art, if (compact) android.view.View.GONE else android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_title, if (large) "♫  $title" else title)
            manager.updateAppWidget(id, views)
        }

        private fun loadArtwork(context: Context, uriString: String?): Bitmap? {
            if (uriString.isNullOrBlank()) return null
            return try { context.contentResolver.openInputStream(Uri.parse(uriString))?.use { BitmapFactory.decodeStream(it) } }
            catch (_: Exception) { null }
        }

        private fun loadWallpaper(context: Context, prefs: android.content.SharedPreferences): Bitmap? {
            return try {
                val names = context.assets.list("backgrounds")?.filter { it.endsWith(".jpg", true) || it.endsWith(".png", true) || it.endsWith(".webp", true) }.orEmpty()
                if (names.isEmpty()) return null
                val selected = if (prefs.getBoolean("widget_wallpaper_auto", true)) {
                    val old = prefs.getInt("widget_wallpaper_index", 0)
                    val next = (old + 1) % names.size
                    prefs.edit().putInt("widget_wallpaper_index", next).apply()
                    names[next]
                } else prefs.getString("widget_wallpaper", names.first())?.takeIf { it in names } ?: names.first()
                context.assets.open("backgrounds/$selected").use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) { null }
        }
    }
}

class WidgetActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val service = Intent(context, MusicPlaybackService::class.java).setAction(intent.action)
        context.startService(service)
    }
    companion object {
        const val PREVIOUS = "com.reet.music.WIDGET_PREVIOUS"
        const val PLAY_PAUSE = "com.reet.music.WIDGET_PLAY_PAUSE"
        const val NEXT = "com.reet.music.WIDGET_NEXT"
        const val FAVOURITE = "com.reet.music.WIDGET_FAVOURITE"
    }
}
