package com.aistudio.mj.wxyt.service.assistant

import android.content.Intent
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log
import com.aistudio.mj.wxyt.MainActivity

/**
 * Lightweight system-assistant session. Heavy UI remains in MainActivity while
 * the VoiceInteractionService itself stays lightweight.
 */
class MayaVoiceInteractionSession(service: VoiceInteractionSessionService) : VoiceInteractionSession(service) {
    override fun onShow(args: android.os.Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_ASSIST
            putExtra("assistant_entry", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        try {
            // Use the assistant-specific activity launch path instead of a plain
            // Context.startActivity so the system keeps the interaction associated
            // with the active VoiceInteractionSession.
            startAssistantActivity(intent)
        } catch (e: Exception) {
            Log.e("MAYA_ASSISTANT", "Unable to open assistant UI", e)
        }
    }
}
