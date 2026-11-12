package com.medibot.g1.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.medibot.g1.data.local.EventLogEntity
import com.medibot.g1.data.local.MissionHistoryEntity
import com.medibot.g1.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    missionsHistory: List<MissionHistoryEntity>,
    eventLogs: List<EventLogEntity>,
    onInsertSeedData: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("MISIONES EJECUTADAS", "AUDITORÍA / EVENTOS")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // Header & Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "HISTORIAL Y AUDITORÍA (ROOM)",
                    style = MaterialTheme.typography.titleLarge,
                    color = DarkOnBackground,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Persistencia local SQLite en tiempo real",
                    fontSize = 11.sp,
                    color = DarkOnSurface
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onInsertSeedData,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(NeonCyan)
                    ),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SEMILLA", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onClearHistory,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CriticalRed),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(CriticalRed)
                    ),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("LIMPIAR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = DarkSurface,
            contentColor = NeonCyan,
            divider = { Divider(color = CardNormalBorder) }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp,
                            color = if (selectedTab == index) NeonCyan else DarkOnSurface
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Content
        if (selectedTab == 0) {
            MissionsTabContent(missions = missionsHistory)
        } else {
            EventsTabContent(events = eventLogs)
        }
    }
}

@Composable
private fun MissionsTabContent(missions: List<MissionHistoryEntity>) {
    if (missions.isEmpty()) {
        EmptyState(message = "No hay misiones persistidas en Room.\nEjecute el Escenario 1 o pulse 'SEMILLA' para poblar la base.")
    } else {
        val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(missions, key = { it.missionId }) { item ->
                val statusColor = when (item.status) {
                    "COMPLETED" -> TerminalGreen
                    "CANCELLED" -> WarningAmber
                    else -> CriticalRed
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurface)
                        .border(1.dp, CardNormalBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.missionName,
                                style = MaterialTheme.typography.titleMedium,
                                color = DarkOnBackground,
                                fontWeight = FontWeight.Bold
                            )

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(statusColor.copy(alpha = 0.15f))
                                    .border(1.dp, statusColor, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = item.status,
                                    color = statusColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "${item.originNode}  ➔  ${item.destinationNode}",
                            fontSize = 11.sp,
                            color = NeonCyan,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = item.description,
                            fontSize = 11.sp,
                            color = DarkOnSurface
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Divider(color = DarkSurfaceVariant, thickness = 1.dp)

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val durationSec = ((item.endTime - item.startTime) / 1000).coerceAtLeast(0)
                            Text(
                                text = "Fin: ${dateFormat.format(Date(item.endTime))} (${durationSec}s)",
                                fontSize = 10.sp,
                                color = DarkOnSurface
                            )
                            Text(
                                text = "Batería: ${item.finalBatteryPct}% | Temp: ${item.maxTempCelsius}°C",
                                fontSize = 10.sp,
                                color = DarkOnSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventsTabContent(events: List<EventLogEntity>) {
    if (events.isEmpty()) {
        EmptyState(message = "Log de auditoría vacío en Room.\nLas acciones del operador y eventos se registrarán aquí automáticamente.")
    } else {
        val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(events, key = { it.eventId }) { event ->
                val severityColor = when (event.severity) {
                    "CRITICAL" -> CriticalRed
                    "WARNING" -> WarningAmber
                    else -> NeonCyan
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceVariant)
                        .border(1.dp, CardNormalBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(severityColor)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = event.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkOnBackground
                                )

                                Text(
                                    text = timeFormat.format(Date(event.timestamp)),
                                    fontSize = 10.sp,
                                    color = DarkOnSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = event.details,
                                fontSize = 11.sp,
                                color = DarkOnSurface,
                                lineHeight = 15.sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(DarkBackground)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${event.category} • ${event.severity}",
                                    fontSize = 9.sp,
                                    color = severityColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = StaleGray,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = DarkOnSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

