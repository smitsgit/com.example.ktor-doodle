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