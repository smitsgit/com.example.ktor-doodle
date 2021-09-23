package com.example.data.models

import com.example.other.Constants.TYPE_GAME_ERROR

data class GameError(
    val errorType: Int
): BaseModel(TYPE_GAME_ERROR) {
    companion object {
        const val ERROR_ROOM_NOT_FOUND = 1
    }
}
