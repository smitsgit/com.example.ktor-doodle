package com.example.data.models

import com.example.data.Room
import com.example.other.Constants.TYPE_PHASE_CHANGE

data class PhaseChange(
    var newPhase: Room.Phase?,
    var time: Long,
    val drawingPlayer: String? = null
): BaseModel(TYPE_PHASE_CHANGE)
