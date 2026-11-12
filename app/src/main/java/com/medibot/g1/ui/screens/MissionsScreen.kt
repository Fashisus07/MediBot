package com.medibot.g1.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medibot.g1.data.model.*
import com.medibot.g1.ui.theme.*

@Composable
fun MissionsScreen(
    telemetry: TelemetryState,
    onStartMission: (MissionConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val predefinedMissions = remember {
        listOf(
            MissionConfig(
                missionId = "MED-01-GAS",
                name = "Urgencia: Gases en Sangre → UTI",
                description = "Transporte prioritario de muestra arterial heparinada desde el Laboratorio Central hasta el puesto de Terapia Intensiva.",
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
            ),
            MissionConfig(
                missionId = "MED-02-HEM",
                name = "Hemoterapia: 2 Unidades Plasma → Quirófano",
                description = "Traslado de 2 bolsas de plasma fresco congelado hacia quirófano/UTI en el Piso 2, manteniendo hermetismo y cadena de frío.",
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
                maxVibrationG = 0.25f,
                requiresNfcHandoff = true
            ),
            MissionConfig(
                missionId = "MED-03-FAR",
                name = "Farmacia: Medicación Controlada → Piso 2",
                description = "Distribución programada de antibióticos y analgésicos desde Base de Carga hasta el Receso Seguro de Espera.",
                priority = MissionPriority.STANDARD,
                origin = HospitalNode.DOCK_BASE,
                destination = HospitalNode.SAFE_POINT_P2,
                route = listOf(
                    HospitalNode.DOCK_BASE,
                    HospitalNode.LAB_CENTRAL,
                    HospitalNode.FIRE_DOOR_PB,
                    HospitalNode.ELEVATOR_BANK,
                    HospitalNode.SAFE_POINT_P2
                ),
                requiredTempRange = 15.0f..25.0f,
                maxVibrationG = 0.40f,
                requiresNfcHandoff = true
            )
        )
    }

    var selectedMissionIndex by remember { mutableIntStateOf(0) }
    val selectedMission = predefinedMissions[selectedMissionIndex]

    // Pre-condition evaluations
    val isBatteryOk = telemetry.batteryPercentage >= 20
    val isLockSealed = telemetry.servicePayload.smartLockState == SmartLockState.SEALED_LOCKED
    val isTempOk = telemetry.servicePayload.compartmentTempCelsius <= 6.0f
    val isIdle = telemetry.operationalState == OperationalState.IDLE_AT_DOCK || telemetry.operationalState == OperationalState.PAUSED
    val allPreconditionsMet = isBatteryOk && isLockSealed && isTempOk && isIdle

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Text(
            text = "CATÁLOGO DE MISIONES HOSPITALARIAS",
            style = MaterialTheme.typography.headlineMedium,
            color = DarkOnBackground,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Seleccione un protocolo de logística intrahospitalaria para despacho autónomo.",
            style = MaterialTheme.typography.bodySmall,
            color = DarkOnSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Preconditions Checklist Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurface)
                .border(
                    1.dp,
                    if (allPreconditionsMet) TerminalGreen.copy(alpha = 0.5f) else WarningAmber.copy(alpha = 0.5f),
                    RoundedCornerShape(14.dp)
                )
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CHECKLIST DE PRE-CONDICIONES DE DESPACHO",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkOnSurface,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = if (allPreconditionsMet) "TODO LISTO" else "BLOQUEADO",
                        color = if (allPreconditionsMet) TerminalGreen else WarningAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                PreconditionItem(
                    label = "Batería suficiente (> 20%)",
                    statusText = "${telemetry.batteryPercentage}%",
                    passed = isBatteryOk
                )
                PreconditionItem(
                    label = "Cerradura inteligente sellada",
                    statusText = telemetry.servicePayload.smartLockState.displayName,
                    passed = isLockSealed
                )
                PreconditionItem(
                    label = "Temperatura compartimento (< 6.0°C)",
                    statusText = "${telemetry.servicePayload.compartmentTempCelsius}°C",
                    passed = isTempOk
                )
                PreconditionItem(
                    label = "Robot disponible en reposo",
                    statusText = telemetry.operationalState.displayName,
                    passed = isIdle
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Missions List
        Text(
            text = "MISIONES CONFIGURADAS (3 DISPONIBLES):",
            style = MaterialTheme.typography.labelSmall,
            color = DarkOnSurface,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        predefinedMissions.forEachIndexed { index, mission ->
            val isSelected = index == selectedMissionIndex
            val priorityColor = when (mission.priority) {
                MissionPriority.URGENT -> CriticalRed
                MissionPriority.STANDARD -> NeonCyan
                MissionPriority.ROUTINE -> TerminalGreen
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) DarkSurfaceVariant else DarkSurface)
                    .border(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) NeonCyan else CardNormalBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { selectedMissionIndex = index }
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = mission.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isSelected) NeonCyan else DarkOnBackground,
                            fontWeight = FontWeight.Bold
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(priorityColor.copy(alpha = 0.15f))
                                .border(1.dp, priorityColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = mission.priority.displayName.uppercase(),
                                color = priorityColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = mission.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurface,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Ruta: ${mission.origin.displayName} → ${mission.destination.displayName}",
                            fontSize = 10.sp,
                            color = NeonCyan.copy(alpha = 0.8f),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Max vib: ${mission.maxVibrationG}G",
                            fontSize = 10.sp,
                            color = DarkOnSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Dispatch Button
        Button(
            onClick = { onStartMission(selectedMission) },
            enabled = allPreconditionsMet,
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonCyan,
                contentColor = DarkBackground,
                disabledContainerColor = DarkSurfaceVariant,
                disabledContentColor = StaleGray
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "DESPACHAR: ${selectedMission.name.take(28)}...",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PreconditionItem(label: String, statusText: String, passed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (passed) TerminalGreen else CriticalRed,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = DarkOnBackground
            )
        }

        Text(
            text = statusText,
            fontSize = 11.sp,
            color = if (passed) TerminalGreen else CriticalRed,
            fontWeight = FontWeight.SemiBold
        )
    }
}

