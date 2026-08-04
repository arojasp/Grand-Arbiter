package es.alejandro.grandarbiter.ui.comprehensiverules

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.ui.common.DocumentDetails
import es.alejandro.grandarbiter.ui.common.DocumentSection
import es.alejandro.grandarbiter.ui.common.DocumentSectionItem
import es.alejandro.grandarbiter.ui.common.DocumentSubSection
import es.alejandro.grandarbiter.ui.theme.GrandArbiterTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun ComprehensiveRulesScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    viewModel: ComprehensiveRulesViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    ComprehensiveRulesScreenContent(
        uiState = uiState,
        onBackClick = onBackClick,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ComprehensiveRulesScreenContent(
    uiState: CRUiState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedSectionId by remember { mutableStateOf<String?>(null) }
    var expandedSubSectionId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.home_cr_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.sections) { section ->
                    val isSectionExpanded = expandedSectionId == section.id

                    DocumentSectionItem(
                        section = section,
                        isExpanded = isSectionExpanded,
                        onExpandClick = {
                            expandedSectionId = if (isSectionExpanded) null else section.id
                        },
                        onSubSectionClick = { subSectionId ->
                            expandedSubSectionId =
                                if (expandedSubSectionId == subSectionId) null else subSectionId
                        },
                        expandedSubSectionId = expandedSubSectionId
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

        }
    }
}

private val sampleData = listOf(
    DocumentSection(
        id = "1",
        title = "1. General Philosophy",
        subSections = emptyList()
    ),
    DocumentSection(
        id = "2",
        title = "2. Game Play Errors",
        subSections = listOf(
            DocumentSubSection(id = "2.1", title = "2.1 Missed Trigger"),
            DocumentSubSection(id = "2.2", title = "2.2 Looking at Extra Cards"),
            DocumentSubSection(
                id = "2.5",
                title = "2.5 Game Rule Violation",
                details = DocumentDetails(
                    definition = "This infraction covers an error in a game that does not fall into another category. It specifically addresses situations where a player makes an illegal play or fails to follow instructions on a card or from the rules.",
                    philosophy = "While Game Rule Violations are often unintentional, they can provide a significant advantage if not caught. Judges should focus on restoring a legal game state without providing either player with undue benefit.",
                    defaultPenalty = "Warning",
                    timeLimit = "+0 mins",
                    upgradeDowngradePath = "If the error was discovered immediately, a full backup may be considered.\n\nIf the player has committed the same infraction twice previously in the tournament, upgrade to Game Loss."
                )
            ),
            DocumentSubSection(id = "2.6", title = "2.6 Failure to Maintain Game State")
        )
    ),
    DocumentSection(
        id = "3",
        title = "3. Tournament Errors",
        subSections = emptyList()
    ),
    DocumentSection(
        id = "4",
        title = "4. Unsporting Conduct",
        subSections = listOf(
            DocumentSubSection(id = "4.1", title = "4.1 Unsporting Conduct — Minor"),
            DocumentSubSection(id = "4.2", title = "4.2 Unsporting Conduct — Major"),
            DocumentSubSection(id = "4.3", title = "4.3 Improperly Determining a Winner"),
            DocumentSubSection(id = "4.4", title = "4.4 Bribery and Wagering"),
            DocumentSubSection(id = "4.5", title = "4.5 Aggressive Behavior")
        )
    )
)

@Preview
@Composable
private fun ComprehensiveRulesScreenPreview() {
    GrandArbiterTheme {
        ComprehensiveRulesScreenContent(
            uiState = CRUiState(isLoading = false, sections = sampleData),
            onBackClick = {}
        )
    }
}
