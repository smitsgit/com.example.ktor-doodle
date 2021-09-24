package com.example.data.models

import com.example.other.Constants.TYPE_GAME_STATE

data class GameState(
    val drawingPLayer: String,
    val word: String
): BaseModel(TYPE_GAME_STATE)
