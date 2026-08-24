package com.aistudio.mj.wxyt.domain.jarvis

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MayaRuntimeState { IDLE, LISTENING, UNDERSTANDING, THINKING, EXECUTING, SPEAKING, ERROR }

class MayaStateMachine {
    private val _state = MutableStateFlow(MayaRuntimeState.IDLE)
    val state: StateFlow<MayaRuntimeState> = _state.asStateFlow()

    fun transition(next: MayaRuntimeState) { _state.value = next }
    fun reset() { _state.value = MayaRuntimeState.IDLE }
}
