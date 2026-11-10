package es.alejandro.grandarbiter.domain.repository

import kotlinx.coroutines.flow.Flow

/** Card search settings. Stored in SettingsDataStore; an interface so the search logic tests without Android. */
interface SearchPreferences {
    /** True: searches only Scryfall can answer go out by themselves. False: only when the judge asks. */
    val autoOnlineSearch: Flow<Boolean>
}
