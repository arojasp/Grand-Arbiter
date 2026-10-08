package es.alejandro.grandarbiter.data.releasenotes

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileNotFoundException

/**
 * Sets' release notes, newest first: the index and, per set, the offline document.
 *
 * Everything ships in the APK (app/src/main/assets/rules/) and is also published from branch main,
 * like the rules bundle: adding or revising a set = running tools/releasenotes/build_release_notes.py
 * and merging to main. [refresh] (at launch) downloads the index and only the documents whose SHA
 * changed, each verified against the index before it's kept.
 */
class ReleaseNotesRepository(
    private val context: Context,
    private val http: OkHttpClient,
    baseUrl: String,
) {
    private val base = baseUrl.trimEnd('/')
    private val dir = File(context.filesDir, "releasenotes").apply { mkdirs() }
    private val indexCache = File(dir, "index.json")
    private val mutex = Mutex()
    private val documents = HashMap<String, ReleaseNotesDocument>()

    private val _sets = MutableStateFlow<List<ReleaseNoteSet>>(emptyList())
    val sets: StateFlow<List<ReleaseNoteSet>> = _sets.asStateFlow()

    /** Downloaded index if valid, else the APK's. */
    suspend fun load() = withContext(Dispatchers.IO) {
        if (_sets.value.isNotEmpty()) return@withContext
        val cached = runCatching { indexCache.readText() }.getOrNull()?.let(ReleaseNotesParser::parse)
        _sets.value = cached ?: ReleaseNotesParser.parse(asset(INDEX_ASSET).orEmpty()).orEmpty()
    }

    /** The set's offline release notes; null if there's no valid copy (then: view online). */
    suspend fun document(code: String): ReleaseNotesDocument? = withContext(Dispatchers.IO) {
        load()
        mutex.withLock {
            val sha = _sets.value.firstOrNull { it.code == code }?.sha ?: return@withLock null
            documents[code]?.let { return@withLock it }
            (read(downloaded(code), sha) ?: read(asset(docAsset(code)), sha))?.also { documents[code] = it }
        }
    }

    /** Silent on failure (offline, bad files): what's installed stays. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        load()
        val text = get("$base/release-notes.json") ?: return@withContext
        val sets = ReleaseNotesParser.parse(text) ?: return@withContext
        for (set in sets) {
            val sha = set.sha ?: continue
            if (hasValidCopy(set.code, sha)) continue
            val doc = get("$base/releasenotes/${set.code}.json") ?: continue
            if (ReleaseNotesParser.parseDocument(doc, sha) != null) save(downloaded(set.code), doc)
        }
        save(indexCache, text)
        mutex.withLock { documents.clear() }
        _sets.value = sets
    }

    private fun hasValidCopy(code: String, sha: String): Boolean =
        listOf(runCatching { downloaded(code).readText() }.getOrNull(), asset(docAsset(code)))
            .any { it != null && ReleaseNotesParser.sha16(it) == sha }

    private fun read(text: String?, sha: String): ReleaseNotesDocument? = text?.let { ReleaseNotesParser.parseDocument(it, sha) }

    private fun read(file: File, sha: String) = read(runCatching { file.readText() }.getOrNull(), sha)

    private fun downloaded(code: String) = File(dir, "$code.json")
    private fun docAsset(code: String) = "rules/releasenotes/$code.json"

    private fun asset(path: String): String? = try {
        context.assets.open(path).use { String(it.readBytes(), Charsets.UTF_8) }
    } catch (_: FileNotFoundException) {
        null
    }

    private fun get(url: String): String? = runCatching {
        http.newCall(Request.Builder().url(url).header("Cache-Control", "no-cache").build()).execute().use { r ->
            if (r.isSuccessful) r.body.string() else null
        }
    }.onFailure { Log.i(TAG, "release notes: ${it.message}") }.getOrNull()

    /** Write-then-rename: a cut write never leaves a half file that looks valid. */
    private fun save(target: File, text: String) {
        val tmp = File(target.path + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
    }

    private companion object {
        const val INDEX_ASSET = "rules/release-notes.json"
        const val TAG = "ReleaseNotes"
    }
}
