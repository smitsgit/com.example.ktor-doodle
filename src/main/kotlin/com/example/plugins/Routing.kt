package com.example.plugins

import com.example.data.Room
import com.example.data.models.BasicApiResponse
import com.example.data.models.CreateRoomRequest
import com.example.data.models.RoomResponse
import com.example.other.Constants.MAX_ROOM_SIZE
import com.example.server
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*

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

            call.respond(HttpStatusCode.OK  , roomResponse)
        }
    }
}

fun Route.joinRoomRoute() {
    route("/api/joinRoom") {
        get {
            val username = call.parameters["username"]
            val roomName = call.parameters["roomName"]
            if(username == null || roomName == null) {
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
