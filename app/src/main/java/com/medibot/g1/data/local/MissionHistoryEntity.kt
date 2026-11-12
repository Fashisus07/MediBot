package com.medibot.g1.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mission_history")
data class MissionHistoryEntity(
    @PrimaryKey val missionId: String,
    val missionName: String,
    val description: String,
    val priority: String,
    val originNode: String,
    val destinationNode: String,
    val status: String,
    val startTime: Long,
    val endTime: Long,
    val finalBatteryPct: Int,
    val maxTempCelsius: Float,
    val maxVibrationG: Float,
    val operatorNotes: String = ""
)
