package com.medibot.g1.data.model

enum class HospitalNode(val displayName: String, val floor: Int) {
    DOCK_BASE("PB - Base de Carga Laboratorio", 0),
    LAB_CENTRAL("PB - Laboratorio Bioquímico", 0),
    FIRE_DOOR_PB("PB - Puerta Cortafuego Principal", 0),
    ELEVATOR_BANK("Ascensor Hospitalario (PB <-> Piso 2)", -1),
    SAFE_POINT_P2("Piso 2 - Receso Seguro de Espera", 2),
    UTI_NURSING("Piso 2 - Enfermería Terapia Intensiva", 2)
}
