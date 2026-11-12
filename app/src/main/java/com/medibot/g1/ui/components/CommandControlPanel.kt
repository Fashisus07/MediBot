package com.medibot.g1.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medibot.g1.data.model.CommandExecutionState
import com.medibot.g1.data.model.OperationalState
import com.medibot.g1.data.model.RobotCommand
import com.medibot.g1.ui.theme.*

@Composable
fun CommandControlPanel(
    operationalState: OperationalState,
    commandState: CommandExecutionState,
    onSendCommand: (RobotCommand) -> Unit,
    onResetFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .border(1.dp, CardNormalBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CONSOLA DE CONTROL OPERATIVO",
                    style = MaterialTheme.typography.titleMedium,
                    color = DarkOnBackground,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Fixed Safety Disclaimer (OBLIGATORIO)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(WarningAmber.copy(alpha = 0.1f))
                    .border(1.dp, WarningAmber.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AVISO DE SEGURIDAD HRI: Las órdenes emitidas son de supervisión lógica por software. No reemplazan la parada física de emergencia (botón tipo seta E-Stop en la cintura del Unitree G1).",
                        fontSize = 10.sp,
                        color = DarkOnSurface,
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Feedback Banner for CommandExecutionState
            AnimatedVisibility(
                visible = commandState !is CommandExecutionState.Idle,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when (commandState) {
                                is CommandExecutionState.Pending -> NeonCyan.copy(alpha = 0.15f)
                                is CommandExecutionState.Confirmed -> TerminalGreen.copy(alpha = 0.15f)
                                is CommandExecutionState.Rejected -> WarningAmber.copy(alpha = 0.18f)
                                is CommandExecutionState.NoResponse -> CriticalRed.copy(alpha = 0.18f)
                                is CommandExecutionState.Idle -> Color.Transparent
                            }
                        )
                        .border(
                            1.dp,
                            when (commandState) {
                                is CommandExecutionState.Pending -> NeonCyan
                                is CommandExecutionState.Confirmed -> TerminalGreen
                                is CommandExecutionState.Rejected -> WarningAmber
                                is CommandExecutionState.NoResponse -> CriticalRed
                                is CommandExecutionState.Idle -> Color.Transparent
                            },
                            RoundedCornerShape(10.dp)
                        )
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            when (commandState) {
                                is CommandExecutionState.Pending -> {
                                    CircularProgressIndicator(
                                        color = NeonCyan,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Transmitiendo [${commandState.commandName}]... (1200ms)",
                                        fontSize = 12.sp,
                                        color = NeonCyan,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                is CommandExecutionState.Confirmed -> {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = TerminalGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "CONFIRMADO: ${commandState.message}",
                                        fontSize = 12.sp,
                                        color = TerminalGreen,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                is CommandExecutionState.Rejected -> {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = null,
                                        tint = WarningAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "RECHAZADO: ${commandState.reason}",
                                        fontSize = 12.sp,
                                        color = WarningAmber,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                is CommandExecutionState.NoResponse -> {
                                    Icon(
                                        imageVector = Icons.Default.SignalWifiConnectedNoInternet4,
                                        contentDescription = null,
                                        tint = CriticalRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "SIN RESPUESTA: Enlace telemétrico fuera de línea (> 2500ms).",
                                        fontSize = 12.sp,
                                        color = CriticalRed,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                is CommandExecutionState.Idle -> {}
                            }
                        }

                        if (commandState !is CommandExecutionState.Pending) {
                            IconButton(
                                onClick = onResetFeedback,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar",
                                    tint = DarkOnSurface,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Command Action Buttons Grid
            val isExecuting = operationalState == OperationalState.EXECUTING_MISSION
            val isPaused = operationalState == OperationalState.PAUSED
            val canCancel = isExecuting || isPaused || operationalState == OperationalState.INCIDENT_BLOCKED
            val canReturn = operationalState != OperationalState.IDLE_AT_DOCK && operationalState != OperationalState.LOW_BATTERY_CROUCHED

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Pause Button
                    Button(
                        onClick = { onSendCommand(RobotCommand.PauseMission) },
                        enabled = isExecuting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WarningAmber,
                            contentColor = DarkBackground,
                            disabledContainerColor = DarkSurfaceVariant,
                            disabledContentColor = StaleGray
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PAUSAR", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    // Resume Button
                    Button(
                        onClick = { onSendCommand(RobotCommand.ResumeMission) },
                        enabled = isPaused,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TerminalGreen,
                            contentColor = DarkBackground,
                            disabledContainerColor = DarkSurfaceVariant,
                            disabledContentColor = StaleGray
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("REANUDAR", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Return to Safe Point Button
                    OutlinedButton(
                        onClick = { onSendCommand(RobotCommand.ReturnToSafePoint) },
                        enabled = canReturn,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NeonCyan,
                            disabledContentColor = StaleGray
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                if (canReturn) NeonCyan else CardStaleBorder
                            )
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("A PTO. SEGURO", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    // Cancel Mission Button
                    OutlinedButton(
                        onClick = { onSendCommand(RobotCommand.CancelMission) },
                        enabled = canCancel,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = CriticalRed,
                            disabledContentColor = StaleGray
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                if (canCancel) CriticalRed else CardStaleBorder
                            )
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("CANCELAR", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

