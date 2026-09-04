package com.charizard.charify

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LibraryStore(context: Context) {
    private val prefs = context.getSharedPreferences("charify_store", Context.MODE_PRIVATE)

    fun apiKey(): String = prefs.getString("youtube_api_key", "") ?: ""
    fun saveApiKey(value: String) = prefs.edit().putString("youtube_api_key", value.trim()).apply()

    fun themeIndex(): Int = prefs.getInt("theme_index", 0)
    fun saveThemeIndex(value: Int) = prefs.edit().putInt("theme_index", value).apply()

    fun wallpaperMode(): String = prefs.getString("wallpaper_mode", "theme") ?: "theme"
    fun saveWallpaperMode(value: String) = prefs.edit().putString("wallpaper_mode", value).apply()

    fun customWallpaperUri(): String = prefs.getString("custom_wallpaper_uri", "") ?: ""
    fun saveCustomWallpaperUri(value: String) = prefs.edit().putString("custom_wallpaper_uri", value).apply()
    fun clearCustomWallpaper() = prefs.edit().remove("custom_wallpaper_uri").putString("wallpaper_mode", "theme").apply()

    fun wallpaperDarkness(): Int = prefs.getInt("wallpaper_darkness", 48)
    fun saveWallpaperDarkness(value: Int) = prefs.edit().putInt("wallpaper_darkness", value.coerceIn(0, 85)).apply()

    fun wallpaperBlur(): Int = prefs.getInt("wallpaper_blur", 0)
    fun saveWallpaperBlur(value: Int) = prefs.edit().putInt("wallpaper_blur", value.coerceIn(0, 24)).apply()

    fun wallpaperScale(): String = prefs.getString("wallpaper_scale", "crop") ?: "crop"
    fun saveWallpaperScale(value: String) = prefs.edit().putString("wallpaper_scale", value).apply()

    fun shouldShowIntro(): Boolean = !prefs.getBoolean("intro_seen_v7", false)
    fun markIntroSeen() = prefs.edit().putBoolean("intro_seen_v7", true).apply()
    fun resetIntro() = prefs.edit().putBoolean("intro_seen_v7", false).apply()

    fun favorites(): MutableList<Song> = readSongs("favorites")
    fun history(): MutableList<Song> = readSongs("history")
    fun isFavorite(song: Song): Boolean = favorites().any { it.id == song.id }

    fun toggleFavorite(song: Song): Boolean {
        val list = favorites()
        val index = list.indexOfFirst { it.id == song.id }
        val nowFavorite = index < 0
        if (nowFavorite) list.add(0, song) else list.removeAt(index)
        writeSongs("favorites", list)
        return nowFavorite
    }

    fun addHistory(song: Song) {
        val list = history()
        list.removeAll { it.id == song.id }
        list.add(0, song)
        while (list.size > 60) list.removeAt(list.lastIndex)
        writeSongs("history", list)
    }

    fun playlists(): MutableList<LocalPlaylist> {
        val array = JSONArray(prefs.getString("playlists", "[]") ?: "[]")
        return MutableList(array.length()) { i ->
            val obj = array.getJSONObject(i)
            val songsJson = obj.optJSONArray("songs") ?: JSONArray()
            val songs = MutableList(songsJson.length()) { j -> songFromJson(songsJson.getJSONObject(j)) }
            LocalPlaylist(obj.optString("name", "Playlist"), songs)
        }
    }

    fun createPlaylist(name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        val all = playlists()
        if (all.none { it.name.equals(clean, true) }) all += LocalPlaylist(clean)
        savePlaylists(all)
    }

    fun addToPlaylist(name: String, song: Song) {
        val all = playlists()
        val playlist = all.firstOrNull { it.name == name } ?: LocalPlaylist(name).also { all += it }
        if (playlist.songs.none { it.id == song.id }) playlist.songs.add(0, song)
        savePlaylists(all)
    }

    private fun savePlaylists(playlists: List<LocalPlaylist>) {
        val array = JSONArray()
        playlists.forEach { p ->
            val songs = JSONArray()
            p.songs.forEach { songs.put(songToJson(it)) }
            array.put(JSONObject().put("name", p.name).put("songs", songs))
        }
        prefs.edit().putString("playlists", array.toString()).apply()
    }

    private fun readSongs(key: String): MutableList<Song> {
        val array = JSONArray(prefs.getString(key, "[]") ?: "[]")
        return MutableList(array.length()) { i -> songFromJson(array.getJSONObject(i)) }
    }

    private fun writeSongs(key: String, songs: List<Song>) {
        val array = JSONArray()
        songs.forEach { array.put(songToJson(it)) }
        prefs.edit().putString(key, array.toString()).apply()
    }

    private fun songToJson(song: Song) = JSONObject()
        .put("id", song.id).put("title", song.title).put("artist", song.artist).put("thumbnail", song.thumbnail)

    private fun songFromJson(obj: JSONObject) = Song(
        obj.optString("id"), obj.optString("title"), obj.optString("artist"), obj.optString("thumbnail")
    )
}
