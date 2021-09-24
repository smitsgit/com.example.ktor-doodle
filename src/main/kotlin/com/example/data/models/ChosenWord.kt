package com.example.data.models

import com.example.other.Constants.TYPE_CHOSEN_WORD

data class ChosenWord(
    val word: String,
    val roomName: String
): BaseModel(TYPE_CHOSEN_WORD)
