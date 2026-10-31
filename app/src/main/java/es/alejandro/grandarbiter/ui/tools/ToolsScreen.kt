package es.alejandro.grandarbiter.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.ui.navigation.Screen
import es.alejandro.grandarbiter.ui.theme.GoldDeep
import es.alejandro.grandarbiter.ui.theme.OnGoldDeep

/*
 * Figma: "Grand Arbiter - Tools with Disabled State" (node 19:723). The tools hub: in-tournament
 * tools as large cards, references as compact rows.
 */

private data class Tool(val title: Int, val description: Int, val icon: ImageVector, val route: String?)

private val ACTIVE_TOOLS = listOf(
    Tool(R.string.tools_deck_counter, R.string.tools_deck_counter_desc, Icons.Outlined.Style, Screen.DeckCounter.route),
    Tool(R.string.tools_announcement, R.string.tools_announcement_desc, Icons.Outlined.Campaign, Screen.HeadJudgeAnnouncement.route),
    Tool(R.string.tools_draft_timer, R.string.tools_draft_timer_desc, Icons.Outlined.Timelapse, Screen.DraftTimer.route),
)

private val REFERENCES = listOf(
    Tool(R.string.tools_banned_lists, R.string.tools_banned_lists_desc, Icons.Outlined.Block, Screen.BannedLists.route),
    Tool(R.string.tools_quick_references, R.string.tools_quick_references_desc, Icons.AutoMirrored.Outlined.MenuBook, Screen.QuickReferences.route),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onOpen: (route: String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val divider = colors.surfaceContainerHighest
    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.drawBehind {
                    drawLine(divider, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                },
                title = {
                    Text(stringResource(R.string.nav_tools),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
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
        Box(Modifier.padding(innerPadding).fillMaxSize().atmosphere()) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { SectionHeader(stringResource(R.string.tools_active), accent = colors.primary) }
                ACTIVE_TOOLS.forEach { tool -> item { ToolCard(tool, onOpen) } }
                item { Spacer(Modifier.height(24.dp)) }
                item { SectionHeader(stringResource(R.string.tools_references), accent = colors.surfaceContainerHighest) }
                REFERENCES.forEach { tool -> item { ReferenceRow(tool, onOpen) } }
            }
        }
    }
}

/** Figma "Atmospheric Background Element": two soft gold glows (radial gradients, no blur API needed). */
private fun Modifier.atmosphere(): Modifier = drawBehind {
    val gold = Color(0xFFE6C364)
    fun glow(center: Offset, radius: Float) = drawCircle(
        Brush.radialGradient(listOf(gold.copy(alpha = 0.06f), Color.Transparent), center = center, radius = radius),
        radius = radius, center = center,
    )
    glow(Offset(size.width, size.height), 200.dp.toPx())
    glow(Offset(0f, size.height * 0.45f), 240.dp.toPx())
}

@Composable
private fun SectionHeader(title: String, accent: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(6.dp).height(24.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(8.dp))
        Text(title, style = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}

/** Large in-tournament tool card. route == null: shown disabled with a "Coming soon" pill. */
@Composable
private fun ToolCard(tool: Tool, onOpen: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val enabled = tool.route != null
    Box(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0x0DE2C364), colors.surfaceContainer.copy(alpha = 0.5f))))
            .border(1.dp, colors.primary.copy(alpha = 0.1f), shape)
            .clickable(enabled = enabled, role = Role.Button) { tool.route?.let(onOpen) }
            .padding(16.dp)
    ) {
        Column {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(GoldDeep), contentAlignment = Alignment.Center) {
                Icon(tool.icon, null, tint = OnGoldDeep, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(tool.title),
                style = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.25.sp),
                color = colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(tool.description), style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
                color = colors.onSurfaceVariant)
        }
        if (!enabled) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(colors.surfaceContainerHighest)
                    .border(1.dp, colors.outlineVariant, CircleShape)
                    .padding(horizontal = 9.dp, vertical = 2.dp)
            ) {
                Text(stringResource(R.string.tools_coming_soon).uppercase(),
                    style = TextStyle(fontSize = 10.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.25.sp),
                    color = colors.onSurfaceVariant)
            }
        }
    }
}

/** Compact reference row: round icon, title, description. */
@Composable
private fun ReferenceRow(tool: Tool, onOpen: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceContainer)
            .border(1.dp, colors.outlineVariant, shape)
            .clickable(role = Role.Button) { tool.route?.let(onOpen) }
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF45485B)), contentAlignment = Alignment.Center) {
            Icon(tool.icon, null, tint = colors.onSurface, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(tool.title), style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp), color = colors.onSurface)
            Text(stringResource(tool.description), style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp), color = colors.onSurfaceVariant)
        }
    }
}
