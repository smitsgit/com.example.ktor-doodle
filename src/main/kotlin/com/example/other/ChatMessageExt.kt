package com.example.other

import com.example.data.models.ChatMessage

fun ChatMessage.matchesWord(word: String): Boolean {
    return message.toLowerCase().trim() == word.toLowerCase().trim()
}