package es.alejandro.grandarbiter.data.updates

import es.alejandro.grandarbiter.data.local.datastore.SettingsDataStore
import es.alejandro.grandarbiter.data.releasenotes.ReleaseNotesRepository
import es.alejandro.grandarbiter.domain.repository.RulesRepository
import es.alejandro.grandarbiter.domain.updates.SectionUpdates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach

/** The «Updated» label: installed version of each section, what the user has seen, and marking it seen. */
class SectionVersions(
    rules: RulesRepository,
    releaseNotes: ReleaseNotesRepository,
    private val settings: SettingsDataStore,
) {
    /** Documents by effective date (fix-only updates keep it), release notes by the newest set's code. */
    val installed: Flow<Map<String, String>> = combine(rules.observeDocuments(), releaseNotes.sets) { docs, sets ->
        docs.associate { it.id.key to it.effectiveDate.toString() } +
            listOfNotNull(sets.firstOrNull()?.let { SectionUpdates.RELEASE_NOTES to it.code })
    }.distinctUntilChanged()

    /** Sections to label. Collecting it also stores sections with nothing stored as seen (first run). */
    val updated: Flow<Set<String>> = installed
        .onEach { settings.seedSectionVersions(it) }
        .combine(settings.seenSectionVersions) { installed, seen -> SectionUpdates.updated(installed, seen) }
        .distinctUntilChanged()

    /** The user entered [sectionId]: waits until its installed version is known, then stores it as seen. */
    suspend fun markSeen(sectionId: String) {
        val version = installed.first { sectionId in it }.getValue(sectionId)
        settings.markSectionSeen(sectionId, version)
    }
}
