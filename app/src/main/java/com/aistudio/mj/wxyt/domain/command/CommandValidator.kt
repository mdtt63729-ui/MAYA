package com.aistudio.mj.wxyt.domain.command

class CommandValidator {
    fun validate(command: VoiceCommand): VoiceCommand {
        if (command.action == CommandAction.UNKNOWN) {
            return command.copy(
                requiresClarification = true,
                clarificationPrompt = "আমি command-টা বুঝতে পারিনি। আবার বলবে?"
            )
        }

        if (command.confidence < 0.60f) {
            return command.copy(
                requiresClarification = true,
                clarificationPrompt = "আমি command-টা বুঝতে পারিনি। আবার বলবে?"
            )
        }

        // Validate required parameters based on action
        var missingInfo = false
        var clarification = "কী করতে হবে?"

        when (command.action) {
            CommandAction.OPEN_APP -> {
                if (command.target.isNullOrBlank()) {
                    missingInfo = true
                    clarification = "কোন অ্যাপটা খুলব?"
                }
            }
            CommandAction.SEARCH_WEB -> {
                if (command.query.isNullOrBlank()) {
                    missingInfo = true
                    clarification = "কী search করব?"
                }
            }
            CommandAction.SEARCH_APP -> {
                if (command.target.isNullOrBlank()) {
                    missingInfo = true
                    clarification = "কোন অ্যাপে search করব?"
                } else if (command.query.isNullOrBlank()) {
                    missingInfo = true
                    clarification = "কী search করব?"
                }
            }
            CommandAction.SEND_MESSAGE -> {
                if (command.target.isNullOrBlank()) { // Recipient
                    missingInfo = true
                    clarification = "কাকে message পাঠাব?"
                }
            }
            CommandAction.CALL_CONTACT -> {
                if (command.target.isNullOrBlank()) {
                    missingInfo = true
                    clarification = "কাকে কল করব?"
                }
            }
            else -> {}
        }

        if (missingInfo) {
            return command.copy(
                requiresClarification = true,
                clarificationPrompt = clarification,
                confidence = 0.5f // downgrade confidence because we need more info
            )
        }

        if (command.confidence in 0.60f..0.84f) {
            val confirm = when (command.action) {
                CommandAction.OPEN_APP -> "তুমি কি ${command.target} খুলতে বলছ?"
                else -> "তুমি কি এই command টা রান করতে বলছ?"
            }
            return command.copy(
                requiresClarification = true,
                clarificationPrompt = confirm
            )
        }

        return command
    }
}
