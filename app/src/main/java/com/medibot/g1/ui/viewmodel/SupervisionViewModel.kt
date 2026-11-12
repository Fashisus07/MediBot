package com.medibot.g1.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.medibot.g1.data.local.AppDatabase
import com.medibot.g1.data.local.EventLogEntity
import com.medibot.g1.data.local.MissionHistoryEntity
import com.medibot.g1.data.model.*
import com.medibot.g1.data.repository.RobotRepository
import com.medibot.g1.data.repository.SimulatedRobotRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

data class SupervisionUiState(
    val telemetry: TelemetryState = TelemetryState(),
    val commandState: CommandExecutionState = CommandExecutionState.Idle,
    val isTelemetryStale: Boolean = false,
    val staleSecondsAgo: Long = 0L,
    val forceRejectNext: Boolean = false
)

class SupervisionViewModel(
    private val repository: RobotRepository,
    private val database: AppDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SupervisionUiState())
    val uiState: StateFlow<SupervisionUiState> = _uiState.asStateFlow()

    val missionsHistory: StateFlow<List<MissionHistoryEntity>> = database.missionHistoryDao()
        .getAllMissions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val eventLogs: StateFlow<List<EventLogEntity>> = database.eventLogDao()
        .getAllEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var staleCheckJob: Job? = null
    private var lastRecordedMission: MissionConfig? = null
    private var lastOperationalState: OperationalState = OperationalState.IDLE_AT_DOCK

    init {
        observeTelemetry()
        observeCommandState()
        observeForceReject()
        startStaleDataTicker()
        logSystemStartup()
    }

    private fun logSystemStartup() {
        viewModelScope.launch {
            database.eventLogDao().insertEvent(
                EventLogEntity(
                    category = "SYSTEM",
                    severity = "INFO",
                    title = "Sistema HRI Iniciado",
                    details = "Supervisión HMI/HRI conectada con Unitree G1 EDU (G1-EDU-MED01)."
                )
            )
        }
    }

    private fun observeTelemetry() {
        viewModelScope.launch {
            repository.telemetryFlow.collect { telemetry ->
                val prevMission = lastRecordedMission
                val prevState = lastOperationalState
                lastOperationalState = telemetry.operationalState

                // Detect mission transitions for Room history
                if (telemetry.currentMission != null) {
                    lastRecordedMission = telemetry.currentMission
                } else if (prevMission != null && prevState == OperationalState.EXECUTING_MISSION || prevState == OperationalState.AWAITING_NFC_HANDOFF) {
                    // Mission completed or transitioned to idle
                    val status = if (telemetry.servicePayload.smartLockState == SmartLockState.UNLOCKED_NFC_AUTHORIZED) {
                        "COMPLETED"
                    } else {
                        "CANCELLED"
                    }
                    saveMissionToHistory(prevMission, status, telemetry)
                    lastRecordedMission = null
                }

                _uiState.update { current ->
                    current.copy(telemetry = telemetry)
                }
            }
        }
    }

    private fun saveMissionToHistory(mission: MissionConfig, status: String, telemetry: TelemetryState) {
        viewModelScope.launch {
            val entity = MissionHistoryEntity(
                missionId = mission.missionId,
                missionName = mission.name,
                description = mission.description,
                priority = mission.priority.name,
                originNode = mission.origin.displayName,
                destinationNode = mission.destination.displayName,
                status = status,
                startTime = mission.createdAt,
                endTime = System.currentTimeMillis(),
                finalBatteryPct = telemetry.batteryPercentage,
                maxTempCelsius = telemetry.servicePayload.compartmentTempCelsius,
                maxVibrationG = telemetry.servicePayload.bioSampleStabilityIndexG,
                operatorNotes = "Misión finalizada con estado: $status"
            )
            database.missionHistoryDao().insertMission(entity)

            database.eventLogDao().insertEvent(
                EventLogEntity(
                    category = "MISSION",
                    severity = if (status == "COMPLETED") "INFO" else "WARNING",
                    title = "Misión $status: ${mission.name}",
                    details = "Destino: ${mission.destination.displayName} | Batería final: ${telemetry.batteryPercentage}%",
                    relatedMissionId = mission.missionId
                )
            )
        }
    }

    private fun observeCommandState() {
        viewModelScope.launch {
            repository.commandStateFlow.collect { cmdState ->
                _uiState.update { it.copy(commandState = cmdState) }

                // Log commands to Room when resolved
                when (cmdState) {
                    is CommandExecutionState.Confirmed -> {
                        database.eventLogDao().insertEvent(
                            EventLogEntity(
                                category = "COMMAND",
                                severity = "INFO",
                                title = "Comando Confirmado: ${cmdState.commandName}",
                                details = cmdState.message
                            )
                        )
                    }
                    is CommandExecutionState.Rejected -> {
                        database.eventLogDao().insertEvent(
                            EventLogEntity(
                                category = "COMMAND",
                                severity = "WARNING",
                                title = "Comando Rechazado: ${cmdState.commandName}",
                                details = "Motivo: ${cmdState.reason}"
                            )
                        )
                    }
                    is CommandExecutionState.NoResponse -> {
                        database.eventLogDao().insertEvent(
                            EventLogEntity(
                                category = "COMMAND",
                                severity = "CRITICAL",
                                title = "Timeout Enlace: ${cmdState.commandName}",
                                details = "Sin respuesta del robot tras ${cmdState.timeoutMs}ms (Enlace OFFLINE)."
                            )
                        )
                    }
                    else -> {}
                }
            }
        }
    }

    private fun observeForceReject() {
        viewModelScope.launch {
            repository.forceRejectNext.collect { forceReject ->
                _uiState.update { it.copy(forceRejectNext = forceReject) }
            }
        }
    }

    // Ticker to evaluate stale data (> 4000ms without fresh telemetry)
    private fun startStaleDataTicker() {
        staleCheckJob?.cancel()
        staleCheckJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                val currentTimestamp = System.currentTimeMillis()
                val lastUpdate = _uiState.value.telemetry.lastUpdatedTimestamp
                val diff = currentTimestamp - lastUpdate
                val isStale = diff > 4000L
                val staleSec = if (isStale) diff / 1000L else 0L

                _uiState.update { current ->
                    current.copy(
                        isTelemetryStale = isStale,
                        staleSecondsAgo = staleSec
                    )
                }
            }
        }
    }

    fun sendCommand(command: RobotCommand) {
        viewModelScope.launch {
            // Check if telemetry is stale when sending command
            if (_uiState.value.isTelemetryStale) {
                database.eventLogDao().insertEvent(
                    EventLogEntity(
                        category = "COMMAND",
                        severity = "WARNING",
                        title = "Comando emitido con Datos Antiguos",
                        details = "Se intentó enviar ${command.commandName} con enlace degradado/stale."
                    )
                )
            }
            repository.sendCommand(command)
        }
    }

    fun acknowledgeAlert(alertId: String) {
        viewModelScope.launch {
            repository.sendCommand(RobotCommand.AcknowledgeAlert(alertId))
            database.eventLogDao().insertEvent(
                EventLogEntity(
                    category = "ALERT",
                    severity = "INFO",
                    title = "Alerta Reconocida",
                    details = "Operador reconoció la alerta ID: $alertId"
                )
            )
        }
    }

    fun toggleForceReject(value: Boolean) {
        repository.setForceRejectNext(value)
    }

    fun restoreConnection() {
        viewModelScope.launch {
            repository.restoreConnection()
            database.eventLogDao().insertEvent(
                EventLogEntity(
                    category = "SYSTEM",
                    severity = "INFO",
                    title = "Enlace Wi-Fi Restablecido",
                    details = "Conexión con el robot reanudada por intervención de ingeniería."
                )
            )
        }
    }

    fun resetCommandFeedback() {
        repository.resetCommandState()
    }

    // Scenario Injectors
    fun injectScenario(scenarioIndex: Int) {
        viewModelScope.launch {
            val scenarioName = when (scenarioIndex) {
                1 -> "Escenario 1: Misión Normal"
                2 -> "Escenario 2: Batería Baja (14% -> 7%)"
                3 -> "Escenario 3: Desconexión y Telemetría Stale"
                4 -> "Escenario 4: Incidente en Pasillo (Camilla)"
                5 -> "Escenario 5: Falla de Cadena de Frío / Tamper"
                else -> "Escenario Desconocido"
            }

            database.eventLogDao().insertEvent(
                EventLogEntity(
                    category = "SCENARIO",
                    severity = "INFO",
                    title = "Inyección de Prueba: $scenarioName",
                    details = "Disparado manualmente desde el panel de ingeniería."
                )
            )

            when (scenarioIndex) {
                1 -> repository.injectScenarioNormalMission()
                2 -> repository.injectScenarioLowBattery()
                3 -> repository.injectScenarioDisconnection()
                4 -> repository.injectScenarioIncident()
                5 -> repository.injectScenarioServiceFailure()
            }
        }
    }

    // Room seed data for demonstration
    fun insertSeedData() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val seedMissions = listOf(
                MissionHistoryEntity(
                    missionId = "SEED01",
                    missionName = "Farmacia: Antibióticos → Piso 2",
                    description = "Traslado urgente de vancomicina y meropenem.",
                    priority = "URGENT",
                    originNode = HospitalNode.DOCK_BASE.displayName,
                    destinationNode = HospitalNode.SAFE_POINT_P2.displayName,
                    status = "COMPLETED",
                    startTime = now - 3600000,
                    endTime = now - 3000000,
                    finalBatteryPct = 91,
                    maxTempCelsius = 4.2f,
                    maxVibrationG = 0.18f,
                    operatorNotes = "Completado sin anomalías de transporte."
                ),
                MissionHistoryEntity(
                    missionId = "SEED02",
                    missionName = "Hemoterapia: Plasma Fresco",
                    description = "2 Unidades de PFC a Terapia Intensiva.",
                    priority = "URGENT",
                    originNode = HospitalNode.LAB_CENTRAL.displayName,
                    destinationNode = HospitalNode.UTI_NURSING.displayName,
                    status = "CANCELLED",
                    startTime = now - 7200000,
                    endTime = now - 6800000,
                    finalBatteryPct = 78,
                    maxTempCelsius = 5.8f,
                    maxVibrationG = 0.22f,
                    operatorNotes = "Cancelada por cambio de prioridad médica en UTI."
                )
            )

            for (m in seedMissions) {
                database.missionHistoryDao().insertMission(m)
            }

            val seedEvents = listOf(
                EventLogEntity(
                    timestamp = now - 3600000,
                    category = "MISSION",
                    severity = "INFO",
                    title = "Misión SEED01 Iniciada",
                    details = "Traslado de farmacia hacia Piso 2 en ejecución."
                ),
                EventLogEntity(
                    timestamp = now - 3000000,
                    category = "MISSION",
                    severity = "INFO",
                    title = "Misión SEED01 Completada",
                    details = "Custodia validada por enfermería."
                ),
                EventLogEntity(
                    timestamp = now - 1800000,
                    category = "ALERT",
                    severity = "WARNING",
                    title = "Tránsito Peatonal Denso",
                    details = "Velocidad reducida temporalmente a 0.3 m/s en pasillo central."
                )
            )

            for (e in seedEvents) {
                database.eventLogDao().insertEvent(e)
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            database.missionHistoryDao().clearAll()
            database.eventLogDao().clearAll()
            database.eventLogDao().insertEvent(
                EventLogEntity(
                    category = "SYSTEM",
                    severity = "INFO",
                    title = "Historial Depurado",
                    details = "Tablas de historial y auditoría limpiadas por el operador."
                )
            )
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = AppDatabase.getInstance(context)
                    val repo = SimulatedRobotRepository()
                    return SupervisionViewModel(repo, db) as T
                }
            }
        }
    }
}

