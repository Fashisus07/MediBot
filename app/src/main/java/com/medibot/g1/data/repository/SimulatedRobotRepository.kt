package com.medibot.g1.data.repository

import com.medibot.g1.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class SimulatedRobotRepository : RobotRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _telemetryFlow = MutableStateFlow(
        TelemetryState(
            robotId = "G1-EDU-MED01",
            firmwareVersion = "v2.4.1-DEX3",
            connectionStatus = ConnectionStatus.ONLINE,
            latencyMs = 42,
            signalDbm = -35,
            lastUpdatedTimestamp = System.currentTimeMillis(),
            batteryPercentage = 95,
            batteryTempCelsius = 28.5f,
            estimatedMinutesLeft = 190,
            operationalState = OperationalState.IDLE_AT_DOCK,
            currentMission = null,
            missionProgressPct = 0,
            currentNode = HospitalNode.DOCK_BASE,
            speedMps = 0.0f,
            posture = RobotPosture.BIPED_STANDING,
            hriLedColor = "#00F5D4",
            hriVoiceStatus = "Sistema listo en Base de Carga",
            servicePayload = ServicePayloadTelemetry(
                compartmentTempCelsius = 4.0f,
                bioSampleStabilityIndexG = 0.12f,
                smartLockState = SmartLockState.SEALED_LOCKED
            ),
            activeAlerts = emptyList()
        )
    )
    override val telemetryFlow: StateFlow<TelemetryState> = _telemetryFlow.asStateFlow()

    private val _commandStateFlow = MutableStateFlow<CommandExecutionState>(CommandExecutionState.Idle)
    override val commandStateFlow: StateFlow<CommandExecutionState> = _commandStateFlow.asStateFlow()

    private val _forceRejectNext = MutableStateFlow(false)
    override val forceRejectNext: StateFlow<Boolean> = _forceRejectNext.asStateFlow()

    private var telemetryJob: Job? = null
    private var scenarioJob: Job? = null

    init {
        startTelemetryLoop()
    }

    private fun startTelemetryLoop() {
        telemetryJob?.cancel()
        telemetryJob = repositoryScope.launch {
            while (isActive) {
                delay(1000L)
                val current = _telemetryFlow.value
                if (current.connectionStatus != ConnectionStatus.OFFLINE) {
                    val latVariation = (-3..4).random()
                    val tempVariation = ((-1..1).random()) * 0.1f
                    val signalVariation = (-2..2).random()
                    val vibVariation = if (current.speedMps > 0.1f) ((-2..3).random()) * 0.01f else 0.0f

                    _telemetryFlow.value = current.copy(
                        lastUpdatedTimestamp = System.currentTimeMillis(),
                        latencyMs = (current.latencyMs + latVariation).coerceIn(28, 85),
                        signalDbm = (current.signalDbm + signalVariation).coerceIn(-75, -20),
                        batteryTempCelsius = ((current.batteryTempCelsius + tempVariation) * 10).toInt() / 10f,
                        servicePayload = current.servicePayload.copy(
                            bioSampleStabilityIndexG = if (current.speedMps > 0f) {
                                (0.12f + vibVariation).coerceIn(0.08f, 0.45f)
                            } else {
                                0.05f
                            }
                        )
                    )
                }
                // When OFFLINE, lastUpdatedTimestamp is deliberately NOT updated to trigger stale detection
            }
        }
    }

    override fun resetCommandState() {
        _commandStateFlow.value = CommandExecutionState.Idle
    }

    override suspend fun sendCommand(command: RobotCommand) {
        val current = _telemetryFlow.value

        // Check force reject flag from debug panel
        if (_forceRejectNext.value) {
            _forceRejectNext.value = false
            _commandStateFlow.value = CommandExecutionState.Pending(command.commandName)
            delay(1200L)
            _commandStateFlow.value = CommandExecutionState.Rejected(
                command.commandName,
                "Comando rechazado por política de seguridad (Forzado por modo debug de ingeniería)"
            )
            return
        }

        // Check if offline
        if (current.connectionStatus == ConnectionStatus.OFFLINE) {
            _commandStateFlow.value = CommandExecutionState.Pending(command.commandName)
            delay(2500L)
            _commandStateFlow.value = CommandExecutionState.NoResponse(command.commandName, 2500L)
            return
        }

        // Simulated network latency
        _commandStateFlow.value = CommandExecutionState.Pending(command.commandName)
        delay(1200L)

        // Process discrete command
        when (command) {
            is RobotCommand.StartMission -> handleStartMission(command.mission)
            is RobotCommand.PauseMission -> handlePauseMission()
            is RobotCommand.ResumeMission -> handleResumeMission()
            is RobotCommand.CancelMission -> handleCancelMission()
            is RobotCommand.ReturnToSafePoint -> handleReturnToSafePoint()
            is RobotCommand.OperationalStop -> handleOperationalStop()
            is RobotCommand.AcknowledgeAlert -> handleAcknowledgeAlert(command.alertId)
        }
    }

    private fun handleStartMission(mission: MissionConfig) {
        val current = _telemetryFlow.value

        if (current.batteryPercentage < 20) {
            _commandStateFlow.value = CommandExecutionState.Rejected(
                "START_MISSION",
                "Batería crítica (${current.batteryPercentage}%). Se requiere mínimo 20% para iniciar."
            )
            return
        }

        if (current.servicePayload.smartLockState != SmartLockState.SEALED_LOCKED) {
            _commandStateFlow.value = CommandExecutionState.Rejected(
                "START_MISSION",
                "Compuerta no sellada (${current.servicePayload.smartLockState.displayName}). Cierre el compartimento antes de iniciar."
            )
            return
        }

        if (current.servicePayload.compartmentTempCelsius > 6.0f) {
            _commandStateFlow.value = CommandExecutionState.Rejected(
                "START_MISSION",
                "Temperatura del compartimento elevada (${current.servicePayload.compartmentTempCelsius}°C). Máximo permitido 6.0°C."
            )
            return
        }

        if (current.operationalState != OperationalState.IDLE_AT_DOCK &&
            current.operationalState != OperationalState.PAUSED &&
            current.operationalState != OperationalState.RETURNING_TO_SAFE_POINT) {
            _commandStateFlow.value = CommandExecutionState.Rejected(
                "START_MISSION",
                "Robot en estado incompatible: ${current.operationalState.displayName}"
            )
            return
        }

        _telemetryFlow.value = current.copy(
            operationalState = OperationalState.EXECUTING_MISSION,
            currentMission = mission,
            missionProgressPct = 0,
            currentNode = mission.origin,
            speedMps = 0.6f,
            posture = RobotPosture.BIPED_WALKING,
            hriLedColor = "#00F5D4",
            hriVoiceStatus = "Iniciando misión: ${mission.name}"
        )

        _commandStateFlow.value = CommandExecutionState.Confirmed(
            "START_MISSION",
            "Misión '${mission.name}' iniciada con éxito hacia ${mission.destination.displayName}."
        )

        // Launch execution steps
        scenarioJob?.cancel()
        scenarioJob = repositoryScope.launch {
            val route = mission.route
            for (i in route.indices) {
                delay(2500L)
                val node = route[i]
                val pct = ((i + 1) * 100) / route.size
                val snap = _telemetryFlow.value
                if (snap.operationalState != OperationalState.EXECUTING_MISSION) break

                val newState = if (node == HospitalNode.ELEVATOR_BANK) OperationalState.WAITING_ELEVATOR else OperationalState.EXECUTING_MISSION
                val newPosture = if (node == HospitalNode.FIRE_DOOR_PB) RobotPosture.OPERATING_DOOR_ARM else RobotPosture.BIPED_WALKING

                _telemetryFlow.value = snap.copy(
                    lastUpdatedTimestamp = System.currentTimeMillis(),
                    currentNode = node,
                    operationalState = newState,
                    missionProgressPct = pct,
                    posture = newPosture,
                    speedMps = if (node == HospitalNode.ELEVATOR_BANK) 0.0f else 0.6f,
                    hriVoiceStatus = "Transitando por: ${node.displayName}"
                )
            }

            val finalSnap = _telemetryFlow.value
            if (finalSnap.operationalState == OperationalState.EXECUTING_MISSION) {
                _telemetryFlow.value = finalSnap.copy(
                    operationalState = OperationalState.AWAITING_NFC_HANDOFF,
                    missionProgressPct = 100,
                    speedMps = 0.0f,
                    posture = RobotPosture.BIPED_STANDING,
                    hriLedColor = "#7C4DFF",
                    hriVoiceStatus = "Destino alcanzado. A la espera de validación de enfermería (NFC)."
                )

                delay(2500L)
                val validatedSnap = _telemetryFlow.value
                _telemetryFlow.value = validatedSnap.copy(
                    operationalState = OperationalState.IDLE_AT_DOCK,
                    currentMission = null,
                    missionProgressPct = 0,
                    hriLedColor = "#00E676",
                    hriVoiceStatus = "Misión completada. Muestra biológica entregada.",
                    servicePayload = validatedSnap.servicePayload.copy(
                        smartLockState = SmartLockState.UNLOCKED_NFC_AUTHORIZED
                    ),
                    activeAlerts = validatedSnap.activeAlerts + RobotAlert(
                        id = UUID.randomUUID().toString().take(8),
                        severity = AlertSeverity.INFO,
                        title = "Misión Finalizada",
                        message = "Misión ${mission.name} completada satisfactoriamente con validación NFC."
                    )
                )
            }
        }
    }

    private fun handlePauseMission() {
        val current = _telemetryFlow.value
        if (current.operationalState != OperationalState.EXECUTING_MISSION) {
            _commandStateFlow.value = CommandExecutionState.Rejected(
                "PAUSE_MISSION",
                "El robot no se encuentra en ejecución de misión activa."
            )
            return
        }

        if (current.currentNode == HospitalNode.ELEVATOR_BANK) {
            _commandStateFlow.value = CommandExecutionState.Rejected(
                "PAUSE_MISSION",
                "Protocolo de Seguridad: Prohibido pausar durante la maniobra o cruce de puertas del ascensor."
            )
            return
        }

        scenarioJob?.cancel()
        _telemetryFlow.value = current.copy(
            operationalState = OperationalState.PAUSED,
            speedMps = 0.0f,
            posture = RobotPosture.BIPED_STANDING,
            hriLedColor = "#FFAB00",
            hriVoiceStatus = "Misión en pausa por comando de operador"
        )
        _commandStateFlow.value = CommandExecutionState.Confirmed(
            "PAUSE_MISSION",
            "Misión pausada de forma segura."
        )
    }

    private fun handleResumeMission() {
        val current = _telemetryFlow.value
        if (current.operationalState != OperationalState.PAUSED) {
            _commandStateFlow.value = CommandExecutionState.Rejected(
                "RESUME_MISSION",
                "El robot no está pausado (Estado actual: ${current.operationalState.displayName})."
            )
            return
        }

        _telemetryFlow.value = current.copy(
            operationalState = OperationalState.EXECUTING_MISSION,
            speedMps = 0.6f,
            posture = RobotPosture.BIPED_WALKING,
            hriLedColor = "#00F5D4",
            hriVoiceStatus = "Reanudando trayectoria"
        )
        _commandStateFlow.value = CommandExecutionState.Confirmed(
            "RESUME_MISSION",
            "Misión reanudada con éxito."
        )
    }

    private fun handleCancelMission() {
        val current = _telemetryFlow.value
        if (current.currentMission == null && current.operationalState != OperationalState.EXECUTING_MISSION && current.operationalState != OperationalState.PAUSED) {
            _commandStateFlow.value = CommandExecutionState.Rejected(
                "CANCEL_MISSION",
                "No hay ninguna misión activa o pausada para cancelar."
            )
            return
        }

        scenarioJob?.cancel()
        _telemetryFlow.value = current.copy(
            operationalState = OperationalState.IDLE_AT_DOCK,
            currentMission = null,
            missionProgressPct = 0,
            speedMps = 0.0f,
            posture = RobotPosture.BIPED_STANDING,
            hriLedColor = "#FFAB00",
            hriVoiceStatus = "Misión cancelada por el operador",
            activeAlerts = current.activeAlerts + RobotAlert(
                id = UUID.randomUUID().toString().take(8),
                severity = AlertSeverity.WARNING,
                title = "Misión Cancelada",
                message = "La misión actual fue abortada por orden directa del operador."
            )
        )
        _commandStateFlow.value = CommandExecutionState.Confirmed(
            "CANCEL_MISSION",
            "Misión cancelada. El robot permanece estático a la espera de instrucciones."
        )
    }

    private fun handleReturnToSafePoint() {
        val current = _telemetryFlow.value
        scenarioJob?.cancel()

        _telemetryFlow.value = current.copy(
            operationalState = OperationalState.RETURNING_TO_SAFE_POINT,
            speedMps = 0.4f,
            posture = RobotPosture.BIPED_WALKING,
            hriLedColor = "#FFAB00",
            hriVoiceStatus = "Evacuando hacia Punto Seguro de Espera"
        )
        _commandStateFlow.value = CommandExecutionState.Confirmed(
            "RETURN_TO_SAFE_POINT",
            "Orden de retorno a Punto Seguro confirmada."
        )

        scenarioJob = repositoryScope.launch {
            delay(3000L)
            val updated = _telemetryFlow.value
            _telemetryFlow.value = updated.copy(
                operationalState = OperationalState.IDLE_AT_DOCK,
                currentNode = HospitalNode.SAFE_POINT_P2,
                currentMission = null,
                missionProgressPct = 0,
                speedMps = 0.0f,
                posture = RobotPosture.BIPED_STANDING,
                hriLedColor = "#00F5D4",
                hriVoiceStatus = "A salvo en Punto Seguro de Espera (P2)"
            )
        }
    }

    private fun handleOperationalStop() {
        scenarioJob?.cancel()
        val current = _telemetryFlow.value
        _telemetryFlow.value = current.copy(
            operationalState = OperationalState.EMERGENCY_STOPPED,
            speedMps = 0.0f,
            posture = RobotPosture.BIPED_STANDING,
            hriLedColor = "#FF1744",
            hriVoiceStatus = "PARADA OPERATIVA ACTIVADA",
            activeAlerts = current.activeAlerts + RobotAlert(
                id = UUID.randomUUID().toString().take(8),
                severity = AlertSeverity.CRITICAL,
                title = "Detención Operativa",
                message = "Detención operativa por software ejecutada de inmediato. Nota: No sustituye la parada física de emergencia."
            )
        )
        _commandStateFlow.value = CommandExecutionState.Confirmed(
            "OPERATIONAL_STOP",
            "Detención operativa inmediata completada."
        )
    }

    private fun handleAcknowledgeAlert(alertId: String) {
        val current = _telemetryFlow.value
        val alert = current.activeAlerts.find { it.id == alertId }
        if (alert == null) {
            _commandStateFlow.value = CommandExecutionState.Rejected(
                "ACKNOWLEDGE_ALERT",
                "Identificador de alerta '$alertId' no encontrado."
            )
            return
        }

        _telemetryFlow.value = current.copy(
            activeAlerts = current.activeAlerts.map {
                if (it.id == alertId) it.copy(isAcknowledged = true) else it
            }
        )
        _commandStateFlow.value = CommandExecutionState.Confirmed(
            "ACKNOWLEDGE_ALERT",
            "Alerta '${alert.title}' reconocida."
        )
    }

    // ==================== ESCENARIOS OBLIGATORIOS ====================

    override fun injectScenarioNormalMission() {
        scenarioJob?.cancel()
        val mission = MissionConfig(
            name = "Urgencia: Gases en Sangre → UTI",
            description = "Transporte prioritario de jeringa heparinizada de gases arteriales desde Lab Central a UTI Piso 2.",
            priority = MissionPriority.URGENT,
            origin = HospitalNode.LAB_CENTRAL,
            destination = HospitalNode.UTI_NURSING,
            route = listOf(
                HospitalNode.LAB_CENTRAL,
                HospitalNode.FIRE_DOOR_PB,
                HospitalNode.ELEVATOR_BANK,
                HospitalNode.SAFE_POINT_P2,
                HospitalNode.UTI_NURSING
            ),
            requiredTempRange = 2.0f..6.0f,
            maxVibrationG = 0.30f,
            requiresNfcHandoff = true
        )

        _telemetryFlow.value = TelemetryState(
            robotId = "G1-EDU-MED01",
            firmwareVersion = "v2.4.1-DEX3",
            connectionStatus = ConnectionStatus.ONLINE,
            latencyMs = 38,
            signalDbm = -32,
            lastUpdatedTimestamp = System.currentTimeMillis(),
            batteryPercentage = 85,
            batteryTempCelsius = 29.2f,
            estimatedMinutesLeft = 175,
            operationalState = OperationalState.EXECUTING_MISSION,
            currentMission = mission,
            missionProgressPct = 5,
            currentNode = HospitalNode.LAB_CENTRAL,
            speedMps = 0.6f,
            posture = RobotPosture.BIPED_WALKING,
            hriLedColor = "#00F5D4",
            hriVoiceStatus = "Misión iniciada: Gases en Sangre → UTI",
            servicePayload = ServicePayloadTelemetry(
                compartmentTempCelsius = 4.0f,
                bioSampleStabilityIndexG = 0.14f,
                smartLockState = SmartLockState.SEALED_LOCKED
            ),
            activeAlerts = emptyList()
        )

        scenarioJob = repositoryScope.launch {
            val route = mission.route
            for (i in route.indices) {
                delay(2000L)
                val node = route[i]
                val pct = ((i + 1) * 100) / route.size
                val snap = _telemetryFlow.value

                val newState = when (node) {
                    HospitalNode.ELEVATOR_BANK -> OperationalState.WAITING_ELEVATOR
                    else -> OperationalState.EXECUTING_MISSION
                }

                val newPosture = when (node) {
                    HospitalNode.FIRE_DOOR_PB -> RobotPosture.OPERATING_DOOR_ARM
                    HospitalNode.ELEVATOR_BANK -> RobotPosture.BIPED_STANDING
                    else -> RobotPosture.BIPED_WALKING
                }

                val speed = if (node == HospitalNode.ELEVATOR_BANK) 0.0f else 0.6f

                _telemetryFlow.value = snap.copy(
                    lastUpdatedTimestamp = System.currentTimeMillis(),
                    currentNode = node,
                    operationalState = newState,
                    missionProgressPct = pct,
                    posture = newPosture,
                    speedMps = speed,
                    batteryPercentage = (snap.batteryPercentage - 1).coerceAtLeast(1),
                    estimatedMinutesLeft = (snap.estimatedMinutesLeft - 2).coerceAtLeast(1),
                    hriVoiceStatus = "Transitando por: ${node.displayName}",
                    servicePayload = snap.servicePayload.copy(
                        compartmentTempCelsius = 3.9f + ((-1..1).random() * 0.1f),
                        bioSampleStabilityIndexG = 0.15f + ((-2..2).random() * 0.02f)
                    )
                )
            }

            // Arrive at UTI
            delay(1500L)
            val atUti = _telemetryFlow.value
            _telemetryFlow.value = atUti.copy(
                operationalState = OperationalState.AWAITING_NFC_HANDOFF,
                missionProgressPct = 100,
                speedMps = 0.0f,
                posture = RobotPosture.BIPED_STANDING,
                hriLedColor = "#7C4DFF",
                hriVoiceStatus = "Esperando validación NFC de enfermería en UTI"
            )

            // Simulate authorized NFC tap
            delay(2500L)
            val done = _telemetryFlow.value
            _telemetryFlow.value = done.copy(
                operationalState = OperationalState.IDLE_AT_DOCK,
                currentMission = null,
                missionProgressPct = 0,
                speedMps = 0.0f,
                hriLedColor = "#00E676",
                hriVoiceStatus = "Misión completada con éxito. Custodia transferida.",
                servicePayload = done.servicePayload.copy(
                    smartLockState = SmartLockState.UNLOCKED_NFC_AUTHORIZED
                ),
                activeAlerts = done.activeAlerts + RobotAlert(
                    id = UUID.randomUUID().toString().take(8),
                    severity = AlertSeverity.INFO,
                    title = "Entrega Validada",
                    message = "Muestra de gases recibida correctamente en Enfermería UTI con firma digital NFC."
                )
            )
        }
    }

    override fun injectScenarioLowBattery() {
        scenarioJob?.cancel()
        val current = _telemetryFlow.value

        _telemetryFlow.value = current.copy(
            batteryPercentage = 14,
            estimatedMinutesLeft = 24,
            batteryTempCelsius = 36.1f,
            hriLedColor = "#FFAB00",
            hriVoiceStatus = "Batería baja: Retorno a base recomendado",
            activeAlerts = current.activeAlerts + RobotAlert(
                id = UUID.randomUUID().toString().take(8),
                severity = AlertSeverity.WARNING,
                title = "Batería Baja (14%)",
                message = "Capacidad insuficiente para nuevas misiones. Se prohíbe el inicio de traslados. Regrese a punto seguro o base."
            )
        )

        scenarioJob = repositoryScope.launch {
            delay(5000L)
            val snap = _telemetryFlow.value
            _telemetryFlow.value = snap.copy(
                batteryPercentage = 7,
                estimatedMinutesLeft = 11,
                batteryTempCelsius = 39.4f,
                operationalState = OperationalState.LOW_BATTERY_CROUCHED,
                speedMps = 0.0f,
                posture = RobotPosture.CROUCHED_SAFE_LOCK,
                hriLedColor = "#FF1744",
                hriVoiceStatus = "Batería crítica. Postura agachada de seguridad activa para evitar desplome.",
                activeAlerts = snap.activeAlerts + RobotAlert(
                    id = UUID.randomUUID().toString().take(8),
                    severity = AlertSeverity.CRITICAL,
                    title = "Batería Crítica (7%) - Agachado",
                    message = "El robot adoptó la postura bípeda agachada (CROUCHED_SAFE_LOCK) para prevenir caídas accidentales. Conecte cargador urgente."
                )
            )
        }
    }

    override fun injectScenarioDisconnection() {
        scenarioJob?.cancel()
        telemetryJob?.cancel()
        val current = _telemetryFlow.value
        _telemetryFlow.value = current.copy(
            connectionStatus = ConnectionStatus.OFFLINE,
            latencyMs = 0,
            signalDbm = -99,
            hriVoiceStatus = "Sin enlace telemétrico"
        )
    }

    override fun restoreConnection() {
        val current = _telemetryFlow.value
        _telemetryFlow.value = current.copy(
            connectionStatus = ConnectionStatus.ONLINE,
            latencyMs = 40,
            signalDbm = -34,
            lastUpdatedTimestamp = System.currentTimeMillis(),
            hriVoiceStatus = "Enlace Wi-Fi 6 industrial restablecido"
        )
        startTelemetryLoop()
    }

    override fun injectScenarioIncident() {
        scenarioJob?.cancel()
        val mission = MissionConfig(
            name = "Hemoterapia: 2 Unidades Plasma → Quirófano",
            description = "Transporte de hemocomponentes con estricto control de temperatura.",
            priority = MissionPriority.URGENT,
            origin = HospitalNode.LAB_CENTRAL,
            destination = HospitalNode.UTI_NURSING,
            route = listOf(
                HospitalNode.LAB_CENTRAL,
                HospitalNode.FIRE_DOOR_PB,
                HospitalNode.ELEVATOR_BANK,
                HospitalNode.SAFE_POINT_P2,
                HospitalNode.UTI_NURSING
            )
        )

        _telemetryFlow.value = TelemetryState(
            batteryPercentage = 76,
            operationalState = OperationalState.EXECUTING_MISSION,
            currentMission = mission,
            missionProgressPct = 20,
            currentNode = HospitalNode.LAB_CENTRAL,
            speedMps = 0.6f,
            posture = RobotPosture.BIPED_WALKING,
            hriLedColor = "#00F5D4",
            hriVoiceStatus = "En tránsito hacia puerta cortafuego PB",
            servicePayload = ServicePayloadTelemetry(
                compartmentTempCelsius = 3.6f,
                bioSampleStabilityIndexG = 0.16f,
                smartLockState = SmartLockState.SEALED_LOCKED
            )
        )

        scenarioJob = repositoryScope.launch {
            delay(2000L)
            val snap = _telemetryFlow.value
            _telemetryFlow.value = snap.copy(
                lastUpdatedTimestamp = System.currentTimeMillis(),
                currentNode = HospitalNode.FIRE_DOOR_PB,
                operationalState = OperationalState.INCIDENT_BLOCKED,
                missionProgressPct = 40,
                speedMps = 0.0f,
                posture = RobotPosture.BIPED_STANDING,
                hriLedColor = "#FF1744",
                hriVoiceStatus = "INCIDENTE: Pasillo bloqueado por camilla hospitalaria",
                activeAlerts = snap.activeAlerts + RobotAlert(
                    id = UUID.randomUUID().toString().take(8),
                    severity = AlertSeverity.CRITICAL,
                    title = "Obstáculo Crítico en Pasillo",
                    message = "Camilla bloqueando paso frontal a 0.35m en Puerta Cortafuego PB. Decisión requerida del operador: Esperar y Reanudar o Desviar a Punto Seguro."
                )
            )
        }
    }

    override fun injectScenarioServiceFailure() {
        scenarioJob?.cancel()
        val current = _telemetryFlow.value

        _telemetryFlow.value = current.copy(
            hriLedColor = "#FF1744",
            hriVoiceStatus = "ALERTA BIOLÓGICA: Cadena de frío rota y manipulación detectada",
            servicePayload = current.servicePayload.copy(
                compartmentTempCelsius = 8.4f,
                smartLockState = SmartLockState.TAMPER_ALERT
            ),
            activeAlerts = current.activeAlerts + listOf(
                RobotAlert(
                    id = UUID.randomUUID().toString().take(8),
                    severity = AlertSeverity.CRITICAL,
                    title = "Cadena de Frío Comprometida (8.4°C)",
                    message = "Temperatura de cápsula superó el límite biológico (8.4°C > 6.0°C). Alto riesgo de desnaturalización. Reconozca alerta y cancele misión o desvíe a punto seguro."
                ),
                RobotAlert(
                    id = UUID.randomUUID().toString().take(8),
                    severity = AlertSeverity.CRITICAL,
                    title = "Alerta de Manipulación (Tamper)",
                    message = "Se registró apertura forzada o manipulación no autorizada del cerrojo electromagnético del compartimento biológico."
                )
            )
        )
    }

    override fun setForceRejectNext(value: Boolean) {
        _forceRejectNext.value = value
    }
}

