package es.alejandro.grandarbiter.ui.cardsearch

import android.util.Log
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.domain.model.cards.CardDetail
import es.alejandro.grandarbiter.domain.model.cards.CardFace
import es.alejandro.grandarbiter.domain.model.cards.CardRuling
import es.alejandro.grandarbiter.domain.model.cards.FormatLegality
import es.alejandro.grandarbiter.domain.model.cards.Legality
import org.koin.androidx.compose.koinViewModel

/*
 * Figma: "Oracle Search - Card Detail Refined Grid" (node 19:932).
 * Order: header (name, cost, type, stats) · oracle text · rulings · legality · card image (collapsed).
 */

private val LabelStyle = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.1.sp)
private val LegalContainer = Color(0x6614532D)   // Figma: rgba(20,83,45,0.4)
private val LegalContent = Color(0xFF86EFAC)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    onBack: () -> Unit,
    viewModel: CardDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.reader_back),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                },
                title = {
                    Text(
                        stringResource(R.string.cards_details),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.55).sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
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
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { HeaderBlock(card) }
        item { OracleBlock(card) }
        item { RulingsBlock(card.rulings) }
        if (!card.isToken) item { LegalityBlock(card.legalities) }
        val arts = artsOf(card)
        if (arts.isNotEmpty()) item { ArtBlock(arts) }
        item { Attribution(card.scryfallUrl) }
    }
}

// ---------------------------------------------------------------- header

@Composable
private fun HeaderBlock(card: CardDetail) {
    val colors = MaterialTheme.colorScheme
    Panel(colors.surfaceContainerHigh, RoundedCornerShape(12.dp), Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(stringResource(R.string.cards_label_name))
                Text(card.name, style = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
                    color = colors.onSurface)
            }
            if (card.manaCost != null) {
                Column(Modifier.padding(start = 12.dp), horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Label(stringResource(R.string.cards_label_mana))
                    ManaCostSymbols(card.manaCost)
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = colors.outlineVariant.copy(alpha = 0.3f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            card.typeLine?.let { LabeledValue(stringResource(R.string.cards_label_type), it, Modifier.weight(1f)) }
            statsFor(card)?.let { (label, value) -> LabeledValue(label, value, Modifier.weight(1f)) }
        }
        if (card.reserved || card.gameChanger) {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (card.reserved) Tag(stringResource(R.string.cards_reserved), colors.primaryContainer, colors.primary)
                if (card.gameChanger) Tag(stringResource(R.string.cards_game_changer), colors.primaryContainer, colors.primary)
            }
        }
    }
}

/** Single-faced cards only: multi-faced cards show their stats per face. */
@Composable
private fun statsFor(card: CardDetail): Pair<String, String>? = when {
    card.faces.isNotEmpty() -> null
    else -> stats(card.power, card.toughness, card.loyalty, card.defense)
        ?: if (card.handModifier != null && card.lifeModifier != null) {
            stringResource(R.string.cards_label_hand_life) to "${card.handModifier} / ${card.lifeModifier}"
        } else null
}

@Composable
private fun stats(power: String?, toughness: String?, loyalty: String?, defense: String?): Pair<String, String>? = when {
    power != null && toughness != null -> stringResource(R.string.cards_label_pt) to "$power / $toughness"
    loyalty != null -> stringResource(R.string.cards_label_loyalty) to loyalty
    defense != null -> stringResource(R.string.cards_label_defense) to defense
    else -> null
}

@Composable
private fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label(label)
        Text(value, style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
            color = MaterialTheme.colorScheme.onSurface)
    }
}

// ---------------------------------------------------------------- oracle text

private val OracleStyle = TextStyle(fontSize = 16.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)

@Composable
private fun OracleBlock(card: CardDetail) {
    val colors = MaterialTheme.colorScheme
    Section(stringResource(R.string.cards_oracle_text)) {
        Panel(colors.surfaceContainer, RoundedCornerShape(12.dp), Modifier.padding(20.dp)) {
            if (card.faces.isEmpty()) {
                if (card.oracleText.isNullOrBlank()) {
                    Text(stringResource(R.string.cards_no_text), style = OracleStyle, color = colors.onSurfaceVariant)
                } else {
                    OracleText(card.oracleText, OracleStyle, colors.onSurface)
                }
            } else {
                // Not in the Figma: transform, MDFC, split and adventure cards, one face after another.
                card.faces.forEachIndexed { i, face ->
                    if (i > 0) HorizontalDivider(Modifier.padding(vertical = 16.dp), color = colors.outlineVariant)
                    FaceText(face)
                }
            }
        }
    }
}

@Composable
private fun FaceText(face: CardFace) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(face.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface, modifier = Modifier.weight(1f))
            face.manaCost?.let { ManaCostSymbols(it, Modifier.padding(start = 8.dp), size = 18.dp) }
        }
        face.typeLine?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant) }
        face.oracleText?.takeIf { it.isNotBlank() }?.let { OracleText(it, OracleStyle, colors.onSurface) }
        stats(face.power, face.toughness, face.loyalty, face.defense)?.let { (label, value) ->
            Text("$label: $value", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
        }
    }
}

// ---------------------------------------------------------------- rulings

@Composable
private fun RulingsBlock(rulings: List<CardRuling>) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Label(stringResource(R.string.cards_official_rulings), Modifier.weight(1f))
            if (rulings.isNotEmpty()) {
                Text(stringResource(R.string.cards_rulings_total, rulings.size),
                    style = LabelStyle.copy(letterSpacing = 0.5.sp), color = colors.primary)
            }
        }
        if (rulings.isEmpty()) {
            Text(stringResource(R.string.cards_no_rulings), style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
        rulings.forEach { RulingCard(it) }
    }
}

