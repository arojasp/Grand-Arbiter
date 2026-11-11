package es.alejandro.grandarbiter.ui.cardsearch

import androidx.lifecycle.SavedStateHandle
import es.alejandro.grandarbiter.data.cards.search.CardQueryParser
import es.alejandro.grandarbiter.domain.model.cards.CardDataInfo
import es.alejandro.grandarbiter.domain.model.cards.CardDetail
import es.alejandro.grandarbiter.domain.model.cards.CardSearchResult
import es.alejandro.grandarbiter.domain.model.cards.CardSummary
import es.alejandro.grandarbiter.domain.model.cards.FormatBanList
import es.alejandro.grandarbiter.domain.repository.CardRepository
import es.alejandro.grandarbiter.domain.repository.SearchPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The search screen's timing, in virtual time: what is searched where, when a query goes to
 * Scryfall, and how many requests it costs. The repository is fake (it classifies queries with
 * the real parser); the real SQL and HTTP have their own tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CardSearchViewModelTest {

    private val main = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(main)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeCards : CardRepository {
        val info = MutableStateFlow<CardDataInfo?>(CardDataInfo("2026-11-09", "2026-11-09", "seed", 0L, 30_000))
        val importing = MutableStateFlow<Int?>(null)
        val onlineCalls = mutableListOf<String>()
        var answer: (String) -> CardSearchResult = { q -> CardSearchResult.Online(listOf(row("online:$q")), 1, emptyList()) }
        /** Holds the online answer until completed (a slow connection). */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun search(query: String, limit: Int): CardSearchResult {
            val p = CardQueryParser.parse(query)
            return if (p.needsOnline) CardSearchResult.NeedsOnline(p.onlineParts, p.isComplete)
            else if (query == NO_SUCH_CARD) CardSearchResult.Offline(emptyList())
            else CardSearchResult.Offline(listOf(row("local:$query")))
        }

        override suspend fun searchOnline(query: String): CardSearchResult {
            onlineCalls += query
            gate?.await()
            return answer(query)
        }

        override suspend fun card(oracleId: String): CardDetail? = null
        override suspend fun findByName(name: String): CardSummary? = null
        override fun observeDataInfo(): Flow<CardDataInfo?> = info
        override fun observeImportProgress(): Flow<Int?> = importing
        override fun observeBanLists(): Flow<List<FormatBanList>> = flowOf(emptyList())

        companion object {
            const val NO_SUCH_CARD = "no such card"
            fun row(name: String) = CardSummary(name, name, null, null, isToken = false)
        }
    }

    private val repo = FakeCards()

    /** Settings › "Search Scryfall automatically". */
    private val prefs = object : SearchPreferences {
        val auto = MutableStateFlow(true)
        override val autoOnlineSearch: Flow<Boolean> = auto
    }

    /** A ViewModel with the screen collecting it, as when Card Search is open. */
    private fun TestScope.open(): CardSearchViewModel {
        val vm = CardSearchViewModel(SavedStateHandle(), repo, prefs)
        backgroundScope.launch(main) { vm.results.collect {} }
        runCurrent()
        return vm
    }

    private fun TestScope.type(vm: CardSearchViewModel, q: String, thenWaitMs: Long = 2_000) {
        vm.onQueryChange(q)
        advanceTimeBy(thenWaitMs)
        runCurrent()
    }

    private fun CardSearchViewModel.shown(): CardSearchResult? = (results.value as? SearchState.Shown)?.result
    private fun CardSearchViewModel.names(): List<String> = when (val r = shown()) {
        is CardSearchResult.Offline -> r.cards.map { it.name }
        is CardSearchResult.Online -> r.cards.map { it.name }
        else -> emptyList()
    }

    // ---- offline

    @Test fun offlineQuery_staysOffline_noRequest() = runTest(main) {
        val vm = open()
        type(vm, "t:elf")
        assertEquals(listOf("local:t:elf"), vm.names())
        assertTrue(repo.onlineCalls.isEmpty())
    }

    @Test fun plainName_withOr_staysOffline() = runTest(main) {
        val vm = open()
        type(vm, "fight or flight")
        assertTrue(vm.shown() is CardSearchResult.Offline)
        assertTrue(repo.onlineCalls.isEmpty())
    }

    @Test fun halfTypedNegation_staysOffline() = runTest(main) {
        val vm = open()
        type(vm, "t:elf -t:")
        assertEquals(listOf("local:t:elf -t:"), vm.names())
        assertTrue(repo.onlineCalls.isEmpty())
    }

    @Test fun notSearchedYet_isNeverZeroCards() = runTest(main) {
        val vm = open()
        type(vm, "doubling season", thenWaitMs = 100)                      // inside the 200 ms pause
        assertEquals(SearchState.Pending, vm.results.value)
        advanceTimeBy(200); runCurrent()
        assertEquals(listOf("local:doubling season"), vm.names())
    }

    @Test fun whileTyping_thePreviousCardsStay() = runTest(main) {
        val vm = open()
        type(vm, "doubling")
        type(vm, "doubling s", thenWaitMs = 100)
        assertEquals(listOf("local:doubling"), vm.names())
    }

    @Test fun aRealZero_isShown() = runTest(main) {
        val vm = open()
        type(vm, FakeCards.NO_SUCH_CARD)
        assertEquals(CardSearchResult.Offline(emptyList()), vm.shown())
    }

    @Test fun afterAZero_typingIsPending_notZeroAgain() = runTest(main) {
        val vm = open()
        type(vm, FakeCards.NO_SUCH_CARD)
        type(vm, "doubling", thenWaitMs = 100)
        assertEquals(SearchState.Pending, vm.results.value)
    }

    // ---- online, automatic

    @Test fun onlineQuery_goesOutByItself_afterThePause() = runTest(main) {
        val vm = open()
        type(vm, "t:elf -t:goblin", thenWaitMs = 400)
        assertEquals(SearchState.SearchingOnline, vm.results.value)       // still typing, maybe
        assertTrue(repo.onlineCalls.isEmpty())
        advanceTimeBy(400); runCurrent()                                   // past 700 ms
        assertEquals(listOf("t:elf -t:goblin"), repo.onlineCalls)
        assertEquals(listOf("online:t:elf -t:goblin"), vm.names())
    }

    @Test fun typing_sendsOnlyTheLastQuery() = runTest(main) {
        val vm = open()
        for (q in listOf("t:elf -t:g", "t:elf -t:go", "t:elf -t:gob", "t:elf -t:goblin")) type(vm, q, thenWaitMs = 300)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf("t:elf -t:goblin"), repo.onlineCalls)
    }

    @Test fun openQuotes_withAnOnlinePart_wait_thenGoOut() = runTest(main) {
        val vm = open()
        type(vm, "-t:land o:\"draw a")
        assertEquals(CardSearchResult.NeedsOnline(listOf("-t:land"), complete = false), vm.shown())
        assertTrue(repo.onlineCalls.isEmpty())
        type(vm, "-t:land o:\"draw a card\"")
        assertEquals(listOf("-t:land o:\"draw a card\""), repo.onlineCalls)
    }

    @Test fun openParenthesis_waits() = runTest(main) {
        val vm = open()
        type(vm, "(t:elf or t:goblin")
        assertFalse((vm.shown() as CardSearchResult.NeedsOnline).complete)
        assertTrue(repo.onlineCalls.isEmpty())
    }

    @Test fun typographicQuotes_countAsQuotes() = runTest(main) {
        // Many Android keyboards type “ ” instead of ": an open “ must wait like an open ".
        val vm = open()
        type(vm, "-t:land o:“draw a")
        assertEquals(false, (vm.shown() as? CardSearchResult.NeedsOnline)?.complete)
        assertTrue(repo.onlineCalls.isEmpty())
    }

    @Test fun slowAnswer_showsSearching_thenTheAnswer() = runTest(main) {
        repo.gate = CompletableDeferred()
        val vm = open()
        type(vm, "t:elf -t:goblin")
        assertEquals(SearchState.SearchingOnline, vm.results.value)
        repo.gate!!.complete(Unit); runCurrent()
        assertEquals(listOf("online:t:elf -t:goblin"), vm.names())
    }

    @Test fun newQuery_replacesTheOneInFlight() = runTest(main) {
        repo.gate = CompletableDeferred()
        val vm = open()
        type(vm, "t:elf -t:goblin")
        type(vm, "t:elf -t:human")
        repo.gate!!.complete(Unit); runCurrent()
        assertEquals(listOf("t:elf -t:goblin", "t:elf -t:human"), repo.onlineCalls)
        assertEquals(listOf("online:t:elf -t:human"), vm.names())          // never the older answer
    }

    @Test fun changingToAnOfflineQuery_dropsTheOnlineAnswer() = runTest(main) {
        val vm = open()
        type(vm, "t:elf -t:goblin")
        type(vm, "t:elf")
        assertEquals(listOf("local:t:elf"), vm.names())
    }

    // ---- failures

    @Test fun noConnection_thenTryAgain() = runTest(main) {
        repo.answer = { CardSearchResult.OnlineFailed(CardSearchResult.OnlineFailed.Reason.NO_CONNECTION, listOf("-t:goblin")) }
        val vm = open()
        type(vm, "t:elf -t:goblin")
        assertTrue(vm.shown() is CardSearchResult.OnlineFailed)
        repo.answer = { q -> CardSearchResult.Online(listOf(FakeCards.row("online:$q")), 1, emptyList()) }
        vm.retryOnline(); advanceTimeBy(1_000); runCurrent()
        assertEquals(2, repo.onlineCalls.size)
        assertEquals(listOf("online:t:elf -t:goblin"), vm.names())
    }

    @Test fun tryAgain_neverSendsAnOfflineQuery() = runTest(main) {
        val vm = open()
        type(vm, "t:elf")
        vm.retryOnline(); advanceTimeBy(1_000); runCurrent()
        assertTrue(repo.onlineCalls.isEmpty())
    }

    // ---- automatic search off (Settings)

    @Test fun autoOff_nothingGoesOut_untilAsked() = runTest(main) {
        prefs.auto.value = false
        val vm = open()
        type(vm, "t:elf -t:goblin")
        assertEquals(SearchState.AskToSearchOnline(listOf("-t:goblin")), vm.results.value)
        assertTrue(repo.onlineCalls.isEmpty())
        vm.retryOnline(); advanceTimeBy(1_000); runCurrent()              // "Search on Scryfall"
        assertEquals(listOf("t:elf -t:goblin"), repo.onlineCalls)
        assertEquals(listOf("online:t:elf -t:goblin"), vm.names())
    }

    @Test fun autoOff_aNewQuery_asksAgain() = runTest(main) {
        prefs.auto.value = false
        val vm = open()
        type(vm, "t:elf -t:goblin")
        vm.retryOnline(); advanceTimeBy(1_000); runCurrent()
        type(vm, "t:elf -t:human")
        assertTrue(vm.results.value is SearchState.AskToSearchOnline)
        assertEquals(1, repo.onlineCalls.size)
    }

    @Test fun autoOff_offlineQueries_workAsAlways() = runTest(main) {
        prefs.auto.value = false
        val vm = open()
        type(vm, "t:elf")
        assertEquals(listOf("local:t:elf"), vm.names())
        vm.retryOnline(); advanceTimeBy(1_000); runCurrent()
        assertTrue(repo.onlineCalls.isEmpty())
    }

    @Test fun autoOff_failedSearch_tryAgain() = runTest(main) {
        prefs.auto.value = false
        repo.answer = { CardSearchResult.OnlineFailed(CardSearchResult.OnlineFailed.Reason.NO_CONNECTION, listOf("-t:goblin")) }
        val vm = open()
        type(vm, "t:elf -t:goblin")
        vm.retryOnline(); advanceTimeBy(1_000); runCurrent()
        assertTrue(vm.shown() is CardSearchResult.OnlineFailed)
        repo.answer = { q -> CardSearchResult.Online(listOf(FakeCards.row("online:$q")), 1, emptyList()) }
        vm.retryOnline(); advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf("online:t:elf -t:goblin"), vm.names())
    }

    @Test fun turningAutoOn_sendsTheWaitingQuery() = runTest(main) {
        prefs.auto.value = false
        val vm = open()
        type(vm, "t:elf -t:goblin")
        prefs.auto.value = true
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf("t:elf -t:goblin"), repo.onlineCalls)
    }

    // ---- coming back, first run

    @Test fun comingBackFromACard_reusesTheAnswer() = runTest(main) {
        val vm = CardSearchViewModel(SavedStateHandle(), repo, prefs)
        val screen = launch(main) { vm.results.collect {} }
        type(vm, "t:elf -t:goblin")
        screen.cancel()
        advanceTimeBy(30_000); runCurrent()                                // reading a card for a while
        val back = launch(main) { vm.results.collect {} }
        advanceTimeBy(2_000); runCurrent()
        assertEquals(listOf("online:t:elf -t:goblin"), vm.names())
        assertEquals(1, repo.onlineCalls.size)                             // no second request
        back.cancel()
    }

    @Test fun nothingIsSearched_untilTheCardsAreReady() = runTest(main) {
        repo.info.value = null
        repo.importing.value = 1_000                                       // first run: Preparing
        val vm = open()
        type(vm, "t:elf -t:goblin")
        assertEquals(SearchState.Pending, vm.results.value)
        assertTrue(repo.onlineCalls.isEmpty())
    }
}
