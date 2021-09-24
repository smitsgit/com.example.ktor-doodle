package com.example.data

import com.example.data.Room.Phase.*
import com.example.data.models.*
import com.example.gson
import com.example.other.getRandomWords
import com.example.other.matchesWord
import com.example.other.transforToUnderscores
import com.example.other.words
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*

class Room(
    val name: String,
    val maxPlayers: Int,
    var players: List<Player> = listOf()
) {
    private var timerJob: Job? = null
    private var drawingPlayer: Player? = null
    private var winningPlayers = listOf<String>()
    private var word: String? = null
    private var curWords: List<String>? = null
    private var drawingPlayerIndex = 0
    private var startTime = 0L

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
            startTime = System.currentTimeMillis()
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

    private fun isGuessCorrect(guess: ChatMessage): Boolean {
        return guess.matchesWord(word ?: return false) &&
                !winningPlayers.contains(guess.from) &&
                guess.from != drawingPlayer?.username &&
                currentPhase == GAME_RUNNING
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

    fun setWordAndSwitchToGameRunning(word: String) {
        this.word = word
        currentPhase = Phase.GAME_RUNNING
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
        curWords = getRandomWords(3)
        val newWords = NewWords(curWords!!)
        nextDrawingPlayer()

        GlobalScope.launch {
            drawingPlayer?.socket?.send(Frame.Text(gson.toJson(newWords)))
            waitAndNotify(DELAY_NEW_ROUND_TO_GAME_RUNNING)
        }
    }

    private fun gameRunning() {
        winningPlayers = listOf()
        val wordToSend = word ?: curWords?.random() ?: words.random()
        val wordWithUnderScores = wordToSend.transforToUnderscores()
        val drawingUserName = (drawingPlayer ?: players.random()).username

        val gameStateForDrawingPlayer = GameState(
            drawingUserName, wordToSend
        )

        val gameStateForGuessingPlayers = GameState(
            drawingUserName, wordWithUnderScores
        )

        GlobalScope.launch {
            broadcastExceptTo(
                drawingPlayer?.clientId ?: players.random().clientId,
                gson.toJson(gameStateForGuessingPlayers)
            )

            drawingPlayer?.socket?.send(Frame.Text(gson.toJson(gameStateForDrawingPlayer)))
            println("Drawing phase in room $name started. It will last for ${DELAY_GAME_RUNNING_TO_SHOW_WORD / 1000}s")
        }
    }

    private fun showWord() {
         GlobalScope.launch {
             if (winningPlayers.isEmpty()) {
                 drawingPlayer?.let {
                     it.score -= PENALTY_NOBODY_GUESSED_IT
                 }
             }

             word?.let {
                 val chosenWord = ChosenWord(it, name)
                 broadcast(gson.toJson(chosenWord))
             }

             waitAndNotify(DELAY_SHOW_WORD_TO_NEW_ROUND)
             val phaseChange = PhaseChange(Phase.SHOW_WORD, DELAY_SHOW_WORD_TO_NEW_ROUND)
             broadcast(gson.toJson(phaseChange))
         }
    }

    private fun addWinningPlayer(username: String): Boolean {
        winningPlayers = winningPlayers + username

        if (winningPlayers.size == players.size - 1) {
            currentPhase = NEW_ROUND
            return true
        }else {
            return false
        }
    }

    suspend fun checkWordAndNotifyPlayers(message: ChatMessage): Boolean {
        if (isGuessCorrect(message)) {
            val guessingTime = System.currentTimeMillis() - startTime
            val timePercentageLeft = 1f - guessingTime.toFloat() / DELAY_GAME_RUNNING_TO_SHOW_WORD

            val score = GUESS_SCORE_DEFAULT + GUESS_SCORE_PERCENTAGE_MULTIPLIER * timePercentageLeft
            val player = players.find {
                it.username == message.from
            }

            player?.let {
                it.score += score.toInt()
            }

            drawingPlayer?.let {
                it.score += GUESS_SCORE_FOR_DRAWING_PLAYER / players.size
            }

            val announcement = Announcement(
                "${message.from} has guessed the word",
                System.currentTimeMillis(),
                Announcement.TYPE_PLAYER_GUESSED_WORD
            )
            broadcast(gson.toJson(announcement))

            val isRoundOver = addWinningPlayer(message.from)
            if (isRoundOver) {
                val roundOver = Announcement(
                    "Everyone guessed it. New round starting",
                    System.currentTimeMillis(),
                    Announcement.TYPE_EVERYONE_GUESSED_WORD
                )
                broadcast(gson.toJson(roundOver))
            }
            return true
        }
        return false
    }

    private fun nextDrawingPlayer() {
         drawingPlayer?.isDrawing = false
         if (players.isEmpty()) return

         drawingPlayer = if (drawingPlayerIndex <= players.size - 1) {
             players[drawingPlayerIndex]
         }else {
             players.last()
         }

        if (drawingPlayerIndex < players.size - 1) {
            drawingPlayerIndex++
        }else {
            drawingPlayerIndex = 0
        }



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

        const val PENALTY_NOBODY_GUESSED_IT = 50
        const val GUESS_SCORE_DEFAULT = 50
        const val GUESS_SCORE_PERCENTAGE_MULTIPLIER = 50
        const val GUESS_SCORE_FOR_DRAWING_PLAYER = 50
    }
}