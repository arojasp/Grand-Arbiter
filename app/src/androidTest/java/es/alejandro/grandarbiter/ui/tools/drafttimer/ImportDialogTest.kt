package es.alejandro.grandarbiter.ui.tools.drafttimer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.data.draft.DraftPresetShareCodec
import es.alejandro.grandarbiter.data.draft.SharedDraftPreset
import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.DraftTimes
import es.alejandro.grandarbiter.domain.draft.UserDraftPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Import preset dialog on a device: what it says for each paste, when Import works, the paste button. */
@RunWith(AndroidJUnit4::class)
class ImportDialogTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)
    private var imported: SharedDraftPreset? = null

    private val rcq = UserDraftPreset("a", "RCQ Girona", DraftFormat.BOOSTER,
        DraftTimes(true, mapOf(14 to mapOf(1 to 50, 2 to 45, 3 to 40)), mapOf(1 to 90)), 30)
    private val rcqJson = DraftPresetShareCodec.encode(rcq)

    @Before fun show() {
        compose.setContent { ImportDialog(DraftFormat.BOOSTER, onDismiss = {}, onImport = { imported = it }) }
    }

    private fun type(text: String) = compose.onNode(hasSetTextAction()).performTextReplacement(text)
    private fun importButton() = compose.onNodeWithText(s(R.string.draft_times_import_confirm))

    @Test fun empty_hint_importOff() {
        compose.onNodeWithText(s(R.string.draft_times_import_hint)).assertExists()
        importButton().assertIsNotEnabled()
    }

    @Test fun notAPreset_saysSo_importOff() {
        type("Hola, aquí tienes los tiempos")
        compose.onNodeWithText(s(R.string.draft_times_import_not_preset)).assertExists()
        importButton().assertIsNotEnabled()
        importButton().performClick()
        assertNull(imported)
    }

    @Test fun cutOff_saysSo() {
        type(rcqJson.dropLast(10))
        compose.onNodeWithText(s(R.string.draft_times_import_incomplete)).assertExists()
        importButton().assertIsNotEnabled()
    }

    @Test fun newerVersion_saysSo() {
        type("""{"kind":"grand-arbiter-draft-preset","v":9,"name":"X","format":"booster","minutes":25}""")
        compose.onNodeWithText(s(R.string.draft_times_import_newer)).assertExists()
        importButton().assertIsNotEnabled()
    }

    @Test fun invalid_saysSo() {
        type("""{"kind":"grand-arbiter-draft-preset","v":1,"name":"X","format":"booster","minutes":0}""")
        compose.onNodeWithText(s(R.string.draft_times_import_invalid)).assertExists()
        importButton().assertIsNotEnabled()
    }

    @Test fun preset_preview_thenImport() {
        type("[19/11/26, 14:45] Alejandro: $rcqJson")
        compose.onNodeWithText("RCQ Girona").assertExists()
        compose.onNodeWithText(s(R.string.draft_format_booster)).assertExists()
        compose.onNodeWithText("3 picks changed", substring = true).assertExists()
        compose.onNodeWithText("Construction 30 min", substring = true).assertExists()
        importButton().assertIsEnabled().performClick()
        assertEquals(SharedDraftPreset("RCQ Girona", DraftFormat.BOOSTER, rcq.times, 30), imported)
    }

    @Test fun otherFormat_saysWhereItGoes() {
        type(DraftPresetShareCodec.encode(UserDraftPreset("b", "Casual", DraftFormat.TWO_HEADED_GIANT, DraftTimes.MTR.withTimedPicks(false), 40)))
        compose.onNodeWithText(s(R.string.draft_times_import_other_format, s(R.string.draft_format_2hg))).assertExists()
        compose.onNodeWithText(s(R.string.draft_times_untimed_title), substring = true).assertExists()
        importButton().assertIsEnabled()
    }

    /** A name that means something to String.format or a browser is shown exactly as written. */
    @Test fun formatAndMarkupNames_shownAsText() {
        listOf("%1\$s %d %n", "<b>MTR</b>").forEach { name ->
            type(DraftPresetShareCodec.encode(rcq.copy(name = name)))
            compose.onNodeWithText(name).assertExists()
            importButton().assertIsEnabled()
        }
    }

    @Test fun fixingTheText_turnsImportOn() {
        type(rcqJson.dropLast(1))
        importButton().assertIsNotEnabled()
        type(rcqJson)
        importButton().assertIsEnabled()
    }

    @Test fun pasteButton_readsTheClipboard() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("preset", "[19/11/26, 14:45] Alejandro: $rcqJson"))
        }
        compose.onNodeWithContentDescription(s(R.string.draft_times_import_paste)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("RCQ Girona").fetchSemanticsNodes().isNotEmpty() }
        importButton().assertIsEnabled()
    }
}
