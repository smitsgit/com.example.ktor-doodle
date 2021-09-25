package com.example

import com.example.data.Player
import com.example.data.Room
import java.util.concurrent.ConcurrentHashMap

class DrawingServer {
    val rooms = ConcurrentHashMap<String, Room>()
    val players = ConcurrentHashMap<String, Player>()

    fun playerJoined(player: Player) {
        players[player.clientId] = player
    }

    fun playerLeft(clientId: String, immediatelyDisconnect: Boolean = false) {
        val room = getRoomWithClientId(clientId)
        if (immediatelyDisconnect) {
            println("Closing connection to ${players[clientId]?.username}")
            room?.removePlayer(clientId)
            players.remove(clientId)
        }
    }

    fun getRoomWithClientId(clientId: String): Room? {
        rooms.values.forEach { room ->
            room.players.forEach { player ->
                if (player.clientId == clientId) {
                    return room
                }
            }
        }
        return null
    }
}