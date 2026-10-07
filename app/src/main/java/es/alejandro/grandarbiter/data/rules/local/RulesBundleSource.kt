package es.alejandro.grandarbiter.data.rules.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

/** Where a bundle comes from. Today: the APK assets. Later: an OTA download, same importer. */
fun interface RulesBundleSource {
    /** The bundle JSON, or null if there is none. */
    suspend fun read(): String?
}

class AssetRulesBundleSource(
    private val context: Context,
    private val path: String = "rules/bundle.json",
) : RulesBundleSource {
    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (_: FileNotFoundException) {
            null
        }
    }
}
