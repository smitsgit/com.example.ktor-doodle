package com.example

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.example.plugins.*
import com.google.gson.Gson
import io.ktor.application.*
import io.ktor.sessions.*
import io.ktor.util.*

val server = DrawingServer()
val gson = Gson()

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configureRouting()
        configureSerialization()
        configureSockets()
        configureMonitoring()
        configureSession()

        intercept(ApplicationCallPipeline.Features) {
            if (call.sessions.get<DrawingSession>() == null) {
                val clientId = call.parameters["client_id"] ?: ""
                call.sessions.set(DrawingSession(clientId, generateNonce()))
            }
        }



    }.start(wait = true)
}
