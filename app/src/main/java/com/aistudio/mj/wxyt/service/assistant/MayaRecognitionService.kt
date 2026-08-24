package com.aistudio.mj.wxyt.service.assistant

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Compatibility recognition-service endpoint required by Android's VoiceInteractionService
 * metadata on older Android releases. MAYA's primary conversational voice path uses its
 * dedicated Gemini voice pipeline; this endpoint intentionally fails closed rather than
 * pretending to provide a second recognizer.
 */
class MayaRecognitionService : RecognitionService() {
    override fun onStartListening(intent: Intent?, callback: Callback) {
        callback.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onStopListening(callback: Callback?) {
        // No recognition session is started by this compatibility endpoint.
    }

    override fun onCancel(callback: Callback?) {
        // No recognition session is started by this compatibility endpoint.
    }
}
