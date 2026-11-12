package com.medibot.g1.data.repository

import com.medibot.g1.data.model.CommandExecutionState
import com.medibot.g1.data.model.RobotCommand
import com.medibot.g1.data.model.TelemetryState
import kotlinx.coroutines.flow.StateFlow

interface RobotRepository {
    val telemetryFlow: StateFlow<TelemetryState>
    val commandStateFlow: StateFlow<CommandExecutionState>
    val forceRejectNext: StateFlow<Boolean>

    suspend fun sendCommand(command: RobotCommand)

    // Scenario Injectors
    fun injectScenarioNormalMission()
    fun injectScenarioLowBattery()
    fun injectScenarioDisconnection()
    fun injectScenarioIncident()
    fun injectScenarioServiceFailure()

    // Debug controls
    fun setForceRejectNext(value: Boolean)
    fun restoreConnection()
    fun resetCommandState()
}

