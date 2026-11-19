package es.alejandro.grandarbiter.data.draft

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.DraftTimes
import es.alejandro.grandarbiter.domain.draft.DraftTimesPreset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Share on one phone, import on another, on a real device: the JSON goes from one store to a
 * second one (another prefs file stands for the other phone), and a new store (as after a restart)
 * reads what was saved. Real kotlinx.serialization and SharedPreferences, no fakes; test files
 * only, never the judge's own presets.
 */
@RunWith(AndroidJUnit4::class)
class DraftPresetShareDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sender = "draft_presets_test_sender"
    private val receiver = "draft_presets_test_receiver"

    private fun store(name: String) = PrefsUserDraftPresetStore(context, prefsName = name)
    private fun clear() = listOf(sender, receiver).forEach { context.deleteSharedPreferences(it) }

    @Before fun before() = clear()
    @After fun after() = clear()

    private fun importInto(store: PrefsUserDraftPresetStore, text: String): SharedDraftPreset {
        val result = DraftPresetShareCodec.decode(text)
        assertTrue("$result", result is SharedPresetResult.Read)
        val p = (result as SharedPresetResult.Read).preset
        store.save(p.format, p.name, p.times, p.constructionMinutes)
        return p
    }

    @Test fun shareThenImport_sameTimesOnTheOtherPhone() {
        val times = DraftTimes(true, mapOf(14 to mapOf(1 to 50, 2 to 45), 15 to mapOf(1 to 45)), mapOf(1 to 90, 2 to 120))
        val shared = store(sender).save(DraftFormat.BOOSTER, "RCQ Girona", times, 30)

        importInto(store(receiver), "[19/11/26, 14:45] Alejandro: " + DraftPresetShareCodec.encode(shared))

        val after = store(receiver).presets(DraftFormat.BOOSTER).value.single()
        assertEquals("RCQ Girona", after.name)
        assertEquals(times, after.times)
        assertEquals(30, after.constructionMinutes)
        assertTrue(store(receiver).presets(DraftFormat.TWO_HEADED_GIANT).value.isEmpty())
    }

    @Test fun twoHeadedGiant_goesToItsOwnList() {
        val shared = store(sender).save(DraftFormat.TWO_HEADED_GIANT, "Casual", DraftTimes.MTR.withTimedPicks(false), 40)
        importInto(store(receiver), DraftPresetShareCodec.encode(shared))
        assertTrue(store(receiver).presets(DraftFormat.BOOSTER).value.isEmpty())
        assertEquals(listOf("Casual"), store(receiver).presets(DraftFormat.TWO_HEADED_GIANT).value.map { it.name })
    }

    @Test fun replace_keepsOnePreset_withTheNewTimes() {
        val receiving = store(receiver)
        val old = receiving.save(DraftFormat.BOOSTER, "RCQ Girona", DraftTimes.MTR, 25)
        val shared = store(sender).save(DraftFormat.BOOSTER, "rcq girona", DraftTimesPreset.PRO_TOUR.times, 30)

        importInto(receiving, DraftPresetShareCodec.encode(shared))

        val list = store(receiver).presets(DraftFormat.BOOSTER).value
        assertEquals(1, list.size)
        assertEquals(old.id, list[0].id)                         // same entry, replaced
        assertEquals(DraftTimesPreset.PRO_TOUR.times, list[0].times)
        assertEquals(30, list[0].constructionMinutes)
    }

    @Test fun manyPresets_allSurvive() {
        val sending = store(sender)
        val receiving = store(receiver)
        (1..30).forEach { i ->
            val p = sending.save(DraftFormat.BOOSTER, "Preset $i", DraftTimes(true, mapOf(14 to mapOf(1 to 30 + i)), emptyMap()), 20 + i)
            importInto(receiving, DraftPresetShareCodec.encode(p))
        }
        val list = store(receiver).presets(DraftFormat.BOOSTER).value
        assertEquals(30, list.size)
        assertEquals(30 + 17, list.first { it.name == "Preset 17" }.times.pickSeconds[14]?.get(1))
    }
}
