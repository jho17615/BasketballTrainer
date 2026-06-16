package com.example.basketballtrainer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.basketballtrainer.model.TrainingMode
import com.example.basketballtrainer.model.TrainingUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 훈련 세션의 상태 단일 소스(SSOT).
 *
 * - UI 는 [state] 를 collect 해서 그린다.
 * - 다음 단계(STEP 2)에서 추가될 ImageAnalysis.Analyzer 는
 *   on*Detected() 콜백들로 이 ViewModel 에 결과를 통보한다.
 * - 현재는 elapsedSec(세션 타이머) 만 동작하고 카운터는 0 으로 고정 —
 *   실제 인식 로직이 붙기 전까지는 카운트가 올라가지 않는 게 정상이다.
 */
class TrainingViewModel : ViewModel() {

    private val _state = MutableStateFlow(TrainingUiState())
    val state: StateFlow<TrainingUiState> = _state.asStateFlow()

    private var sessionJob: Job? = null

    fun startSession(mode: TrainingMode) {
        sessionJob?.cancel()
        _state.value = TrainingUiState(mode = mode, fps = 0)
        sessionJob = viewModelScope.launch {
            var t = 0L
            while (true) {
                delay(1000)
                t++
                _state.update { it.copy(elapsedSec = t) }
            }
        }
    }

    fun stopSession() {
        sessionJob?.cancel()
        sessionJob = null
        _state.value = TrainingUiState()
    }

    // ---- 다음 단계(Analyzer) 가 호출할 훅들 ----
    fun reportFps(fps: Int) = _state.update { it.copy(fps = fps) }

    fun onDribbleDetected() = _state.update { it.copy(dribbleCount = it.dribbleCount + 1) }

    fun onShootAttempt() = _state.update { it.copy(shootAttempt = it.shootAttempt + 1) }

    fun onShootSuccess() = _state.update { it.copy(shootSuccess = it.shootSuccess + 1) }

    fun setTooHigh(value: Boolean) = _state.update { it.copy(tooHigh = value) }
}