package com.example.data

import com.example.data.Room.Phase.*
import com.example.data.models.Announcement
import com.example.data.models.PhaseChange
import com.example.gson
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*

class Room(
    val name: String,
    val maxPlayers: Int,
    var players: List<Player> = listOf()
) {
    private var timerJob: Job? = null
    private var drawingPlayer: Player? = null

    private var phaseChangedListener: ((Phase) -> Unit)? = null
    var currentPhase = WAITING_FOR_PLAYERS
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
                WAITING_FOR_PLAYERS -> waitingForPlayers()
                WAITING_FOR_START -> waitingForStart()
                NEW_ROUND -> newRound()
                GAME_RUNNING -> gameRunning()
                SHOW_WORD -> showWord()
            }
        }
    }

    private fun waitAndNotify(ms: Long) {
        timerJob?.cancel()
        timerJob = GlobalScope.launch {
            val phaseChange = PhaseChange(
                currentPhase,
                ms,
                drawingPlayer?.username
            )
            repeat((ms / UPDATE_TIME_FREQ).toInt()) {
                if (it != 0) {
                    phaseChange.newPhase = null
                }
                broadcast(gson.toJson(phaseChange))
                phaseChange.time -= UPDATE_TIME_FREQ
                delay(UPDATE_TIME_FREQ)
            }

            currentPhase = when(currentPhase) {
                WAITING_FOR_START -> NEW_ROUND
                NEW_ROUND -> GAME_RUNNING
                GAME_RUNNING -> SHOW_WORD
                SHOW_WORD -> NEW_ROUND
                else -> WAITING_FOR_PLAYERS
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
            currentPhase = WAITING_FOR_PLAYERS
        } else if (players.size == 2 && currentPhase == WAITING_FOR_PLAYERS) {
            currentPhase = WAITING_FOR_START
            players = players.shuffled()
        } else if (currentPhase == WAITING_FOR_START && maxPlayers == players.size) {
            currentPhase = NEW_ROUND
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
         GlobalScope.launch {
             val phaseChange = PhaseChange(
                 WAITING_FOR_PLAYERS,
                 DELAY_WAITING_FOR_PLAYERS
             )
             broadcast(gson.toJson(phaseChange))
         }
    }

    private fun waitingForStart() {
        GlobalScope.launch {
            waitAndNotify(DELAY_WAITING_FOR_START_TO_NEW_ROUND)
            val phaseChange = PhaseChange(
                WAITING_FOR_START,
                DELAY_WAITING_FOR_PLAYERS
            )
            broadcast(gson.toJson(phaseChange))
        }
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

    companion object {
        const val UPDATE_TIME_FREQ = 1000L
        const val DELAY_WAITING_FOR_START_TO_NEW_ROUND = 10000L
        const val DELAY_NEW_ROUND_TO_GAME_RUNNING = 20000L
        const val DELAY_GAME_RUNNING_TO_SHOW_WORD = 60000L
        const val DELAY_SHOW_WORD_TO_NEW_ROUND = 10000L
        const val DELAY_WAITING_FOR_PLAYERS = 10000L
    }
}