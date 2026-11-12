package com.medibot.g1.data.model

sealed class CommandExecutionState {
    data object Idle : CommandExecutionState()
    data class Pending(
        val commandName: String,
        val sentAt: Long = System.currentTimeMillis()
    ) : CommandExecutionState()
    data class Confirmed(
        val commandName: String,
        val message: String
    ) : CommandExecutionState()
    data class Rejected(
        val commandName: String,
        val reason: String
    ) : CommandExecutionState()
    data class NoResponse(
        val commandName: String,
        val timeoutMs: Long = 2500L
    ) : CommandExecutionState()
}