@Composable
private fun RulingCard(ruling: CardRuling) {
    val colors = MaterialTheme.colorScheme
    val official = ruling.source == "wotc"
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(colors.surfaceContainerLow)
            .outlined(shape, colors.outlineVariant)
    ) {
        // Gold accent for official (Wizards) rulings; Scryfall's own notes get a neutral one.
        Box(Modifier.width(4.dp).fillMaxHeight().background(if (official) colors.primary else colors.outlineVariant))
        Column(Modifier.padding(start = 13.dp, end = 16.dp, top = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag(ruling.date, colors.primaryContainer, colors.primary)
                if (!official) Tag(stringResource(R.string.cards_ruling_scryfall))
                Spacer(Modifier.weight(1f))
                if (official) {
                    Icon(painterResource(R.drawable.ic_gavel), null, tint = colors.onSurfaceVariant, modifier = Modifier.size(12.dp))
                }
            }
            OracleText(ruling.text, TextStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp), colors.onSurface)
        }
    }
}

// ---------------------------------------------------------------- legality

@Composable
private fun LegalityBlock(legalities: List<FormatLegality>) {
    Section(stringResource(R.string.cards_legality)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            legalities.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { LegalityItem(it, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LegalityItem(item: FormatLegality, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val (container, content, label) = when (item.legality) {
        Legality.LEGAL -> Triple(LegalContainer, LegalContent, R.string.legality_legal)
        Legality.RESTRICTED -> Triple(colors.primaryContainer, colors.primary, R.string.legality_restricted)
        Legality.BANNED -> Triple(colors.errorContainer, colors.error, R.string.legality_banned)
        Legality.NOT_LEGAL -> Triple(colors.errorContainer, colors.error, R.string.legality_not_legal)
    }
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .height(38.dp)
            // Banned stays at full strength: it matters more than "not legal" to a judge.
            .alpha(if (item.legality == Legality.NOT_LEGAL) 0.6f else 1f)
            .background(colors.surfaceContainerHighest.copy(alpha = 0.3f), shape)
            .outlined(shape, colors.outlineVariant)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(CardText.formatName(item.format),
            style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
            color = colors.onSurface, maxLines = 1, modifier = Modifier.weight(1f))
        Tag(stringResource(label), container, content)
    }
}

// ---------------------------------------------------------------- art (online only, collapsed)

/** Scryfall art crops of modern frames are 626×457. */
private const val DEFAULT_ART_RATIO = 626f / 457f

/** One art crop with its credit. [title] is the face name on multi-faced cards. */
private data class Art(val url: String, val artist: String?, val title: String?)

/** Transform/MDFC cards: one art per face. Others (split, adventure too): the card's art. */
private fun artsOf(card: CardDetail): List<Art> {
    val faces = card.faces.mapNotNull { f -> f.artCropUrl?.let { Art(it, f.artist, f.name) } }
    return faces.ifEmpty { listOfNotNull(card.artCropUrl?.let { Art(it, card.artist, null) }) }
}

@Composable
private fun ArtBlock(arts: List<Art>) {
    val colors = MaterialTheme.colorScheme
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Section(stringResource(R.string.cards_card_image)) {
        Panel(colors.surfaceContainerHigh, RoundedCornerShape(12.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.Image, null, tint = colors.primary, modifier = Modifier.size(18.dp))
                Text(stringResource(if (expanded) R.string.cards_hide_image else R.string.cards_show_image),
                    style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
                    color = colors.onSurface, modifier = Modifier.weight(1f))
                Icon(painterResource(R.drawable.ic_expand_more), null, tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).rotate(rotation))
            }
            // Only requested when opened: the image is the one part that needs a connection.
            AnimatedVisibility(expanded) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    arts.forEach { ArtView(it, showTitle = arts.size > 1) }
                }
            }
        }
    }
}

/** Scryfall's art crop + "Illustrated by …" (required next to an art crop by Scryfall's guidelines). */
@Composable
private fun ArtView(art: Art, showTitle: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showTitle && art.title != null) {
            Text(art.title, style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
        }
        // Always a bounded size: the art sits in a LazyColumn (unbounded height). The box starts at
        // a typical art-crop ratio and adopts the real one once the image arrives.
        var ratio by remember(art.url) { mutableFloatStateOf(DEFAULT_ART_RATIO) }
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(art.url).crossfade(true).build(),
            contentDescription = art.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(8.dp)),
            onSuccess = { state ->
                val size = state.painter.intrinsicSize
                if (size.width > 0f && size.height > 0f) ratio = size.width / size.height
            },
            onError = { state -> Log.w("CardArt", "Art failed: ${art.url}", state.result.throwable) },
            loading = {
                Box(Modifier.fillMaxSize().background(colors.surfaceContainer), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            },
            error = {
                Box(Modifier.fillMaxSize().background(colors.surfaceContainer).padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.cards_image_unavailable), style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            },
        )
        Text(
            if (art.artist != null) stringResource(R.string.cards_art_credit, art.artist) else stringResource(R.string.cards_art_copyright),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------- attribution

/** Not in the Figma: Scryfall's terms ask for credit; card text and images belong to Wizards. */
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
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = LabelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** A section heading (11sp, uppercase) above its content. */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(title, Modifier.padding(start = 4.dp))
        content()
    }
}

/** Bordered surface: the Figma blocks (1dp #4D4637 border). */
@Composable
private fun Panel(color: Color, shape: Shape, contentModifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color)
            .outlined(shape, MaterialTheme.colorScheme.outlineVariant)
            .then(contentModifier)
    ) { content() }
}
