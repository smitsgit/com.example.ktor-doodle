package com.example.data

import com.example.data.models.Announcement
import com.example.gson
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.isActive

class Room(
    val name: String,
    val maxPlayers: Int,
    var players: List<Player> = listOf()
) {

    private var phaseChangedListener: ((Phase) -> Unit)? = null
    var currentPhase = Phase.WAITING_FOR_PLAYERS
        set(value) {
            synchronized(field) {
                field = value
                phaseChangedListener?.let { it(value) }
            }
        }

    private fun setPhaseChangeListener(listener: (Phase) -> Unit) {
        phaseChangedListener = listener
    }

    init {
        setPhaseChangeListener { phase ->
            when (phase) {
                Phase.WAITING_FOR_PLAYERS -> waitingForPlayers()
                Phase.WAITING_FOR_START -> waitingForStart()
                Phase.NEW_ROUND -> newRound()
                Phase.GAME_RUNNING -> gameRunning()
                Phase.SHOW_WORD -> showWord()
            }
        }
    }

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

    suspend fun addPlayer(clientId: String, username: String, socketSession: WebSocketSession): Player {
        val player = Player(username, socketSession, clientId)
        players = players + player

        if (players.size == 1) {
            currentPhase = Phase.WAITING_FOR_PLAYERS
        } else if (players.size == 2 && currentPhase == Phase.WAITING_FOR_PLAYERS) {
            currentPhase = Phase.WAITING_FOR_START
            players = players.shuffled()
        } else if (currentPhase == Phase.WAITING_FOR_START && maxPlayers == players.size) {
            currentPhase = Phase.NEW_ROUND
            players = players.shuffled()
        }

        val announcement = Announcement(
            message = "$username has joined the party",
            System.currentTimeMillis(),
            Announcement.TYPE_PLAYER_ENTERED_ROOM
        )
        broadcast(gson.toJson(announcement))

        return player
    }

    fun containsPlayer(username: String): Boolean {
        return players.find {
            it.username == username
        } != null
    }


    private fun waitingForPlayers() {

    }

    private fun waitingForStart() {

    }

    private fun newRound() {

    }

    private fun gameRunning() {

    }

    private fun showWord() {

    }

    enum class Phase {
        WAITING_FOR_PLAYERS,
        WAITING_FOR_START,
        NEW_ROUND,
        GAME_RUNNING,
        SHOW_WORD
    }
}