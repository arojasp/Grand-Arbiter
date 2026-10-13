package es.alejandro.grandarbiter.ui.tools.quickrefs

/**
 * One entry of a document's index in the quick reference editor: a chapter ("7. Additional
 * Rules"), a page ("702. Keyword Abilities") or a rule in it ("702.19. Trample").
 * [children]: known up front (chapters); [lazyChildren]: a page whose rules load when opened.
 */
data class IndexNode(
    val sectionId: String,
    val label: String,
    /** Rules without a title ("100.1") show the start of their text instead. */
    val detail: String? = null,
    val addable: Boolean = true,
    val children: List<IndexNode>? = null,
    val lazyChildren: Boolean = false,
)

/** What the editor's list shows: the nodes that are visible, flattened, with their state. */
data class IndexRow(
    val sectionId: String,
    val label: String,
    val detail: String?,
    val depth: Int,
    val expandable: Boolean,
    val expanded: Boolean,
    val addable: Boolean,
    val added: Boolean,
    /** Opened, and its rules are still loading. */
    val loading: Boolean,
)

/**
 * The index as a flat list: chapters start collapsed, opening one shows its pages, opening a page
 * shows its rules. Only what's open is listed, so the CR (~3300 sections) stays a short list.
 * Pure: unit-tested.
 */
object QuickReferenceIndex {

    fun rows(
        nodes: List<IndexNode>,
        expanded: Set<String>,
        loaded: Map<String, List<IndexNode>>,
        added: Set<String>,
        depth: Int = 0,
    ): List<IndexRow> = buildList {
        for (node in nodes) {
            val children = node.children ?: loaded[node.sectionId]
            // A lazy page is expandable until loading shows it has no rules under it.
            val expandable = when {
                node.children != null -> node.children.isNotEmpty()
                node.lazyChildren -> loaded[node.sectionId]?.isNotEmpty() ?: true
                else -> false
            }
            val isExpanded = expandable && node.sectionId in expanded
            add(IndexRow(
                sectionId = node.sectionId,
                label = node.label,
                detail = node.detail,
                depth = depth,
                expandable = expandable,
                expanded = isExpanded,
                addable = node.addable,
                added = node.sectionId in added,
                loading = isExpanded && children == null,
            ))
            if (isExpanded && children != null) addAll(rows(children, expanded, loaded, added, depth + 1))
        }
    }

    /** The node with [sectionId] anywhere in the tree (including loaded rules), or null. */
    fun find(nodes: List<IndexNode>, loaded: Map<String, List<IndexNode>>, sectionId: String): IndexNode? {
        for (node in nodes) {
            if (node.sectionId == sectionId) return node
            val children = node.children ?: loaded[node.sectionId] ?: continue
            find(children, loaded, sectionId)?.let { return it }
        }
        return null
    }

    /** Opening a lazy page that hasn't been loaded yet: its rules must be fetched. */
    fun needsLoading(nodes: List<IndexNode>, loaded: Map<String, List<IndexNode>>, sectionId: String): Boolean {
        val node = find(nodes, loaded, sectionId) ?: return false
        return node.lazyChildren && node.children == null && sectionId !in loaded
    }
}
