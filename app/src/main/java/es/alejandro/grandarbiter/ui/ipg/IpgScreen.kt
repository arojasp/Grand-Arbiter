package es.alejandro.grandarbiter.ui.ipg

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.ui.common.DocumentDetails
import es.alejandro.grandarbiter.ui.common.DocumentSection
import es.alejandro.grandarbiter.ui.common.DocumentSectionItem
import es.alejandro.grandarbiter.ui.common.DocumentSubSection
import es.alejandro.grandarbiter.ui.theme.GrandArbiterTheme

@Composable
fun IpgScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {}
) {
    IpgScreenContent(
        modifier = modifier,
        onBackClick = onBackClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IpgScreenContent(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    initialExpandedSectionId: String? = null,
    initialExpandedSubSectionId: String? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    val sections = remember { sampleIpgData }
    var expandedSectionId by remember { mutableStateOf(initialExpandedSectionId) }
    var expandedSubSectionId by remember { mutableStateOf(initialExpandedSubSectionId) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.ipg_title),
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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            IpgSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                modifier = Modifier.padding(vertical = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(sections) { section ->
                    DocumentSectionItem(
                        section = section,
                        isExpanded = expandedSectionId == section.id,
                        onExpandClick = {
                            expandedSectionId = if (expandedSectionId == section.id) null else section.id
                        },
                        expandedSubSectionId = expandedSubSectionId,
                        onSubSectionClick = { subSectionId ->
                            expandedSubSectionId = if (expandedSubSectionId == subSectionId) null else subSectionId
                        }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun IpgSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = stringResource(R.string.ipg_search_placeholder),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                fontSize = 14.sp
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp)),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        singleLine = true
    )
}

private val sampleIpgData = listOf(
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
private fun IpgScreenPreview() {
    GrandArbiterTheme {
        IpgScreen()
    }
}

@Preview
@Composable
private fun IpgScreenExpandedPreview() {
    GrandArbiterTheme {
        IpgScreenContent(
            initialExpandedSectionId = "2",
            initialExpandedSubSectionId = "2.5"
        )
    }
}
