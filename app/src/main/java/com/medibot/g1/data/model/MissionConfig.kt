package com.medibot.g1.data.model

import java.util.UUID

data class MissionConfig(
    val missionId: String = UUID.randomUUID().toString().take(8).uppercase(),
    val name: String,
    val description: String,
    val priority: MissionPriority = MissionPriority.STANDARD,
    val origin: HospitalNode,
    val destination: HospitalNode,
    val route: List<HospitalNode>,
    val requiredTempRange: ClosedFloatingPointRange<Float> = 2.0f..6.0f,
    val maxVibrationG: Float = 0.30f,
    val requiresNfcHandoff: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

enum class MissionPriority(val displayName: String) {
    URGENT("Urgente"),
    STANDARD("Estándar"),
    ROUTINE("Rutinaria")
}
