package es.alejandro.grandarbiter.domain.repository

import kotlinx.coroutines.flow.Flow

/** What card details show. Stored in SettingsDataStore; an interface so the logic tests without Android. */
interface CardDisplayPreferences {
    /**
     * Community formats the judge chose not to see in card details. The hidden ones are stored, not
     * the shown ones: a format added later shows up until someone hides it.
     */
    val hiddenCommunityFormats: Flow<Set<String>>
}