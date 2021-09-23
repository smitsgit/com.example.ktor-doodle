package com.example.data.models

import com.example.other.Constants.TYPE_ANNOUNCEMENT_DATA

data class Announcement(
    val message: String,
    val timestamp: Long,
    val type: Int
):BaseModel(TYPE_ANNOUNCEMENT_DATA) {
    companion object {
        const val TYPE_PLAYER_GUESSED_WORD = 1
        const val TYPE_PLAYER_ENTERED_ROOM = 2
        const val TYPE_PLAYER_LEFT_ROOM = 3
        const val TYPE_EVERYONE_GUESSED_WORD = 4
    }
}
