package es.alejandro.grandarbiter.ui.reader

import android.content.ClipData
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.data.rules.bundle.Block
import es.alejandro.grandarbiter.data.rules.bundle.DocumentId
import es.alejandro.grandarbiter.domain.model.rules.PageSection
import kotlinx.coroutines.launch

/**
 * A rule as plain text for the clipboard, ready to paste in a chat or an appeal report:
 *
 *     CR 603.2a
 *     Rule text…
 *
 *     Example: …
 *
 * Only the section itself, not its subrules (each has its own button). Pure: unit-tested.
 * Labels are English because the rules text is English.
 */
object RuleText {

    /** "CR 603.2a", "MTR 6.1 Tardiness", "IPG 2.5 Game Rule Violation". */
    fun reference(section: PageSection, document: DocumentId): String =
        listOfNotNull(document.key.uppercase(), displayNumber(section.number), section.title?.let(InlineMarkup::plainText))
            .joinToString(" ")

    fun of(section: PageSection, document: DocumentId, penalty: String? = null): String = buildString {
        append(reference(section, document))
        penalty?.let { append("\nPenalty: ").append(it) }
        section.blocks.forEach { block -> append("\n\n").append(blockText(block)) }
    }

    fun blockText(block: Block): String = when (block) {
        is Block.Paragraph -> InlineMarkup.plainText(block.text)
        is Block.Heading -> InlineMarkup.plainText(block.text)
        is Block.Example -> (block.label?.let { "Example $it: " } ?: "Example: ") + InlineMarkup.plainText(block.text)
        is Block.BulletList -> block.items.mapIndexed { i, item ->
            (if (block.ordered) "${i + 1}. " else "• ") + InlineMarkup.plainText(item)
        }.joinToString("\n")
        is Block.Table -> (listOf(block.header) + block.rows).joinToString("\n") { row ->
            row.joinToString(" | ") { InlineMarkup.plainText(it).replace("\n", " ") }
        }
    }
}

/** Copies text to the clipboard. Android 13+ confirms it itself; older versions get a toast. */
@Composable
fun rememberCopyText(): (label: String, text: String) -> Unit {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return { label, text ->
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, text)))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context, context.getString(R.string.reader_copied, label), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** Small, quiet copy button for a rule; outside text selection so it never gets selected. */
@Composable
fun CopyRuleButton(reference: String, onCopy: () -> Unit, modifier: Modifier = Modifier) {
    DisableSelection {
        IconButton(onClick = onCopy, modifier = modifier.size(40.dp)) {
            Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = stringResource(R.string.reader_copy, reference),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
