package es.alejandro.grandarbiter.domain.updates

/**
 * The «Updated» label: which sections changed since the user last entered them.
 *
 * A section is anything with its own label (a rules document, the release notes). Its version is
 * any string that changes when the content changes: a document's effective date (fix-only updates
 * keep it, so they don't count), the code of the newest set with release notes.
 */
object SectionUpdates {

    /** Section id of the set release notes; documents use their DocumentId key ("cr", "ipg"…). */
    const val RELEASE_NOTES = "release_notes"

    /**
     * Sections whose installed version is not the one the user last saw. "Not the one", not
     * "newer": set codes have no order, and a rolled-back document is also different content.
     * A section with nothing stored is not updated: [unseeded] stores it first.
     */
    fun updated(installed: Map<String, String>, seen: Map<String, String>): Set<String> =
        installed.filter { (id, version) -> seen[id] != null && seen[id] != version }.keys

    /**
     * What to store as seen for sections that have nothing stored yet: everything on the first run
     * (new install, or first launch of the version with the label), and a section that appears
     * later. They count as seen, so no label until their next real update.
     */
    fun unseeded(installed: Map<String, String>, seen: Map<String, String>): Map<String, String> =
        installed.filterKeys { it !in seen }
}
