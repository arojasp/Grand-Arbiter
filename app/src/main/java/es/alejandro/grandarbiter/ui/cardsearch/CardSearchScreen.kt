package es.alejandro.grandarbiter.ui.cardsearch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.domain.model.cards.CardSummary
import es.alejandro.grandarbiter.ui.reader.ReaderSearchField
import org.koin.androidx.compose.koinViewModel
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardSearchScreen(
    onOpenCard: (String) -> Unit,
    viewModel: CardSearchViewModel = koinViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val inText by viewModel.inText.collectAsState()
    val dataState by viewModel.dataState.collectAsState()
    val results by viewModel.results.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nav_card_search),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            ReaderSearchField(
                query = query,
                onQueryChange = viewModel::onQueryChange,
                placeholder = stringResource(if (inText) R.string.cards_search_text_placeholder else R.string.cards_search_placeholder),
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = inText,
                    onClick = { viewModel.onInTextChange(!inText) },
                    label = { Text(stringResource(R.string.cards_search_in_text)) },
                )
                Spacer(Modifier.weight(1f))
                (dataState as? CardDataState.Ready)?.let { DataLine(it) }
            }
            when {
                dataState is CardDataState.Checking -> Unit
                dataState is CardDataState.Preparing -> Centered {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator()
                        Text(
                            stringResource(R.string.cards_preparing),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                query.isBlank() -> Unit
                results.isEmpty() -> Centered {
                    Text(stringResource(R.string.cards_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Spacer(Modifier.height(4.dp)) }
                    items(results, key = { it.oracleId }) { card -> CardRow(card) { onOpenCard(card.oracleId) } }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun DataLine(ready: CardDataState.Ready) {
    val count = NumberFormat.getIntegerInstance().format(ready.info.cardCount)
    val date = CardText.date(ready.info.oracleUpdatedAt)?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    Column(horizontalAlignment = Alignment.End) {
        Text(stringResource(R.string.cards_count, count), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (date != null) {
            Text(stringResource(R.string.cards_data_from, date), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CardRow(card: CardSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (card.isToken) SmallBadge(stringResource(R.string.cards_token))
                Spacer(Modifier.weight(1f))
                card.manaCost?.let { ManaCost(it) }
            }
            card.typeLine?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Mana symbols as text ({2}{U}) for now; drawn symbols are a later step. */
@Composable
internal fun ManaCost(cost: String, modifier: Modifier = Modifier) {
    Text(cost, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = modifier)
}

@Composable
internal fun SmallBadge(text: String, container: Color = MaterialTheme.colorScheme.secondaryContainer,
                        content: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Surface(shape = RoundedCornerShape(6.dp), color = container) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = content,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
internal fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(bottom = 48.dp), contentAlignment = Alignment.Center) { content() }
}
