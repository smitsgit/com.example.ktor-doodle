package com.example.plugins

import io.ktor.application.*
import io.ktor.sessions.*

data class DrawingSession(
    val clientId: String,
    val sessionId: String
)

fun Application.configureSession() {
    install(Sessions) {
        cookie<DrawingSession>("SESSION")
    }
}