package es.alejandro.grandarbiter.ui.comprehensiverules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.data.local.entities.CREntity
import es.alejandro.grandarbiter.domain.repository.CRRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CRUiState(
    val sections: List<RuleSection> = emptyList(),
    val isLoading: Boolean = true
)

data class RuleSection(
    val number: String,
    val title: String,
    val subsections: List<RuleSubSection>
)

data class RuleSubSection(
    val number: String,
    val title: String,
    val rules: List<CREntity>
)

class ComprehensiveRulesViewModel(
    private val crRepository: CRRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)

    val uiState: StateFlow<CRUiState> = combine(
        crRepository.getTOC(),
        crRepository.getAllRules()
    ) { toc, rules ->
        val sections = toc.map { tocItem ->
            RuleSection(
                number = tocItem.number,
                title = tocItem.title,
                subsections = tocItem.subsections.map { subItem ->
                    RuleSubSection(
                        number = subItem.number,
                        title = subItem.title,
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
