package es.alejandro.grandarbiter.ui.tools.banned

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.domain.model.customlists.CustomListEntry
import es.alejandro.grandarbiter.domain.model.customlists.ListStatus
import es.alejandro.grandarbiter.ui.components.ListDivider
import es.alejandro.grandarbiter.ui.components.Tag

private val Indent = 32.dp

/** A custom list in Banned & Restricted: like a format, plus its note and an Edit action. */
@Composable
internal fun CustomListItem(
    view: CustomListView,
    expanded: Boolean,
    divider: Boolean,
    modifier: Modifier,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onOpenCard: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")
    val list = view.list
    Column(modifier) {
        if (divider) ListDivider()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(list.name, style = MaterialTheme.typography.titleMedium,
                color = if (expanded) colors.primary else colors.onSurface, modifier = Modifier.weight(1f))
            val count = list.entries.size.toString()
            if (expanded) Tag(count, container = colors.primary, content = colors.onPrimary) else Tag(count)
            Icon(painterResource(R.drawable.ic_chevron_right), null, tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(rotation))
        }
        AnimatedVisibility(expanded) {
            Column {
                if (list.note.isNotBlank()) {
                    Text(list.note, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = Indent, end = 12.dp, bottom = 12.dp))
                }
                list.sorted.forEach { entry ->
                    ListDivider(Modifier.padding(start = Indent - 12.dp))
                    CustomEntryRow(entry, highlighted = entry.oracleId in view.matches) { onOpenCard(entry.oracleId) }
                }
                ListDivider(Modifier.padding(start = Indent - 12.dp))
                Row(Modifier.padding(start = Indent - 12.dp, top = 4.dp, bottom = 4.dp)) {
                    TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.custom_edit), Modifier.padding(start = 8.dp))
                    }
                    val context = LocalContext.current
                    TextButton(onClick = { shareCustomList(context, list) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Outlined.Share, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.custom_share), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomEntryRow(entry: CustomListEntry, highlighted: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (highlighted) Modifier.background(colors.primary.copy(alpha = 0.12f)) else Modifier)   // a fill, not content
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(start = Indent, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(entry.name,
            style = if (highlighted) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyLarge,
            color = if (highlighted) colors.primary else colors.onSurface,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        StatusTag(entry.status)
    }
}

@Composable
internal fun StatusTag(status: ListStatus) {
    val colors = MaterialTheme.colorScheme
    when (status) {
        ListStatus.Banned -> Tag(stringResource(R.string.legality_banned), container = colors.errorContainer, content = colors.onErrorContainer)
        ListStatus.Restricted -> Tag(stringResource(R.string.legality_restricted), container = colors.primary, content = colors.onPrimary)
        is ListStatus.Points -> Tag(pluralStringResource(R.plurals.custom_points, status.value, status.value))
    }
}

@Composable
internal fun NewListRow(divider: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        if (divider) ListDivider()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.Add, null, tint = colors.primary)
            Text(stringResource(R.string.custom_new_list), style = MaterialTheme.typography.titleMedium, color = colors.primary)
        }
    }
}
