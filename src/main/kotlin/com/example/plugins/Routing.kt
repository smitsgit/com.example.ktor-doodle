package com.example.plugins

import com.example.data.Player
import com.example.data.Room
import com.example.data.models.*
import com.example.gson
import com.example.other.Constants.MAX_ROOM_SIZE
import com.example.other.Constants.TYPE_ANNOUNCEMENT_DATA
import com.example.other.Constants.TYPE_CHAT_MESSAGE
import com.example.other.Constants.TYPE_CHOSEN_WORD
import com.example.other.Constants.TYPE_DRAW_DATA
import com.example.other.Constants.TYPE_GAME_STATE
import com.example.other.Constants.TYPE_JOIN_ROOM_HANDSHAKE
import com.example.other.Constants.TYPE_PHASE_CHANGE
import com.example.server
import com.google.gson.Gson
import com.google.gson.JsonParser
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.sessions.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach

fun Application.configureRouting() {

    install(Routing) {
        createRoomRoute()
        createSearchRoomRoute()
        joinRoomRoute()
    }
    routing {
        get("/") {
            call.respondText("Hello World!")
        }
    }

}


fun Route.createRoomRoute() {
    route("/api/createRoom") {
        post {
            val roomRequest = call.receiveOrNull<CreateRoomRequest>()
            if (roomRequest == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            if (server.rooms[roomRequest.name] != null) {
                call.respond(
                    HttpStatusCode.OK,
                    BasicApiResponse(false, "Room already exists")
                )
                return@post
            }

            if (roomRequest.maxPlayers < 2) {
                call.respond(
                    HttpStatusCode.OK,
                    BasicApiResponse(false, "Minimum Room size is 2")
                )
                return@post
            }

            if (roomRequest.maxPlayers > MAX_ROOM_SIZE) {
                call.respond(
                    HttpStatusCode.OK,
                    BasicApiResponse(false, "Max Room size is $MAX_ROOM_SIZE")
                )
                return@post
            }

            val room = Room(roomRequest.name, roomRequest.maxPlayers)
            server.rooms[room.name] = room
            println("Room Created: ${room.name}")

            call.respond(HttpStatusCode.OK, BasicApiResponse(true))
        }
    }
}

fun Route.createSearchRoomRoute() {
    route("/api/getRooms") {
        get {
            val query = call.parameters["searchQuery"]

            if (query == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val rooms = server.rooms.filterKeys {
                it.contains(query, ignoreCase = true)
            }

            val roomResponse = rooms.values.map {
                RoomResponse(it.name, it.maxPlayers, it.players.size)
            }.sortedBy {
                it.name
            }

            call.respond(HttpStatusCode.OK, roomResponse)
        }
    }
}

fun Route.joinRoomRoute() {
    route("/api/joinRoom") {
        get {
            val username = call.parameters["username"]
            val roomName = call.parameters["roomName"]
            if (username == null || roomName == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val room = server.rooms[roomName]
            when {
                room == null -> {
                    call.respond(
                        HttpStatusCode.OK,
                        BasicApiResponse(false, "Room not found.")
                    )
                }
                room.containsPlayer(username) -> {
                    call.respond(
                        HttpStatusCode.OK,
                        BasicApiResponse(false, "A player with this username already joined.")
                    )
                }
                room.players.size >= room.maxPlayers -> {
                    call.respond(
                        HttpStatusCode.OK,
                        BasicApiResponse(false, "This room is already full.")
                    )
                }
                else -> {
                    call.respond(HttpStatusCode.OK, BasicApiResponse(true))
                }
            }
        }
    }
}

fun Route.getWebSocketRoute() {
    route("/ws/draw") {
        standardWebSocket { socket, clientId, message, payload ->
            when (payload) {
               is JoinRoomHandshake -> {
                   val room = server.rooms[payload.roomName]
                   if (room == null) {
                       val gameError = GameError(GameError.ERROR_ROOM_NOT_FOUND)
                       socket.send(Frame.Text(gson.toJson(gameError)))
                       return@standardWebSocket
                   }
                   val player = Player(payload.username, socket, payload.clientId)
                   server.playerJoined(player)

                   if (!room.containsPlayer(player.username)) {
                       room.addPlayer(player.clientId, player.username, socket)
                   }
1
               }
               is DrawData -> {
                   val room = server.rooms[payload.roomName] ?: return@standardWebSocket
                   if (room.currentPhase == Room.Phase.GAME_RUNNING) {
                       room.broadcastExceptTo(clientId, message)
                   }
               }

               is ChosenWord -> {
                   val room = server.rooms[payload.roomName] ?: return@standardWebSocket
                   room.setWordAndSwitchToGameRunning(payload.word)
               }
               is ChatMessage -> {
                   val room = server.rooms[payload.roomName] ?: return@standardWebSocket
                   if (!room.checkWordAndNotifyPlayers(payload)) {
                       room.broadcast(message)
                   }
               }
            }
        }

    }
}


fun Route.standardWebSocket(
    handleFrame: suspend (
        socket: DefaultWebSocketServerSession,
        clientId: String,
        message: String,
        payload: BaseModel
    ) -> Unit
) {
    webSocket {
        val session = call.sessions.get<DrawingSession>()

        if (session == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No session"))
            return@webSocket
        }

        try {
            incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val message = frame.readText()
                    val jsonObject = JsonParser.parseString(message).asJsonObject
                    val type = when(jsonObject.get("type").asString) {
                        TYPE_CHAT_MESSAGE -> ChatMessage::class.java
                        TYPE_DRAW_DATA -> DrawData::class.java
                        TYPE_ANNOUNCEMENT_DATA -> Announcement::class.java
                        TYPE_JOIN_ROOM_HANDSHAKE -> JoinRoomHandshake::class.java
                        TYPE_PHASE_CHANGE -> PhaseChange::class.java
                        TYPE_CHOSEN_WORD -> ChosenWord::class.java
                        TYPE_GAME_STATE -> GameState::class.java
                        else -> BaseModel::class.java
                    }

                    val payload = gson.fromJson(message, type)
                    handleFrame(this, session.clientId, message, payload)
                }
            }
        }catch (e: Exception) {
           e.printStackTrace()
        }
        finally {
            // Handle the disconnects
        }

    }

}
