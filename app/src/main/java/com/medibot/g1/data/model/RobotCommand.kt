package com.medibot.g1.data.model

sealed class RobotCommand(val commandName: String) {
    data class StartMission(val mission: MissionConfig) : RobotCommand("START_MISSION")
    data object PauseMission : RobotCommand("PAUSE_MISSION")
    data object ResumeMission : RobotCommand("RESUME_MISSION")
    data object CancelMission : RobotCommand("CANCEL_MISSION")
    data object ReturnToSafePoint : RobotCommand("RETURN_TO_SAFE_POINT")
    data object OperationalStop : RobotCommand("OPERATIONAL_STOP")
    data class AcknowledgeAlert(val alertId: String) : RobotCommand("ACKNOWLEDGE_ALERT")
}
