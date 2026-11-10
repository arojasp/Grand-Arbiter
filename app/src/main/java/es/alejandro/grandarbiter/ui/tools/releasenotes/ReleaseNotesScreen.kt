package es.alejandro.grandarbiter.ui.tools.releasenotes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.data.releasenotes.ReleaseNoteSet
import es.alejandro.grandarbiter.data.releasenotes.ReleaseNotesRepository
import es.alejandro.grandarbiter.data.updates.SectionVersions
import es.alejandro.grandarbiter.domain.updates.SectionUpdates
import es.alejandro.grandarbiter.ui.components.ListDivider
import es.alejandro.grandarbiter.ui.components.Panel
import es.alejandro.grandarbiter.ui.components.Tag
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

class ReleaseNotesViewModel(
    private val repository: ReleaseNotesRepository,
    private val sectionVersions: SectionVersions,
) : ViewModel() {
    val sets: StateFlow<List<ReleaseNoteSet>> = repository.sets

    init {
        viewModelScope.launch { sectionVersions.markSeen(SectionUpdates.RELEASE_NOTES) }   // its «Updated» label goes
        // Shows the stored list at once, then picks up any set published since (silent if offline).
        viewModelScope.launch {
            repository.refresh()
            sectionVersions.markSeen(SectionUpdates.RELEASE_NOTES)   // a set that just appeared is on screen: seen too
        }
    }
}

/**
 * The official release notes of each set, newest first. Each opens in the in-app viewer: the
 * articles are Wizards', shown as published (always their current revision).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseNotesScreen(onBack: () -> Unit, onOpenSet: (ReleaseNoteSet) -> Unit, viewModel: ReleaseNotesViewModel = koinViewModel()) {
    val sets by viewModel.sets.collectAsState()
    val colors = MaterialTheme.colorScheme

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.reader_back), tint = colors.primary)
                    }
                },
                title = {
                    Text(stringResource(R.string.tools_release_notes),
                        style = MaterialTheme.typography.titleLarge, color = colors.primary)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(stringResource(R.string.release_notes_caption), style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            }
            item {
                Panel {
                    sets.forEachIndexed { i, set ->
                        if (i > 0) ListDivider()
                        SetRow(set, latest = i == 0) { onOpenSet(set) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetRow(set: ReleaseNoteSet, latest: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The set code in its own column, like rule numbers: text, not a tag (it isn't a status).
        Text(set.code, style = MaterialTheme.typography.labelLarge, color = colors.primary, modifier = Modifier.widthIn(min = 56.dp))
        Spacer(Modifier.width(8.dp))
        Text(set.name, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, modifier = Modifier.weight(1f))
        if (latest) {
            Tag(stringResource(R.string.release_notes_latest), container = colors.primary, content = colors.onPrimary)
            Spacer(Modifier.width(8.dp))
        }
        if (set.sha == null) {   // not converted yet: opens the original page
            Tag(stringResource(R.string.release_notes_online))
            Spacer(Modifier.width(8.dp))
        }
        Icon(painterResource(R.drawable.ic_chevron_right), null,   // the row's text names it
            tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}
