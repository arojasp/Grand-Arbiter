package es.alejandro.grandarbiter.ui.tools.quickrefs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.data.rules.bundle.DocumentId
import es.alejandro.grandarbiter.domain.model.quickrefs.QuickReference
import es.alejandro.grandarbiter.ui.components.Panel
import es.alejandro.grandarbiter.ui.components.outlined
import es.alejandro.grandarbiter.ui.theme.GoldDeep
import es.alejandro.grandarbiter.ui.theme.OnGoldDeep
import org.koin.androidx.compose.koinViewModel
import es.alejandro.grandarbiter.ui.theme.softened

/*
 * Figma: Quick References (19:1045) and its "new reference" menu (19:1214).
 * The FAB opens the document picker; a new reference only holds rules of one document.
 * Built-in references can be deleted; the overflow menu brings them back.
 */

private val ScreenTitle = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.7).sp, fontWeight = FontWeight.Bold)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickReferencesScreen(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onCreate: (DocumentId) -> Unit,
    viewModel: QuickReferencesViewModel = koinViewModel(),
) {
    val references by viewModel.references.collectAsState()
    val canRestore by viewModel.canRestorePresets.collectAsState()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    BackHandler(enabled = menuOpen) { menuOpen = false }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = colors.background,
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.reader_back), tint = colors.primary)
                        }
                    },
                    title = { Text(stringResource(R.string.tools_quick_references), style = ScreenTitle, color = colors.primary) },
                    actions = {
                        if (canRestore) {
                            IconButton(onClick = { overflowOpen = true }) {
                                Icon(Icons.Default.MoreVert, stringResource(R.string.qr_more), tint = colors.primary)
                            }
                            DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.qr_restore)) },
                                    onClick = { overflowOpen = false; confirmRestore = true },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
                )
            },
        ) { innerPadding ->
            val list = references
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState(canRestore, viewModel::restorePresets, Modifier.padding(innerPadding))
                else -> LazyColumn(
                    modifier = Modifier.padding(innerPadding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 112.dp),
                ) {
                    item {
                        Panel {
                            list.forEachIndexed { i, ref ->
                                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = colors.outlineVariant.softened(0.4f))
                                ReferenceRow(ref) { onOpen(ref.id) }
                            }
                        }
                    }
                }
            }
        }

        // Scrim + document picker (Figma 19:1214).
        AnimatedVisibility(menuOpen, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { menuOpen = false }
            )
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedVisibility(menuOpen, enter = fadeIn() + slideInVertically { it / 4 }, exit = fadeOut() + slideOutVertically { it / 4 }) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MENU_DOCUMENTS.forEach { document ->
                        DocumentOption(document) { menuOpen = false; onCreate(document) }
                    }
                }
            }
            FloatingActionButton(
                onClick = { menuOpen = !menuOpen },
                containerColor = GoldDeep,
                contentColor = OnGoldDeep,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(if (menuOpen) Icons.Default.Close else Icons.Default.Add,
                    stringResource(if (menuOpen) R.string.qr_close_menu else R.string.qr_new))
            }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(stringResource(R.string.qr_restore)) },
            text = { Text(stringResource(R.string.qr_restore_message)) },
            confirmButton = {
                TextButton(onClick = { confirmRestore = false; viewModel.restorePresets() }) { Text(stringResource(R.string.qr_restore_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text(stringResource(R.string.qr_cancel)) } },
        )
    }
}

/** Figma order: CR, IPG, MTR, JAR. */
private val MENU_DOCUMENTS = listOf(DocumentId.CR, DocumentId.IPG, DocumentId.MTR, DocumentId.JAR)

@Composable
private fun ReferenceRow(ref: QuickReference, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(ref.title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            val subtitle = ref.firstRuleNumber?.let { "${ref.documentId.shortLabel()} · $it" }
                ?: "${ref.documentId.shortLabel()} · ${stringResource(R.string.qr_no_rules)}"
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.onSurfaceVariant)
    }
}

@Composable
private fun DocumentOption(document: DocumentId, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Surface(onClick = onClick, shape = shape, color = colors.surfaceContainerHigh, modifier = Modifier.outlined(shape, colors.outlineVariant)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalAlignment = Alignment.End) {
            Text(document.shortLabel(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.onSurface)
            Text(stringResource(document.titleRes()), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyState(canRestore: Boolean, onRestore: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.qr_empty), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (canRestore) TextButton(onClick = onRestore) { Text(stringResource(R.string.qr_restore)) }
    }
}

fun DocumentId.shortLabel(): String = key.uppercase()

fun DocumentId.titleRes(): Int = when (this) {
    DocumentId.CR -> R.string.home_cr_title
    DocumentId.IPG -> R.string.home_ipg_title
    DocumentId.MTR -> R.string.home_mtr_title
    DocumentId.JAR -> R.string.home_jar_title
}
