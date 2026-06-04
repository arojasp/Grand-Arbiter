package es.alejandro.grandarbiter.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.domain.repository.CRRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeViewModel(
    private val crRepository: CRRepository
) : ViewModel() {

    var elapsedTime by mutableLongStateOf(0L)
        private set

    var isRunning by mutableStateOf(false)
        private set

    private var timerJob: Job? = null

    fun toggleTimer() {
        if (isRunning) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    private fun startTimer() {
        isRunning = true
        timerJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis() - elapsedTime
            while (isRunning) {
                elapsedTime = System.currentTimeMillis() - startTime
                delay(10)
            }
        }
    }

    fun pauseTimer() {
        isRunning = false
        timerJob?.cancel()
    }

    fun resetTimer() {
        pauseTimer()
        elapsedTime = 0L
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
