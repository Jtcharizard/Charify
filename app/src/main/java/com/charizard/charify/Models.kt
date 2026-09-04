package com.charizard.charify

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnail: String = ""
)

data class LocalPlaylist(
    val name: String,
    val songs: MutableList<Song> = mutableListOf()
)
