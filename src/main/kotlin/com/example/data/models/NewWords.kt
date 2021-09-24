package com.example.data.models

import com.example.other.Constants.TYPE_NEW_WORDS

data class NewWords(
    val words: List<String>
): BaseModel(TYPE_NEW_WORDS)
