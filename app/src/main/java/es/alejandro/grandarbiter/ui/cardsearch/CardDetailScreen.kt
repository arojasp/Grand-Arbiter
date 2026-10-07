package es.alejandro.grandarbiter.ui.cardsearch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.domain.model.cards.CardDetail
import es.alejandro.grandarbiter.domain.model.cards.CardFace
import es.alejandro.grandarbiter.domain.model.cards.CardRuling
import es.alejandro.grandarbiter.domain.model.cards.FormatLegality
import es.alejandro.grandarbiter.domain.model.cards.Legality
import es.alejandro.grandarbiter.ui.reader.ReaderTopBar
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun CardDetailScreen(
    onBack: () -> Unit,
    viewModel: CardDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val card = (state as? CardDetailState.Loaded)?.card

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ReaderTopBar(title = card?.name.orEmpty(), subtitle = card?.typeLine, onBack = onBack) },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (val s = state) {
                CardDetailState.Loading -> Centered { CircularProgressIndicator() }
                CardDetailState.NotFound -> Centered {
                    Text(stringResource(R.string.cards_not_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is CardDetailState.Loaded -> CardDetailContent(s.card)
            }
        }
    }
}

@Composable
private fun CardDetailContent(card: CardDetail) {
    val images = remember(card) { card.faces.mapNotNull { it.imageUrl }.ifEmpty { listOfNotNull(card.imageUrl) } }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (images.isNotEmpty()) item { CardImages(images, card.name) }

        item {
            SectionCard {
                if (card.faces.isEmpty()) {
                    FaceBlock(card.name, card.manaCost, card.typeLine, card.oracleText,
                        stats(card.power, card.toughness, card.loyalty, card.defense))
                } else {
                    card.faces.forEachIndexed { i, face ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        FaceBlock(face.name, face.manaCost, face.typeLine, face.oracleText, face.stats())
                    }
                }
                if (card.handModifier != null && card.lifeModifier != null) {
                    BodyText(stringResource(R.string.cards_hand_life, card.handModifier, card.lifeModifier))
                }
                if (card.reserved || card.gameChanger) {
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (card.reserved) SmallBadge(stringResource(R.string.cards_reserved))
                        if (card.gameChanger) SmallBadge(stringResource(R.string.cards_game_changer))
                    }
                }
            }
        }

        if (!card.isToken) item { Legalities(card.legalities) }
        item { Rulings(card.rulings) }
        item { Attribution(card.scryfallUrl) }
    }
}

// ---------------------------------------------------------------- image (online only)

@Composable
private fun CardImages(urls: List<String>, name: String) {
    if (urls.size == 1) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CardImage(urls.single(), name, Modifier.width(260.dp))
        }
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(urls) { CardImage(it, name, Modifier.width(240.dp)) }
        }
    }
}

/** Scryfall's CDN; Coil caches it. Offline (or no image): a placeholder, the text is all local. */
@Composable
private fun CardImage(url: String, name: String, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    SubcomposeAsyncImage(
        model = url,
        contentDescription = name,
        contentScale = ContentScale.Fit,
        modifier = modifier.aspectRatio(488f / 680f).clip(shape),
        loading = {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        },
        error = {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
                contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.cards_image_unavailable), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        },
    )
}

// ---------------------------------------------------------------- text

@Composable
private fun FaceBlock(name: String, manaCost: String?, typeLine: String?, text: String?, stats: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            manaCost?.let { ManaCost(it, Modifier.padding(start = 8.dp)) }
        }
        typeLine?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!text.isNullOrBlank()) {
            val annotated = remember(text) { CardText.oracle(text) }
            Text(annotated, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        stats?.let {
            Text(it, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun stats(power: String?, toughness: String?, loyalty: String?, defense: String?): String? =
    CardText.powerToughness(power, toughness)
        ?: loyalty?.let { stringResource(R.string.cards_loyalty, it) }
        ?: defense?.let { stringResource(R.string.cards_defense, it) }

@Composable
private fun CardFace.stats(): String? = stats(power, toughness, loyalty, defense)

// ---------------------------------------------------------------- legality

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legalities(legalities: List<FormatLegality>) {
    SectionCard(title = stringResource(R.string.cards_legality)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 2,
        ) {
            legalities.forEach { LegalityRow(it, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun LegalityRow(item: FormatLegality, modifier: Modifier) {
    val (container, content, label) = when (item.legality) {
        Legality.LEGAL -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, R.string.legality_legal)
        Legality.RESTRICTED -> Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, R.string.legality_restricted)
        Legality.BANNED -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, R.string.legality_banned)
        Legality.NOT_LEGAL -> Triple(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, R.string.legality_not_legal)
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SmallBadge(stringResource(label), container, content)
        Text(CardText.formatName(item.format), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

// ---------------------------------------------------------------- rulings

@Composable
private fun Rulings(rulings: List<CardRuling>) {
    SectionCard(title = stringResource(R.string.cards_rulings)) {
        if (rulings.isEmpty()) BodyText(stringResource(R.string.cards_no_rulings), muted = true)
        rulings.forEachIndexed { i, ruling ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(rulingDate(ruling.date), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    if (ruling.source != "wotc") SmallBadge(stringResource(R.string.cards_ruling_scryfall))
                }
                Text(CardText.oracle(ruling.text), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

private fun rulingDate(date: String): String =
    runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrDefault(date)

// ---------------------------------------------------------------- attribution

/** Scryfall's terms ask for credit; the card text and images belong to Wizards. */
@Composable
private fun Attribution(scryfallUrl: String) {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.cards_attribution), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        TextButton(onClick = { uriHandler.openUri(scryfallUrl) }) { Text(stringResource(R.string.cards_open_scryfall)) }
    }
}

// ---------------------------------------------------------------- building blocks

@Composable
private fun SectionCard(title: String? = null, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

@Composable
private fun BodyText(text: String, muted: Boolean = false) {
    Text(text, style = MaterialTheme.typography.bodyMedium,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 6.dp))
}
