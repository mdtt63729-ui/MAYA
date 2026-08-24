package com.aistudio.mj.wxyt.service.assistant

import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class MayaVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: android.os.Bundle?): VoiceInteractionSession {
        return MayaVoiceInteractionSession(this)
    }
}
