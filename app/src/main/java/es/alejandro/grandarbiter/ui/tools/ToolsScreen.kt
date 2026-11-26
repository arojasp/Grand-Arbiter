package es.alejandro.grandarbiter.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.ui.components.ListDivider
import es.alejandro.grandarbiter.ui.components.Panel
import es.alejandro.grandarbiter.ui.home.UpdatedLabel
import es.alejandro.grandarbiter.ui.components.SectionTitle
import es.alejandro.grandarbiter.ui.navigation.Screen

/*
 * The tools hub: two grouped lists. The tools you run (Draft Timer, Deck Counter) come first, with no
 * heading, in a primaryContainer panel with the icon on a primary tile. The references keep a plain
 * icon on the neutral panel.
 */

private data class Tool(val title: Int, val description: Int, val icon: ImageVector, val route: String)

/** Things you run during an event. */
private val TOOLS = listOf(
    Tool(R.string.tools_draft_timer, R.string.tools_draft_timer_desc, Icons.Outlined.Timelapse, Screen.DraftTimer.route),
    Tool(R.string.tools_deck_counter, R.string.tools_deck_counter_desc, Icons.Outlined.Style, Screen.DeckCounter.route),
)

/** Things you read; the Head Judge Announcement is a checklist to read from, so it's here. */
private val REFERENCES = listOf(
    Tool(R.string.tools_banned_lists, R.string.tools_banned_lists_desc, Icons.Outlined.Block, Screen.BannedLists.route),
    Tool(R.string.tools_quick_references, R.string.tools_quick_references_desc, Icons.AutoMirrored.Outlined.MenuBook, Screen.QuickReferences.route),
    Tool(R.string.tools_announcement, R.string.tools_announcement_desc, Icons.Outlined.Campaign, Screen.HeadJudgeAnnouncement.route),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onOpen: (route: String) -> Unit,
    onOpenSettings: () -> Unit,
    /** «Updated» on the Draft timer row: the timer now keeps running in the background. */
    draftTimerUpdated: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(stringResource(R.string.nav_tools),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.primary)
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.cards_settings), tint = colors.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ToolList(TOOLS, primary = true, onOpen, updated = setOfNotNull(Screen.DraftTimer.route.takeIf { draftTimerUpdated })) }
            item { SectionTitle(stringResource(R.string.tools_references), Modifier.padding(top = 12.dp)) }
            item { ToolList(REFERENCES, primary = false, onOpen) }
        }
    }
}

@Composable
private fun ToolList(tools: List<Tool>, primary: Boolean, onOpen: (String) -> Unit, updated: Set<String> = emptySet()) {
    val colors = MaterialTheme.colorScheme
    Panel(color = if (primary) colors.primaryContainer else colors.surfaceContainer, shape = RoundedCornerShape(16.dp)) {
        tools.forEachIndexed { i, tool ->
            if (i > 0) ListDivider()
            ToolRow(tool, primary, tool.route in updated) { onOpen(tool.route) }
        }
    }
}

/** One row: icon, title, description, chevron. The whole row is one button. */
@Composable
private fun ToolRow(tool: Tool, primary: Boolean, updated: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val content = if (primary) colors.onPrimaryContainer else colors.onSurface
    val secondary = if (primary) colors.onPrimaryContainer else colors.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (primary) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.primary),
                contentAlignment = Alignment.Center) {
                Icon(tool.icon, null, tint = colors.onPrimary, modifier = Modifier.size(24.dp))
            }
        } else {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(tool.icon, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(tool.title), style = MaterialTheme.typography.titleMedium, color = content)
                if (updated) UpdatedLabel()
            }
            Text(stringResource(tool.description), style = MaterialTheme.typography.bodyMedium, color = secondary)
        }
        Icon(painterResource(R.drawable.ic_chevron_right), null,   // the row's text names it
            tint = secondary, modifier = Modifier.size(20.dp))
    }
}
