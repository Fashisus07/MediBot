package com.medibot.g1.data.model

data class ServicePayloadTelemetry(
    val compartmentTempCelsius: Float = 4.0f,
    val bioSampleStabilityIndexG: Float = 0.12f,
    val smartLockState: SmartLockState = SmartLockState.SEALED_LOCKED
)
