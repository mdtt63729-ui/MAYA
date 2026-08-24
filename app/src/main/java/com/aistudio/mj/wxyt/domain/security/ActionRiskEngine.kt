package com.aistudio.mj.wxyt.domain.security

import com.aistudio.mj.wxyt.domain.brain.ActionRisk
import com.aistudio.mj.wxyt.domain.brain.JarvisCognitiveCore
import com.aistudio.mj.wxyt.domain.settings.MJSettings

/** Central risk gate: no agent can bypass this policy layer. */
class ActionRiskEngine(private val core: JarvisCognitiveCore) {
    fun requiresConfirmation(action: String, settings: MJSettings): Boolean {
        if (!settings.riskBasedConfirmation) return settings.requireConfirmation
        return when (core.riskFor(action)) {
            ActionRisk.LOW -> false
            ActionRisk.MEDIUM -> settings.requireConfirmation
            ActionRisk.HIGH, ActionRisk.CRITICAL -> true
        }
    }

    fun requiresBiometric(action: String, settings: MJSettings): Boolean {
        if (!settings.highRiskBiometricConfirmation) return false
        return core.riskFor(action) == ActionRisk.CRITICAL && settings.biometricActionConfirmation
    }
}
