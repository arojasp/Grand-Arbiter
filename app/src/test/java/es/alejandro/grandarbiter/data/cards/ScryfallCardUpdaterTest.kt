package es.alejandro.grandarbiter.data.cards

import es.alejandro.grandarbiter.data.cards.local.CardDataSource
import es.alejandro.grandarbiter.data.cards.local.CardDataVersion
import es.alejandro.grandarbiter.data.cards.local.CardImportResult
import es.alejandro.grandarbiter.data.cards.update.InstalledCards
import es.alejandro.grandarbiter.data.cards.update.ScryfallBulkClient
import es.alejandro.grandarbiter.data.cards.update.ScryfallCardUpdater
import es.alejandro.grandarbiter.data.update.FakeHttp
import es.alejandro.grandarbiter.data.update.SilentLog
import es.alejandro.grandarbiter.domain.model.cards.CardUpdateState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Settings › "Check for updates" for the cards: compare with Scryfall's catalog, download both
 * files only when something is newer, import, and never leave the ~30 MB behind or crash.
 */
class ScryfallCardUpdaterTest {

    @get:Rule val tmp = TemporaryFolder()

    private val api = "https://api.scryfall.com"
    private val catalogUrl = "$api/bulk-data"
    private val oracleUrl = "https://data.scryfall.io/oracle-cards/o.jsonl"
    private val rulingsUrl = "https://data.scryfall.io/rulings/r.jsonl"
    private val http = FakeHttp()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var workDir: File
    private lateinit var updater: ScryfallCardUpdater

    @After fun tearDown() = scope.cancel()

    private val oct1 = "2026-10-01T09:00:00.000+00:00"
    private val oct8 = "2026-10-08T09:00:00.000+00:00"

    private fun catalog(oracle: String = oct8, rulings: String = oct8, withRulings: Boolean = true) = """
        {"object":"list","data":[
          {"type":"oracle_cards","updated_at":"$oracle","jsonl_download_uri":"$oracleUrl","compressed_size":1000},
          ${if (withRulings) """{"type":"rulings","updated_at":"$rulings","jsonl_download_uri":"$rulingsUrl"},""" else ""}
          {"type":"all_cards","updated_at":"$oracle"}
        ]}
    """.trimIndent()

    private fun publish(catalogJson: String = catalog()) {
        http.on(catalogUrl, body = catalogJson).on(oracleUrl, body = "ORACLE").on(rulingsUrl, body = "RULINGS")
    }

    private inner class FakeInstalled(
        val version: CardDataVersion? = CardDataVersion(oct1, oct1),
        val result: () -> CardImportResult = { imported(31_000) },
        val gate: CompletableDeferred<Unit>? = null,
    ) : InstalledCards {
        var importedFiles: Pair<String, String>? = null
        var importedVersion: CardDataVersion? = null
        var stateDuringImport: CardUpdateState? = null
        override suspend fun version(): CardDataVersion? { gate?.await(); return version }
        override suspend fun import(source: CardDataSource): CardImportResult {
            importedVersion = source.version()
            importedFiles = source.openOracleCards().use { String(it.readBytes()) } to source.openRulings().use { String(it.readBytes()) }
            stateDuringImport = updater.state.value
            return result()
        }
    }

    private fun imported(cards: Int) = CardImportResult.Imported("scryfall", CardDataVersion(oct8, oct8), cards, 0, 0, emptyMap(), 0)

    private fun updaterWith(installed: InstalledCards): ScryfallCardUpdater {
        workDir = tmp.newFolder()
        return ScryfallCardUpdater(ScryfallBulkClient(http.client, api), installed, workDir, scope, SilentLog).also { updater = it }
    }

    private fun ScryfallCardUpdater.runOnce(): CardUpdateState = runBlocking {
        checkForUpdates()
        withTimeout(5_000) { scope.coroutineContext.job.children.toList().joinAll() }
        state.value
    }

    private fun assertNoDownloadsLeft() = assertFalse("the ~30 MB must not stay in the cache", File(workDir, "cards-update").exists())

    // ------------------------------------------------------------ nothing to do

    @Test fun sameVersion_isUpToDate_andDownloadsNothing() {
        publish(catalog(oracle = oct1, rulings = oct1))
        assertEquals(CardUpdateState.UpToDate(oct1), updaterWith(FakeInstalled()).runOnce())
        assertEquals(0, http.count(oracleUrl))
        assertEquals(0, http.count(rulingsUrl))
    }

