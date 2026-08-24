package com.aistudio.mj.wxyt.domain.brain

import android.content.Context
import com.aistudio.mj.wxyt.domain.ai.AIOrchestrator
import com.aistudio.mj.wxyt.domain.command.AndroidCommandExecutor
import com.aistudio.mj.wxyt.domain.command.VoiceCommandEngine
import com.aistudio.mj.wxyt.domain.security.ActionRiskEngine
import com.aistudio.mj.wxyt.domain.settings.MJSettings

/**
 * Permission-aware JARVIS execution loop:
 * Understand -> Plan -> Risk Gate -> Execute -> Verify -> Recover.
 */
class MayaAgentPlanner(
    context: Context,
    aiOrchestrator: AIOrchestrator
) {
    private val commandEngine = VoiceCommandEngine(aiOrchestrator)
    private val executor = AndroidCommandExecutor(context)
    private val cognitive = JarvisCognitiveCore(context)
    private val risk = ActionRiskEngine(cognitive)

    suspend fun execute(rawText: String, settings: MJSettings): String? {
        if (!settings.autoExecuteSafeActions) return null
        val plan = cognitive.buildExecutionPlan(rawText, settings)
        if (plan.steps.size < 2) return null

        if (settings.dryRunMode) {
            return "Dry run complete. Plan: ${plan.steps.joinToString(" → ")}" 
        }

        val results = mutableListOf<String>()
        for (step in plan.steps.take(settings.maxAgentSteps)) {
            val command = commandEngine.processCommand(step) ?: return "আমি এই ধাপটা বুঝতে পারিনি: $step"
            if (command.confidence < settings.actionConfidenceThreshold) {
                return "এই ধাপটি execute করার confidence যথেষ্ট নয়: $step"
            }
            val actionRisk = cognitive.riskFor(command.action.name)
            if (settings.simulationBeforeRiskyAction && actionRisk != ActionRisk.LOW) {
                return "আমি আগে এই risky action-এর simulation/confirmation চাই: ${command.action.name}"
            }
            if (risk.requiresConfirmation(command.action.name, settings)) {
                return "আমি এই কাজটা করার আগে confirmation চাই: ${command.action.name}"
            }
            if (risk.requiresBiometric(command.action.name, settings)) {
                return "এই critical action-এর জন্য biometric confirmation প্রয়োজন।"
            }

            var attempt = 0
            var result = executor.execute(command)
            while (!result.success && settings.failureRecovery && settings.selfCorrection && settings.failedActionRetry && attempt < settings.maximumRetryCount) {
                attempt++
                result = executor.execute(command)
            }
            if (!result.success) return result.userMessage
            results += result.userMessage
        }
        return results.joinToString(" ")
    }
}
