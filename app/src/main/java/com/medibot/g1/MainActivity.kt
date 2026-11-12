package com.medibot.g1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medibot.g1.data.model.RobotCommand
import com.medibot.g1.ui.components.SimulatorControlBottomSheet
import com.medibot.g1.ui.screens.DashboardScreen
import com.medibot.g1.ui.screens.HistoryScreen
import com.medibot.g1.ui.screens.MissionsScreen
import com.medibot.g1.ui.screens.RobotSelectionScreen
import com.medibot.g1.ui.theme.*
import com.medibot.g1.ui.viewmodel.SupervisionViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Robot : Screen("robot", "Robot", Icons.Default.SmartToy)
    data object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    data object Missions : Screen("missions", "Misiones", Icons.Default.ListAlt)
    data object History : Screen("history", "Historial", Icons.Default.History)
}

class MainActivity : ComponentActivity() {

    private val viewModel: SupervisionViewModel by viewModels {
        SupervisionViewModel.provideFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MediBotTheme {
                val uiState by viewModel.uiState.collectAsState()
                val missionsHistory by viewModel.missionsHistory.collectAsState()
                val eventLogs by viewModel.eventLogs.collectAsState()

                var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
                var showSimulatorBottomSheet by remember { mutableStateOf(false) }

                val screens = listOf(
                    Screen.Robot,
                    Screen.Dashboard,
                    Screen.Missions,
                    Screen.History
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = DarkBackground,
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { showSimulatorBottomSheet = true },
                            containerColor = ElectricViolet,
                            contentColor = DarkBackground,
                            elevation = FloatingActionButtonDefaults.elevation(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "Modo Ingeniero / Simulador"
                            )
                        }
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = DarkSurface,
                            contentColor = NeonCyan
                        ) {
                            screens.forEach { screen ->
                                val isSelected = currentScreen.route == screen.route
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { currentScreen = screen },
                                    icon = {
                                        Icon(
                                            imageVector = screen.icon,
                                            contentDescription = screen.title
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = screen.title,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = DarkBackground,
                                        selectedTextColor = NeonCyan,
                                        indicatorColor = NeonCyan,
                                        unselectedIconColor = DarkOnSurface,
                                        unselectedTextColor = DarkOnSurface
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    val modifier = Modifier.padding(innerPadding)

                    when (currentScreen) {
                        is Screen.Robot -> {
                            RobotSelectionScreen(
                                telemetry = uiState.telemetry,
                                onNavigateToDashboard = { currentScreen = Screen.Dashboard },
                                modifier = modifier
                            )
                        }
                        is Screen.Dashboard -> {
                            DashboardScreen(
                                uiState = uiState,
                                onSendCommand = viewModel::sendCommand,
                                onAcknowledgeAlert = viewModel::acknowledgeAlert,
                                onResetCommandFeedback = viewModel::resetCommandFeedback,
                                modifier = modifier
                            )
                        }
                        is Screen.Missions -> {
                            MissionsScreen(
                                telemetry = uiState.telemetry,
                                onStartMission = { mission ->
                                    viewModel.sendCommand(RobotCommand.StartMission(mission))
                                    currentScreen = Screen.Dashboard
                                },
                                modifier = modifier
                            )
                        }
                        is Screen.History -> {
                            HistoryScreen(
                                missionsHistory = missionsHistory,
                                eventLogs = eventLogs,
                                onInsertSeedData = viewModel::insertSeedData,
                                onClearHistory = viewModel::clearHistory,
                                modifier = modifier
                            )
                        }
                    }

                    if (showSimulatorBottomSheet) {
                        SimulatorControlBottomSheet(
                            connectionStatus = uiState.telemetry.connectionStatus,
                            forceRejectNext = uiState.forceRejectNext,
                            onInjectScenario = viewModel::injectScenario,
                            onToggleForceReject = viewModel::toggleForceReject,
                            onRestoreConnection = viewModel::restoreConnection,
                            onDismissRequest = { showSimulatorBottomSheet = false }
                        )
                    }
                }
            }
        }
    }
}