    @Test fun olderOnScryfall_isUpToDate() {
        publish(catalog(oracle = "2026-09-01T00:00:00.000+00:00", rulings = "2026-09-01T00:00:00.000+00:00"))
        assertTrue(updaterWith(FakeInstalled()).runOnce() is CardUpdateState.UpToDate)
        assertEquals(0, http.count(oracleUrl))
    }

    // ------------------------------------------------------------ updating

    @Test fun newerCards_downloadsBothFiles_importsThem_andCleansUp() {
        publish()
        val installed = FakeInstalled()

        val state = updaterWith(installed).runOnce()

        assertEquals(CardUpdateState.Updated(31_000, oct8), state)
        assertEquals("ORACLE" to "RULINGS", installed.importedFiles)
        assertEquals(CardDataVersion(oct8, oct8), installed.importedVersion)
        assertEquals(CardUpdateState.Importing, installed.stateDuringImport)
        assertNoDownloadsLeft()
    }

    @Test fun onlyRulingsNewer_stillUpdates_bothFilesTravelTogether() {
        publish(catalog(oracle = oct1, rulings = oct8))
        val installed = FakeInstalled()
        assertTrue(updaterWith(installed).runOnce() is CardUpdateState.Updated)
        assertEquals(1, http.count(oracleUrl))
        assertEquals(1, http.count(rulingsUrl))
    }

    @Test fun noCardsInstalled_downloads() {
        publish()
        assertTrue(updaterWith(FakeInstalled(version = null)).runOnce() is CardUpdateState.Updated)
    }

    // ------------------------------------------------------------ failures: Failed, files cleaned, nothing imported

    @Test fun catalogWithoutRulings_failsBeforeDownloading() {
        publish(catalog(withRulings = false))
        assertTrue(updaterWith(FakeInstalled()).runOnce() is CardUpdateState.Failed)
        assertEquals(0, http.count(oracleUrl))
    }

    @Test fun scryfallRejectsTheRequest_fails() {
        http.on(catalogUrl, code = 400)   // e.g. missing User-Agent / Accept
        assertTrue(updaterWith(FakeInstalled()).runOnce() is CardUpdateState.Failed)
    }

    @Test fun noConnection_fails() {
        http.onThrow(catalogUrl, java.net.UnknownHostException("api.scryfall.com"))
        assertTrue(updaterWith(FakeInstalled()).runOnce() is CardUpdateState.Failed)
    }

    @Test fun captivePortalHtml_failsAsUnexpectedAnswer() {
        http.on(catalogUrl, body = "<html>Hotel wifi login</html>")
        assertEquals(CardUpdateState.Failed("unexpected answer from Scryfall"), updaterWith(FakeInstalled()).runOnce())
    }

    @Test fun downloadCutShort_failsWithoutImporting_andCleansUp() {
        publish()
        http.onBody(rulingsUrl, FakeHttp.truncated(promised = 5_000))
        val installed = FakeInstalled()
        assertTrue(updaterWith(installed).runOnce() is CardUpdateState.Failed)
        assertEquals(null, installed.importedFiles)
        assertNoDownloadsLeft()
    }

    @Test fun rejectedImport_failsWithItsReason_andCleansUp() {
        publish()
        val state = updaterWith(FakeInstalled(result = { CardImportResult.Rejected("only 12 cards") })).runOnce()
        assertEquals(CardUpdateState.Failed("only 12 cards"), state)
        assertNoDownloadsLeft()
    }

    @Test fun unexpectedDatabaseError_fails_insteadOfCrashing() {
        // SQLiteFullException after a 30 MB download is a RuntimeException: it used to escape.
        publish()
        val state = updaterWith(FakeInstalled(result = { throw IllegalStateException("disk full") })).runOnce()
        assertEquals(CardUpdateState.Failed("disk full"), state)
        assertNoDownloadsLeft()
    }

    // ------------------------------------------------------------ the button

    @Test fun tappingTwiceWhileRunning_downloadsOnce() = runBlocking {
        publish()
        val gate = CompletableDeferred<Unit>()
        val u = updaterWith(FakeInstalled(gate = gate))
        u.checkForUpdates()
        u.checkForUpdates()
        gate.complete(Unit)
        withTimeout(5_000) { scope.coroutineContext.job.children.toList().joinAll() }
        assertEquals(1, http.count(catalogUrl))
        assertEquals(1, http.count(oracleUrl))
    }
}
