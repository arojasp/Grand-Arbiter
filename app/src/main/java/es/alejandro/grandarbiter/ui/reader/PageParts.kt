package es.alejandro.grandarbiter.ui.reader

import es.alejandro.grandarbiter.data.rules.bundle.Block
import es.alejandro.grandarbiter.data.rules.bundle.HeadingRole

/**
 * A run of blocks under one IPG part heading (Definition, Examples, Philosophy, …).
 * Blocks before the first part heading form a part with role null (always shown).
 * Documents without part headings (CR, MTR, JAR) are a single role-null part.
 */
data class PagePart(
    val role: HeadingRole?,
    val title: String?,
    val blocks: List<Block>,
) {
    /** What a judge needs at the table is open; the rest is one tap away. */
    val expandedByDefault: Boolean
        get() = role == null || role == HeadingRole.DEFINITION || role == HeadingRole.ADDITIONAL_REMEDY

    val exampleCount: Int get() = blocks.count { it is Block.Example }
}

fun groupIntoParts(blocks: List<Block>): List<PagePart> {
    val parts = mutableListOf<PagePart>()
    var role: HeadingRole? = null
    var title: String? = null
    var current = mutableListOf<Block>()

    fun close() {
        if (current.isNotEmpty() || role != null) parts += PagePart(role, title, current.toList())
        current = mutableListOf()
    }

    for (block in blocks) {
        if (block is Block.Heading && block.role != null) {
            close()
            role = block.role
            title = block.text
        } else {
            current += block
        }
    }
    close()
    return parts
}
