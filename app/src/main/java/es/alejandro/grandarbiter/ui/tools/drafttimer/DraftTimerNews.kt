package es.alejandro.grandarbiter.ui.tools.drafttimer

import android.content.Context
import es.alejandro.grandarbiter.data.local.datastore.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * «Updated» on Tools › Draft timer: the timer now keeps running in the background. Shown to whoever
 * updated the app until they open the Draft timer; a fresh install has nothing new to point out
 * (design-decisions §7: on a first run everything counts as seen).
 */
class DraftTimerNews(private val context: Context, private val settings: SettingsDataStore) {

    val show: Flow<Boolean> = settings.draftBackgroundNewsSeen.map { it == false }

    /** Once, at start: decides whether this install has news (an update) or not (a fresh install). */
    suspend fun seed() {
        if (settings.draftBackgroundNewsSeen.first() == null) settings.setDraftBackgroundNewsSeen(seen = isFreshInstall())
    }

    suspend fun markSeen() = settings.setDraftBackgroundNewsSeen(true)

    private fun isFreshInstall(): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    }.getOrDefault(true)
}
