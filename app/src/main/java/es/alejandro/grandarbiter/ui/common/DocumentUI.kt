package es.alejandro.grandarbiter.ui.common

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.data.local.entities.CREntity
import es.alejandro.grandarbiter.data.local.entities.NavigationEntity
import es.alejandro.grandarbiter.ui.theme.GrandArbiterTheme

data class DocumentSection(
    val id: String,
    val title: String,
    val subSections: List<DocumentSubSection> = emptyList()
)

data class DocumentSubSection(
    val id: String,
    val title: String,
    val details: DocumentDetails? = null,
    val rules: List<CREntity> = emptyList()
)

data class DocumentDetails(
    val definition: String,
    val philosophy: String,
    val defaultPenalty: String,
    val timeLimit: String,
    val upgradeDowngradePath: String
)

@Composable
fun DocumentSectionItem(
    section: DocumentSection,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    expandedSubSectionId: String?,
    onSubSectionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandClick() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Icon(
                    painter = painterResource(
                        if (isExpanded) R.drawable.ic_expand_more else R.drawable.ic_chevron_right
                    ),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (isExpanded && section.subSections.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    section.subSections.forEach { subSection ->
                        DocumentSubSectionItem(
                            subSection = subSection,
                            isExpanded = expandedSubSectionId == subSection.id,
                            onClick = { onSubSectionClick(subSection.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentSubSectionItem(
    subSection: DocumentSubSection,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clickable { onClick() }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isExpanded) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Text(
                text = subSection.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isExpanded) FontWeight.Bold else FontWeight.Normal,
                    color = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            )
        }

        if (isExpanded) {
            if (subSection.details != null) {
                DocumentInfractionDetails(
                    details = subSection.details,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (subSection.rules.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(top = 8.dp, start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    subSection.rules.forEach { rule ->
                        RuleDetailItem(rule = rule)
                    }
                }
            }
        }
    }
}

@Composable
fun RuleDetailItem(
    rule: CREntity,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = rule.ruleNumber,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
            Text(
                text = rule.ruleText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                lineHeight = 20.sp
            )
        }
        if (rule.examples.isNotEmpty()) {
            rule.examples.forEach { example ->
                Text(
                    text = example,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    ),
                    modifier = Modifier.padding(top = 4.dp, start = 16.dp),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun DocumentInfractionDetails(
    details: DocumentDetails,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DetailCard(
            label = stringResource(R.string.ipg_definition_label),
            content = details.definition
        )
        DetailCard(
            label = stringResource(R.string.ipg_philosophy_label),
            content = details.philosophy
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_warning),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.ipg_default_penalty_label),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                    Surface(
                        color = Color(0xFF8B0000),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = details.defaultPenalty,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = stringResource(R.string.ipg_time_limit_label),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = details.timeLimit,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }

        DetailCard(
            label = stringResource(R.string.ipg_upgrade_downgrade_path_label),
            content = details.upgradeDowngradePath
        )
    }
}

@Composable
private fun DetailCard(
    label: String,
    content: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer

        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun DocumentSectionCollapsedPreview() {
    GrandArbiterTheme {
            DocumentSectionItem(
                section = sampleDocumentSection,
                isExpanded = false,
                onExpandClick = {},
                expandedSubSectionId = null,
                onSubSectionClick = {}
            )
    }
}

@Preview(showSystemUi = true)
@Composable
private fun DocumentSectionExpandedPreview() {
    GrandArbiterTheme {

            DocumentSectionItem(
                section = sampleDocumentSection,
                isExpanded = true,
                onExpandClick = {},
                expandedSubSectionId = "1.1",
                onSubSectionClick = {}
            )

    }
}

@Preview(showSystemUi = true)
@Composable
private fun DocumentSubSectionInfractionPreview() {
    GrandArbiterTheme {
            DocumentSubSectionItem(
                subSection = sampleDocumentSection.subSections[0],
                isExpanded = true,
                onClick = {}
            )

    }
}

@Preview(showSystemUi = true)
@Composable
private fun DocumentSubSectionRulesPreview() {
    GrandArbiterTheme {
            DocumentSubSectionItem(
                subSection = sampleDocumentSection.subSections[1],
                isExpanded = true,
                onClick = {}
            )

    }
}

private val sampleDocumentSection = DocumentSection(
    id = "1",
    title = "1. Game Play Errors",
    subSections = listOf(
        DocumentSubSection(
            id = "1.1",
            title = "1.1 Missed Trigger",
            details = DocumentDetails(
                definition = "A triggered ability triggers, but the player controlling the ability is unaware of its existence and/or forgets to announce its effect or make the appropriate choices.",
                philosophy = "Triggers are not mandatory in the sense that a player is not required to remind their opponent of a trigger they control.",
                defaultPenalty = "Warning",
                timeLimit = "+0 mins",
                upgradeDowngradePath = "If the triggered ability is generally considered detrimental for the player controlling the ability, the penalty is a Warning."
            )
        ),
        DocumentSubSection(
            id = "1.2",
            title = "1.2 Looking at Extra Cards",
            rules = listOf(
                CREntity(
                    ruleNumber = "1.2a",
                    ruleText = "A player is considered to have looked at extra cards if they see a card that they were not supposed to see.",
                    fragment = "",
                    examples = listOf("Example 1: A player accidentally flips the top card of their library."),
                    navigation = NavigationEntity()
                ),
                CREntity(
                    ruleNumber = "1.2b",
                    ruleText = "If a player looks at extra cards, the cards should be returned to where they were supposed to be.",
                    fragment = "",
                    examples = emptyList(),
                    navigation = NavigationEntity()
                )
            )
        )
    )
)
