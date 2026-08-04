package es.alejandro.grandarbiter.ui.comprehensiverules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.domain.repository.CRRepository
import es.alejandro.grandarbiter.ui.common.DocumentSection
import es.alejandro.grandarbiter.ui.common.DocumentSubSection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CRUiState(
    val sections: List<DocumentSection> = emptyList(),
    val isLoading: Boolean = true
)

class ComprehensiveRulesViewModel(
    private val crRepository: CRRepository
) : ViewModel() {

    val uiState: StateFlow<CRUiState> = combine(
        crRepository.getTOC(),
        crRepository.getAllRules()
    ) { toc, rules ->
        val sections = toc.map { tocItem ->
            DocumentSection(
                id = tocItem.number,
                title = "${tocItem.number}. ${tocItem.title}",
                subSections = tocItem.subsections.map { subItem ->
                    DocumentSubSection(
                        id = subItem.number,
                        title =  "${subItem.number}. ${subItem.title}",
                        rules = rules.filter { it.ruleNumber.startsWith("${subItem.number}.") || it.ruleNumber == subItem.number }
                    )
                }
            )
        }
        CRUiState(sections = sections, isLoading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CRUiState()
    )
}
