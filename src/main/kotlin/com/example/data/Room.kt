package com.example.data

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.isActive

class Room(
    val name: String,
    val maxPlayers: Int,
    var players: List<Player> = listOf()
) {
    suspend fun broadcast(message: String) {
        players.forEach {
            if (it.socket.isActive) {
                it.socket.send(Frame.Text(message))
            }
        }
    }

    suspend fun broadcastExceptTo(clientId: String, message: String) {
        players.filter {
            it.clientId != clientId
        }.forEach {
            if (it.socket.isActive) {
                it.socket.send(Frame.Text(message))
            }
        }
    }

    fun containsPlayer(username: String): Boolean {
        return players.find {
            it.username == username
        } != null
    }
}