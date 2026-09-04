package com.charizard.charify

import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object YouTubeApi {
    fun search(apiKey: String, query: String): List<Song> {
        require(apiKey.isNotBlank()) { "Configure sua chave da YouTube Data API nas Configurações." }
        val url = URL(
            "https://www.googleapis.com/youtube/v3/search" +
                "?part=snippet&type=video&videoCategoryId=10&maxResults=15" +
                "&q=${Uri.encode(query)}&key=${Uri.encode(apiKey)}"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) {
                val msg = runCatching {
                    JSONObject(body).getJSONObject("error").optString("message")
                }.getOrNull()
                throw IllegalStateException(msg ?: "Erro HTTP $code na API do YouTube")
            }

            val json = JSONObject(body)
            val items = json.getJSONArray("items")
            buildList {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val id = item.getJSONObject("id").optString("videoId")
                    if (id.isBlank()) continue
                    val snippet = item.getJSONObject("snippet")
                    val thumbs = snippet.optJSONObject("thumbnails")
                    val thumb = thumbs?.optJSONObject("high")?.optString("url")
                        ?: thumbs?.optJSONObject("medium")?.optString("url")
                        ?: ""
                    add(
                        Song(
                            id = id,
                            title = decodeEntities(snippet.optString("title")),
                            artist = decodeEntities(snippet.optString("channelTitle")),
                            thumbnail = thumb
                        )
                    )
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun decodeEntities(value: String): String = value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
}
