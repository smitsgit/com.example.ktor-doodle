package com.example.plugins

import com.example.data.Room
import com.example.data.models.BasicApiResponse
import com.example.data.models.CreateRoomRequest
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
