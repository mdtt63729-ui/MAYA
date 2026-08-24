package com.aistudio.mj.wxyt.domain.command

enum class ExecutionStatus {
    COMPLETED, FAILED, BLOCKED, APP_NOT_INSTALLED, AMBIGUOUS
}

data class ExecutionResult(
    val commandId: String,
    val success: Boolean,
    val action: CommandAction,
    val target: String?,
    val status: ExecutionStatus,
    val userMessage: String,
    val errorCode: String? = null
)
