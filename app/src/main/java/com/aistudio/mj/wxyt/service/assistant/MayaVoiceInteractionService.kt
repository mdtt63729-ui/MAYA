package com.aistudio.mj.wxyt.service.assistant

import android.service.voice.VoiceInteractionService
import android.util.Log

/** Android system-managed default-assistant entry point. */
class MayaVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        Log.d("MAYA_ASSISTANT", "VoiceInteractionService ready")
    }

    override fun onShutdown() {
        Log.d("MAYA_ASSISTANT", "VoiceInteractionService shutdown")
        super.onShutdown()
    }
}
