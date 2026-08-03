package es.alejandro.grandarbiter.ui.setup

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.data.local.entities.CREntity
import es.alejandro.grandarbiter.data.local.entities.GlossaryEntity
import es.alejandro.grandarbiter.data.local.entities.TOCEntity
import es.alejandro.grandarbiter.domain.connectivity.ConnectivityObserver
import es.alejandro.grandarbiter.domain.repository.CRRepository
import kotlinx.coroutines.launch

class SetUpViewModel(
    private val crRepository: CRRepository,
    private val connectivityObserver: ConnectivityObserver
) : ViewModel() {

    val steps = listOf(
        SetupStep(SetupStepType.CONNECTIVITY_CHECK, SetupStepStatus.PENDING),
        SetupStep(SetupStepType.CR_DOWNLOAD, SetupStepStatus.PENDING),
        SetupStep(SetupStepType.CR_STORE, SetupStepStatus.PENDING)
    )

    var uiState by mutableStateOf(SetUpUiState.Loading(steps = steps, currentStepType = steps.first().type, 0f))
        private set


    private var cachedRules: List<CREntity>? = null
    private var cachedTOC: List<TOCEntity>? = null
    private var cachedGlossary: List<GlossaryEntity>? = null

    fun downloadNow() {
        viewModelScope.launch {
            runSetup(steps)
        }
    }

    private suspend fun runSetup(initialSteps: List<SetupStep>) {
        val currentSteps = initialSteps.toMutableList()

        for (index in currentSteps.indices) {
            val step = currentSteps[index]
            
            // Set current step to IN_PROGRESS
            currentSteps[index] = step.copy(status = SetupStepStatus.IN_PROGRESS)
            updateLoadingState(currentSteps, step.type, calculateProgress(index, currentSteps.size))

            var shouldStop = false
            when (step.type) {
                SetupStepType.CONNECTIVITY_CHECK -> {
                    runCatching {
                        connectivityObserver.isConnected()
                    }.onSuccess {
                        currentSteps[index] = currentSteps[index].copy(status = SetupStepStatus.COMPLETED)
                        updateLoadingState(currentSteps, step.type, calculateProgress(index + 1, currentSteps.size))
                    }.onFailure {
                        currentSteps[index] = currentSteps[index].copy(status = SetupStepStatus.ERROR)
                        shouldStop = true
                    }
                }
                SetupStepType.CR_DOWNLOAD -> {
                    runCatching {
                        // Progress for CR_DOWNLOAD starts at 33.3%
                        // Rules (1/3 of this step)
                        cachedRules = crRepository.downloadCR()
                        updateLoadingState(currentSteps, step.type, 33.3f + (33.3f / 3f) * 1)
                        
                        // TOC (1/3 of this step)
                        cachedTOC = crRepository.downloadTOC()
                        updateLoadingState(currentSteps, step.type, 33.3f + (33.3f / 3f) * 2)

                        // Glossary (1/3 of this step)
                        cachedGlossary = crRepository.downloadGlossary()
                        updateLoadingState(currentSteps, step.type, 33.3f + (33.3f / 3f) * 3)
                    }.onSuccess {
                        currentSteps[index] = currentSteps[index].copy(status = SetupStepStatus.COMPLETED)
                        updateLoadingState(currentSteps, step.type, calculateProgress(index + 1, currentSteps.size))
                    }.onFailure {
                        currentSteps[index] = currentSteps[index].copy(status = SetupStepStatus.ERROR)
                        shouldStop = true
                    }

                }
                SetupStepType.CR_STORE -> {
                    runCatching {
                        // Progress for CR_STORE starts at 66.6%
                        // Rules (1/3 of this step)
                        cachedRules?.let { crRepository.storeRules(it) }
                        updateLoadingState(currentSteps, step.type, 66.6f + (33.3f / 3f) * 1)

                        // TOC (1/3 of this step)
                        cachedTOC?.let { crRepository.storeTOC(it) }
                        updateLoadingState(currentSteps, step.type, 66.6f + (33.3f / 3f) * 2)

                        // Glossary (1/3 of this step)
                        cachedGlossary?.let { crRepository.storeGlossary(it) }
                    }.onSuccess {
                        currentSteps[index] = currentSteps[index].copy(status = SetupStepStatus.COMPLETED)
                        updateLoadingState(currentSteps, step.type, 100f)
                    }.onFailure {
                        currentSteps[index] = currentSteps[index].copy(status = SetupStepStatus.ERROR)
                        shouldStop = true
                    }
                }
            }
            if (shouldStop) break
        }

    }

    private fun updateLoadingState(steps: List<SetupStep>, currentType: SetupStepType, progress: Float) {
        uiState = SetUpUiState.Loading(
            steps = steps.toList(),
            currentStepType = currentType,
            progress = progress
        )
    }

    private fun calculateProgress(completedCount: Int, totalCount: Int): Float {
        return (completedCount * 100f) / totalCount
    }

}

sealed class SetUpUiState {
    data class Loading(
        val steps: List<SetupStep>,
        val currentStepType: SetupStepType,
        val progress: Float
    ) : SetUpUiState()
}

data class SetupStep(
    val type: SetupStepType,
    val status: SetupStepStatus
)

enum class SetupStepType(@StringRes val titleRes: Int, @StringRes val subtitleRes: Int) {
    CONNECTIVITY_CHECK(titleRes = R.string.connectivity_check_title, subtitleRes= R.string.connectivity_check_subtitle),
    CR_DOWNLOAD(titleRes = R.string.cr_download_title, subtitleRes = R.string.cr_download_subtitle),
    CR_STORE(titleRes = R.string.cr_store_title, subtitleRes = R.string.cr_store_subtitle)
}

enum class SetupStepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    ERROR
}
